package com.bitaspire.pal.identity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class IdentityRequest {
    @NotNull
    private final String name;

    @Nullable
    private final UUID uniqueId;

    @Nullable
    private final InetAddress address;

    @Nullable
    private final String virtualHost;

    private final boolean proxyConnection;
    private final boolean fastLoginVerified;
    private final boolean bedrockVerified;
    private final boolean linkedJava;
    private final boolean serverOnlineMode;

    @NotNull
    public static IdentityRequest of(@NotNull String name, @Nullable InetAddress address) {
        return builder()
                .name(name)
                .address(address)
                .build();
    }
}
