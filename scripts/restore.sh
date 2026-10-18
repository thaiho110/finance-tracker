#!/bin/sh
# Restore a finance-tracker backup produced by backup.sh. DESTRUCTIVE: the dump is created with
# --clean --if-exists, so restoring drops and recreates every object in the target database.
#
# Usage (from a host with psql, or inside the compose network):
#   docker compose run --rm -v "$PWD/backups:/backups" backup \
#       sh /usr/local/bin/restore.sh /backups/finance_tracker-YYYYMMDD-HHMMSS.sql.gz
#
# Connection comes from the standard PG* env vars (PGHOST/PGUSER/PGPASSWORD/PGDATABASE).
set -eu

file="${1:-}"
if [ -z "$file" ]; then
    echo "Usage: restore.sh <backup-file.sql.gz>" >&2
    exit 2
fi
if [ ! -f "$file" ]; then
    echo "No such backup file: $file" >&2
    exit 1
fi

DB="${PGDATABASE:-finance_tracker}"
echo "[restore] restoring $file into $DB (this overwrites current data)"
gunzip -c "$file" | psql --set ON_ERROR_STOP=on "$DB"
echo "[restore] done"
