package com.bitaspire.pal.bridge;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface BridgeService {

    boolean isEnabled();

    @NotNull
    Mode getMode();

    @NotNull
    CompletionStage<Void> publishSession(@NotNull BridgeSession session);

    @NotNull
    CompletionStage<Void> invalidateSession(@NotNull UUID uniqueId);

    enum Mode {
        DISABLED,
        MEMORY,
        DATABASE,
        STORAGE,
        REDIS,
        PLUGIN_MESSAGE;

        public static Mode from(String value) {
            if (value == null) return DISABLED;

            String normalized = value.trim().toUpperCase().replace('-', '_');
            if ("DB".equals(normalized) || "SQL".equals(normalized) || "STORAGE".equals(normalized))
                return DATABASE;

            if ("IN_MEMORY".equals(normalized) || "LOCAL".equals(normalized)) return MEMORY;

            try {
                return valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return DISABLED;
            }
        }
    }
}
