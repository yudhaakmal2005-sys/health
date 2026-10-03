#!/usr/bin/env bash
# Menjalankan tes terhadap Postgres NYATA.
# - Bila DATABASE_URL sudah diatur (mis. CI), tes langsung dijalankan terhadap basis data itu.
# - Bila tidak, script membuat cluster Postgres 16 sementara di direktori temp (port 54329), lalu menghapusnya.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -n "${DATABASE_URL:-}" ]; then
  exec npm run --silent test:only
fi

PGBIN="${PGBIN:-/usr/lib/postgresql/16/bin}"
PORT="${PGPORT_TEST:-54329}"
if [ ! -x "$PGBIN/initdb" ]; then
  echo "initdb tidak ditemukan di $PGBIN. Atur DATABASE_URL atau PGBIN." >&2
  exit 1
fi

TMP="$(mktemp -d -t sehati-pg-XXXXXX)"
RUN=()
# initdb/postgres menolak berjalan sebagai root: jalankan sebagai user postgres (atau nobody).
if [ "$(id -u)" = "0" ]; then
  PGUSER_OS="postgres"
  id "$PGUSER_OS" >/dev/null 2>&1 || PGUSER_OS="nobody"
  chown "$PGUSER_OS" "$TMP"
  RUN=(runuser -u "$PGUSER_OS" --)
fi

cleanup() {
  "${RUN[@]}" "$PGBIN/pg_ctl" -D "$TMP/data" -m immediate stop >/dev/null 2>&1 || true
  rm -rf "$TMP"
}
trap cleanup EXIT

"${RUN[@]}" "$PGBIN/initdb" -D "$TMP/data" -U postgres --auth=trust -E UTF8 --locale=C >/dev/null
"${RUN[@]}" "$PGBIN/pg_ctl" -D "$TMP/data" -l "$TMP/postgres.log" -w \
  -o "-p $PORT -k $TMP -c listen_addresses=127.0.0.1 -c fsync=off -c synchronous_commit=off -c full_page_writes=off" start >/dev/null
"$PGBIN/createdb" -h 127.0.0.1 -p "$PORT" -U postgres sehati_test

export DATABASE_URL="postgres://postgres@127.0.0.1:$PORT/sehati_test"
npm run --silent test:only
