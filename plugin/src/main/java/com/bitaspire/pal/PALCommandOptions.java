package com.bitaspire.pal;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class PALCommandOptions {

    private final String key;
    private final String name;
    private final String permission;
    private final String description;
    private final String usage;
    private final String permissionMessage;
    private final String permissionDefault;
    private final List<String> aliases;
    private final Map<String, PALSubCommandOptions> subCommands;
    private final boolean enabled;
    private final boolean overrideExisting;
    private final boolean requireConfirmation;

    @NotNull
    static PALCommandOptions from(@NotNull String key, @NotNull ConfigurationSection section) {
        return from(key, section, null);
    }

    @NotNull
    static PALCommandOptions from(@NotNull String key, @NotNull ConfigurationSection section, @Nullable String legacyKey) {
        String name = string(section, "name", key);
        String permission = string(section, "permission", "pal.command." + key);
        String usage = string(section, "usage", "/" + name);

        // Sections written before a command was renamed keep the old label, permission node and
        // usage string; only values still left at the old defaults are moved to the new key.
        if (legacyKey != null) {
            if (name.equalsIgnoreCase(legacyKey)) name = key;
            if (permission.equalsIgnoreCase("pal.command." + legacyKey)) permission = "pal.command." + key;

            String legacyUsage = "/" + legacyKey;
            if (usage.equalsIgnoreCase(legacyUsage) || usage.toLowerCase().startsWith(legacyUsage + " "))
                usage = "/" + key + usage.substring(legacyUsage.length());
        }

        List<String> aliases = new ArrayList<>();

        for (String alias : section.getStringList("aliases")) {
            if (isBlank(alias) || alias.equalsIgnoreCase(name)) continue;
            aliases.add(alias);
        }

        return new PALCommandOptions(
                key,
                name,
                permission,
                string(section, "description", "PAL command: " + name),
                usage,
                string(section, "permission-message", "general.no-permission"),
                string(section, "default", defaultPermission(key)),
                Collections.unmodifiableList(aliases),
                Collections.unmodifiableMap(loadSubCommands(permission, section)),
                section.getBoolean("enabled", true),
                section.getBoolean("override-existing", false),
                section.getBoolean("require-confirmation", false)
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
        return isBlank(value) ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String defaultPermission(String key) {
        return "pal".equalsIgnoreCase(key) ? "op" : "true";
    }

    private static Map<String, PALSubCommandOptions> loadSubCommands(String permission, ConfigurationSection section) {
        Map<String, PALSubCommandOptions> commands = new LinkedHashMap<>();
        ConfigurationSection subCommands = section.getConfigurationSection("subcommands");
        if (subCommands == null) return commands;

        for (String key : subCommands.getKeys(false)) {
            PALSubCommandOptions options = subCommands.isConfigurationSection(key) ?
                    PALSubCommandOptions.from(permission, key, subCommands.getConfigurationSection(key)) :
                    PALSubCommandOptions.from(permission, key, subCommands.getBoolean(key, true));

            commands.put(key.toLowerCase(), options);
        }

        return commands;
    }
}
