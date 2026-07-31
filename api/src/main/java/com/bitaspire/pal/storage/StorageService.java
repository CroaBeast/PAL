package com.bitaspire.pal.storage;

import org.jetbrains.annotations.NotNull;

public interface StorageService {

    @NotNull
    StorageType getType();

    boolean isConnected();
}
