package com.bitaspire.pal.proxy.bungee;

import com.bitaspire.pal.proxy.PALAddon;
import com.bitaspire.pal.proxy.Platform;
import com.bitaspire.pal.proxy.bridge.BridgeOptions;
import com.bitaspire.pal.proxy.bridge.ProxyBridgeConfig;
import com.bitaspire.pal.proxy.bridge.SessionBridge;
import com.bitaspire.pal.proxy.bridge.SessionBridgeListener;
import com.bitaspire.pal.proxy.connection.ConnectionDecision;
import com.bitaspire.pal.proxy.connection.ConnectionGuard;
import com.bitaspire.pal.proxy.connection.ConnectionRequest;
import com.bitaspire.pal.proxy.identity.IdentityResolver;
import com.bitaspire.pal.proxy.realm.ProxyRealm;
import com.bitaspire.pal.proxy.realm.ProxyRoleMessageCodec;
import com.bitaspire.pal.proxy.session.AuthSession;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public final class PALBungeePlugin extends Plugin implements Listener, PALAddon {

    private final Map<UUID, String> pendingTargets = new ConcurrentHashMap<>();
    private final Map<UUID, ProxyRealm> pendingRealms = new ConcurrentHashMap<>();

    @Getter
    private ConnectionGuard connectionGuard;
    private SessionBridge sessionBridge;
    private IdentityResolver identityResolver = IdentityResolver.noop();
    private ProxyBridgeConfig bridgeConfig;
    private Listener fastLoginHook;

    @Override
    public void onEnable() {
        reload();
        getProxy().registerChannel(ProxyRoleMessageCodec.CHANNEL);
        getProxy().getPluginManager().registerListener(this, this);
        registerFastLoginHooks();
        getLogger().info("PAL Proxy initialized on BungeeCord.");
    }

    @Override
    public void onDisable() {
        if (fastLoginHook != null) getProxy().getPluginManager().unregisterListener(fastLoginHook);
        if (sessionBridge != null) sessionBridge.close();
        getProxy().unregisterChannel(ProxyRoleMessageCodec.CHANNEL);
        getProxy().getPluginManager().unregisterListener(this);
    }

    @EventHandler
    void onPreLogin(PreLoginEvent event) {
        ConnectionRequest request = ConnectionRequest.of(
                event.getConnection().getName(),
                event.getConnection().getUniqueId(),
                event.getConnection().getSocketAddress()
        );

        ConnectionDecision decision = connectionGuard.validate(request).toCompletableFuture().join();

        if (decision.getType() == ConnectionDecision.Type.DENY) {
            event.setCancelled(true);
            event.setCancelReason(message(decision.getReason()));
            return;
        }

        IdentityResolver.NativeDecision nativeDecision = identityResolver.prepareNative(
                event.getConnection().getName(),
                bridgeConfig,
                hasPlugin("FastLogin")
        );

        if (nativeDecision.isBlock()) {
            event.setCancelled(true);
            event.setCancelReason(message(nativeDecision.getReason()));
            return;
        }

        event.getConnection().setOnlineMode(nativeDecision.isOnlineMode());
    }

    @EventHandler
    void onServerConnect(ServerConnectEvent event) {
        String currentServer = event.getPlayer().getServer() == null
                ? null
                : event.getPlayer().getServer().getInfo().getName();
        boolean manualAuthTarget = isManualAuthTarget(currentServer, event.getTarget().getName());
        ConnectionRequest request = ConnectionRequest.builder()
                .name(event.getPlayer().getName())
                .uniqueId(event.getPlayer().getUniqueId())
                .address(event.getPlayer().getAddress())
                .requestedServer(event.getTarget().getName())
                .allowAuthenticatedAuthTarget(manualAuthTarget && allowsAuthenticatedAuthTarget())
                .build();

        ConnectionDecision decision = connectionGuard.validate(request).toCompletableFuture().join();

        if (decision.getType() == ConnectionDecision.Type.DENY) {
            event.setCancelled(true);
            event.getPlayer().disconnect(message(decision.getReason()));
            return;
        }

        if (decision.getType() == ConnectionDecision.Type.REDIRECT && decision.getTargetServer() != null) {
            rememberTarget(event.getPlayer(), request.getRequestedServer());
            ServerInfo target = getProxy().getServerInfo(decision.getTargetServer());

            if (target != null) {
                if (sameServer(target.getName(), currentServer)) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(message(decision.getReason(), authenticationRequiredMessage()));
                    return;
                }

                rememberRealm(event.getPlayer(), target, realmForTarget(decision.getTargetServer()));
                event.setTarget(target);
            } else if (applyFailover(event, ProxyRealm.AUTH)) {
                return;
            } else {
                event.setCancelled(true);
                event.getPlayer().disconnect(message(authServerUnavailableMessage()));
            }
        } else if (decision.getType() == ConnectionDecision.Type.ALLOW) {
            if (manualAuthTarget && allowsAuthenticatedAuthTarget()) {
                event.getPlayer().sendMessage(message(authenticatedAuthAllowedMessage()));
            }
            rememberRealm(event.getPlayer(), event.getTarget(), realmForTarget(event.getTarget().getName()));
        }
    }

    @EventHandler
    void onServerConnected(ServerConnectedEvent event) {
        if (returnAuthenticatedFromAuth(event.getPlayer(), event.getServer().getInfo().getName())) return;
        if (bridgeConfig == null || !hasRoleSecret()) return;

        ProxyRealm realm = pendingRealms.remove(event.getPlayer().getUniqueId());
        if (realm == null) realm = realmForTarget(event.getServer().getInfo().getName());

        byte[] payload = ProxyRoleMessageCodec.encode(
                event.getPlayer().getUniqueId(),
                realm,
                event.getServer().getInfo().getName(),
                bridgeConfig.getSecret()
        );
        if (payload.length > 0) event.getServer().sendData(ProxyRoleMessageCodec.CHANNEL, payload);
    }

    @EventHandler
    void onServerKick(ServerKickEvent event) {
        if (bridgeConfig == null || connectionGuard == null) return;

        String kickedFrom = event.getKickedFrom() == null ? null : event.getKickedFrom().getName();
        ServerInfo target = kickTarget(event.getPlayer(), kickedFrom);
        if (target == null) return;

        event.setCancelled(true);
        event.setCancelServer(target);
        event.setKickReason(color(redirectingMessage(target.getName())));
    }

    @NotNull
    public Platform getPlatform() {
        return Platform.BUNGEE;
    }

    @Override
    public void reload() {
        if (sessionBridge != null) sessionBridge.close();

        bridgeConfig = ProxyBridgeConfig.load(getDataFolder(), getProxy().getServers().size());
        sessionBridge = SessionBridge.create(bridgeConfig);
        identityResolver = IdentityResolver.create(hasPlugin("FastLogin"), hasPlugin("floodgate"), bridgeConfig.isNativePremium());
        sessionBridge.addListener(new SessionBridgeListener() {
            @Override
            public void onSessionSaved(@NotNull AuthSession session) {
                getProxy().getScheduler().runAsync(PALBungeePlugin.this, () -> returnToTarget(session.getUniqueId()));
            }

            @Override
            public void onSessionInvalidated(@NotNull UUID uniqueId) {
                getProxy().getScheduler().runAsync(PALBungeePlugin.this, () -> moveToAuth(uniqueId));
            }
        });

        if (bridgeConfig.isRedis() && !bridgeConfig.hasUsableSecret()) {
            getLogger().warning("PAL Proxy Redis bridge requires bridge.sec.secret. Set the same non-default secret in Bukkit and proxy bridge.yml.");
        }
        warnBridgeMode();

        connectionGuard = ConnectionGuard.create(
                sessionBridge,
                identityResolver,
                bridgeConfig.toOptions()
        );
    }

    private boolean hasPlugin(String name) {
        return getProxy().getPluginManager().getPlugin(name) != null
                || getProxy().getPluginManager().getPlugin(name.toLowerCase()) != null;
    }

    void handleFastLogin(@NotNull Object event) {
        identityResolver.handleFastLogin(event);
    }

    private void rememberTarget(@NotNull ProxiedPlayer player, @Nullable String requestedServer) {
        if (requestedServer == null) return;
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

        ProxiedPlayer player = getProxy().getPlayer(uniqueId);
        ServerInfo target = getProxy().getServerInfo(targetName);
        if (player == null || target == null) return;
        if (player.getServer() != null && targetName.equalsIgnoreCase(player.getServer().getInfo().getName())) return;

        rememberRealm(player, target, realmForTarget(targetName));
        player.connect(target);
    }

    private boolean returnAuthenticatedFromAuth(@NotNull ServerConnectedEvent event) {
        return returnAuthenticatedFromAuth(event.getPlayer(), event.getServer().getInfo().getName());
    }

    private boolean returnAuthenticatedFromAuth(@NotNull UUID uniqueId) {
        if (bridgeConfig == null || connectionGuard == null) return false;

        ProxiedPlayer player = getProxy().getPlayer(uniqueId);
        if (player == null || player.getServer() == null) return false;

        String connected = player.getServer().getInfo().getName();
        return returnAuthenticatedFromAuth(player, connected);
    }

    private boolean returnAuthenticatedFromAuth(@NotNull ProxiedPlayer player, @NotNull String connected) {
        if (bridgeConfig == null || connectionGuard == null) return false;

        BridgeOptions options = bridgeConfig.toOptions();
        if (!options.hasAuthServer() || !options.getAuthServer().equalsIgnoreCase(connected)) return false;

        ConnectionDecision decision = validate(player, connected);
        if (decision.getType() != ConnectionDecision.Type.REDIRECT || decision.getTargetServer() == null) return false;

        ServerInfo target = getProxy().getServerInfo(decision.getTargetServer());
        if (target == null || sameServer(connected, target.getName())) return false;

        rememberRealm(player, target, realmForTarget(decision.getTargetServer()));
        getProxy().getScheduler().runAsync(this, () -> player.connect(target));
        return true;
    }

    private void moveToAuth(@NotNull UUID uniqueId) {
        if (bridgeConfig == null || !bridgeConfig.toOptions().hasAuthServer()) return;

        ProxiedPlayer player = getProxy().getPlayer(uniqueId);
        ServerInfo auth = authServer(bridgeConfig.toOptions(), null);
        if (player == null) return;

        if (auth != null) {
            rememberRealm(player, auth, ProxyRealm.AUTH);
            player.connect(auth);
        }
        else if (!bridgeConfig.toOptions().isKeepOnline()) player.disconnect(message(authenticationRequiredMessage()));
    }

    private BaseComponent[] message(@Nullable String value) {
        return message(value, "");
    }

    private BaseComponent[] message(@Nullable String value, @NotNull String fallback) {
        String resolved = value == null || value.trim().isEmpty() ? fallback : value;
        return TextComponent.fromLegacyText(color(resolved));
    }

    @NotNull
    private String color(@NotNull String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
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

    private boolean applyFailover(@NotNull ServerConnectEvent event, @NotNull ProxyRealm realm) {
        BridgeOptions options = bridgeConfig.toOptions();
        if (!options.isFailover()) return false;

        return connectFallback(event, realm) || options.isNewLogin();
    }

    private boolean connectFallback(@NotNull ServerConnectEvent event, @NotNull ProxyRealm realm) {
        BridgeOptions options = bridgeConfig.toOptions();

        ServerInfo fallback = fallbackServer(options, null);
        if (fallback == null) return false;

        rememberRealm(event.getPlayer(), fallback, realm);
        event.setTarget(fallback);
        return true;
    }

    @Nullable
    private ServerInfo kickTarget(@NotNull ProxiedPlayer player, @Nullable String kickedFrom) {
        BridgeOptions options = bridgeConfig.toOptions();
        ServerInfo fallback = fallbackServer(options, kickedFrom);

        if (fallback != null) {
            ConnectionDecision decision = validate(player, fallback.getName());
            if (decision.getType() == ConnectionDecision.Type.ALLOW) {
                rememberRealm(player, fallback, ProxyRealm.LIMBO);
                return fallback;
            }

            ServerInfo redirected = redirectTarget(options, decision, kickedFrom);
            if (redirected != null) {
                rememberRealm(player, redirected, realmForTarget(decision.getTargetServer()));
                return redirected;
            }
            if (options.isNewLogin()) {
                rememberRealm(player, fallback, ProxyRealm.LIMBO);
                return fallback;
            }
        }

        ServerInfo auth = authServer(options, kickedFrom);
        if (auth == null) return null;

        ConnectionDecision decision = validate(player, auth.getName());
        if (!decision.isAllowed()) return null;
        rememberRealm(player, auth, ProxyRealm.AUTH);
        return auth;
    }

    @NotNull
    private ConnectionDecision validate(@NotNull ProxiedPlayer player, @NotNull String target) {
        ConnectionRequest request = ConnectionRequest.builder()
                .name(player.getName())
                .uniqueId(player.getUniqueId())
                .address(player.getAddress())
                .requestedServer(target)
                .build();

        return connectionGuard.validate(request).toCompletableFuture().join();
    }

    @Nullable
    private ServerInfo redirectTarget(@NotNull BridgeOptions options, @NotNull ConnectionDecision decision, @Nullable String kickedFrom) {
        if (decision.getType() != ConnectionDecision.Type.REDIRECT || decision.getTargetServer() == null) return null;
        if (sameServer(decision.getTargetServer(), kickedFrom)) return null;
        ServerInfo target = getProxy().getServerInfo(decision.getTargetServer());
        return target == null ? authServer(options, kickedFrom) : target;
    }

    @Nullable
    private ServerInfo fallbackServer(@NotNull BridgeOptions options, @Nullable String kickedFrom) {
        if (!options.isFailover()) return null;

        ServerInfo fallback = configuredServer(options.getFallbackServer(), kickedFrom);
        return fallback == null ? configuredServer(bridgeConfig.getLobbyServer(), kickedFrom) : fallback;
    }

    @Nullable
    private ServerInfo authServer(@NotNull BridgeOptions options, @Nullable String kickedFrom) {
        if (!options.hasAuthServer()) return null;

        ServerInfo auth = configuredServer(options.getAuthServer(), kickedFrom);
        if (auth != null) return auth;

        ServerInfo fallback = fallbackServer(options, kickedFrom);
        return fallback == null ? configuredServer(bridgeConfig.getLobbyServer(), kickedFrom) : fallback;
    }

    @Nullable
    private ServerInfo configuredServer(@Nullable String name, @Nullable String kickedFrom) {
        if (name == null || name.trim().isEmpty() || sameServer(name, kickedFrom)) return null;
        return getProxy().getServerInfo(name);
    }

    @NotNull
    private ProxyRealm realmForTarget(@NotNull String target) {
        return ProxyRealm.fromTarget(target, bridgeConfig.getAuthServer(), bridgeConfig.getLobbyServer(), bridgeConfig.getFallbackServer());
    }

    private void rememberRealm(@NotNull ProxiedPlayer player, @NotNull ServerInfo target, @NotNull ProxyRealm realm) {
        pendingRealms.put(player.getUniqueId(), realm);
    }

    private boolean hasRoleSecret() {
        String secret = bridgeConfig.getSecret().trim();
        return !secret.isEmpty() && !"change-me".equalsIgnoreCase(secret);
    }

    private boolean isManualAuthTarget(@Nullable String currentServer, @NotNull String requestedServer) {
        if (currentServer == null || bridgeConfig == null) return false;

        BridgeOptions options = bridgeConfig.toOptions();
        return options.hasAuthServer() && options.getAuthServer().equalsIgnoreCase(requestedServer);
    }

    private boolean allowsAuthenticatedAuthTarget() {
        return bridgeConfig != null && bridgeConfig.toOptions().allowsAuthenticatedAuthTarget();
    }

    private boolean sameServer(@Nullable String first, @Nullable String second) {
        return first != null && second != null && first.equalsIgnoreCase(second);
    }

    private void registerFastLoginHooks() {
        if (!hasPlugin("FastLogin") || fastLoginHook != null) return;

        try {
            fastLoginHook = new BungeeFastLoginHook(this);
            getProxy().getPluginManager().registerListener(this, fastLoginHook);
        } catch (NoClassDefFoundError error) {
            fastLoginHook = null;
            getLogger().warning("FastLogin is installed, but PAL could not load its Bungee API: " + error.getMessage());
        } catch (Exception exception) {
            fastLoginHook = null;
            getLogger().warning("Could not hook FastLogin: " + exception.getMessage());
        }
    }

    private void warnBridgeMode() {
        if (bridgeConfig.isMemory()) {
            getLogger().warning("PAL Proxy MEMORY bridge is local-only and will not receive Bukkit auth-server sessions.");
        }

        if (!bridgeConfig.isDatabase()) return;
        if ("username".equalsIgnoreCase(bridgeConfig.getDatabaseUsername()) || "password".equals(bridgeConfig.getDatabasePassword())) {
            getLogger().warning("PAL Proxy DATABASE bridge is using placeholder database credentials.");
        }

        if (bridgeConfig.getDatabaseType() == ProxyBridgeConfig.DatabaseType.SQLITE && bridgeConfig.getServers() > 1) {
            getLogger().warning("PAL Proxy DATABASE bridge is using SQLite with " + bridgeConfig.getServers() + " configured servers. Use shared MySQL, MariaDB or PostgreSQL for networks.");
        }
    }
}
