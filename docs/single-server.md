# PAL Single Server Setup

PAL can run as a normal Bukkit/Paper/Folia authentication plugin on one server.

## Recommended files

- `plugins/PAL/config.yml`: general options and message prefix.
- `plugins/PAL/commands.yml`: command registration through CommandFramework.
- `plugins/PAL/security.yml`: auth restrictions, password policy, rate limit and lockout.
- `plugins/PAL/storage.yml`: SQLite or remote SQL storage.
- `plugins/PAL/premium.yml`: FastLogin, Floodgate and PAL native premium behavior.

## Basic setup

1. Install `PAL.jar` in the Bukkit/Paper/Folia server.
2. Start the server once so PAL creates its config files.
3. Keep `storage.type: SQLITE` for small single-server setups.
4. Configure `premium.yml`:
   - Keep `premium.fast-login.enabled: true` if FastLogin is installed.
   - Keep `premium.bedrock.floodgate.enabled: true` if Floodgate/Geyser is installed.
   - Use PAL native premium mode only with verified session proof.
5. Adjust `security.yml` and `commands.yml` for the login flow you want.

## Bukkit premium auto-login

PAL can auto-login premium players on Bukkit when the connection has already been verified by Mojang. That means:

- Bukkit `online-mode=true`, or
- a proxy/login protocol layer proves ownership and passes the verified decision to PAL.

On offline-mode standalone Bukkit, a name lookup is not enough to prove ownership. In PAL Free this is a hard rule: PAL Native can protect premium names from offline users, but automatic premium login needs online-mode, FastLogin, or the proxy addon login phase.

## Integrations

PlaceholderAPI registers `%pal_*%` placeholders for session state, bridge mode, storage type and integration availability. LuckPerms registers contexts such as `pal:authenticated`, `pal:session_source` and `pal:2fa_pending` for permission rules.

## Bukkit API events

PAL exposes Bukkit events for addon authors: `PALAccountCreateEvent`, `PALAccountDeleteEvent`, `PALSessionCreateEvent` and `PALSessionInvalidateEvent`.

## Admin commands

- `/pal account <player>`
- `/pal session <player>`
- `/pal session clear <player>`
- `/pal force-login <player>`
- `/pal force-logout <player>`
- `/pal reset-password <player> <password>`
- `/pal unregister <player>`
- `/pal migrate <provider|all>`

All admin actions are written to the PAL audit table.
