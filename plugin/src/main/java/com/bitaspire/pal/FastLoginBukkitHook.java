package com.bitaspire.pal;

import com.bitaspire.pal.identity.IdentityProvider;
import com.bitaspire.pal.identity.IdentityResult;
import com.bitaspire.pal.identity.IdentityTrust;
import com.github.games647.fastlogin.bukkit.event.BukkitFastLoginAutoLoginEvent;
import com.github.games647.fastlogin.bukkit.event.BukkitFastLoginPreLoginEvent;
import com.github.games647.fastlogin.core.shared.LoginSession;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class FastLoginBukkitHook implements Listener {

    @NotNull
    private final IdentityHookServiceImpl service;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onAutoLogin(@NotNull BukkitFastLoginAutoLoginEvent event) {
        LoginSession session = event.getSession();
        if (session == null) return;

        String name = firstNonBlank(session.getUsername(), session.getRequestUsername());
        if (name == null) return;

        StoredProfile profile = firstProfile(event.getProfile(), session.getProfile());
        UUID uniqueId = firstUniqueId(session.getUuid(), profile == null ? null : profile.getId());
        boolean verified = uniqueId != null && profile != null && profile.isPremium();

        service.cacheFastLogin(name, verified
                ? IdentityResult.verifiedPremium(IdentityProvider.FAST_LOGIN, uniqueId, name, IdentityTrust.FAST_LOGIN)
                : IdentityResult.offline(IdentityProvider.FAST_LOGIN, name, "FastLogin did not verify premium ownership"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onPreLogin(@NotNull BukkitFastLoginPreLoginEvent event) {
        String name = blankToNull(event.getUsername());
        StoredProfile profile = event.getProfile();
        if (name == null || profile == null || profile.isOnlinemodePreferred()) return;

        service.cacheFastLogin(name, IdentityResult.offline(
                IdentityProvider.FAST_LOGIN,
                name,
                "FastLogin profile is not configured for premium online-mode login"
        ));
    }

    @Nullable
    private StoredProfile firstProfile(@Nullable StoredProfile first, @Nullable StoredProfile second) {
        return first == null ? second : first;
    }

    @Nullable
    private UUID firstUniqueId(@Nullable UUID first, @Nullable UUID second) {
        return first == null ? second : first;
    }

    @Nullable
    private String firstNonBlank(@Nullable String first, @Nullable String second) {
        String value = blankToNull(first);
        return value == null ? blankToNull(second) : value;
    }

    @Nullable
    private String blankToNull(@Nullable String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
