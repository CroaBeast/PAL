package com.bitaspire.pal;

import com.bitaspire.pal.account.PALAccount;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
final class MigrationProviderOptions {

    @NotNull
    private final String key;

    private final boolean enabled;

    @NotNull
    private final MigrationSourceOptions source;

    @NotNull
    private final String table;

    @NotNull
    private final String nameColumn;

    @Nullable
    private final String uuidColumn;

    @Nullable
    private final String passwordColumn;

    @Nullable
    private final String addressColumn;

    @Nullable
    private final String registeredAtColumn;

    @Nullable
    private final String lastLoginAtColumn;

    @Nullable
    private final String premiumColumn;

    @NotNull
    private final PALAccount.Type accountType;

    private final boolean overwrite;

    @NotNull
    private final String passwordAlgorithm;

    @NotNull
    static MigrationProviderOptions load(@NotNull Plugin plugin, @NotNull String key,
                                         @NotNull ConfigurationSection providersSection) {
        MigrationProviderOptions fallback = defaults(plugin, key);

        Object raw = providersSection.get(key);
        if (raw instanceof Boolean) {
            return new MigrationProviderOptions(
                    key,
                    (Boolean) raw,
                    fallback.getSource(),
                    fallback.getTable(),
                    fallback.getNameColumn(),
                    fallback.getUuidColumn(),
                    fallback.getPasswordColumn(),
                    fallback.getAddressColumn(),
                    fallback.getRegisteredAtColumn(),
                    fallback.getLastLoginAtColumn(),
                    fallback.getPremiumColumn(),
                    fallback.getAccountType(),
                    fallback.isOverwrite(),
                    fallback.getPasswordAlgorithm()
            );
        }

        ConfigurationSection section = providersSection.getConfigurationSection(key);
        if (section == null) return fallback;

        ConfigurationSection sourceSection = section.getConfigurationSection("source");
        MigrationSourceOptions source = sourceSection == null
                ? fallback.getSource()
                : MigrationSourceOptions.load(plugin, sourceSection, fallback.getSource());

        return new MigrationProviderOptions(
                key,
                section.getBoolean("enabled", fallback.isEnabled()),
                source,
                section.getString("table", fallback.getTable()),
                string(section, "columns.name", fallback.getNameColumn()),
                optional(section, "columns.uuid", fallback.getUuidColumn()),
                optional(section, "columns.password", fallback.getPasswordColumn()),
                optional(section, "columns.address", fallback.getAddressColumn()),
                optional(section, "columns.registered-at", fallback.getRegisteredAtColumn()),
                optional(section, "columns.last-login-at", fallback.getLastLoginAtColumn()),
                optional(section, "columns.premium", fallback.getPremiumColumn()),
                accountType(section.getString("account-type", fallback.getAccountType().name()), fallback.getAccountType()),
                section.getBoolean("overwrite", fallback.isOverwrite()),
                section.getString("password-algorithm", fallback.getPasswordAlgorithm())
        );
    }

    @NotNull
    private static MigrationProviderOptions defaults(@NotNull Plugin plugin, @NotNull String key) {
        String normalized = key.toLowerCase(Locale.ROOT);

        if ("authme".equals(normalized)) {
            return option(plugin, key, "plugins/AuthMe/authme.db", "authme", "username", null,
                    "password", "ip", "regdate", "lastlogin", null, PALAccount.Type.OFFLINE);
        }

        if ("nlogin".equals(normalized)) {
            return option(plugin, key, "plugins/nLogin/data.db", "nlogin", "name", "uuid",
                    "password", "last_ip", "created_at", "last_login", "premium", PALAccount.Type.OFFLINE);
        }

        if ("openlogin".equals(normalized)) {
            return option(plugin, key, "plugins/OpeNLogin/database.db", "openlogin", "name", "uuid",
                    "password", "ip", "register_date", "last_login", "premium", PALAccount.Type.OFFLINE);
        }

        if ("librelogin".equals(normalized)) {
            return option(plugin, key, "plugins/LibreLogin/accounts.db", "librelogin_accounts", "last_name", "uuid",
                    "password", "last_ip", "created", "last_login", "premium", PALAccount.Type.OFFLINE);
        }

        if ("loginsecurity".equals(normalized)) {
            return option(plugin, key, "plugins/LoginSecurity/database.db", "ls_players", "name", "uuid",
                    "password", "ip", "registered", "last_login", null, PALAccount.Type.OFFLINE);
        }

        if ("fastlogin".equals(normalized)) {
            return option(plugin, key, "plugins/FastLogin/fastlogin.db", "fastlogin", "name", "uuid",
                    null, null, "firstlogin", "lastlogin", "premium", PALAccount.Type.PREMIUM);
        }

        return option(plugin, key, "plugins/" + key + "/database.db", key, "name", "uuid",
                "password", "ip", "registered_at", "last_login_at", "premium", PALAccount.Type.OFFLINE);
    }

    @NotNull
    private static MigrationProviderOptions option(@NotNull Plugin plugin, @NotNull String key,
                                                   @NotNull String path, @NotNull String table,
                                                   @NotNull String nameColumn, @Nullable String uuidColumn,
                                                   @Nullable String passwordColumn, @Nullable String addressColumn,
                                                   @Nullable String registeredAtColumn, @Nullable String lastLoginAtColumn,
                                                   @Nullable String premiumColumn, @NotNull PALAccount.Type accountType) {
        return new MigrationProviderOptions(
                key,
                true,
                MigrationSourceOptions.sqlite(plugin, path),
                table,
                nameColumn,
                uuidColumn,
                passwordColumn,
                addressColumn,
                registeredAtColumn,
                lastLoginAtColumn,
                premiumColumn,
                accountType,
                false,
                "LEGACY_" + key.toUpperCase(Locale.ROOT).replace('-', '_')
        );
    }

    @NotNull
    private static PALAccount.Type accountType(@Nullable String value, @NotNull PALAccount.Type fallback) {
        if (value == null || value.trim().isEmpty() || "AUTO".equalsIgnoreCase(value)) return fallback;

        try {
            return PALAccount.Type.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    @NotNull
    private static String string(@NotNull ConfigurationSection section, @NotNull String path, @NotNull String fallback) {
        String value = section.getString(path);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    @Nullable
    private static String optional(@NotNull ConfigurationSection section, @NotNull String path, @Nullable String fallback) {
        String value = section.getString(path);
        if (value == null) return fallback;
        value = value.trim();
        return value.isEmpty() ? null : value;
    }
}
