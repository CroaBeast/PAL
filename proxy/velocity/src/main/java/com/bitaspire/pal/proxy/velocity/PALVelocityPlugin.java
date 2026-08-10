package com.bitaspire.pal.proxy.velocity;

import com.bitaspire.pal.proxy.PALAddon;
import com.bitaspire.pal.proxy.Platform;
import com.bitaspire.pal.proxy.bridge.BridgeOptions;
import com.bitaspire.pal.proxy.bridge.ProxyBridgeConfig;
import com.bitaspire.pal.proxy.bridge.ProxyStorageDrivers;
import com.bitaspire.pal.proxy.bridge.SessionBridge;
import com.bitaspire.pal.proxy.bridge.SessionBridgeListener;
import com.bitaspire.pal.proxy.connection.ConnectionDecision;
import com.bitaspire.pal.proxy.connection.ConnectionGuard;
import com.bitaspire.pal.proxy.connection.ConnectionRequest;
import com.bitaspire.pal.proxy.identity.IdentityResolver;
import com.bitaspire.pal.proxy.realm.ProxyRealm;
import com.bitaspire.pal.proxy.realm.ProxyRoleMessageCodec;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bstats.velocity.Metrics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "pal-proxy",
        name = "PAL Proxy",
        version = "0.1.1",
        authors = {"CroaBeast"},
        dependencies = {
                @Dependency(id = "fastlogin", optional = true),
                @Dependency(id = "floodgate", optional = true)
        }
)
public final class PALVelocityPlugin implements PALAddon {

    private static final ChannelIdentifier ROLE_CHANNEL = MinecraftChannelIdentifier.from(ProxyRoleMessageCodec.CHANNEL);
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ProxyServer server;
    private final Logger logger;
    private final File dataFolder;
    private final Metrics.Factory metricsFactory;
    private final Map<UUID, String> pendingTargets = new ConcurrentHashMap<>();
    private final Map<UUID, ProxyRealm> pendingRealms = new ConcurrentHashMap<>();
    private final Map<String, Boolean> serverHealth = new ConcurrentHashMap<>();

    @Getter
    private ConnectionGuard connectionGuard;
    private SessionBridge sessionBridge;
    private IdentityResolver identityResolver = IdentityResolver.noop();
    private ProxyBridgeConfig bridgeConfig;
    private ScheduledTask healthTask;
    private boolean fastLoginHooksRegistered = false;

    @Inject
    public PALVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) {
        this.server = server;
        this.logger = logger;
        this.dataFolder = dataDirectory.toFile();
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    void onProxyInitialization(ProxyInitializeEvent event) {
        server.getChannelRegistrar().register(ROLE_CHANNEL);
        ProxyStorageDrivers.install(dataFolder, jar -> server.getPluginManager().addToClasspath(this, jar.toPath()));
        reload();
        registerFastLoginHooks();
        metricsFactory.make(this, 33120);
        logger.info("PAL Proxy initialized on Velocity with {} registered servers.", server.getAllServers().size());
    }

    @Subscribe
    void onProxyShutdown(ProxyShutdownEvent event) {
        server.getChannelRegistrar().unregister(ROLE_CHANNEL);
        if (healthTask != null) healthTask.cancel();
        if (sessionBridge != null) sessionBridge.close();
    }

    @Subscribe
    void onPreLogin(PreLoginEvent event) {
        ConnectionRequest request = ConnectionRequest.of(
                event.getUsername(), null,
                event.getConnection().getRemoteAddress()
        );

        ConnectionDecision decision = connectionGuard.validate(request).toCompletableFuture().join();

        if (decision.getType() == ConnectionDecision.Type.DENY) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.text(decision.getReason())));
            return;
        }

        IdentityResolver.NativeDecision nativeDecision = identityResolver.prepareNative(
                event.getUsername(),
                bridgeConfig,
                hasPlugin("fastlogin") || isGeyserFloodgateConnection(event.getConnection().getRemoteAddress())
        );

        if (nativeDecision.isBlock()) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.text(nativeDecision.getReason())));
            return;
        }

        event.setResult(nativeDecision.isOnlineMode()
                ? PreLoginEvent.PreLoginComponentResult.forceOnlineMode()
                : PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
    }

    private boolean isGeyserFloodgateConnection(@NotNull SocketAddress address) {
        if (!hasPlugin("floodgate")) return false;
        if (!(address instanceof InetSocketAddress)) return false;

        InetSocketAddress socketAddress = (InetSocketAddress) address;
        return socketAddress.getPort() == 0;
    }

    @Subscribe
    void onServerPreConnect(ServerPreConnectEvent event) {
        String requestedServer = event.getOriginalServer().getServerInfo().getName();
        String currentServer = event.getPlayer().getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse("<none>");
        boolean manualAuthTarget = isManualAuthTarget(currentServer, requestedServer);
        ConnectionRequest request = ConnectionRequest.builder()
                .name(event.getPlayer().getUsername())
                .uniqueId(event.getPlayer().getUniqueId())
                .address(event.getPlayer().getRemoteAddress())
                .requestedServer(requestedServer)
                .allowAuthenticatedAuthTarget(manualAuthTarget && allowsAuthenticatedAuthTarget())
                .build();

        ConnectionDecision decision = connectionGuard.validate(request).toCompletableFuture().join();

        if (decision.getType() == ConnectionDecision.Type.DENY) {
            event.getPlayer().disconnect(message(decision.getReason()));
            return;
        }

        if (decision.getType() == ConnectionDecision.Type.REDIRECT && decision.getTargetServer() != null) {
            rememberTarget(event.getPlayer(), requestedServer);
            Optional<RegisteredServer> target = availableServer(decision.getTargetServer(), currentServer);
            if (target.isPresent()) {
                if (currentServer.equalsIgnoreCase(target.get().getServerInfo().getName())) {
                    event.setResult(ServerPreConnectEvent.ServerResult.denied());
                    event.getPlayer().sendMessage(message(decision.getReason(), authenticationRequiredMessage()));
                    return;
                }

                rememberRealm(event.getPlayer(), target.get(), realmForTarget(decision.getTargetServer()));
                event.setResult(ServerPreConnectEvent.ServerResult.allowed(target.get()));
            } else if (applyFailover(event, ProxyRealm.AUTH)) {
                return;
            } else {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                event.getPlayer().disconnect(message(authServerUnavailableMessage()));
            }
        } else if (decision.getType() == ConnectionDecision.Type.ALLOW) {
            if (!isAvailable(event.getOriginalServer())) {
                Optional<RegisteredServer> target = unavailableTarget(requestedServer);
                if (target.isPresent()) {
                    rememberRealm(event.getPlayer(), target.get(), realmForTarget(target.get().getServerInfo().getName()));
                    event.setResult(ServerPreConnectEvent.ServerResult.allowed(target.get()));
                    return;
                }

                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                event.getPlayer().disconnect(message(authServerUnavailableMessage()));
                return;
            }

            if (manualAuthTarget && allowsAuthenticatedAuthTarget()) {
                event.getPlayer().sendMessage(message(authenticatedAuthAllowedMessage()));
            }
            rememberRealm(event.getPlayer(), event.getOriginalServer(), realmForTarget(requestedServer));
        }
    }

    @Subscribe
    void onServerConnected(ServerConnectedEvent event) {
        if (returnAuthenticatedFromAuth(event.getPlayer(), event.getServer().getServerInfo().getName())) return;
        if (bridgeConfig == null || !hasRoleSecret()) return;

        ProxyRealm realm = pendingRealms.remove(event.getPlayer().getUniqueId());
        if (realm == null) realm = realmForTarget(event.getServer().getServerInfo().getName());

        byte[] payload = ProxyRoleMessageCodec.encode(
                event.getPlayer().getUniqueId(),
                realm,
                event.getServer().getServerInfo().getName(),
                bridgeConfig.getSecret()
        );
        if (payload.length == 0) return;

        event.getPlayer().getCurrentServer().ifPresent(connection -> connection.sendPluginMessage(ROLE_CHANNEL, payload));
    }

    @Subscribe
    void onKickedFromServer(KickedFromServerEvent event) {
        if (bridgeConfig == null || connectionGuard == null) return;

        String kickedFrom = event.getServer().getServerInfo().getName();
        Optional<RegisteredServer> target = kickTarget(event.getPlayer(), kickedFrom);
        if (!target.isPresent()) {
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(message(authServerUnavailableMessage())));
            return;
        }

        event.setResult(KickedFromServerEvent.RedirectPlayer.create(
                target.get(),
                message(redirectingMessage(target.get().getServerInfo().getName()))
        ));
    }

    @NotNull
    public Platform getPlatform() {
        return Platform.VELOCITY;
    }

    @Override
    public void reload() {
        if (sessionBridge != null) sessionBridge.close();
        if (healthTask != null) healthTask.cancel();
        bridgeConfig = ProxyBridgeConfig.load(dataFolder, server.getAllServers().size());
        sessionBridge = SessionBridge.create(bridgeConfig);
        identityResolver = IdentityResolver.create(hasPlugin("fastlogin"), hasPlugin("floodgate"), bridgeConfig.isNativePremium());
        sessionBridge.addListener(new SessionBridgeListener() {
            @Override
            public void onSessionSaved(@NotNull com.bitaspire.pal.proxy.session.AuthSession session) {
                server.getScheduler().buildTask(PALVelocityPlugin.this, () -> returnToTarget(session.getUniqueId())).schedule();
            }

            @Override
            public void onSessionInvalidated(@NotNull UUID uniqueId) {
                server.getScheduler().buildTask(PALVelocityPlugin.this, () -> moveToAuth(uniqueId)).schedule();
            }
        });

        if (bridgeConfig.isRedis() && !bridgeConfig.hasUsableSecret()) {
            logger.warn("PAL Proxy Redis bridge requires bridge.sec.secret. Set the same non-default secret in Bukkit and proxy bridge.yml.");
        }
        warnBridgeMode();
        startHealthChecks();

        connectionGuard = ConnectionGuard.create(
                sessionBridge,
                identityResolver,
                bridgeConfig.toOptions()
        );
    }

    private boolean hasPlugin(String id) {
        return server.getPluginManager().getPlugin(id).isPresent();
    }

    private void rememberTarget(@NotNull Player player, @NotNull String requestedServer) {
        BridgeOptions options = bridgeConfig.toOptions();
        if (!options.isRememberTarget()) return;
        if (options.hasAuthServer() && options.getAuthServer().equalsIgnoreCase(requestedServer)) return;
        pendingTargets.put(player.getUniqueId(), requestedServer);
    }

    private void returnToTarget(@NotNull UUID uniqueId) {
        String targetName = pendingTargets.remove(uniqueId);
        if (targetName == null) {
            returnAuthenticatedFromAuth(uniqueId);
            return;
        }

        Optional<Player> player = server.getPlayer(uniqueId);
        Optional<RegisteredServer> target = availableServer(targetName, "");
        if (!player.isPresent() || !target.isPresent()) return;

        String current = player.get().getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse(null);
        if (targetName.equalsIgnoreCase(current)) return;

        rememberRealm(player.get(), target.get(), realmForTarget(targetName));
        player.get().createConnectionRequest(target.get()).fireAndForget();
    }

    private boolean returnAuthenticatedFromAuth(@NotNull ServerConnectedEvent event) {
        return returnAuthenticatedFromAuth(event.getPlayer(), event.getServer().getServerInfo().getName());
    }

    private boolean returnAuthenticatedFromAuth(@NotNull UUID uniqueId) {
        if (bridgeConfig == null || connectionGuard == null) return false;

        Optional<Player> player = server.getPlayer(uniqueId);
        if (!player.isPresent()) return false;

        String connected = player.get().getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse(null);
        if (connected == null) return false;

        return returnAuthenticatedFromAuth(player.get(), connected);
    }

    private boolean returnAuthenticatedFromAuth(@NotNull Player player, @NotNull String connected) {
        if (bridgeConfig == null || connectionGuard == null) return false;

        BridgeOptions options = bridgeConfig.toOptions();
        if (!options.hasAuthServer() || !options.getAuthServer().equalsIgnoreCase(connected)) {
            return false;
        }

        ConnectionDecision decision = validate(player, connected);
        if (decision.getType() != ConnectionDecision.Type.REDIRECT || decision.getTargetServer() == null) return false;

        Optional<RegisteredServer> target = availableServer(decision.getTargetServer(), connected);
        if (!target.isPresent() || connected.equalsIgnoreCase(target.get().getServerInfo().getName())) return false;

        rememberRealm(player, target.get(), realmForTarget(decision.getTargetServer()));
        server.getScheduler().buildTask(this, () -> player.createConnectionRequest(target.get()).fireAndForget()).schedule();
        return true;
    }

    private boolean isManualAuthTarget(@NotNull String currentServer, @NotNull String requestedServer) {
        if ("<none>".equals(currentServer) || bridgeConfig == null) return false;

        BridgeOptions options = bridgeConfig.toOptions();
        return options.hasAuthServer() && options.getAuthServer().equalsIgnoreCase(requestedServer);
    }

    private boolean allowsAuthenticatedAuthTarget() {
        return bridgeConfig != null && bridgeConfig.toOptions().allowsAuthenticatedAuthTarget();
    }

    private void moveToAuth(@NotNull UUID uniqueId) {
        if (bridgeConfig == null || !bridgeConfig.toOptions().hasAuthServer()) return;

        Optional<Player> player = server.getPlayer(uniqueId);
        Optional<RegisteredServer> auth = authServer(bridgeConfig.toOptions(), "");
        if (!player.isPresent()) return;

        if (auth.isPresent()) {
            rememberRealm(player.get(), auth.get(), ProxyRealm.AUTH);
            player.get().createConnectionRequest(auth.get()).fireAndForget();
        }
        else if (!bridgeConfig.toOptions().isKeepOnline()) player.get().disconnect(message(authenticationRequiredMessage()));
    }

    @NotNull
    private Component message(@Nullable String value) {
        return message(value, "");
    }

    @NotNull
    private Component message(@Nullable String value, @NotNull String fallback) {
        String resolved = value == null || value.trim().isEmpty() ? fallback : value;
        return LEGACY.deserialize(resolved);
    }

    @NotNull
    private String authenticationRequiredMessage() {
        return bridgeConfig == null ? "Authentication required" : bridgeConfig.getAuthenticationRequiredMessage();
    }

    @NotNull
    private String authServerUnavailableMessage() {
        return bridgeConfig == null ? "Authentication server is not available" : bridgeConfig.getAuthServerUnavailableMessage();
    }

    @NotNull
    private String authenticatedAuthAllowedMessage() {
        return bridgeConfig == null
                ? "You are already authenticated. Auth is only needed for login."
                : bridgeConfig.getAuthenticatedAuthAllowedMessage();
    }

    @NotNull
    private String redirectingMessage(@NotNull String serverName) {
        String value = bridgeConfig == null ? "Redirecting to {server}" : bridgeConfig.getRedirectingMessage();
        return value.replace("{server}", serverName);
    }

    private boolean applyFailover(@NotNull ServerPreConnectEvent event, @NotNull ProxyRealm realm) {
        BridgeOptions options = bridgeConfig.toOptions();
        if (!options.isFailover()) return false;

        return connectFallback(event.getPlayer(), event, realm) || options.isNewLogin();
    }

    private boolean connectFallback(@NotNull Player player, @NotNull ServerPreConnectEvent event, @NotNull ProxyRealm realm) {
        BridgeOptions options = bridgeConfig.toOptions();

        Optional<RegisteredServer> fallback = fallbackServer(options, "");
        if (!fallback.isPresent()) return false;

        rememberRealm(player, fallback.get(), realm);
        event.setResult(ServerPreConnectEvent.ServerResult.allowed(fallback.get()));
        return true;
    }

    @NotNull
    private Optional<RegisteredServer> unavailableTarget(@NotNull String unavailable) {
        BridgeOptions options = bridgeConfig.toOptions();
        Optional<RegisteredServer> fallback = fallbackServer(options, unavailable);
        if (fallback.isPresent()) return fallback;
        return authServer(options, unavailable);
    }

    @NotNull
    private Optional<RegisteredServer> kickTarget(@NotNull Player player, @NotNull String kickedFrom) {
        BridgeOptions options = bridgeConfig.toOptions();
        Optional<RegisteredServer> fallback = fallbackServer(options, kickedFrom);

        if (fallback.isPresent()) {
            ConnectionDecision decision = validate(player, fallback.get().getServerInfo().getName());
            if (decision.getType() == ConnectionDecision.Type.ALLOW) {
                rememberRealm(player, fallback.get(), ProxyRealm.LIMBO);
                return fallback;
            }

            Optional<RegisteredServer> redirected = redirectTarget(options, decision, kickedFrom);
            if (redirected.isPresent()) {
                rememberRealm(player, redirected.get(), realmForTarget(decision.getTargetServer()));
                return redirected;
            }
            if (options.isNewLogin()) {
                rememberRealm(player, fallback.get(), ProxyRealm.LIMBO);
                return fallback;
            }
        }

        Optional<RegisteredServer> auth = authServer(options, kickedFrom);
        if (!auth.isPresent()) return Optional.empty();

        ConnectionDecision decision = validate(player, auth.get().getServerInfo().getName());
        if (!decision.isAllowed()) return Optional.empty();
        rememberRealm(player, auth.get(), ProxyRealm.AUTH);
        return auth;
    }

    @NotNull
    private ConnectionDecision validate(@NotNull Player player, @NotNull String target) {
        ConnectionRequest request = ConnectionRequest.builder()
                .name(player.getUsername())
                .uniqueId(player.getUniqueId())
                .address(player.getRemoteAddress())
                .requestedServer(target)
                .build();

        return connectionGuard.validate(request).toCompletableFuture().join();
    }

    @NotNull
    private Optional<RegisteredServer> redirectTarget(@NotNull BridgeOptions options, @NotNull ConnectionDecision decision, @NotNull String kickedFrom) {
        if (decision.getType() != ConnectionDecision.Type.REDIRECT || decision.getTargetServer() == null) {
            return Optional.empty();
        }

        if (decision.getTargetServer().equalsIgnoreCase(kickedFrom)) return Optional.empty();
        Optional<RegisteredServer> target = availableServer(decision.getTargetServer(), kickedFrom);
        return target.isPresent() ? target : authServer(options, kickedFrom);
    }

    @NotNull
    private Optional<RegisteredServer> fallbackServer(@NotNull BridgeOptions options, @NotNull String kickedFrom) {
        if (!options.isFailover()) {
            return Optional.empty();
        }

        Optional<RegisteredServer> fallback = availableServer(options.getFallbackServer(), kickedFrom);
        return fallback.isPresent() ? fallback : availableServer(bridgeConfig.getLobbyServer(), kickedFrom);
    }

    @NotNull
    private Optional<RegisteredServer> authServer(@NotNull BridgeOptions options, @NotNull String kickedFrom) {
        if (!options.hasAuthServer()) return Optional.empty();

        Optional<RegisteredServer> auth = availableServer(options.getAuthServer(), kickedFrom);
        if (auth.isPresent()) return auth;

        Optional<RegisteredServer> fallback = fallbackServer(options, kickedFrom);
        return fallback.isPresent() ? fallback : availableServer(bridgeConfig.getLobbyServer(), kickedFrom);
    }

    @NotNull
    private Optional<RegisteredServer> availableServer(@NotNull String name, @NotNull String kickedFrom) {
        if (name.trim().isEmpty() || name.equalsIgnoreCase(kickedFrom)) return Optional.empty();
        Optional<RegisteredServer> registered = server.getServer(name);
        return registered.isPresent() && isAvailable(registered.get()) ? registered : Optional.empty();
    }

    private boolean isAvailable(@NotNull RegisteredServer registered) {
        return serverHealth.getOrDefault(key(registered.getServerInfo().getName()), true);
    }

    @NotNull
    private ProxyRealm realmForTarget(@NotNull String target) {
        return ProxyRealm.fromTarget(target, bridgeConfig.getAuthServer(), bridgeConfig.getLobbyServer(), bridgeConfig.getFallbackServer());
    }

    private void rememberRealm(@NotNull Player player, @NotNull RegisteredServer target, @NotNull ProxyRealm realm) {
        pendingRealms.put(player.getUniqueId(), realm);
    }

    private boolean hasRoleSecret() {
        String secret = bridgeConfig.getSecret().trim();
        return !secret.isEmpty() && !"change-me".equalsIgnoreCase(secret);
    }

    private void registerFastLoginHooks() {
        if (fastLoginHooksRegistered || !hasPlugin("fastlogin")) return;

        try {
            server.getEventManager().register(this, new VelocityFastLoginHook(this));
            fastLoginHooksRegistered = true;
        } catch (NoClassDefFoundError error) {
            logger.warn("FastLogin is installed, but PAL could not load its Velocity API: {}", error.getMessage());
        } catch (Exception exception) {
            logger.warn("Could not hook FastLogin: {}", exception.getMessage());
        }
    }

    void handleFastLogin(@NotNull Object event) {
        identityResolver.handleFastLogin(event);
    }

    private void warnBridgeMode() {
        if (bridgeConfig.isMemory()) {
            logger.warn("PAL Proxy MEMORY bridge is local-only and will not receive Bukkit auth-server sessions.");
        }

        if (!bridgeConfig.isDatabase()) return;
        if ("username".equalsIgnoreCase(bridgeConfig.getDatabaseUsername()) || "password".equals(bridgeConfig.getDatabasePassword())) {
            logger.warn("PAL Proxy DATABASE bridge is using placeholder database credentials.");
        }

        if (bridgeConfig.getDatabaseType() == ProxyBridgeConfig.DatabaseType.SQLITE && bridgeConfig.getServers() > 1) {
            logger.warn("PAL Proxy DATABASE bridge is using SQLite with {} configured servers. Use shared MySQL, MariaDB or PostgreSQL for networks.", bridgeConfig.getServers());
        }
    }

    private void startHealthChecks() {
        serverHealth.clear();
        refreshServerHealth();
        healthTask = server.getScheduler()
                .buildTask(this, this::refreshServerHealth)
                .repeat(1L, TimeUnit.SECONDS)
                .schedule();
    }

    private void refreshServerHealth() {
        for (RegisteredServer registered : server.getAllServers()) {
            String name = registered.getServerInfo().getName();
            registered.ping().whenComplete((ping, throwable) -> serverHealth.put(key(name), throwable == null));
        }
    }

    @NotNull
    private String key(@NotNull String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
