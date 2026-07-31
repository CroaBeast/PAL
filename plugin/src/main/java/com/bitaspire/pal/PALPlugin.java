package com.bitaspire.pal;

import com.bitaspire.pal.account.AccountService;
import com.bitaspire.pal.auth.AuthService;
import com.bitaspire.pal.bridge.BridgeService;
import com.bitaspire.pal.integration.IntegrationManager;
import com.bitaspire.pal.premium.PremiumService;
import com.bitaspire.pal.session.SessionService;
import com.bitaspire.pal.storage.StorageService;
import me.croabeast.scheduler.GlobalScheduler;
import me.croabeast.takion.TakionLib;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class PALPlugin extends JavaPlugin implements PALApi {

    private Platform platform;
    private GlobalScheduler scheduler;

    private PALLibrary library;
    private PALConfig configuration;
    private MessageServiceImpl messageService;

    private ConfigurationServiceImpl configurationService;
    private IdentityHookServiceImpl identityHookService;
    private AccountServiceImpl accountService;
    private AuthServiceImpl authService;
    private SessionServiceImpl sessionService;
    private PreAuthServiceImpl preAuthService;
    private TwoFactorServiceImpl twoFactorService;
    private StorageServiceImpl storageService;
    private PremiumServiceImpl premiumService;
    private BridgeServiceImpl bridgeService;
    private ProtocolServiceImpl protocolService;
    private MigrationServiceImpl migrationService;

    private IntegrationManagerImpl integrationManager;
    private PALCommandManager commandManager;

    @Override
    public void onEnable() {
        Api.api = this;

        platform = PlatformDetector.detect();
        scheduler = GlobalScheduler.getScheduler(this);
        configurationService = new ConfigurationServiceImpl(this);
        configurationService.saveDefaults();
        configurationService.validate();

        configuration = new PALConfig(this);
        messageService = new MessageServiceImpl(this);
        library = new PALLibrary(this);
        library.reload();

        integrationManager = new IntegrationManagerImpl(this);
        identityHookService = new IdentityHookServiceImpl(this);
        accountService = new AccountServiceImpl(this);
        authService = new AuthServiceImpl(this);
        sessionService = new SessionServiceImpl(this);
        preAuthService = new PreAuthServiceImpl(this);
        twoFactorService = new TwoFactorServiceImpl(this);
        storageService = new StorageServiceImpl(this);
        premiumService = new PremiumServiceImpl(this);
        bridgeService = new BridgeServiceImpl(this);
        protocolService = new ProtocolServiceImpl(this);
        migrationService = new MigrationServiceImpl(this);

        integrationManager.register();
        identityHookService.register();
        if (!storageService.register()) {
            getLogger().severe("PAL storage could not be initialized. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        accountService.register();
        authService.register();
        sessionService.register();
        twoFactorService.register();
        preAuthService.register();
        premiumService.register();
        bridgeService.register();
        protocolService.register();
        migrationService.register();

        commandManager = new PALCommandManager(this);
        commandManager.register();

        getLogger().info("PAL initialized on " + platform + " with scaffold services.");
    }

    @Override
    public void onDisable() {
        if (commandManager != null) commandManager.unregister();
        if (migrationService != null) migrationService.unregister();
        if (protocolService != null) protocolService.unregister();
        if (bridgeService != null) bridgeService.unregister();
        if (premiumService != null) premiumService.unregister();
        if (preAuthService != null) preAuthService.unregister();
        if (twoFactorService != null) twoFactorService.unregister();
        if (sessionService != null) sessionService.unregister();
        if (authService != null) authService.unregister();
        if (accountService != null) accountService.unregister();
        if (storageService != null) storageService.unregister();
        if (identityHookService != null) identityHookService.unregister();
        if (integrationManager != null) integrationManager.unregister();

        Api.api = null;
    }

    @Override
    public void reload() {
        configurationService.reload();
        configuration = new PALConfig(this);
        if (messageService != null) messageService.reload();
        library.reload();

        if (integrationManager != null) {
            integrationManager.unregister();
            integrationManager.register();
        }

        if (identityHookService != null) identityHookService.reload();
        if (premiumService != null) premiumService.reload();
        if (storageService != null) storageService.reload();
        if (authService != null) authService.reload();
        if (sessionService != null) sessionService.reload();
        if (twoFactorService != null) twoFactorService.reload();
        if (preAuthService != null) preAuthService.reload();
        if (bridgeService != null) bridgeService.reload();
        if (protocolService != null) protocolService.reload();
        if (migrationService != null) migrationService.reload();

        if (commandManager != null) commandManager.reload();
    }

    @NotNull
    @Override
    public Plugin getPlugin() {
        return this;
    }

    @NotNull
    @Override
    public Platform getPlatform() {
        return platform;
    }

    @NotNull
    @Override
    public GlobalScheduler getScheduler() {
        return scheduler;
    }

    @NotNull
    @Override
    public TakionLib getLibrary() {
        return library;
    }

    @NotNull
    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @NotNull
    @Override
    public AccountService getAccountService() {
        return accountService;
    }

    @NotNull
    @Override
    public AuthService getAuthService() {
        return authService;
    }

    @NotNull
    @Override
    public SessionService getSessionService() {
        return sessionService;
    }

    @NotNull
    @Override
    public StorageService getStorageService() {
        return storageService;
    }

    @NotNull
    @Override
    public PremiumService getPremiumService() {
        return premiumService;
    }

    @NotNull
    @Override
    public BridgeService getBridgeService() {
        return bridgeService;
    }

    @NotNull
    @Override
    public IntegrationManager getIntegrationManager() {
        return integrationManager;
    }

    @NotNull
    PreAuthServiceImpl getPreAuthService() {
        return preAuthService;
    }

    @NotNull
    TwoFactorServiceImpl getTwoFactorService() {
        return twoFactorService;
    }

    @NotNull
    MessageServiceImpl getMessageService() {
        return messageService;
    }

    @NotNull
    IdentityHookServiceImpl getIdentityHookService() {
        return identityHookService;
    }

    @NotNull
    MigrationServiceImpl getMigrationService() {
        return migrationService;
    }

    void callEvent(@NotNull Event event) {
        scheduler.runTask(() -> getServer().getPluginManager().callEvent(event));
    }
}
