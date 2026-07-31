package com.bitaspire.pal.proxy.session;

import com.bitaspire.pal.proxy.identity.IdentityTrust;
import com.bitaspire.pal.proxy.identity.IdentityType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Builder(toBuilder = true)
public final class AuthSession {

    @NotNull
    private final UUID uniqueId;
    @NotNull
    private final String name, sessionId;

    @NotNull
    private final IdentityType identityType;
    @NotNull
    private final IdentityTrust identityTrust;

    private final boolean verifiedIdentity;
    private final long authenticatedAtMillis, expiresAtMillis;

    @Nullable
    private final String source, sourceServer, addressHash;

    @NotNull
    public static AuthSession of(
            @NotNull UUID uniqueId,
            @NotNull String name,
            @NotNull String sessionId,
            long expiresAtMillis,
            @Nullable String sourceServer,
            @Nullable String addressHash
    ) {
        return builder()
                .uniqueId(uniqueId)
                .name(name)
                .sessionId(sessionId)
                .identityType(IdentityType.UNKNOWN)
                .identityTrust(IdentityTrust.UNVERIFIED)
                .verifiedIdentity(false)
                .authenticatedAtMillis(System.currentTimeMillis())
                .expiresAtMillis(expiresAtMillis)
                .source(null)
                .sourceServer(sourceServer)
                .addressHash(addressHash)
                .build();
    }

    public boolean isExpired(long nowMillis) {
        return expiresAtMillis > 0L && expiresAtMillis <= nowMillis;
    }
}
