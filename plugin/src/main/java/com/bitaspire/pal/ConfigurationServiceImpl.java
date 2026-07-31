package com.bitaspire.pal;

import lombok.RequiredArgsConstructor;
import me.croabeast.file.ConfigurableFile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

@RequiredArgsConstructor
final class ConfigurationServiceImpl {

    private static final String[] FILES = {
            "config",
            "commands",
            "messages",
            "security",
            "storage",
            "premium",
            "bridge",
            "integrations"
    };

    private final PALPlugin plugin;

    public void saveDefaults() {
        for (String file : FILES) {
            try {
                new ConfigurableFile(plugin, file).saveDefaults();
            } catch (Exception exception) {
                plugin.getLogger().warning("Could not save " + file + ".yml: " + exception.getMessage());
            }
        }
    }

    public void reload() {
        saveDefaults();
        validate();
    }

    void validate() {
        validateCommands();
        validateStorage();
        validateBridge();
    }

    private void validateCommands() {
        YamlConfiguration config = load("commands");
        ConfigurationSection subcommands = config.getConfigurationSection("commands.pal.subcommands");
        if (subcommands == null || subcommands.getKeys(false).isEmpty()) {
            plugin.getLogger().warning("commands.yml should define commands.pal.subcommands for dynamic admin tooling.");
        }
    }

    private void validateStorage() {
        YamlConfiguration config = load("storage");
        String type = config.getString("storage.type", "SQLITE");
        if (!"SQLITE".equalsIgnoreCase(type)) {
            String username = config.getString("storage.remote.username", "username");
            String password = config.getString("storage.remote.password", "password");
            if ("username".equalsIgnoreCase(username) || "password".equals(password)) {
                plugin.getLogger().warning("storage.yml remote credentials still look like placeholders.");
            }
        }

        if (config.getBoolean("migrations.enabled", true)
                && config.getConfigurationSection("migrations.providers") == null) {
            plugin.getLogger().warning("migrations.enabled is true, but migrations.providers is missing.");
        }
    }

    private void validateBridge() {
        YamlConfiguration config = load("bridge");
        if (!config.getBoolean("bridge.enabled", false)) return;

        String mode = config.getString("bridge.mode", "REDIS");
        String secret = config.getString("bridge.sec.secret", "change-me");
        if ("REDIS".equalsIgnoreCase(mode) && "change-me".equalsIgnoreCase(secret)) {
            plugin.getLogger().warning("bridge.yml uses the default Redis shared secret. Change bridge.sec.secret before production.");
        }

        if ("MEMORY".equalsIgnoreCase(mode)) {
            plugin.getLogger().warning("bridge.yml MEMORY mode is local-only and does not synchronize Bukkit with proxy addons.");
        }

        if ("DATABASE".equalsIgnoreCase(mode) && "SQLITE".equalsIgnoreCase(load("storage").getString("storage.type", "SQLITE"))) {
            plugin.getLogger().warning("bridge.yml DATABASE mode should use shared remote SQL for network setups, not SQLite.");
        }
    }

    private YamlConfiguration load(String name) {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), name + ".yml"));
    }
}
