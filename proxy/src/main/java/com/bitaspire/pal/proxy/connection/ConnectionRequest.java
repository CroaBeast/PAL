package com.bitaspire.pal.proxy.connection;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.SocketAddress;
import java.util.UUID;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Builder(toBuilder = true)
public final class ConnectionRequest {
    @NotNull
    private final String name;

    @Nullable
    private final UUID uniqueId;

    @Nullable
    private final SocketAddress address;

    @Nullable
    private final String requestedServer;

    @Nullable
    private final String virtualHost;

    private final boolean fastLoginVerified;
    private final boolean bedrockVerified;
    private final boolean linkedJava;
    private final boolean palNativeVerified;
    private final boolean allowAuthenticatedAuthTarget;

    @NotNull
    public static ConnectionRequest of(@NotNull String name, @Nullable UUID uniqueId, @Nullable SocketAddress address) {
        return builder()
                .name(name)
                .uniqueId(uniqueId)
                .address(address)
                .build();
    }
}
