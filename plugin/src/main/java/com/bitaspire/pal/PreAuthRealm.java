package com.bitaspire.pal;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

enum PreAuthRealm {
    AUTO,
    AUTH,
    LOBBY,
    LIMBO;

    @NotNull
    static PreAuthRealm from(@NotNull String value, @NotNull PreAuthRealm fallback) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
