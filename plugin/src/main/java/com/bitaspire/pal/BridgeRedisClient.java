package com.bitaspire.pal;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

final class BridgeRedisClient {

    private final BridgeRedisSettings settings;
    private final int timeoutMillis = 2000;

    BridgeRedisClient(@NotNull String uri) {
        this.settings = BridgeRedisSettings.from(uri);
    }

    String get(@NotNull String key) throws IOException {
        try (Connection connection = open()) {
            connection.write(commandBytes("GET", key));
            Object result = connection.read();
            return result instanceof String ? (String) result : null;
        }
    }

    void set(@NotNull String key, @NotNull String value, long ttlMillis) throws IOException {
        if (ttlMillis > 0L) command("SET", key, value, "PX", String.valueOf(ttlMillis));
        else command("SET", key, value);
    }

    void del(@NotNull String key) throws IOException {
        command("DEL", key);
    }

    void publish(@NotNull String channel, @NotNull String message) throws IOException {
        command("PUBLISH", channel, message);
    }

    private void command(@NotNull String... parts) throws IOException {
        try (Connection connection = open()) {
            connection.write(commandBytes(parts));
            connection.read();
        }
    }

    @NotNull
    private Connection open() throws IOException {
        Connection connection = new Connection(settings.getHost(), settings.getPort(), timeoutMillis);

        if (settings.getPassword() != null && !settings.getPassword().trim().isEmpty()) {
            connection.write(commandBytes("AUTH", settings.getPassword()));
            connection.read();
        }

        if (settings.getDatabase() > 0) {
            connection.write(commandBytes("SELECT", String.valueOf(settings.getDatabase())));
            connection.read();
        }

        return connection;
    }

    @NotNull
    private byte[] commandBytes(@NotNull String... parts) {
        StringBuilder builder = new StringBuilder();
        builder.append('*').append(parts.length).append("\r\n");

        for (String part : parts) {
            byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
            builder.append('$').append(bytes.length).append("\r\n").append(part).append("\r\n");
        }

        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static final class Connection implements Closeable {

        private final Socket socket;
        private final BufferedInputStream input;
        private final BufferedOutputStream output;

        private Connection(@NotNull String host, int port, int timeoutMillis) throws IOException {
            socket = new Socket(host, port);
            socket.setSoTimeout(timeoutMillis);
            input = new BufferedInputStream(socket.getInputStream());
            output = new BufferedOutputStream(socket.getOutputStream());
        }

        private void write(byte[] bytes) throws IOException {
            output.write(bytes);
            output.flush();
        }

        private Object read() throws IOException {
            int type = input.read();
            if (type < 0) return null;
            if (type == '-') throw new IOException(line());
            if (type == '$') {
                int length = Integer.parseInt(line());
                if (length < 0) return null;

                byte[] bytes = new byte[length];
                int offset = 0;
                while (offset < length) {
                    int read = input.read(bytes, offset, length - offset);
                    if (read < 0) throw new IOException("Unexpected Redis EOF");
                    offset += read;
                }

                input.read();
                input.read();
                return new String(bytes, StandardCharsets.UTF_8);
            }
            return line();
        }

        @NotNull
        private String line() throws IOException {
            StringBuilder builder = new StringBuilder();
            int previous = -1;
            int current;

            while ((current = input.read()) >= 0) {
                if (previous == '\r' && current == '\n') {
                    builder.setLength(builder.length() - 1);
                    break;
                }

                builder.append((char) current);
                previous = current;
            }

            return builder.toString();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
