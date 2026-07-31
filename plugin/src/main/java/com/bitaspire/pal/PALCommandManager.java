package com.bitaspire.pal;

import lombok.Getter;
import me.croabeast.command.Synchronizer;
import me.croabeast.common.Registrable;
import me.croabeast.scheduler.GlobalScheduler;
import me.croabeast.scheduler.GlobalTask;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PALCommandManager implements Registrable {

    private static final List<ReservedDefinition> RESERVED = Arrays.asList(
            new ReservedDefinition("login", "the login flow", PALAuthCommand.Action.LOGIN),
            new ReservedDefinition("register", "the register flow", PALAuthCommand.Action.REGISTER),
            new ReservedDefinition("changepassword", "password changes", PALAuthCommand.Action.CHANGE_PASSWORD),
            new ReservedDefinition("logout", "session logout", PALAuthCommand.Action.LOGOUT),
            new ReservedDefinition("unregister", "account deletion", PALAuthCommand.Action.UNREGISTER),
            new ReservedDefinition("premium", "premium account switching", PALAuthCommand.Action.PREMIUM),
            new ReservedDefinition("cracked", "offline account switching", PALAuthCommand.Action.CRACKED),
            new ReservedDefinition("two-factor", "two-factor authentication", PALAuthCommand.Action.TWO_FACTOR)
    );

    private final PALPlugin plugin;
    private final Map<String, PALCommand> commands = new LinkedHashMap<>();

    @Getter
    private final Synchronizer synchronizer;

    private boolean registered = false;

    PALCommandManager(@NotNull PALPlugin plugin) {
        this.plugin = plugin;
        this.synchronizer = createSynchronizer(plugin);
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public boolean register() {
        if (registered) return false;

        YamlConfiguration configuration = loadConfiguration();
        ConfigurationSection section = configuration.getConfigurationSection("commands");
        if (section == null) {
            plugin.getLogger().warning("commands.yml does not contain a 'commands' section.");
            return false;
        }

        registerRoot(section);
        registerReserved(section);

        registered = true;
        synchronizer.sync();
        return true;
    }

    @Override
    public boolean unregister() {
        if (!registered) return false;

        unregisterLoaded(false);
        registered = false;
        synchronizer.sync();
        return true;
    }

    void reload() {
        unregisterLoaded(false);
        registered = false;
        register();
    }

    private void registerRoot(ConfigurationSection section) {
        PALCommandOptions options = readOptions(section, "pal");
        if (options == null) return;

        registerCommand(new PALRootCommand(plugin, options, synchronizer), false);
    }

    private void registerReserved(ConfigurationSection section) {
        for (ReservedDefinition definition : RESERVED) {
            PALCommandOptions options = readOptions(section, definition.key);
            if (options == null) continue;

            PALCommand command = definition.action == null
                    ? new PALReservedCommand(plugin, options, synchronizer, definition.feature)
                    : new PALAuthCommand(plugin, options, synchronizer, definition.action);

            registerCommand(command, false);
        }
    }

    private PALCommandOptions readOptions(ConfigurationSection root, String key) {
        ConfigurationSection section = root.getConfigurationSection(key);
        if (section != null) return PALCommandOptions.from(key, section);

        plugin.getLogger().warning("Command '" + key + "' is missing in commands.yml, skipping.");
        return null;
    }

    private void registerCommand(PALCommand command, boolean sync) {
        if (!command.isEnabled()) {
            plugin.getLogger().info("Command '" + command.getName() + "' is disabled, skipping registration.");
            return;
        }

        if (command.register(sync)) {
            commands.put(command.getOptions().getKey(), command);
            return;
        }

        plugin.getLogger().warning("Could not register command '" + command.getName() + "'.");
    }

    private void unregisterLoaded(boolean sync) {
        for (PALCommand command : new ArrayList<>(commands.values())) {
            try {
                command.unregister(sync);
            } catch (Exception exception) {
                plugin.getLogger().warning("Could not unregister command '" + command.getName() + "': " + exception.getMessage());
            }
        }

        commands.clear();
    }

    private YamlConfiguration loadConfiguration() {
        File file = new File(plugin.getDataFolder(), "commands.yml");
        if (!file.exists()) {
            plugin.saveResource("commands.yml", false);
        }

        return YamlConfiguration.loadConfiguration(file);
    }

    private static Synchronizer createSynchronizer(PALPlugin plugin) {
        return new Synchronizer() {

            private GlobalTask task = null;

            private void cancel(boolean clearTask) {
                if (task == null) return;

                task.cancel();
                if (clearTask) task = null;
            }

            @Override
            public void sync() {
                if (!plugin.isEnabled()) {
                    cancel(true);
                    return;
                }

                GlobalScheduler scheduler = plugin.getScheduler();
                scheduler.runTask(() -> {
                    cancel(false);

                    task = scheduler.runTaskLater(() -> {
                        task = null;
                        Synchronizer.syncCommands();
                    }, 1L);
                });
            }

            @Override
            public void cancel() {
                cancel(true);
            }
        };
    }

    private static final class ReservedDefinition {

        private final String key;
        private final String feature;
        private final PALAuthCommand.Action action;

        private ReservedDefinition(String key, String feature) {
            this(key, feature, null);
        }

        private ReservedDefinition(String key, String feature, PALAuthCommand.Action action) {
            this.key = key;
            this.feature = feature;
            this.action = action;
        }
    }
}
