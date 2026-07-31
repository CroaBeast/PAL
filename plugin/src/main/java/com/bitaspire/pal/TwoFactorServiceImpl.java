package com.bitaspire.pal;

import com.bitaspire.pal.account.PALAccount;
import com.bitaspire.pal.auth.AuthResult;
import com.bitaspire.pal.auth.AuthSource;
import com.bitaspire.pal.auth.PasswordHash;
import com.bitaspire.pal.auth.PasswordHasher;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

final class TwoFactorServiceImpl extends AbstractService {

    private final Map<UUID, PendingChallenge> challenges = new ConcurrentHashMap<>();
    private final Map<UUID, PendingSetup> setups = new ConcurrentHashMap<>();
    private final TotpAuthenticator authenticator = new TotpAuthenticator();
    private final PasswordHasher backupHasher = new Pbkdf2PasswordHasher();
    private final SecureRandom random = new SecureRandom();

    private TwoFactorOptions options = TwoFactorOptions.defaults();

    TwoFactorServiceImpl(@NotNull PALApi api) {
        super(api);
    }

    @Override
    public boolean register() {
        super.register();
        reload();
        return true;
    }

    void reload() {
        options = TwoFactorOptions.load(plugin());
    }

    @Override
    public boolean unregister() {
        challenges.clear();
        setups.clear();
        return super.unregister();
    }

    @NotNull
    CompletionStage<Boolean> beginLogin(
            @NotNull UUID playerId,
            @NotNull String playerName,
            @NotNull PALAccount account,
            @NotNull AuthSource source,
            @Nullable InetAddress address
    ) {
        if (!canChallenge()) return CompletableFuture.completedFuture(false);

        return storage().findTwoFactor(account.getUniqueId()).thenApply(twoFactor -> {
            if (!twoFactor.isPresent() || !twoFactor.get().isEnabled()) return false;

            challenges.put(playerId, new PendingChallenge(playerId, playerName, account, source, address));
            setups.remove(playerId);
            return true;
        });
    }

    boolean hasPending(@NotNull UUID playerId) {
        return challenges.containsKey(playerId);
    }

    void clear(@NotNull UUID playerId) {
        challenges.remove(playerId);
        setups.remove(playerId);
    }

    @NotNull
    CompletionStage<Boolean> isEnabled(@NotNull UUID accountId) {
        if (!options.isEnabled()) return CompletableFuture.completedFuture(false);
        return storage().findTwoFactor(accountId).thenApply(Optional::isPresent);
    }

    @NotNull
    CompletionStage<TwoFactorResult> verifyPending(@NotNull Player player, @NotNull String code, boolean recovery) {
        PendingChallenge challenge = challenges.get(player.getUniqueId());
        if (challenge == null) return completed("security.two-factor-no-pending");

        CompletionStage<Boolean> verification = recovery
                ? verifyBackup(challenge.getAccount().getUniqueId(), code)
                : verifyTotp(challenge.getAccount().getUniqueId(), code);

        return verification.thenCompose(valid -> {
            if (!valid) return failedChallenge(challenge);

            challenges.remove(player.getUniqueId());
            return plugin().getSessionService().create(challenge.getAccount(), challenge.getSource(), challenge.getAddress())
                    .thenRun(() -> plugin().getPreAuthService().markAuthenticated(challenge.getAccount()))
                    .thenCompose(ignored -> storage().audit(
                            challenge.getAccount().getUniqueId(),
                            challenge.getAccount().getName(),
                            recovery ? "auth.2fa-recovery" : "auth.2fa",
                            challenge.getAddress(),
                            challenge.getSource().name()
                    ))
                    .thenApply(ignored -> TwoFactorResult.message("security.two-factor-success"));
        }).exceptionally(throwable -> TwoFactorResult.message("security.two-factor-error"));
    }

    @NotNull
    CompletionStage<TwoFactorResult> startSetup(@NotNull Player player) {
        if (!options.isEnabled() || !options.isTotp()) return completed("security.two-factor-disabled");

        return currentAccount(player).thenCompose(account -> {
            if (!account.isPresent()) return completed("auth.not-logged");

            PALAccount palAccount = account.get();
            return storage().findTwoFactor(palAccount.getUniqueId()).thenApply(existing -> {
                if (existing.isPresent()) return TwoFactorResult.message("security.two-factor-already-enabled");

                String secret = authenticator.createSecret();
                String uri = authenticator.uri(options.getIssuer(), palAccount.getName(), secret, options.getDigits(), options.getPeriodSeconds());
                setups.put(player.getUniqueId(), new PendingSetup(palAccount, secret, uri));
                challenges.remove(player.getUniqueId());

                return TwoFactorResult.message("security.two-factor-setup", placeholders(
                        "secret", secret,
                        "uri", uri
                ));
            });
        }).exceptionally(throwable -> TwoFactorResult.message("security.two-factor-error"));
    }

    @NotNull
    CompletionStage<TwoFactorResult> confirmSetup(@NotNull Player player, @NotNull String code) {
        PendingSetup setup = setups.get(player.getUniqueId());
        if (setup == null) return completed("security.two-factor-no-setup");

        if (!authenticator.verify(setup.getSecret(), code, options.getDigits(), options.getPeriodSeconds(), options.getWindow())) {
            return completed("security.two-factor-invalid");
        }

        List<String> backupCodes = options.isBackupCodes() ? createBackupCodes() : Collections.emptyList();
        List<PasswordHash> hashes = new ArrayList<>();
        for (String backupCode : backupCodes) hashes.add(backupHasher.hash(normalizeBackup(backupCode).toCharArray()));

        return storage().saveTwoFactor(setup.getAccount().getUniqueId(), setup.getSecret())
                .thenCompose(ignored -> storage().saveBackupCodes(setup.getAccount().getUniqueId(), hashes))
                .thenCompose(ignored -> storage().audit(
                        setup.getAccount().getUniqueId(),
                        setup.getAccount().getName(),
                        "auth.2fa-enable",
                        address(player),
                        "codes=" + backupCodes.size()
                ))
                .thenApply(ignored -> {
                    setups.remove(player.getUniqueId());
                    return TwoFactorResult.message("security.two-factor-enabled", placeholders(
                            "codes", backupCodes.isEmpty() ? "none" : String.join(", ", backupCodes)
                    ), backupCodes);
                })
                .exceptionally(throwable -> TwoFactorResult.message("security.two-factor-error"));
    }

    @NotNull
    CompletionStage<TwoFactorResult> disable(@NotNull Player player, @NotNull String code) {
        if (!options.isEnabled()) return completed("security.two-factor-disabled");

        return currentAccount(player).thenCompose(account -> {
            if (!account.isPresent()) return completed("auth.not-logged");

            PALAccount palAccount = account.get();
            return storage().findTwoFactor(palAccount.getUniqueId()).thenCompose(existing -> {
                if (!existing.isPresent()) return completed("security.two-factor-not-enabled");

                CompletionStage<Boolean> verification = options.isTotp() &&
                        authenticator.verify(existing.get().getSecret(), code, options.getDigits(), options.getPeriodSeconds(), options.getWindow())
                        ? CompletableFuture.completedFuture(true)
                        : verifyBackup(palAccount.getUniqueId(), code);

                return verification.thenCompose(valid -> {
                    if (!valid) return completed("security.two-factor-invalid");

                    return storage().deleteTwoFactor(palAccount.getUniqueId())
                            .thenCompose(ignored -> storage().audit(
                                    palAccount.getUniqueId(),
                                    palAccount.getName(),
                                    "auth.2fa-disable",
                                    address(player),
                                    "command"
                            ))
                            .thenApply(ignored -> TwoFactorResult.message("security.two-factor-disabled-success"));
                });
            });
        }).exceptionally(throwable -> TwoFactorResult.message("security.two-factor-error"));
    }

    @NotNull
    CompletionStage<Void> delete(@NotNull UUID accountId) {
        challenges.values().removeIf(challenge -> challenge.getAccount().getUniqueId().equals(accountId));
        setups.values().removeIf(setup -> setup.getAccount().getUniqueId().equals(accountId));
        return storage().deleteTwoFactor(accountId);
    }

    @NotNull
    private CompletionStage<TwoFactorResult> failedChallenge(@NotNull PendingChallenge challenge) {
        challenge.incrementAttempts();

        return storage().loginAttempt(
                challenge.getAccount().getUniqueId(),
                challenge.getAccount().getName(),
                challenge.getAddress(),
                false,
                "security.two-factor-invalid"
        ).thenApply(ignored -> {
            if (challenge.getAttempts() < options.getMaxAttempts()) {
                return TwoFactorResult.message("security.two-factor-invalid");
            }

            challenges.remove(challenge.getPlayerId());
            return TwoFactorResult.message("security.two-factor-too-many");
        });
    }

    @NotNull
    private CompletionStage<Boolean> verifyTotp(@NotNull UUID accountId, @NotNull String code) {
        if (!options.isTotp()) return CompletableFuture.completedFuture(false);

        return storage().findTwoFactor(accountId)
                .thenApply(twoFactor -> twoFactor.isPresent() &&
                        authenticator.verify(twoFactor.get().getSecret(), code, options.getDigits(), options.getPeriodSeconds(), options.getWindow()));
    }

    @NotNull
    private CompletionStage<Boolean> verifyBackup(@NotNull UUID accountId, @NotNull String code) {
        if (!options.isBackupCodes()) return CompletableFuture.completedFuture(false);

        String normalized = normalizeBackup(code);
        return storage().findBackupCodes(accountId).thenCompose(codes -> {
            CompletionStage<Boolean> stage = CompletableFuture.completedFuture(false);

            for (StoredBackupCode backupCode : codes) {
                stage = stage.thenCompose(found -> {
                    if (found) return CompletableFuture.completedFuture(true);
                    if (!backupHasher.verify(normalized.toCharArray(), backupCode.getHash())) {
                        return CompletableFuture.completedFuture(false);
                    }

                    return storage().markBackupCodeUsed(backupCode.getId()).thenApply(ignored -> true);
                });
            }

            return stage;
        });
    }

    @NotNull
    private CompletionStage<Optional<PALAccount>> currentAccount(@NotNull Player player) {
        return plugin().getAccountService().findByName(player.getName()).thenApply(account -> {
            if (!account.isPresent()) return Optional.empty();

            PALAccount palAccount = account.get();
            return plugin().getSessionService().isAuthenticated(palAccount.getUniqueId(), palAccount.getName(), address(player))
                    ? account
                    : Optional.empty();
        });
    }

    @NotNull
    private List<String> createBackupCodes() {
        List<String> codes = new ArrayList<>();
        for (int index = 0; index < options.getBackupCodeAmount(); index++) codes.add(createBackupCode());
        return codes;
    }

    @NotNull
    private String createBackupCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder builder = new StringBuilder("PAL-");

        for (int index = 0; index < 8; index++) {
            if (index == 4) builder.append('-');
            builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }

        return builder.toString();
    }

    @NotNull
    private String normalizeBackup(@NotNull String code) {
        return code.trim().replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private boolean canChallenge() {
        return options.isEnabled() && (options.isTotp() || options.isBackupCodes());
    }

    @NotNull
    private CompletionStage<TwoFactorResult> completed(@NotNull String key) {
        return CompletableFuture.completedFuture(TwoFactorResult.message(key));
    }

    @NotNull
    private Map<String, Object> placeholders(@NotNull Object... values) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            placeholders.put(String.valueOf(values[index]), values[index + 1]);
        }
        return placeholders;
    }

    @Nullable
    private InetAddress address(@NotNull Player player) {
        InetSocketAddress socketAddress = player.getAddress();
        return socketAddress == null ? null : socketAddress.getAddress();
    }

    @NotNull
    private StorageServiceImpl storage() {
        return (StorageServiceImpl) api.getStorageService();
    }

    @NotNull
    private PALPlugin plugin() {
        return (PALPlugin) api.getPlugin();
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    static final class TwoFactorResult {

        private final boolean successful;
        @NotNull
        private final String messageKey;
        @NotNull
        private final Map<String, Object> placeholders;
        @NotNull
        private final List<String> backupCodes;

        @NotNull
        static TwoFactorResult message(@NotNull String messageKey) {
            return message(messageKey, Collections.emptyMap());
        }

        @NotNull
        static TwoFactorResult message(@NotNull String messageKey, @NotNull Map<String, Object> placeholders) {
            return message(messageKey, placeholders, Collections.emptyList());
        }

        @NotNull
        static TwoFactorResult message(
                @NotNull String messageKey,
                @NotNull Map<String, Object> placeholders,
                @NotNull List<String> backupCodes
        ) {
            boolean successful = messageKey.endsWith("-success")
                    || messageKey.endsWith("-enabled")
                    || messageKey.endsWith("-enabled-success")
                    || messageKey.endsWith("two-factor-success");

            return new TwoFactorResult(
                    successful,
                    messageKey,
                    Collections.unmodifiableMap(new LinkedHashMap<>(placeholders)),
                    Collections.unmodifiableList(new ArrayList<>(backupCodes))
            );
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class PendingChallenge {

        @NotNull
        private final UUID playerId;
        @NotNull
        private final String playerName;
        @NotNull
        private final PALAccount account;
        @NotNull
        private final AuthSource source;
        @Nullable
        private final InetAddress address;
        private int attempts;

        private PendingChallenge(
                @NotNull UUID playerId,
                @NotNull String playerName,
                @NotNull PALAccount account,
                @NotNull AuthSource source,
                @Nullable InetAddress address
        ) {
            this(playerId, playerName, account, source, address, 0);
        }

        private void incrementAttempts() {
            attempts++;
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class PendingSetup {

        @NotNull
        private final PALAccount account;
        @NotNull
        private final String secret;
        @NotNull
        private final String uri;
    }
}
