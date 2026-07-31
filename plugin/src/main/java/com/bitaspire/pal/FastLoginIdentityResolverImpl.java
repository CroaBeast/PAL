package com.bitaspire.pal;

import com.bitaspire.pal.identity.IdentityProvider;
import com.bitaspire.pal.identity.IdentityRequest;
import com.bitaspire.pal.identity.IdentityResolver;
import com.bitaspire.pal.identity.IdentityResult;
import com.bitaspire.pal.identity.IdentityTrust;
import com.bitaspire.pal.integration.Integration;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class FastLoginIdentityResolverImpl implements IdentityResolver {

    private final PALApi api;
    private final PremiumOptions options;

    @NotNull
    @Override
    public IdentityProvider getProvider() {
        return IdentityProvider.FAST_LOGIN;
    }

    @Override
    public boolean isAvailable() {
        return options.isFastLoginEnabled()
                && api.getIntegrationManager().isEnabled(Integration.Type.FAST_LOGIN);
    }

    @NotNull
    @Override
    public CompletionStage<IdentityResult> resolve(@NotNull IdentityRequest request) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(IdentityResult.unknown(getProvider(), "FastLogin is not available"));
        }

        if (request.isFastLoginVerified() && request.getUniqueId() != null) {
            return CompletableFuture.completedFuture(IdentityResult.verifiedPremium(
                    getProvider(),
                    request.getUniqueId(),
                    request.getName(),
                    IdentityTrust.FAST_LOGIN
            ));
        }

        java.util.Optional<IdentityResult> hooked = hooks().findFastLoginDecision(request);
        if (hooked.isPresent()) return CompletableFuture.completedFuture(hooked.get());

        return CompletableFuture.completedFuture(IdentityResult.unknown(
                getProvider(),
                "FastLogin is installed, but PAL has not received a verified FastLogin decision"
        ));
    }

    private IdentityHookServiceImpl hooks() {
        return ((PALPlugin) api.getPlugin()).getIdentityHookService();
    }
}
