package com.bitaspire.pal;

import com.bitaspire.pal.session.AuthSession;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Optional;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class PlaceholderApiExpansionHook extends PlaceholderExpansion implements IntegrationHook {

    private final PALPlugin plugin;

    @NotNull
    @Override
    public String getIdentifier() {
        return "pal";
    }

    @NotNull
    @Override
    public String getAuthor() {
        return "BitAspire";
    }

    @NotNull
    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean register() {
        return super.register();
    }

    @NotNull
    @Override
    public String name() {
        return "PlaceholderAPI expansion";
    }

    @Nullable
    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        String key = params.trim().toLowerCase().replace('-', '_');

        if ("bridge_enabled".equals(key)) return bool(plugin.getBridgeService().isEnabled());
        if ("bridge_mode".equals(key)) return plugin.getBridgeService().getMode().name();
        if ("storage_type".equals(key)) return plugin.getStorageService().getType().name();
        if ("fastlogin".equals(key) || "fastlogin_available".equals(key)) return bool(plugin.getPremiumService().isFastLoginAvailable());
        if ("floodgate".equals(key) || "floodgate_available".equals(key)) return bool(plugin.getPremiumService().isBedrockDetectionAvailable());
        if ("native".equals(key) || "native_available".equals(key)) return bool(plugin.getPremiumService().isNativeResolverAvailable());

        if (player == null) return "";

        Optional<AuthSession> session = plugin.getSessionService().getSession(player.getUniqueId(), player.getName(), address(player));

        if ("authenticated".equals(key)) return bool(session.isPresent());
        if ("session_source".equals(key)) return session.map(value -> value.getSource().name()).orElse("NONE");
        if ("session_id".equals(key)) return session.map(AuthSession::getSessionId).orElse("");
        if ("session_expires".equals(key)) return session.map(value -> format(value.getExpiresAt())).orElse("");
        if ("2fa_pending".equals(key) || "twofactor_pending".equals(key)) {
            return bool(plugin.getTwoFactorService().hasPending(player.getUniqueId()));
        }

        return null;
    }

    @NotNull
    private String bool(boolean value) {
        return value ? "true" : "false";
    }

    @NotNull
    private String format(@Nullable Instant instant) {
        return instant == null ? "never" : instant.toString();
    }

    @Nullable
    private InetAddress address(@NotNull Player player) {
        InetSocketAddress socketAddress = player.getAddress();
        return socketAddress == null ? null : socketAddress.getAddress();
    }
}
