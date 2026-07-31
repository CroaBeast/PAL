package com.bitaspire.pal.proxy;

import com.bitaspire.pal.proxy.connection.ConnectionGuard;
import org.jetbrains.annotations.NotNull;

public interface PALAddon {

    @NotNull
    Platform getPlatform();

    @NotNull
    ConnectionGuard getConnectionGuard();

    void reload();
}
