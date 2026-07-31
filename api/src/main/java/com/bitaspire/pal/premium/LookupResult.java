package com.bitaspire.pal.premium;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class LookupResult {
    @NotNull
    private final PremiumProvider provider;

    private final boolean premium;

    @Nullable
    private final UUID uniqueId;

    @Nullable
    private final String name;

    @Nullable
    private final String reason;

    @NotNull
    public static LookupResult premium(@NotNull PremiumProvider provider, @NotNull UUID uniqueId, @NotNull String name) {
        return new LookupResult(provider, true, uniqueId, name, null);
    }

    @NotNull
    public static LookupResult offline(@NotNull PremiumProvider provider, @Nullable String reason) {
        return new LookupResult(provider, false, null, null, reason);
    }
}
