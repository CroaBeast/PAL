package com.bitaspire.pal;

import org.jetbrains.annotations.NotNull;

interface IntegrationHook {

    boolean register();

    boolean unregister();

    @NotNull
    String name();
}
