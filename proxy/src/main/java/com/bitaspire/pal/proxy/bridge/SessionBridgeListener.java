package com.bitaspire.pal.proxy.bridge;

import com.bitaspire.pal.proxy.session.AuthSession;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface SessionBridgeListener {

    default void onSessionSaved(@NotNull AuthSession session) {}

    default void onSessionInvalidated(@NotNull UUID uniqueId) {}
}
