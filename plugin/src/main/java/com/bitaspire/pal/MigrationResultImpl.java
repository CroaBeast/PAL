package com.bitaspire.pal;

import com.bitaspire.pal.migration.MigrationResult;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
final class MigrationResultImpl implements MigrationResult {

    private final boolean successful;
    private final int accounts;
    private final int sessions;
    private final int skipped;
    private final int failed;

    @NotNull
    private final String message;

    @NotNull
    static MigrationResultImpl success(int accounts, int sessions, int skipped, int failed, @NotNull String message) {
        return new MigrationResultImpl(true, accounts, sessions, skipped, failed, message);
    }

    @NotNull
    static MigrationResultImpl failure(@NotNull String message) {
        return new MigrationResultImpl(false, 0, 0, 0, 0, message);
    }
}
