package com.bitaspire.pal.proxy.bridge;

import com.bitaspire.pal.proxy.session.AuthSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

final class RedisSessionBridge implements SessionBridge {

    private final RedisClient redis;
    private final SignedBridgeCodec codec;
    private final String prefix;
    private final String channel;
    private final Map<UUID, AuthSession> cacheById = new ConcurrentHashMap<>();
    private final Map<String, UUID> cacheByName = new ConcurrentHashMap<>();
    private final Set<SessionBridgeListener> listeners = java.util.Collections.newSetFromMap(new ConcurrentHashMap<SessionBridgeListener, Boolean>());

    private volatile boolean closed = false;
    private Thread subscriber;

    RedisSessionBridge(@NotNull ProxyBridgeConfig config) {
        this.redis = new RedisClient(config.getRedisUri());
        this.codec = new SignedBridgeCodec(
                config.getSecret(),
                config.isRequireSecret(),
                config.getSkewSeconds() * 1000L
        );
        this.prefix = config.getRedisPrefix();
        this.channel = config.getRedisChannel();
        startSubscriber();
    }

    @NotNull
    @Override
    public CompletionStage<Optional<AuthSession>> findSession(@Nullable UUID uniqueId, @NotNull String name) {
        return CompletableFuture.completedFuture(find(uniqueId, name));
    }

    @NotNull
    @Override
    public CompletionStage<Void> saveSession(@NotNull AuthSession session) {
        return CompletableFuture.runAsync(() -> {
            String wire = codec.encode(session);
            long ttl = ttl(session);

            try {
                redis.set(sessionKey(session.getUniqueId()), wire, ttl);
                redis.set(nameKey(session.getName()), wire, ttl);
                redis.publish(channel, wire);
            } catch (IOException ignored) {
                // The caller will still keep the local cache for this proxy process.
            }

            saveLocal(session, true);
        });
    }

    @NotNull
    @Override
    public CompletionStage<Void> invalidate(@NotNull UUID uniqueId) {
        return CompletableFuture.runAsync(() -> {
            AuthSession previous = cacheById.get(uniqueId);

            try {
                redis.del(sessionKey(uniqueId));
                if (previous != null) redis.del(nameKey(previous.getName()));
                redis.publish(channel, codec.encodeDelete(uniqueId));
            } catch (IOException ignored) {
                // Local invalidation still applies for this proxy process.
            }

            invalidateLocal(uniqueId, true);
        });
    }

    @Override
    public void addListener(@NotNull SessionBridgeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void close() {
        closed = true;
        if (subscriber != null) subscriber.interrupt();
    }

    @NotNull
    private Optional<AuthSession> find(@Nullable UUID uniqueId, @NotNull String name) {
        AuthSession cached = uniqueId == null ? null : cacheById.get(uniqueId);
        if (cached == null) {
            UUID byName = cacheByName.get(key(name));
            if (byName != null) cached = cacheById.get(byName);
        }

        if (cached != null && !cached.isExpired(System.currentTimeMillis())) return Optional.of(cached);

        AuthSession loaded = load(uniqueId == null ? nameKey(name) : sessionKey(uniqueId));
        if (loaded == null && uniqueId != null) loaded = load(nameKey(name));
        if (loaded == null || loaded.isExpired(System.currentTimeMillis())) return Optional.empty();

        saveLocal(loaded, false);
        return Optional.of(loaded);
    }

    @Nullable
    private AuthSession load(@NotNull String key) {
        try {
            Optional<SignedBridgeCodec.Message> message = codec.decode(redis.get(key));
            if (!message.isPresent() || message.get().getType() != SignedBridgeCodec.Message.Type.SESSION) return null;
            return message.get().getSession();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void startSubscriber() {
        subscriber = new Thread(() -> redis.subscribe(channel, this::handleMessage, () -> closed), "PAL Redis Bridge");
        subscriber.setDaemon(true);
        subscriber.start();
    }

    private void handleMessage(@NotNull String wire) {
        Optional<SignedBridgeCodec.Message> decoded = codec.decode(wire);
        if (!decoded.isPresent()) return;

        SignedBridgeCodec.Message message = decoded.get();
        if (message.getType() == SignedBridgeCodec.Message.Type.SESSION && message.getSession() != null) {
            saveLocal(message.getSession(), true);
            return;
        }

        if (message.getUniqueId() != null) invalidateLocal(message.getUniqueId(), true);
    }

    private void saveLocal(@NotNull AuthSession session, boolean notify) {
        cacheById.put(session.getUniqueId(), session);
        cacheByName.put(key(session.getName()), session.getUniqueId());
        if (notify) for (SessionBridgeListener listener : listeners) listener.onSessionSaved(session);
    }

    private void invalidateLocal(@NotNull UUID uniqueId, boolean notify) {
        AuthSession previous = cacheById.remove(uniqueId);
        if (previous != null) cacheByName.remove(key(previous.getName()));
        if (notify) for (SessionBridgeListener listener : listeners) listener.onSessionInvalidated(uniqueId);
    }

    private long ttl(@NotNull AuthSession session) {
        return session.getExpiresAtMillis() <= 0L
                ? 0L
                : Math.max(1L, session.getExpiresAtMillis() - System.currentTimeMillis());
    }

    @NotNull
    private String sessionKey(@NotNull UUID uniqueId) {
        return prefix + "session:" + uniqueId;
    }

    @NotNull
    private String nameKey(@NotNull String name) {
        return prefix + "name:" + key(name);
    }

    @NotNull
    private String key(@NotNull String name) {
        return name.toLowerCase(java.util.Locale.ROOT);
    }
}
