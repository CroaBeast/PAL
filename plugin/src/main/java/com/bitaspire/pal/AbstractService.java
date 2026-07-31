package com.bitaspire.pal;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.croabeast.common.Registrable;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
abstract class AbstractService implements Registrable {

    @NotNull
    protected final PALApi api;
    private boolean registered = false;

    @Override
    public final boolean isRegistered() {
        return registered;
    }

    @Override
    public boolean register() {
        registered = true;
        return true;
    }

    @Override
    public boolean unregister() {
        registered = false;
        return true;
    }
}
