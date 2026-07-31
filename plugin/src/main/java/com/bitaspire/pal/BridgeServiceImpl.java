package com.bitaspire.pal;

import com.bitaspire.pal.bridge.BridgeService;
import com.bitaspire.pal.bridge.BridgeSession;
import com.bitaspire.pal.storage.StorageType;
import lombok.Getter;
import me.croabeast.file.ConfigurableFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

final class BridgeServiceImpl extends AbstractService implements BridgeService {

    private final Map<UUID, BridgeSession> publishedSessions = new ConcurrentHashMap<>();

    @Getter
    private boolean enabled = false;

    @Getter
    @NotNull
    private BridgeService.Mode mode = Mode.DISABLED;

    private boolean sqliteAdvice = true;
    private int sqliteCap = 4;
    private int servers = 1;
    private boolean warnedSqliteNetwork = false;
    private boolean requireSecret = true;
    private String secret = "change-me";
    private String authServer = "auth";
    private String redisUri = "redis://localhost:6379/0";
    private String redisPrefix = "pal:";
    private String redisChannel = "pal:auth";
    private BridgeRedisClient redisClient;
    private BridgeSessionWire wire;
    private ExecutorService executor;
    private boolean backendGuard = false;
    private Set<String> proxyAddresses = new HashSet<>();

    BridgeServiceImpl(@NotNull PALApi api) {
        super(api);
    }

    @Override
    public boolean register() {
        super.register();
        reload();
        return true;
    }

    void reload() {
        PALPlugin plugin = (PALPlugin) api.getPlugin();

        try {
            ConfigurableFile file = new ConfigurableFile(plugin, "bridge");
            file.saveDefaults();

            enabled = bool(file, "bridge.enabled", false);
            mode = enabled ? Mode.from(string(file, "bridge.mode", "REDIS")) : Mode.DISABLED;
            servers = integer(file, "bridge.net.servers", integer(file, "bridge.network.servers", 1));
            sqliteAdvice = bool(file, "bridge.advice.sqlite", true);
            sqliteCap = integer(file, "bridge.advice.sqlite-cap", 4);
            authServer = string(file, "bridge.net.auth", "auth");
            requireSecret = bool(file, "bridge.sec.require", bool(file, "bridge.sec.require-secret", true));
            secret = string(file, "bridge.sec.secret", "change-me");
            redisUri = string(file, "bridge.redis.uri", "redis://localhost:6379/0");
            redisPrefix = string(file, "bridge.redis.prefix", "pal:");
            redisChannel = string(file, "bridge.redis.channel", "pal:auth");
            backendGuard = bool(file, "bridge.backend.guard", false);
            proxyAddresses = strings(file.get("bridge.backend.proxy-ips", java.util.Arrays.asList("127.0.0.1", "::1")));
            redisClient = mode == Mode.REDIS ? new BridgeRedisClient(redisUri) : null;
            wire = mode == Mode.REDIS || mode == Mode.DATABASE ? new BridgeSessionWire(secret) : null;

            if (executor == null || executor.isShutdown()) {
                executor = Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "PAL Bridge");
                    thread.setDaemon(true);
                    return thread;
                });
            }

            if (!enabled) publishedSessions.clear();
            warnIfSQLiteNetwork(plugin);
            warnIfSecretInvalid(plugin);
            warnIfUnsupportedMode(plugin);
        } catch (Exception exception) {
            enabled = false;
            mode = Mode.DISABLED;
            publishedSessions.clear();
            redisClient = null;
            wire = null;
            plugin.getLogger().warning("Could not load bridge.yml: " + exception.getMessage());
        }
    }

    @Override
    public boolean unregister() {
        publishedSessions.clear();
        if (executor != null) executor.shutdownNow();
        executor = null;
        return super.unregister();
    }

    @NotNull
    @Override
    public CompletionStage<Void> publishSession(@NotNull BridgeSession session) {
        if (!enabled) return CompletableFuture.completedFuture(null);

        publishedSessions.put(session.getUniqueId(), session);
        if (mode != Mode.REDIS) return CompletableFuture.completedFuture(null);
        if (!canUseSignedBridge()) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            try {
                String payload = wire.encode(session, authServer);
                long ttl = ttl(session);

                redisClient.set(sessionKey(session.getUniqueId()), payload, ttl);
                redisClient.set(nameKey(session.getName()), payload, ttl);
                redisClient.publish(redisChannel, payload);
            } catch (IOException exception) {
                plugin().getLogger().log(Level.WARNING, "Could not publish PAL bridge session: " + exception.getMessage());
            }
        }, executor);
    }

    @NotNull
    @Override
    public CompletionStage<Void> invalidateSession(@NotNull UUID uniqueId) {
        if (!enabled) return CompletableFuture.completedFuture(null);
        BridgeSession previous = publishedSessions.remove(uniqueId);
        if (mode != Mode.REDIS) return CompletableFuture.completedFuture(null);
        if (!canUseSignedBridge()) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            try {
                redisClient.del(sessionKey(uniqueId));
                if (previous != null) redisClient.del(nameKey(previous.getName()));
                redisClient.publish(redisChannel, wire.encodeDelete(uniqueId));
            } catch (IOException exception) {
                plugin().getLogger().log(Level.WARNING, "Could not invalidate PAL bridge session: " + exception.getMessage());
            }
        }, executor);
    }

    @NotNull
    Optional<BridgeSession> getPublishedSession(@NotNull UUID uniqueId) {
        return Optional.ofNullable(publishedSessions.get(uniqueId));
    }

    @Nullable
    String encodeSession(@NotNull BridgeSession session) {
        if (!enabled || wire == null || !canUseSignedBridge()) return null;
        return wire.encode(session, authServer);
    }

    @NotNull
    CompletionStage<Optional<BridgeSession>> findSession(@NotNull UUID uniqueId, @NotNull String name) {
        if (!enabled || !canUseSignedBridge()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        if (mode == Mode.DATABASE) {
            return ((StorageServiceImpl) api.getStorageService()).findBridgePayload(uniqueId, name)
                    .thenApply(payload -> payload.flatMap(wire::decodeSession))
                    .exceptionally(throwable -> {
                        plugin().getLogger().log(Level.WARNING, "Could not read PAL database bridge session: " + rootMessage(throwable));
                        return Optional.empty();
                    });
        }

        if (mode != Mode.REDIS) return CompletableFuture.completedFuture(Optional.empty());

        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<BridgeSession> byId = wire.decodeSession(redisClient.get(sessionKey(uniqueId)));
                if (byId.isPresent()) return byId;

                return wire.decodeSession(redisClient.get(nameKey(name)));
            } catch (IOException exception) {
                plugin().getLogger().log(Level.WARNING, "Could not read PAL bridge session: " + exception.getMessage());
                return Optional.empty();
            }
        }, executor);
    }

    @NotNull
    String authServer() {
        return authServer;
    }

    boolean blocksDirectBackend(@NotNull InetAddress address) {
        return backendGuard && !proxyAddresses.contains(address.getHostAddress().toLowerCase(java.util.Locale.ROOT));
    }

    private void warnIfSQLiteNetwork(@NotNull PALPlugin plugin) {
        if (!enabled || !sqliteAdvice || warnedSqliteNetwork) return;
        if (api.getStorageService().getType() != StorageType.SQLITE) return;
        if (servers < sqliteCap) return;

        warnedSqliteNetwork = true;
        plugin.getLogger().warning("PAL is using SQLite in network mode with " + servers + " configured servers.");
        plugin.getLogger().warning("SQLite is fine for a limited auth-server setup, but shared SQL + Redis is recommended for " + sqliteCap + "+ servers.");
    }

    private void warnIfSecretInvalid(@NotNull PALPlugin plugin) {
        if (!enabled || mode != Mode.REDIS || canUseSignedBridge()) return;
        plugin.getLogger().warning("PAL bridge Redis mode requires a non-default bridge.sec.secret shared with the proxy.");
    }

    private void warnIfUnsupportedMode(@NotNull PALPlugin plugin) {
        if (!enabled || mode == Mode.REDIS || mode == Mode.DATABASE || mode == Mode.DISABLED) return;
        if (mode == Mode.MEMORY) {
            plugin.getLogger().warning("PAL bridge MEMORY mode is local-only. Use DATABASE or REDIS for Bukkit <-> proxy sessions.");
            return;
        }

        plugin.getLogger().warning("PAL bridge mode " + mode + " is not available in this build. Use DATABASE or REDIS for Bukkit <-> proxy sessions.");
    }

    private boolean canUseSignedBridge() {
        return !requireSecret || (!secret.trim().isEmpty() && !"change-me".equalsIgnoreCase(secret.trim()));
    }

    private long ttl(@NotNull BridgeSession session) {
        return session.getExpiresAt() == null
                ? 0L
                : Math.max(1L, session.getExpiresAt().toEpochMilli() - System.currentTimeMillis());
    }

    @NotNull
    private String sessionKey(@NotNull UUID uniqueId) {
        return redisPrefix + "session:" + uniqueId;
    }

    @NotNull
    private String nameKey(@NotNull String name) {
        return redisPrefix + "name:" + name.toLowerCase(java.util.Locale.ROOT);
    }

    @NotNull
    private PALPlugin plugin() {
        return (PALPlugin) api.getPlugin();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage();
    }

    private static boolean bool(ConfigurableFile file, String path, boolean defaultValue) {
        Object value = file.get(path, defaultValue);
        return value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(String.valueOf(value));
    }

    private static int integer(ConfigurableFile file, String path, int defaultValue) {
        Object value = file.get(path, defaultValue);
        if (value instanceof Number) return Math.max(0, ((Number) value).intValue());

        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String string(ConfigurableFile file, String path, String defaultValue) {
        Object value = file.get(path, defaultValue);
        return value == null ? defaultValue : String.valueOf(value);
    }

    @NotNull
    private static Set<String> strings(Object value) {
        Set<String> result = new HashSet<>();
        if (value instanceof Collection) {
            for (Object entry : (Collection<?>) value) {
                if (entry != null && !String.valueOf(entry).trim().isEmpty()) {
                    result.add(String.valueOf(entry).trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
            return result;
        }

        if (value != null) {
            for (String entry : String.valueOf(value).split(",")) {
                if (!entry.trim().isEmpty()) result.add(entry.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }

        return result;
    }
}
