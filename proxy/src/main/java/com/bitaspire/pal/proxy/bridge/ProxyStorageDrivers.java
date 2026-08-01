package com.bitaspire.pal.proxy.bridge;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Set;

/**
 * Resolves JDBC drivers at runtime instead of shipping them inside the proxy jar.
 * <p>
 * BungeeCord downloads them through the {@code libraries} entry in bungee.yml, so its addon
 * never calls {@link #install(File, ClasspathInjector)} and the driver is already on the class
 * path. Velocity has no such mechanism, so its addon installs an injector backed by
 * {@code PluginManager#addToClasspath} and the jar is fetched here.
 */
@UtilityClass
public class ProxyStorageDrivers {

    private final String CENTRAL = "https://repo.maven.apache.org/maven2/";
    private final Set<ProxyBridgeConfig.DatabaseType> RESOLVED = EnumSet.noneOf(ProxyBridgeConfig.DatabaseType.class);

    private ClasspathInjector injector = null;
    private File dataFolder = null;

    public interface ClasspathInjector {
        void inject(@NotNull File jar) throws Exception;
    }

    /**
     * Installed by platforms that can attach a jar to their own class path. Platforms whose
     * proxy already resolves the driver, such as BungeeCord, never call this.
     */
    public void install(@NotNull File folder, @NotNull ClasspathInjector classpathInjector) {
        dataFolder = folder;
        injector = classpathInjector;
    }

    void resolve(@NotNull ProxyBridgeConfig.DatabaseType type) throws SQLException {
        if (RESOLVED.contains(type)) return;

        String driver = driverClass(type);
        if (register(driver)) {
            RESOLVED.add(type);
            return;
        }

        String coordinates = groupId(type) + ':' + artifactId(type) + ':' + version(type);
        if (injector == null || dataFolder == null)
            throw new SQLException("PAL database driver is not available: " + driver +
                    ". Add " + coordinates + " to the proxy class path.");

        try {
            injector.inject(download(type));
        } catch (Exception exception) {
            throw new SQLException("Could not download the " + type + " driver (" + coordinates + ')', exception);
        }

        if (!register(driver))
            throw new SQLException("PAL database driver is not available after download: " + driver +
                    ". Add " + coordinates + " to the proxy class path.");

        RESOLVED.add(type);
    }

    @NotNull
    private File download(@NotNull ProxyBridgeConfig.DatabaseType type) throws Exception {
        String name = artifactId(type) + '-' + version(type) + ".jar";

        File folder = new File(dataFolder, "drivers");
        File target = new File(folder, name);
        if (target.isFile() && target.length() > 0) return target;

        if (!folder.isDirectory() && !folder.mkdirs())
            throw new IllegalStateException("Could not create driver folder: " + folder.getAbsolutePath());

        URL url = URI.create(CENTRAL + groupId(type).replace('.', '/') + '/' +
                artifactId(type) + '/' + version(type) + '/' + name).toURL();

        File temp = new File(folder, name + ".tmp");
        try (InputStream input = url.openStream()) {
            Files.copy(input, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        if (temp.length() == 0) {
            temp.delete();
            throw new IllegalStateException("Downloaded an empty driver jar from " + url);
        }

        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private boolean register(@NotNull String driver) {
        try {
            Class.forName(driver);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    @NotNull
    private String driverClass(@NotNull ProxyBridgeConfig.DatabaseType type) {
        switch (type) {
            case SQLITE: return "org.sqlite.JDBC";
            case MARIADB: return "org.mariadb.jdbc.Driver";
            case POSTGRESQL: return "org.postgresql.Driver";
            default: return "com.mysql.cj.jdbc.Driver";
        }
    }

    @NotNull
    private String groupId(@NotNull ProxyBridgeConfig.DatabaseType type) {
        switch (type) {
            case SQLITE: return "org.xerial";
            case MARIADB: return "org.mariadb.jdbc";
            case POSTGRESQL: return "org.postgresql";
            default: return "com.mysql";
        }
    }

    @NotNull
    private String artifactId(@NotNull ProxyBridgeConfig.DatabaseType type) {
        switch (type) {
            case SQLITE: return "sqlite-jdbc";
            case MARIADB: return "mariadb-java-client";
            case POSTGRESQL: return "postgresql";
            default: return "mysql-connector-j";
        }
    }

    @NotNull
    private String version(@NotNull ProxyBridgeConfig.DatabaseType type) {
        switch (type) {
            case SQLITE: return "3.53.1.0";
            case MARIADB: return "3.3.3";
            case POSTGRESQL: return "42.7.3";
            default: return "8.0.33";
        }
    }
}
