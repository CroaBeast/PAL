package com.bitaspire.pal.migration;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletionStage;

public interface MigrationProvider {

    @NotNull
    String getName();

    boolean isAvailable();

    @NotNull
    CompletionStage<MigrationResult> migrate();
}
