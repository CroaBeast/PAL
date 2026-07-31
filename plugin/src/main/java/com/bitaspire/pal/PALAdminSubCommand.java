package com.bitaspire.pal;

import com.bitaspire.pal.auth.AuthSource;
import com.bitaspire.pal.auth.PasswordHash;
import com.bitaspire.pal.migration.MigrationProvider;
import com.bitaspire.pal.migration.MigrationResult;
import com.bitaspire.pal.account.PALAccount;
import com.bitaspire.pal.session.AuthSession;
import lombok.AccessLevel;
import lombok.Getter;
import me.croabeast.command.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Getter(AccessLevel.PACKAGE)
final class PALAdminSubCommand extends SubCommand {

    private final PALRootCommand root;
    private final PALSubCommandOptions options;

    PALAdminSubCommand(@NotNull PALRootCommand root, @NotNull PALSubCommandOptions options) {
        super(root, options.getKey());
        this.root = root;
        this.options = options;

        setPermission(options.getPermission());
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        switch (getName()) {
            case "reload":
                root.getPlugin().reload();
                return root.send(sender, "admin.reloaded");
            case "help":
                return root.sendHelp(sender);
            case "info":
                return root.sendInfo(sender);
            case "account":
                return account(sender, args);
            case "session":
                return session(sender, args);
            case "force-login":
                return forceLogin(sender, args);
            case "force-logout":
                return forceLogout(sender, args);
            case "reset-password":
                return resetPassword(sender, args);
            case "unregister":
                return unregister(sender, args);
            case "migrate":
                return migrate(sender, args);
            default:
                return root.send(sender, "reserved.subcommand", placeholders("command", getName()));
        }
    }

    @Override
    public boolean isPermitted(CommandSender sender, boolean log) {
        if (root.hasSubPermission(sender, options)) return true;
        return !log || root.send(sender, "admin.no-sub-permission", placeholders("permission", options.getPermission()));
    }

    private boolean account(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 1) return root.send(sender, "admin.usage-account");

        PALPlugin plugin = root.getPlugin();
        plugin.getAccountService().findByName(args[0]).whenComplete((account, throwable) ->
                plugin.getScheduler().runTask(() -> {
                    if (throwable != null) {
                        root.send(sender, "admin.account-error", placeholders("player", args[0]));
                        return;
                    }

                    if (!account.isPresent()) {
                        root.send(sender, "account.not-found", placeholders("player", args[0]));
                        return;
                    }

                    PALAccount palAccount = account.get();
                    root.send(sender, "admin.account-header", placeholders("player", palAccount.getName()));
                    root.send(sender, "admin.account-uuid", placeholders("uuid", palAccount.getUniqueId()));
                    root.send(sender, "admin.account-type", placeholders(
                            "type", palAccount.getType(),
                            "status", palAccount.getStatus()
                    ));
                    root.send(sender, "admin.account-registered", placeholders("registered", value(palAccount.getRegisteredAt())));
                    root.send(sender, "admin.account-last-login", placeholders("last_login", value(palAccount.getLastLoginAt())));
                }));

        return true;
    }

    private boolean session(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 1) return root.send(sender, "admin.usage-session");
        if ("clear".equalsIgnoreCase(args[0])) {
            if (args.length < 2) return root.send(sender, "admin.usage-session-clear");
            return forceLogout(sender, new String[]{args[1]});
        }

        PALPlugin plugin = root.getPlugin();
        plugin.getAccountService().findByName(args[0]).thenAccept(account -> {
            String message;
            Map<String, Object> placeholders = placeholders("player", args[0]);

            if (!account.isPresent()) {
                message = "account.not-found";
            } else {
                PALAccount palAccount = account.get();
                Optional<AuthSession> session = plugin.getSessionService().getSession(palAccount.getUniqueId());
                message = session.isPresent()
                        ? "admin.session-active"
                        : "admin.session-missing";
                placeholders = session.isPresent()
                        ? placeholders("player", palAccount.getName(), "source", session.get().getSource())
                        : placeholders("player", palAccount.getName());
            }

            Map<String, Object> finalPlaceholders = placeholders;
            plugin.getScheduler().runTask(() -> root.send(sender, message, finalPlaceholders));
        });

        return true;
    }

    private boolean forceLogin(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 1) return root.send(sender, "admin.usage-force-login");

        PALPlugin plugin = root.getPlugin();
        Player player = Bukkit.getPlayerExact(args[0]);
        if (player == null) return root.send(sender, "admin.player-online-required", placeholders("player", args[0]));

        plugin.getAccountService().findByName(args[0]).thenCompose(account -> {
            if (!account.isPresent()) return CompletableFuture.completedFuture(false);

            PALAccount palAccount = account.get();
            return plugin.getSessionService().create(palAccount, AuthSource.ADMIN, address(player))
                    .thenRun(() -> plugin.getPreAuthService().markAuthenticated(palAccount))
                    .thenCompose(ignored -> storage(plugin).audit(
                            palAccount.getUniqueId(),
                            palAccount.getName(),
                            "auth.admin-force-login",
                            address(player),
                            sender.getName()
                    ))
                    .thenApply(ignored -> true);
        }).whenComplete((success, throwable) -> plugin.getScheduler().runTask(() -> {
            if (throwable != null) {
                root.send(sender, "admin.force-login-error", placeholders("player", args[0]));
            } else if (!Boolean.TRUE.equals(success)) {
                root.send(sender, "admin.force-login-missing", placeholders("player", args[0]));
            } else {
                root.send(sender, "admin.force-login", placeholders("player", args[0]));
            }
        }));

        return true;
    }

    private boolean forceLogout(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 1) return root.send(sender, "admin.usage-force-logout");

        PALPlugin plugin = root.getPlugin();
        plugin.getAccountService().findByName(args[0]).thenCompose(account -> {
            if (!account.isPresent()) return CompletableFuture.completedFuture(false);

            PALAccount palAccount = account.get();
            return plugin.getSessionService().invalidateAsync(palAccount.getUniqueId())
                    .thenRun(() -> plugin.getPreAuthService().markForceLoggedOut(palAccount))
                    .thenCompose(ignored -> storage(plugin).audit(
                            palAccount.getUniqueId(),
                            palAccount.getName(),
                            "auth.admin-force-logout",
                            null,
                            sender.getName()
                    ))
                    .thenApply(ignored -> true);
        }).whenComplete((success, throwable) -> plugin.getScheduler().runTask(() -> {
            if (throwable != null) {
                root.send(sender, "admin.force-logout-error", placeholders("player", args[0]));
            } else if (!Boolean.TRUE.equals(success)) {
                root.send(sender, "account.not-found", placeholders("player", args[0]));
            } else {
                root.send(sender, "admin.force-logout", placeholders("player", args[0]));
            }
        }));

        return true;
    }

    private boolean resetPassword(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) return root.send(sender, "admin.usage-reset-password");
        if (args[1].trim().isEmpty()) return root.send(sender, "admin.reset-password-empty");

        PALPlugin plugin = root.getPlugin();
        plugin.getAccountService().findByName(args[0]).thenCompose(account -> {
            if (!account.isPresent()) return CompletableFuture.completedFuture(false);

            PALAccount palAccount = account.get();
            PasswordHash hash = new Pbkdf2PasswordHasher().hash(args[1].toCharArray());

            return storage(plugin).savePasswordHash(palAccount.getUniqueId(), hash)
                    .thenCompose(ignored -> plugin.getSessionService().invalidateAsync(palAccount.getUniqueId()))
                    .thenRun(() -> plugin.getPreAuthService().markUnauthenticated(palAccount.getUniqueId(), palAccount.getName()))
                    .thenCompose(ignored -> storage(plugin).audit(
                            palAccount.getUniqueId(),
                            palAccount.getName(),
                            "auth.admin-reset-password",
                            null,
                            sender.getName()
                    ))
                    .thenApply(ignored -> true);
        }).whenComplete((success, throwable) -> plugin.getScheduler().runTask(() -> {
            if (throwable != null) {
                root.send(sender, "admin.reset-password-error", placeholders("player", args[0]));
            } else if (!Boolean.TRUE.equals(success)) {
                root.send(sender, "account.not-found", placeholders("player", args[0]));
            } else {
                root.send(sender, "admin.reset-password", placeholders("player", args[0]));
            }
        }));

        return true;
    }

    private boolean unregister(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 1) return root.send(sender, "admin.usage-unregister");

        PALPlugin plugin = root.getPlugin();
        plugin.getAccountService().findByName(args[0]).thenCompose(account -> {
            if (!account.isPresent()) return CompletableFuture.completedFuture(false);

            PALAccount palAccount = account.get();
            return plugin.getSessionService().invalidateAsync(palAccount.getUniqueId())
                    .thenRun(() -> plugin.getPreAuthService().markUnauthenticated(palAccount.getUniqueId(), palAccount.getName()))
                    .thenCompose(ignored -> plugin.getAccountService().delete(palAccount.getUniqueId()))
                    .thenCompose(result -> storage(plugin).audit(
                            palAccount.getUniqueId(),
                            palAccount.getName(),
                            "account.admin-unregister",
                            null,
                            sender.getName()
                    ).thenApply(ignored -> result.isSuccessful()));
        }).whenComplete((success, throwable) -> plugin.getScheduler().runTask(() -> {
            if (throwable != null) {
                root.send(sender, "admin.unregister-error", placeholders("player", args[0]));
            } else if (!Boolean.TRUE.equals(success)) {
                root.send(sender, "account.not-found", placeholders("player", args[0]));
            } else {
                root.send(sender, "admin.unregister", placeholders("player", args[0]));
            }
        }));

        return true;
    }

    private boolean migrate(@NotNull CommandSender sender, @NotNull String[] args) {
        PALPlugin plugin = root.getPlugin();
        if (args.length < 1) {
            List<String> names = new ArrayList<>();
            for (MigrationProvider provider : plugin.getMigrationService().providers()) {
                names.add(provider.getName() + (provider.isAvailable() ? "" : " (missing)"));
            }
            return root.send(sender, "migration.usage-providers", placeholders("providers", String.join("|", names)));
        }

        CompletionStage<MigrationResult> stage = plugin.getMigrationService().migrate(args[0]);
        stage.whenComplete((result, throwable) -> plugin.getScheduler().runTask(() -> {
            if (throwable != null) {
                root.send(sender, "migration.failed", placeholders("reason", throwable.getMessage()));
                return;
            }

            if (!result.isSuccessful()) {
                String message = result instanceof MigrationResultImpl ? ((MigrationResultImpl) result).getMessage() : "unknown error";
                root.send(sender, "migration.failed", placeholders("reason", message));
                return;
            }

            String skipped = "0";
            String failed = "0";
            if (result instanceof MigrationResultImpl) {
                MigrationResultImpl impl = (MigrationResultImpl) result;
                skipped = String.valueOf(impl.getSkipped());
                failed = String.valueOf(impl.getFailed());
            }

            root.send(sender, "migration.completed-detail", placeholders(
                    "accounts", result.getAccounts(),
                    "skipped", skipped,
                    "failed", failed
            ));
        }));

        return true;
    }

    @NotNull
    private StorageServiceImpl storage(@NotNull PALPlugin plugin) {
        return (StorageServiceImpl) plugin.getStorageService();
    }

    @Nullable
    private InetAddress address(@NotNull Player player) {
        InetSocketAddress socketAddress = player.getAddress();
        return socketAddress == null ? null : socketAddress.getAddress();
    }

    @NotNull
    private String value(@Nullable Object value) {
        return value == null ? "never" : String.valueOf(value);
    }

    @NotNull
    private Map<String, Object> placeholders(@NotNull Object... values) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            placeholders.put(String.valueOf(values[index]), values[index + 1]);
        }
        return placeholders;
    }
}
