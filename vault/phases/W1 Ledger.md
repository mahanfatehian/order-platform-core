# W1 Ledger

## Build

- `wallet-service` Spring Boot module, own Postgres, health check, non-root image, Compose service
- Tables: `wallets`, `ledger_entries`, `holds`, `processed_commands`
- One wallet row per user id, created on first credit or on an explicit ensure command
- Post function used by every command: lock wallet, apply rule, insert entry, commit
- Query: available balance, booked balance, open holds

## Tests that must exist before W2

- Credit increases available balance
- Hold fails when available balance is short, and writes nothing
- Two concurrent holds on one balance: one wins, available balance is never negative
- Same idempotency key and same body returns one entry
- Same key and a different amount returns 409
- Capture of a missing hold fails
- Release of an open hold restores available balance
- Capture after release fails
- Release after capture fails
- No repository outside `wallet-service` is on the wallet datasource

## Not in this phase

Admin UI, checkout, Kafka, gateway.
