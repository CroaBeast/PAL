package com.bitaspire.pal;

import com.bitaspire.pal.storage.StorageType;
import lombok.experimental.UtilityClass;
import me.croabeast.common.DependencyLoader;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Set;

/**
 * Resolves JDBC drivers at runtime instead of shipping them inside the plugin jar.
 * <p>
 * Spigot 1.16.5+ downloads them through the {@code libraries} entry in plugin.yml; older
 * servers ignore that entry, so the driver is fetched here through Takion's dependency
 * loader, which injects the jar into this plugin's class loader.
 */
@UtilityClass
class StorageDrivers {

    private final Set<StorageType> RESOLVED = EnumSet.noneOf(StorageType.class);

    void resolve(@NotNull Plugin plugin, @NotNull StorageType type) throws SQLException {
        if (RESOLVED.contains(type)) return;

        String driver = driverClass(type);
        if (driver == null) return;

        if (register(driver)) {
            RESOLVED.add(type);
            return;
        }

        boolean downloaded = DependencyLoader
                .fromFolder(plugin.getDataFolder(), "drivers")
                .load(groupId(type), artifactId(type), version(type));

        if (!downloaded || !register(driver))
            throw new SQLException(
                    "Could not load the " + type + " driver (" + driver + "). Add " +
                    groupId(type) + ':' + artifactId(type) + ':' + version(type) +
                    " to the server class path manually if this server has no internet access."
            );

        RESOLVED.add(type);
    }

    private boolean register(@NotNull String driver) {
        try {
            Class.forName(driver);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private String driverClass(@NotNull StorageType type) {
        switch (type) {
            case SQLITE: return "org.sqlite.JDBC";
            case MYSQL: return "com.mysql.cj.jdbc.Driver";
            case MARIADB: return "org.mariadb.jdbc.Driver";
            case POSTGRESQL: return "org.postgresql.Driver";
            default: return null;
        }
    }

    private String groupId(@NotNull StorageType type) {
        switch (type) {
            case SQLITE: return "org.xerial";
            case MYSQL: return "com.mysql";
            case MARIADB: return "org.mariadb.jdbc";
            default: return "org.postgresql";
        }
    }

    private String artifactId(@NotNull StorageType type) {
        switch (type) {
            case SQLITE: return "sqlite-jdbc";
            case MYSQL: return "mysql-connector-j";
            case MARIADB: return "mariadb-java-client";
            default: return "postgresql";
        }
    }

    private String version(@NotNull StorageType type) {
        switch (type) {
            case SQLITE: return "3.53.1.0";
            case MYSQL: return "8.0.33";
            case MARIADB: return "3.3.3";
            default: return "42.7.3";
        }
    }
}
