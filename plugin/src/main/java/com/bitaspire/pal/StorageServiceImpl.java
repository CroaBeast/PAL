package com.bitaspire.pal;

import com.bitaspire.pal.account.AccountNameLock;
import com.bitaspire.pal.account.PALAccount;
import com.bitaspire.pal.auth.AuthSource;
import com.bitaspire.pal.auth.PasswordHash;
import com.bitaspire.pal.session.AuthSession;
import com.bitaspire.pal.storage.StorageService;
import com.bitaspire.pal.storage.StorageType;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class StorageServiceImpl extends AbstractService implements StorageService {

    @Getter
    @NotNull
    private StorageType type = StorageType.SQLITE;

    private StorageSettings settings;
    private Connection connection;
    private ExecutorService executor;
    private boolean connected = false;

    StorageServiceImpl(@NotNull PALApi api) {
        super(api);
    }

    @Override
    public boolean register() {
        super.register();

        try {
            connect().toCompletableFuture().join();
        } catch (Exception exception) {
            connected = false;
            ((PALPlugin) api.getPlugin()).getLogger().severe("Could not initialize storage: " + rootMessage(exception));
        }

        return connected;
    }

    void reload() {
        if (isRegistered()) connect().toCompletableFuture().join();
        else loadSettings();
    }

    @Override
    public boolean unregister() {
        try {
            close().toCompletableFuture().join();
        } catch (Exception exception) {
            ((PALPlugin) api.getPlugin()).getLogger().warning("Could not shutdown storage cleanly: " + rootMessage(exception));
        }

        return super.unregister();
    }

    @NotNull
    public CompletionStage<Void> connect() {
        loadSettings();

        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "PAL Storage");
                thread.setDaemon(true);
                return thread;
            });
        }

        return CompletableFuture.runAsync(() -> {
            try {
                closeConnection();
                connectJdbc();
                migrate();
                connected = true;
            } catch (SQLException exception) {
                connected = false;
                throw new CompletionException(exception);
            }
        }, executor);
    }

    @NotNull
    public CompletionStage<Void> close() {
        ExecutorService currentExecutor = executor;
        if (currentExecutor == null) {
            closeConnection();
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = CompletableFuture.runAsync(this::closeConnection, currentExecutor);
        future.whenComplete((ignored, throwable) -> currentExecutor.shutdown());
        executor = null;
        return future;
    }

    @NotNull
    CompletionStage<Optional<PALAccount>> findAccountById(@NotNull UUID uniqueId) {
        return supply(() -> findAccount("unique_id = ?", uniqueId.toString()));
    }

    @NotNull
    CompletionStage<Optional<PALAccount>> findAccountByName(@NotNull String name) {
        return supply(() -> findAccount("name_key = ?", key(name)));
    }

    @NotNull
    CompletionStage<Void> saveAccount(@NotNull PALAccount account) {
        return run(() -> {
            long now = System.currentTimeMillis();

            try (PreparedStatement statement = connection.prepareStatement(
                    upsert(
                            "accounts",
                            "unique_id",
                            columns("unique_id", "name", "name_key", "type", "status", "last_address", "registered_at", "last_login_at", "created_at", "updated_at"),
                            columns("name", "name_key", "type", "status", "last_address", "registered_at", "last_login_at", "updated_at")
                    )
            )) {
                statement.setString(1, account.getUniqueId().toString());
                statement.setString(2, account.getName());
                statement.setString(3, key(account.getName()));
                statement.setString(4, account.getType().name());
                statement.setString(5, account.getStatus().name());
                statement.setString(6, address(account.getLastAddress()));
                setInstant(statement, 7, account.getRegisteredAt());
                setInstant(statement, 8, account.getLastLoginAt());
                statement.setLong(9, now);
                statement.setLong(10, now);
                statement.executeUpdate();
            }
        });
    }

    @NotNull
    CompletionStage<Boolean> deleteAccount(@NotNull UUID uniqueId) {
        return supply(() -> {
            deleteByUniqueId("credentials", uniqueId);
            deleteByUniqueId("sessions", uniqueId);
            deleteByUniqueId("two_factor_backup_codes", uniqueId);
            deleteByUniqueId("two_factor", uniqueId);

            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + table("accounts") + " WHERE unique_id = ?"
            )) {
                statement.setString(1, uniqueId.toString());
                return statement.executeUpdate() > 0;
            }
        });
    }

    private void deleteByUniqueId(@NotNull String sourceTable, @NotNull UUID uniqueId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + table(sourceTable) + " WHERE unique_id = ?"
        )) {
            statement.setString(1, uniqueId.toString());
            statement.executeUpdate();
        }
    }

    @NotNull
    CompletionStage<Optional<AccountNameLock>> findNameLockByName(@NotNull String name) {
        return supply(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT name, type, unique_id, source, created_at, expires_at " +
                            "FROM " + table("name_locks") + " WHERE name_key = ? " +
                            "AND (expires_at IS NULL OR expires_at > ?) LIMIT 1"
            )) {
                statement.setString(1, key(name));
                statement.setLong(2, System.currentTimeMillis());

                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return Optional.empty();
                    return Optional.of(readNameLock(result));
                }
            }
        });
    }

    @NotNull
    CompletionStage<AccountNameLock> saveNameLock(@NotNull AccountNameLock lock) {
        return run(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    upsert(
                            "name_locks",
                            "name_key",
                            columns("name_key", "name", "type", "unique_id", "source", "created_at", "expires_at"),
                            columns("name", "type", "unique_id", "source", "created_at", "expires_at")
                    )
            )) {
                statement.setString(1, key(lock.getName()));
                statement.setString(2, lock.getName());
                statement.setString(3, lock.getType().name());
                statement.setString(4, lock.getUniqueId() == null ? null : lock.getUniqueId().toString());
                statement.setString(5, lock.getSource());
                statement.setLong(6, lock.getCreatedAt().toEpochMilli());
                setInstant(statement, 7, lock.getExpiresAt());
                statement.executeUpdate();
            }
        }).thenApply(ignored -> lock);
    }

    @NotNull
    CompletionStage<Boolean> deleteNameLock(@NotNull String name) {
        return supply(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + table("name_locks") + " WHERE name_key = ?"
            )) {
                statement.setString(1, key(name));
                return statement.executeUpdate() > 0;
            }
        });
    }

    @NotNull
    CompletionStage<Optional<PasswordHash>> findPasswordHash(@NotNull UUID uniqueId) {
        return supply(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT algorithm, encoded, version FROM " + table("credentials") + " WHERE unique_id = ?"
            )) {
                statement.setString(1, uniqueId.toString());

                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return Optional.empty();

                    return Optional.of(new StoredPasswordHash(
                            result.getString("algorithm"),
                            result.getString("encoded"),
                            result.getInt("version")
                    ));
                }
            }
        });
    }

    @NotNull
    CompletionStage<Void> savePasswordHash(@NotNull UUID uniqueId, @NotNull PasswordHash hash) {
        return run(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    upsert(
                            "credentials",
                            "unique_id",
                            columns("unique_id", "algorithm", "encoded", "version", "updated_at"),
                            columns("algorithm", "encoded", "version", "updated_at")
                    )
            )) {
                statement.setString(1, uniqueId.toString());
                statement.setString(2, hash.getAlgorithm());
                statement.setString(3, hash.getEncoded());
                statement.setInt(4, hash.getVersion());
                statement.setLong(5, System.currentTimeMillis());
                statement.executeUpdate();
            }
        });
    }

    @NotNull
    CompletionStage<Optional<StoredTwoFactor>> findTwoFactor(@NotNull UUID uniqueId) {
        return supply(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT unique_id, secret, enabled, created_at, updated_at FROM " + table("two_factor") +
                            " WHERE unique_id = ? AND enabled = 1"
            )) {
                statement.setString(1, uniqueId.toString());

                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return Optional.empty();

                    return Optional.of(new StoredTwoFactor(
                            UUID.fromString(result.getString("unique_id")),
                            result.getString("secret"),
                            result.getInt("enabled") == 1,
                            Instant.ofEpochMilli(result.getLong("created_at")),
                            Instant.ofEpochMilli(result.getLong("updated_at"))
                    ));
                }
            }
        });
    }

    @NotNull
    CompletionStage<Void> saveTwoFactor(@NotNull UUID uniqueId, @NotNull String secret) {
        return run(() -> {
            long now = System.currentTimeMillis();

            try (PreparedStatement statement = connection.prepareStatement(
                    upsert(
                            "two_factor",
                            "unique_id",
                            columns("unique_id", "secret", "enabled", "created_at", "updated_at"),
                            columns("secret", "enabled", "updated_at")
                    )
            )) {
                statement.setString(1, uniqueId.toString());
                statement.setString(2, secret);
                statement.setInt(3, 1);
                statement.setLong(4, now);
                statement.setLong(5, now);
                statement.executeUpdate();
            }
        });
    }

    @NotNull
    CompletionStage<Void> deleteTwoFactor(@NotNull UUID uniqueId) {
        return run(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + table("two_factor_backup_codes") + " WHERE unique_id = ?"
            )) {
                statement.setString(1, uniqueId.toString());
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + table("two_factor") + " WHERE unique_id = ?"
            )) {
                statement.setString(1, uniqueId.toString());
                statement.executeUpdate();
            }
        });
    }

    @NotNull
    CompletionStage<Void> saveBackupCodes(@NotNull UUID uniqueId, @NotNull List<PasswordHash> hashes) {
        return run(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + table("two_factor_backup_codes") + " WHERE unique_id = ?"
            )) {
                statement.setString(1, uniqueId.toString());
                statement.executeUpdate();
            }

            for (PasswordHash hash : hashes) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO " + table("two_factor_backup_codes") +
                                " (unique_id, algorithm, encoded, version, created_at, used_at) VALUES (?, ?, ?, ?, ?, NULL)"
                )) {
                    statement.setString(1, uniqueId.toString());
                    statement.setString(2, hash.getAlgorithm());
                    statement.setString(3, hash.getEncoded());
                    statement.setInt(4, hash.getVersion());
                    statement.setLong(5, System.currentTimeMillis());
                    statement.executeUpdate();
                }
            }
        });
    }

    @NotNull
    CompletionStage<List<StoredBackupCode>> findBackupCodes(@NotNull UUID uniqueId) {
        return supply(() -> {
            List<StoredBackupCode> codes = new ArrayList<>();

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, algorithm, encoded, version FROM " + table("two_factor_backup_codes") +
                            " WHERE unique_id = ? AND used_at IS NULL"
            )) {
                statement.setString(1, uniqueId.toString());

                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        codes.add(new StoredBackupCode(
                                result.getLong("id"),
                                new StoredPasswordHash(
                                        result.getString("algorithm"),
                                        result.getString("encoded"),
                                        result.getInt("version")
                                )
                        ));
                    }
                }
            }

            return codes;
        });
    }

    @NotNull
    CompletionStage<Void> markBackupCodeUsed(long id) {
        return run(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE " + table("two_factor_backup_codes") + " SET used_at = ? WHERE id = ? AND used_at IS NULL"
            )) {
                statement.setLong(1, System.currentTimeMillis());
                statement.setLong(2, id);
                statement.executeUpdate();
            }
        });
    }

    @NotNull
    CompletionStage<List<AuthSession>> findActiveSessions(long nowMillis) {
        return supply(() -> {
            List<AuthSession> sessions = new ArrayList<>();

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT unique_id, name, session_id, source, address, address_hash, auth_at, expires_at " +
                            "FROM " + table("sessions") +
                            " WHERE expires_at IS NULL OR expires_at > ?"
            )) {
                statement.setLong(1, nowMillis);

                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) sessions.add(readSession(result));
                }
            }

            return sessions;
        });
    }

    @NotNull
    CompletionStage<Optional<String>> findBridgePayload(@NotNull UUID uniqueId, @NotNull String name) {
        return supply(() -> {
            Optional<String> byId = findBridgePayload("unique_id = ?", uniqueId.toString());
            return byId.isPresent() ? byId : findBridgePayload("name_key = ?", key(name));
        });
    }

    @NotNull
    private Optional<String> findBridgePayload(@NotNull String where, @NotNull String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT bridge_payload FROM " + table("sessions") +
                        " WHERE " + where + " AND bridge_payload IS NOT NULL " +
                        "AND (expires_at IS NULL OR expires_at > ?) LIMIT 1"
        )) {
            statement.setString(1, value);
            statement.setLong(2, System.currentTimeMillis());

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();

                String payload = result.getString("bridge_payload");
                return payload == null || payload.trim().isEmpty()
                        ? Optional.empty()
                        : Optional.of(payload);
            }
        }
    }

    @NotNull
    CompletionStage<Void> saveSession(@NotNull AuthSession session) {
        return saveSession(session, null);
    }

    @NotNull
    CompletionStage<Void> saveSession(@NotNull AuthSession session, @Nullable String bridgePayload) {
        return run(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    upsert(
                            "sessions",
                            "unique_id",
                            columns("unique_id", "name", "name_key", "session_id", "source", "address", "address_hash", "auth_at", "expires_at", "updated_at", "bridge_payload"),
                            columns("name", "name_key", "session_id", "source", "address", "address_hash", "auth_at", "expires_at", "updated_at", "bridge_payload")
                    )
            )) {
                statement.setString(1, session.getUniqueId().toString());
                statement.setString(2, session.getName());
                statement.setString(3, key(session.getName()));
                statement.setString(4, session.getSessionId());
                statement.setString(5, session.getSource().name());
                statement.setString(6, address(session.getAddress()));
                statement.setString(7, session.getAddressHash());
                statement.setLong(8, session.getAuthenticatedAt().toEpochMilli());
                setInstant(statement, 9, session.getExpiresAt());
                statement.setLong(10, System.currentTimeMillis());
                statement.setString(11, bridgePayload);
                statement.executeUpdate();
            }
        });
    }

    @NotNull
    CompletionStage<Void> deleteSession(@NotNull UUID uniqueId) {
        return run(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + table("sessions") + " WHERE unique_id = ?"
            )) {
                statement.setString(1, uniqueId.toString());
                statement.executeUpdate();
            }
        });
    }

    @NotNull
    CompletionStage<Void> audit(@Nullable UUID uniqueId, @Nullable String name, @NotNull String action,
                                @Nullable InetAddress address, @Nullable String detail) {
        return run(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + table("audit") + " (unique_id, name, action, address, detail, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?)"
            )) {
                statement.setString(1, uniqueId == null ? null : uniqueId.toString());
                statement.setString(2, name);
                statement.setString(3, action);
                statement.setString(4, address(address));
                statement.setString(5, detail);
                statement.setLong(6, System.currentTimeMillis());
                statement.executeUpdate();
            }
        });
    }

    @NotNull
    CompletionStage<Void> loginAttempt(@Nullable UUID uniqueId, @NotNull String name, @Nullable InetAddress address,
                                       boolean success, @Nullable String reason) {
        return run(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + table("login_attempts") + " (unique_id, name, address, success, reason, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?)"
            )) {
                statement.setString(1, uniqueId == null ? null : uniqueId.toString());
                statement.setString(2, name);
                statement.setString(3, address(address));
                statement.setInt(4, success ? 1 : 0);
                statement.setString(5, reason);
                statement.setLong(6, System.currentTimeMillis());
                statement.executeUpdate();
            }
        });
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    private void loadSettings() {
        PALPlugin plugin = (PALPlugin) api.getPlugin();

        try {
            settings = StorageSettings.load(plugin);
            type = settings.getType();
            warnIfDefaultRemoteCredentials(plugin);
        } catch (Exception exception) {
            settings = null;
            type = StorageType.SQLITE;
            plugin.getLogger().warning("Could not load storage.yml: " + exception.getMessage());
        }
    }

    private void warnIfDefaultRemoteCredentials(@NotNull PALPlugin plugin) {
        if (settings == null || type == StorageType.SQLITE) return;
        if (!"username".equalsIgnoreCase(settings.getUsername()) && !"password".equals(settings.getPassword())) return;

        plugin.getLogger().warning("PAL remote storage is using placeholder credentials in storage.yml.");
    }

    private void connectSQLite() throws SQLException {
        if (settings == null) throw new SQLException("Storage settings are not loaded");

        File file = settings.getSqliteFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Could not create SQLite directory: " + parent.getAbsolutePath());
        }

        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA journal_mode = WAL");
        }
    }

    private void connectJdbc() throws SQLException {
        if (type == StorageType.SQLITE) {
            connectSQLite();
            return;
        }

        if (settings == null) throw new SQLException("Storage settings are not loaded");

        String url;
        switch (type) {
            case MYSQL:
                url = "jdbc:mysql://" + settings.getHost() + ":" + settings.getPort() + "/" + settings.getDatabase()
                        + "?useSSL=" + settings.isSsl()
                        + "&verifyServerCertificate=false&useUnicode=true&characterEncoding=utf8&serverTimezone=UTC";
                break;
            case MARIADB:
                url = "jdbc:mariadb://" + settings.getHost() + ":" + settings.getPort() + "/" + settings.getDatabase()
                        + "?useSsl=" + settings.isSsl();
                break;
            case POSTGRESQL:
                url = "jdbc:postgresql://" + settings.getHost() + ":" + settings.getPort() + "/" + settings.getDatabase()
                        + "?ssl=" + settings.isSsl();
                break;
            default:
                throw new SQLException(type + " storage is not supported by the default PAL storage provider");
        }

        connection = DriverManager.getConnection(url, settings.getUsername(), settings.getPassword());
    }

    private void migrate() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("meta") + " (" +
                    "meta_key " + varchar(64) + " PRIMARY KEY, meta_value " + varchar(255) + " NOT NULL" +
                    ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("accounts") + " (" +
                    "id " + autoIncrement() + ", " +
                    "unique_id " + varchar(36) + " NOT NULL UNIQUE, " +
                    "name " + varchar(16) + " NOT NULL, " +
                    "name_key " + varchar(32) + " NOT NULL UNIQUE, " +
                    "type " + varchar(32) + " NOT NULL, " +
                    "status " + varchar(32) + " NOT NULL, " +
                    "last_address " + varchar(64) + ", " +
                    "registered_at BIGINT, " +
                    "last_login_at BIGINT, " +
                    "created_at BIGINT NOT NULL, " +
                    "updated_at BIGINT NOT NULL" +
                    ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("credentials") + " (" +
                    "unique_id " + varchar(36) + " PRIMARY KEY, " +
                    "algorithm " + varchar(64) + " NOT NULL, " +
                    "encoded TEXT NOT NULL, " +
                    "version INTEGER NOT NULL, " +
                    "updated_at BIGINT NOT NULL, " +
                    "FOREIGN KEY(unique_id) REFERENCES " + table("accounts") + "(unique_id) ON DELETE CASCADE" +
                    ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("two_factor") + " (" +
                    "unique_id " + varchar(36) + " PRIMARY KEY, " +
                    "secret " + varchar(128) + " NOT NULL, " +
                    "enabled INTEGER NOT NULL, " +
                    "created_at BIGINT NOT NULL, " +
                    "updated_at BIGINT NOT NULL, " +
                    "FOREIGN KEY(unique_id) REFERENCES " + table("accounts") + "(unique_id) ON DELETE CASCADE" +
                    ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("two_factor_backup_codes") + " (" +
                    "id " + autoIncrement() + ", " +
                    "unique_id " + varchar(36) + " NOT NULL, " +
                    "algorithm " + varchar(64) + " NOT NULL, " +
                    "encoded TEXT NOT NULL, " +
                    "version INTEGER NOT NULL, " +
                    "created_at BIGINT NOT NULL, " +
                    "used_at BIGINT, " +
                    "FOREIGN KEY(unique_id) REFERENCES " + table("accounts") + "(unique_id) ON DELETE CASCADE" +
                    ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("name_locks") + " (" +
                    "name_key " + varchar(32) + " PRIMARY KEY, " +
                    "name " + varchar(16) + " NOT NULL, " +
                    "type " + varchar(32) + " NOT NULL, " +
                    "unique_id " + varchar(36) + ", " +
                    "source " + varchar(64) + ", " +
                    "created_at BIGINT NOT NULL, " +
                    "expires_at BIGINT" +
                    ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("sessions") + " (" +
                    "unique_id " + varchar(36) + " PRIMARY KEY, " +
                    "name " + varchar(16) + " NOT NULL, " +
                    "name_key " + varchar(32) + " NOT NULL, " +
                    "session_id " + varchar(64) + " NOT NULL, " +
                    "source " + varchar(32) + " NOT NULL, " +
                    "address " + varchar(64) + ", " +
                    "address_hash " + varchar(128) + ", " +
                    "auth_at BIGINT NOT NULL, " +
                    "expires_at BIGINT, " +
                    "updated_at BIGINT NOT NULL, " +
                    "bridge_payload TEXT, " +
                    "FOREIGN KEY(unique_id) REFERENCES " + table("accounts") + "(unique_id) ON DELETE CASCADE" +
                    ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("login_attempts") + " (" +
                    "id " + autoIncrement() + ", " +
                    "unique_id " + varchar(36) + ", " +
                    "name " + varchar(16) + " NOT NULL, " +
                    "address " + varchar(64) + ", " +
                    "success INTEGER NOT NULL, " +
                    "reason TEXT, " +
                    "created_at BIGINT NOT NULL" +
                    ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("audit") + " (" +
                    "id " + autoIncrement() + ", " +
                    "unique_id " + varchar(36) + ", " +
                    "name " + varchar(16) + ", " +
                    "action " + varchar(64) + " NOT NULL, " +
                    "address " + varchar(64) + ", " +
                    "detail TEXT, " +
                    "created_at BIGINT NOT NULL" +
                    ")");

            statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + table("idx_accounts_name") +
                    " ON " + table("accounts") + "(name_key)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + table("idx_locks_type") +
                    " ON " + table("name_locks") + "(type)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + table("idx_attempts_name_time") +
                    " ON " + table("login_attempts") + "(name, created_at)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + table("idx_audit_time") +
                    " ON " + table("audit") + "(created_at)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + table("idx_2fa_codes_account") +
                    " ON " + table("two_factor_backup_codes") + "(unique_id, used_at)");
        }

        ensureColumn("sessions", "name", varchar(16));
        ensureColumn("sessions", "name_key", varchar(32));
        ensureColumn("sessions", "session_id", varchar(64));
        ensureColumn("sessions", "address_hash", varchar(128));
        ensureColumn("sessions", "bridge_payload", "TEXT");

        try (PreparedStatement statement = connection.prepareStatement(upsertMetaSql())) {
            statement.setString(1, "schema");
            statement.setString(2, "5");
            statement.executeUpdate();
        }
    }

    @NotNull
    private Optional<PALAccount> findAccount(@NotNull String where, @NotNull String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT unique_id, name, type, status, last_address, registered_at, last_login_at " +
                        "FROM " + table("accounts") + " WHERE " + where + " LIMIT 1"
        )) {
            statement.setString(1, value);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(readAccount(result));
            }
        }
    }

    @NotNull
    private PALAccount readAccount(@NotNull ResultSet result) throws SQLException {
        return new StoredAccount(
                UUID.fromString(result.getString("unique_id")),
                result.getString("name"),
                PALAccount.Type.valueOf(result.getString("type")),
                PALAccount.Status.valueOf(result.getString("status")),
                parseAddress(result.getString("last_address")),
                instant(result, "registered_at"),
                instant(result, "last_login_at")
        );
    }

    @NotNull
    private AccountNameLock readNameLock(@NotNull ResultSet result) throws SQLException {
        String uniqueId = result.getString("unique_id");

        return AccountNameLock.builder()
                .name(result.getString("name"))
                .type(AccountNameLock.Type.valueOf(result.getString("type")))
                .uniqueId(uniqueId == null ? null : UUID.fromString(uniqueId))
                .source(result.getString("source"))
                .createdAt(Instant.ofEpochMilli(result.getLong("created_at")))
                .expiresAt(instant(result, "expires_at"))
                .build();
    }

    @NotNull
    private AuthSession readSession(@NotNull ResultSet result) throws SQLException {
        String name = result.getString("name");
        String sessionId = result.getString("session_id");

        return new StoredSession(
                UUID.fromString(result.getString("unique_id")),
                name == null ? "" : name,
                sessionId == null || sessionId.trim().isEmpty()
                        ? UUID.randomUUID().toString().replace("-", "")
                        : sessionId,
                AuthSource.valueOf(result.getString("source")),
                parseAddress(result.getString("address")),
                result.getString("address_hash"),
                Instant.ofEpochMilli(result.getLong("auth_at")),
                instant(result, "expires_at")
        );
    }

    private void closeConnection() {
        connected = false;

        if (connection == null) return;

        try {
            connection.close();
        } catch (SQLException exception) {
            ((PALPlugin) api.getPlugin()).getLogger().warning("Could not close storage: " + exception.getMessage());
        } finally {
            connection = null;
        }
    }

    @NotNull
    private CompletionStage<Void> run(@NotNull SQLRunnable runnable) {
        return supply(() -> {
            runnable.run();
            return null;
        });
    }

    @NotNull
    private <T> CompletionStage<T> supply(@NotNull SQLSupplier<T> supplier) {
        ExecutorService currentExecutor = executor;
        if (currentExecutor == null || currentExecutor.isShutdown()) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Storage is not connected"));
            return future;
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                if (connection == null || connection.isClosed()) {
                    throw new SQLException("Storage connection is closed");
                }

                return supplier.get();
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, currentExecutor);
    }

    private void setInstant(PreparedStatement statement, int index, Instant instant) throws SQLException {
        if (instant == null) statement.setNull(index, Types.BIGINT);
        else statement.setLong(index, instant.toEpochMilli());
    }

    @Nullable
    private Instant instant(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : Instant.ofEpochMilli(value);
    }

    @Nullable
    private String address(@Nullable InetAddress address) {
        return address == null ? null : address.getHostAddress();
    }

    @Nullable
    private InetAddress parseAddress(@Nullable String address) {
        if (address == null || address.trim().isEmpty()) return null;

        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException ignored) {
            return null;
        }
    }

    private void ensureColumn(@NotNull String tableName, @NotNull String column, @NotNull String definition) throws SQLException {
        if (hasColumn(tableName, column)) return;

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table(tableName) + " ADD COLUMN " + column + " " + definition);
        }
    }

    private boolean hasColumn(@NotNull String tableName, @NotNull String column) throws SQLException {
        if (type == StorageType.SQLITE) {
            try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + table(tableName) + ")")) {
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        if (column.equalsIgnoreCase(result.getString("name"))) return true;
                    }
                }
            }

            return false;
        }

        DatabaseMetaData metadata = connection.getMetaData();
        String resolvedTable = table(tableName);
        return hasColumn(metadata, null, resolvedTable, column)
                || hasColumn(metadata, settings.getDatabase(), resolvedTable, column)
                || hasColumn(metadata, null, resolvedTable.toLowerCase(Locale.ROOT), column)
                || hasColumn(metadata, null, resolvedTable.toUpperCase(Locale.ROOT), column);
    }

    private boolean hasColumn(@NotNull DatabaseMetaData metadata, @Nullable String catalog,
                              @NotNull String table, @NotNull String column) throws SQLException {
        try (ResultSet result = metadata.getColumns(catalog, null, table, column)) {
            if (result.next()) return true;
        }

        try (ResultSet result = metadata.getColumns(catalog, null, table, column.toUpperCase(Locale.ROOT))) {
            return result.next();
        }
    }

    private String autoIncrement() {
        switch (type) {
            case MYSQL:
            case MARIADB:
                return "INTEGER PRIMARY KEY AUTO_INCREMENT";
            case POSTGRESQL:
                return "BIGSERIAL PRIMARY KEY";
            case SQLITE:
            default:
                return "INTEGER PRIMARY KEY AUTOINCREMENT";
        }
    }

    private String varchar(int length) {
        return "VARCHAR(" + length + ")";
    }

    private String upsertMetaSql() throws SQLException {
        String keyColumn = hasColumn("meta", "meta_key") ? "meta_key" : "key";
        String valueColumn = hasColumn("meta", "meta_value") ? "meta_value" : "value";
        return upsert("meta", keyColumn, columns(keyColumn, valueColumn), columns(valueColumn));
    }

    private String upsert(@NotNull String tableName, @NotNull String conflictColumn,
                          @NotNull List<String> insertColumns, @NotNull List<String> updateColumns) {
        StringBuilder sql = new StringBuilder("INSERT INTO ")
                .append(table(tableName))
                .append(" (")
                .append(join(insertColumns, ", "))
                .append(") VALUES (")
                .append(placeholders(insertColumns.size()))
                .append(") ");

        if (type == StorageType.MYSQL || type == StorageType.MARIADB) {
            sql.append("ON DUPLICATE KEY UPDATE ");
            for (int i = 0; i < updateColumns.size(); i++) {
                if (i > 0) sql.append(", ");
                String column = updateColumns.get(i);
                sql.append(column).append(" = VALUES(").append(column).append(")");
            }
            return sql.toString();
        }

        sql.append("ON CONFLICT(")
                .append(conflictColumn)
                .append(") DO UPDATE SET ");
        for (int i = 0; i < updateColumns.size(); i++) {
            if (i > 0) sql.append(", ");
            String column = updateColumns.get(i);
            sql.append(column).append(" = excluded.").append(column);
        }

        return sql.toString();
    }

    @NotNull
    private List<String> columns(@NotNull String... names) {
        List<String> columns = new ArrayList<>();
        java.util.Collections.addAll(columns, names);
        return columns;
    }

    @NotNull
    private String placeholders(int amount) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < amount; index++) {
            if (index > 0) builder.append(", ");
            builder.append("?");
        }
        return builder.toString();
    }

    @NotNull
    private String join(@NotNull List<String> values, @NotNull String separator) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) builder.append(separator);
            builder.append(values.get(index));
        }
        return builder.toString();
    }

    private String table(String name) {
        return settings.table(name);
    }

    private String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage();
    }

    private interface SQLRunnable {
        void run() throws SQLException;
    }

    private interface SQLSupplier<T> {
        T get() throws SQLException;
    }
}
