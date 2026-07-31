package com.bitaspire.pal.protocol.mojang;

import org.jetbrains.annotations.NotNull;

import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

public final class MinecraftServerHash {

    private MinecraftServerHash() {
    }

    @NotNull
    public static String create(@NotNull String serverId, @NotNull SecretKey sharedSecret, @NotNull PublicKey publicKey) {
        return create(serverId.getBytes(StandardCharsets.ISO_8859_1), sharedSecret.getEncoded(), publicKey.getEncoded());
    }

    @NotNull
    public static String create(@NotNull byte[] serverId, @NotNull byte[] sharedSecret, @NotNull byte[] publicKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(serverId);
            digest.update(sharedSecret);
            digest.update(publicKey);
            return new BigInteger(digest.digest()).toString(16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is not available", exception);
        }
    }
}
