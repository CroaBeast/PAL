package com.bitaspire.pal.account;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.util.UUID;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Builder(toBuilder = true)
public final class AccountCreateRequest {

    @NotNull
    private final String name;

    @NotNull
    private final PALAccount.Type type;

    @Nullable
    private final UUID uniqueId;

    @Nullable
    private final InetAddress address;

    @Builder.Default
    @NotNull
    private final PALAccount.Status status = PALAccount.Status.REGISTERED;

    @Builder.Default
    private final boolean lockName = true;

    @Nullable
    private final String source;

    @NotNull
    public static AccountCreateRequest offline(@NotNull String name, @Nullable InetAddress address) {
        return builder()
                .name(name)
                .type(PALAccount.Type.OFFLINE)
                .address(address)
                .source("PAL")
                .build();
    }

    @NotNull
    public static AccountCreateRequest premium(@NotNull String name, @NotNull UUID uniqueId, @Nullable InetAddress address) {
        return builder()
                .name(name)
                .type(PALAccount.Type.PREMIUM)
                .uniqueId(uniqueId)
                .address(address)
                .source("PAL")
                .build();
    }

    @NotNull
    public static AccountCreateRequest bedrock(@NotNull String name, @Nullable UUID uniqueId, @Nullable InetAddress address) {
        return builder()
                .name(name)
                .type(PALAccount.Type.BEDROCK)
                .uniqueId(uniqueId)
                .address(address)
                .source("PAL")
                .build();
    }
}
