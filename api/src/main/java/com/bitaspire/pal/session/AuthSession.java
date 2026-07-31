package com.bitaspire.pal.session;

import com.bitaspire.pal.auth.AuthSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

public interface AuthSession {

    @NotNull
    UUID getUniqueId();

    @NotNull
    String getName();

    @NotNull
    String getSessionId();

    @NotNull
    AuthSource getSource();

    @Nullable
    InetAddress getAddress();

    @Nullable
    String getAddressHash();

    @NotNull
    Instant getAuthenticatedAt();

    @Nullable
    Instant getExpiresAt();
}
