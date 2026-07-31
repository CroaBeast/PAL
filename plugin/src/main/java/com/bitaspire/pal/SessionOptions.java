package com.bitaspire.pal;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.croabeast.file.ConfigurableFile;
import org.jetbrains.annotations.NotNull;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
final class SessionOptions {

    private final boolean enabled;
    private final int minutes;
    private final boolean bindIp;
    private final boolean bindName;

    @NotNull
    static SessionOptions load(@NotNull PALPlugin plugin) {
        try {
            ConfigurableFile file = new ConfigurableFile(plugin, "security");
            file.saveDefaults();

            return new SessionOptions(
                    bool(file, "auth.sessions.enabled", true),
                    integer(file, "auth.sessions.duration-minutes", 30),
                    bool(file, "auth.sessions.bind-ip", true),
                    bool(file, "auth.sessions.bind-username", true)
            );
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not load session options: " + exception.getMessage());
            return defaults();
        }
    }

    @NotNull
    static SessionOptions defaults() {
        return new SessionOptions(true, 30, true, true);
    }

    private static boolean bool(ConfigurableFile file, String path, boolean defaultValue) {
        Object value = file.get(path, defaultValue);
        return value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(String.valueOf(value));
    }

    private static int integer(ConfigurableFile file, String path, int defaultValue) {
        Object value = file.get(path, defaultValue);
        if (value instanceof Number) return Math.max(0, ((Number) value).intValue());

        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
