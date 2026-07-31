package com.bitaspire.pal.protocol;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProtocolIdentity {
    @NotNull
    private final UUID uniqueId;

    @NotNull
    private final String name;

    @NotNull
    private final ProtocolProvider provider;

    @Nullable
    private final String source;
}
