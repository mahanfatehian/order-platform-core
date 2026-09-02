#!/usr/bin/env sh

if [ -n "${ORDER_PLATFORM_ENV_FILE:-}" ]; then
  DEMO_ENV_FILE=$ORDER_PLATFORM_ENV_FILE
elif [ -f .env ]; then
  DEMO_ENV_FILE=.env
else
  DEMO_ENV_FILE=.env.example
fi

if [ ! -f "$DEMO_ENV_FILE" ]; then
  echo "Environment file not found: $DEMO_ENV_FILE" >&2
  exit 1
fi
