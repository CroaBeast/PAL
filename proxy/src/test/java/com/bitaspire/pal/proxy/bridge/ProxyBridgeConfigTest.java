package com.bitaspire.pal.proxy.bridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyBridgeConfigTest {

    @TempDir
    private Path directory;

    @Test
    void minimalEnabledConfigDefaultsToDatabaseWithProxyAutoLogin() throws Exception {
        Files.write(directory.resolve("bridge.yml"), (
                "bridge:\n" +
                        "  enabled: true\n"
        ).getBytes(StandardCharsets.UTF_8));

        ProxyBridgeConfig config = ProxyBridgeConfig.load(directory.toFile(), 1);

        assertEquals(ProxyBridgeConfig.Mode.DATABASE, config.getMode());
        assertTrue(config.isProxyAutoLogin());
    }

    @Test
    void invalidBridgeModeFallsBackToDatabase() {
        assertEquals(ProxyBridgeConfig.Mode.DATABASE, ProxyBridgeConfig.Mode.from("bad-mode"));
    }
}
