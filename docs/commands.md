# PAL Commands

Commands are registered dynamically through `commands.yml` and CommandFramework. Names, aliases, permissions, default access, usage text and enabled state can be changed from that file.

## Player Commands

| Command | Aliases | Permission | Default | Description |
| --- | --- | --- | --- | --- |
| `/login <password>` | `/l` | `pal.command.login` | true | Logs into an existing account. |
| `/register <password> <password>` | `/reg` | `pal.command.register` | true | Registers an offline account. |
| `/changepassword <old-password> <new-password>` | `/changepw`, `/cpw` | `pal.command.changepassword` | true | Changes the current account password. |
| `/logout` | none | `pal.command.logout` | true | Invalidates the current session. |
| `/unregister confirm` | none | `pal.command.unregister` | true | Deletes the current account registration. |
| `/premium confirm` | none | `pal.command.premium` | true | Switches the current account to premium mode after verified ownership. |
| `/cracked confirm` | `/offline` | `pal.command.cracked` | true | Switches the current account to offline mode. |
| `/pal2fa <code>` | `/2fa` | `pal.command.2fa` | true | Completes a pending two-factor login. |
| `/pal2fa setup` | `/2fa setup` | `pal.command.2fa` | true | Starts TOTP setup for the authenticated account. |
| `/pal2fa confirm <code>` | `/2fa confirm <code>` | `pal.command.2fa` | true | Confirms TOTP setup. |
| `/pal2fa disable <code>` | `/2fa disable <code>` | `pal.command.2fa` | true | Disables two-factor authentication. |
| `/pal2fa recover <backup-code>` | `/2fa recover <backup-code>` | `pal.command.2fa` | true | Completes a pending two-factor login with a backup code. |

## Admin Command

The root admin command is `/pal`, with `/palauth` as an alias.

| Command | Permission | Default | Description |
| --- | --- | --- | --- |
| `/pal info` | `pal.admin.info` | op | Shows plugin information. |
| `/pal reload` | `pal.admin.reload` | op | Reloads configuration and dynamic commands. |
| `/pal help` | `pal.admin.help` | op | Shows available admin subcommands. |
| `/pal account <player>` | `pal.admin.account` | op | Shows stored account data. |
| `/pal session <player>` | `pal.admin.session` | op | Shows active session state. |
| `/pal session clear <player>` | `pal.admin.session` | op | Clears an active session. |
| `/pal force-login <player>` | `pal.admin.force-login` | op | Creates an authenticated session for an online player. |
| `/pal force-logout <player>` | `pal.admin.force-logout` | op | Invalidates a player's session. |
| `/pal reset-password <player> <password>` | `pal.admin.reset-password` | op | Sets a new password and invalidates sessions. |
| `/pal unregister <player>` | `pal.admin.unregister` | op | Removes a player's account registration. |
| `/pal migrate <provider>` | `pal.admin.migrate` | op | Runs one migration provider. |
| `/pal migrate all` | `pal.admin.migrate` | op | Runs all enabled migration providers. |

## Confirmation Commands

These commands are intentionally destructive or identity-changing and require `confirm` by default:

- `/unregister confirm`
- `/premium confirm`
- `/cracked confirm`

This behavior is controlled by `commands.<command>.require-confirmation`.

## Pre-Auth Command Allowlist

Before a player is authenticated, PAL only allows commands configured in `security.yml`:

```yaml
auth:
  pre-auth-restrictions:
    allowed-commands:
      - login
      - l
      - register
      - reg
      - pal2fa
      - 2fa
```

Add recovery or support commands here only if they are safe to run before login.
