package com.bitaspire.pal;

import com.bitaspire.pal.account.AccountUniqueIds;
import com.bitaspire.pal.account.PALAccount;
import com.bitaspire.pal.migration.MigrationProvider;
import com.bitaspire.pal.migration.MigrationResult;
import com.bitaspire.pal.storage.StorageType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;

@Getter(AccessLevel.PACKAGE)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class SqlMigrationProviderImpl implements MigrationProvider {

    @NotNull
    private final PALPlugin plugin;

    @NotNull
    private final StorageServiceImpl storage;

    @NotNull
    private final MigrationProviderOptions options;

    @NotNull
    private final ExecutorService executor;

    @NotNull
    public String getName() {
        return options.getKey();
    }

    @Override
    public boolean isAvailable() {
        if (!options.isEnabled()) return false;
        MigrationSourceOptions source = options.getSource();
        return source.getType() == StorageType.SQLITE
                ? source.getFile().isFile()
                : source.getType() != StorageType.CUSTOM;
    }

    @NotNull
    @Override
    public CompletionStage<MigrationResult> migrate() {
        if (!options.isEnabled())
            return CompletableFuture.completedFuture(MigrationResultImpl.failure("Migration provider is disabled."));

        return CompletableFuture.<MigrationResult>supplyAsync(() -> {
            int accounts = 0;
            int skipped = 0;
            int failed = 0;

            try (Connection connection = open()) {
                String sql = "SELECT * FROM " + identifier(options.getTable());

                try (Statement statement = connection.createStatement();
                     ResultSet result = statement.executeQuery(sql)) {
                    while (result.next()) {
                        ImportStatus status = importRow(result);
                        switch (status) {
                            case IMPORTED:
                                accounts++;
                                break;
                            case SKIPPED:
                                skipped++;
                                break;
                            case FAILED:
                            default:
                                failed++;
                                break;
                        }
                    }
                }

                return MigrationResultImpl.success(accounts, 0, skipped, failed,
                        "Imported " + accounts + " accounts from " + getName() + ".");
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, executor).exceptionally(throwable -> MigrationResultImpl.failure(rootMessage(throwable)));
    }

    @NotNull
    private ImportStatus importRow(@NotNull ResultSet result) {
        try {
            String name = value(result, options.getNameColumn());
            if (!validName(name)) return ImportStatus.SKIPPED;

            Optional<PALAccount> existing = storage.findAccountByName(name).toCompletableFuture().join();
            if (existing.isPresent() && !options.isOverwrite()) return ImportStatus.SKIPPED;

            PALAccount.Type type = accountType(result);
            UUID supplied = uuid(value(result, options.getUuidColumn()));
            if (type == PALAccount.Type.PREMIUM && supplied == null) return ImportStatus.SKIPPED;

            UUID uniqueId = existing.map(PALAccount::getUniqueId)
                    .orElse(AccountUniqueIds.resolve(name, type, supplied));

            InetAddress address = address(value(result, options.getAddressColumn()));
            Instant registeredAt = timestamp(value(result, options.getRegisteredAtColumn()));
            Instant lastLoginAt = timestamp(value(result, options.getLastLoginAtColumn()));

            PALAccount account = new StoredAccount(
                    uniqueId,
                    name,
                    type,
                    PALAccount.Status.REGISTERED,
                    address,
                    registeredAt,
                    lastLoginAt
            );

            storage.saveAccount(account).toCompletableFuture().join();

            String password = value(result, options.getPasswordColumn());
            if (password != null && !password.trim().isEmpty()) {
                storage.savePasswordHash(uniqueId, new StoredPasswordHash(
                        options.getPasswordAlgorithm(),
                        password,
                        1
                )).toCompletableFuture().join();
            }

            storage.audit(uniqueId, name, "migration.import", address, options.getKey()).toCompletableFuture().join();
            return ImportStatus.IMPORTED;
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not import " + options.getKey() + " account row: " + rootMessage(exception));
            return ImportStatus.FAILED;
        }
    }

    @NotNull
    private Connection open() throws SQLException {
        MigrationSourceOptions source = options.getSource();

        switch (source.getType()) {
            case SQLITE:
                return openSQLite(source.getFile());
            case MYSQL:
                return DriverManager.getConnection(
                        "jdbc:mysql://" + source.getHost() + ":" + source.getPort() + "/" + source.getDatabase()
                                + "?useSSL=" + source.isSsl()
                                + "&verifyServerCertificate=false&useUnicode=true&characterEncoding=utf8&serverTimezone=UTC",
                        source.getUsername(),
                        source.getPassword()
                );
            case MARIADB:
                return DriverManager.getConnection(
                        "jdbc:mariadb://" + source.getHost() + ":" + source.getPort() + "/" + source.getDatabase()
                                + "?useSsl=" + source.isSsl(),
                        source.getUsername(),
                        source.getPassword()
                );
            case POSTGRESQL:
                return DriverManager.getConnection(
                        "jdbc:postgresql://" + source.getHost() + ":" + source.getPort() + "/" + source.getDatabase()
                                + "?ssl=" + source.isSsl(),
                        source.getUsername(),
                        source.getPassword()
                );
            default:
                throw new SQLException("Unsupported migration source: " + source.getType());
        }
    }

    @NotNull
    private Connection openSQLite(@NotNull File file) throws SQLException {
        if (!file.isFile()) throw new SQLException("SQLite file does not exist: " + file.getAbsolutePath());
        return DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
    }

    @NotNull
    private PALAccount.Type accountType(@NotNull ResultSet result) {
        String premium = value(result, options.getPremiumColumn());
        return premium != null
                ? (truthy(premium) ? PALAccount.Type.PREMIUM : PALAccount.Type.OFFLINE)
                : options.getAccountType();
    }

    private boolean truthy(@NotNull String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized)
                || "true".equals(normalized)
                || "yes".equals(normalized)
                || "premium".equals(normalized)
                || "online".equals(normalized);
    }

    @Nullable
    private String value(@NotNull ResultSet result, @Nullable String column) {
        if (column == null || column.trim().isEmpty()) return null;

        try {
            Object value = result.getObject(column);
            return value == null ? null : String.valueOf(value);
        } catch (SQLException ignored) {
            return null;
        }
    }

    @Nullable
    private UUID uuid(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) return null;

        String normalized = value.trim();
        if (normalized.length() == 32) {
            normalized = normalized.substring(0, 8) + "-"
                    + normalized.substring(8, 12) + "-"
                    + normalized.substring(12, 16) + "-"
                    + normalized.substring(16, 20) + "-"
                    + normalized.substring(20);
        }

        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    private InetAddress address(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) return null;

        try {
            return InetAddress.getByName(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private Instant timestamp(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) return null;

        String normalized = value.trim();
        try {
            long number = Long.parseLong(normalized);
            if (number <= 0L) return null;
            if (number < 10000000000L) number *= 1000L;
            return Instant.ofEpochMilli(number);
        } catch (NumberFormatException ignored) {
            try {
                return Instant.parse(normalized);
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private boolean validName(@Nullable String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        return trimmed.length() >= 3 && trimmed.length() <= 16 && trimmed.matches("[A-Za-z0-9_]+");
    }

    @NotNull
    private String identifier(@NotNull String value) throws SQLException {
        if (!value.matches("[A-Za-z0-9_.$]+")) throw new SQLException("Unsafe SQL identifier: " + value);
        return value;
    }

    @NotNull
    private String rootMessage(@NotNull Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private enum ImportStatus {
        IMPORTED,
        SKIPPED,
        FAILED
    }
}
