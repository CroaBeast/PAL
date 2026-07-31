package com.bitaspire.pal.bridge;

import com.bitaspire.pal.auth.AuthSource;
import com.bitaspire.pal.identity.IdentityTrust;
import com.bitaspire.pal.identity.IdentityType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true) @Getter
public final class BridgeSession {
    @NotNull
    private final UUID uniqueId;
    @NotNull
    private final String name;
    @NotNull
    private final String sessionId;
    @NotNull
    private final AuthSource source;
    @NotNull
    private final IdentityType identityType;
    @NotNull
    private final IdentityTrust identityTrust;
    @NotNull
    private final Instant authenticatedAt;

    @Nullable
    private final Instant expiresAt;

    @Nullable
    private final String addressHash;
}
