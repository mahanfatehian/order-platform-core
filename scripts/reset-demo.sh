#!/usr/bin/env sh
set -eu

if [ "${1:-}" != "--yes" ]; then
  echo "This permanently removes the local PostgreSQL, Redis, and Prometheus demo data."
  echo "Run: ./scripts/reset-demo.sh --yes"
  exit 2
fi

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"
docker compose --env-file .env.example down --volumes --remove-orphans
echo "Local demo data was removed. Run ./scripts/start-demo.sh to recreate it."
