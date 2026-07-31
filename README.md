# PAL - Player Advanced Login

PAL is a Bukkit/Paper/Folia authentication plugin with optional proxy support for Velocity and BungeeCord networks.

The free edition focuses on the core auth stack:

- Offline account registration and login.
- Premium account handling through Bukkit online-mode, FastLogin or the PAL proxy addon.
- Bedrock identity handling through Floodgate.
- TOTP two-factor authentication with backup codes.
- Pre-auth restrictions for movement, chat, commands, inventory, damage and block actions.
- SQLite, MySQL, MariaDB and PostgreSQL storage.
- Redis or shared database bridge for network sessions.
- Importers for AuthMe, nLogin, OpeNLogin, LibreLogin, LoginSecurity and FastLogin.
- PlaceholderAPI expansion, LuckPerms contexts and Bukkit API events.

## Requirements

- Java 8 bytecode target.
- Bukkit-compatible server, including Spigot, Paper and Folia forks.
- Optional proxy addon for Velocity or BungeeCord networks.
- Optional integrations:
  - FastLogin for premium ownership decisions.
  - Floodgate/Geyser for Bedrock identity detection.
  - PlaceholderAPI for `%pal_*%` placeholders.
  - LuckPerms for PAL contexts.

## Quick Start

Single server:

1. Put `PAL-<version>.jar` in the Bukkit/Paper/Folia `plugins` folder.
2. Start the server once.
3. Review `plugins/PAL/security.yml`, `storage.yml`, `premium.yml` and `commands.yml`.
4. Keep `storage.type: SQLITE` for small single-server installs.
5. Use `/register <password> <password>` and `/login <password>` to test the basic flow.

Network:

1. Put PAL on the auth/lobby Bukkit server.
2. Put the PAL proxy addon on Velocity or BungeeCord.
3. Use remote SQL for accounts and sessions.
4. Use `bridge.mode: REDIS` when Redis is available, or `DATABASE` when the proxy can access the same remote SQL database.
5. Set the same `bridge.sec.secret` on Bukkit and proxy.
6. Block direct backend access with firewall rules, proxy forwarding secrets, or `bridge.backend.guard`.

## Beta Status

PAL is currently suitable for closed beta and local staging. A public beta should only be opened after the unverified items below are tested on the target network stack.

### Main Features

- Offline/cracked account registration and password login.
- Java premium auto-login through verified proxy identity.
- Bedrock auto-login through Geyser and Floodgate.
- Hybrid premium/cracked handling for offline-mode networks.
- Session sync between Bukkit auth and proxy through Redis or shared SQL.
- Shared SQL storage for accounts, passwords, sessions and audit data.
- Pre-auth realms for auth, lobby and limbo style behavior.
- Separate auth, lobby and limbo spawn/restriction behavior on Bukkit.
- Configurable proxy behavior when authenticated players manually enter the auth server.
- Configurable user-facing messages for auth and proxy guard flows.
- Velocity and BungeeCord proxy addons.
- ViaVersion/ViaBackwards compatible local test network.
- Admin session commands including force-login, force-logout, password reset and unregister.
- Premium/Bedrock force-logout revalidation by reconnect.
- Direct backend access protection support through proxy/server forwarding configuration.

### Verified In Local Staging

- Velocity proxy starts with PAL proxy, Geyser and Floodgate.
- Purpur auth, lobby and survival backends start with PAL, ViaVersion and ViaBackwards where configured.
- Offline/cracked players are redirected to auth, can register, can authenticate and can reach protected servers.
- Already-authenticated players are not forced to re-login when moving between lobby/survival.
- Premium Java players can be verified by the proxy and auto-logged without using `/login`.
- Proxy-created premium sessions are persisted into SQL before being used by Bukkit.
- Bukkit PAL can read DATABASE bridge sessions written by the proxy.
- Name-conflict protection prevents a verified premium session from claiming an existing offline account with the same name but a different UUID.
- Backend outage handling can redirect to a configured fallback/auth target instead of leaving the player stuck on a dead backend.
- `/server auth` behavior is configurable with `bridge.guard.authenticated-auth-target`.
- Auth/lobby/limbo logical realms can point to the same Bukkit backend and still apply different realm behavior.
- User-facing auth/proxy messages can be edited in YAML config.
- Force-logout keeps offline/cracked behavior as manual re-auth, while premium/Bedrock accounts are kicked to reconnect and revalidate.
- Direct connection to a Velocity-forwarded Purpur backend is blocked by the server/proxy forwarding layer.

### Not Yet Public-Beta Verified

- Redis bridge in a live multi-process setup, including Pub/Sub invalidation and reconnect behavior.
- MariaDB/MySQL bridge in the local test network after migrating away from SQLite.
- PostgreSQL storage and bridge behavior.
- BungeeCord proxy addon behavior with equivalent auth, redirect, failover and Bedrock flows.
- BungeeCord backend spoofing protection with firewall and token/secret based guard.
- Folia runtime behavior beyond compilation-level support.
- Older Minecraft server versions listed in the test matrix.
- FastLogin integration in a real proxy/server stack.
- Two-factor authentication setup, login, backup-code recovery and force-login interactions.
- Migration importers against real copied AuthMe, nLogin, OpeNLogin, LibreLogin, LoginSecurity and FastLogin databases.
- Bedrock account linking behavior when a Floodgate player links to a Java account.
- Bedrock reconnect, server switching and fallback behavior under Redis mode.
- Session expiration after the configured timeout in Redis and remote SQL modes.
- Password change invalidation across proxy and multiple Bukkit backends.
- Reset-password and unregister flows for online premium, offline and Bedrock accounts.
- Auth server downtime during first join, during login and during proxy auto-login.
- Database outage and recovery while players are online.
- Redis outage and recovery while players are online.
- Proxy restart while authenticated players are online.
- Multi-player concurrency, especially simultaneous login/register attempts using similar names.
- Rate-limit and brute-force behavior under sustained failed login attempts.
- IP binding behavior with real client IP forwarding, Cloudflare Spectrum or external VPS tunneling.
- Floodgate key rotation and reload/restart order across proxy and backends.
- Production logging review to ensure no secrets, passwords or session payloads are printed.
- Backup and restore process for SQL data and PAL configs.
- Long-running memory and CPU behavior under beta traffic.

## Documentation

- [Single Server Setup](docs/single-server.md)
- [Network Setup](docs/network.md)
- [Commands](docs/commands.md)
- [Permissions](docs/permissions.md)
- [Config Reference](docs/config-reference.md)
- [Migrations](docs/migrations.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Limitations](docs/limitations.md)
- [Testing Matrix](docs/testing.md)

## Premium Ownership Rule

PAL Free never treats a Mojang name lookup as ownership proof. Premium auto-login is considered real only when one of these sources verifies the connection:

- Bukkit `online-mode=true`.
- FastLogin verified the player.
- The PAL proxy addon handled the login phase and published a verified session.

Standalone offline-mode Bukkit can protect premium names, but it cannot prove ownership by itself.

## Build

```bash
./gradlew clean build
```

The Bukkit jar is generated under:

```text
plugin/build/libs/PAL-<version>.jar
```
