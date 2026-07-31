package com.bitaspire.pal.account;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Builder(toBuilder = true)
public final class AccountNameLock {

    @NotNull
    private final String name;

    @NotNull
    private final Type type;

    @Nullable
    private final UUID uniqueId;

    @Nullable
    private final String source;

    @NotNull
    private final Instant createdAt;

    @Nullable
    private final Instant expiresAt;

    @NotNull
    public static AccountNameLock premium(@NotNull String name, @NotNull UUID uniqueId, @Nullable String source) {
        return builder()
                .name(name)
                .type(Type.PREMIUM)
                .uniqueId(uniqueId)
                .source(source)
                .createdAt(Instant.now())
                .build();
    }

    @NotNull
    public static AccountNameLock bedrock(@NotNull String name, @Nullable UUID uniqueId, @Nullable String source) {
        return builder()
                .name(name)
                .type(Type.BEDROCK)
                .uniqueId(uniqueId)
                .source(source)
                .createdAt(Instant.now())
                .build();
    }

    public boolean isExpired(@NotNull Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public boolean belongsTo(@NotNull UUID uniqueId) {
        return this.uniqueId != null && this.uniqueId.equals(uniqueId);
    }

    public enum Type {
        PREMIUM,
        BEDROCK,
        MANUAL
    }
}
