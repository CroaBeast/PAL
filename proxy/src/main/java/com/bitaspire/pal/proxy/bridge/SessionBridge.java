package com.bitaspire.pal.proxy.bridge;

import com.bitaspire.pal.proxy.session.AuthSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface SessionBridge {

    @NotNull
    static SessionBridge create(@NotNull ProxyBridgeConfig config) {
        if (!config.isEnabled() || config.isMemory()) return new InMemorySessionBridge();
        if (config.isDatabase()) return new DatabaseSessionBridge(config);
        return config.isRedis() ? new RedisSessionBridge(config) : new InMemorySessionBridge();
    }

    @NotNull
    CompletionStage<Optional<AuthSession>> findSession(@Nullable UUID uniqueId, @NotNull String name);

    @NotNull
    CompletionStage<Void> saveSession(@NotNull AuthSession session);

    @NotNull
    CompletionStage<Void> invalidate(@NotNull UUID uniqueId);

    default void addListener(@NotNull SessionBridgeListener listener) {}

    default void close() {}
}
