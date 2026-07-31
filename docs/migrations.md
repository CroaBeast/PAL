# PAL Migrations

PAL Free includes SQL-based importers for common authentication plugins. Migrations run from the Bukkit server because Bukkit PAL owns account storage.

```text
/pal migrate <provider>
/pal migrate all
```

## Supported Providers

Provider settings live in `storage.yml` under `migrations.providers`.

| Provider | Command Name | Default Source | Default Table | Imported Type |
| --- | --- | --- | --- | --- |
| AuthMe | `authme` | `plugins/AuthMe/authme.db` | `authme` | `OFFLINE` |
| nLogin | `nlogin` | `plugins/nLogin/data.db` | `nlogin` | `OFFLINE` |
| OpeNLogin | `openlogin` | `plugins/OpeNLogin/database.db` | `openlogin` | `OFFLINE` |
| LibreLogin | `librelogin` | `plugins/LibreLogin/accounts.db` | `librelogin_accounts` | `OFFLINE` |
| LoginSecurity | `loginsecurity` | `plugins/LoginSecurity/database.db` | `ls_players` | `OFFLINE` |
| FastLogin | `fastlogin` | `plugins/FastLogin/fastlogin.db` | `fastlogin` | `PREMIUM` |

Source databases can be `SQLITE`, `MYSQL`, `MARIADB` or `POSTGRESQL`. Column names are configurable because legacy plugins often changed schemas between versions.

## Recommended Workflow

1. Stop the server or make sure the legacy auth plugin is not writing while importing.
2. Back up the current PAL database.
3. Copy the source legacy database into a stable path if it is SQLite.
4. Configure one provider in `storage.yml`.
5. Run `/pal migrate <provider>` for that provider only.
6. Inspect one imported account with `/pal account <player>`.
7. Test login with a migrated account.
8. Run `/pal migrate all` only after single-provider imports are confirmed.

For production networks, migrate into remote SQL first, then configure the proxy bridge against the same database or Redis-backed sessions.

## Provider Config

Example:

```yaml
migrations:
  enabled: true
  backup-before-import: true
  providers:
    authme:
      enabled: true
      source:
        type: SQLITE
        file: plugins/AuthMe/authme.db
      table: authme
      account-type: OFFLINE
      columns:
        name: username
        password: password
        address: ip
        registered-at: regdate
        last-login-at: lastlogin
```

Required fields:

| Key | Description |
| --- | --- |
| `source.type` | Source database type: `SQLITE`, `MYSQL`, `MARIADB` or `POSTGRESQL`. |
| `source.file` | SQLite file path, when source type is `SQLITE`. |
| `source.remote.*` | Remote SQL connection settings, when source type is not SQLite. |
| `table` | Legacy account table. |
| `account-type` | PAL account type used when the source does not provide a reliable premium flag. |
| `columns.name` | Legacy username column. |
| `columns.uuid` | Optional UUID column. |
| `columns.password` | Optional password hash column. |
| `columns.premium` | Optional premium flag column. |
| `columns.address` | Optional last IP/address column. |
| `columns.registered-at` | Optional registration timestamp column. |
| `columns.last-login-at` | Optional last-login timestamp column. |

## Password Compatibility

PAL imports legacy passwords as `LEGACY_<PROVIDER>` credentials. On the next successful login, PAL rehashes the password to its PBKDF2 format when `auth.passwords.rehash-on-login` is enabled.

Currently supported legacy checks:

- AuthMe `$SHA$salt$hash`.
- BCrypt hashes beginning with `$2a$`, `$2b` or `$2y$`.
- Simple MD5 or SHA-256 hex hashes for older installs.

Unsupported legacy formats can still be imported, but users may need `/pal reset-password <player> <password>` if the original plugin used a custom algorithm.

## FastLogin Imports

The FastLogin importer can import known premium records, but it does not replace premium ownership proof.

After migration, premium auto-login still requires one of:

- Bukkit `online-mode=true`.
- FastLogin verifying the player at login time.
- PAL proxy addon verifying the login phase and publishing a trusted session.

## Rollback

SQLite PAL storage is copied to `plugins/PAL/backups/` when `migrations.backup-before-import` is enabled. To roll back, stop the server and restore the backup database file before starting PAL again.

Remote SQL rollback should be handled through the database host or server tooling. Take a database dump before importing.

## Safety Notes

- Run one provider first, then `all`.
- Do not run migrations while the source plugin is still mutating the same database.
- Keep source plugin jars disabled after a completed migration to avoid two auth plugins fighting over login flow.
- Preserve the source database until users have successfully logged in and PAL has rehashed legacy passwords.
- If a provider has a custom schema, adjust the `columns` section before importing.
