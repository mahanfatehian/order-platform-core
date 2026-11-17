# W0 Decisions

No migration until this note is accepted.

## Decisions to lock

1. New module `wallet-service`, database `walletdb`, Flyway in that module. Not a package inside `order-service`.
2. Currency code is configuration, one per deployment. Recommend `IRR` with minor unit 1 (rial) if the first real gateway will be Iranian. The demo may keep displaying the existing order amounts, but the ledger does not inherit scale 2 by accident. Record the chosen code in ADR-0003 before W1.
3. Amount type is `long` minor units.
4. Hold at order create. Capture only when stock reservation succeeds, inside the confirm path. Release on fail, timeout, and cancel-before-capture.
5. `AdminCredit` is the only top-up in the base app.
6. Internal wallet commands use a dedicated internal key, not the user JWT.
7. Kafka facts after commit are optional for the UI. They are never a command path.

## Done when

- ADR-0003 names the currency and the capture point
- This note links to that ADR
- No table has been added yet
