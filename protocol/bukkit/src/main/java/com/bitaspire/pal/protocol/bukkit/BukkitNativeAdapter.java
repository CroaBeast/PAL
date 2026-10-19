package com.bitaspire.pal.protocol.bukkit;

import com.bitaspire.pal.protocol.ProtocolIdentity;
import com.bitaspire.pal.protocol.ProtocolPlatform;
import com.bitaspire.pal.protocol.ProtocolProvider;
import com.bitaspire.pal.protocol.mojang.MojangProfile;
import com.bitaspire.pal.protocol.mojang.MojangProfileClient;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class BukkitNativeAdapter implements BukkitAdapter {

    private static final String HANDLER = "pal_native_login";

    /**
     * Covers the gap between the handshake and the join event, slow world loads included.
     */
    private static final long TTL_MILLIS = 300_000L;

    @Getter
    @NotNull
    private final Plugin plugin;
    @Getter
    @NotNull
    private final BukkitOptions options;
    @NotNull
    private final Function<String, CompletionStage<Boolean>> premiumName;

    @Getter(AccessLevel.PACKAGE)
    private final MojangProfileClient client = new MojangProfileClient();
    private final Map<String, Long> challenges = new ConcurrentHashMap<>();
    private final Map<String, Verified> verified = new ConcurrentHashMap<>();

    @Getter(AccessLevel.PACKAGE)
    private KeyPair keyPair;
    private Acceptor acceptor;
    private final List<Channel> serverChannels = new ArrayList<>();

    @NotNull
    @Override
    public ProtocolPlatform getPlatform() {
        return ProtocolPlatform.BUKKIT;
    }

    @Override
    public boolean supportsLoginPhase() {
        return plugin.getServer().getOnlineMode() || isChallenging();
    }

    @Override
    public boolean isChallenging() {
        return acceptor != null;
    }

    @Override
    public void start() {
        if (plugin.getServer().getOnlineMode() || isBehindProxy()) return;
        if (options.getMode() == BukkitOptions.Mode.AUTO && isEnabled("FastLogin")) return;

        try {
            LoginPackets packets = LoginPackets.find(plugin.getServer());

            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(1024);
            keyPair = generator.generateKeyPair();

            acceptor = new Acceptor(packets);
            if (hook()) {
                plugin.getLogger().info("PAL Native login is verifying premium accounts on this offline-mode server.");
                return;
            }

            // spigot.yml late-bind opens the port after plugins enable.
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    if (acceptor != null && !hook()) fail("the server has no listening channel");
                } catch (Exception exception) {
                    fail(exception.toString());
                }
            });
        } catch (Exception | LinkageError exception) {
            fail(exception.toString());
        }
    }

    @Override
    public void stop() {
        for (Channel channel : serverChannels) {
            if (channel.pipeline().get(HANDLER) != null) channel.pipeline().remove(HANDLER);
        }

        serverChannels.clear();
        acceptor = null;
        challenges.clear();
        verified.clear();
    }

    @Override
    public void challenge(@NotNull String name) {
        prune();
        challenges.put(key(name), System.currentTimeMillis());
    }

    @Nullable
    @Override
    public ProtocolIdentity consumeVerified(@NotNull String name) {
        Verified entry = verified.remove(key(name));
        return entry == null || isExpired(entry.at) ? null : entry.identity;
    }

    @NotNull
    CompletionStage<Boolean> shouldChallenge(@NotNull String name) {
        Long at = challenges.remove(key(name));
        if (at != null && !isExpired(at)) return CompletableFuture.completedFuture(true);

        return premiumName.apply(name);
    }

    void verified(@NotNull String name, @NotNull MojangProfile profile) {
        prune();
        verified.put(key(name), new Verified(System.currentTimeMillis(), ProtocolIdentity.builder()
                .uniqueId(profile.getUniqueId())
                .name(profile.getName())
                .provider(ProtocolProvider.PAL_NATIVE)
                .source("hasJoined")
                .build()));
    }

    /**
     * Puts the acceptor on every channel the server listens on. Works the same on Spigot and
     * Paper from 1.8 up, where the field names differ but the types do not.
     */
    private boolean hook() throws ReflectiveOperationException {
        Object server = plugin.getServer().getClass().getMethod("getServer").invoke(plugin.getServer());
        Object connection = fieldOfType(server, "ServerConnection", "ServerConnectionListener");
        if (connection == null) return false;

        for (Field field : connection.getClass().getDeclaredFields()) {
            if (!List.class.isAssignableFrom(field.getType())) continue;

            field.setAccessible(true);
            List<?> futures = (List<?>) field.get(connection);
            if (futures == null) continue;

            synchronized (futures) {
                for (Object future : futures) {
                    if (!(future instanceof ChannelFuture)) continue;

                    Channel channel = ((ChannelFuture) future).channel();
                    channel.pipeline().addFirst(HANDLER, acceptor);
                    serverChannels.add(channel);
                }
            }
        }

        return !serverChannels.isEmpty();
    }

    @Nullable
    private static Object fieldOfType(@NotNull Object owner, @NotNull String... names) throws IllegalAccessException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!Arrays.asList(names).contains(field.getType().getSimpleName())) continue;

                field.setAccessible(true);
                return field.get(owner);
            }
        }
        return null;
    }

    private void inject(@NotNull Channel channel, @NotNull LoginPackets packets) {
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get("packet_handler") == null || pipeline.get(HANDLER) != null) return;

        pipeline.addBefore("packet_handler", HANDLER, new NativeLoginHandler(this, packets));
    }

    private void fail(@NotNull String reason) {
        acceptor = null;
        plugin.getLogger().warning("PAL Native login could not hook the server network: " + reason);
    }

    /**
     * A proxy already verified the player, and an encryption request sent through it would
     * break the login.
     */
    private static boolean isBehindProxy() {
        return yaml("spigot.yml").getBoolean("settings.bungeecord")
                || yaml("config/paper-global.yml").getBoolean("proxies.velocity.enabled")
                || yaml("paper.yml").getBoolean("settings.velocity-support.enabled");
    }

    @NotNull
    private static YamlConfiguration yaml(@NotNull String path) {
        File file = new File(path);
        return file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    private boolean isEnabled(@NotNull String name) {
        Plugin dependency = plugin.getServer().getPluginManager().getPlugin(name);
        return dependency != null && dependency.isEnabled();
    }

    private void prune() {
        verified.values().removeIf(entry -> isExpired(entry.at));
        challenges.values().removeIf(BukkitNativeAdapter::isExpired);
    }

    private static boolean isExpired(long at) {
        return System.currentTimeMillis() - at > TTL_MILLIS;
    }

    @NotNull
    private static String key(@NotNull String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * Sits on the listening channel and reaches each new connection before the server
     * initializes it, then adds the login handler once the server's own handlers are in.
     */
    @ChannelHandler.Sharable
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private final class Acceptor extends ChannelInboundHandlerAdapter {

        private final LoginPackets packets;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ((Channel) msg).pipeline().addFirst(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel channel) {
                    channel.eventLoop().execute(() -> inject(channel, packets));
                }
            });
            ctx.fireChannelRead(msg);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Verified {

        private final long at;
        private final ProtocolIdentity identity;
    }
}
