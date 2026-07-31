package com.bitaspire.pal;

import com.bitaspire.pal.auth.PasswordHash;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
final class StoredBackupCode {

    private final long id;

    @NotNull
    private final PasswordHash hash;
}
