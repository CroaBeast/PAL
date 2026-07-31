package com.bitaspire.pal.identity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class IdentityResult {
    @NotNull
    private final IdentityProvider provider;
    @NotNull
    private final IdentityType type;
    @NotNull
    private final IdentityTrust trust;

    private final boolean verified;
    private final boolean premiumNameExists;
    private final boolean autoLoginAllowed;
    private final boolean passwordRequired;

    @Nullable
    private final UUID uniqueId;

    @Nullable
    private final String name;

    @Nullable
    private final String reason;

    @NotNull
    public static IdentityResult verifiedPremium(
            @NotNull IdentityProvider provider,
            @NotNull UUID uniqueId,
            @NotNull String name,
            @NotNull IdentityTrust trust
    ) {
        return new IdentityResult(provider, IdentityType.JAVA_PREMIUM, trust,
                true, true, true, false, uniqueId, name, null);
    }

    @NotNull
    public static IdentityResult claimedPremiumName(
            @NotNull IdentityProvider provider,
            @Nullable UUID uniqueId,
            @NotNull String name,
            @Nullable String reason
    ) {
        return new IdentityResult(provider, IdentityType.JAVA_PREMIUM, IdentityTrust.NAME_LOOKUP,
                false, true, false, true, uniqueId, name, reason);
    }

    @NotNull
    public static IdentityResult offline(
            @NotNull IdentityProvider provider,
            @NotNull String name,
            @Nullable String reason
    ) {
        return new IdentityResult(provider, IdentityType.JAVA_OFFLINE, IdentityTrust.UNVERIFIED,
                false, false, false, true, null, name, reason);
    }

    @NotNull
    public static IdentityResult bedrock(
            @NotNull IdentityProvider provider,
            @Nullable UUID uniqueId,
            @NotNull String name,
            @NotNull IdentityTrust trust
    ) {
        return new IdentityResult(provider, IdentityType.BEDROCK, trust,
                true, false, true, false, uniqueId, name, null);
    }

    @NotNull
    public static IdentityResult unknown(@NotNull IdentityProvider provider, @Nullable String reason) {
        return new IdentityResult(provider, IdentityType.UNKNOWN, IdentityTrust.UNVERIFIED,
                false, false, false, true, null, null, reason);
    }

    public boolean isAutoLoginEligible() {
        return verified && autoLoginAllowed;
    }

    public boolean isDecisive() {
        return type != IdentityType.UNKNOWN;
    }
}
