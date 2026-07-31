package com.bitaspire.pal.protocol.bukkit;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Builder(toBuilder = true)
public final class BukkitOptions {

    private final boolean enabled;
    private final boolean requireLoginPhase;
    private final int timeoutMillis;

    @NotNull
    @Builder.Default
    private final Mode mode = Mode.AUTO;

    @NotNull
    public static BukkitOptions defaults() {
        return builder()
                .requireLoginPhase(true)
                .enabled(false)
                .timeoutMillis(5000)
                .build();
    }

    public enum Mode {
        AUTO,
        PAL_NATIVE,
        PROTOCOL_LIB,
        PACKET_EVENTS,
        DISABLED
    }
}
