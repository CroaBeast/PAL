package com.bitaspire.pal.protocol.bukkit;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.bukkit.Server;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The three login packets the encryption handshake needs, found by reflection so one jar
 * covers Spigot-mapped servers and Mojang-mapped Paper alike.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class LoginPackets {

    private static final String LOGIN = "net.minecraft.network.protocol.login.";

    private final Class<?> hello;
    private final Class<?> key;
    private final Constructor<?> request;
    private final Field name;
    private final Field[] keyFields;
    private final Field signedChallenge;

    @NotNull
    static LoginPackets find(@NotNull Server server) throws ReflectiveOperationException {
        ClassLoader loader = server.getClass().getClassLoader();
        String[] parts = server.getClass().getPackage().getName().split("\\.");
        String legacy = parts.length > 3 ? "net.minecraft.server." + parts[3] + "." : LOGIN;

        Class<?> hello = type(loader, LOGIN + "ServerboundHelloPacket", LOGIN + "PacketLoginInStart", legacy + "PacketLoginInStart");
        Class<?> key = type(loader, LOGIN + "ServerboundKeyPacket", LOGIN + "PacketLoginInEncryptionBegin", legacy + "PacketLoginInEncryptionBegin");
        Class<?> request = type(loader, LOGIN + "ClientboundHelloPacket", LOGIN + "PacketLoginOutEncryptionBegin", legacy + "PacketLoginOutEncryptionBegin");

        Field[] keyFields = keyFields(key);
        Field signedChallenge = keyFields.length == 1 ? eitherField(key) : null;
        return new LoginPackets(hello, key, constructor(request), nameField(hello), keyFields, signedChallenge);
    }

    boolean isHello(@NotNull Object packet) {
        return hello.isInstance(packet);
    }

    boolean isKey(@NotNull Object packet) {
        return key.isInstance(packet);
    }

    @NotNull
    String name(@NotNull Object hello) throws ReflectiveOperationException {
        Object value = name.get(hello);
        if (value instanceof String) return (String) value;

        // 1.16.5 carries a GameProfile instead of the bare name.
        try {
            return (String) value.getClass().getMethod("getName").invoke(value);
        } catch (NoSuchMethodException ignored) {
            return (String) value.getClass().getMethod("name").invoke(value);
        }
    }

    @NotNull
    Object request(@NotNull PublicKey publicKey, @NotNull byte[] challenge) throws ReflectiveOperationException {
        Class<?>[] types = request.getParameterTypes();
        Object encoded = types[1] == PublicKey.class ? publicKey : publicKey.getEncoded();

        return types.length == 4
                ? request.newInstance("", encoded, challenge, true)
                : request.newInstance("", encoded, challenge);
    }

    /**
     * The encrypted shared secret and the encrypted challenge, in no promised order. Only the
     * secret when a 1.19 to 1.19.2 client signed the challenge instead of encrypting it.
     */
    @NotNull
    byte[][] keyBytes(@NotNull Object key) throws ReflectiveOperationException {
        byte[] first = (byte[]) keyFields[0].get(key);
        if (signedChallenge == null) return new byte[][] {first, (byte[]) keyFields[1].get(key)};

        Object either = signedChallenge.get(key);
        Optional<?> encrypted = (Optional<?>) signedChallenge.getType().getMethod("left").invoke(either);
        return encrypted.isPresent() ? new byte[][] {first, (byte[]) encrypted.get()} : new byte[][] {first};
    }

    @NotNull
    private static Class<?> type(@NotNull ClassLoader loader, @NotNull String... names) throws ClassNotFoundException {
        for (String name : names) {
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new ClassNotFoundException(names[0]);
    }

    @NotNull
    private static Constructor<?> constructor(@NotNull Class<?> type) throws NoSuchMethodException {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            boolean base = types.length >= 3
                    && types[0] == String.class
                    && (types[1] == byte[].class || types[1] == PublicKey.class)
                    && types[2] == byte[].class;

            if (base && (types.length == 3 || types.length == 4 && types[3] == boolean.class)) {
                constructor.setAccessible(true);
                return constructor;
            }
        }
        throw new NoSuchMethodException(type.getName() + " has no encryption request constructor");
    }

    @NotNull
    private static Field nameField(@NotNull Class<?> type) throws NoSuchFieldException {
        Field profile = null;
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (field.getType() == String.class) return accessible(field);
            if (field.getType().getSimpleName().equals("GameProfile")) profile = field;
        }

        if (profile == null) throw new NoSuchFieldException(type.getName() + " has no name field");
        return accessible(profile);
    }

    @NotNull
    private static Field[] keyFields(@NotNull Class<?> type) throws NoSuchFieldException {
        List<Field> fields = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == byte[].class) fields.add(accessible(field));
        }

        // 1.19 to 1.19.2 keep the challenge in an Either next to the secret.
        if (fields.isEmpty() || fields.size() > 2) throw new NoSuchFieldException(type.getName() + " has no key fields");
        return fields.toArray(new Field[0]);
    }

    @NotNull
    private static Field eitherField(@NotNull Class<?> type) throws NoSuchFieldException {
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType().getSimpleName().equals("Either")) return accessible(field);
        }
        throw new NoSuchFieldException(type.getName() + " has no challenge field");
    }

    @NotNull
    private static Field accessible(@NotNull Field field) {
        field.setAccessible(true);
        return field;
    }
}
