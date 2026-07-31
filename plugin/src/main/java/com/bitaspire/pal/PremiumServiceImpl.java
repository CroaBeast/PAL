package com.bitaspire.pal;

import com.bitaspire.pal.identity.IdentityProvider;
import com.bitaspire.pal.identity.IdentityRequest;
import com.bitaspire.pal.identity.IdentityResolver;
import com.bitaspire.pal.identity.IdentityResult;
import com.bitaspire.pal.identity.IdentityType;
import com.bitaspire.pal.integration.Integration;
import com.bitaspire.pal.premium.LookupResult;
import com.bitaspire.pal.premium.PremiumProvider;
import com.bitaspire.pal.premium.PremiumService;
import com.bitaspire.pal.protocol.mojang.MojangProfileClient;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class PremiumServiceImpl extends AbstractService implements PremiumService {

    private final MojangProfileClient profileClient = new MojangProfileClient();
    private PremiumOptions options;
    private List<IdentityResolver> resolvers = Collections.emptyList();

    PremiumServiceImpl(@NotNull PALApi api) {
        super(api);
    }

    @Override
    public boolean register() {
        super.register();
        reload();
        return true;
    }

    void reload() {
        options = PremiumOptions.load((PALPlugin) api.getPlugin());

        List<IdentityResolver> nextResolvers = new ArrayList<>();
        nextResolvers.add(new FloodgateIdentityResolverImpl(api, options));
        nextResolvers.add(new FastLoginIdentityResolverImpl(api, options));
        nextResolvers.add(new NativeIdentityResolverImpl(api, options, profileClient));

        resolvers = Collections.unmodifiableList(nextResolvers);
    }

    @NotNull
    PremiumOptions options() {
        if (options == null) reload();
        return options;
    }

    @Override
    public boolean isFastLoginAvailable() {
        return api.getIntegrationManager().isEnabled(Integration.Type.FAST_LOGIN);
    }

    @Override
    public boolean isNativeResolverAvailable() {
        return options != null && options.isNativeResolverEnabled();
    }

    @Override
    public boolean isBedrockDetectionAvailable() {
        return options != null
                && options.isFloodgateEnabled()
                && api.getIntegrationManager().isEnabled(Integration.Type.FLOODGATE);
    }

    @NotNull
    @Override
    public CompletionStage<IdentityResult> resolveIdentity(@NotNull IdentityRequest request) {
        if (options == null) reload();

        CompletableFuture<IdentityResult> chain = CompletableFuture.completedFuture(
                IdentityResult.unknown(IdentityProvider.NONE, "No resolver has handled this identity yet")
        );

        for (IdentityResolver resolver : resolvers) {
            if (!resolver.isAvailable()) continue;

            chain = chain.thenCompose(current -> {
                if (current.isDecisive()) return CompletableFuture.completedFuture(current);

                return resolver.resolve(request)
                        .exceptionally(throwable -> IdentityResult.unknown(
                                resolver.getProvider(),
                                throwable.getCause() == null ? throwable.getMessage() : throwable.getCause().getMessage()
                        ));
            }).toCompletableFuture();
        }

        return chain.thenApply(result -> result.isDecisive()
                ? result
                : IdentityResult.offline(IdentityProvider.PAL_NATIVE, request.getName(), result.getReason()));
    }

    @NotNull
    @Override
    public CompletionStage<LookupResult> resolve(@NotNull String name, @NotNull InetAddress address) {
        return resolveIdentity(IdentityRequest.of(name, address)).thenApply(identity -> {
            PremiumProvider provider = toPremiumProvider(identity.getProvider());

            if (identity.getType() == IdentityType.JAVA_PREMIUM
                    && identity.isVerified()
                    && identity.getUniqueId() != null
                    && identity.getName() != null) {
                return LookupResult.premium(provider, identity.getUniqueId(), identity.getName());
            }

            if (identity.isPremiumNameExists()) {
                return LookupResult.offline(provider, "Premium name exists, but ownership was not verified");
            }

            return LookupResult.offline(provider, identity.getReason());
        });
    }

    private PremiumProvider toPremiumProvider(IdentityProvider provider) {
        switch (provider) {
            case FAST_LOGIN:
                return PremiumProvider.FAST_LOGIN;
            case MANUAL:
                return PremiumProvider.MANUAL;
            case PAL_NATIVE:
            case FLOODGATE:
                return PremiumProvider.PAL;
            case NONE:
            default:
                return PremiumProvider.NONE;
        }
    }
}
