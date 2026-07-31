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
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public interface PALApi {

    @NotNull
    Plugin getPlugin();

    @NotNull
    Platform getPlatform();

    @NotNull
    GlobalScheduler getScheduler();

    @NotNull
    TakionLib getLibrary();

    @NotNull
    Configuration getConfiguration();

    @NotNull
    AccountService getAccountService();

    @NotNull
    AuthService getAuthService();

    @NotNull
    SessionService getSessionService();

    @NotNull
    StorageService getStorageService();

    @NotNull
    PremiumService getPremiumService();

    @NotNull
    BridgeService getBridgeService();

    @NotNull
    IntegrationManager getIntegrationManager();

    void reload();

    static boolean isInitialized() {
        return Api.api != null;
    }

    @NotNull
    static PALApi instance() {
        return Objects.requireNonNull(Api.api, "PAL's API is not initialized yet");
    }
}
