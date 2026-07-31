package com.bitaspire.pal.proxy.connection;

import com.bitaspire.pal.proxy.identity.IdentityResult;
import com.bitaspire.pal.proxy.session.AuthSession;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConnectionDecision {

    public enum Type {
        ALLOW,
        DENY,
        REDIRECT
    }

    private final Type type;
    private final String reason;
    private final String targetServer;
    private final IdentityResult identity;
    private final AuthSession session;

    @NotNull
    public static ConnectionDecision allow() {
        return new ConnectionDecision(Type.ALLOW, null, null, null, null);
    }

    @NotNull
    public static ConnectionDecision allow(@NotNull AuthSession session) {
        return new ConnectionDecision(Type.ALLOW, null, null, null, session);
    }

    @NotNull
    public static ConnectionDecision allow(@NotNull IdentityResult identity) {
        return new ConnectionDecision(Type.ALLOW, null, null, identity, null);
    }

    @NotNull
    public static ConnectionDecision deny(@NotNull String reason) {
        return new ConnectionDecision(Type.DENY, reason, null, null, null);
    }

    @NotNull
    public static ConnectionDecision redirect(@NotNull String targetServer, @Nullable String reason) {
        return new ConnectionDecision(Type.REDIRECT, reason, targetServer, null, null);
    }

    @NotNull
    public static ConnectionDecision redirect(@NotNull String targetServer, @Nullable String reason, @Nullable IdentityResult identity) {
        return new ConnectionDecision(Type.REDIRECT, reason, targetServer, identity, null);
    }

    public boolean isAllowed() {
        return type == Type.ALLOW || type == Type.REDIRECT;
    }
}
