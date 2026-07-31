package com.bitaspire.pal.proxy.identity;

import com.bitaspire.pal.proxy.connection.ConnectionRequest;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

enum NoopIdentityResolver implements IdentityResolver {
    INSTANCE;

    @NotNull
    @Override
    public IdentityProvider getProvider() {
        return IdentityProvider.NONE;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @NotNull
    @Override
    public CompletionStage<IdentityResult> resolve(@NotNull ConnectionRequest request) {
        return CompletableFuture.completedFuture(IdentityResult.unknown(getProvider(), "No proxy identity hook is configured"));
    }
}
