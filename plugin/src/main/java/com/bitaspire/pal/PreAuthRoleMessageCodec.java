package com.bitaspire.pal;

import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

final class PreAuthRoleMessageCodec {

    static final String CHANNEL = "pal:realm";

    private PreAuthRoleMessageCodec() {
    }

    @NotNull
    static Optional<PreAuthRealm> decode(
            @NotNull UUID expectedPlayer,
            @NotNull byte[] payload,
            @NotNull String secret,
            int skewSeconds
    ) {
        if (!hasUsableSecret(secret)) return Optional.empty();

        String message = new String(payload, StandardCharsets.UTF_8);
        String[] parts = message.split("\\|", -1);
        if (parts.length != 6 || !"1".equals(parts[0])) return Optional.empty();

        try {
            UUID playerId = UUID.fromString(parts[1]);
            if (!expectedPlayer.equals(playerId)) return Optional.empty();

            long issuedAt = Long.parseLong(parts[4]);
            long now = Instant.now().getEpochSecond();
            if (Math.abs(now - issuedAt) > Math.max(5, skewSeconds)) return Optional.empty();

            String signed = parts[0] + "|" + parts[1] + "|" + parts[2] + "|" + parts[3] + "|" + parts[4];
            if (!MessageDigest.isEqual(sign(signed, secret).getBytes(StandardCharsets.UTF_8), parts[5].getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }

            return Optional.of(PreAuthRealm.from(parts[2], PreAuthRealm.AUTO));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    static boolean hasUsableSecret(@NotNull String secret) {
        String value = secret.trim();
        return !value.isEmpty() && !"change-me".equalsIgnoreCase(value);
    }

    @NotNull
    private static String sign(@NotNull String content, @NotNull String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }
}
