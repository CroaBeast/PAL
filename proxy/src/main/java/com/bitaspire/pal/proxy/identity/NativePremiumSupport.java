package com.bitaspire.pal.proxy.identity;

import com.bitaspire.pal.proxy.bridge.ProxyBridgeConfig;
import com.bitaspire.pal.proxy.connection.ConnectionRequest;
import com.bitaspire.pal.protocol.proxy.ProxyOptions;
import com.bitaspire.pal.protocol.proxy.ProxyResolver;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

final class NativePremiumSupport {

    private static final long RETRY_MILLIS = 300_000L;

    private final Map<String, IdentityResolver.NativeDecision> decisions = new ConcurrentHashMap<>();
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    @NotNull
    IdentityResolver.NativeDecision prepare(@NotNull String name, @Nullable InetAddress address, @NotNull ProxyBridgeConfig config, boolean skip) {
        if (skip || !config.isNativePremium()) return IdentityResolver.NativeDecision.offline(name);

        String key = key(name);
        // Not cached: only this address gets offline mode, other connections still get verified.
        if (config.isNativeCrackedRetry() && retried(key, address))
            return IdentityResolver.NativeDecision.offline(name);

        IdentityResolver.NativeDecision decision = lookup(name, key, config);
        if (decision.isOnlineMode()) attempts.put(key, new Attempt(System.currentTimeMillis(), address));
        return decision;
    }

    @NotNull
    private IdentityResolver.NativeDecision lookup(@NotNull String name, @NotNull String key, @NotNull ProxyBridgeConfig config) {
        IdentityResolver.NativeDecision cached = decisions.get(key);
        if (cached != null && !cached.isExpired()) return cached;

        try {
            com.bitaspire.pal.protocol.proxy.ProxyDecision proxyDecision = new ProxyResolver(ProxyOptions.builder()
                    .enabled(true)
                    .blockOnError(config.isNativeBlockOnError())
                    .timeoutMillis(config.getNativeTimeoutMillis())
                    .build())
                    .prepare(name)
                    .toCompletableFuture()
                    .join();

            IdentityResolver.NativeDecision decision = IdentityResolver.NativeDecision.from(proxyDecision, name);
            decisions.put(key, decision);
            if (decision.getName() != null) decisions.put(key(decision.getName()), decision);
            return decision;
        } catch (Exception exception) {
            IdentityResolver.NativeDecision decision = config.isNativeBlockOnError()
                    ? IdentityResolver.NativeDecision.block(config.getNativeVerificationFailedMessage())
                    : IdentityResolver.NativeDecision.offline(name);
            decisions.put(key, decision);
            return decision;
        }
    }

    @NotNull
    ConnectionRequest apply(@NotNull ConnectionRequest request) {
        IdentityResolver.NativeDecision decision = decisions.get(key(request.getName()));
        if (decision == null || decision.isExpired()) {
            if (decision != null) decisions.remove(key(request.getName()));
            return request;
        }

        if (!decision.isOnlineMode() || decision.getUniqueId() == null || request.getUniqueId() == null) return request;
        if (!decision.getUniqueId().equals(request.getUniqueId())) return request;

        attempts.remove(key(request.getName()));
        if (decision.getName() != null) attempts.remove(key(decision.getName()));

        return request.toBuilder()
                .name(decision.getName() == null ? request.getName() : decision.getName())
                .uniqueId(decision.getUniqueId())
                .palNativeVerified(true)
                .build();
    }

    private boolean retried(@NotNull String key, @Nullable InetAddress address) {
        Attempt attempt = attempts.remove(key);
        return attempt != null
                && System.currentTimeMillis() - attempt.at <= RETRY_MILLIS
                && Objects.equals(attempt.address, address);
    }

    @NotNull
    private String key(@NotNull String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Attempt {

        private final long at;

        @Nullable
        private final InetAddress address;
    }
}
