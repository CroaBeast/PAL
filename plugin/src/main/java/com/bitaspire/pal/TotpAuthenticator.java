package com.bitaspire.pal;

import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.Locale;

final class TotpAuthenticator {

    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final String HMAC = "HmacSHA1";

    private final SecureRandom random = new SecureRandom();

    @NotNull
    String createSecret() {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    boolean verify(@NotNull String secret, @NotNull String code, int digits, int periodSeconds, int window) {
        String normalized = code.replace(" ", "").trim();
        if (!normalized.matches("\\d{" + digits + "}")) return false;

        long counter = System.currentTimeMillis() / 1000L / Math.max(1, periodSeconds);
        for (int offset = -Math.max(0, window); offset <= Math.max(0, window); offset++) {
            if (normalized.equals(code(secret, counter + offset, digits))) return true;
        }

        return false;
    }

    @NotNull
    String uri(@NotNull String issuer, @NotNull String account, @NotNull String secret, int digits, int periodSeconds) {
        return "otpauth://totp/" + encode(issuer + ":" + account) +
                "?secret=" + secret +
                "&issuer=" + encode(issuer) +
                "&algorithm=SHA1" +
                "&digits=" + digits +
                "&period=" + periodSeconds;
    }

    @NotNull
    private String code(@NotNull String secret, long counter, int digits) {
        try {
            byte[] key = decodeBase32(secret);
            byte[] data = new byte[8];
            long value = counter;
            for (int index = 7; index >= 0; index--) {
                data[index] = (byte) (value & 0xff);
                value >>= 8;
            }

            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);

            int modulo = 1;
            for (int index = 0; index < digits; index++) modulo *= 10;

            String result = String.valueOf(binary % modulo);
            while (result.length() < digits) result = "0" + result;
            return result;
        } catch (Exception exception) {
            return "";
        }
    }

    @NotNull
    private String encodeBase32(byte @NotNull [] bytes) {
        StringBuilder builder = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;

        for (byte current : bytes) {
            buffer = (buffer << 8) | (current & 0xff);
            bits += 8;

            while (bits >= 5) {
                builder.append(BASE32[(buffer >> (bits - 5)) & 31]);
                bits -= 5;
            }
        }

        if (bits > 0) builder.append(BASE32[(buffer << (5 - bits)) & 31]);
        return builder.toString();
    }

    private byte[] decodeBase32(@NotNull String value) {
        String normalized = value.replace("=", "").replace(" ", "").trim().toUpperCase(Locale.ROOT);
        byte[] result = new byte[normalized.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int index = 0;

        for (int i = 0; i < normalized.length(); i++) {
            int digit = digit(normalized.charAt(i));
            if (digit < 0) continue;

            buffer = (buffer << 5) | digit;
            bits += 5;
            if (bits >= 8) {
                result[index++] = (byte) ((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }

        if (index == result.length) return result;

        byte[] resized = new byte[index];
        System.arraycopy(result, 0, resized, 0, index);
        return resized;
    }

    private int digit(char value) {
        if (value >= 'A' && value <= 'Z') return value - 'A';
        if (value >= '2' && value <= '7') return 26 + value - '2';
        return -1;
    }

    @NotNull
    private String encode(@NotNull String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (Exception ignored) {
            return value;
        }
    }
}
