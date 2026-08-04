# PAL Free Limitations

This page is intentionally explicit. PAL should be predictable for server owners, especially in mixed premium/offline networks.

## Premium Ownership

PAL Free does not treat a Mojang name lookup as proof of ownership.

Real premium auto-login requires one of:

- Bukkit `online-mode=true`.
- FastLogin verified the player.
- PAL proxy addon verified the login phase and published a trusted session.

Standalone offline-mode Bukkit cannot prove Java premium ownership by itself. In that environment, PAL Native can protect premium names and detect that a Mojang profile exists, but the player should still authenticate with a password unless FastLogin or proxy proof is present.

## Native Bukkit Protocol

The `protocol` module exists as the future home for login-phase adapters, but Bukkit alone does not expose every login handshake detail needed for secure ownership verification on offline-mode servers.

Free currently takes the secure path:

- Do not auto-login from name lookup alone.
- Prefer FastLogin when installed.
- Prefer proxy login-phase verification for networks.
- Treat Bukkit online-mode as verified Mojang proof.

## Redis vs Database Bridge

`DATABASE` is the default network bridge when the proxy and Bukkit side can share the same remote SQL database. `REDIS` remains available when a network needs real-time Pub/Sub session updates.

`DATABASE` works without Redis, but the proxy sees updates through polling. This is acceptable for networks that can share a private remote SQL database, but it is not as immediate as Redis. Production `DATABASE` mode should keep `bridge.sec.require: true` and a non-default `bridge.sec.secret`, because the proxy will reject unsigned SQL sessions when signed sessions are required.

`MEMORY` is local-only. It is useful for tests and proxy-local behavior, but it does not synchronize Bukkit auth-server sessions to other proxy processes.

PAL Free does not include a native socket/WebSocket relay bridge. If Redis is not available, use `DATABASE` mode with shared remote SQL.

## SQLite in Networks

SQLite is intended for single-server setups or controlled auth-server tests.

For production networks:

- Use MySQL, MariaDB or PostgreSQL.
- Do not point multiple servers at a random local SQLite file.
- If the proxy and Bukkit server do not share the same filesystem path, SQLite bridge mode cannot behave like shared storage.

PAL warns when bridge mode is enabled with SQLite and several configured servers.

## Two-Factor Authentication

PAL Free supports basic TOTP and backup codes.

Current limits:

- No GUI setup flow.
- No advanced recovery workflow.
- Backup codes are shown during setup and must be stored by the player.
- Admin recovery must be handled carefully by server staff.

PAL+ can expand this into GUI setup, richer recovery, alerts and account security dashboards.

## Bedrock Accounts

Floodgate-linked Bedrock players can be treated as the linked Java identity when Floodgate exposes that relationship.

Unlinked Bedrock players remain separate `BEDROCK` identities. PAL should not assume that a Bedrock name matching a Java premium account proves ownership of that Java account.

## GUI

PAL Free is command-first.

GUI account management, admin dashboards, visual setup flows and richer recovery screens are intended for PAL+.

## Migration Coverage

PAL supports configurable SQL imports for common auth plugins, but legacy plugins often changed schemas across versions.

Supported providers:

- AuthMe
- nLogin
- OpeNLogin
- LibreLogin
- LoginSecurity
- FastLogin

Limits:

- Custom legacy schemas may need column mapping changes in `storage.yml`.
- Unsupported password hashes may import but require password reset.
- Remote SQL backups are the server owner's responsibility.

## API Events

PAL exposes basic Bukkit events:

- `PALAccountCreateEvent`
- `PALAccountDeleteEvent`
- `PALSessionCreateEvent`
- `PALSessionInvalidateEvent`

This is enough for basic addons. More granular events, cancellable policy hooks and richer account-security events can be added later as the API stabilizes.

Proxy-side decisions are not exposed as Bukkit events. The proxy addon can publish bridge sessions, but Bukkit events only fire inside the Bukkit plugin process.

## PlaceholderAPI And LuckPerms

PlaceholderAPI output is session/cache-oriented. Heavy account lookups should not be treated as live database queries in placeholders.

LuckPerms integration registers runtime contexts only. It does not create permission groups, assign permissions or replace a server owner's LuckPerms policy.

## Backend Protection

PAL's backend guard is an extra runtime check, not a firewall replacement.

Production networks should still use firewall rules, proxy forwarding secrets and correct proxy configuration so players cannot connect directly to backend Bukkit servers.

## Version Testing

PAL targets legacy and modern Bukkit-compatible servers, including Folia through Takion's scheduler support. Before public release, test at least:

- Spigot/Paper 1.8.8.
- A modern Paper build.
- Folia.
- Velocity and BungeeCord proxy addons.
- SQLite single-server.
- Remote SQL network.
- FastLogin and Floodgate installed/missing states.
