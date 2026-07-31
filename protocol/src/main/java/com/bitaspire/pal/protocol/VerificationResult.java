package com.bitaspire.pal.protocol;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class VerificationResult {
    @NotNull
    private final VerificationResult.Status status;

    @NotNull
    private final ProtocolProvider provider;

    private final boolean verified;
    private final boolean onlineModeRequired;

    @Nullable
    private final ProtocolIdentity identity;

    @Nullable
    private final String reason;

    @NotNull
    public static VerificationResult verified(
            @NotNull ProtocolProvider provider,
            @NotNull UUID uniqueId,
            @NotNull String name,
            @Nullable String source
    ) {
        return new VerificationResult(
                Status.VERIFIED,
                provider,
                true,
                true,
                ProtocolIdentity.builder()
                        .uniqueId(uniqueId)
                        .name(name)
                        .provider(provider)
                        .source(source)
                        .build(),
                null
        );
    }

    @NotNull
    public static VerificationResult claimed(
            @NotNull ProtocolProvider provider,
            @NotNull UUID uniqueId,
            @NotNull String name,
            @Nullable String reason
    ) {
        return new VerificationResult(
                Status.CLAIMED,
                provider,
                false,
                true,
                ProtocolIdentity.builder()
                        .uniqueId(uniqueId)
                        .name(name)
                        .provider(provider)
                        .build(),
                reason
        );
    }

    @NotNull
    public static VerificationResult offline(
            @NotNull ProtocolProvider provider,
            @NotNull String reason
    ) {
        return new VerificationResult(
                Status.OFFLINE,
                provider,
                false,
                false,
                null,
                reason
        );
    }

    @NotNull
    public static VerificationResult blocked(
            @NotNull ProtocolProvider provider,
            @NotNull String reason
    ) {
        return new VerificationResult(
                Status.BLOCKED,
                provider,
                false,
                false,
                null,
                reason
        );
    }

    @NotNull
    public static VerificationResult error(
            @NotNull ProtocolProvider provider,
            @Nullable String reason
    ) {
        return new VerificationResult(
                Status.ERROR,
                provider,
                false,
                false,
                null,
                reason
        );
    }

    @NotNull
    public static VerificationResult unknown(
            @NotNull ProtocolProvider provider,
            @Nullable String reason
    ) {
        return new VerificationResult(
                Status.UNKNOWN,
                provider,
                false,
                false,
                null,
                reason
        );
    }

    public enum Status {
        VERIFIED,
        CLAIMED,
        OFFLINE,
        BLOCKED,
        ERROR,
        UNKNOWN
    }
}
