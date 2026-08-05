package com.bitaspire.pal;

import com.bitaspire.pal.account.PALAccount;
import com.bitaspire.pal.auth.AuthSource;
import com.bitaspire.pal.bridge.BridgeSession;
import com.bitaspire.pal.event.PALSessionCreateEvent;
import com.bitaspire.pal.event.PALSessionInvalidateEvent;
import com.bitaspire.pal.identity.IdentityTrust;
import com.bitaspire.pal.identity.IdentityType;
import com.bitaspire.pal.session.AuthSession;
import com.bitaspire.pal.session.SessionService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

final class SessionServiceImpl extends AbstractService implements SessionService {

    private final Map<UUID, AuthSession> sessionsById = new ConcurrentHashMap<>();
    private final Map<String, UUID> sessionsByName = new ConcurrentHashMap<>();
    private SessionOptions options = SessionOptions.defaults();

    SessionServiceImpl(@NotNull PALApi api) {
        super(api);
    }

    @Override
    public boolean register() {
        super.register();
        reload();
        return true;
    }

    void reload() {
        options = SessionOptions.load((PALPlugin) api.getPlugin());

        if (!isRegistered()) return;

        sessionsById.clear();
        sessionsByName.clear();
        storage().findActiveSessions(System.currentTimeMillis()).toCompletableFuture().join()
                .forEach(this::cache);
    }

    @Override
    public boolean unregister() {
        sessionsById.clear();
        sessionsByName.clear();
        return super.unregister();
    }

    @Override
    public boolean isAuthenticated(@NotNull UUID uniqueId) {
        return getSession(uniqueId).isPresent();
    }

    @Override
    public boolean isAuthenticated(@NotNull UUID uniqueId, @NotNull String name, @Nullable InetAddress address) {
        return getSession(uniqueId, name, address).isPresent();
    }

    @NotNull
    @Override
    public Optional<AuthSession> getSession(@NotNull UUID uniqueId) {
        return lookup(uniqueId, null, null);
    }

    @NotNull
    @Override
    public Optional<AuthSession> getSession(@NotNull UUID uniqueId, @NotNull String name, @Nullable InetAddress address) {
        return lookup(uniqueId, name, address);
    }

    @NotNull
    @Override
    public CompletionStage<AuthSession> create(@NotNull PALAccount account, @NotNull AuthSource source, @Nullable InetAddress address) {
        Instant now = Instant.now();
        Instant expires = options.isEnabled() && options.getMinutes() > 0
                ? now.plusSeconds(options.getMinutes() * 60L)
                : null;

        StoredSession session = new StoredSession(
                account.getUniqueId(),
                account.getName(),
                UUID.randomUUID().toString().replace("-", ""),
                source,
                options.isBindIp() ? address : null,
                options.isBindIp() ? hashAddress(address) : null,
                now,
                expires
        );

        if (!options.isEnabled()) {
            cache(session);
            return publish(session)
                    .thenCompose(ignored -> storage().touchLogin(account.getUniqueId(), address, now))
                    .thenRun(() -> plugin().callEvent(new PALSessionCreateEvent(account, session)))
                    .thenApply(ignored -> session);
        }

        return save(session)
                .thenCompose(ignored -> storage().touchLogin(account.getUniqueId(), address, now))
                .thenRun(() -> plugin().callEvent(new PALSessionCreateEvent(account, session)))
                .thenApply(ignored -> session);
    }

    @Override
    public void invalidate(@NotNull UUID uniqueId) {
        invalidateAsync(uniqueId);
    }

    @NotNull
    @Override
    public CompletionStage<Void> invalidateAsync(@NotNull UUID uniqueId) {
        AuthSession previous = remove(uniqueId);

        CompletionStage<Void> storageStage = options.isEnabled()
                ? storage().deleteSession(uniqueId)
                : CompletableFuture.completedFuture(null);

        return storageStage
                .thenCompose(ignored -> api.getBridgeService().invalidateSession(uniqueId))
                .thenRun(() -> {
                    if (previous != null) plugin().callEvent(new PALSessionInvalidateEvent(previous));
                })
                .exceptionally(throwable -> {
                    logger().log(Level.WARNING, "Could not invalidate session " + uniqueId + ": " + rootMessage(throwable));
                    return null;
                });
    }

    @NotNull
    CompletionStage<Void> save(@NotNull AuthSession session) {
        cache(session);

        return api.getAccountService().findById(session.getUniqueId())
                .thenCompose(account -> save(session, toBridgeSession(session, account.orElse(null))))
                .exceptionally(throwable -> {
                    logger().log(Level.WARNING, "Could not save session " + session.getUniqueId() + ": " + rootMessage(throwable));
                    return null;
                });
    }

    @NotNull
    private CompletionStage<Void> save(@NotNull AuthSession session, @NotNull BridgeSession bridgeSession) {
        CompletionStage<Void> storageStage = options.isEnabled()
                ? storage().saveSession(session, bridge().encodeSession(bridgeSession))
                : CompletableFuture.completedFuture(null);

        return storageStage.thenCompose(ignored -> publish(bridgeSession));
    }

    @NotNull
    private Optional<AuthSession> lookup(@NotNull UUID uniqueId, @Nullable String name, @Nullable InetAddress address) {
        AuthSession session = sessionsById.get(uniqueId);
        if (session == null && name != null) {
            UUID sessionId = sessionsByName.get(key(name));
            if (sessionId != null) session = sessionsById.get(sessionId);
        }

        if (session == null) return Optional.empty();
        if (isExpired(session)) {
            invalidate(session.getUniqueId());
            return Optional.empty();
        }

        if (!matches(session, name, address)) return Optional.empty();
        return Optional.of(session);
    }

    private boolean matches(@NotNull AuthSession session, @Nullable String name, @Nullable InetAddress address) {
        if (options.isBindName() && name != null && !session.getName().equalsIgnoreCase(name)) return false;
        if (!options.isBindIp()) return true;

        String storedHash = session.getAddressHash();
        return storedHash == null || address == null || storedHash.equals(hashAddress(address));
    }

    private boolean isExpired(@NotNull AuthSession session) {
        return session.getExpiresAt() != null && session.getExpiresAt().toEpochMilli() <= System.currentTimeMillis();
    }

    private void cache(@NotNull AuthSession session) {
        sessionsById.put(session.getUniqueId(), session);
        if (!session.getName().trim().isEmpty()) sessionsByName.put(key(session.getName()), session.getUniqueId());
    }

    @Nullable
    private AuthSession remove(@NotNull UUID uniqueId) {
        AuthSession session = sessionsById.remove(uniqueId);
        if (session != null && !session.getName().trim().isEmpty()) sessionsByName.remove(key(session.getName()));
        return session;
    }

    @NotNull
    private CompletionStage<Void> publish(@NotNull AuthSession session) {
        if (!api.getBridgeService().isEnabled()) return CompletableFuture.completedFuture(null);

        return api.getAccountService().findById(session.getUniqueId()).thenCompose(account ->
                api.getBridgeService().publishSession(toBridgeSession(session, account.orElse(null)))
        );
    }

    @NotNull
    private CompletionStage<Void> publish(@NotNull BridgeSession session) {
        return api.getBridgeService().isEnabled()
                ? api.getBridgeService().publishSession(session)
                : CompletableFuture.completedFuture(null);
    }

    @NotNull
    private BridgeSession toBridgeSession(@NotNull AuthSession session, @Nullable PALAccount account) {
        PALAccount.Type type = account == null ? PALAccount.Type.UNKNOWN : account.getType();

        return BridgeSession.builder()
                .uniqueId(session.getUniqueId())
                .name(session.getName())
                .sessionId(session.getSessionId())
                .source(session.getSource())
                .identityType(identityType(type))
                .identityTrust(identityTrust(type))
                .authenticatedAt(session.getAuthenticatedAt())
                .expiresAt(session.getExpiresAt())
                .addressHash(session.getAddressHash())
                .build();
    }

    @NotNull
    private IdentityType identityType(@NotNull PALAccount.Type type) {
        switch (type) {
            case PREMIUM:
                return IdentityType.JAVA_PREMIUM;
            case OFFLINE:
                return IdentityType.JAVA_OFFLINE;
            case BEDROCK:
                return IdentityType.BEDROCK;
            default:
                return IdentityType.UNKNOWN;
        }
    }

    @NotNull
    private IdentityTrust identityTrust(@NotNull PALAccount.Type type) {
        switch (type) {
            case PREMIUM:
            case BEDROCK:
                return IdentityTrust.VERIFIED_SESSION;
            case OFFLINE:
                return IdentityTrust.MANUAL;
            default:
                return IdentityTrust.UNVERIFIED;
        }
    }

    @Nullable
    private String hashAddress(@Nullable InetAddress address) {
        if (address == null) return null;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(address.getHostAddress().getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private StorageServiceImpl storage() {
        return (StorageServiceImpl) api.getStorageService();
    }

    private BridgeServiceImpl bridge() {
        return (BridgeServiceImpl) api.getBridgeService();
    }

    private PALPlugin plugin() {
        return (PALPlugin) api.getPlugin();
    }

    private java.util.logging.Logger logger() {
        return plugin().getLogger();
    }

    private String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage();
    }
}
