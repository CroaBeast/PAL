package com.bitaspire.pal;

import com.bitaspire.pal.account.AccountCreateRequest;
import com.bitaspire.pal.account.AccountResult;
import com.bitaspire.pal.account.PALAccount;
import com.bitaspire.pal.auth.AuthRequest;
import com.bitaspire.pal.auth.AuthResult;
import com.bitaspire.pal.auth.AuthSource;
import com.bitaspire.pal.auth.PasswordHash;
import com.bitaspire.pal.identity.IdentityRequest;
import com.bitaspire.pal.identity.IdentityResult;
import com.bitaspire.pal.identity.IdentityType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.croabeast.command.Synchronizer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

final class PALAuthCommand extends PALCommand {

    private final Action action;

    PALAuthCommand(
            @NotNull PALPlugin plugin,
            @NotNull PALCommandOptions options,
            @NotNull Synchronizer synchronizer,
            @NotNull Action action
    ) {
        super(plugin, options, synchronizer);
        this.action = action;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player)) return send(sender, "general.player-only");

        Player player = (Player) sender;
        switch (action) {
            case REGISTER:
                return register(player, args);
            case LOGIN:
                return login(player, args);
            case CHANGE_PASSWORD:
                return changePassword(player, args);
            case LOGOUT:
                return logout(player);
            case UNREGISTER:
                return unregister(player, args);
            case PREMIUM:
                return premium(player, args);
            case CRACKED:
                return cracked(player, args);
            case TWO_FACTOR:
                return twoFactor(player, args);
            default:
                return send(player, "general.not-ready");
        }
    }

    @NotNull
    @Override
    public Supplier<Collection<String>> generateCompletions(CommandSender sender, String[] arguments) {
        if (action != Action.TWO_FACTOR || arguments.length != 1) return Collections::emptyList;

        return () -> Arrays.asList("setup", "confirm", "disable", "recover");
    }

    private boolean register(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 2) return usage(player);

        AuthRequest request = AuthRequest.register(
                player.getUniqueId(),
                player.getName(),
                args[0],
                args[1],
                address(player)
        );

        plugin.getAuthService().register(request).thenAccept(result -> reply(player, result));
        return true;
    }

    private boolean login(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 1) return usage(player);

        AuthRequest request = AuthRequest.command(player.getUniqueId(), player.getName(), args[0], address(player));
        plugin.getAuthService().login(request).thenAccept(result -> reply(player, result));
        return true;
    }

    private boolean changePassword(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 2) return usage(player);

        AuthRequest request = AuthRequest.changePassword(
                player.getUniqueId(),
                player.getName(),
                args[0],
                args[1],
                address(player)
        );

        plugin.getAuthService().changePassword(request).thenAccept(result -> reply(player, result));
        return true;
    }

    private boolean logout(@NotNull Player player) {
        AuthRequest request = AuthRequest.command(player.getUniqueId(), player.getName(), null, address(player));
        plugin.getAuthService().logout(request).thenAccept(result -> reply(player, result));
        return true;
    }

    private boolean unregister(@NotNull Player player, @NotNull String[] args) {
        if (!confirmed(args)) return confirmation(player);

        authenticated(player, account -> plugin.getSessionService().invalidateAsync(account.getUniqueId())
                .thenCompose(ignored -> plugin.getTwoFactorService().delete(account.getUniqueId()))
                .thenCompose(ignored -> plugin.getAccountService().delete(account.getUniqueId()))
                .thenRun(() -> plugin.getPreAuthService().markUnregistered(player.getUniqueId(), player.getName()))
                .thenCompose(ignored -> storage().audit(
                        account.getUniqueId(),
                        account.getName(),
                        "account.unregister",
                        address(player),
                        "self"
                ))
                .thenApply(ignored -> CommandResult.message("auth.unregistered"))
        ).thenAccept(result -> reply(player, result));

        return true;
    }

    private boolean premium(@NotNull Player player, @NotNull String[] args) {
        if (!confirmed(args)) return confirmation(player);

        authenticated(player, account -> {
            if (account.getType() == PALAccount.Type.PREMIUM) {
                return completed(CommandResult.message("premium.already-enabled"));
            }

            IdentityRequest request = IdentityRequest.builder()
                    .name(player.getName())
                    .uniqueId(player.getUniqueId())
                    .address(address(player))
                    .serverOnlineMode(plugin.getServer().getOnlineMode())
                    .build();

            return plugin.getPremiumService().resolveIdentity(request).thenCompose(identity -> {
                if (!isVerifiedPremium(identity)) {
                    return completed(CommandResult.message("premium.verify-required", placeholders(
                            "reason", identity.getReason() == null ? "ownership was not verified" : identity.getReason()
                    )));
                }

                AccountCreateRequest create = AccountCreateRequest.premium(
                        identity.getName() == null ? player.getName() : identity.getName(),
                        identity.getUniqueId(),
                        address(player)
                );

                return storage().findPasswordHash(account.getUniqueId())
                        .thenCompose(hash -> switchAccount(player, account, create, hash.orElse(null), AuthSource.PREMIUM, "premium.enabled"));
            });
        }).thenAccept(result -> reply(player, result));

        return true;
    }

    private boolean cracked(@NotNull Player player, @NotNull String[] args) {
        if (!confirmed(args)) return confirmation(player);

        authenticated(player, account -> {
            if (account.getType() == PALAccount.Type.OFFLINE) {
                return completed(CommandResult.message("cracked.already-enabled"));
            }

            int offset = confirmationOffset();
            AccountCreateRequest create = AccountCreateRequest.offline(player.getName(), address(player));

            return storage().findPasswordHash(account.getUniqueId()).thenCompose(hash -> {
                if (hash.isPresent()) {
                    return switchAccount(player, account, create, hash.get(), AuthSource.COMMAND, "cracked.enabled");
                }

                if (args.length < offset + 2) return completed(CommandResult.message("cracked.password-required"));

                AuthResult validation = ((AuthServiceImpl) plugin.getAuthService()).validateNewPassword(player.getName(), args[offset], args[offset + 1]);
                if (validation != null) return completed(CommandResult.message(message(validation)));

                PasswordHash nextHash = ((AuthServiceImpl) plugin.getAuthService()).hashPassword(args[offset]);
                return switchAccount(player, account, create, nextHash, AuthSource.COMMAND, "cracked.enabled");
            });
        }).thenAccept(result -> reply(player, result));

        return true;
    }

    private boolean twoFactor(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 1) return usage(player);

        String action = args[0].toLowerCase();
        CompletionStage<TwoFactorServiceImpl.TwoFactorResult> stage;

        switch (action) {
            case "setup":
                stage = plugin.getTwoFactorService().startSetup(player);
                break;
            case "confirm":
                if (args.length < 2) return usage(player);
                stage = plugin.getTwoFactorService().confirmSetup(player, args[1]);
                break;
            case "disable":
                if (args.length < 2) return usage(player);
                stage = plugin.getTwoFactorService().disable(player, args[1]);
                break;
            case "recover":
                if (args.length < 2) return usage(player);
                stage = plugin.getTwoFactorService().verifyPending(player, args[1], true);
                break;
            default:
                stage = plugin.getTwoFactorService().verifyPending(player, args[0], false);
                break;
        }

        stage.thenAccept(result -> reply(player, result));
        return true;
    }

    @NotNull
    private CompletionStage<CommandResult> authenticated(
            @NotNull Player player,
            @NotNull Function<PALAccount, CompletionStage<CommandResult>> action
    ) {
        return plugin.getAccountService().findByName(player.getName()).thenCompose(account -> {
            if (!account.isPresent()) return completed(CommandResult.message("auth.not-registered"));

            PALAccount palAccount = account.get();
            return plugin.getSessionService().isAuthenticated(palAccount.getUniqueId(), palAccount.getName(), address(player))
                    ? action.apply(palAccount)
                    : completed(CommandResult.message("auth.not-logged"));
        }).exceptionally(throwable -> CommandResult.message("general.error"));
    }

    @NotNull
    private CompletionStage<CommandResult> switchAccount(
            @NotNull Player player,
            @NotNull PALAccount current,
            @NotNull AccountCreateRequest create,
            @Nullable PasswordHash passwordHash,
            @NotNull AuthSource source,
            @NotNull String successKey
    ) {
        return plugin.getSessionService().invalidateAsync(current.getUniqueId())
                .thenCompose(ignored -> plugin.getTwoFactorService().delete(current.getUniqueId()))
                .thenCompose(ignored -> plugin.getAccountService().delete(current.getUniqueId()))
                .thenCompose(ignored -> plugin.getAccountService().create(create))
                .thenCompose(result -> {
                    if (!result.isSuccessful() || result.getAccount() == null) {
                        return completed(CommandResult.message(accountMessage(result)));
                    }

                    PALAccount next = result.getAccount();
                    CompletionStage<Void> passwordStage = passwordHash == null
                            ? CompletableFuture.completedFuture(null)
                            : storage().savePasswordHash(next.getUniqueId(), passwordHash);

                    return passwordStage
                            .thenCompose(ignored -> plugin.getSessionService().create(next, source, address(player)))
                            .thenRun(() -> plugin.getPreAuthService().markAuthenticated(next))
                            .thenCompose(ignored -> storage().audit(
                                    next.getUniqueId(),
                                    next.getName(),
                                    "account.switch",
                                    address(player),
                                    current.getType().name() + "->" + next.getType().name()
                            ))
                            .thenApply(ignored -> CommandResult.message(successKey));
                })
                .exceptionally(throwable -> CommandResult.message("general.error"));
    }

    private boolean confirmed(@NotNull String[] args) {
        return !getOptions().isRequireConfirmation() || args.length > 0 && "confirm".equalsIgnoreCase(args[0]);
    }

    private int confirmationOffset() {
        return getOptions().isRequireConfirmation() ? 1 : 0;
    }

    private boolean confirmation(@NotNull Player player) {
        return send(player, "general.confirm", placeholders("usage", getUsage()));
    }

    private void reply(@NotNull Player player, @NotNull AuthResult result) {
        plugin.getScheduler().runTask(() -> send(player, message(result)));
    }

    private void reply(@NotNull Player player, @NotNull CommandResult result) {
        plugin.getScheduler().runTask(() -> send(player, result.getMessageKey(), result.getPlaceholders()));
    }

    private void reply(@NotNull Player player, @NotNull TwoFactorServiceImpl.TwoFactorResult result) {
        plugin.getScheduler().runTask(() -> send(player, result.getMessageKey(), result.getPlaceholders()));
    }

    private boolean usage(@NotNull Player player) {
        return send(player, "general.usage", placeholders("usage", getUsage()));
    }

    @NotNull
    private String message(@NotNull AuthResult result) {
        return result.getMessageKey() == null ? "auth.error" : result.getMessageKey();
    }

    @NotNull
    private String accountMessage(@NotNull AccountResult result) {
        switch (result.getType()) {
            case ALREADY_EXISTS:
                return "auth.already-registered";
            case NAME_LOCKED:
                return "account.name-locked";
            case NOT_FOUND:
                return "auth.not-registered";
            case INVALID_REQUEST:
            case ERROR:
            default:
                return result.getMessageKey() == null ? "general.error" : result.getMessageKey();
        }
    }

    private boolean isVerifiedPremium(@NotNull IdentityResult identity) {
        return identity.getType() == IdentityType.JAVA_PREMIUM
                && identity.isVerified()
                && identity.getUniqueId() != null;
    }

    @NotNull
    private CompletionStage<CommandResult> completed(@NotNull CommandResult result) {
        return CompletableFuture.completedFuture(result);
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
    private StorageServiceImpl storage() {
        return (StorageServiceImpl) plugin.getStorageService();
    }

    @Nullable
    private InetAddress address(@NotNull Player player) {
        InetSocketAddress socketAddress = player.getAddress();
        return socketAddress == null ? null : socketAddress.getAddress();
    }

    enum Action {
        REGISTER,
        LOGIN,
        CHANGE_PASSWORD,
        LOGOUT,
        UNREGISTER,
        PREMIUM,
        CRACKED,
        TWO_FACTOR
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class CommandResult {

        @NotNull
        private final String messageKey;
        @NotNull
        private final Map<String, Object> placeholders;

        @NotNull
        static CommandResult message(@NotNull String messageKey) {
            return message(messageKey, Collections.emptyMap());
        }

        @NotNull
        static CommandResult message(@NotNull String messageKey, @NotNull Map<String, Object> placeholders) {
            return new CommandResult(messageKey, Collections.unmodifiableMap(new LinkedHashMap<>(placeholders)));
        }
    }
}
