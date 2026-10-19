package com.bitaspire.pal.protocol.bukkit;

import com.bitaspire.pal.protocol.LoginProtocolAdapter;
import com.bitaspire.pal.protocol.ProtocolIdentity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BukkitAdapter extends LoginProtocolAdapter {

    @NotNull
    Plugin getPlugin();

    @NotNull
    BukkitOptions getOptions();

    /**
     * Whether this adapter asks offline-mode connections for encryption itself, so a premium
     * name can be verified by Mojang without online-mode, FastLogin or a proxy.
     */
    default boolean isChallenging() {
        return false;
    }

    /**
     * Challenges the next login of this name once, even when its account is not premium yet.
     */
    default void challenge(@NotNull String name) {
    }

    /**
     * The Mojang identity the last login of this name proved, consumed on read so a caller
     * that loses track fails closed and falls back to a password.
     */
    @Nullable
    default ProtocolIdentity consumeVerified(@NotNull String name) {
        return null;
    }
}
