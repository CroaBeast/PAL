package com.bitaspire.pal.proxy.bungee;

import com.github.games647.fastlogin.bungee.event.BungeeFastLoginAutoLoginEvent;
import com.github.games647.fastlogin.bungee.event.BungeeFastLoginPreLoginEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class BungeeFastLoginHook implements Listener {

    @NotNull
    private final PALBungeePlugin plugin;

    @EventHandler(priority = Byte.MAX_VALUE)
    void onAutoLogin(@NotNull BungeeFastLoginAutoLoginEvent event) {
        if (!event.isCancelled()) plugin.handleFastLogin(event);
    }

    @EventHandler(priority = Byte.MAX_VALUE)
    void onPreLogin(@NotNull BungeeFastLoginPreLoginEvent event) {
        plugin.handleFastLogin(event);
    }
}
