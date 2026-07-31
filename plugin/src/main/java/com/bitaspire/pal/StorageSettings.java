package com.bitaspire.pal;

import com.bitaspire.pal.storage.StorageType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.croabeast.file.ConfigurableFile;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
final class StorageSettings {

    @NotNull
    private final StorageType type;

    @NotNull
    private final String tablePrefix;

    @NotNull
    private final File sqliteFile;

    @NotNull
    private final String host;

    private final int port;

    @NotNull
    private final String database;

    @NotNull
    private final String username;

    @NotNull
    private final String password;

    private final boolean ssl;

    static StorageSettings load(@NotNull PALPlugin plugin) throws IOException {
        ConfigurableFile file = new ConfigurableFile(plugin, "storage");
        file.saveDefaults();

        StorageType type = StorageType.from(string(file, "storage.type", "SQLITE"));
        String prefix = string(file, "storage.table-prefix", "pal_");
        File sqlite = resolve(plugin, string(file, "storage.sqlite.file", "plugins/PAL/database.db"));
        String host = string(file, "storage.remote.host", "localhost");
        int port = integer(file, "storage.remote.port", defaultPort(type));
        String database = string(file, "storage.remote.database", "pal");
        String username = string(file, "storage.remote.username", "username");
        String password = string(file, "storage.remote.password", "password");
        boolean ssl = bool(file, "storage.remote.ssl", true);

        return new StorageSettings(type, sanitizePrefix(prefix), sqlite, host, port, database, username, password, ssl);
    }

    @NotNull
    String table(@NotNull String name) {
        return tablePrefix + name;
    }

    private static File resolve(@NotNull Plugin plugin, @NotNull String path) {
        File file = new File(path);
        return file.isAbsolute() ? file : new File(plugin.getServer().getWorldContainer(), path);
    }

    private static String sanitizePrefix(String prefix) {
        return prefix == null || prefix.trim().isEmpty()
                ? "pal_"
                : prefix.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private static String string(ConfigurableFile file, String path, String defaultValue) {
        Object value = file.get(path, defaultValue);
        return value == null ? defaultValue : String.valueOf(value);
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

    private static boolean bool(ConfigurableFile file, String path, boolean defaultValue) {
        Object value = file.get(path, defaultValue);
        return value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(String.valueOf(value));
    }

    private static int defaultPort(StorageType type) {
        return type == StorageType.POSTGRESQL ? 5432 : 3306;
    }
}
