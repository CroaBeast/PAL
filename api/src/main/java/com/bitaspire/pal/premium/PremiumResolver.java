package com.bitaspire.pal.premium;

import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.util.concurrent.CompletionStage;

public interface PremiumResolver {

    @NotNull
    PremiumProvider getProvider();

    @NotNull
    CompletionStage<LookupResult> resolve(@NotNull String name, @NotNull InetAddress address);
}
