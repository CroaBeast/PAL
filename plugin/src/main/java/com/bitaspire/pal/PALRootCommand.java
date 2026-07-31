package com.bitaspire.pal;

import me.croabeast.command.Synchronizer;
import me.croabeast.command.TabBuilder;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

final class PALRootCommand extends PALCommand {

    PALRootCommand(@NotNull PALPlugin plugin, @NotNull PALCommandOptions options, @NotNull Synchronizer synchronizer) {
        super(plugin, options, synchronizer);
        registerSubCommands();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        return args.length == 0 ? sendInfo(sender) : sendHelp(sender);
    }

    @Override
    public TabBuilder getCompletionBuilder() {
        TabBuilder builder = new TabBuilder();

        for (PALSubCommandOptions subCommand : getOptions().getSubCommands().values()) {
            if (!subCommand.isEnabled()) continue;

            builder.addArgument(0, (sender, args) -> hasSubPermission(sender, subCommand), subCommand.getKey());
        }

        return builder;
    }

    boolean sendInfo(CommandSender sender) {
        return send(sender, "root.info", placeholders(
                "version", plugin.getDescription().getVersion(),
                "platform", plugin.getPlatform()
        ));
    }

    boolean sendHelp(CommandSender sender) {
        for (PALSubCommandOptions subCommand : getOptions().getSubCommands().values()) {
            if (!subCommand.isEnabled() || !hasSubPermission(sender, subCommand)) continue;

            send(sender, "root.help-entry", placeholders(
                    "command", subCommand.getKey(),
                    "description", subCommand.getDescription()
            ));
        }

        return true;
    }

    boolean hasSubPermission(CommandSender sender, PALSubCommandOptions subCommand) {
        String permission = subCommand.getPermission();
        if (permission == null || permission.trim().isEmpty()) return true;
        if (sender.hasPermission(permission) || sender.hasPermission(getPermission()) || sender.hasPermission(getPermission(true))) return true;

        boolean permissionSet = sender.isPermissionSet(permission);
        if (!permissionSet && subCommand.isDefaultPermitted(sender.isOp())) return true;
        return !permissionSet && sender.isOp() && !plugin.getConfiguration().isOverrideOp();
    }

    private void registerSubCommands() {
        String[] consoleArguments = getOptions().getSubCommands().values().stream()
                .filter(PALSubCommandOptions::isEnabled)
                .map(PALSubCommandOptions::getKey)
                .toArray(String[]::new);

        getSubCommandMap().setConsoleArguments(consoleArguments);

        for (PALSubCommandOptions subCommand : getOptions().getSubCommands().values()) {
            if (!subCommand.isEnabled()) continue;
            getSubCommandMap().add(new PALAdminSubCommand(this, subCommand));
        }
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
