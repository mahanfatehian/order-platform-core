#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

echo "Starting the Order/flow showcase..."
docker compose --env-file .env.example up -d --build --wait --wait-timeout 360
docker compose --env-file .env.example ps

echo
echo "Order/flow is ready at http://localhost:8080"
echo "Demo personas are listed on the sign-in page and in README.md."
