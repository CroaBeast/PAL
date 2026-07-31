package com.bitaspire.pal;


import lombok.Getter;
import me.croabeast.file.ConfigurableFile;

@Getter
final class PALConfig implements Configuration {

    private boolean updateCheckEnabled = true;
    private boolean coloredConsole = true;
    private boolean showPrefix = true;
    private boolean overrideOp = false;

    private String prefixKey = "<P>";
    private String prefix = " &b&lPAL &8>&7";
    private String centerPrefix = "<C>";
    private String lineSeparator = "<n>";

    PALConfig(PALPlugin plugin) {
        try {
            ConfigurableFile file = new ConfigurableFile(plugin, "config");
            file.saveDefaults();

            updateCheckEnabled = file.get("options.update-check", true);
            coloredConsole = file.get("options.console.colored", true);
            showPrefix = file.get("options.console.show-message-type", true);
            overrideOp = file.get("options.permissions.override-op", false);

            prefixKey = file.get("messages.prefix-key", prefixKey);
            prefix = file.get("messages.prefix", prefix);
            centerPrefix = file.get("messages.center-prefix", centerPrefix);
            lineSeparator = file.get("messages.line-separator", lineSeparator);
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not load config.yml: " + exception.getMessage());
        }
    }
}
