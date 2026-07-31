package com.bitaspire.pal;

import org.jetbrains.annotations.NotNull;

public interface Configuration {

    boolean isUpdateCheckEnabled();

    boolean isColoredConsole();

    boolean isShowPrefix();

    boolean isOverrideOp();

    @NotNull
    String getPrefixKey();

    @NotNull
    String getPrefix();

    @NotNull
    String getCenterPrefix();

    @NotNull
    String getLineSeparator();
}
