package com.bitaspire.pal.protocol.proxy;

import com.bitaspire.pal.protocol.mojang.MojangProfile;
import com.bitaspire.pal.protocol.mojang.MojangProfileClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
public final class ProxyResolver {

    private final MojangProfileClient client;
    private final ProxyOptions options;

    public ProxyResolver(@NotNull ProxyOptions options) {
        this(new MojangProfileClient(), options);
    }

    @NotNull
    public CompletionStage<ProxyDecision> prepare(@NotNull String name) {
        if (!options.isEnabled())
            return CompletableFuture.completedFuture(ProxyDecision.offline(name));

        return client.findByName(name, options.getTimeoutMillis())
                .thenApply(profile -> fromProfile(name, profile))
                .exceptionally(throwable -> !options.isBlockOnError()
                        ? ProxyDecision.offline(name)
                        : ProxyDecision.block(message(throwable)));
    }

    @NotNull
    private static ProxyDecision fromProfile(@NotNull String name, @NotNull Optional<MojangProfile> profile) {
        if (!profile.isPresent()) return ProxyDecision.offline(name);

        MojangProfile mojangProfile = profile.get();
        return ProxyDecision.online(mojangProfile.getUniqueId(), mojangProfile.getName());
    }

    private static String message(@NotNull Throwable throwable) {
        Throwable cause = throwable.getCause();
        String message = cause == null ? throwable.getMessage() : cause.getMessage();
        return message == null ? "Mojang lookup failed" : message;
    }
}
