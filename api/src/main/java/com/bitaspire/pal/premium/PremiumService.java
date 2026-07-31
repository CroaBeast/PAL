package com.bitaspire.pal.premium;

import com.bitaspire.pal.identity.IdentityRequest;
import com.bitaspire.pal.identity.IdentityResult;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.util.concurrent.CompletionStage;

public interface PremiumService {

    boolean isFastLoginAvailable();

    boolean isNativeResolverAvailable();

    boolean isBedrockDetectionAvailable();

    @NotNull
    CompletionStage<IdentityResult> resolveIdentity(@NotNull IdentityRequest request);

    @NotNull
    CompletionStage<LookupResult> resolve(@NotNull String name, @NotNull InetAddress address);
}
