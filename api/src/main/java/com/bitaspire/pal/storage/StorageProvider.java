package com.bitaspire.pal.storage;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletionStage;

public interface StorageProvider {

    @NotNull
    StorageType getType();

    @NotNull
    CompletionStage<Void> connect();

    @NotNull
    CompletionStage<Void> close();
}
