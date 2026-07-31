package com.bitaspire.pal.auth;

import org.jetbrains.annotations.NotNull;

public interface PasswordHash {

    @NotNull
    String getAlgorithm();

    @NotNull
    String getEncoded();

    int getVersion();
}
