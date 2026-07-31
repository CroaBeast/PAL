package com.bitaspire.pal.account;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

public interface PALAccount {

    @NotNull
    UUID getUniqueId();

    @NotNull
    String getName();

    @NotNull
    Type getType();

    @NotNull
    Status getStatus();

    @Nullable
    InetAddress getLastAddress();

    @Nullable
    Instant getRegisteredAt();

    @Nullable
    Instant getLastLoginAt();

    enum Type {
        PREMIUM,
        OFFLINE,
        BEDROCK,
        UNKNOWN
    }

    enum Status {
        UNREGISTERED,
        REGISTERED,
        AUTHENTICATED,
        LOCKED,
        DISABLED
    }
}
