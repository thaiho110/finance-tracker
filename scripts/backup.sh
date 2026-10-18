#!/bin/sh
# Nightly Postgres backups for the finance-tracker database (deploy hardening).
# Runs one dump on start, then every 24h, writing gzipped SQL to /backups and pruning dumps older than
# BACKUP_KEEP_DAYS. Connection comes from the standard PG* env vars (set in docker-compose).
#
# Off-site copies are a deployment concern: sync /backups to object storage or another host separately.
set -eu

BACKUP_DIR="${BACKUP_DIR:-/backups}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
DB="${PGDATABASE:-finance_tracker}"

run_backup() {
    ts=$(date +%Y%m%d-%H%M%S)
    file="$BACKUP_DIR/${DB}-${ts}.sql.gz"
    echo "[backup] $(date -u +%FT%TZ) dumping $DB -> $file"
    # --clean --if-exists so the dump can be restored straight into an existing database.
    if pg_dump --clean --if-exists --no-owner --no-privileges "$DB" | gzip > "${file}.tmp"; then
        mv "${file}.tmp" "$file"
        echo "[backup] done ($(wc -c < "$file") bytes)"
    else
        echo "[backup] FAILED" >&2
        rm -f "${file}.tmp"
    fi
    # Keep the last KEEP_DAYS days of dumps.
    find "$BACKUP_DIR" -name "${DB}-*.sql.gz" -type f -mtime "+${KEEP_DAYS}" -delete 2>/dev/null || true
}

mkdir -p "$BACKUP_DIR"
run_backup
while true; do
    sleep 86400
    run_backup
done
