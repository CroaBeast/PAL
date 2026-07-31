package com.bitaspire.pal.protocol.mojang;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
public final class MojangProfile {

    @NotNull
    private final UUID uniqueId;

    @NotNull
    private final String name;
}
