package com.bitaspire.pal.auth;

import org.jetbrains.annotations.NotNull;

public interface PasswordHasher {

    @NotNull
    PasswordHash hash(char @NotNull [] password);

    boolean verify(char @NotNull [] password, @NotNull PasswordHash hash);

    boolean needsRehash(@NotNull PasswordHash hash);
}
