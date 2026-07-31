package com.bitaspire.pal;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
final class StoredTwoFactor {

    @NotNull
    private final UUID uniqueId;

    @NotNull
    private final String secret;

    private final boolean enabled;

    @NotNull
    private final Instant createdAt;

    @NotNull
    private final Instant updatedAt;
}
