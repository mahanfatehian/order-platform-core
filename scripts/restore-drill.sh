#!/bin/sh
set -eu
# Run once against a scratch Postgres. Dumps the wallet, store, and order databases and reads one row back from each.
# Usage: ./scripts/restore-drill.sh
CONTAINER="${DRILL_CONTAINER:-shop-restore-drill}"
docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$CONTAINER" -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_DB=source postgres:16-alpine >/dev/null
ready=0
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40; do
  if docker exec "$CONTAINER" psql -U postgres -tAc "SELECT 1" >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done
if [ "$ready" -ne 1 ]; then
  echo "scratch database did not accept connections" >&2
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  exit 1
fi
docker exec "$CONTAINER" psql -U postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE wallet;"
docker exec "$CONTAINER" psql -U postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE store;"
docker exec "$CONTAINER" psql -U postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE orders;"
docker exec "$CONTAINER" psql -U postgres -d wallet -v ON_ERROR_STOP=1 -c "CREATE TABLE ledger_entries (id text PRIMARY KEY, amount bigint NOT NULL); INSERT INTO ledger_entries (id, amount) VALUES ('saga-1', 1000);"
docker exec "$CONTAINER" psql -U postgres -d store -v ON_ERROR_STOP=1 -c "CREATE TABLE products (id text PRIMARY KEY, name text NOT NULL); INSERT INTO products (id, name) VALUES ('lamp-1', 'Lamp');"
docker exec "$CONTAINER" psql -U postgres -d orders -v ON_ERROR_STOP=1 -c "CREATE TABLE orders (id text PRIMARY KEY, status text NOT NULL); INSERT INTO orders (id, status) VALUES ('order-1', 'CONFIRMED');"
for db in wallet store orders; do
  docker exec "$CONTAINER" pg_dump -U postgres -d "$db" --format=plain --file="/tmp/${db}.sql"
  docker exec "$CONTAINER" psql -U postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE restored_${db};"
  docker exec "$CONTAINER" psql -U postgres -d "restored_${db}" -v ON_ERROR_STOP=1 -f "/tmp/${db}.sql"
done
docker exec "$CONTAINER" psql -U postgres -d restored_wallet -tAc "SELECT amount FROM ledger_entries WHERE id = 'saga-1';"
docker exec "$CONTAINER" psql -U postgres -d restored_store -tAc "SELECT name FROM products WHERE id = 'lamp-1';"
docker exec "$CONTAINER" psql -U postgres -d restored_orders -tAc "SELECT status FROM orders WHERE id = 'order-1';"
docker rm -f "$CONTAINER" >/dev/null
