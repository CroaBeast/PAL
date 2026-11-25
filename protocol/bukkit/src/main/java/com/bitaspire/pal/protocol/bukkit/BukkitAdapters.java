package com.bitaspire.pal.protocol.bukkit;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public final class BukkitAdapters {

    private BukkitAdapters() {
    }

    /**
     * @param storedAccount answers what PAL stores under that name; premium accounts are always
     *                    challenged, password accounts never are. Called off the main thread.
     */
    @NotNull
    public static BukkitAdapter create(
            @NotNull Plugin plugin,
            @NotNull BukkitOptions options,
            @NotNull Function<String, CompletionStage<BukkitAdapter.StoredAccount>> storedAccount
    ) {
        if (!options.isEnabled() || options.getMode() == BukkitOptions.Mode.DISABLED) {
            return new DisabledBukkitAdapter(plugin, options);
        }

        return new BukkitNativeAdapter(plugin, options, storedAccount);
    }
}
