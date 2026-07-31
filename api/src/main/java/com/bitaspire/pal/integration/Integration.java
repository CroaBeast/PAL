package com.bitaspire.pal.integration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

public interface Integration {

    @NotNull
    Type getType();

    @NotNull
    State getState();

    boolean isEnabled();

    enum State {
        ENABLED,
        DISABLED,
        MISSING,
        FAILED
    }

    @RequiredArgsConstructor
    @Getter
    enum Type {
        FAST_LOGIN("FastLogin"),
        PLACEHOLDER_API("PlaceholderAPI"),
        LUCK_PERMS("LuckPerms"),
        FLOODGATE("floodgate"),
        GEYSER("Geyser-Spigot"),
        AUTH_ME("AuthMe"),
        N_LOGIN("nLogin"),
        OPEN_LOGIN("OpeNLogin"),
        LIBRE_LOGIN("LibreLogin");

        private final String id;
    }
}
