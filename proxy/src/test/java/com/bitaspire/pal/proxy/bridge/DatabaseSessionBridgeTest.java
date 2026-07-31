package com.bitaspire.pal.proxy.bridge;

import com.bitaspire.pal.proxy.identity.IdentityTrust;
import com.bitaspire.pal.proxy.identity.IdentityType;
import com.bitaspire.pal.proxy.session.AuthSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSessionBridgeTest {

    @TempDir
    private Path directory;

    @Test
    void verifiedPremiumSessionCreatesAccountBeforeSession() throws Exception {
        Path database = directory.resolve("database.db");
        createSchema(database);

        DatabaseSessionBridge bridge = new DatabaseSessionBridge(config(database));
        UUID uniqueId = UUID.fromString("5843a491-fdbc-440a-a54f-c6c2bac73c58");
        AuthSession session = AuthSession.builder()
                .uniqueId(uniqueId)
                .name("CroaBeast")
                .sessionId("session")
                .source("MOJANG")
                .identityType(IdentityType.JAVA_PREMIUM)
                .identityTrust(IdentityTrust.VERIFIED_SESSION)
                .verifiedIdentity(true)
                .authenticatedAtMillis(System.currentTimeMillis())
                .expiresAtMillis(System.currentTimeMillis() + 60_000L)
                .sourceServer("auth")
                .build();

        try {
            bridge.saveSession(session).toCompletableFuture().join();

            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                 Statement statement = connection.createStatement();
                 ResultSet account = statement.executeQuery("SELECT name, name_key, type, status FROM pal_accounts WHERE unique_id = '" + uniqueId + "'")) {
                assertTrue(account.next());
                assertEquals("CroaBeast", account.getString("name"));
                assertEquals("croabeast", account.getString("name_key"));
                assertEquals("PREMIUM", account.getString("type"));
                assertEquals("REGISTERED", account.getString("status"));
            }

            Optional<AuthSession> loaded = bridge.findSession(uniqueId, "CroaBeast").toCompletableFuture().join();
            assertTrue(loaded.isPresent());
            assertEquals(IdentityType.JAVA_PREMIUM, loaded.get().getIdentityType());
            assertEquals(IdentityTrust.VERIFIED_SESSION, loaded.get().getIdentityTrust());
        } finally {
            bridge.close();
        }
    }

    @Test
    void unverifiedSessionDoesNotCreateAccount() throws Exception {
        Path database = directory.resolve("database.db");
        createSchema(database);

        DatabaseSessionBridge bridge = new DatabaseSessionBridge(config(database));
        UUID uniqueId = UUID.fromString("55a0e60b-184c-3b18-a863-891527b754f6");
        AuthSession session = AuthSession.of(
                uniqueId,
                "BeTezca",
                "session",
                System.currentTimeMillis() + 60_000L,
                "auth",
                null
        );

        try {
            bridge.saveSession(session).toCompletableFuture().join();

            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                 Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM pal_accounts")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
        } finally {
            bridge.close();
        }
    }

    @Test
    void verifiedSessionDoesNotClaimNameOwnedByAnotherAccount() throws Exception {
        Path database = directory.resolve("database.db");
        createSchema(database);

        UUID existingId = UUID.fromString("55a0e60b-184c-3b18-a863-891527b754f6");
        UUID premiumId = UUID.fromString("5843a491-fdbc-440a-a54f-c6c2bac73c58");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO pal_accounts " +
                    "(unique_id, name, name_key, type, status, created_at, updated_at) VALUES " +
                    "('" + existingId + "', 'CroaBeast', 'croabeast', 'OFFLINE', 'REGISTERED', 1, 1)");
        }

        DatabaseSessionBridge bridge = new DatabaseSessionBridge(config(database));
        AuthSession session = AuthSession.builder()
                .uniqueId(premiumId)
                .name("CroaBeast")
                .sessionId("session")
                .source("MOJANG")
                .identityType(IdentityType.JAVA_PREMIUM)
                .identityTrust(IdentityTrust.VERIFIED_SESSION)
                .verifiedIdentity(true)
                .authenticatedAtMillis(System.currentTimeMillis())
                .expiresAtMillis(System.currentTimeMillis() + 60_000L)
                .sourceServer("auth")
                .build();

        try {
            bridge.saveSession(session).toCompletableFuture().join();

            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                 Statement statement = connection.createStatement();
                 ResultSet account = statement.executeQuery("SELECT unique_id, type FROM pal_accounts WHERE name_key = 'croabeast'")) {
                assertTrue(account.next());
                assertEquals(existingId.toString(), account.getString("unique_id"));
                assertEquals("OFFLINE", account.getString("type"));
            }

            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                 Statement statement = connection.createStatement();
                 ResultSet sessions = statement.executeQuery("SELECT COUNT(*) FROM pal_sessions")) {
                assertTrue(sessions.next());
                assertEquals(0, sessions.getInt(1));
            }
        } finally {
            bridge.close();
        }
    }

    private ProxyBridgeConfig config(Path database) throws Exception {
        Path dataFolder = directory.resolve("proxy");
        Files.createDirectories(dataFolder);
        Files.write(dataFolder.resolve("bridge.yml"), (
                "bridge:\n" +
                        "  enabled: true\n" +
                        "  mode: DATABASE\n" +
                        "  net:\n" +
                        "    auth: auth\n" +
                        "    lobby: lobby\n" +
                        "  guard:\n" +
                        "    proxy-auto-login: true\n" +
                        "  sec:\n" +
                        "    require: true\n" +
                        "    secret: \"test-secret-that-is-not-default\"\n" +
                        "  database:\n" +
                        "    type: SQLITE\n" +
                        "    table-prefix: pal_\n" +
                        "    poll: 0\n" +
                        "    sqlite:\n" +
                        "      file: " + database.toString().replace("\\", "/") + "\n"
        ).getBytes(StandardCharsets.UTF_8));
        return ProxyBridgeConfig.load(dataFolder.toFile(), 1);
    }

    private void createSchema(Path database) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("CREATE TABLE pal_accounts (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "unique_id VARCHAR(36) NOT NULL UNIQUE, " +
                    "name VARCHAR(16) NOT NULL, " +
                    "name_key VARCHAR(32) NOT NULL UNIQUE, " +
                    "type VARCHAR(32) NOT NULL, " +
                    "status VARCHAR(32) NOT NULL, " +
                    "last_address VARCHAR(64), " +
                    "registered_at BIGINT, " +
                    "last_login_at BIGINT, " +
                    "created_at BIGINT NOT NULL, " +
                    "updated_at BIGINT NOT NULL" +
                    ")");
            statement.execute("CREATE TABLE pal_sessions (" +
                    "unique_id VARCHAR(36) PRIMARY KEY, " +
                    "name VARCHAR(16) NOT NULL, " +
                    "name_key VARCHAR(32) NOT NULL, " +
                    "session_id VARCHAR(64) NOT NULL, " +
                    "source VARCHAR(32) NOT NULL, " +
                    "address VARCHAR(64), " +
                    "address_hash VARCHAR(128), " +
                    "auth_at BIGINT NOT NULL, " +
                    "expires_at BIGINT, " +
                    "updated_at BIGINT NOT NULL, " +
                    "bridge_payload TEXT, " +
                    "FOREIGN KEY(unique_id) REFERENCES pal_accounts(unique_id) ON DELETE CASCADE" +
                    ")");
        }
    }
}
