package com.bitaspire.pal.protocol.bukkit;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public final class BukkitAdapters {

    private BukkitAdapters() {
    }

    /**
     * @param premiumName answers whether a login of that name must be challenged, usually
     *                    because its account is stored as premium. Called off the main thread.
     */
    @NotNull
    public static BukkitAdapter create(
            @NotNull Plugin plugin,
            @NotNull BukkitOptions options,
            @NotNull Function<String, CompletionStage<Boolean>> premiumName
    ) {
        if (!options.isEnabled() || options.getMode() == BukkitOptions.Mode.DISABLED) {
            return new DisabledBukkitAdapter(plugin, options);
        }

        return new BukkitNativeAdapter(plugin, options, premiumName);
    }
}
