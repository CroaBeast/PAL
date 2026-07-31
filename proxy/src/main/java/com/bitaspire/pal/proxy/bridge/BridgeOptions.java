package com.bitaspire.pal.proxy.bridge;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class BridgeOptions {

    private final String authServer;
    private final String lobbyServer;
    private final boolean strict;
    private final boolean authRequired;
    private final boolean premiumAuto;
    private final boolean bedrockAuto;
    private final boolean proxyAutoLogin;
    private final int sessionMinutes;
    private final boolean rememberTarget;
    private final boolean failover;
    private final boolean keepOnline;
    private final boolean rejoin;
    private final boolean newLogin;
    private final String fallbackServer;
    @NotNull
    private final AuthenticatedAuthTarget authenticatedAuthTarget;
    @NotNull
    private final String authenticationRequiredMessage;
    @NotNull
    private final String alreadyAuthenticatedMessage;
    @NotNull
    private final String authenticatedAuthAllowedMessage;

    @NotNull
    public static BridgeOptions authServer(@Nullable String authServer) {
        return new BridgeOptions(authServer, null, false, true, true, true,
                false, 30, true, true, true, true, false, null,
                AuthenticatedAuthTarget.REDIRECT,
                "Authentication required", "Already authenticated",
                "You are already authenticated. Auth is only needed for login.");
    }

    @NotNull
    public static BridgeOptions strict(@Nullable String authServer) {
        return new BridgeOptions(authServer, null, true, true, true, true,
                false, 30, true, true, true, true, false, null,
                AuthenticatedAuthTarget.REDIRECT,
                "Authentication required", "Already authenticated",
                "You are already authenticated. Auth is only needed for login.");
    }

    @NotNull
    public static BridgeOptions disabled() {
        return new BridgeOptions(null, null, false, false, false, false,
                false, 0, false, false, false, false, false, null,
                AuthenticatedAuthTarget.REDIRECT,
                "Authentication required", "Already authenticated",
                "You are already authenticated. Auth is only needed for login.");
    }

    public boolean hasAuthServer() {
        return authServer != null && !authServer.trim().isEmpty();
    }

    public boolean hasLobbyServer() {
        return lobbyServer != null && !lobbyServer.trim().isEmpty();
    }

    public boolean allowsAuthenticatedAuthTarget() {
        return authenticatedAuthTarget == AuthenticatedAuthTarget.ALLOW;
    }

    public enum AuthenticatedAuthTarget {
        REDIRECT,
        ALLOW;

        @NotNull
        public static AuthenticatedAuthTarget from(@NotNull String value) {
            try {
                return valueOf(value.trim().toUpperCase().replace('-', '_'));
            } catch (IllegalArgumentException ignored) {
                return REDIRECT;
            }
        }
    }
}
