#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"
. "$ROOT_DIR/scripts/demo-env.sh"

echo "Using environment file: $DEMO_ENV_FILE"
docker compose --env-file "$DEMO_ENV_FILE" down
