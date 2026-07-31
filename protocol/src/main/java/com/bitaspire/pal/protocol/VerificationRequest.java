package com.bitaspire.pal.protocol;

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
public final class VerificationRequest {
    @NotNull
    private final String name;

    @Nullable
    private final UUID uniqueIdHint;

    @Nullable
    private final InetAddress address;

    @Nullable
    private final String virtualHost;

    @Nullable
    private final String serverHash;

    @Builder.Default
    @NotNull
    private final ProtocolPlatform platform = ProtocolPlatform.UNKNOWN;

    private final int protocolVersion;
    private final boolean proxyConnection;

    @NotNull
    public static VerificationRequest of(@NotNull String name, @Nullable InetAddress address) {
        return builder()
                .name(name)
                .address(address)
                .build();
    }
}
