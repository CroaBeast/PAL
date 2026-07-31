package com.bitaspire.pal;

import com.bitaspire.pal.identity.IdentityMode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.croabeast.file.ConfigurableFile;
import org.jetbrains.annotations.NotNull;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
final class PremiumOptions {

    private final boolean enabled;
    private final IdentityMode mode;

    private final boolean fastLoginEnabled;
    private final boolean fastLoginRequired;
    private final boolean fastLoginAutoDetect;

    private final boolean nativeResolverEnabled;
    private final int nativeCacheMinutes;
    private final int nativeTimeoutMillis;
    private final boolean trustNameLookup;
    private final boolean bukkitOnlineModeProof;
    private final String mojangErrorFallback;

    private final boolean premiumAutoLogin;
    private final boolean upgradeOfflineOnVerifiedPremium;
    private final boolean protectPremiumNameFromOffline;

    private final boolean offlineAccountsEnabled;
    private final String premiumNameConflictPolicy;

    private final boolean floodgateEnabled;
    private final boolean bedrockAutoLogin;

    @NotNull
    static PremiumOptions load(@NotNull PALPlugin plugin) {
        try {
            ConfigurableFile file = new ConfigurableFile(plugin, "premium");
            file.saveDefaults();

            return new PremiumOptions(
                    bool(file, "premium.enabled", true),
                    IdentityMode.from(string(file, "premium.mode", "HYBRID")),
                    bool(file, "premium.fast-login.enabled", true),
                    bool(file, "premium.fast-login.required", false),
                    bool(file, "premium.fast-login.auto-detect", true),
                    bool(file, "premium.pal-resolver.enabled", true),
                    integer(file, "premium.pal-resolver.cache-minutes", 1440),
                    integer(file, "premium.pal-resolver.timeout-ms", 5000),
                    bool(file, "premium.pal-resolver.trust-name-lookup", false),
                    bool(file, "premium.pal-resolver.bukkit-online-mode-proof", true),
                    string(file, "premium.pal-resolver.fallback-on-mojang-error", "OFFLINE_LOGIN"),
                    bool(file, "premium.premium-accounts.auto-login", true),
                    bool(file, "premium.premium-accounts.upgrade-offline-on-verified-login", true),
                    bool(file, "premium.premium-accounts.protect-premium-name-from-offline", true),
                    bool(file, "premium.offline-accounts.enabled", true),
                    string(file, "premium.offline-accounts.block-if-premium-name-exists", "ASK"),
                    bool(file, "premium.bedrock.floodgate.enabled", true),
                    bool(file, "premium.bedrock.floodgate.auto-login", true)
            );
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not load premium.yml: " + exception.getMessage());
            return defaults();
        }
    }

    @NotNull
    private static PremiumOptions defaults() {
        return new PremiumOptions(true, IdentityMode.HYBRID,
                true, false, true,
                true, 1440, 5000, false, true, "OFFLINE_LOGIN",
                true, true, true,
                true, "ASK",
                true, true);
    }

    private static boolean bool(ConfigurableFile file, String path, boolean defaultValue) {
        Object value = file.get(path, defaultValue);
        return value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(String.valueOf(value));
    }

    private static int integer(ConfigurableFile file, String path, int defaultValue) {
        Object value = file.get(path, defaultValue);
        if (value instanceof Number) return Math.max(0, ((Number) value).intValue());

        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String string(ConfigurableFile file, String path, String defaultValue) {
        Object value = file.get(path, defaultValue);
        return value == null ? defaultValue : String.valueOf(value);
    }
}
