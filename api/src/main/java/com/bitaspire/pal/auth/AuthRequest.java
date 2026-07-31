package com.bitaspire.pal.auth;

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
public final class AuthRequest {

    @NotNull
    private final UUID uniqueId;

    @NotNull
    private final String name;

    @Nullable
    private final String secret;

    @Nullable
    private final String newSecret;

    @Nullable
    private final String confirmation;

    @Nullable
    private final InetAddress address;

    @NotNull
    private final AuthSource source;

    @NotNull
    public static AuthRequest command(@NotNull UUID uniqueId, @NotNull String name, @Nullable String secret, @Nullable InetAddress address) {
        return builder()
                .uniqueId(uniqueId)
                .name(name)
                .secret(secret)
                .address(address)
                .source(AuthSource.COMMAND)
                .build();
    }

    @NotNull
    public static AuthRequest register(
            @NotNull UUID uniqueId,
            @NotNull String name,
            @Nullable String secret,
            @Nullable String confirmation,
            @Nullable InetAddress address
    ) {
        return builder()
                .uniqueId(uniqueId)
                .name(name)
                .secret(secret)
                .confirmation(confirmation)
                .address(address)
                .source(AuthSource.COMMAND)
                .build();
    }

    @NotNull
    public static AuthRequest changePassword(
            @NotNull UUID uniqueId,
            @NotNull String name,
            @Nullable String currentSecret,
            @Nullable String newSecret,
            @Nullable InetAddress address
    ) {
        return builder()
                .uniqueId(uniqueId)
                .name(name)
                .secret(currentSecret)
                .newSecret(newSecret)
                .address(address)
                .source(AuthSource.COMMAND)
                .build();
    }
}
