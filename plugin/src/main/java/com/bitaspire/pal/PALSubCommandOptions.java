package com.bitaspire.pal;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class PALSubCommandOptions {

    private final String key;
    private final String permission;
    private final String permissionDefault;
    private final String description;
    private final boolean enabled;

    @NotNull
    static PALSubCommandOptions from(@NotNull String commandPermission, @NotNull String key, @NotNull ConfigurationSection section) {
        return new PALSubCommandOptions(
                key,
                string(section, "permission", commandPermission + "." + key),
                string(section, "default", "op"),
                string(section, "description", "PAL subcommand: " + key),
                section.getBoolean("enabled", true)
        );
    }

    @NotNull
    static PALSubCommandOptions from(@NotNull String commandPermission, @NotNull String key, boolean enabled) {
        return new PALSubCommandOptions(
                key,
                commandPermission + "." + key,
                "op",
                "PAL subcommand: " + key,
                enabled
        );
    }

    boolean isDefaultPermitted(boolean operator) {
        switch (permissionDefault.toLowerCase()) {
            case "true":
                return true;
            case "op":
                return operator;
            case "not-op":
            case "not_op":
                return !operator;
            default:
                return false;
        }
    }

    private static String string(ConfigurationSection section, String path, String fallback) {
        String value = section.getString(path);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
