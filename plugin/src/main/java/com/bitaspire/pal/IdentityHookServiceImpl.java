package com.bitaspire.pal;

import com.bitaspire.pal.identity.IdentityRequest;
import com.bitaspire.pal.identity.IdentityResult;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class IdentityHookServiceImpl extends AbstractService implements Listener {

    private final Map<String, CachedIdentity> fastLoginDecisions = new ConcurrentHashMap<>();

    private Listener fastLoginHook;

    IdentityHookServiceImpl(@NotNull PALApi api) {
        super(api);
    }

    @Override
    public boolean register() {
        super.register();
        reload();
        return true;
    }

    void reload() {
        fastLoginDecisions.clear();
        if (!isRegistered()) return;

        HandlerList.unregisterAll(this);
        unregisterFastLoginHook();
        Bukkit.getPluginManager().registerEvents(this, plugin());
        registerFastLoginHook();
    }

    @Override
    public boolean unregister() {
        HandlerList.unregisterAll(this);
        unregisterFastLoginHook();
        fastLoginDecisions.clear();
        return super.unregister();
    }

    @NotNull
    Optional<IdentityResult> findFastLoginDecision(@NotNull IdentityRequest request) {
        CachedIdentity identity = fastLoginDecisions.get(key(request.getName()));
        if (identity == null || identity.isExpired()) {
            if (identity != null) fastLoginDecisions.remove(key(request.getName()));
            return Optional.empty();
        }

        return Optional.of(identity.getResult());
    }

    @NotNull
    Optional<IdentityResult> findFloodgateIdentity(@NotNull IdentityRequest request) {
        if (request.getUniqueId() == null || !isPluginEnabled("floodgate")) return Optional.empty();

        try {
            return FloodgateIdentityHook.find(request);
        } catch (NoClassDefFoundError ignored) {
            return Optional.empty();
        } catch (Exception exception) {
            plugin().getLogger().warning("Could not query Floodgate API: " + rootMessage(exception));
            return Optional.empty();
        }
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent event) {
        fastLoginDecisions.remove(key(event.getPlayer().getName()));
    }

    void cacheFastLogin(@NotNull String name, @NotNull IdentityResult result) {
        long expiresAt = System.currentTimeMillis() + 120_000L;
        CachedIdentity identity = new CachedIdentity(result, expiresAt);
        fastLoginDecisions.put(key(name), identity);
        if (result.getName() != null) fastLoginDecisions.put(key(result.getName()), identity);
    }

    @NotNull
    PALPlugin plugin() {
        return (PALPlugin) api.getPlugin();
    }

    @NotNull
    String rootMessage(@NotNull Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage();
    }

    private void registerFastLoginHook() {
        if (!isPluginEnabled("FastLogin") && !isPluginEnabled("fastlogin")) return;

        try {
            fastLoginHook = new FastLoginBukkitHook(this);
            Bukkit.getPluginManager().registerEvents(fastLoginHook, plugin());
        } catch (NoClassDefFoundError error) {
            fastLoginHook = null;
            plugin().getLogger().warning("FastLogin is installed, but PAL could not load its Bukkit API: " + error.getMessage());
        } catch (Exception exception) {
            fastLoginHook = null;
            plugin().getLogger().warning("Could not hook FastLogin: " + rootMessage(exception));
        }
    }

    private void unregisterFastLoginHook() {
        if (fastLoginHook == null) return;
        HandlerList.unregisterAll(fastLoginHook);
        fastLoginHook = null;
    }

    private boolean isPluginEnabled(@NotNull String name) {
        org.bukkit.plugin.Plugin dependency = Bukkit.getPluginManager().getPlugin(name);
        return dependency != null && dependency.isEnabled();
    }

    @NotNull
    private String key(@NotNull String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class CachedIdentity {

        @NotNull
        private final IdentityResult result;

        private final long expiresAt;

        private boolean isExpired() {
            return expiresAt <= System.currentTimeMillis();
        }
    }
}
