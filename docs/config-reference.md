# PAL Config Reference

PAL creates its config files under `plugins/PAL/` on first startup. The defaults are stored in `plugin/src/main/resources/`.

## `config.yml`

General plugin behavior and message formatting.

| Key | Default | Description |
| --- | --- | --- |
| `options.update-check` | `true` | Enables update checks when implemented. |
| `options.console.colored` | `true` | Enables colored console output. |
| `options.console.show-message-type` | `true` | Keeps message type prefixes in console output. |
| `options.console.debug` | `false` | Reserved for debug output. |
| `options.permissions.override-op` | `false` | When true, OP players still need explicit PAL permissions. |
| `messages.prefix-key` | `<P>` | Token replaced by the PAL prefix in messages. |
| `messages.prefix` | ` &b&lPAL &8>&7` | Prefix inserted into messages. |
| `messages.center-prefix` | `<C>` | Reserved center token. |
| `messages.line-separator` | `<n>` | Token replaced with a line break. |
| `startup.log-platform` | `true` | Reserved startup logging option. |
| `startup.log-integrations` | `true` | Reserved startup integration logging option. |

## `commands.yml`

Dynamic command registration through CommandFramework.

Common keys per command:

| Key | Description |
| --- | --- |
| `name` | Registered Bukkit command name. |
| `enabled` | Enables or disables the command. |
| `override-existing` | Allows PAL to override an existing command name. |
| `permission` | Permission checked by CommandFramework. |
| `default` | Permission default: `true`, `op`, `not-op` or false-like fallback. |
| `description` | Command description. |
| `aliases` | Command aliases. |
| `usage` | Usage shown by PAL. |
| `permission-message` | Message shown when permission is denied. |
| `require-confirmation` | Used by destructive identity commands such as `/unregister`, `/premium` and `/cracked`. |

The admin command uses `commands.pal.subcommands` for `/pal` subcommands.

## `messages.yml`

All visible PAL command and auth messages should be defined here.

Supported tokens:

| Token | Description |
| --- | --- |
| `<P>` | Replaced with `messages.prefix` from `config.yml`. |
| `<n>` | Line break. |
| `{placeholder}` | Runtime placeholder, depending on the message. |
| `&` color codes | Legacy Bukkit color formatting. |

## `security.yml`

Authentication policy, session policy, restrictions, brute-force protection, captcha and two-factor authentication.

### Registration

| Key | Default | Description |
| --- | --- | --- |
| `auth.registration.enabled` | `true` | Allows new account registration. |
| `auth.registration.require-confirmation` | `true` | Requires repeated password on `/register`. |
| `auth.registration.max-accounts-per-ip` | `3` | Reserved account-per-IP cap. |
| `auth.registration.allowed-name-pattern` | `^[A-Za-z0-9_]{3,16}$` | Allowed username regex. |

### Login

| Key | Default | Description |
| --- | --- | --- |
| `auth.login.timeout-seconds` | `90` | Time before unauthenticated players are kicked or warned. |
| `auth.login.max-attempts` | `5` | Max failed attempts before lockout. |
| `auth.login.kick-on-timeout` | `true` | Kicks on auth timeout. |
| `auth.login.hide-location-before-auth` | `true` | Reserved pre-auth privacy option. |
| `auth.login.hide-inventory-before-auth` | `true` | Reserved pre-auth privacy option. |

### Passwords

| Key | Default | Description |
| --- | --- | --- |
| `auth.passwords.algorithm` | `PBKDF2_SHA256` | Current PAL password hashing algorithm. |
| `auth.passwords.min-length` | `6` | Minimum password length. |
| `auth.passwords.max-length` | `64` | Maximum password length. |
| `auth.passwords.block-username` | `true` | Blocks passwords containing the username. |
| `auth.passwords.block-common-passwords` | `true` | Blocks common passwords. |
| `auth.passwords.require-repeat-on-register` | `true` | Requires password confirmation. |
| `auth.passwords.rehash-on-login` | `true` | Rehashes legacy or outdated hashes after successful login. |

### Sessions

| Key | Default | Description |
| --- | --- | --- |
| `auth.sessions.enabled` | `true` | Persists sessions. |
| `auth.sessions.duration-minutes` | `30` | Session lifetime. |
| `auth.sessions.bind-ip` | `true` | Binds sessions to IP hash. |
| `auth.sessions.bind-username` | `true` | Binds sessions to username. |
| `auth.sessions.invalidate-on-password-change` | `true` | Invalidates sessions after password change. |

### Pre-Auth Restrictions

`auth.pre-auth-restrictions` controls blocked actions before login:

- `movement`
- `chat`
- `commands`
- `inventory-click`
- `item-drop`
- `item-pickup`
- `block-place`
- `block-break`
- `damage`
- `teleport`
- `allowed-commands`

### Brute Force

| Key | Default | Description |
| --- | --- | --- |
| `auth.brute-force.enabled` | `true` | Enables throttling/lockout logic. |
| `auth.brute-force.window-seconds` | `300` | Failure tracking window. |
| `auth.brute-force.lockout-seconds` | `300` | Lockout duration. |
| `auth.brute-force.ip-throttle-ms` | `750` | Minimum delay per IP. |
| `auth.brute-force.username-throttle-ms` | `750` | Minimum delay per username. |
| `auth.brute-force.notify-admins` | `true` | Reserved notification option. |

### Two-Factor

| Key | Default | Description |
| --- | --- | --- |
| `auth.two-factor.enabled` | `false` | Enables PAL TOTP support. |
| `auth.two-factor.totp` | `true` | Enables TOTP codes. |
| `auth.two-factor.backup-codes` | `true` | Enables backup recovery codes. |
| `auth.two-factor.required-permission` | `pal.security.2fa.required` | Policy permission for required 2FA. |
| `auth.two-factor.issuer` | `PAL` | Issuer shown by authenticator apps. |
| `auth.two-factor.digits` | `6` | TOTP code length. |
| `auth.two-factor.period-seconds` | `30` | TOTP time period. |
| `auth.two-factor.window` | `1` | Allowed time-window drift. |
| `auth.two-factor.backup-code-amount` | `8` | Backup codes generated during setup. |
| `auth.two-factor.max-attempts` | `5` | Failed 2FA attempts before the pending challenge is dropped. |

## `storage.yml`

PAL storage and migration sources.

| Key | Default | Description |
| --- | --- | --- |
| `storage.type` | `SQLITE` | `SQLITE`, `MYSQL`, `MARIADB` or `POSTGRESQL`. |
| `storage.table-prefix` | `pal_` | SQL table prefix. |
| `storage.sqlite.file` | `plugins/PAL/database.db` | SQLite database path. |
| `storage.remote.*` | varies | Host, port, database, username, password and SSL for remote SQL. |
| `cache.accounts.*` | enabled | Reserved account cache settings. |
| `cache.sessions.*` | enabled | Reserved session cache settings. |
| `autosave.*` | enabled | Reserved autosave settings. |
| `migrations.*` | enabled | Migration provider configuration. |

Use remote SQL for networks. SQLite is intended for single-server installs or isolated testing.

## `premium.yml`

Premium/offline/Bedrock identity behavior.

| Key | Default | Description |
| --- | --- | --- |
| `premium.enabled` | `true` | Enables premium identity handling. |
| `premium.mode` | `HYBRID` | `HYBRID`, `PREMIUM_ONLY` or `OFFLINE_ONLY`. |
| `premium.fast-login.enabled` | `true` | Uses FastLogin decisions when installed. |
| `premium.fast-login.required` | `false` | Makes FastLogin required for premium decisions. |
| `premium.pal-resolver.enabled` | `true` | Enables PAL native profile lookup/name protection. |
| `premium.pal-resolver.trust-name-lookup` | `false` | Must stay false for secure ownership handling. |
| `premium.pal-resolver.bukkit-online-mode-proof` | `true` | Treats Bukkit online-mode as verified Mojang proof. |
| `premium.protocol.enabled` | `true` | Enables protocol/login-phase integration when available. |
| `premium.premium-accounts.auto-login` | `true` | Auto-login verified premium users. |
| `premium.premium-accounts.upgrade-offline-on-verified-login` | `true` | Converts offline accounts to premium after verified login. |
| `premium.offline-accounts.enabled` | `true` | Allows offline accounts. |
| `premium.bedrock.floodgate.enabled` | `true` | Enables Floodgate Bedrock detection. |

Important: standalone offline-mode Bukkit cannot prove Java premium ownership. Use Bukkit online-mode, FastLogin or the PAL proxy addon for real ownership proof.

## `bridge.yml`

Bukkit/proxy session bridge configuration.

| Key | Default | Description |
| --- | --- | --- |
| `bridge.enabled` | `false` on Bukkit | Enables bridge publishing. |
| `bridge.mode` | `DATABASE` | `DATABASE`, `REDIS`, `MEMORY` or `DISABLED`. |
| `bridge.net.auth` | `auth` | Auth server name. |
| `bridge.net.lobby` | `lobby` | Lobby server name. |
| `bridge.net.remember` | `true` | Remembers original destination. |
| `bridge.net.servers` | `1` | Manual network-size hint for warnings. |
| `bridge.guard.*` | enabled | Proxy guard behavior. |
| `bridge.session.minutes` | `30` | Proxy session lifetime hint. |
| `bridge.native.*` | enabled | Proxy native premium behavior. |
| `bridge.fail.*` | enabled | Auth-server-down behavior. |
| `bridge.sec.require` | `true` | Requires shared secret for signed bridge payloads. |
| `bridge.sec.secret` | `change-me` | Shared Bukkit/proxy secret. Change before production. |
| `bridge.sec.hash-ip` | `true` | Hashes player IP data before bridge/session storage. |
| `bridge.sec.bind-ip` | `false` | Binds bridge sessions to the same address hash. |
| `bridge.sec.skew` | `15` | Accepted timestamp skew for signed bridge payloads. |
| `bridge.advice.sqlite` | `true` | Enables warnings when SQLite is used in network-like setups. |
| `bridge.advice.sqlite-cap` | `4` | Minimum configured server count that triggers the SQLite network warning. |
| `bridge.redis.*` | local Redis defaults | Redis connection and channel settings used only in REDIS mode. |
| `bridge.database.poll` | `3` | Proxy polling interval in DATABASE mode. |
| `bridge.backend.guard` | `false` | Bukkit-side direct-backend guard. |
| `bridge.backend.proxy-ips` | localhost | Allowed proxy addresses when backend guard is enabled. |
| `bridge.plugin.channel` | `pal:bridge` | Plugin messaging channel reserved for future bridge handshakes. |

Old `bridge.failover.*` and `bridge.sec.require-secret` keys are read as compatibility fallbacks, but new configs should use `bridge.fail.*` and `bridge.sec.require`.

## `integrations.yml`

Optional plugin integrations.

| Section | Description |
| --- | --- |
| `integrations.placeholderapi` | Registers `%pal_*%` placeholders. |
| `integrations.luckperms` | Registers PAL LuckPerms contexts. |
| `integrations.fastlogin` | Enables FastLogin identity priority. |
| `integrations.floodgate` | Enables Bedrock identity checks. |
| `integrations.geyser` | Detects Geyser availability. |
| `integrations.importers.*` | Enables legacy migration provider detection. |
