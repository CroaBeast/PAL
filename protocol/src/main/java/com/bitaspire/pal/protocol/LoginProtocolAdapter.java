package com.bitaspire.pal.protocol;

import org.jetbrains.annotations.NotNull;

public interface LoginProtocolAdapter {

    @NotNull
    ProtocolPlatform getPlatform();

    boolean supportsLoginPhase();

    void start();

    void stop();
}
