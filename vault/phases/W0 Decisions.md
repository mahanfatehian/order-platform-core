# W0 Decisions

No migration until this note is accepted.

## Decisions to lock

1. New module `wallet-service`, database `walletdb`, Flyway in that module. Not a package inside `order-service`.
2. Currency is `IRR`. The minor unit is 1 (one rial). That lock is ADR-0003. Catalog prices stay decimal until a wallet command converts them. A scale mismatch fails the command.
3. Amount type is `long` minor units.
4. Hold at order create. Capture only when stock reservation succeeds, inside the confirm path. Release on fail, timeout, and cancel-before-capture.
5. `AdminCredit` is the only top-up in the base app.
6. Internal wallet commands use a dedicated internal key, not the user JWT.
7. Kafka facts after commit are optional for the UI. They are never a command path.

## Done when

- ADR-0003 names `IRR` and minor unit 1. It does.
- ADR-0004 names the shop around the wallet. It does.
- This note links to both.
- No wallet table has been added yet.
