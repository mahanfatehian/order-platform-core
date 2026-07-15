## What changed

Describe the user-visible or architectural outcome.

## Verification

- [ ] `mvn -B -ntp clean verify`
- [ ] `docker compose --env-file .env.example config --quiet`
- [ ] Relevant browser or API flow exercised
- [ ] Migrations tested from an existing schema when applicable
- [ ] README or architecture documentation updated

## Event and data safety

- [ ] Duplicate delivery remains idempotent
- [ ] Aggregate event ordering is preserved
- [ ] Failure and compensation paths were considered
