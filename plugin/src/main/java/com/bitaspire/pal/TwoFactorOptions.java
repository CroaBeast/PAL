package com.bitaspire.pal;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.croabeast.file.ConfigurableFile;
import org.jetbrains.annotations.NotNull;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
final class TwoFactorOptions {

    private final boolean enabled;
    private final boolean totp;
    private final boolean backupCodes;
    @NotNull
    private final String requiredPermission;
    @NotNull
    private final String issuer;
    private final int digits;
    private final int periodSeconds;
    private final int window;
    private final int backupCodeAmount;
    private final int maxAttempts;

    @NotNull
    static TwoFactorOptions load(@NotNull PALPlugin plugin) {
        try {
            ConfigurableFile file = new ConfigurableFile(plugin, "security");
            file.saveDefaults();

            return new TwoFactorOptions(
                    bool(file, "auth.two-factor.enabled", false),
                    bool(file, "auth.two-factor.totp", true),
                    bool(file, "auth.two-factor.backup-codes", true),
                    string(file, "auth.two-factor.required-permission", "pal.security.2fa.required"),
                    string(file, "auth.two-factor.issuer", "PAL"),
                    integer(file, "auth.two-factor.digits", 6),
                    integer(file, "auth.two-factor.period-seconds", 30),
                    integer(file, "auth.two-factor.window", 1),
                    integer(file, "auth.two-factor.backup-code-amount", 8),
                    integer(file, "auth.two-factor.max-attempts", 5)
            );
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not load two-factor options: " + exception.getMessage());
            return defaults();
        }
    }

    @NotNull
    static TwoFactorOptions defaults() {
        return new TwoFactorOptions(false, true, true, "pal.security.2fa.required",
                "PAL", 6, 30, 1, 8, 5);
    }

    boolean shouldRequireSetup(@NotNull org.bukkit.entity.Player player) {
        return enabled && !requiredPermission.trim().isEmpty() && player.hasPermission(requiredPermission);
    }

    private static boolean bool(ConfigurableFile file, String path, boolean defaultValue) {
        Object value = file.get(path, defaultValue);
        return value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(String.valueOf(value));
    }

    private static int integer(ConfigurableFile file, String path, int defaultValue) {
        Object value = file.get(path, defaultValue);
        if (value instanceof Number) return Math.max(1, ((Number) value).intValue());

        try {
            return Math.max(1, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String string(ConfigurableFile file, String path, String defaultValue) {
        Object value = file.get(path, defaultValue);
        return value == null ? defaultValue : String.valueOf(value);
    }
}
