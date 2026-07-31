# PAL Test Matrix

This is the minimum manual matrix before a public release.

## Bukkit/Paper/Folia

- Spigot/Paper 1.8.8:
  - server starts with PAL
  - `/register`, `/login`, `/changepassword`, optional `/logout`
  - pre-auth restrictions block movement, chat, commands and inventory
- Modern Paper:
  - same auth flow
  - FastLogin hook, if installed
  - Floodgate hook, if installed
- Folia:
  - startup with Takion GlobalScheduler
  - join/login/logout without region-thread errors

## Proxy

- Velocity with PAL proxy addon:
  - Redis bridge receives login/logout/password-change events
  - Database bridge reads shared sessions and sees invalidations through polling
  - Memory bridge stays local to the proxy and does not claim Bukkit sync
  - authenticated players return to original destination
  - unauthenticated players are sent to auth server
- BungeeCord with PAL proxy addon:
  - same bridge flow
  - direct backend access is blocked by server/network config

## Storage

- SQLite single-server
- MySQL or MariaDB network
- PostgreSQL network
- Migration import from each supported legacy provider using a copied database
