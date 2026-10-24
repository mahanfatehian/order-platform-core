#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"
. "$ROOT_DIR/scripts/demo-env.sh"

echo "Using environment file: $DEMO_ENV_FILE"
# Compose only acts on the profiles it is given, so name the optional ones or their containers outlive the stop.
docker compose --env-file "$DEMO_ENV_FILE" --profile tools --profile observability down
