package com.bitaspire.pal;

import lombok.AccessLevel;
import lombok.Getter;
import me.croabeast.command.BukkitCommand;
import me.croabeast.command.Synchronizer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

abstract class PALCommand extends BukkitCommand {

    protected final PALPlugin plugin;

    @Getter(AccessLevel.PACKAGE)
    private final PALCommandOptions options;

    PALCommand(@NotNull PALPlugin plugin, @NotNull PALCommandOptions options, @NotNull Synchronizer synchronizer) {
        super(plugin, options.getName(), options.getPermission());
        this.plugin = plugin;
        this.options = options;

        setSynchronizer(synchronizer);
        setDescription(options.getDescription());
        setUsage(options.getUsage());
        setAliases(options.getAliases());
        setPermissionMessage(format(options.getPermissionMessage()));

        setExecuteCheck((sender, throwable) -> {
            plugin.getLogger().warning("Could not execute command '" + getName() + "': " + throwable.getMessage());
            return send(sender, "general.error");
        });

        setCompleteCheck((sender, throwable) -> {
            plugin.getLogger().warning("Could not complete command '" + getName() + "': " + throwable.getMessage());
            return true;
        });

        setArgumentCheck((sender, argument) -> send(sender, "general.unknown-argument", Collections.singletonMap("argument", argument)));
    }

    @Override
    public final boolean isEnabled() {
        return options.isEnabled();
    }

    @Override
    public final boolean isOverriding() {
        return options.isOverrideExisting();
    }

    @Override
    public boolean isPermitted(CommandSender sender, boolean log) {
        String permission = getPermission();
        if (permission == null || permission.trim().isEmpty()) return true;
        if (sender.hasPermission(permission)) return true;

        boolean permissionSet = sender.isPermissionSet(permission);
        if (!permissionSet && options.isDefaultPermitted(sender.isOp())) return true;
        if (!permissionSet && sender.isOp() && !plugin.getConfiguration().isOverrideOp()) return true;

        return !log || send(sender, options.getPermissionMessage());
    }

    @NotNull
    @Override
    public Supplier<Collection<String>> generateCompletions(CommandSender sender, String[] arguments) {
        return Collections::emptyList;
    }

    protected final boolean send(CommandSender sender, String message) {
        sender.sendMessage(format(message));
        return true;
    }

    protected final boolean send(CommandSender sender, String message, Map<String, ?> placeholders) {
        sender.sendMessage(plugin.getMessageService().get(message, placeholders));
        return true;
    }

    @NotNull
    @Override
    public final PALPlugin getPlugin() {
        return plugin;
    }

    private String format(String message) {
        return plugin.getMessageService().get(message);
    }
}
