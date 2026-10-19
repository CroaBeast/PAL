package com.bitaspire.pal.protocol.bukkit;

import com.bitaspire.pal.protocol.mojang.MinecraftServerHash;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

/**
 * Does on an offline-mode server what vanilla does in online-mode, for the names the
 * adapter picks: holds the hello packet, sends an encryption request, turns encryption on
 * with the client's shared secret and asks Mojang whether that name joined with it.
 *
 * <p>The server never sees any of it. Once the answer is in, the held hello packet goes
 * through and the server runs its normal offline login.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class NativeLoginHandler extends ChannelInboundHandlerAdapter {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final BukkitNativeAdapter adapter;
    private final LoginPackets packets;

    private Object hello;
    private String name;
    private byte[] challenge;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (hello == null && packets.isHello(msg)) {
            onHello(ctx, msg);
            return;
        }

        if (challenge != null && packets.isKey(msg)) {
            onKey(ctx, msg);
            return;
        }

        ctx.fireChannelRead(msg);
    }

    private void onHello(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) {
        hello = msg;
        try {
            name = packets.name(msg);
            if (ctx.pipeline().get("splitter") == null || ctx.pipeline().get("prepender") == null) {
                release(ctx);
                return;
            }

            adapter.shouldChallenge(name).whenComplete((challenged, error) -> ctx.channel().eventLoop().execute(() -> {
                if (error != null || !Boolean.TRUE.equals(challenged)) {
                    release(ctx);
                    return;
                }

                challenge = new byte[4];
                RANDOM.nextBytes(challenge);
                try {
                    ctx.channel().writeAndFlush(packets.request(adapter.getKeyPair().getPublic(), challenge));
                } catch (ReflectiveOperationException exception) {
                    adapter.getPlugin().getLogger().warning("PAL Native login could not send the encryption request: " + exception);
                    challenge = null;
                    release(ctx);
                }
            }));
        } catch (Exception exception) {
            release(ctx);
        }
    }

    private void onKey(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) {
        SecretKey key;
        try {
            byte[] secret = secret(packets.keyBytes(msg));
            if (secret == null) {
                ctx.close();
                return;
            }

            key = new SecretKeySpec(secret, "AES");
            ctx.pipeline().addBefore("splitter", "decrypt", new Decrypt(cipher(Cipher.DECRYPT_MODE, key)));
            ctx.pipeline().addBefore("prepender", "encrypt", new Encrypt(cipher(Cipher.ENCRYPT_MODE, key)));
        } catch (Exception exception) {
            ctx.close();
            return;
        }

        String hash = MinecraftServerHash.create("", key, adapter.getKeyPair().getPublic());
        adapter.getClient().hasJoined(name, hash, null, adapter.getOptions().getTimeoutMillis())
                .whenComplete((profile, error) -> ctx.channel().eventLoop().execute(() -> {
                    // Mojang unreachable: the player gets in unverified and logs in with a password.
                    if (error != null) {
                        release(ctx);
                        return;
                    }

                    if (!profile.isPresent() || !profile.get().getName().equalsIgnoreCase(name)) {
                        ctx.close();
                        return;
                    }

                    adapter.verified(name, profile.get());
                    release(ctx);
                }));
    }

    /**
     * Decrypts both halves of the key packet and tells them apart by the one that matches
     * the challenge, so the field order of the packet does not matter.
     *
     * <p>A 1.19 to 1.19.2 client with a chat key signs the challenge instead, and only the
     * secret arrives. That skips no proof: Mojang still has to confirm a session hashed with
     * this secret and this server's key, which only the account owner can produce.
     */
    @Nullable
    private byte[] secret(@NotNull byte[][] encrypted) throws GeneralSecurityException {
        Cipher rsa = Cipher.getInstance("RSA");
        rsa.init(Cipher.DECRYPT_MODE, adapter.getKeyPair().getPrivate());
        byte[] first = rsa.doFinal(encrypted[0]);
        if (encrypted.length == 1) return first.length == 16 ? first : null;

        byte[] second = rsa.doFinal(encrypted[1]);

        if (Arrays.equals(second, challenge) && first.length == 16) return first;
        if (Arrays.equals(first, challenge) && second.length == 16) return second;
        return null;
    }

    private void release(@NotNull ChannelHandlerContext ctx) {
        if (!ctx.channel().isActive()) return;

        ctx.fireChannelRead(hello);
        if (ctx.pipeline().context(this) != null) ctx.pipeline().remove(this);
    }

    @NotNull
    private static Cipher cipher(int mode, @NotNull SecretKey key) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
        cipher.init(mode, key, new IvParameterSpec(key.getEncoded()));
        return cipher;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Decrypt extends MessageToMessageDecoder<ByteBuf> {

        private final Cipher cipher;

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (!in.isReadable()) return;

            byte[] bytes = new byte[in.readableBytes()];
            in.readBytes(bytes);
            out.add(Unpooled.wrappedBuffer(cipher.update(bytes)));
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Encrypt extends MessageToByteEncoder<ByteBuf> {

        private final Cipher cipher;

        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
            if (!msg.isReadable()) return;

            byte[] bytes = new byte[msg.readableBytes()];
            msg.readBytes(bytes);
            out.writeBytes(cipher.update(bytes));
        }
    }
}
