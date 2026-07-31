package com.bitaspire.pal;

import com.bitaspire.pal.auth.PasswordHash;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
final class StoredPasswordHash implements PasswordHash {

    @NotNull
    private final String algorithm;

    @NotNull
    private final String encoded;

    private final int version;
}
