package com.bitaspire.pal;

import com.bitaspire.pal.account.AccountCreateRequest;
import com.bitaspire.pal.account.AccountResult;
import com.bitaspire.pal.account.PALAccount;
import com.bitaspire.pal.auth.AuthRequest;
import com.bitaspire.pal.auth.AuthResult;
import com.bitaspire.pal.auth.AuthService;
import com.bitaspire.pal.auth.PasswordHash;
import com.bitaspire.pal.auth.PasswordHasher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class AuthServiceImpl extends AbstractService implements AuthService {

    private final PasswordHasher hasher = new Pbkdf2PasswordHasher();
    private final AuthThrottle throttle = new AuthThrottle();
    private AuthOptions options;

    AuthServiceImpl(@NotNull PALApi api) {
        super(api);
    }

    @Override
    public boolean register() {
        super.register();
        reload();
        return true;
    }

    void reload() {
        options = AuthOptions.load((PALPlugin) api.getPlugin());
    }

    @NotNull
    @Override
    public CompletionStage<AuthResult> register(@NotNull AuthRequest request) {
        if (options == null) reload();
        if (!options.isRegistration()) return completed(AuthResult.Type.NOT_AVAILABLE, "auth.registration-disabled");

        String password = request.getSecret();
        AuthResult validation = validateRegistration(request, password);
        if (validation != null) return CompletableFuture.completedFuture(validation);

        AccountCreateRequest createRequest = AccountCreateRequest.offline(request.getName(), request.getAddress());

        return accountService().create(createRequest).thenCompose(result -> {
            if (!result.isSuccessful() || result.getAccount() == null) {
                return CompletableFuture.completedFuture(mapAccountResult(result));
            }

            PasswordHash hash = hasher.hash(password.toCharArray());
            clear(password);

            return storage().savePasswordHash(result.getAccount().getUniqueId(), hash)
                    .thenCompose(ignored -> storage().audit(result.getAccount().getUniqueId(), result.getAccount().getName(),
                            "auth.register", request.getAddress(), result.getAccount().getType().name()))
                    .thenCompose(ignored -> startSession(result.getAccount(), request))
                    .thenApply(ignored -> AuthResult.of(AuthResult.Type.SUCCESS, "auth.registered"));
        }).exceptionally(throwable -> AuthResult.of(AuthResult.Type.ERROR, "auth.error"));
    }

    @NotNull
    @Override
    public CompletionStage<AuthResult> login(@NotNull AuthRequest request) {
        if (options == null) reload();

        AuthResult.Type throttleResult = throttle.check(request.getName(), request.getAddress(), options);
        if (throttleResult != null) return completed(throttleResult, throttleResult == AuthResult.Type.BLOCKED ? "security.locked" : "auth.rate-limited");

        String password = request.getSecret();
        if (isBlank(password)) return failedAttempt(request, AuthResult.Type.INVALID_SECRET, "auth.invalid-password");

        return accountService().findByName(request.getName()).thenCompose(account -> {
            if (!account.isPresent()) {
                return failedAttempt(request, AuthResult.Type.NOT_REGISTERED, "auth.not-registered");
            }

            PALAccount palAccount = account.get();
            if (sessionService().isAuthenticated(palAccount.getUniqueId(), palAccount.getName(), request.getAddress())) {
                return completed(AuthResult.Type.ALREADY_AUTHENTICATED, "auth.already-logged");
            }

            return storage().findPasswordHash(palAccount.getUniqueId()).thenCompose(hash -> {
                if (!hash.isPresent() || !hasher.verify(password.toCharArray(), hash.get())) {
                    clear(password);
                    return failedAttempt(request, AuthResult.Type.INVALID_SECRET, "auth.invalid-password");
                }

                clear(password);
                throttle.success(request.getName(), request.getAddress());

                CompletionStage<Void> rehashStage = options.isRehash() && hasher.needsRehash(hash.get())
                        ? storage().savePasswordHash(palAccount.getUniqueId(), hasher.hash(request.getSecret().toCharArray()))
                        : CompletableFuture.completedFuture(null);

                return rehashStage
                        .thenCompose(ignored -> storage().loginAttempt(palAccount.getUniqueId(), request.getName(), request.getAddress(), true, null))
                        .thenCompose(ignored -> storage().audit(palAccount.getUniqueId(), palAccount.getName(), "auth.login",
                                request.getAddress(), request.getSource().name()))
                        .thenCompose(ignored -> twoFactorService().beginLogin(
                                request.getUniqueId(),
                                request.getName(),
                                palAccount,
                                request.getSource(),
                                request.getAddress()
                        ))
                        .thenCompose(required -> required
                                ? completed(AuthResult.Type.BLOCKED, "security.two-factor-required")
                                : startSession(palAccount, request).thenApply(ignored -> AuthResult.success()));
            });
        }).exceptionally(throwable -> AuthResult.of(AuthResult.Type.ERROR, "auth.error"));
    }

    @NotNull
    @Override
    public CompletionStage<AuthResult> logout(@NotNull AuthRequest request) {
        return accountService().findByName(request.getName()).thenCompose(account -> {
            UUID uniqueId = account.map(PALAccount::getUniqueId).orElse(request.getUniqueId());
            String name = account.map(PALAccount::getName).orElse(request.getName());

            if (!sessionService().isAuthenticated(uniqueId, name, request.getAddress())) {
                return completed(AuthResult.Type.NOT_AVAILABLE, "auth.not-logged");
            }

            return sessionService().invalidateAsync(uniqueId)
                    .thenRun(() -> preAuthService().markUnauthenticated(uniqueId, name))
                    .thenCompose(ignored -> storage().audit(uniqueId, name, "auth.logout", request.getAddress(), request.getSource().name()))
                    .thenApply(ignored -> AuthResult.of(AuthResult.Type.SUCCESS, "auth.logout"));
        }).exceptionally(throwable -> AuthResult.of(AuthResult.Type.ERROR, "auth.error"));
    }

    @NotNull
    @Override
    public CompletionStage<AuthResult> changePassword(@NotNull AuthRequest request) {
        if (options == null) reload();

        String current = request.getSecret();
        String next = request.getNewSecret();
        if (isBlank(current) || isBlank(next)) return completed(AuthResult.Type.INVALID_SECRET, "auth.invalid-password");

        AuthResult validation = validatePassword(request.getName(), next);
        if (validation != null) return CompletableFuture.completedFuture(validation);

        return accountService().findByName(request.getName()).thenCompose(account -> {
            if (!account.isPresent()) return completed(AuthResult.Type.NOT_REGISTERED, "auth.not-registered");

            PALAccount palAccount = account.get();

            return storage().findPasswordHash(palAccount.getUniqueId()).thenCompose(hash -> {
                if (!hash.isPresent() || !hasher.verify(current.toCharArray(), hash.get())) {
                    clear(current);
                    clear(next);
                    return completed(AuthResult.Type.INVALID_SECRET, "auth.invalid-password");
                }

                clear(current);
                PasswordHash nextHash = hasher.hash(next.toCharArray());
                clear(next);

                CompletionStage<Void> invalidation = options.isChangeInvalidates()
                        ? sessionService().invalidateAsync(palAccount.getUniqueId())
                        .thenRun(() -> preAuthService().markUnauthenticated(palAccount.getUniqueId(), palAccount.getName()))
                        : CompletableFuture.completedFuture(null);

                return invalidation
                        .thenCompose(ignored -> storage().savePasswordHash(palAccount.getUniqueId(), nextHash))
                        .thenCompose(ignored -> storage().audit(palAccount.getUniqueId(), palAccount.getName(),
                                "auth.password", request.getAddress(), request.getSource().name()))
                        .thenApply(ignored -> AuthResult.of(AuthResult.Type.SUCCESS, "auth.password-changed"));
            });
        }).exceptionally(throwable -> AuthResult.of(AuthResult.Type.ERROR, "auth.error"));
    }

    private AuthResult validateRegistration(@NotNull AuthRequest request, @Nullable String password) {
        if (!options.getNamePattern().matcher(request.getName()).matches()) {
            return AuthResult.of(AuthResult.Type.INVALID_SECRET, "auth.invalid-name");
        }

        AuthResult passwordResult = validatePassword(request.getName(), password);
        if (passwordResult != null) return passwordResult;

        if (options.isRepeat() && !password.equals(request.getConfirmation())) {
            return AuthResult.of(AuthResult.Type.INVALID_CONFIRMATION, "auth.password-mismatch");
        }

        return null;
    }

    private AuthResult validatePassword(@NotNull String name, @Nullable String password) {
        if (isBlank(password)) return AuthResult.of(AuthResult.Type.INVALID_SECRET, "auth.invalid-password");
        if (password.length() < options.getMinLength()) return AuthResult.of(AuthResult.Type.PASSWORD_WEAK, "auth.password-short");
        if (password.length() > options.getMaxLength()) return AuthResult.of(AuthResult.Type.PASSWORD_WEAK, "auth.password-long");
        if (options.isBlockUsername() && password.toLowerCase().contains(name.toLowerCase())) {
            return AuthResult.of(AuthResult.Type.PASSWORD_WEAK, "auth.password-name");
        }
        if (options.isCommon(password)) return AuthResult.of(AuthResult.Type.PASSWORD_WEAK, "auth.password-common");

        return null;
    }

    @Nullable
    AuthResult validateNewPassword(@NotNull String name, @Nullable String password, @Nullable String confirmation) {
        if (options == null) reload();

        AuthResult result = validatePassword(name, password);
        if (result != null) return result;
        return !password.equals(confirmation)
                ? AuthResult.of(AuthResult.Type.INVALID_CONFIRMATION, "auth.password-mismatch")
                : null;
    }

    @NotNull
    PasswordHash hashPassword(@NotNull String password) {
        return hasher.hash(password.toCharArray());
    }

    private CompletionStage<Void> startSession(@NotNull PALAccount account, @NotNull AuthRequest request) {
        return sessionService().create(account, request.getSource(), request.getAddress()).thenApply(session -> {
            preAuthService().markAuthenticated(account);
            return null;
        });
    }

    private CompletionStage<AuthResult> failedAttempt(@NotNull AuthRequest request, @NotNull AuthResult.Type type, @NotNull String messageKey) {
        throttle.failure(request.getName(), request.getAddress(), options);

        return storage().loginAttempt(request.getUniqueId(), request.getName(), request.getAddress(), false, messageKey)
                .thenApply(ignored -> AuthResult.of(type, messageKey));
    }

    private AuthResult mapAccountResult(@NotNull AccountResult result) {
        switch (result.getType()) {
            case ALREADY_EXISTS:
                return AuthResult.of(AuthResult.Type.ALREADY_REGISTERED, "auth.already-registered");
            case NAME_LOCKED:
                return AuthResult.of(AuthResult.Type.BLOCKED, "account.name-locked");
            case INVALID_REQUEST:
                return AuthResult.of(AuthResult.Type.INVALID_SECRET, result.getMessageKey());
            default:
                return AuthResult.of(AuthResult.Type.ERROR, result.getMessageKey());
        }
    }

    private CompletionStage<AuthResult> completed(@NotNull AuthResult.Type type, @NotNull String messageKey) {
        return CompletableFuture.completedFuture(AuthResult.of(type, messageKey));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void clear(String value) {
        if (value == null) return;

        char[] chars = value.toCharArray();
        Arrays.fill(chars, '\0');
    }

    private AccountServiceImpl accountService() {
        return (AccountServiceImpl) api.getAccountService();
    }

    private SessionServiceImpl sessionService() {
        return (SessionServiceImpl) api.getSessionService();
    }

    private StorageServiceImpl storage() {
        return (StorageServiceImpl) api.getStorageService();
    }

    private PreAuthServiceImpl preAuthService() {
        return ((PALPlugin) api.getPlugin()).getPreAuthService();
    }

    private TwoFactorServiceImpl twoFactorService() {
        return ((PALPlugin) api.getPlugin()).getTwoFactorService();
    }
}
