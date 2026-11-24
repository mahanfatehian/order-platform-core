#!/bin/sh
set -eu
# Restore a dump into an empty scratch database. Do not point this at a production host.
# Usage: DATABASE_URL=postgresql://user:pass@host:5432/empty ./scripts/restore-scratch.sh dump.sql
if [ -z "${DATABASE_URL:-}" ] || [ -z "${1:-}" ]; then
  echo "usage: DATABASE_URL=postgresql://... $0 dump.sql" >&2
  exit 2
fi
case "$DATABASE_URL" in
  *prod*|*production*) echo "refusing a production-looking url" >&2; exit 2 ;;
esac
psql --dbname="$DATABASE_URL" --set ON_ERROR_STOP=1 --file="$1"
