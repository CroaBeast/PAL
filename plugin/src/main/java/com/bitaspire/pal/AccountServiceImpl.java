package com.bitaspire.pal;

import com.bitaspire.pal.account.AccountCreateRequest;
import com.bitaspire.pal.account.AccountNameLock;
import com.bitaspire.pal.account.AccountResult;
import com.bitaspire.pal.account.AccountService;
import com.bitaspire.pal.account.AccountUniqueIds;
import com.bitaspire.pal.account.PALAccount;
import com.bitaspire.pal.event.PALAccountCreateEvent;
import com.bitaspire.pal.event.PALAccountDeleteEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class AccountServiceImpl extends AbstractService implements AccountService {

    AccountServiceImpl(@NotNull PALApi api) {
        super(api);
    }

    @NotNull
    @Override
    public CompletionStage<Optional<PALAccount>> findById(@NotNull UUID uniqueId) {
        return storage().findAccountById(uniqueId);
    }

    @NotNull
    @Override
    public CompletionStage<Optional<PALAccount>> findByName(@NotNull String name) {
        return storage().findAccountByName(name);
    }

    @NotNull
    @Override
    public CompletionStage<AccountResult> create(@NotNull AccountCreateRequest request) {
        UUID uniqueId;

        try {
            uniqueId = AccountUniqueIds.resolve(request.getName(), request.getType(), request.getUniqueId());
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture(AccountResult.invalid("account.invalid-uuid"));
        }

        if (request.getType() == PALAccount.Type.UNKNOWN) {
            return CompletableFuture.completedFuture(AccountResult.invalid("account.invalid-type"));
        }

        PALAccount account = new StoredAccount(
                uniqueId,
                request.getName(),
                request.getType(),
                request.getStatus(),
                request.getAddress(),
                Instant.now(),
                null
        );

        return storage().findAccountByName(request.getName()).thenCompose(existingByName -> {
            if (existingByName.isPresent()) {
                return CompletableFuture.completedFuture(AccountResult.alreadyExists(existingByName.get()));
            }

            return storage().findAccountById(uniqueId).thenCompose(existingById -> {
                if (existingById.isPresent()) {
                    return CompletableFuture.completedFuture(AccountResult.alreadyExists(existingById.get()));
                }

                return createIfUnlocked(request, account);
            });
        }).exceptionally(throwable -> AccountResult.error("account.error"));
    }

    @NotNull
    @Override
    public CompletionStage<AccountResult> update(@NotNull PALAccount account) {
        return storage().findAccountById(account.getUniqueId()).thenCompose(existing -> {
            if (!existing.isPresent()) return CompletableFuture.completedFuture(AccountResult.notFound());

            return storage().saveAccount(account)
                    .thenCompose(ignored -> storage().audit(account.getUniqueId(), account.getName(), "account.update",
                            account.getLastAddress(), account.getType().name()))
                    .thenApply(ignored -> AccountResult.success(account));
        }).exceptionally(throwable -> AccountResult.error("account.error"));
    }

    @NotNull
    @Override
    public CompletionStage<AccountResult> delete(@NotNull UUID uniqueId) {
        return storage().findAccountById(uniqueId).thenCompose(existing -> {
            if (!existing.isPresent()) return CompletableFuture.completedFuture(AccountResult.notFound());

            PALAccount account = existing.get();
            return storage().deleteAccount(uniqueId)
                    .thenCompose(deleted -> storage().deleteNameLock(account.getName()).thenApply(ignored -> deleted))
                    .thenCompose(deleted -> storage().audit(uniqueId, account.getName(), "account.delete",
                            account.getLastAddress(), account.getType().name()))
                    .thenRun(() -> plugin().callEvent(new PALAccountDeleteEvent(account)))
                    .thenApply(ignored -> AccountResult.success(account));
        }).exceptionally(throwable -> AccountResult.error("account.error"));
    }

    @NotNull
    @Override
    public CompletionStage<Optional<AccountNameLock>> findLockByName(@NotNull String name) {
        return storage().findNameLockByName(name);
    }

    @NotNull
    @Override
    public CompletionStage<AccountNameLock> lockName(@NotNull AccountNameLock lock) {
        return storage().saveNameLock(lock);
    }

    @NotNull
    @Override
    public CompletionStage<Boolean> unlockName(@NotNull String name) {
        return storage().deleteNameLock(name);
    }

    @NotNull
    CompletionStage<AccountResult> save(@NotNull PALAccount account) {
        return update(account);
    }

    @NotNull
    private CompletionStage<AccountResult> createIfUnlocked(
            @NotNull AccountCreateRequest request,
            @NotNull PALAccount account
    ) {
        return storage().findNameLockByName(request.getName()).thenCompose(lock -> {
            if (isBlockedByLock(account, lock)) {
                return CompletableFuture.completedFuture(AccountResult.nameLocked(lock.get()));
            }

            AccountNameLock nextLock = request.isLockName() ? createLock(request, account) : null;
            CompletionStage<?> lockStage = nextLock == null
                    ? CompletableFuture.completedFuture(null)
                    : storage().saveNameLock(nextLock);

            return lockStage
                    .thenCompose(ignored -> storage().saveAccount(account))
                    .thenCompose(ignored -> storage().audit(account.getUniqueId(), account.getName(), "account.create",
                            account.getLastAddress(), account.getType().name()))
                    .thenRun(() -> plugin().callEvent(new PALAccountCreateEvent(account)))
                    .thenApply(ignored -> AccountResult.success(account));
        });
    }

    private boolean isBlockedByLock(@NotNull PALAccount account, @NotNull Optional<AccountNameLock> lock) {
        if (!lock.isPresent()) return false;
        AccountNameLock current = lock.get();

        return !current.belongsTo(account.getUniqueId());
    }

    @Nullable
    private AccountNameLock createLock(@NotNull AccountCreateRequest request, @NotNull PALAccount account) {
        switch (account.getType()) {
            case PREMIUM:
                return AccountNameLock.premium(account.getName(), account.getUniqueId(), request.getSource());
            case BEDROCK:
                return AccountNameLock.bedrock(account.getName(), account.getUniqueId(), request.getSource());
            case OFFLINE:
            case UNKNOWN:
            default:
                return null;
        }
    }

    private StorageServiceImpl storage() {
        return (StorageServiceImpl) api.getStorageService();
    }

    private PALPlugin plugin() {
        return (PALPlugin) api.getPlugin();
    }
}
