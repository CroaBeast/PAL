package com.bitaspire.pal;

import com.bitaspire.pal.auth.AuthSource;
import com.bitaspire.pal.session.AuthSession;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
final class StoredSession implements AuthSession {

    @NotNull
    private final UUID uniqueId;

    @NotNull
    private final String name;

    @NotNull
    private final String sessionId;

    @NotNull
    private final AuthSource source;

    @Nullable
    private final InetAddress address;

    @Nullable
    private final String addressHash;

    @NotNull
    private final Instant authenticatedAt;

    @Nullable
    private final Instant expiresAt;

    boolean isExpired(long nowMillis) {
        return expiresAt != null && expiresAt.toEpochMilli() <= nowMillis;
    }
}
