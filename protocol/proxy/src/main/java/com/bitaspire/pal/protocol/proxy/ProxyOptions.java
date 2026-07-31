package com.bitaspire.pal.protocol.proxy;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Builder(toBuilder = true)
public final class ProxyOptions {

    private final boolean enabled;
    private final boolean blockOnError;
    private final int timeoutMillis;

    @NotNull
    public static ProxyOptions defaults() {
        return builder()
                .enabled(true)
                .blockOnError(false)
                .timeoutMillis(5000)
                .build();
    }
}
