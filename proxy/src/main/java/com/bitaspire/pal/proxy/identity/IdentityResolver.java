package com.bitaspire.pal.proxy.identity;

import com.bitaspire.pal.proxy.bridge.ProxyBridgeConfig;
import com.bitaspire.pal.proxy.connection.ConnectionRequest;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface IdentityResolver {

    @NotNull
    static IdentityResolver create(boolean fastLoginAvailable, boolean floodgateAvailable, boolean nativeAvailable) {
        return new HookIdentityResolver(fastLoginAvailable, floodgateAvailable, nativeAvailable);
    }

    @NotNull
    static IdentityResolver noop() {
        return NoopIdentityResolver.INSTANCE;
    }

    @NotNull
    IdentityProvider getProvider();

    boolean isAvailable();

    default void handleFastLogin(@NotNull Object event) {}

    @NotNull
    default NativeDecision prepareNative(@NotNull String name, @NotNull ProxyBridgeConfig config, boolean skip) {
        return NativeDecision.offline(name);
    }

    @NotNull
    default ConnectionRequest enrich(@NotNull ConnectionRequest request) {
        return request;
    }

    @NotNull
    CompletionStage<IdentityResult> resolve(@NotNull ConnectionRequest request);

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class NativeDecision {

        private static final long TTL = 120_000L;

        @Nullable
        private final UUID uniqueId;

        @Nullable
        private final String name;

        private final boolean onlineMode;
        private final boolean block;

        @Nullable
        private final String reason;

        private final long expiresAt;

        @NotNull
        static NativeDecision from(@NotNull com.bitaspire.pal.protocol.proxy.ProxyDecision decision, @NotNull String fallbackName) {
            return new NativeDecision(
                    decision.getUniqueId(),
                    decision.getName() == null ? fallbackName : decision.getName(),
                    decision.isOnlineMode(),
                    decision.isBlock(),
                    decision.getReason(),
                    System.currentTimeMillis() + TTL
            );
        }

        @NotNull
        static NativeDecision offline(@NotNull String name) {
            return new NativeDecision(null, name, false, false, null, System.currentTimeMillis() + TTL);
        }

        @NotNull
        static NativeDecision block() {
            return new NativeDecision(null, null, false, true, "Could not verify Mojang ownership", System.currentTimeMillis() + 10_000L);
        }

        @NotNull
        static NativeDecision block(@NotNull String reason) {
            return new NativeDecision(null, null, false, true, reason, System.currentTimeMillis() + 10_000L);
        }

        public boolean isExpired() {
            return expiresAt <= System.currentTimeMillis();
        }
    }
}
