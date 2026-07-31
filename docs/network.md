# PAL Network Setup

PAL supports a Bukkit auth server plus a proxy addon through Redis or a shared database bridge.

## Recommended topology

- Put `PAL.jar` on the auth/lobby Bukkit server.
- Put the PAL proxy addon on Velocity or BungeeCord.
- Use shared remote SQL for accounts on production networks.
- Use Redis for signed session state and Pub/Sub events when possible.
- Use `DATABASE` mode when Redis is not available and the proxy can access the same remote SQL database.
- Block direct access to backend servers with firewall rules or proxy forwarding secrets.

SQLite can work for a small auth-server-only setup, but PAL warns when bridge mode is enabled with several configured servers. For networks with multiple backends, remote SQL plus Redis is the recommended baseline. `DATABASE` mode should use MySQL, MariaDB or PostgreSQL; SQLite is local-file only unless the proxy and auth server intentionally share the same filesystem path.

## Bridge config

On Bukkit, configure `bridge.yml`:

```yaml
bridge:
  enabled: true
  mode: REDIS # REDIS, DATABASE, MEMORY, DISABLED
  net:
    auth: auth
    lobby: lobby
    servers: 4
  guard:
    strict: true
    required: true
    premium: true
    bedrock: true
    proxy-auto-login: false
  fail:
    enabled: true
    keep: true
    rejoin: true
    new-login: false
    fallback: limbo
  sec:
    require: true
    secret: "replace-this-with-a-long-random-secret"
  redis:
    uri: "redis://localhost:6379/0"
```

The same `bridge.sec.secret`, Redis URI, prefix and channel must be configured in the proxy addon.

For `DATABASE` mode, Bukkit keeps using `storage.yml`; the proxy addon must configure the same database in its generated `bridge.yml`:

```yaml
bridge:
  enabled: true
  mode: DATABASE
  database:
    type: MYSQL
    table-prefix: pal_
    poll: 3
    remote:
      host: localhost
      port: 3306
      database: pal
      username: username
      password: password
      ssl: true
```

`MEMORY` mode stores sessions only inside the current proxy process. It is useful for isolated tests or proxy-only premium auto-login, but it does not synchronize Bukkit sessions to the proxy.

`DATABASE` mode stores a signed `bridge_payload` beside each SQL session when
`bridge.sec.secret` is set. Keep `bridge.sec.require: true` on production
networks so the proxy rejects unsigned or tampered SQL sessions. If Redis is not
available, run MySQL/MariaDB/PostgreSQL on a private network, restrict the proxy
database user to the PAL database, and use a long random shared secret.

## Login flow

1. Player connects to the proxy.
2. Proxy detects FastLogin/Floodgate/PAL native identity when available.
3. If a valid signed PAL session exists, the proxy can send the player to the target backend.
4. If no session exists, the proxy sends the player to the auth server.
5. Bukkit PAL authenticates the player and saves the session.
6. In `REDIS` mode, Bukkit publishes a signed session event immediately.
7. In `DATABASE` mode, the proxy sees the signed saved session on its next poll.
8. Proxy can move the player to the original destination.

`bridge.guard.proxy-auto-login` is intentionally `false` by default. With that
setting, verified premium and Floodgate players still pass through the auth
server once so Bukkit PAL creates the persistent account and signed session.
Enable it only if you intentionally want the proxy to create temporary verified
sessions before the Bukkit auth server has seen the account.

## Auth Server Failure

`bridge.fail` controls what happens if the auth server is down:

- `keep`: lets already-authenticated players remain online if their session is still valid.
- `rejoin`: allows rejoin with an existing bridge session.
- `new-login`: should stay `false` unless you intentionally allow new unauthenticated logins without the auth server.
- `fallback`: proxy server used when auth is unavailable.

## Backend Failure Redirects

PAL proxy addons listen for backend kick/failure events. When a player is kicked
from a backend, PAL first validates the configured fallback server against the
current auth session. If the session is still valid, the player is redirected to
fallback and remains authenticated. If the session is missing or expired, PAL
redirects to the auth server instead. PAL will not redirect back to the server
that just kicked the player, which avoids loops when fallback or auth is the
failed backend.

If auth is unavailable and `bridge.fail.new-login` is `false`, unauthenticated
players are not sent to fallback. Set `new-login: true` only when you
intentionally accept unauthenticated players during auth outages.

## Shared Auth/Limbo/Lobby Backend

The proxy addons now resolve missing logical servers in this order:

- auth redirects use `auth`, then `fallback`, then `lobby`.
- fallback/limbo redirects use `fallback`, then `lobby`.

This lets a small network register only the main `lobby` server and still use it
as the physical auth or limbo backend. When a proxy sends a player to a backend,
it also sends a signed `pal:realm` plugin message with one of `AUTH`, `LOBBY` or
`LIMBO`. Bukkit PAL verifies that message with `bridge.sec.secret`; if the
secret is missing or still `change-me`, Bukkit ignores the message and falls
back to `auth.realms.default`.

Configure the three Bukkit profiles in `security.yml`:

```yaml
auth:
  realms:
    default: AUTO
    proxy-message:
      enabled: true
    auth:
      spawn:
        enabled: true
        world: world
        x: 0.5
        y: 64.0
        z: 0.5
      effects:
        - BLINDNESS:1
      restrictions:
        movement: true
        commands: true
    lobby:
      spawn:
        enabled: true
        world: world
        x: 20.5
        y: 64.0
        z: 20.5
      effects: []
      restrictions:
        movement: false
        commands: false
    limbo:
      spawn:
        enabled: true
        world: world
        x: -20.5
        y: 64.0
        z: -20.5
      effects: []
      restrictions:
        movement: true
        inventory-click: true
```

For one physical backend, point `bridge.net.auth`, `bridge.fail.fallback` and
`bridge.net.lobby` at the same registered proxy server name or leave `auth` and
`limbo` unregistered. In both cases, keep backend access firewalled to the proxy
and use the same long random `bridge.sec.secret` on Bukkit and proxy.
