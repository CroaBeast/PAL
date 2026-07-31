package com.bitaspire.pal;

import com.bitaspire.pal.migration.MigrationProvider;
import com.bitaspire.pal.migration.MigrationResult;
import com.bitaspire.pal.storage.StorageType;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class MigrationServiceImpl extends AbstractService {

    @Getter
    @NotNull
    private MigrationOptions options;

    @NotNull
    private final Map<String, MigrationProvider> providers = new LinkedHashMap<>();

    private ExecutorService executor;

    MigrationServiceImpl(@NotNull PALApi api) {
        super(api);
        options = MigrationOptions.load((PALPlugin) api.getPlugin());
    }

    @Override
    public boolean register() {
        super.register();
        reload();
        return true;
    }

    void reload() {
        options = MigrationOptions.load(plugin());

        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "PAL Migration");
                thread.setDaemon(true);
                return thread;
            });
        }

        providers.clear();
        for (MigrationProviderOptions providerOptions : options.getProviders().values()) {
            providers.put(key(providerOptions.getKey()), new SqlMigrationProviderImpl(
                    plugin(),
                    storage(),
                    providerOptions,
                    executor
            ));
        }
    }

    @Override
    public boolean unregister() {
        providers.clear();
        if (executor != null) executor.shutdownNow();
        executor = null;
        return super.unregister();
    }

    @NotNull
    Collection<MigrationProvider> providers() {
        return new ArrayList<>(providers.values());
    }

    @NotNull
    CompletionStage<MigrationResult> migrate(@NotNull String provider) {
        if (!options.isEnabled()) {
            return CompletableFuture.completedFuture(MigrationResultImpl.failure("Migrations are disabled in storage.yml."));
        }

        if ("all".equalsIgnoreCase(provider)) return migrateAll();

        MigrationProvider migrationProvider = providers.get(key(provider));
        if (migrationProvider == null) {
            return CompletableFuture.completedFuture(MigrationResultImpl.failure("Unknown migration provider: " + provider));
        }

        if (!migrationProvider.isAvailable()) {
            return CompletableFuture.completedFuture(MigrationResultImpl.failure("Migration provider is not available: " + provider));
        }

        return backup().thenCompose(ignored -> migrationProvider.migrate())
                .thenCompose(result -> audit(provider, result).thenApply(audited -> result));
    }

    @NotNull
    private CompletionStage<MigrationResult> migrateAll() {
        return backup().thenCompose(ignored -> {
            CompletableFuture<MigrationAccumulator> future = CompletableFuture.completedFuture(new MigrationAccumulator());

            for (MigrationProvider provider : providers.values()) {
                if (!provider.isAvailable()) continue;
                future = future.thenCompose(accumulator -> provider.migrate().thenApply(result -> {
                    accumulator.add(result);
                    return accumulator;
                }));
            }

            return future.thenApply(MigrationAccumulator::toResult);
        }).thenCompose(result -> audit("all", result).thenApply(audited -> result));
    }

    @NotNull
    private CompletionStage<Void> backup() {
        if (!options.isBackup()) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            try {
                StorageSettings settings = StorageSettings.load(plugin());
                if (settings.getType() != StorageType.SQLITE) return;

                File source = settings.getSqliteFile();
                if (!source.isFile()) return;

                File directory = new File(plugin().getDataFolder(), "backups");
                if (!directory.exists() && !directory.mkdirs()) return;

                String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
                File target = new File(directory, "database-before-migration-" + stamp + ".db");
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception exception) {
                throw new IllegalStateException("Could not backup PAL SQLite database: " + exception.getMessage(), exception);
            }
        }, executor);
    }

    @NotNull
    private CompletionStage<Void> audit(@NotNull String provider, @NotNull MigrationResult result) {
        return storage().audit(null, null, "migration.run", null,
                provider + ": accounts=" + result.getAccounts() + ", sessions=" + result.getSessions()
                        + ", success=" + result.isSuccessful());
    }

    private PALPlugin plugin() {
        return (PALPlugin) api.getPlugin();
    }

    private StorageServiceImpl storage() {
        return (StorageServiceImpl) api.getStorageService();
    }

    @NotNull
    private String key(@NotNull String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static final class MigrationAccumulator {
        private int accounts;
        private int sessions;
        private int skipped;
        private int failed;
        private final List<String> messages = new ArrayList<>();

        void add(@NotNull MigrationResult result) {
            accounts += result.getAccounts();
            sessions += result.getSessions();
            if (result instanceof MigrationResultImpl) {
                skipped += ((MigrationResultImpl) result).getSkipped();
                failed += ((MigrationResultImpl) result).getFailed();
                messages.add(((MigrationResultImpl) result).getMessage());
            }
        }

        @NotNull
        MigrationResult toResult() {
            return MigrationResultImpl.success(accounts, sessions, skipped, failed, String.join(" ", messages));
        }
    }
}
