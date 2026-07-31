package com.bitaspire.pal.protocol.proxy;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class ProxyDecision {
    @Nullable
    private final UUID uniqueId;

    @Nullable
    private final String name;

    private final boolean onlineMode;
    private final boolean block;

    @Nullable
    private final String reason;

    @NotNull
    public static ProxyDecision online(@NotNull UUID uniqueId, @NotNull String name) {
        return new ProxyDecision(uniqueId, name, true, false, null);
    }

    @NotNull
    public static ProxyDecision offline(@NotNull String name) {
        return new ProxyDecision(null, name, false, false, null);
    }

    @NotNull
    public static ProxyDecision block(@NotNull String reason) {
        return new ProxyDecision(null, null, false, true, reason);
    }
}
