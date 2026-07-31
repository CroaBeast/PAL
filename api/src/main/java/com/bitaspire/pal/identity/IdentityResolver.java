package com.bitaspire.pal.identity;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletionStage;

public interface IdentityResolver {

    @NotNull
    IdentityProvider getProvider();

    boolean isAvailable();

    @NotNull
    CompletionStage<IdentityResult> resolve(@NotNull IdentityRequest request);
}
