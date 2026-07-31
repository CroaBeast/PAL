package com.bitaspire.pal.protocol;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletionStage;

public interface ProtocolVerifier {

    @NotNull
    ProtocolProvider getProvider();

    default boolean isAvailable() {
        return true;
    }

    @NotNull
    CompletionStage<VerificationResult> verify(@NotNull VerificationRequest request);
}
