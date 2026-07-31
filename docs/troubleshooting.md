# PAL Troubleshooting

This page lists the most common setup problems and the checks to run before reporting an issue.

## Server Starts, But Commands Do Not Work

Check:

- `commands.yml` has the command enabled.
- The command permission is granted.
- `options.permissions.override-op` in `config.yml` is not unexpectedly forcing explicit permissions for OPs.
- No other plugin owns the same command name, unless `override-existing: true` is set.

Useful commands:

```text
/pal info
/pal reload
```

## Players Cannot Move, Chat or Use Commands

This usually means the player is still in pre-auth.

Check:

- The account exists: `/pal account <player>`.
- The session exists: `/pal session <player>`.
- The command being used before login is listed in `security.yml` under `auth.pre-auth-restrictions.allowed-commands`.
- If 2FA is enabled, the player may need `/pal2fa <code>`.

## Players Are Kicked For Timeout

Relevant config:

```yaml
auth:
  login:
    timeout-seconds: 90
    kick-on-timeout: true
```

Increase `timeout-seconds` for slower networks or set `kick-on-timeout: false` while testing.

## Premium Auto-Login Does Not Happen

PAL Free only auto-logins premium users when ownership is verified.

Valid proof sources:

- Bukkit `online-mode=true`.
- FastLogin verified the player.
- PAL proxy addon verified the login phase and published a verified bridge session.

Not valid proof:

- Mojang name lookup alone.
- Standalone offline-mode Bukkit without FastLogin or proxy login proof.

If the server is standalone offline-mode, PAL can protect premium names but should still require password login.

## FastLogin Is Installed, But PAL Does Not Use It

Check:

- FastLogin is enabled on the same platform where the login decision happens.
- `integrations.fastlogin.enabled: true`.
- `premium.fast-login.enabled: true`.
- The server/proxy logs do not show a FastLogin API loading warning.

PAL hooks FastLogin through isolated adapters. If the FastLogin plugin is installed but its API is incompatible, PAL should keep running and log the hook failure.

## Floodgate/Geyser Bedrock Detection Fails

Check:

- Floodgate is installed and enabled.
- `integrations.floodgate.enabled: true`.
- `premium.bedrock.floodgate.enabled: true`.
- The player UUID/name is the one Floodgate exposes.

Bedrock accounts are treated as separate identities unless Floodgate reports a linked Java identity.

## Two-Factor Codes Are Invalid

Check:

- `auth.two-factor.enabled: true`.
- The player's phone/server clocks are reasonably synchronized.
- `auth.two-factor.period-seconds` is normally `30`.
- `auth.two-factor.window` can be increased while testing clock drift.
- Backup codes are single-use and are stored hashed.

If a player loses all codes, use an admin password/session reset flow and remove 2FA from storage only after verifying ownership.

## Redis Bridge Does Not Sync Sessions

Check:

- Bukkit and proxy use `bridge.mode: REDIS`.
- Bukkit and proxy use the same `bridge.sec.secret`.
- `bridge.sec.secret` is not `change-me`.
- Redis URI, prefix and channel match.
- Firewall allows both Bukkit and proxy to reach Redis.

PAL logs a warning when Redis mode uses the default secret.

## DATABASE Bridge Does Not Sync Sessions

Check:

- Bukkit account storage uses remote SQL, not local-only SQLite.
- Proxy `bridge.database` points to the same remote SQL database and table prefix.
- `bridge.database.poll` is not too high.
- The proxy database credentials are not placeholder values.

SQLite is local-file storage. It should not be used for a normal multi-backend network unless the proxy and auth server intentionally share the same filesystem path.

## Direct Backend Access Is Still Possible

PAL's backend guard is an extra check, not a firewall replacement.

Recommended:

- Use proxy forwarding secrets.
- Firewall backend ports so only the proxy can connect.
- Enable `bridge.backend.guard` only after `bridge.backend.proxy-ips` is correct.

## PlaceholderAPI Placeholders Do Not Resolve

Check:

- PlaceholderAPI is installed and enabled.
- `integrations.placeholderapi.enabled: true`.
- `integrations.placeholderapi.register-expansion: true`.
- Use placeholders with the `pal` identifier, for example `%pal_authenticated%`.

Supported examples:

- `%pal_authenticated%`
- `%pal_session_source%`
- `%pal_session_expires%`
- `%pal_bridge_mode%`
- `%pal_storage_type%`
- `%pal_fastlogin_available%`
- `%pal_floodgate_available%`

## LuckPerms Contexts Do Not Apply

Check:

- LuckPerms is installed and enabled.
- `integrations.luckperms.enabled: true`.
- `integrations.luckperms.check-contexts: true`.
- Context names use the `pal:` prefix.

Examples:

```text
pal:authenticated=true
pal:session_source=premium
pal:2fa_pending=false
```

## Migration Imports Fail

Check:

- The provider is enabled under `storage.yml -> migrations.providers`.
- The source file exists if using SQLite.
- Remote source credentials are correct if using MySQL/MariaDB/PostgreSQL.
- Column names match the legacy plugin database schema.
- Unsupported legacy hashes may import but fail verification until the password is reset.

Run one provider first:

```text
/pal migrate authme
```

Then run all providers only after a successful backup/test import:

```text
/pal migrate all
```
