package com.bitaspire.pal.protocol.mojang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MojangProfileClient {

    private static final String PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String HAS_JOINED_URL = "https://sessionserver.mojang.com/session/minecraft/hasJoined";
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
    private static final Pattern NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    @NotNull
    public CompletionStage<Optional<MojangProfile>> findByName(@NotNull String name, int timeoutMillis) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return lookupProfile(name, timeoutMillis);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @NotNull
    public CompletionStage<Optional<MojangProfile>> hasJoined(
            @NotNull String name,
            @NotNull String serverHash,
            @Nullable InetAddress address,
            int timeoutMillis
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return lookupSession(name, serverHash, address, timeoutMillis);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @NotNull
    private Optional<MojangProfile> lookupProfile(@NotNull String name, int timeoutMillis) throws IOException {
        if (!isMinecraftName(name)) return Optional.empty();

        HttpURLConnection connection = open(
                PROFILE_URL + URLEncoder.encode(name, "UTF-8"),
                timeoutMillis
        );

        int status = connection.getResponseCode();
        if (status == HttpURLConnection.HTTP_NO_CONTENT || status == HttpURLConnection.HTTP_NOT_FOUND) {
            return Optional.empty();
        }

        if (status != HttpURLConnection.HTTP_OK) {
            throw new IOException("Mojang profile lookup returned HTTP " + status);
        }

        return parse(read(connection.getInputStream()));
    }

    @NotNull
    private Optional<MojangProfile> lookupSession(
            @NotNull String name,
            @NotNull String serverHash,
            @Nullable InetAddress address,
            int timeoutMillis
    ) throws IOException {
        if (!isMinecraftName(name) || serverHash.trim().isEmpty()) return Optional.empty();

        StringBuilder url = new StringBuilder(HAS_JOINED_URL)
                .append("?username=").append(URLEncoder.encode(name, "UTF-8"))
                .append("&serverId=").append(URLEncoder.encode(serverHash, "UTF-8"));

        if (address != null) {
            url.append("&ip=").append(URLEncoder.encode(address.getHostAddress(), "UTF-8"));
        }

        HttpURLConnection connection = open(url.toString(), timeoutMillis);
        int status = connection.getResponseCode();
        if (status == HttpURLConnection.HTTP_NO_CONTENT || status == HttpURLConnection.HTTP_NOT_FOUND) {
            return Optional.empty();
        }

        if (status != HttpURLConnection.HTTP_OK) {
            throw new IOException("Mojang session lookup returned HTTP " + status);
        }

        return parse(read(connection.getInputStream()));
    }

    @NotNull
    private static HttpURLConnection open(@NotNull String url, int timeoutMillis) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(timeoutMillis);
        connection.setReadTimeout(timeoutMillis);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "PAL-Protocol/0.1.0");
        return connection;
    }

    @NotNull
    private static Optional<MojangProfile> parse(@NotNull String body) {
        String id = find(ID_PATTERN, body);
        String profileName = find(NAME_PATTERN, body);

        if (id == null || profileName == null) return Optional.empty();
        return Optional.of(new MojangProfile(toUuid(id), profileName));
    }

    public static boolean isMinecraftName(@Nullable String name) {
        return name != null && name.matches("^[A-Za-z0-9_]{3,16}$");
    }

    @NotNull
    private static String read(@NotNull InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = stream.read(buffer)) != -1) output.write(buffer, 0, length);
            return output.toString("UTF-8");
        }
    }

    @Nullable
    private static String find(@NotNull Pattern pattern, @NotNull String body) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    @NotNull
    private static UUID toUuid(@NotNull String raw) {
        String uuid = raw.replaceFirst(
                "([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})",
                "$1-$2-$3-$4-$5"
        );
        return UUID.fromString(uuid);
    }
}
