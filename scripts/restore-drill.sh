#!/bin/sh
set -eu
# Run once against a scratch Postgres. Creates a ledger row, dumps it, restores it, and reads it back.
# Usage: ./scripts/restore-drill.sh
CONTAINER="${DRILL_CONTAINER:-shop-restore-drill}"
docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$CONTAINER" -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_DB=source postgres:16-alpine >/dev/null
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
  if docker exec "$CONTAINER" pg_isready -U postgres >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "$CONTAINER" psql -U postgres -d source -v ON_ERROR_STOP=1 -c "CREATE TABLE ledger_entries (id text PRIMARY KEY, amount bigint NOT NULL); INSERT INTO ledger_entries (id, amount) VALUES ('saga-1', 1000);"
docker exec "$CONTAINER" pg_dump -U postgres -d source --format=plain --file=/tmp/ledger.sql
docker exec "$CONTAINER" psql -U postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE restored;"
docker exec "$CONTAINER" psql -U postgres -d restored -v ON_ERROR_STOP=1 -f /tmp/ledger.sql
docker exec "$CONTAINER" psql -U postgres -d restored -tAc "SELECT amount FROM ledger_entries WHERE id = 'saga-1';"
docker rm -f "$CONTAINER" >/dev/null
