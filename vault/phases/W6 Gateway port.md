# W6 Gateway port

This phase builds the socket. It does not build a درگاه.

## Build

- `payment_intents` table: id, type (`WALLET_TOPUP` or `ORDER_CHECKOUT`), wallet id, optional order id, amount, currency, status (`CREATED`, `VERIFIED`, `REJECTED`), idempotency key
- `WalletPaymentApplier.apply(verifiedPayment)` posts through the W1 function only
- A `FakeGateway` in tests that returns a configured verify result
- No HTTP callback from the public internet in the base Compose file
- A short note in this vault, [[03 Gateway port]], stays the adapter guide

## Tests

- Verified top-up credits once
- Second verify with the same provider reference credits once
- Amount mismatch posts nothing and marks the attempt rejected
- Fake gateway module cannot see `LedgerRepository`
- `AdminCredit` still works with zero gateways registered

## Done when

A later module can implement `start` and `verify` and call the applier without a change to the ledger rules.
