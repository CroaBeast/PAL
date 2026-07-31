package com.bitaspire.pal.proxy.realm;

import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public final class ProxyRoleMessageCodec {

    public static final String CHANNEL = "pal:realm";

    private ProxyRoleMessageCodec() {
    }

    @NotNull
    public static byte[] encode(
            @NotNull UUID playerId,
            @NotNull ProxyRealm realm,
            @NotNull String server,
            @NotNull String secret
    ) {
        try {
            String encodedServer = Base64.getEncoder().encodeToString(server.getBytes(StandardCharsets.UTF_8));
            String signed = "1|" + playerId + "|" + realm.name() + "|" + encodedServer + "|" + Instant.now().getEpochSecond();
            return (signed + "|" + sign(signed, secret)).getBytes(StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return new byte[0];
        }
    }

    @NotNull
    private static String sign(@NotNull String content, @NotNull String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }
}
