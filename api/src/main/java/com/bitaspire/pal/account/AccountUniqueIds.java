package com.bitaspire.pal.account;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@UtilityClass
public class AccountUniqueIds {

    @NotNull
    public UUID resolve(@NotNull String name, @NotNull PALAccount.Type type, @Nullable UUID supplied) {
        switch (type) {
            case PREMIUM:
                if (supplied == null) throw new IllegalArgumentException("Premium accounts require a Mojang UUID");
                return supplied;
            case BEDROCK:
                return supplied == null ? bedrock(name) : supplied;
            case OFFLINE:
            case UNKNOWN:
            default:
                return offline(name);
        }
    }

    @NotNull
    public UUID offline(@NotNull String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    @NotNull
    public UUID bedrock(@NotNull String name) {
        return UUID.nameUUIDFromBytes(("BedrockPlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
