package com.bitaspire.pal.protocol.bukkit;

import com.bitaspire.pal.protocol.ProtocolPlatform;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class BukkitNativeAdapter implements BukkitAdapter {

    private final Plugin plugin;
    private final BukkitOptions options;

    @NotNull
    @Override
    public Plugin getPlugin() {
        return plugin;
    }

    @NotNull
    @Override
    public BukkitOptions getOptions() {
        return options;
    }

    @NotNull
    @Override
    public ProtocolPlatform getPlatform() {
        return ProtocolPlatform.BUKKIT;
    }

    @Override
    public boolean supportsLoginPhase() {
        return plugin.getServer().getOnlineMode();
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
}
