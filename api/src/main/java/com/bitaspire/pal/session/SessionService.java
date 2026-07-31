package com.bitaspire.pal.session;

import com.bitaspire.pal.account.PALAccount;
import com.bitaspire.pal.auth.AuthSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface SessionService {

    boolean isAuthenticated(@NotNull UUID uniqueId);

    boolean isAuthenticated(@NotNull UUID uniqueId, @NotNull String name, @Nullable InetAddress address);

    @NotNull
    Optional<AuthSession> getSession(@NotNull UUID uniqueId);

    @NotNull
    Optional<AuthSession> getSession(@NotNull UUID uniqueId, @NotNull String name, @Nullable InetAddress address);

    @NotNull
    CompletionStage<AuthSession> create(@NotNull PALAccount account, @NotNull AuthSource source, @Nullable InetAddress address);

    void invalidate(@NotNull UUID uniqueId);

    @NotNull
    CompletionStage<Void> invalidateAsync(@NotNull UUID uniqueId);
}
