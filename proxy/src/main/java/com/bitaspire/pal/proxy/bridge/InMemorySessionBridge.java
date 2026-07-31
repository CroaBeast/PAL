package com.bitaspire.pal.proxy.bridge;

import com.bitaspire.pal.proxy.session.AuthSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

final class InMemorySessionBridge implements SessionBridge {

    private final Map<UUID, AuthSession> sessionsById = new ConcurrentHashMap<>();
    private final Map<String, AuthSession> sessionsByName = new ConcurrentHashMap<>();
    private final Set<SessionBridgeListener> listeners = java.util.Collections.newSetFromMap(new ConcurrentHashMap<SessionBridgeListener, Boolean>());

    @NotNull
    @Override
    public CompletionStage<Optional<AuthSession>> findSession(@Nullable UUID uniqueId, @NotNull String name) {
        AuthSession session = uniqueId == null ? null : sessionsById.get(uniqueId);
        if (session == null) session = sessionsByName.get(key(name));
        if (session != null && session.isExpired(System.currentTimeMillis())) {
            invalidate(session.getUniqueId());
            session = null;
        }

        return CompletableFuture.completedFuture(Optional.ofNullable(session));
    }

    @NotNull
    @Override
    public CompletionStage<Void> saveSession(@NotNull AuthSession session) {
        sessionsById.put(session.getUniqueId(), session);
        sessionsByName.put(key(session.getName()), session);
        for (SessionBridgeListener listener : listeners) listener.onSessionSaved(session);
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    @Override
    public CompletionStage<Void> invalidate(@NotNull UUID uniqueId) {
        AuthSession session = sessionsById.remove(uniqueId);
        if (session != null) sessionsByName.remove(key(session.getName()));
        for (SessionBridgeListener listener : listeners) listener.onSessionInvalidated(uniqueId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void addListener(@NotNull SessionBridgeListener listener) {
        listeners.add(listener);
    }

    private String key(String name) {
        return name.toLowerCase(java.util.Locale.ROOT);
    }
}
