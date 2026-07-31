package com.bitaspire.pal.auth;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuthResult {

    @NotNull
    private final Type type;

    @Nullable
    private final String messageKey;

    @NotNull
    public static AuthResult of(@NotNull Type type, @Nullable String messageKey) {
        return new AuthResult(type, messageKey);
    }

    @NotNull
    public static AuthResult success() {
        return new AuthResult(Type.SUCCESS, "auth.success");
    }

    public boolean isSuccessful() {
        return type == Type.SUCCESS;
    }

    public enum Type {
        SUCCESS,
        ALREADY_AUTHENTICATED,
        NOT_REGISTERED,
        ALREADY_REGISTERED,
        INVALID_SECRET,
        INVALID_CONFIRMATION,
        PASSWORD_WEAK,
        RATE_LIMITED,
        BLOCKED,
        NOT_AVAILABLE,
        ERROR
    }
}
