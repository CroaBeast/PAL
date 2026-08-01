package com.bitaspire.pal.proxy.bridge;

import com.bitaspire.pal.proxy.identity.IdentityTrust;
import com.bitaspire.pal.proxy.identity.IdentityType;
import com.bitaspire.pal.proxy.session.AuthSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

final class DatabaseSessionBridge implements SessionBridge {

    private final ProxyBridgeConfig config;
    private final SignedBridgeCodec codec;
    private final Map<UUID, AuthSession> cacheById = new ConcurrentHashMap<>();
    private final Map<String, UUID> cacheByName = new ConcurrentHashMap<>();
    private final Set<SessionBridgeListener> listeners = java.util.Collections.newSetFromMap(new ConcurrentHashMap<SessionBridgeListener, Boolean>());

    private volatile boolean closed = false;
    private Thread poller;

    DatabaseSessionBridge(@NotNull ProxyBridgeConfig config) {
        this.config = config;
        this.codec = new SignedBridgeCodec(
                config.getSecret(),
                config.isRequireSecret(),
                config.getSkewSeconds() * 1000L
        );
        startPoller();
    }

    @NotNull
    @Override
    public CompletionStage<Optional<AuthSession>> findSession(@Nullable UUID uniqueId, @NotNull String name) {
        AuthSession cached = cached(uniqueId, name);
        if (cached != null) return CompletableFuture.completedFuture(Optional.of(cached));

        return CompletableFuture.supplyAsync(() -> {
            Optional<AuthSession> loaded = load(uniqueId, name);
            if (loaded.isPresent()) saveLocal(loaded.get(), false);
            return loaded;
        });
    }

    @NotNull
    @Override
    public CompletionStage<Void> saveSession(@NotNull AuthSession session) {
        return CompletableFuture.runAsync(() -> {
            try {
                upsertVerifiedAccount(session);
                upsert(session);
            } catch (SQLException ignored) {
                // Local proxy state still applies if SQL is unavailable or the account name is already claimed.
            }

            saveLocal(session, true);
        });
    }

    @NotNull
    @Override
    public CompletionStage<Void> invalidate(@NotNull UUID uniqueId) {
        return CompletableFuture.runAsync(() -> {
            try {
                delete(uniqueId);
            } catch (SQLException ignored) {
                // Local invalidation still applies for this proxy process.
            }

            invalidateLocal(uniqueId, true);
        });
    }

    @Override
    public void addListener(@NotNull SessionBridgeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void close() {
        closed = true;
        if (poller != null) poller.interrupt();
    }

    @Nullable
    private AuthSession cached(@Nullable UUID uniqueId, @NotNull String name) {
        AuthSession session = uniqueId == null ? null : cacheById.get(uniqueId);
        if (session == null) {
            UUID byName = cacheByName.get(key(name));
            if (byName != null) session = cacheById.get(byName);
        }

        if (session == null) return null;
        if (!session.isExpired(System.currentTimeMillis())) return session;

        invalidateLocal(session.getUniqueId(), false);
        return null;
    }

    @NotNull
    private Optional<AuthSession> load(@Nullable UUID uniqueId, @NotNull String name) {
        String where = uniqueId == null ? "s.name_key = ?" : "s.unique_id = ?";
        String value = uniqueId == null ? key(name) : uniqueId.toString();

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(selectSql(where))) {
            statement.setString(1, value);
            statement.setLong(2, System.currentTimeMillis());

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) return Optional.ofNullable(read(result));
            }
        } catch (SQLException ignored) {
            return Optional.empty();
        }

        return uniqueId == null ? Optional.empty() : load(null, name);
    }

    private void startPoller() {
        if (config.getDatabasePollSeconds() <= 0) return;

        poller = new Thread(this::pollLoop, "PAL Database Bridge");
        poller.setDaemon(true);
        poller.start();
    }

    private void pollLoop() {
        while (!closed) {
            try {
                poll();
                Thread.sleep(Math.max(1, config.getDatabasePollSeconds()) * 1000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ignored) {
                sleepQuietly();
            }
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(Math.max(1, config.getDatabasePollSeconds()) * 1000L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void poll() {
        Map<UUID, AuthSession> loaded = loadActive();

        for (AuthSession session : loaded.values()) {
            AuthSession previous = cacheById.get(session.getUniqueId());
            saveLocal(session, previous == null || !same(previous, session));
        }

        for (UUID uniqueId : new java.util.ArrayList<>(cacheById.keySet())) {
            if (!loaded.containsKey(uniqueId)) invalidateLocal(uniqueId, true);
        }
    }

    @NotNull
    private Map<UUID, AuthSession> loadActive() {
        Map<UUID, AuthSession> sessions = new HashMap<>();

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(selectActiveSql())) {
            statement.setLong(1, System.currentTimeMillis());

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    AuthSession session = read(result);
                    if (session != null) sessions.put(session.getUniqueId(), session);
                }
            }
        } catch (SQLException ignored) {
            return sessions;
        }

        return sessions;
    }

    @Nullable
    private AuthSession read(@NotNull ResultSet result) throws SQLException {
        AuthSession signed = readSigned(result);
        if (signed != null) return signed;
        if (config.isRequireSecret()) return null;

        return readUnsigned(result);
    }

    @Nullable
    private AuthSession readSigned(@NotNull ResultSet result) throws SQLException {
        String payload = result.getString("bridge_payload");
        if (payload == null || payload.trim().isEmpty()) return null;

        Optional<SignedBridgeCodec.Message> decoded = codec.decode(payload);
        if (!decoded.isPresent() || decoded.get().getType() != SignedBridgeCodec.Message.Type.SESSION) return null;

        AuthSession session = decoded.get().getSession();
        if (session == null || session.isExpired(System.currentTimeMillis())) return null;
        if (!session.getUniqueId().toString().equalsIgnoreCase(result.getString("unique_id"))) return null;
        if (!session.getName().equalsIgnoreCase(result.getString("name"))) return null;
        if (!session.getSessionId().equals(result.getString("session_id"))) return null;

        return session;
    }

    @NotNull
    private AuthSession readUnsigned(@NotNull ResultSet result) throws SQLException {
        String accountType = result.getString("account_type");
        IdentityType identityType = identityType(accountType);
        IdentityTrust identityTrust = identityTrust(identityType);

        return AuthSession.builder()
                .uniqueId(UUID.fromString(result.getString("unique_id")))
                .name(result.getString("name"))
                .sessionId(result.getString("session_id"))
                .source(result.getString("source"))
                .identityType(identityType)
                .identityTrust(identityTrust)
                .verifiedIdentity(identityTrust != IdentityTrust.UNVERIFIED)
                .authenticatedAtMillis(result.getLong("auth_at"))
                .expiresAtMillis(nullableLong(result, "expires_at"))
                .addressHash(result.getString("address_hash"))
                .sourceServer(config.getAuthServer())
                .build();
    }

    private void upsert(@NotNull AuthSession session) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(upsertSql())) {
            statement.setString(1, session.getUniqueId().toString());
            statement.setString(2, session.getName());
            statement.setString(3, key(session.getName()));
            statement.setString(4, session.getSessionId());
            statement.setString(5, databaseSource(session));
            statement.setNull(6, Types.VARCHAR);
            statement.setString(7, session.getAddressHash());
            statement.setLong(8, session.getAuthenticatedAtMillis());
            setNullableLong(statement, 9, session.getExpiresAtMillis());
            statement.setString(10, encode(session));
            statement.setLong(11, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private void upsertVerifiedAccount(@NotNull AuthSession session) throws SQLException {
        String accountType = verifiedAccountType(session);
        if (accountType == null) return;

        long now = System.currentTimeMillis();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(insertAccountSql())) {
            statement.setString(1, session.getUniqueId().toString());
            statement.setString(2, session.getName());
            statement.setString(3, key(session.getName()));
            statement.setString(4, accountType);
            statement.setString(5, "REGISTERED");
            statement.setNull(6, Types.VARCHAR);
            statement.setLong(7, now);
            statement.setLong(8, now);
            statement.setLong(9, now);
            statement.setLong(10, now);
            statement.executeUpdate();
        }

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(updateAccountSql())) {
            statement.setString(1, session.getName());
            statement.setString(2, key(session.getName()));
            statement.setString(3, accountType);
            statement.setString(4, "REGISTERED");
            statement.setLong(5, now);
            statement.setLong(6, now);
            statement.setString(7, session.getUniqueId().toString());
            statement.executeUpdate();
        }
    }

    private void delete(@NotNull UUID uniqueId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table("sessions") + " WHERE unique_id = ?")) {
            statement.setString(1, uniqueId.toString());
            statement.executeUpdate();
        }
    }

    @NotNull
    private String selectSql(@NotNull String where) {
        return "SELECT s.unique_id, s.name, s.session_id, s.source, s.address_hash, s.auth_at, s.expires_at, s.bridge_payload, a.type AS account_type " +
                "FROM " + table("sessions") + " s " +
                "LEFT JOIN " + table("accounts") + " a ON a.unique_id = s.unique_id " +
                "WHERE (" + where + ") AND (s.expires_at IS NULL OR s.expires_at > ?) LIMIT 1";
    }

    @NotNull
    private String selectActiveSql() {
        return "SELECT s.unique_id, s.name, s.session_id, s.source, s.address_hash, s.auth_at, s.expires_at, s.bridge_payload, a.type AS account_type " +
                "FROM " + table("sessions") + " s " +
                "LEFT JOIN " + table("accounts") + " a ON a.unique_id = s.unique_id " +
                "WHERE s.expires_at IS NULL OR s.expires_at > ?";
    }

    @NotNull
    private String upsertSql() {
        String table = table("sessions");
        String columns = "unique_id, name, name_key, session_id, source, address, address_hash, auth_at, expires_at, bridge_payload, updated_at";
        String values = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?";

        switch (config.getDatabaseType()) {
            case MYSQL:
            case MARIADB:
                return "INSERT INTO " + table + " (" + columns + ") VALUES (" + values + ") " +
                        "ON DUPLICATE KEY UPDATE name = VALUES(name), name_key = VALUES(name_key), session_id = VALUES(session_id), " +
                        "source = VALUES(source), address = VALUES(address), address_hash = VALUES(address_hash), bridge_payload = VALUES(bridge_payload), " +
                        "auth_at = VALUES(auth_at), expires_at = VALUES(expires_at), updated_at = VALUES(updated_at)";
            case POSTGRESQL:
            case SQLITE:
            default:
                return "INSERT INTO " + table + " (" + columns + ") VALUES (" + values + ") " +
                        "ON CONFLICT(unique_id) DO UPDATE SET name = excluded.name, name_key = excluded.name_key, " +
                        "session_id = excluded.session_id, source = excluded.source, address = excluded.address, " +
                        "address_hash = excluded.address_hash, bridge_payload = excluded.bridge_payload, " +
                        "auth_at = excluded.auth_at, expires_at = excluded.expires_at, updated_at = excluded.updated_at";
        }
    }

    @NotNull
    private String insertAccountSql() {
        String table = table("accounts");
        String columns = "unique_id, name, name_key, type, status, last_address, registered_at, last_login_at, created_at, updated_at";
        String values = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?";

        switch (config.getDatabaseType()) {
            case MYSQL:
            case MARIADB:
                return "INSERT IGNORE INTO " + table + " (" + columns + ") VALUES (" + values + ")";
            case POSTGRESQL:
            case SQLITE:
            default:
                return "INSERT INTO " + table + " (" + columns + ") VALUES (" + values + ") ON CONFLICT DO NOTHING";
        }
    }

    @NotNull
    private String updateAccountSql() {
        return "UPDATE " + table("accounts") + " SET name = ?, name_key = ?, type = ?, status = ?, " +
                "last_login_at = ?, updated_at = ? WHERE unique_id = ?";
    }

    @NotNull
    private Connection connect() throws SQLException {
        loadDriver();

        if (config.getDatabaseType() == ProxyBridgeConfig.DatabaseType.SQLITE) {
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + config.getDatabaseSqliteFile());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA busy_timeout = 5000");
            }
            return connection;
        }

        return DriverManager.getConnection(url(), config.getDatabaseUsername(), config.getDatabasePassword());
    }

    private void loadDriver() throws SQLException {
        ProxyStorageDrivers.resolve(config.getDatabaseType());
    }

    @NotNull
    private String url() {
        switch (config.getDatabaseType()) {
            case MARIADB:
                return "jdbc:mariadb://" + config.getDatabaseHost() + ":" + config.getDatabasePort() + "/" + config.getDatabaseName()
                        + "?useSsl=" + config.isDatabaseSsl();
            case POSTGRESQL:
                return "jdbc:postgresql://" + config.getDatabaseHost() + ":" + config.getDatabasePort() + "/" + config.getDatabaseName()
                        + "?ssl=" + config.isDatabaseSsl();
            case MYSQL:
            default:
                return "jdbc:mysql://" + config.getDatabaseHost() + ":" + config.getDatabasePort() + "/" + config.getDatabaseName()
                        + "?useSSL=" + config.isDatabaseSsl()
                        + "&verifyServerCertificate=false&useUnicode=true&characterEncoding=utf8&serverTimezone=UTC";
        }
    }

    @NotNull
    private String table(@NotNull String name) {
        return config.getDatabaseTablePrefix() + name;
    }

    @NotNull
    private String databaseSource(@NotNull AuthSession session) {
        return session.isVerifiedIdentity() ? "PREMIUM" : "SESSION";
    }

    @Nullable
    private String verifiedAccountType(@NotNull AuthSession session) {
        if (!session.isVerifiedIdentity()) return null;

        switch (session.getIdentityType()) {
            case JAVA_PREMIUM:
                return "PREMIUM";
            case BEDROCK:
                return "BEDROCK";
            default:
                return null;
        }
    }

    @Nullable
    private String encode(@NotNull AuthSession session) {
        return config.hasUsableSecret() ? codec.encode(session) : null;
    }

    @NotNull
    private IdentityType identityType(@Nullable String accountType) {
        if ("PREMIUM".equalsIgnoreCase(accountType)) return IdentityType.JAVA_PREMIUM;
        if ("BEDROCK".equalsIgnoreCase(accountType)) return IdentityType.BEDROCK;
        if ("OFFLINE".equalsIgnoreCase(accountType)) return IdentityType.JAVA_OFFLINE;
        return IdentityType.UNKNOWN;
    }

    @NotNull
    private IdentityTrust identityTrust(@NotNull IdentityType identityType) {
        switch (identityType) {
            case JAVA_PREMIUM:
            case BEDROCK:
                return IdentityTrust.VERIFIED_SESSION;
            case JAVA_OFFLINE:
                return IdentityTrust.MANUAL;
            default:
                return IdentityTrust.UNVERIFIED;
        }
    }

    private long nullableLong(@NotNull ResultSet result, @NotNull String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? 0L : value;
    }

    private void setNullableLong(@NotNull PreparedStatement statement, int index, long value) throws SQLException {
        if (value <= 0L) statement.setNull(index, Types.BIGINT);
        else statement.setLong(index, value);
    }

    private boolean same(@NotNull AuthSession first, @NotNull AuthSession second) {
        return first.getUniqueId().equals(second.getUniqueId())
                && first.getSessionId().equals(second.getSessionId())
                && first.getExpiresAtMillis() == second.getExpiresAtMillis()
                && Objects.equals(first.getAddressHash(), second.getAddressHash());
    }

    private void saveLocal(@NotNull AuthSession session, boolean notify) {
        cacheById.put(session.getUniqueId(), session);
        cacheByName.put(key(session.getName()), session.getUniqueId());
        if (notify) for (SessionBridgeListener listener : listeners) listener.onSessionSaved(session);
    }

    private void invalidateLocal(@NotNull UUID uniqueId, boolean notify) {
        AuthSession previous = cacheById.remove(uniqueId);
        if (previous != null) cacheByName.remove(key(previous.getName()));
        if (notify) for (SessionBridgeListener listener : listeners) listener.onSessionInvalidated(uniqueId);
    }

    @NotNull
    private String key(@NotNull String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
