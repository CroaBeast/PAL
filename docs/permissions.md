# PAL Permissions

PAL does not rely on static `plugin.yml` permission children for command registration. Commands are configured through `commands.yml`, and CommandFramework applies each command permission and default value.

## General Behavior

- `default: true` allows everyone unless a permissions plugin denies it.
- `default: op` allows operators unless `options.permissions.override-op` is enabled in `config.yml`.
- `options.permissions.override-op: true` makes explicit permissions required even for OP players.

## Player Permissions

| Permission | Default | Used By |
| --- | --- | --- |
| `pal.command.login` | true | `/login`, `/l` |
| `pal.command.register` | true | `/register`, `/reg` |
| `pal.command.changepassword` | true | `/changepassword`, `/changepw`, `/cpw` |
| `pal.command.logout` | true | `/logout` |
| `pal.command.unregister` | true | `/unregister` |
| `pal.command.premium` | true | `/premium` |
| `pal.command.cracked` | true | `/cracked`, `/offline` |
| `pal.command.2fa` | true | `/pal2fa`, `/2fa` |

## Admin Permissions

| Permission | Default | Used By |
| --- | --- | --- |
| `pal.admin` | op | Root `/pal` command. |
| `pal.admin.info` | op | `/pal info` |
| `pal.admin.reload` | op | `/pal reload` |
| `pal.admin.help` | op | `/pal help` |
| `pal.admin.account` | op | `/pal account` |
| `pal.admin.session` | op | `/pal session` |
| `pal.admin.force-login` | op | `/pal force-login` |
| `pal.admin.force-logout` | op | `/pal force-logout` |
| `pal.admin.reset-password` | op | `/pal reset-password` |
| `pal.admin.unregister` | op | `/pal unregister` |
| `pal.admin.migrate` | op | `/pal migrate` |

## Two-Factor Setup Requirement

`security.yml` includes:

```yaml
auth:
  two-factor:
    required-permission: "pal.security.2fa.required"
```

This permission is the policy hook for servers that want to require 2FA for selected groups. The current Free flow supports TOTP setup and validation; server owners can grant this permission to staff or high-risk groups and enforce setup through their own policy flow.

## LuckPerms Contexts

When LuckPerms is installed and enabled in `integrations.yml`, PAL registers these contexts:

| Context | Values |
| --- | --- |
| `pal:authenticated` | `true`, `false` |
| `pal:session_source` | `none`, `command`, `premium`, `admin`, `api` |
| `pal:2fa_pending` | `true`, `false` |
| `pal:bridge_mode` | `disabled`, `database`, `memory`, `redis` |
| `pal:storage_type` | `sqlite`, `mysql`, `mariadb`, `postgresql` |

Example:

```text
lp group default permission set server.command.spawn true pal:authenticated=true
```
