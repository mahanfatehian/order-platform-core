# Contributing

Thanks for improving Order/flow. Keep changes small enough to review and preserve the service boundaries documented in `README.md`.

## Local verification

Prerequisites: Java 21 and Maven 3.9+, or Docker with Compose v2.20+.

```bash
mvn -B -ntp clean verify
docker compose --env-file .env.example config --quiet
```

For a full smoke test:

```bash
./scripts/start-demo.sh
./scripts/stop-demo.sh
```

PowerShell equivalents are available in `scripts/`.

## Secret scanning

Run the immutable Gitleaks CLI against the complete local Git history before opening a pull request:

```bash
docker run --rm \
  --mount type=bind,source=$PWD,target=/repo,readonly \
  --workdir /repo \
  ghcr.io/gitleaks/gitleaks:v8.30.0@sha256:691af3c7c5a48b16f187ce3446d5f194838f91238f27270ed36eef6359a574d9 \
  git --config=/repo/.gitleaks.toml --redact --no-banner /repo
```

To verify the scanner itself, create a disposable canary outside the repository by concatenating the `A`, `K`, `I`, and `A` fragments with a cryptographically random 16-character uppercase alphanumeric suffix. Scan only that temporary directory with `gitleaks dir --redact --exit-code=2`; it must exit `2`. Do not paste a literal credential into a file, terminal, issue, or pull request, and remove the verified temporary directory immediately after the check.

## Full Saga acceptance

Run the public, bounded Saga proof only against a disposable Compose project. The runner discovers the four local dev personas from the gateway demo page at runtime; do not pass, print, or store passwords or JWTs.

```bash
DISCOVERY_SERVICE_PORT=18761 GATEWAY_SERVICE_PORT=18080 docker compose -p orderflow-acceptance --env-file .env.example up -d --build --wait --wait-timeout 360
python3 scripts/acceptance/full-saga.py --base-url http://localhost:18080
```

If the runner fails, collect bounded diagnostics before cleanup:

```bash
docker compose -p orderflow-acceptance logs --no-color --tail=300
docker compose -p orderflow-acceptance down --volumes --remove-orphans
```

Always run the final teardown command, including after a successful run. The runner uses only public gateway routes and verifies the customer order history has `PENDING`, `CONFIRMED`, `PACKAGED`, `SHIPPED`, and `DELIVERED` transitions with event and correlation IDs.

## Database changes

Never edit an applied Flyway migration. Add the next versioned migration and verify both clean installation and upgrade behavior. Demo-only records belong in `@Profile("dev")` initializers unless a migration is explicitly production reference data.

## Events

- Commands use imperative names and are validated by the aggregate owner.
- Published events use past tense and describe facts that have already committed.
- Keep event IDs, correlation IDs, aggregate ordering, and consumer idempotency intact.
- Update `docs/asyncapi.yaml` when a public event contract changes.

## Pull requests

Explain the user outcome, the failure path, and the tests that prove it. Include screenshots for visual changes and call out migrations or compatibility considerations.
