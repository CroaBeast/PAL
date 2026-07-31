package com.bitaspire.pal;

import com.bitaspire.pal.account.AccountCreateRequest;
import com.bitaspire.pal.account.AccountResult;
import com.bitaspire.pal.account.PALAccount;
import com.bitaspire.pal.auth.AuthSource;
import com.bitaspire.pal.identity.IdentityRequest;
import com.bitaspire.pal.identity.IdentityResult;
import com.bitaspire.pal.identity.IdentityType;
import me.croabeast.scheduler.GlobalTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

final class PreAuthServiceImpl extends AbstractService implements PluginMessageListener {

    private final Map<UUID, PreAuthState> states = new ConcurrentHashMap<>();
    private final Map<UUID, GlobalTask> timeouts = new ConcurrentHashMap<>();
    private final Map<UUID, PreAuthRealm> realmIntents = new ConcurrentHashMap<>();
    private final Set<UUID> realmTeleports = ConcurrentHashMap.newKeySet();
    private final Listener listener = new ListenerImpl();

    private PreAuthOptions options = PreAuthOptions.defaults();

    PreAuthServiceImpl(@NotNull PALApi api) {
        super(api);
    }

    @Override
    public boolean register() {
        super.register();
        reload();
        Bukkit.getPluginManager().registerEvents(listener, plugin());
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin(), PreAuthRoleMessageCodec.CHANNEL, this);
        for (Player player : Bukkit.getOnlinePlayers()) handleJoin(player);
        return true;
    }

    void reload() {
        options = PreAuthOptions.load(plugin());
        if (!isRegistered()) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isAuthenticated(player)) scheduleTimeout(player);
        }
    }

    @Override
    public boolean unregister() {
        HandlerList.unregisterAll(listener);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin(), PreAuthRoleMessageCodec.CHANNEL, this);
        timeouts.values().forEach(GlobalTask::cancel);
        timeouts.clear();
        states.clear();
        realmIntents.clear();
        realmTeleports.clear();
        return super.unregister();
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] payload) {
        if (!PreAuthRoleMessageCodec.CHANNEL.equals(channel) || !options.isRoleMessages()) return;

        Optional<PreAuthRealm> realm = PreAuthRoleMessageCodec.decode(
                player.getUniqueId(),
                payload,
                options.getRoleSecret(),
                options.getRoleSkewSeconds()
        );

        if (!realm.isPresent() || realm.get() == PreAuthRealm.AUTO) return;
        realmIntents.put(player.getUniqueId(), realm.get());
        applyRealm(player, realm.get());
    }

    void markAuthenticated(@NotNull PALAccount account) {
        plugin().getScheduler().runTask(() -> {
            Player player = Bukkit.getPlayerExact(account.getName());
            if (player == null) return;

            PreAuthState state = state(player);
            state.setAccountId(account.getUniqueId());
            state.setLoaded(true);
            state.setRegistered(true);
            state.setAuthenticated(true);
            plugin().getTwoFactorService().clear(player.getUniqueId());
            cancelTimeout(player.getUniqueId());
            if (realmIntents.get(player.getUniqueId()) == PreAuthRealm.AUTH) {
                realmIntents.put(player.getUniqueId(), PreAuthRealm.LOBBY);
                applyRealm(player, PreAuthRealm.LOBBY);
            }
        });
    }

    void markUnauthenticated(@NotNull UUID accountId, @NotNull String name) {
        plugin().getScheduler().runTask(() -> {
            Player player = Bukkit.getPlayerExact(name);
            if (player == null) return;

            PreAuthState state = state(player);
            state.setAccountId(accountId);
            state.setLoaded(true);
            state.setRegistered(true);
            state.setAuthenticated(false);
            plugin().getTwoFactorService().clear(player.getUniqueId());
            scheduleTimeout(player);
            realmIntents.put(player.getUniqueId(), PreAuthRealm.AUTH);
            applyRealm(player, PreAuthRealm.AUTH);
            warn(player, "auth.session-expired");
        });
    }

    void markForceLoggedOut(@NotNull PALAccount account) {
        if (!requiresProxyRevalidation(account)) {
            markUnauthenticated(account.getUniqueId(), account.getName());
            return;
        }

        plugin().getScheduler().runTask(() -> {
            Player player = Bukkit.getPlayerExact(account.getName());
            if (player == null) return;

            states.remove(player.getUniqueId());
            realmIntents.remove(player.getUniqueId());
            realmTeleports.remove(player.getUniqueId());
            cancelTimeout(player.getUniqueId());
            plugin().getTwoFactorService().clear(player.getUniqueId());
            player.kickPlayer(message("auth.reconnect-to-verify"));
        });
    }

    void markUnregistered(@NotNull UUID playerId, @NotNull String name) {
        plugin().getScheduler().runTask(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) return;

            PreAuthState state = state(player);
            state.setAccountId(null);
            state.setLoaded(true);
            state.setRegistered(false);
            state.setAuthenticated(false);
            plugin().getTwoFactorService().clear(playerId);
            scheduleTimeout(player);
            realmIntents.put(playerId, PreAuthRealm.AUTH);
            applyRealm(player, PreAuthRealm.AUTH);
            warn(player, "auth.prompt-register.chat");
        });
    }

    private void handleJoin(@NotNull Player player) {
        if (isDirectBackendBlocked(player)) {
            player.kickPlayer(message("pre-auth.proxy-required"));
            return;
        }

        PreAuthState state = new PreAuthState(
                player.getUniqueId(),
                player.getName(),
                address(player),
                Instant.now(),
                null,
                false,
                false,
                false,
                0L
        );

        states.put(player.getUniqueId(), state);
        realmIntents.put(player.getUniqueId(), initialRealm(player));
        applyRealm(player, realm(player));
        scheduleTimeout(player);
        loadAccount(player, state);

        plugin().getScheduler().runTaskLater(() -> {
            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online != null && !isAuthenticated(online)) prompt(online, true);
        }, 20L);
    }

    private void handleQuit(@NotNull Player player) {
        states.remove(player.getUniqueId());
        realmIntents.remove(player.getUniqueId());
        realmTeleports.remove(player.getUniqueId());
        cancelTimeout(player.getUniqueId());
        plugin().getTwoFactorService().clear(player.getUniqueId());
    }

    private void loadAccount(@NotNull Player player, @NotNull PreAuthState sourceState) {
        api.getAccountService().findByName(player.getName()).whenComplete((account, throwable) ->
                plugin().getScheduler().runTask(() -> {
                    Player online = Bukkit.getPlayer(sourceState.getPlayerId());
                    PreAuthState state = states.get(sourceState.getPlayerId());
                    if (online == null || state == null || !state.getName().equals(sourceState.getName())) return;

                    state.setLoaded(true);
                    if (throwable != null) {
                        plugin().getLogger().warning("Could not load pre-auth account for " + state.getName() + ": " + rootMessage(throwable));
                        prompt(online);
                        return;
                    }

                    if (account.isPresent()) {
                        state.setRegistered(true);
                        state.setAccountId(account.get().getUniqueId());
                    }

                    if (isAuthenticated(online)) return;
                    tryAutoLogin(online, state, account);
                })
        );
    }

    private void tryAutoLogin(@NotNull Player player, @NotNull PreAuthState state, @NotNull Optional<PALAccount> currentAccount) {
        IdentityRequest request = IdentityRequest.builder()
                .name(player.getName())
                .uniqueId(player.getUniqueId())
                .address(address(player))
                .serverOnlineMode(plugin().getServer().getOnlineMode())
                .build();

        api.getPremiumService().resolveIdentity(request)
                .thenCompose(identity -> autoLogin(player, currentAccount, identity))
                .whenComplete((account, throwable) -> plugin().getScheduler().runTask(() -> {
                    Player online = Bukkit.getPlayer(state.getPlayerId());
                    if (online == null) return;
                    if (throwable != null || account == null) {
                        if (!isAuthenticated(online)) prompt(online);
                        return;
                    }

                    state.setAccountId(account.getUniqueId());
                    state.setLoaded(true);
                    state.setRegistered(true);
                    state.setAuthenticated(true);
                    cancelTimeout(online.getUniqueId());
                    if (realmIntents.get(online.getUniqueId()) == PreAuthRealm.AUTH) {
                        realmIntents.put(online.getUniqueId(), PreAuthRealm.LOBBY);
                        applyRealm(online, PreAuthRealm.LOBBY);
                    }
                    online.sendMessage(message("premium.auto-login", placeholders(
                            "type", account.getType().name().toLowerCase(Locale.ROOT)
                    )));
                }));
    }

    @NotNull
    private CompletionStage<PALAccount> autoLogin(
            @NotNull Player player,
            @NotNull Optional<PALAccount> currentAccount,
            @NotNull IdentityResult identity
    ) {
        if (!identity.isAutoLoginEligible()) return CompletableFuture.completedFuture(null);
        if (identity.getUniqueId() == null || identity.getName() == null) return CompletableFuture.completedFuture(null);

        PALAccount.Type type = accountType(identity.getType());
        if (type == PALAccount.Type.UNKNOWN) return CompletableFuture.completedFuture(null);
        if (!isAutoLoginEnabled(type)) return CompletableFuture.completedFuture(null);

        if (currentAccount.isPresent()) {
            PALAccount account = currentAccount.get();
            if (matches(account, type, identity.getUniqueId())) return createSession(player, account);
            if (canUpgradeOffline(account, type, identity)) return upgradeOfflineAccount(player, account, identity);
        }

        return api.getAccountService().findById(identity.getUniqueId()).thenCompose(existing -> {
            if (existing.isPresent()) {
                PALAccount account = existing.get();
                if (!matches(account, type, identity.getUniqueId())) return CompletableFuture.completedFuture(null);

                return createSession(player, account);
            }

            AccountCreateRequest create = type == PALAccount.Type.BEDROCK
                    ? AccountCreateRequest.bedrock(identity.getName(), identity.getUniqueId(), address(player))
                    : AccountCreateRequest.premium(identity.getName(), identity.getUniqueId(), address(player));

            return api.getAccountService().create(create).thenCompose(result -> {
                PALAccount account = account(result);
                if (account == null || !matches(account, type, identity.getUniqueId())) return CompletableFuture.completedFuture(null);

                return createSession(player, account);
            });
        });
    }

    @NotNull
    private CompletionStage<PALAccount> createSession(@NotNull Player player, @NotNull PALAccount account) {
        return plugin().getTwoFactorService().beginLogin(
                player.getUniqueId(),
                player.getName(),
                account,
                AuthSource.PREMIUM,
                address(player)
        ).thenCompose(required -> required
                ? CompletableFuture.completedFuture(null)
                : api.getSessionService().create(account, AuthSource.PREMIUM, address(player)).thenApply(session -> account));
    }

    private boolean matches(@NotNull PALAccount account, @NotNull PALAccount.Type type, @NotNull UUID uniqueId) {
        return account.getType() == type && account.getUniqueId().equals(uniqueId);
    }

    private boolean canUpgradeOffline(
            @NotNull PALAccount account,
            @NotNull PALAccount.Type type,
            @NotNull IdentityResult identity
    ) {
        return type == PALAccount.Type.PREMIUM
                && account.getType() == PALAccount.Type.OFFLINE
                && identity.isVerified()
                && identity.getUniqueId() != null
                && ((PremiumServiceImpl) api.getPremiumService()).options().isUpgradeOfflineOnVerifiedPremium();
    }

    @NotNull
    private CompletionStage<PALAccount> upgradeOfflineAccount(
            @NotNull Player player,
            @NotNull PALAccount account,
            @NotNull IdentityResult identity
    ) {
        AccountCreateRequest create = AccountCreateRequest.premium(identity.getName(), identity.getUniqueId(), address(player));

        return api.getSessionService().invalidateAsync(account.getUniqueId())
                .thenCompose(ignored -> api.getAccountService().delete(account.getUniqueId()))
                .thenCompose(ignored -> api.getAccountService().create(create))
                .thenCompose(result -> {
                    PALAccount upgraded = account(result);
                    if (upgraded == null || !matches(upgraded, PALAccount.Type.PREMIUM, identity.getUniqueId())) {
                        return CompletableFuture.completedFuture(null);
                    }

                    return createSession(player, upgraded);
                });
    }

    private boolean isAutoLoginEnabled(@NotNull PALAccount.Type type) {
        PremiumOptions options = ((PremiumServiceImpl) api.getPremiumService()).options();
        switch (type) {
            case PREMIUM:
                return options.isPremiumAutoLogin();
            case BEDROCK:
                return options.isBedrockAutoLogin();
            default:
                return false;
        }
    }

    @NotNull
    private PALAccount.Type accountType(@NotNull IdentityType type) {
        switch (type) {
            case JAVA_PREMIUM:
                return PALAccount.Type.PREMIUM;
            case BEDROCK:
                return PALAccount.Type.BEDROCK;
            default:
                return PALAccount.Type.UNKNOWN;
        }
    }

    @Nullable
    private PALAccount account(@NotNull AccountResult result) {
        switch (result.getType()) {
            case SUCCESS:
            case ALREADY_EXISTS:
                return result.getAccount();
            default:
                return null;
        }
    }

    private boolean block(@NotNull Player player, @NotNull String message, @NotNull PreAuthRealm realm) {
        if (realm == PreAuthRealm.AUTH && isAuthenticated(player)) return false;
        warn(player, message);
        return true;
    }

    private boolean isAuthenticated(@NotNull Player player) {
        PreAuthState state = state(player);
        InetAddress currentAddress = address(player);

        if (state.getAccountId() != null &&
                api.getSessionService().isAuthenticated(state.getAccountId(), state.getName(), currentAddress)) {
            state.setAuthenticated(true);
            cancelTimeout(player.getUniqueId());
            if (realmIntents.get(player.getUniqueId()) == PreAuthRealm.AUTH) {
                realmIntents.put(player.getUniqueId(), PreAuthRealm.LOBBY);
                applyRealm(player, PreAuthRealm.LOBBY);
            }
            return true;
        }

        if (api.getSessionService().isAuthenticated(player.getUniqueId(), player.getName(), currentAddress)) {
            state.setAuthenticated(true);
            cancelTimeout(player.getUniqueId());
            if (realmIntents.get(player.getUniqueId()) == PreAuthRealm.AUTH) {
                realmIntents.put(player.getUniqueId(), PreAuthRealm.LOBBY);
                applyRealm(player, PreAuthRealm.LOBBY);
            }
            return true;
        }

        if (state.isAuthenticated()) {
            state.setAuthenticated(false);
            scheduleTimeout(player);
        }

        return false;
    }

    @NotNull
    private PreAuthState state(@NotNull Player player) {
        PreAuthState current = states.get(player.getUniqueId());
        if (current != null) return current;

        PreAuthState created = new PreAuthState(
                player.getUniqueId(),
                player.getName(),
                address(player),
                Instant.now(),
                null,
                false,
                false,
                false,
                0L
        );

        states.put(player.getUniqueId(), created);
        loadAccount(player, created);
        return created;
    }

    @NotNull
    private PreAuthRealm initialRealm(@NotNull Player player) {
        PreAuthRealm configured = options.getDefaultRealm();
        if (configured != PreAuthRealm.AUTO) return configured;
        return isAuthenticated(player) ? PreAuthRealm.LOBBY : PreAuthRealm.AUTH;
    }

    @NotNull
    private PreAuthRealm realm(@NotNull Player player) {
        PreAuthRealm realm = realmIntents.get(player.getUniqueId());
        if (realm != null && realm != PreAuthRealm.AUTO) return realm;

        PreAuthRealm configured = options.getDefaultRealm();
        if (configured != PreAuthRealm.AUTO) return configured;
        return isAuthenticated(player) ? PreAuthRealm.LOBBY : PreAuthRealm.AUTH;
    }

    @NotNull
    private PreAuthOptions.RestrictionOptions restrictions(@NotNull Player player) {
        return options.realm(realm(player)).getRestrictions();
    }

    private void applyRealm(@NotNull Player player, @NotNull PreAuthRealm realm) {
        PreAuthOptions.RealmOptions profile = options.realm(realm);
        for (PotionEffectType type : options.getManagedEffects()) player.removePotionEffect(type);
        for (PotionEffect effect : profile.getEffects()) player.addPotionEffect(effect, true);

        Location location = profile.getSpawn().location(player::getWorld);
        if (location == null) return;

        realmTeleports.add(player.getUniqueId());
        try {
            player.teleport(location);
        } finally {
            realmTeleports.remove(player.getUniqueId());
        }
    }

    private void scheduleTimeout(@NotNull Player player) {
        if (options.getTimeoutSeconds() <= 0 || isAuthenticated(player)) return;
        if (timeouts.containsKey(player.getUniqueId())) return;

        GlobalTask task = plugin().getScheduler().runTaskLater(() -> timeout(player.getUniqueId()), options.getTimeoutSeconds() * 20L);
        timeouts.put(player.getUniqueId(), task);
    }

    private void timeout(@NotNull UUID playerId) {
        timeouts.remove(playerId);

        Player player = Bukkit.getPlayer(playerId);
        if (player == null || isAuthenticated(player)) return;

        if (!options.isKickOnTimeout()) {
            warn(player, "auth.timed-out");
            return;
        }

        states.remove(playerId);
        player.kickPlayer(message("auth.timed-out"));
    }

    private void cancelTimeout(@NotNull UUID playerId) {
        GlobalTask task = timeouts.remove(playerId);
        if (task != null) task.cancel();
    }

    private void prompt(@NotNull Player player) {
        prompt(player, false);
    }

    private void prompt(@NotNull Player player, boolean force) {
        PreAuthState state = state(player);
        if (plugin().getTwoFactorService().hasPending(player.getUniqueId())) {
            warn(player, "security.two-factor-required", force);
            return;
        }

        warn(player, state.isRegistered() ? "auth.prompt-login.chat" : "auth.prompt-register.chat", force);
    }

    private void warn(@NotNull Player player, @NotNull String message) {
        warn(player, message, false);
    }

    private void warn(@NotNull Player player, @NotNull String message, boolean force) {
        PreAuthState state = state(player);
        long now = System.currentTimeMillis();
        if (!force && now - state.getLastWarningAt() < 2000L) return;

        state.setLastWarningAt(now);
        plugin().getScheduler().runTask(() -> {
            if (player.isOnline()) player.sendMessage(message(message));
        });
    }

    private boolean hasPositionChanged(@NotNull PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return false;
        if (from.getWorld() != to.getWorld()) return true;
        return from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
    }

    private boolean isAllowedCommand(@NotNull Player player, @NotNull String message) {
        String command = message.startsWith("/") ? message.substring(1) : message;
        return restrictions(player).isCommandAllowed(command);
    }

    private boolean requiresProxyRevalidation(@NotNull PALAccount account) {
        return account.getType() == PALAccount.Type.PREMIUM || account.getType() == PALAccount.Type.BEDROCK;
    }

    private boolean isDirectBackendBlocked(@NotNull Player player) {
        InetAddress current = address(player);
        return current != null && ((BridgeServiceImpl) api.getBridgeService()).blocksDirectBackend(current);
    }

    @Nullable
    private InetAddress address(@NotNull Player player) {
        InetSocketAddress socketAddress = player.getAddress();
        return socketAddress == null ? null : socketAddress.getAddress();
    }

    @NotNull
    private String message(@NotNull String message) {
        return plugin().getMessageService().get(message);
    }

    @NotNull
    private String message(@NotNull String message, @NotNull Map<String, Object> placeholders) {
        return plugin().getMessageService().get(message, placeholders);
    }

    @NotNull
    private Map<String, Object> placeholders(@NotNull Object... values) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            placeholders.put(String.valueOf(values[index]), values[index + 1]);
        }
        return placeholders;
    }

    @NotNull
    private PALPlugin plugin() {
        return (PALPlugin) api.getPlugin();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage();
    }

    private final class ListenerImpl implements Listener {

        @EventHandler(priority = EventPriority.LOWEST)
        private void onJoin(PlayerJoinEvent event) {
            handleJoin(event.getPlayer());
        }

        @EventHandler(priority = EventPriority.MONITOR)
        private void onQuit(PlayerQuitEvent event) {
            handleQuit(event.getPlayer());
        }

        @EventHandler(priority = EventPriority.MONITOR)
        private void onKick(PlayerKickEvent event) {
            handleQuit(event.getPlayer());
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        private void onMove(PlayerMoveEvent event) {
            PreAuthRealm realm = realm(event.getPlayer());
            if (!options.realm(realm).getRestrictions().isMovement() || !hasPositionChanged(event)) return;
            if (!block(event.getPlayer(), "pre-auth.move", realm)) return;

            event.setTo(event.getFrom());
            event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        private void onTeleport(PlayerTeleportEvent event) {
            if (realmTeleports.contains(event.getPlayer().getUniqueId())) return;
            PreAuthRealm realm = realm(event.getPlayer());
            if (!options.realm(realm).getRestrictions().isTeleport()) return;
            if (block(event.getPlayer(), "pre-auth.teleport", realm)) event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        private void onChat(AsyncPlayerChatEvent event) {
            PreAuthRealm realm = realm(event.getPlayer());
            if (!options.realm(realm).getRestrictions().isChat()) return;
            if (block(event.getPlayer(), "pre-auth.chat", realm)) event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        private void onCommand(PlayerCommandPreprocessEvent event) {
            PreAuthRealm realm = realm(event.getPlayer());
            PreAuthOptions.RestrictionOptions restrictions = options.realm(realm).getRestrictions();
            if (!restrictions.isCommands() || isAllowedCommand(event.getPlayer(), event.getMessage())) return;
            if (block(event.getPlayer(), "pre-auth.command", realm)) event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        private void onInventoryClick(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player player = (Player) event.getWhoClicked();
            PreAuthRealm realm = realm(player);
            if (!options.realm(realm).getRestrictions().isInventory()) return;
            if (block(player, "pre-auth.inventory", realm)) event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        private void onInventoryDrag(InventoryDragEvent event) {
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player player = (Player) event.getWhoClicked();
            PreAuthRealm realm = realm(player);
            if (!options.realm(realm).getRestrictions().isInventory()) return;
            if (block(player, "pre-auth.inventory", realm)) event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        private void onInventoryOpen(InventoryOpenEvent event) {
            if (!(event.getPlayer() instanceof Player)) return;
            Player player = (Player) event.getPlayer();
            PreAuthRealm realm = realm(player);
            if (!options.realm(realm).getRestrictions().isInventory()) return;
            if (block(player, "pre-auth.inventory", realm)) event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        private void onDamage(EntityDamageEvent event) {
            if (!(event.getEntity() instanceof Player)) return;
            Player player = (Player) event.getEntity();
            PreAuthRealm realm = realm(player);
            if (!options.realm(realm).getRestrictions().isDamage()) return;
            if (block(player, "pre-auth.damage", realm)) event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        private void onDamageByEntity(EntityDamageByEntityEvent event) {
            if (!(event.getDamager() instanceof Player)) return;
            Player player = (Player) event.getDamager();
            PreAuthRealm realm = realm(player);
            if (!options.realm(realm).getRestrictions().isDamage()) return;
            if (block(player, "pre-auth.damage", realm)) event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        private void onBlockPlace(BlockPlaceEvent event) {
            PreAuthRealm realm = realm(event.getPlayer());
            if (!options.realm(realm).getRestrictions().isBlockPlace()) return;
            if (block(event.getPlayer(), "pre-auth.block", realm)) event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        private void onBlockBreak(BlockBreakEvent event) {
            PreAuthRealm realm = realm(event.getPlayer());
            if (!options.realm(realm).getRestrictions().isBlockBreak()) return;
            if (block(event.getPlayer(), "pre-auth.block", realm)) event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        private void onDrop(PlayerDropItemEvent event) {
            PreAuthRealm realm = realm(event.getPlayer());
            if (!options.realm(realm).getRestrictions().isItemDrop()) return;
            if (block(event.getPlayer(), "pre-auth.item", realm)) event.setCancelled(true);
        }

        @SuppressWarnings("deprecation")
        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        private void onPickup(PlayerPickupItemEvent event) {
            PreAuthRealm realm = realm(event.getPlayer());
            if (!options.realm(realm).getRestrictions().isItemPickup()) return;
            if (block(event.getPlayer(), "pre-auth.item", realm)) event.setCancelled(true);
        }
    }
}
