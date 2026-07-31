package com.bitaspire.pal.proxy.connection;

import com.bitaspire.pal.proxy.bridge.BridgeOptions;
import com.bitaspire.pal.proxy.bridge.ProxyBridgeConfig;
import com.bitaspire.pal.proxy.bridge.SessionBridge;
import com.bitaspire.pal.proxy.identity.IdentityProvider;
import com.bitaspire.pal.proxy.identity.IdentityResolver;
import com.bitaspire.pal.proxy.identity.IdentityResult;
import com.bitaspire.pal.proxy.identity.IdentityTrust;
import com.bitaspire.pal.proxy.identity.IdentityType;
import com.bitaspire.pal.proxy.session.AuthSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DefaultConnectionGuardTest {

    @TempDir
    private Path directory;

    @Test
    void allowsRawPersistedSessionBeforeTryingEnrichedIdentity() {
        UUID rawId = UUID.fromString("55a0e60b-184c-3b18-a863-891527b754f6");
        UUID enrichedId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        AuthSession session = AuthSession.of(
                rawId,
                "BeTezca",
                "session",
                System.currentTimeMillis() + 60_000L,
                "auth",
                null
        );
        DefaultConnectionGuard guard = new DefaultConnectionGuard(
                new SingleSessionBridge(session),
                new EnrichingResolver(enrichedId),
                BridgeOptions.authServer("auth")
        );

        ConnectionDecision decision = guard.validate(ConnectionRequest.builder()
                .name("BeTezca")
                .uniqueId(rawId)
                .requestedServer("survival")
                .build()
        ).toCompletableFuture().join();

        assertEquals(ConnectionDecision.Type.ALLOW, decision.getType());
        assertSame(session, decision.getSession());
    }

    @Test
    void savesVerifiedPremiumSessionWhenInitialTargetIsAuth() throws Exception {
        UUID uniqueId = UUID.fromString("5843a491-fdbc-440a-a54f-c6c2bac73c58");
        CapturingBridge bridge = new CapturingBridge();
        DefaultConnectionGuard guard = new DefaultConnectionGuard(
                bridge,
                new FixedPremiumResolver(uniqueId, "CroaBeast"),
                proxyAutoLoginOptions()
        );

        ConnectionDecision decision = guard.validate(ConnectionRequest.builder()
                .name("CroaBeast")
                .uniqueId(uniqueId)
                .requestedServer("auth")
                .build()
        ).toCompletableFuture().join();

        assertEquals(ConnectionDecision.Type.ALLOW, decision.getType());
        assertNotNull(bridge.saved);
        assertEquals(uniqueId, bridge.saved.getUniqueId());
        assertEquals("CroaBeast", bridge.saved.getName());
        assertEquals(IdentityType.JAVA_PREMIUM, bridge.saved.getIdentityType());
        assertEquals(IdentityTrust.VERIFIED_SESSION, bridge.saved.getIdentityTrust());
    }

    @Test
    void allowsAuthenticatedPlayerToManuallyTargetAuthServerWhenConfigured() throws Exception {
        UUID uniqueId = UUID.fromString("55a0e60b-184c-3b18-a863-891527b754f6");
        AuthSession session = AuthSession.of(
                uniqueId,
                "BeTezca",
                "session",
                System.currentTimeMillis() + 60_000L,
                "auth",
                null
        );
        DefaultConnectionGuard guard = new DefaultConnectionGuard(
                new SingleSessionBridge(session),
                new EnrichingResolver(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                manualAuthTargetOptions("ALLOW")
        );

        ConnectionDecision decision = guard.validate(ConnectionRequest.builder()
                .name("BeTezca")
                .uniqueId(uniqueId)
                .requestedServer("auth")
                .allowAuthenticatedAuthTarget(true)
                .build()
        ).toCompletableFuture().join();

        assertEquals(ConnectionDecision.Type.ALLOW, decision.getType());
        assertSame(session, decision.getSession());
    }

    @Test
    void redirectsAuthenticatedPlayerAwayFromAuthServerByDefault() throws Exception {
        UUID uniqueId = UUID.fromString("55a0e60b-184c-3b18-a863-891527b754f6");
        AuthSession session = AuthSession.of(
                uniqueId,
                "BeTezca",
                "session",
                System.currentTimeMillis() + 60_000L,
                "auth",
                null
        );
        DefaultConnectionGuard guard = new DefaultConnectionGuard(
                new SingleSessionBridge(session),
                new EnrichingResolver(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                manualAuthTargetOptions("REDIRECT")
        );

        ConnectionDecision decision = guard.validate(ConnectionRequest.builder()
                .name("BeTezca")
                .uniqueId(uniqueId)
                .requestedServer("auth")
                .allowAuthenticatedAuthTarget(true)
                .build()
        ).toCompletableFuture().join();

        assertEquals(ConnectionDecision.Type.REDIRECT, decision.getType());
        assertEquals("lobby", decision.getTargetServer());
    }

    private BridgeOptions proxyAutoLoginOptions() throws Exception {
        Files.write(directory.resolve("bridge.yml"), (
                "bridge:\n" +
                        "  enabled: true\n" +
                        "  mode: MEMORY\n" +
                        "  net:\n" +
                        "    auth: auth\n" +
                        "    lobby: lobby\n" +
                        "  guard:\n" +
                        "    required: true\n" +
                        "    premium: true\n" +
                        "    bedrock: true\n" +
                        "    proxy-auto-login: true\n"
        ).getBytes(StandardCharsets.UTF_8));
        return ProxyBridgeConfig.load(directory.toFile(), 1).toOptions();
    }

    private BridgeOptions manualAuthTargetOptions(String action) throws Exception {
        Files.write(directory.resolve("bridge.yml"), (
                "bridge:\n" +
                        "  enabled: true\n" +
                        "  mode: MEMORY\n" +
                        "  net:\n" +
                        "    auth: auth\n" +
                        "    lobby: lobby\n" +
                        "  guard:\n" +
                        "    authenticated-auth-target: " + action + "\n"
        ).getBytes(StandardCharsets.UTF_8));
        return ProxyBridgeConfig.load(directory.toFile(), 1).toOptions();
    }

    private static final class SingleSessionBridge implements SessionBridge {

        private final AuthSession session;

        private SingleSessionBridge(AuthSession session) {
            this.session = session;
        }

        @Override
        public CompletionStage<Optional<AuthSession>> findSession(UUID uniqueId, String name) {
            return CompletableFuture.completedFuture(session.getUniqueId().equals(uniqueId)
                    && session.getName().equalsIgnoreCase(name)
                    ? Optional.of(session)
                    : Optional.empty());
        }

        @Override
        public CompletionStage<Void> saveSession(AuthSession session) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> invalidate(UUID uniqueId) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class CapturingBridge implements SessionBridge {

        private AuthSession saved;

        @Override
        public CompletionStage<Optional<AuthSession>> findSession(UUID uniqueId, String name) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletionStage<Void> saveSession(AuthSession session) {
            saved = session;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> invalidate(UUID uniqueId) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class EnrichingResolver implements IdentityResolver {

        private final UUID uniqueId;

        private EnrichingResolver(UUID uniqueId) {
            this.uniqueId = uniqueId;
        }

        @Override
        public IdentityProvider getProvider() {
            return IdentityProvider.FLOODGATE;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public ConnectionRequest enrich(ConnectionRequest request) {
            return request.toBuilder()
                    .uniqueId(uniqueId)
                    .name("." + request.getName())
                    .bedrockVerified(true)
                    .build();
        }

        @Override
        public CompletionStage<IdentityResult> resolve(ConnectionRequest request) {
            return CompletableFuture.completedFuture(IdentityResult.verifiedPremium(
                    IdentityProvider.FLOODGATE,
                    uniqueId,
                    request.getName(),
                    IdentityTrust.FLOODGATE
            ));
        }
    }

    private static final class FixedPremiumResolver implements IdentityResolver {

        private final UUID uniqueId;
        private final String name;

        private FixedPremiumResolver(UUID uniqueId, String name) {
            this.uniqueId = uniqueId;
            this.name = name;
        }

        @Override
        public IdentityProvider getProvider() {
            return IdentityProvider.PAL_NATIVE;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public CompletionStage<IdentityResult> resolve(ConnectionRequest request) {
            return CompletableFuture.completedFuture(IdentityResult.verifiedPremium(
                    IdentityProvider.PAL_NATIVE,
                    uniqueId,
                    name,
                    IdentityTrust.VERIFIED_SESSION
            ));
        }
    }
}
