package com.bitaspire.pal.proxy.bridge;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
final class RedisConnectionSettings {

    @NotNull
    private final String host;
    private final int port;
    private final int database;

    @Nullable
    private final String password;

    @NotNull
    static RedisConnectionSettings from(@NotNull String uri) {
        URI parsed = URI.create(uri);
        String host = parsed.getHost() == null ? "localhost" : parsed.getHost();
        int port = parsed.getPort() < 0 ? 6379 : parsed.getPort();
        int database = 0;

        String path = parsed.getPath();
        if (path != null && path.length() > 1) {
            try {
                database = Math.max(0, Integer.parseInt(path.substring(1)));
            } catch (NumberFormatException ignored) {
                database = 0;
            }
        }

        String password = null;
        String userInfo = parsed.getUserInfo();
        if (userInfo != null && !userInfo.trim().isEmpty()) {
            int colon = userInfo.indexOf(':');
            password = colon < 0 ? userInfo : userInfo.substring(colon + 1);
        }

        return new RedisConnectionSettings(host, port, database, password);
    }
}
