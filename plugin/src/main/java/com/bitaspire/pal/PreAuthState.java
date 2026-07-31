package com.bitaspire.pal;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter(AccessLevel.PACKAGE)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
final class PreAuthState {

    @NotNull
    private final UUID playerId;

    @NotNull
    private final String name;

    @Nullable
    private final InetAddress address;

    @NotNull
    private final Instant joinedAt;

    @Nullable
    private UUID accountId;

    private boolean loaded;
    private boolean registered;
    private boolean authenticated;
    private long lastWarningAt;
}
