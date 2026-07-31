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
final class FloodgateIdentityResolverImpl implements IdentityResolver {

    private final PALApi api;
    private final PremiumOptions options;

    @NotNull
    @Override
    public IdentityProvider getProvider() {
        return IdentityProvider.FLOODGATE;
    }

    @Override
    public boolean isAvailable() {
        return options.isFloodgateEnabled()
                && api.getIntegrationManager().isEnabled(Integration.Type.FLOODGATE);
    }

    @NotNull
    @Override
    public CompletionStage<IdentityResult> resolve(@NotNull IdentityRequest request) {
        if (!isAvailable() || !request.isBedrockVerified() || request.getUniqueId() == null) {
            java.util.Optional<IdentityResult> hooked = hooks().findFloodgateIdentity(request);
            if (hooked.isPresent()) return CompletableFuture.completedFuture(hooked.get());

            return CompletableFuture.completedFuture(IdentityResult.unknown(getProvider(), "Floodgate bedrock identity was not verified"));
        }

        if (request.isLinkedJava()) {
            return CompletableFuture.completedFuture(IdentityResult.verifiedPremium(
                    getProvider(),
                    request.getUniqueId(),
                    request.getName(),
                    IdentityTrust.FLOODGATE
            ));
        }

        return CompletableFuture.completedFuture(IdentityResult.bedrock(
                getProvider(),
                request.getUniqueId(),
                request.getName(),
                IdentityTrust.FLOODGATE
        ));
    }

    private IdentityHookServiceImpl hooks() {
        return ((PALPlugin) api.getPlugin()).getIdentityHookService();
    }
}
