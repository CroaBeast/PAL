package com.bitaspire.pal.proxy.identity;

import com.bitaspire.pal.proxy.bridge.ProxyBridgeConfig;
import com.bitaspire.pal.proxy.connection.ConnectionRequest;
import com.bitaspire.pal.protocol.proxy.ProxyOptions;
import com.bitaspire.pal.protocol.proxy.ProxyResolver;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class NativePremiumSupport {

    private final Map<String, IdentityResolver.NativeDecision> decisions = new ConcurrentHashMap<>();

    @NotNull
    IdentityResolver.NativeDecision prepare(@NotNull String name, @NotNull ProxyBridgeConfig config, boolean skip) {
        if (skip || !config.isNativePremium()) return IdentityResolver.NativeDecision.offline(name);

        String key = key(name);
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

        return request.toBuilder()
                .name(decision.getName() == null ? request.getName() : decision.getName())
                .uniqueId(decision.getUniqueId())
                .palNativeVerified(true)
                .build();
    }

    @NotNull
    private String key(@NotNull String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
