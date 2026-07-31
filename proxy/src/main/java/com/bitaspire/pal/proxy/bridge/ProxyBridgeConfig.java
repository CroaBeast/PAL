package com.bitaspire.pal.proxy.bridge;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProxyBridgeConfig {

    private final boolean enabled;
    @NotNull
    private final Mode mode;

    @NotNull
    private final String authServer;
    @NotNull
    private final String lobbyServer;
    private final boolean rememberTarget;
    private final int servers;

    private final boolean strict;
    private final boolean authRequired;
    private final boolean premiumAuto;
    private final boolean bedrockAuto;
    private final boolean proxyAutoLogin;
    private final int sessionMinutes;
    private final boolean nativePremium;
    private final int nativeTimeoutMillis;
    private final boolean nativeBlockOnError;
    @NotNull
    private final String authenticationRequiredMessage;
    @NotNull
    private final String alreadyAuthenticatedMessage;
    @NotNull
    private final String authenticatedAuthAllowedMessage;
    @NotNull
    private final String authServerUnavailableMessage;
    @NotNull
    private final String redirectingMessage;
    @NotNull
    private final String nativeVerificationFailedMessage;

    private final boolean failover;
    private final boolean keepOnline;
    private final boolean rejoin;
    private final boolean newLogin;
    @NotNull
    private final String fallbackServer;
    @NotNull
    private final BridgeOptions.AuthenticatedAuthTarget authenticatedAuthTarget;

    private final boolean requireSecret;
    @NotNull
    private final String secret;
    private final boolean hashIp;
    private final boolean bindIp;
    private final int skewSeconds;

    @NotNull
    private final String redisUri;
    @NotNull
    private final String redisPrefix;
    @NotNull
    private final String redisChannel;

    @NotNull
    private final DatabaseType databaseType;
    @NotNull
    private final String databaseTablePrefix;
    @NotNull
    private final String databaseSqliteFile;
    @NotNull
    private final String databaseHost;
    private final int databasePort;
    @NotNull
    private final String databaseName;
    @NotNull
    private final String databaseUsername;
    @NotNull
    private final String databasePassword;
    private final boolean databaseSsl;
    private final int databasePollSeconds;

    @NotNull
    public BridgeOptions toOptions() {
        return !enabled ? BridgeOptions.disabled() : new BridgeOptions(
                authServer,
                lobbyServer,
                strict,
                authRequired,
                premiumAuto,
                bedrockAuto,
                proxyAutoLogin,
                sessionMinutes,
                rememberTarget,
                failover,
                keepOnline,
                rejoin,
                newLogin,
                fallbackServer,
                authenticatedAuthTarget,
                authenticationRequiredMessage,
                alreadyAuthenticatedMessage,
                authenticatedAuthAllowedMessage
        );
    }

    public boolean isRedis() {
        return enabled && mode == Mode.REDIS;
    }

    public boolean isMemory() {
        return enabled && mode == Mode.MEMORY;
    }

    public boolean isDatabase() {
        return enabled && mode == Mode.DATABASE;
    }

    public boolean hasUsableSecret() {
        return !requireSecret || (!secret.trim().isEmpty() && !"change-me".equalsIgnoreCase(secret.trim()));
    }

    @NotNull
    public static ProxyBridgeConfig load(@NotNull File dataFolder, int detectedServers) {
        File file = new File(dataFolder, "bridge.yml");
        ensureDefault(file);

        Map<String, String> values = read(file);
        int servers = integer(values, "bridge.net.servers", Math.max(1, detectedServers));

        return new ProxyBridgeConfig(
                bool(values, "bridge.enabled", true),
                Mode.from(string(values, "bridge.mode", "REDIS")),
                string(values, "bridge.net.auth", "auth"),
                string(values, "bridge.net.lobby", "lobby"),
                bool(values, "bridge.net.remember", true),
                servers,
                bool(values, "bridge.guard.strict", true),
                bool(values, "bridge.guard.required", true),
                bool(values, "bridge.guard.premium", true),
                bool(values, "bridge.guard.bedrock", true),
                bool(values, "bridge.guard.proxy-auto-login", false),
                integer(values, "bridge.session.minutes", 30),
                bool(values, "bridge.native.enabled", true),
                integer(values, "bridge.native.timeout-ms", 5000),
                bool(values, "bridge.native.block-error", true),
                string(values, "bridge.messages.authentication-required", "Authentication required"),
                string(values, "bridge.messages.already-authenticated", "Already authenticated"),
                string(values, "bridge.messages.authenticated-auth-allowed", "You are already authenticated. Auth is only needed for login."),
                string(values, "bridge.messages.auth-server-unavailable", "Authentication server is not available"),
                string(values, "bridge.messages.redirecting", "Redirecting to {server}"),
                string(values, "bridge.messages.native-verification-failed", "Could not verify Mojang ownership"),
                bool(values, "bridge.fail.enabled", bool(values, "bridge.failover.enabled", true)),
                bool(values, "bridge.fail.keep", bool(values, "bridge.failover.keep-online", true)),
                bool(values, "bridge.fail.rejoin", bool(values, "bridge.failover.rejoin", true)),
                bool(values, "bridge.fail.new-login", bool(values, "bridge.failover.new-login", false)),
                string(values, "bridge.fail.fallback", string(values, "bridge.failover.fallback", "limbo")),
                BridgeOptions.AuthenticatedAuthTarget.from(string(values, "bridge.guard.authenticated-auth-target", "REDIRECT")),
                bool(values, "bridge.sec.require", bool(values, "bridge.sec.require-secret", true)),
                string(values, "bridge.sec.secret", "change-me"),
                bool(values, "bridge.sec.hash-ip", true),
                bool(values, "bridge.sec.bind-ip", false),
                integer(values, "bridge.sec.skew", 15),
                string(values, "bridge.redis.uri", "redis://localhost:6379/0"),
                string(values, "bridge.redis.prefix", "pal:"),
                string(values, "bridge.redis.channel", "pal:auth"),
                DatabaseType.from(string(values, "bridge.database.type", string(values, "storage.type", "MYSQL"))),
                sanitizePrefix(string(values, "bridge.database.table-prefix", string(values, "storage.table-prefix", "pal_"))),
                string(values, "bridge.database.sqlite.file", string(values, "storage.sqlite.file", "plugins/PAL/database.db")),
                string(values, "bridge.database.remote.host", string(values, "storage.remote.host", "localhost")),
                integer(values, "bridge.database.remote.port", integer(values, "storage.remote.port", 3306)),
                string(values, "bridge.database.remote.database", string(values, "storage.remote.database", "pal")),
                string(values, "bridge.database.remote.username", string(values, "storage.remote.username", "username")),
                string(values, "bridge.database.remote.password", string(values, "storage.remote.password", "password")),
                bool(values, "bridge.database.remote.ssl", bool(values, "storage.remote.ssl", true)),
                integer(values, "bridge.database.poll", 3)
        );
    }

    private static void ensureDefault(@NotNull File file) {
        if (file.exists()) return;

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        try (InputStream input = ProxyBridgeConfig.class.getClassLoader().getResourceAsStream("bridge.yml")) {
            if (input == null) return;
            Files.copy(input, file.toPath());
        } catch (IOException ignored) {
            // The proxy can still boot with in-code defaults if the resource cannot be copied.
        }
    }

    @NotNull
    private static Map<String, String> read(@NotNull File file) {
        Map<String, String> values = new HashMap<>();
        if (!file.exists()) return values;

        String[] stack = new String[8];
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = withoutComment(line).trim();
                if (trimmed.isEmpty() || !trimmed.contains(":")) continue;

                int level = Math.max(0, countIndent(line) / 2);
                String key = trimmed.substring(0, trimmed.indexOf(':')).trim();
                String value = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                stack[level] = key;
                for (int index = level + 1; index < stack.length; index++) stack[index] = null;

                if (value.isEmpty()) continue;
                values.put(path(stack, level), unquote(value));
            }
        } catch (IOException ignored) {
            return values;
        }

        return values;
    }

    @NotNull
    private static String path(@NotNull String[] stack, int level) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index <= level; index++) {
            if (stack[index] == null) continue;
            if (builder.length() > 0) builder.append('.');
            builder.append(stack[index]);
        }
        return builder.toString();
    }

    @NotNull
    private static String withoutComment(@NotNull String line) {
        int comment = line.indexOf('#');
        return comment < 0 ? line : line.substring(0, comment);
    }

    private static int countIndent(@NotNull String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') count++;
        return count;
    }

    @NotNull
    private static String unquote(@NotNull String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean bool(@NotNull Map<String, String> values, @NotNull String path, boolean defaultValue) {
        return Boolean.parseBoolean(values.getOrDefault(path, String.valueOf(defaultValue)));
    }

    private static int integer(@NotNull Map<String, String> values, @NotNull String path, int defaultValue) {
        try {
            return Integer.parseInt(values.getOrDefault(path, String.valueOf(defaultValue)));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    @NotNull
    private static String string(@NotNull Map<String, String> values, @NotNull String path, @NotNull String defaultValue) {
        String value = values.get(path);
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }

    public enum Mode {
        DISABLED,
        DATABASE,
        MEMORY,
        REDIS;

        @NotNull
        public static Mode from(@NotNull String value) {
            String normalized = value.trim().toUpperCase().replace('-', '_');
            if ("DB".equals(normalized) || "SQL".equals(normalized) || "STORAGE".equals(normalized)) {
                return DATABASE;
            }
            if ("IN_MEMORY".equals(normalized) || "LOCAL".equals(normalized)) return MEMORY;

            try {
                return valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return REDIS;
            }
        }
    }

    public enum DatabaseType {
        SQLITE,
        MYSQL,
        MARIADB,
        POSTGRESQL;

        @NotNull
        static DatabaseType from(@NotNull String value) {
            try {
                return valueOf(value.trim().toUpperCase().replace('-', '_'));
            } catch (IllegalArgumentException ignored) {
                return MYSQL;
            }
        }
    }

    @NotNull
    private static String sanitizePrefix(@NotNull String prefix) {
        return prefix.trim().isEmpty() ? "pal_" : prefix.replaceAll("[^A-Za-z0-9_]", "_");
    }
}
