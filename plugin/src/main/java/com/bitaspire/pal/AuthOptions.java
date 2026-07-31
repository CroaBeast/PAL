package com.bitaspire.pal;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.croabeast.file.ConfigurableFile;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
final class AuthOptions {

    private final boolean registration;
    private final boolean repeat;
    private final int minLength;
    private final int maxLength;
    private final boolean blockUsername;
    private final boolean rehash;
    private final Pattern namePattern;

    private final boolean changeInvalidates;

    private final boolean bruteForce;
    private final int maxAttempts;
    private final int windowSeconds;
    private final int lockoutSeconds;
    private final long ipThrottleMillis;
    private final long usernameThrottleMillis;

    @NotNull
    private final Set<String> commonPasswords;

    @NotNull
    static AuthOptions load(@NotNull PALPlugin plugin) {
        try {
            ConfigurableFile file = new ConfigurableFile(plugin, "security");
            file.saveDefaults();

            return new AuthOptions(
                    bool(file, "auth.registration.enabled", true),
                    bool(file, "auth.passwords.require-repeat-on-register", true),
                    integer(file, "auth.passwords.min-length", 6),
                    integer(file, "auth.passwords.max-length", 64),
                    bool(file, "auth.passwords.block-username", true),
                    bool(file, "auth.passwords.rehash-on-login", true),
                    Pattern.compile(string(file, "auth.registration.allowed-name-pattern", "^[A-Za-z0-9_]{3,16}$")),
                    bool(file, "auth.sessions.invalidate-on-password-change", true),
                    bool(file, "auth.brute-force.enabled", true),
                    integer(file, "auth.login.max-attempts", 5),
                    integer(file, "auth.brute-force.window-seconds", 300),
                    integer(file, "auth.brute-force.lockout-seconds", 300),
                    integer(file, "auth.brute-force.ip-throttle-ms", 750),
                    integer(file, "auth.brute-force.username-throttle-ms", 750),
                    commonPasswords()
            );
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not load security.yml: " + exception.getMessage());
            return defaults();
        }
    }

    boolean isCommon(@NotNull String password) {
        return commonPasswords.contains(password.toLowerCase());
    }

    private static AuthOptions defaults() {
        return new AuthOptions(true, true, 6, 64, true, true,
                Pattern.compile("^[A-Za-z0-9_]{3,16}$"),
                true,
                true, 5, 300, 300, 750, 750,
                commonPasswords());
    }

    private static Set<String> commonPasswords() {
        return new HashSet<>(Arrays.asList(
                "password", "password1", "123456", "12345678", "123456789",
                "qwerty", "abc123", "minecraft", "letmein", "admin"
        ));
    }

    private static boolean bool(ConfigurableFile file, String path, boolean defaultValue) {
        Object value = file.get(path, defaultValue);
        return value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(String.valueOf(value));
    }

    private static int integer(ConfigurableFile file, String path, int defaultValue) {
        Object value = file.get(path, defaultValue);
        if (value instanceof Number) return Math.max(0, ((Number) value).intValue());

        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String string(ConfigurableFile file, String path, String defaultValue) {
        Object value = file.get(path, defaultValue);
        return value == null ? defaultValue : String.valueOf(value);
    }
}
