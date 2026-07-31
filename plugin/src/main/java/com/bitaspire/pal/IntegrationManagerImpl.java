package com.bitaspire.pal;

import com.bitaspire.pal.integration.Integration;
import com.bitaspire.pal.integration.IntegrationManager;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import me.croabeast.file.ConfigurableFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

final class IntegrationManagerImpl extends AbstractService implements IntegrationManager {

    private final Map<Integration.Type, Integration> integrations = new EnumMap<>(Integration.Type.class);
    private final Collection<IntegrationHook> hooks = new ArrayList<>();

    IntegrationManagerImpl(@NotNull PALApi api) {
        super(api);
    }

    @Override
    public boolean register() {
        super.register();
        integrations.clear();
        unregisterHooks();

        for (Integration.Type integration : Integration.Type.values()) {
            boolean configured = isConfigured(integration);
            boolean enabled = configured && Bukkit.getPluginManager().isPluginEnabled(integration.getId());
            integrations.put(integration, new SimpleIntegration(
                    integration,
                    !configured ? Integration.State.DISABLED : enabled ? Integration.State.ENABLED : Integration.State.MISSING
            ));
        }

        registerHooks();
        return true;
    }

    @Override
    public boolean unregister() {
        unregisterHooks();
        integrations.clear();
        return super.unregister();
    }

    @NotNull
    @Override
    public Collection<Integration> getIntegrations() {
        return Collections.unmodifiableList(new ArrayList<>(integrations.values()));
    }

    @NotNull
    @Override
    public Optional<Integration> getIntegration(@NotNull Integration.Type integration) {
        return Optional.ofNullable(integrations.get(integration));
    }

    @Override
    public boolean isEnabled(@NotNull Integration.Type integration) {
        return getIntegration(integration).map(Integration::isEnabled).orElse(false);
    }

    private void registerHooks() {
        PALPlugin plugin = plugin();
        registerPlaceholderApi(plugin);
        registerLuckPerms(plugin);
    }

    private void registerPlaceholderApi(@NotNull PALPlugin plugin) {
        if (!isEnabled(Integration.Type.PLACEHOLDER_API) || !option("integrations.placeholderapi.register-expansion", true)) return;

        try {
            registerHook(new PlaceholderApiExpansionHook(plugin));
        } catch (NoClassDefFoundError error) {
            failed(Integration.Type.PLACEHOLDER_API, "PlaceholderAPI API is not available: " + error.getMessage());
        } catch (Exception exception) {
            failed(Integration.Type.PLACEHOLDER_API, "Could not register PlaceholderAPI expansion: " + exception.getMessage());
        }
    }

    private void registerLuckPerms(@NotNull PALPlugin plugin) {
        if (!isEnabled(Integration.Type.LUCK_PERMS) || !option("integrations.luckperms.check-contexts", true)) return;

        try {
            registerHook(new LuckPermsContextHook(plugin));
        } catch (NoClassDefFoundError error) {
            failed(Integration.Type.LUCK_PERMS, "LuckPerms API is not available: " + error.getMessage());
        } catch (Exception exception) {
            failed(Integration.Type.LUCK_PERMS, "Could not register LuckPerms contexts: " + exception.getMessage());
        }
    }

    private void registerHook(@NotNull IntegrationHook hook) {
        if (hook.register()) {
            hooks.add(hook);
            plugin().getLogger().info("PAL registered " + hook.name() + ".");
        }
    }

    private void unregisterHooks() {
        for (IntegrationHook hook : new ArrayList<>(hooks)) {
            try {
                hook.unregister();
            } catch (Exception exception) {
                plugin().getLogger().warning("Could not unregister " + hook.name() + ": " + exception.getMessage());
            }
        }

        hooks.clear();
    }

    private void failed(@NotNull Integration.Type integration, @NotNull String message) {
        integrations.put(integration, new SimpleIntegration(integration, Integration.State.FAILED));
        plugin().getLogger().warning(message);
    }

    private boolean isConfigured(@NotNull Integration.Type integration) {
        return option(path(integration) + ".enabled", true);
    }

    private boolean option(@NotNull String path, boolean fallback) {
        try {
            ConfigurableFile file = new ConfigurableFile(plugin(), "integrations");
            file.saveDefaults();
            Object value = file.get(path, fallback);
            return value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(String.valueOf(value));
        } catch (Exception exception) {
            plugin().getLogger().warning("Could not read integrations.yml: " + exception.getMessage());
            return fallback;
        }
    }

    @NotNull
    private String path(@NotNull Integration.Type integration) {
        switch (integration) {
            case PLACEHOLDER_API:
                return "integrations.placeholderapi";
            case LUCK_PERMS:
                return "integrations.luckperms";
            case FAST_LOGIN:
                return "integrations.fastlogin";
            case FLOODGATE:
                return "integrations.floodgate";
            case GEYSER:
                return "integrations.geyser";
            case AUTH_ME:
                return "integrations.importers.authme";
            case N_LOGIN:
                return "integrations.importers.nlogin";
            case OPEN_LOGIN:
                return "integrations.importers.openlogin";
            case LIBRE_LOGIN:
                return "integrations.importers.librelogin";
            default:
                return "integrations." + integration.name().toLowerCase();
        }
    }

    @NotNull
    private PALPlugin plugin() {
        return (PALPlugin) api.getPlugin();
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class SimpleIntegration implements Integration {

        private final Type type;
        private final State state;

        @Override
        public boolean isEnabled() {
            return state == State.ENABLED;
        }
    }
}
