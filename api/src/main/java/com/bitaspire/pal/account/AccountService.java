package com.bitaspire.pal.account;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface AccountService {

    @NotNull
    CompletionStage<Optional<PALAccount>> findById(@NotNull UUID uniqueId);

    @NotNull
    CompletionStage<Optional<PALAccount>> findByName(@NotNull String name);

    @NotNull
    CompletionStage<AccountResult> create(@NotNull AccountCreateRequest request);

    @NotNull
    CompletionStage<AccountResult> update(@NotNull PALAccount account);

    @NotNull
    CompletionStage<AccountResult> delete(@NotNull UUID uniqueId);

    @NotNull
    CompletionStage<Optional<AccountNameLock>> findLockByName(@NotNull String name);

    @NotNull
    CompletionStage<AccountNameLock> lockName(@NotNull AccountNameLock lock);

    @NotNull
    CompletionStage<Boolean> unlockName(@NotNull String name);
}
