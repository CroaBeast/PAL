package com.bitaspire.pal.account;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class AccountResult {

    @NotNull
    private final Type type;

    @Nullable
    private final PALAccount account;

    @Nullable
    private final AccountNameLock lock;

    @Nullable
    private final String messageKey;

    @NotNull
    public static AccountResult success(@NotNull PALAccount account) {
        return new AccountResult(Type.SUCCESS, account, null, "account.success");
    }

    @NotNull
    public static AccountResult alreadyExists(@NotNull PALAccount account) {
        return new AccountResult(Type.ALREADY_EXISTS, account, null, "account.already-exists");
    }

    @NotNull
    public static AccountResult nameLocked(@NotNull AccountNameLock lock) {
        return new AccountResult(Type.NAME_LOCKED, null, lock, "account.name-locked");
    }

    @NotNull
    public static AccountResult notFound() {
        return new AccountResult(Type.NOT_FOUND, null, null, "account.not-found");
    }

    @NotNull
    public static AccountResult invalid(@Nullable String messageKey) {
        return new AccountResult(Type.INVALID_REQUEST, null, null, messageKey == null ? "account.invalid" : messageKey);
    }

    @NotNull
    public static AccountResult error(@Nullable String messageKey) {
        return new AccountResult(Type.ERROR, null, null, messageKey == null ? "account.error" : messageKey);
    }

    public boolean isSuccessful() {
        return type == Type.SUCCESS;
    }

    public enum Type {
        SUCCESS,
        ALREADY_EXISTS,
        NAME_LOCKED,
        NOT_FOUND,
        INVALID_REQUEST,
        ERROR
    }
}
