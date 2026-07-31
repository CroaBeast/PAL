package com.bitaspire.pal;

import com.bitaspire.pal.storage.StorageType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;

@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
final class MigrationSourceOptions {

    @NotNull
    private final StorageType type;

    @NotNull
    private final File file;

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

    @NotNull
    static MigrationSourceOptions load(@NotNull Plugin plugin, @NotNull ConfigurationSection section,
                                       @NotNull MigrationSourceOptions fallback) {
        StorageType type = StorageType.from(section.getString("type", fallback.getType().name()));
        String file = section.getString("file", fallback.getFile().getPath());
        String host = section.getString("host", fallback.getHost());
        int port = Math.max(1, section.getInt("port", fallback.getPort()));
        String database = section.getString("database", fallback.getDatabase());
        String username = section.getString("username", fallback.getUsername());
        String password = section.getString("password", fallback.getPassword());
        boolean ssl = section.getBoolean("ssl", fallback.isSsl());

        return new MigrationSourceOptions(type, resolve(plugin, file), host, port, database, username, password, ssl);
    }

    @NotNull
    static MigrationSourceOptions sqlite(@NotNull Plugin plugin, @NotNull String path) {
        return new MigrationSourceOptions(
                StorageType.SQLITE,
                resolve(plugin, path),
                "localhost",
                3306,
                "pal",
                "username",
                "password",
                false
        );
    }

    @NotNull
    private static File resolve(@NotNull Plugin plugin, @NotNull String path) {
        File value = new File(path);
        return value.isAbsolute() ? value : new File(plugin.getServer().getWorldContainer(), path);
    }
}
