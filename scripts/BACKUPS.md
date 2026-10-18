# Database backups and restore

Nightly `pg_dump` backups of the `finance_tracker` database (deploy hardening, ADR-016 phase).

## How it works

The `backup` compose service (`postgres:16-alpine` running [`backup.sh`](./backup.sh)) takes one dump on
start and then one every 24 hours, writing gzipped SQL to `./backups/finance_tracker-YYYYMMDD-HHMMSS.sql.gz`
and deleting dumps older than `BACKUP_KEEP_DAYS` (default 14). It starts with the `app` or `all` compose
profile:

```bash
docker compose --profile app up -d
```

Dumps are created with `pg_dump --clean --if-exists`, so a restore drops and recreates every object.

**Off-site copies are a deployment concern** — sync `./backups` to object storage or another host from the
host's own scheduler; this service only keeps the local 14-day window.

## Restore

Restoring overwrites the current database.

```bash
docker compose run --rm backup \
  sh /usr/local/bin/restore.sh /backups/finance_tracker-YYYYMMDD-HHMMSS.sql.gz
```

(`restore.sh` uses the same `PG*` env vars as the backup service.)

## Restore test (run after any change to the backup setup)

1. Start the stack and let a backup run:
   ```bash
   docker compose --profile app up -d
   docker compose logs backup        # expect "[backup] done (… bytes)"
   ls backups/                        # a finance_tracker-*.sql.gz file exists
   ```
2. Make a visible change (e.g. register a user or add a transaction).
3. Restore the dump taken *before* the change:
   ```bash
   docker compose run --rm backup \
     sh /usr/local/bin/restore.sh /backups/<the-dump>.sql.gz
   ```
4. Confirm the database matches the dump's point in time (the change from step 2 is gone) and the app still
   starts and serves `/actuator/health`.

Last verified: 2026-10-19 (setup added).
