package com.bitaspire.pal;

import com.bitaspire.pal.bridge.BridgeSession;
import com.bitaspire.pal.auth.AuthSource;
import com.bitaspire.pal.identity.IdentityTrust;
import com.bitaspire.pal.identity.IdentityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

final class BridgeSessionWire {

    private static final String HMAC = "HmacSHA256";
    private static final String NULL = "-";

    private final String secret;

    BridgeSessionWire(@NotNull String secret) {
        this.secret = secret;
    }

    @NotNull
    String encode(@NotNull BridgeSession session, @Nullable String sourceServer) {
        String payload = join(
                "1",
                "session",
                session.getUniqueId().toString(),
                text(session.getName()),
                text(session.getSessionId()),
                text(session.getSource().name()),
                session.getIdentityType().name(),
                session.getIdentityTrust().name(),
                String.valueOf(session.getAuthenticatedAt().toEpochMilli()),
                String.valueOf(session.getExpiresAt() == null ? 0L : session.getExpiresAt().toEpochMilli()),
                text(session.getAddressHash()),
                text(sourceServer),
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
    Optional<BridgeSession> decodeSession(@Nullable String wire) {
        if (wire == null || wire.trim().isEmpty()) return Optional.empty();

        int separator = wire.lastIndexOf('|');
        if (separator < 0) return Optional.empty();

        String payload = wire.substring(0, separator);
        String signature = wire.substring(separator + 1);
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }

        String[] parts = payload.split("\\|", -1);
        if (parts.length < 13 || !"1".equals(parts[0]) || !"session".equals(parts[1])) return Optional.empty();

        long expires = number(parts[9]);
        if (expires > 0L && expires <= System.currentTimeMillis()) return Optional.empty();

        return Optional.of(BridgeSession.builder()
                .uniqueId(UUID.fromString(parts[2]))
                .name(untext(parts[3], ""))
                .sessionId(untext(parts[4], ""))
                .source(source(untext(parts[5], AuthSource.PREMIUM.name())))
                .identityType(identityType(parts[6]))
                .identityTrust(identityTrust(parts[7]))
                .authenticatedAt(java.time.Instant.ofEpochMilli(number(parts[8])))
                .expiresAt(expires <= 0L ? null : java.time.Instant.ofEpochMilli(expires))
                .addressHash(untext(parts[10], null))
                .build());
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
    private static String text(@Nullable String value) {
        return value == null ? NULL : Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Nullable
    private static String untext(@NotNull String value, @Nullable String fallback) {
        return NULL.equals(value) ? fallback : new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static long number(@NotNull String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    @NotNull
    private static AuthSource source(@NotNull String value) {
        try {
            return AuthSource.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return AuthSource.PREMIUM;
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
}
