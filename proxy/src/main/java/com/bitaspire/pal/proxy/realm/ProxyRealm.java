package com.bitaspire.pal.proxy.realm;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum ProxyRealm {
    AUTH,
    LOBBY,
    LIMBO;

    @NotNull
    public static ProxyRealm fromTarget(
            @NotNull String target,
            @NotNull String auth,
            @NotNull String lobby,
            @NotNull String fallback
    ) {
        if (target.equalsIgnoreCase(auth)) return AUTH;
        if (target.equalsIgnoreCase(fallback)) return LIMBO;
        if (target.equalsIgnoreCase(lobby)) return LOBBY;

        try {
            return valueOf(target.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return LOBBY;
        }
    }
}
