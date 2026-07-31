package com.bitaspire.pal;

import com.bitaspire.pal.account.PALAccount;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
final class StoredAccount implements PALAccount {

    @NotNull
    private final UUID uniqueId;

    @NotNull
    private final String name;

    @NotNull
    private final Type type;

    @NotNull
    private final Status status;

    @Nullable
    private final InetAddress lastAddress;

    @Nullable
    private final Instant registeredAt;

    @Nullable
    private final Instant lastLoginAt;
}
