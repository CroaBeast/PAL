package com.bitaspire.pal;

import com.bitaspire.pal.identity.IdentityMode;
import com.bitaspire.pal.identity.IdentityProvider;
import com.bitaspire.pal.identity.IdentityRequest;
import com.bitaspire.pal.identity.IdentityResolver;
import com.bitaspire.pal.identity.IdentityResult;
import com.bitaspire.pal.identity.IdentityTrust;
import com.bitaspire.pal.identity.IdentityType;
import com.bitaspire.pal.bridge.BridgeSession;
import com.bitaspire.pal.protocol.mojang.MojangProfile;
import com.bitaspire.pal.protocol.mojang.MojangProfileClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class NativeIdentityResolverImpl implements IdentityResolver {

    private final PALApi api;
    private final PremiumOptions options;
    private final MojangProfileClient profileClient;

    @NotNull
    @Override
    public IdentityProvider getProvider() {
        return IdentityProvider.PAL_NATIVE;
    }

    @Override
    public boolean isAvailable() {
        return options.isEnabled()
                && options.isNativeResolverEnabled()
                && options.getMode() != IdentityMode.OFFLINE_ONLY;
    }

    @NotNull
    @Override
    public CompletionStage<IdentityResult> resolve(@NotNull IdentityRequest request) {
        if (!isAvailable()) {
            return java.util.concurrent.CompletableFuture.completedFuture(IdentityResult.offline(
                    getProvider(),
                    request.getName(),
                    "PAL native resolver is disabled"
            ));
        }

        return profileClient.findByName(request.getName(), options.getNativeTimeoutMillis())
                .thenCompose(profile -> fromProfile(request, profile))
                .exceptionally(throwable -> handleMojangError(request, throwable));
    }

    @NotNull
    private CompletionStage<IdentityResult> fromProfile(@NotNull IdentityRequest request, @NotNull Optional<MojangProfile> profile) {
        if (!profile.isPresent()) {
            return CompletableFuture.completedFuture(IdentityResult.offline(
                    getProvider(),
                    request.getName(),
                    "No Mojang profile exists for this name"
            ));
        }

        MojangProfile mojangProfile = profile.get();
        if (request.getUniqueId() == null || !request.getUniqueId().equals(mojangProfile.getUniqueId())) {
            return CompletableFuture.completedFuture(claimed(mojangProfile));
        }

        if (options.isBukkitOnlineModeProof() && request.isServerOnlineMode()) {
            return CompletableFuture.completedFuture(IdentityResult.verifiedPremium(
                    getProvider(),
                    mojangProfile.getUniqueId(),
                    mojangProfile.getName(),
                    IdentityTrust.VERIFIED_SESSION
            ));
        }

        return ((BridgeServiceImpl) api.getBridgeService()).findSession(mojangProfile.getUniqueId(), mojangProfile.getName())
                .thenApply(session -> session.isPresent() && isVerifiedProxySession(session.get(), mojangProfile)
                        ? IdentityResult.verifiedPremium(
                        getProvider(),
                        mojangProfile.getUniqueId(),
                        mojangProfile.getName(),
                        IdentityTrust.VERIFIED_SESSION
                )
                        : claimed(mojangProfile));
    }

    @NotNull
    private IdentityResult claimed(@NotNull MojangProfile mojangProfile) {
        return IdentityResult.claimedPremiumName(
                getProvider(),
                mojangProfile.getUniqueId(),
                mojangProfile.getName(),
                "Premium name exists, but ownership still requires session verification"
        );
    }

    private boolean isVerifiedProxySession(@NotNull BridgeSession session, @NotNull MojangProfile profile) {
        return session.getUniqueId().equals(profile.getUniqueId())
                && session.getName().equalsIgnoreCase(profile.getName())
                && session.getIdentityType() == IdentityType.JAVA_PREMIUM
                && session.getIdentityTrust() == IdentityTrust.VERIFIED_SESSION;
    }

    @NotNull
    private IdentityResult handleMojangError(@NotNull IdentityRequest request, @NotNull Throwable throwable) {
        String reason = throwable.getCause() == null ? throwable.getMessage() : throwable.getCause().getMessage();
        if ("BLOCK".equalsIgnoreCase(options.getMojangErrorFallback())) {
            return IdentityResult.unknown(getProvider(), reason);
        }

        return IdentityResult.offline(getProvider(), request.getName(), reason);
    }
}
