package com.bitaspire.pal;

import com.bitaspire.pal.auth.PasswordHash;
import com.bitaspire.pal.auth.PasswordHasher;
import org.mindrot.jbcrypt.BCrypt;
import org.jetbrains.annotations.NotNull;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

final class Pbkdf2PasswordHasher implements PasswordHasher {

    private static final String ALGORITHM = "PBKDF2_SHA256";
    private static final String LEGACY_PREFIX = "LEGACY_";
    private static final String JCA_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int VERSION = 1;
    private static final int ITERATIONS = 120000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    private final SecureRandom random = new SecureRandom();

    @NotNull
    @Override
    public PasswordHash hash(char @NotNull [] password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);

        byte[] derived = derive(password, salt, ITERATIONS, KEY_BITS);
        String encoded = ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" +
                Base64.getEncoder().encodeToString(derived);

        return new StoredPasswordHash(ALGORITHM, encoded, VERSION);
    }

    @Override
    public boolean verify(char @NotNull [] password, @NotNull PasswordHash hash) {
        if (hash.getAlgorithm().toUpperCase(Locale.ROOT).startsWith(LEGACY_PREFIX)) {
            return verifyLegacy(password, hash.getEncoded());
        }

        if (!ALGORITHM.equalsIgnoreCase(hash.getAlgorithm())) return false;

        String[] parts = hash.getEncoded().split("\\$");
        if (parts.length != 3) return false;

        try {
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            byte[] actual = derive(password, salt, iterations, expected.length * 8);

            return MessageDigest.isEqual(expected, actual);
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public boolean needsRehash(@NotNull PasswordHash hash) {
        if (hash.getAlgorithm().toUpperCase(Locale.ROOT).startsWith(LEGACY_PREFIX)) return true;
        if (!ALGORITHM.equalsIgnoreCase(hash.getAlgorithm())) return true;
        if (hash.getVersion() != VERSION) return true;

        String[] parts = hash.getEncoded().split("\\$");
        if (parts.length != 3) return true;

        try {
            return Integer.parseInt(parts[0]) < ITERATIONS;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    private boolean verifyLegacy(char[] password, @NotNull String encoded) {
        String value = encoded.trim();
        String raw = new String(password);

        try {
            if (value.startsWith("$SHA$")) return verifyAuthMe(raw, value);
            if (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$")) {
                return BCrypt.checkpw(raw, normalizeBcrypt(value));
            }

            if (isHex(value, 32)) return value.equalsIgnoreCase(hex("MD5", raw));
            if (isHex(value, 64)) return value.equalsIgnoreCase(hex("SHA-256", raw));
        } catch (Exception ignored) {
            return false;
        }

        return false;
    }

    private boolean verifyAuthMe(@NotNull String password, @NotNull String encoded) throws Exception {
        String[] parts = encoded.split("\\$");
        if (parts.length != 4) return false;

        String salt = parts[2];
        String expected = parts[3];
        String first = hex("SHA-256", password);
        String actual = hex("SHA-256", first + salt);
        return expected.equalsIgnoreCase(actual);
    }

    @NotNull
    private String normalizeBcrypt(@NotNull String value) {
        return value.startsWith("$2y$") ? "$2a$" + value.substring(4) : value;
    }

    private boolean isHex(@NotNull String value, int length) {
        if (value.length() != length) return false;

        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            boolean digit = current >= '0' && current <= '9';
            boolean lower = current >= 'a' && current <= 'f';
            boolean upper = current >= 'A' && current <= 'F';
            if (!digit && !lower && !upper) return false;
        }

        return true;
    }

    @NotNull
    private String hex(@NotNull String algorithm, @NotNull String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(bytes.length * 2);

        for (byte current : bytes) {
            String hex = Integer.toHexString(current & 0xff);
            if (hex.length() == 1) builder.append('0');
            builder.append(hex);
        }

        return builder.toString();
    }

    private byte[] derive(char[] password, byte[] salt, int iterations, int keyBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyBits);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(JCA_ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash password", exception);
        }
    }
}
