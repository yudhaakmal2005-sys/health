#!/usr/bin/env bash
# Cadangan harian Postgres SEHATI: pg_dump terkompresi gzip, rotasi 14 hari.
# Pemakaian (dari mana saja):  /opt/sehati/deploy/backup.sh
# Cron (setiap hari 02.30):    30 2 * * * /opt/sehati/deploy/backup.sh >> /var/log/sehati-backup.log 2>&1
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$DEPLOY_DIR/backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"
FILE="$BACKUP_DIR/sehati-$STAMP.sql.gz"

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
cd "$DEPLOY_DIR"

# Tulis ke file sementara dulu agar cadangan setengah jadi tidak pernah terlihat sebagai cadangan valid.
docker compose exec -T db pg_dump -U sehati -d sehati --no-owner --clean --if-exists | gzip -9 > "$FILE.tmp"
mv "$FILE.tmp" "$FILE"
chmod 600 "$FILE"

# Verifikasi arsip gzip dapat dibaca
gzip -t "$FILE"

find "$BACKUP_DIR" -name 'sehati-*.sql.gz' -type f -mtime +"$KEEP_DAYS" -delete
echo "$(date -Is) cadangan dibuat: $FILE ($(du -h "$FILE" | cut -f1))"
