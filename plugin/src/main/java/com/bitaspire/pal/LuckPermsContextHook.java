package com.bitaspire.pal;

import com.bitaspire.pal.session.AuthSession;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import net.luckperms.api.context.ContextSet;
import net.luckperms.api.context.ImmutableContextSet;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class LuckPermsContextHook implements IntegrationHook {

    private final PALPlugin plugin;
    private final PALContextCalculator calculator = new PALContextCalculator();
    private LuckPerms luckPerms;

    @Override
    public boolean register() {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) return false;

        luckPerms = provider.getProvider();
        luckPerms.getContextManager().registerCalculator(calculator);
        return true;
    }

    @Override
    public boolean unregister() {
        if (luckPerms == null) return false;

        luckPerms.getContextManager().unregisterCalculator(calculator);
        luckPerms = null;
        return true;
    }

    @NotNull
    @Override
    public String name() {
        return "LuckPerms contexts";
    }

    private final class PALContextCalculator implements ContextCalculator<Player> {

        @Override
        public void calculate(@NotNull Player target, @NotNull ContextConsumer consumer) {
            Optional<AuthSession> session = plugin.getSessionService().getSession(target.getUniqueId(), target.getName(), address(target));

            consumer.accept("pal:authenticated", session.isPresent() ? "true" : "false");
            consumer.accept("pal:session_source", session.map(value -> value.getSource().name().toLowerCase()).orElse("none"));
            consumer.accept("pal:2fa_pending", plugin.getTwoFactorService().hasPending(target.getUniqueId()) ? "true" : "false");
            consumer.accept("pal:bridge_mode", plugin.getBridgeService().getMode().name().toLowerCase());
            consumer.accept("pal:storage_type", plugin.getStorageService().getType().name().toLowerCase());
        }

        @Override
        public ContextSet estimatePotentialContexts() {
            return ImmutableContextSet.builder()
                    .add("pal:authenticated", "true")
                    .add("pal:authenticated", "false")
                    .add("pal:session_source", "none")
                    .add("pal:session_source", "command")
                    .add("pal:session_source", "premium")
                    .add("pal:session_source", "admin")
                    .add("pal:session_source", "api")
                    .add("pal:2fa_pending", "true")
                    .add("pal:2fa_pending", "false")
                    .build();
        }
    }

    private InetAddress address(@NotNull Player player) {
        InetSocketAddress socketAddress = player.getAddress();
        return socketAddress == null ? null : socketAddress.getAddress();
    }
}
