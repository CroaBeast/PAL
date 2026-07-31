package com.bitaspire.pal.proxy.bridge;

import com.bitaspire.pal.proxy.identity.IdentityTrust;
import com.bitaspire.pal.proxy.identity.IdentityType;
import com.bitaspire.pal.proxy.session.AuthSession;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

final class SignedBridgeCodec {

    private static final String HMAC = "HmacSHA256";
    private static final String NULL = "-";

    private final String secret;
    private final boolean requireSecret;
    private final long skewMillis;

    SignedBridgeCodec(@NotNull String secret, boolean requireSecret, long skewMillis) {
        this.secret = secret;
        this.requireSecret = requireSecret;
        this.skewMillis = Math.max(0L, skewMillis);
    }

    @NotNull
    String encode(@NotNull AuthSession session) {
        String payload = join(
                "1",
                "session",
                session.getUniqueId().toString(),
                encodeText(session.getName()),
                encodeText(session.getSessionId()),
                encodeText(session.getSource()),
                session.getIdentityType().name(),
                session.getIdentityTrust().name(),
                String.valueOf(session.getAuthenticatedAtMillis()),
                String.valueOf(session.getExpiresAtMillis()),
                encodeText(session.getAddressHash()),
                encodeText(session.getSourceServer()),
                String.valueOf(System.currentTimeMillis())
        );

        return payload + "|" + sign(payload);
    }

    @NotNull
    String encodeDelete(@NotNull UUID uniqueId) {
        String payload = join("1", "delete", uniqueId.toString(), String.valueOf(System.currentTimeMillis()));
        return payload + "|" + sign(payload);
    }

    @NotNull
    Optional<Message> decode(@Nullable String wire) {
        if (wire == null || wire.trim().isEmpty()) return Optional.empty();

        int separator = wire.lastIndexOf('|');
        if (separator < 0) return Optional.empty();

        String payload = wire.substring(0, separator);
        String signature = wire.substring(separator + 1);
        if (!isValid(payload, signature)) return Optional.empty();

        String[] parts = payload.split("\\|", -1);
        if (parts.length < 4 || !"1".equals(parts[0])) return Optional.empty();

        if ("delete".equals(parts[1])) {
            return Optional.of(Message.delete(UUID.fromString(parts[2])));
        }

        if (!"session".equals(parts[1]) || parts.length < 13) return Optional.empty();

        AuthSession session = AuthSession.builder()
                .uniqueId(UUID.fromString(parts[2]))
                .name(decodeText(parts[3], ""))
                .sessionId(decodeText(parts[4], ""))
                .source(decodeText(parts[5], null))
                .identityType(identityType(parts[6]))
                .identityTrust(identityTrust(parts[7]))
                .verifiedIdentity(identityTrust(parts[7]) != IdentityTrust.UNVERIFIED)
                .authenticatedAtMillis(parseLong(parts[8], 0L))
                .expiresAtMillis(parseLong(parts[9], 0L))
                .addressHash(decodeText(parts[10], null))
                .sourceServer(decodeText(parts[11], null))
                .build();

        return Optional.of(Message.session(session));
    }

    private boolean isValid(@NotNull String payload, @NotNull String signature) {
        if (requireSecret && (secret.trim().isEmpty() || "change-me".equalsIgnoreCase(secret.trim()))) return false;
        if (secret.trim().isEmpty()) return !requireSecret;

        boolean valid = MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        if (!valid) return false;

        String[] parts = payload.split("\\|", -1);
        long issuedAt = parseLong(parts[parts.length - 1], 0L);
        return skewMillis <= 0L || issuedAt <= 0L || issuedAt <= System.currentTimeMillis() + skewMillis;
    }

    @NotNull
    private String sign(@NotNull String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign PAL bridge payload", exception);
        }
    }

    @NotNull
    private static String join(@NotNull String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (builder.length() > 0) builder.append('|');
            builder.append(part == null ? NULL : part);
        }
        return builder.toString();
    }

    @NotNull
    private static String encodeText(@Nullable String value) {
        return value == null ? NULL : Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Nullable
    private static String decodeText(@NotNull String value, @Nullable String fallback) {
        return NULL.equals(value) ? fallback : new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static long parseLong(@NotNull String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @NotNull
    private static IdentityType identityType(@NotNull String value) {
        try {
            return IdentityType.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return IdentityType.UNKNOWN;
        }
    }

    @NotNull
    private static IdentityTrust identityTrust(@NotNull String value) {
        try {
            return IdentityTrust.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return IdentityTrust.UNVERIFIED;
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    static final class Message {

        @NotNull
        private final Type type;

        @Nullable
        private final AuthSession session;

        @Nullable
        private final UUID uniqueId;

        @NotNull
        static Message session(@NotNull AuthSession session) {
            return new Message(Type.SESSION, session, session.getUniqueId());
        }

        @NotNull
        static Message delete(@NotNull UUID uniqueId) {
            return new Message(Type.DELETE, null, uniqueId);
        }

        enum Type {
            SESSION,
            DELETE
        }
    }
}
