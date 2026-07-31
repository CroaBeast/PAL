package com.bitaspire.pal.protocol.mojang;

import com.bitaspire.pal.protocol.ProtocolProvider;
import com.bitaspire.pal.protocol.VerificationRequest;
import com.bitaspire.pal.protocol.VerificationResult;
import com.bitaspire.pal.protocol.ProtocolVerifier;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
public final class MojangSessionVerifier implements ProtocolVerifier {

    private final MojangProfileClient client;
    private final int timeoutMillis;

    public MojangSessionVerifier(int timeoutMillis) {
        this(new MojangProfileClient(), timeoutMillis);
    }

    @NotNull
    @Override
    public ProtocolProvider getProvider() {
        return ProtocolProvider.MOJANG_SESSION;
    }

    @NotNull
    @Override
    public CompletionStage<VerificationResult> verify(@NotNull VerificationRequest request) {
        if (request.getServerHash() == null || request.getServerHash().trim().isEmpty()) {
            return CompletableFuture.completedFuture(VerificationResult.unknown(
                    getProvider(),
                    "Missing Minecraft server hash"
            ));
        }

        return client.hasJoined(request.getName(), request.getServerHash(), request.getAddress(), timeoutMillis)
                .thenApply(profile -> fromProfile(request, profile))
                .exceptionally(throwable -> VerificationResult.error(getProvider(), message(throwable)));
    }

    @NotNull
    private VerificationResult fromProfile(
            @NotNull VerificationRequest request,
            @NotNull Optional<MojangProfile> profile
    ) {
        if (!profile.isPresent()) {
            return VerificationResult.offline(
                    getProvider(),
                    "Mojang session server did not confirm this login"
            );
        }

        MojangProfile mojangProfile = profile.get();
        if (request.getUniqueIdHint() != null && !request.getUniqueIdHint().equals(mojangProfile.getUniqueId())) {
            return VerificationResult.blocked(
                    getProvider(),
                    "Login UUID does not match the verified Mojang session"
            );
        }

        return VerificationResult.verified(
                getProvider(),
                mojangProfile.getUniqueId(),
                mojangProfile.getName(),
                "hasJoined"
        );
    }

    private static String message(@NotNull Throwable throwable) {
        Throwable cause = throwable.getCause();
        return cause == null ? throwable.getMessage() : cause.getMessage();
    }
}
