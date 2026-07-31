package com.bitaspire.pal.proxy.velocity;

import com.github.games647.fastlogin.velocity.event.VelocityFastLoginAutoLoginEvent;
import com.github.games647.fastlogin.velocity.event.VelocityFastLoginPreLoginEvent;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class VelocityFastLoginHook {

    @NotNull
    private final PALVelocityPlugin plugin;

    @Subscribe(order = PostOrder.LAST)
    void onAutoLogin(@NotNull VelocityFastLoginAutoLoginEvent event) {
        plugin.handleFastLogin(event);
    }

    @Subscribe(order = PostOrder.LAST)
    void onPreLogin(@NotNull VelocityFastLoginPreLoginEvent event) {
        plugin.handleFastLogin(event);
    }
}
