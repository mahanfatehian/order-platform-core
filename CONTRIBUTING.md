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

## Database changes

Never edit an applied Flyway migration. Add the next versioned migration and verify both clean installation and upgrade behavior. Demo-only records belong in `@Profile("dev")` initializers unless a migration is explicitly production reference data.

## Events

- Commands use imperative names and are validated by the aggregate owner.
- Published events use past tense and describe facts that have already committed.
- Keep event IDs, correlation IDs, aggregate ordering, and consumer idempotency intact.
- Update `docs/asyncapi.yaml` when a public event contract changes.

## Pull requests

Explain the user outcome, the failure path, and the tests that prove it. Include screenshots for visual changes and call out migrations or compatibility considerations.
