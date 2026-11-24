#!/bin/sh
set -eu
# Dump one scratch database. Do not point this at a production host.
# Usage: DATABASE_URL=postgresql://user:pass@host:5432/name ./scripts/backup-scratch.sh out.sql
if [ -z "${DATABASE_URL:-}" ] || [ -z "${1:-}" ]; then
  echo "usage: DATABASE_URL=postgresql://... $0 out.sql" >&2
  exit 2
fi
case "$DATABASE_URL" in
  *prod*|*production*) echo "refusing a production-looking url" >&2; exit 2 ;;
esac
pg_dump --dbname="$DATABASE_URL" --format=plain --file="$1"
