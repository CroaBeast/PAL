package com.bitaspire.pal.proxy.connection;

import com.bitaspire.pal.proxy.bridge.BridgeOptions;
import com.bitaspire.pal.proxy.bridge.SessionBridge;
import com.bitaspire.pal.proxy.identity.IdentityResolver;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletionStage;

public interface ConnectionGuard {

    @NotNull
    static ConnectionGuard create(@NotNull SessionBridge bridge, @NotNull BridgeOptions options) {
        return new DefaultConnectionGuard(bridge, IdentityResolver.noop(), options);
    }

    @NotNull
    static ConnectionGuard create(
            @NotNull SessionBridge bridge,
            @NotNull IdentityResolver identityResolver,
            @NotNull BridgeOptions options
    ) {
        return new DefaultConnectionGuard(bridge, identityResolver, options);
    }

    @NotNull
    CompletionStage<ConnectionDecision> validate(@NotNull ConnectionRequest request);
}
