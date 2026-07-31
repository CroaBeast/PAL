package com.bitaspire.pal.proxy.identity;

import com.bitaspire.pal.proxy.connection.ConnectionRequest;
import com.github.games647.fastlogin.core.shared.LoginSession;
import com.github.games647.fastlogin.core.shared.event.FastLoginAutoLoginEvent;
import com.github.games647.fastlogin.core.shared.event.FastLoginPreLoginEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class FastLoginIdentitySupport {

    private final Map<String, CachedDecision> decisions = new ConcurrentHashMap<>();

    void handle(@NotNull Object event) {
        if (event instanceof FastLoginAutoLoginEvent) {
            handleAutoLogin((FastLoginAutoLoginEvent) event);
            return;
        }

        if (event instanceof FastLoginPreLoginEvent) handlePreLogin((FastLoginPreLoginEvent) event);
    }

    @NotNull
    ConnectionRequest apply(@NotNull ConnectionRequest request) {
        CachedDecision decision = decisions.get(key(request.getName()));
        if (decision == null || decision.isExpired()) {
            if (decision != null) decisions.remove(key(request.getName()));
            return request;
        }

        if (!decision.isVerified() || decision.getUniqueId() == null) return request;

        return request.toBuilder()
                .name(decision.getName())
                .uniqueId(decision.getUniqueId())
                .fastLoginVerified(true)
                .build();
    }

    private void handleAutoLogin(@NotNull FastLoginAutoLoginEvent event) {
        if (event.isCancelled()) return;

        LoginSession session = event.getSession();
        if (session == null) return;

        String name = firstNonBlank(session.getUsername(), session.getRequestUsername());
        if (name == null) return;

        StoredProfile profile = firstProfile(event.getProfile(), session.getProfile());
        UUID uniqueId = firstUniqueId(session.getUuid(), profile == null ? null : profile.getId());
        cache(name, uniqueId, uniqueId != null && profile != null && profile.isPremium());
    }

    private void handlePreLogin(@NotNull FastLoginPreLoginEvent event) {
        String name = blankToNull(event.getUsername());
        StoredProfile profile = event.getProfile();
        if (name == null || profile == null || profile.isOnlinemodePreferred()) return;

        cache(name, null, false);
    }

    private void cache(@NotNull String name, @Nullable UUID uniqueId, boolean verified) {
        decisions.put(key(name), new CachedDecision(name, uniqueId, verified, System.currentTimeMillis() + 120_000L));
    }

    @Nullable
    private StoredProfile firstProfile(@Nullable StoredProfile first, @Nullable StoredProfile second) {
        return first == null ? second : first;
    }

    @Nullable
    private UUID firstUniqueId(@Nullable UUID first, @Nullable UUID second) {
        return first == null ? second : first;
    }

    @Nullable
    private String firstNonBlank(@Nullable String first, @Nullable String second) {
        String value = blankToNull(first);
        return value == null ? blankToNull(second) : value;
    }

    @Nullable
    private String blankToNull(@Nullable String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    @NotNull
    private String key(@NotNull String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    private static final class CachedDecision {

        @NotNull
        private final String name;

        @Nullable
        private final UUID uniqueId;

        private final boolean verified;
        private final long expiresAt;

        private boolean isExpired() {
            return expiresAt <= System.currentTimeMillis();
        }
    }
}
