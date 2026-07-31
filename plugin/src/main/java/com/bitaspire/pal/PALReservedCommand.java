package com.bitaspire.pal;

import lombok.AccessLevel;
import lombok.Getter;
import me.croabeast.command.Synchronizer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

final class PALReservedCommand extends PALCommand {

    @Getter(AccessLevel.PACKAGE)
    private final String feature;

    PALReservedCommand(
            @NotNull PALPlugin plugin,
            @NotNull PALCommandOptions options,
            @NotNull Synchronizer synchronizer,
            @NotNull String feature
    ) {
        super(plugin, options, synchronizer);
        this.feature = feature;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        return send(sender, "reserved.command", placeholders(
                "command", getName(),
                "feature", feature
        ));
    }

    @NotNull
    private Map<String, Object> placeholders(@NotNull Object... values) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            placeholders.put(String.valueOf(values[index]), values[index + 1]);
        }
        return placeholders;
    }
}
