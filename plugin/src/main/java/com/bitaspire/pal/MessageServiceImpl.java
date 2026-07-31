package com.bitaspire.pal;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.Map;

final class MessageServiceImpl {

    private final PALPlugin plugin;
    private YamlConfiguration messages;

    MessageServiceImpl(@NotNull PALPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(file);
    }

    boolean send(@NotNull CommandSender sender, @NotNull String key) {
        sender.sendMessage(get(key));
        return true;
    }

    boolean send(@NotNull CommandSender sender, @NotNull String key, @NotNull Map<String, ?> placeholders) {
        sender.sendMessage(get(key, placeholders));
        return true;
    }

    @NotNull
    String get(@NotNull String key) {
        return get(key, Collections.emptyMap());
    }

    @NotNull
    String get(@NotNull String key, @NotNull Map<String, ?> placeholders) {
        return format(resolve(key), placeholders);
    }

    @NotNull
    String raw(@Nullable String message) {
        return format(message == null ? "" : message, Collections.emptyMap());
    }

    @NotNull
    String raw(@Nullable String message, @NotNull Map<String, ?> placeholders) {
        return format(message == null ? "" : message, placeholders);
    }

    @NotNull
    private String resolve(@NotNull String key) {
        if (messages != null && messages.isString(key)) return messages.getString(key, key);
        return key.indexOf(' ') < 0 && key.indexOf('&') < 0 && key.indexOf('<') < 0 ? "<P> &cMissing message: &f" + key : key;
    }

    @NotNull
    private String format(@NotNull String message, @NotNull Map<String, ?> placeholders) {
        String value = message
                .replace(plugin.getConfiguration().getPrefixKey(), plugin.getConfiguration().getPrefix())
                .replace("<n>", "\n");

        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }

        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
