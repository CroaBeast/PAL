package com.bitaspire.pal.protocol.bukkit;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class BukkitAdapters {

    private BukkitAdapters() {
    }

    @NotNull
    public static BukkitAdapter create(@NotNull Plugin plugin, @NotNull BukkitOptions options) {
        if (!options.isEnabled() || options.getMode() == BukkitOptions.Mode.DISABLED) {
            return new DisabledBukkitAdapter(plugin, options);
        }

        return new BukkitNativeAdapter(plugin, options);
    }
}
