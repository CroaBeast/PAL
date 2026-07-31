package com.bitaspire.pal;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
final class MigrationOptions {

    private static final String[] DEFAULT_PROVIDERS = {
            "authme",
            "nlogin",
            "openlogin",
            "librelogin",
            "loginsecurity",
            "fastlogin"
    };

    private final boolean enabled;
    private final boolean backup;

    @NotNull
    private final Map<String, MigrationProviderOptions> providers;

    @NotNull
    static MigrationOptions load(@NotNull PALPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "storage.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection migrations = config.getConfigurationSection("migrations");

        boolean enabled = migrations == null || migrations.getBoolean("enabled", true);
        boolean backup = migrations != null && migrations.getBoolean("backup-before-import", true);
        ConfigurationSection providersSection = migrations == null ? null : migrations.getConfigurationSection("providers");

        Map<String, MigrationProviderOptions> providers = new LinkedHashMap<>();
        for (String provider : DEFAULT_PROVIDERS) {
            providers.put(provider, providersSection == null
                    ? MigrationProviderOptions.load(plugin, provider, config.createSection("__empty"))
                    : MigrationProviderOptions.load(plugin, provider, providersSection));
        }

        if (providersSection != null) {
            for (String key : providersSection.getKeys(false)) {
                if (providers.containsKey(key.toLowerCase())) continue;
                providers.put(key.toLowerCase(), MigrationProviderOptions.load(plugin, key, providersSection));
            }
        }

        return new MigrationOptions(enabled, backup, providers);
    }
}
