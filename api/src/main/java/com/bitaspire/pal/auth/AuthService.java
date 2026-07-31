package com.bitaspire.pal.auth;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletionStage;

public interface AuthService {

    @NotNull
    CompletionStage<AuthResult> register(@NotNull AuthRequest request);

    @NotNull
    CompletionStage<AuthResult> login(@NotNull AuthRequest request);

    @NotNull
    CompletionStage<AuthResult> logout(@NotNull AuthRequest request);

    @NotNull
    CompletionStage<AuthResult> changePassword(@NotNull AuthRequest request);
}
