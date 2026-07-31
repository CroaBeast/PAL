package com.bitaspire.pal.proxy.identity;

import com.bitaspire.pal.proxy.bridge.ProxyBridgeConfig;
import com.bitaspire.pal.proxy.connection.ConnectionRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class HookIdentityResolver implements IdentityResolver {

    private final boolean fastLoginAvailable;
    private final boolean floodgateAvailable;
    private final boolean nativeAvailable;
    @Nullable
    private final FastLoginIdentitySupport fastLoginSupport;
    private final NativePremiumSupport nativePremiumSupport = new NativePremiumSupport();

    HookIdentityResolver(boolean fastLoginAvailable, boolean floodgateAvailable, boolean nativeAvailable) {
        this.fastLoginAvailable = fastLoginAvailable;
        this.floodgateAvailable = floodgateAvailable;
        this.nativeAvailable = nativeAvailable;
        this.fastLoginSupport = fastLoginAvailable ? new FastLoginIdentitySupport() : null;
    }

    @NotNull
    @Override
    public IdentityProvider getProvider() {
        if (floodgateAvailable) return IdentityProvider.FLOODGATE;
        if (fastLoginAvailable) return IdentityProvider.FAST_LOGIN;
        if (nativeAvailable) return IdentityProvider.PAL_NATIVE;
        return IdentityProvider.NONE;
    }

    @Override
    public boolean isAvailable() {
        return fastLoginAvailable || floodgateAvailable || nativeAvailable;
    }

    @Override
    public void handleFastLogin(@NotNull Object event) {
        if (fastLoginSupport != null) fastLoginSupport.handle(event);
    }

    @NotNull
    @Override
    public NativeDecision prepareNative(@NotNull String name, @NotNull ProxyBridgeConfig config, boolean skip) {
        return nativePremiumSupport.prepare(name, config, skip);
    }

    @NotNull
    @Override
    public ConnectionRequest enrich(@NotNull ConnectionRequest request) {
        ConnectionRequest current = floodgateAvailable ? FloodgateIdentitySupport.apply(request) : request;
        current = fastLoginSupport == null ? current : fastLoginSupport.apply(current);
        return nativeAvailable ? nativePremiumSupport.apply(current) : current;
    }

    @NotNull
    @Override
    public CompletionStage<IdentityResult> resolve(@NotNull ConnectionRequest request) {
        if (request.isBedrockVerified() && floodgateAvailable && request.getUniqueId() != null) {
            if (request.isLinkedJava()) {
                return CompletableFuture.completedFuture(IdentityResult.verifiedPremium(
                        IdentityProvider.FLOODGATE,
                        request.getUniqueId(),
                        request.getName(),
                        IdentityTrust.FLOODGATE
                ));
            }

            return CompletableFuture.completedFuture(IdentityResult.bedrock(
                    IdentityProvider.FLOODGATE,
                    request.getUniqueId(),
                    request.getName()
            ));
        }

        if (request.isFastLoginVerified() && fastLoginAvailable && request.getUniqueId() != null) {
            return CompletableFuture.completedFuture(IdentityResult.verifiedPremium(
                    IdentityProvider.FAST_LOGIN,
                    request.getUniqueId(),
                    request.getName(),
                    IdentityTrust.FAST_LOGIN
            ));
        }

        if (request.isPalNativeVerified() && nativeAvailable && request.getUniqueId() != null) {
            return CompletableFuture.completedFuture(IdentityResult.verifiedPremium(
                    IdentityProvider.PAL_NATIVE,
                    request.getUniqueId(),
                    request.getName(),
                    IdentityTrust.VERIFIED_SESSION
            ));
        }

        return CompletableFuture.completedFuture(IdentityResult.unknown(
                getProvider(),
                "No verified proxy identity decision was supplied"
        ));
    }
}
