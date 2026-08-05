package com.bitaspire.pal.proxy.connection;

import com.bitaspire.pal.proxy.bridge.BridgeOptions;
import com.bitaspire.pal.proxy.bridge.SessionBridge;
import com.bitaspire.pal.proxy.identity.IdentityResolver;
import com.bitaspire.pal.proxy.identity.IdentityResult;
import com.bitaspire.pal.proxy.identity.IdentityType;
import com.bitaspire.pal.proxy.session.AuthSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class DefaultConnectionGuard implements ConnectionGuard {

    private final SessionBridge bridge;
    private final IdentityResolver identityResolver;
    private final BridgeOptions options;

    DefaultConnectionGuard(
            @NotNull SessionBridge bridge,
            @NotNull IdentityResolver identityResolver,
            @NotNull BridgeOptions options
    ) {
        this.bridge = bridge;
        this.identityResolver = identityResolver;
        this.options = options;
    }

    @NotNull
    @Override
    public CompletionStage<ConnectionDecision> validate(@NotNull ConnectionRequest request) {
        ConnectionRequest enriched = identityResolver.enrich(request);

        return bridge.findSession(request.getUniqueId(), request.getName())
                .thenCompose(session -> decideWithSession(request, enriched, session));
    }

    @NotNull
    private CompletionStage<ConnectionDecision> decideWithSession(
            @NotNull ConnectionRequest request,
            @NotNull ConnectionRequest enriched,
            @NotNull Optional<AuthSession> session
    ) {
        ConnectionDecision sessionDecision = decideFromSession(request, session);
        if (sessionDecision != null) return CompletableFuture.completedFuture(sessionDecision);

        if (!sameIdentity(request, enriched)) {
            return bridge.findSession(enriched.getUniqueId(), enriched.getName())
                    .thenCompose(enrichedSession -> {
                        ConnectionDecision enrichedDecision = decideFromSession(enriched, enrichedSession);
                        return enrichedDecision != null
                                ? CompletableFuture.completedFuture(enrichedDecision)
                                : decideWithoutSession(enriched);
                    });
        }

        return decideWithoutSession(enriched);
    }

    @NotNull
    private CompletionStage<ConnectionDecision> decideWithoutSession(@NotNull ConnectionRequest request) {
        return identityResolver.resolve(request)
                .thenCompose(identity -> decide(request, identity))
                .exceptionally(throwable -> decide(request, IdentityResult.unknown(
                        identityResolver.getProvider(),
                        throwable.getCause() == null ? throwable.getMessage() : throwable.getCause().getMessage()
                )).toCompletableFuture().join());
    }

    private ConnectionDecision decideFromSession(@NotNull ConnectionRequest request, Optional<AuthSession> session) {
        long now = System.currentTimeMillis();
        if (!session.isPresent() || session.get().isExpired(now)) return null;
        if (!matchesAddress(request, session.get())) return null;

        String target = authenticatedTarget(request);
        if (target != null) return ConnectionDecision.redirect(target, options.getAlreadyAuthenticatedMessage());

        return ConnectionDecision.allow(session.get());
    }

    private boolean sameIdentity(@NotNull ConnectionRequest first, @NotNull ConnectionRequest second) {
        return first.getName().equalsIgnoreCase(second.getName())
                && ((first.getUniqueId() == null && second.getUniqueId() == null)
                || (first.getUniqueId() != null && first.getUniqueId().equals(second.getUniqueId())));
    }

    private String authenticatedTarget(@NotNull ConnectionRequest request) {
        if (!isAuthServer(request)) return null;
        if (request.isAllowAuthenticatedAuthTarget() && options.allowsAuthenticatedAuthTarget()) return null;

        String requestedServer = request.getRequestedServer();
        if (options.hasLobbyServer() && !options.getLobbyServer().equalsIgnoreCase(requestedServer)) {
            return options.getLobbyServer();
        }

        if (options.getFallbackServer() != null
                && !options.getFallbackServer().trim().isEmpty()
                && !options.getFallbackServer().equalsIgnoreCase(requestedServer)) {
            return options.getFallbackServer();
        }

        return null;
    }

    private CompletionStage<ConnectionDecision> decide(ConnectionRequest request, IdentityResult identity) {
        if (!options.isAuthRequired()) return completed(ConnectionDecision.allow(identity));

        CompletionStage<AuthSession> proxySession = proxyAutoLoginSession(request, identity);
        if (proxySession != null) return proxySession.thenApply(ConnectionDecision::allow);

        if (isAuthServer(request)) return completed(ConnectionDecision.allow(identity));

        if (options.hasAuthServer()) {
            return completed(ConnectionDecision.redirect(options.getAuthServer(), options.getAuthenticationRequiredMessage(), identity));
        }

        if (options.isStrict()) {
            return completed(ConnectionDecision.deny(options.getAuthenticationRequiredMessage()));
        }

        return completed(ConnectionDecision.allow());
    }

    private CompletionStage<AuthSession> proxyAutoLoginSession(@NotNull ConnectionRequest request, @NotNull IdentityResult identity) {
        if (!options.isProxyAutoLogin() || !identity.isAutoLoginEligible()) return null;

        if (identity.getType() == IdentityType.JAVA_PREMIUM && options.isPremiumAuto()) {
            return saveVerifiedSession(request, identity);
        }

        if (identity.getType() == IdentityType.BEDROCK && options.isBedrockAuto()) {
            return saveVerifiedSession(request, identity);
        }

        return null;
    }

    @NotNull
    private CompletionStage<AuthSession> saveVerifiedSession(@NotNull ConnectionRequest request, @NotNull IdentityResult identity) {
        long now = System.currentTimeMillis();
        long expires = options.getSessionMinutes() <= 0 ? 0L : now + options.getSessionMinutes() * 60_000L;

        AuthSession session = AuthSession.builder()
                .uniqueId(identity.getUniqueId())
                .name(identity.getName())
                .sessionId(UUID.randomUUID().toString().replace("-", ""))
                .source(identity.getProvider().name())
                .identityType(identity.getType())
                .identityTrust(identity.getTrust())
                .verifiedIdentity(true)
                .authenticatedAtMillis(now)
                .expiresAtMillis(expires)
                .sourceServer(options.getAuthServer())
                .addressHash(options.isHashIp() ? hashAddress(request.getAddress()) : null)
                .build();

        return bridge.saveSession(session).thenApply(ignored -> session);
    }

    private boolean matchesAddress(@NotNull ConnectionRequest request, @NotNull AuthSession session) {
        if (!options.isBindIp()) return true;

        String storedHash = session.getAddressHash();
        if (storedHash == null) return true;

        String currentHash = hashAddress(request.getAddress());
        return currentHash == null || storedHash.equals(currentHash);
    }

    @Nullable
    private String hashAddress(@Nullable SocketAddress address) {
        if (!(address instanceof InetSocketAddress)) return null;

        InetAddress inetAddress = ((InetSocketAddress) address).getAddress();
        if (inetAddress == null) return null;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(inetAddress.getHostAddress().getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    @NotNull
    private CompletionStage<ConnectionDecision> completed(@NotNull ConnectionDecision decision) {
        return CompletableFuture.completedFuture(decision);
    }

    private boolean isAuthServer(ConnectionRequest request) {
        return options.hasAuthServer()
                && request.getRequestedServer() != null
                && options.getAuthServer().equalsIgnoreCase(request.getRequestedServer());
    }
}
