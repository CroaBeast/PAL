package com.bitaspire.pal;

import com.bitaspire.pal.protocol.bukkit.BukkitAdapter;
import com.bitaspire.pal.protocol.bukkit.BukkitAdapters;
import com.bitaspire.pal.protocol.bukkit.BukkitOptions;
import me.croabeast.file.ConfigurableFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

final class ProtocolServiceImpl extends AbstractService {

    private BukkitAdapter adapter;
    private BukkitOptions options = BukkitOptions.defaults();

    ProtocolServiceImpl(@NotNull PALApi api) {
        super(api);
    }

    @Override
    public boolean register() {
        super.register();
        reload();
        return true;
    }

    void reload() {
        if (adapter != null) adapter.stop();
        options = load(plugin());
        adapter = BukkitAdapters.create(plugin(), options);
        adapter.start();
        warnIfUnsupported();
    }

    @Override
    public boolean unregister() {
        if (adapter != null) adapter.stop();
        adapter = null;
        return super.unregister();
    }

    boolean supportsLoginPhase() {
        return adapter != null && adapter.supportsLoginPhase();
    }

    private void warnIfUnsupported() {
        if (!options.isEnabled() || supportsLoginPhase()) return;
        if (plugin().getServer().getOnlineMode()) return;

        plugin().getLogger().warning("PAL Protocol Bukkit cannot prove premium ownership on standalone offline-mode Bukkit.");
        plugin().getLogger().warning("Use proxy PAL Native, FastLogin, or online-mode for automatic premium login proof.");
    }

    @NotNull
    private static BukkitOptions load(@NotNull PALPlugin plugin) {
        try {
            ConfigurableFile file = new ConfigurableFile(plugin, "premium");
            file.saveDefaults();

            return BukkitOptions.builder()
                    .enabled(bool(file, "premium.protocol.enabled", true))
                    .requireLoginPhase(bool(file, "premium.protocol.phase-required", true))
                    .timeoutMillis(integer(file, "premium.protocol.timeout-ms", 5000))
                    .mode(mode(string(file, "premium.protocol.mode", "AUTO")))
                    .build();
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not load premium protocol options: " + exception.getMessage());
            return BukkitOptions.defaults();
        }
    }

    @NotNull
    private static BukkitOptions.Mode mode(@NotNull String value) {
        try {
            return BukkitOptions.Mode.valueOf(value.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return BukkitOptions.Mode.AUTO;
        }
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

    private static String string(ConfigurableFile file, String path, String defaultValue) {
        Object value = file.get(path, defaultValue);
        return value == null ? defaultValue : String.valueOf(value);
    }

    @NotNull
    private PALPlugin plugin() {
        return (PALPlugin) api.getPlugin();
    }
}
