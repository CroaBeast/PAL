package com.bitaspire.pal.protocol.bukkit;

import com.bitaspire.pal.protocol.LoginProtocolAdapter;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public interface BukkitAdapter extends LoginProtocolAdapter {

    @NotNull
    Plugin getPlugin();

    @NotNull
    BukkitOptions getOptions();
}
