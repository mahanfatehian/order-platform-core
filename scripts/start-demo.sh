#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"
. "$ROOT_DIR/scripts/demo-env.sh"

echo "Starting the Order/flow showcase..."
echo "Using environment file: $DEMO_ENV_FILE"
docker compose --env-file "$DEMO_ENV_FILE" up -d --build --wait --wait-timeout 360
docker compose --env-file "$DEMO_ENV_FILE" ps

echo
echo "Order/flow is ready at http://localhost:8080"
echo "Demo personas are listed on the sign-in page and in README.md."
