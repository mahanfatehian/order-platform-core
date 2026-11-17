# Non-negotiable wallet

These rules are the product. A gateway, an admin screen, or a faster checkout that breaks one of them is a defect.

## One writer

Only `wallet-service` inserts a ledger row. `order-service`, `web-ui-service`, and any gateway adapter call commands. They do not open `walletdb`.

## The ledger is the balance

The balance is the sum of posted entries, minus open holds. A screen must not `UPDATE balance = balance + n`. A correction is a new entry with a reason, an actor, and a link to the entry it corrects.

Every entry stores:

- entry id
- wallet id
- type: `CREDIT`, `HOLD`, `CAPTURE`, `RELEASE`, `REFUND`
- amount in minor units, always positive
- currency code
- idempotency key, unique
- actor id and role, or `SYSTEM`
- reason
- correlation id
- optional order id and payment-intent id
- created time

`HOLD` reduces available balance and does not reduce booked balance. `CAPTURE` consumes a hold. `RELEASE` returns a hold to available. `CREDIT` and `REFUND` increase booked balance. There is no silent debit.

## Holds are first class

A hold has its own row: `OPEN`, `CAPTURED`, `RELEASED`. Capture and release change an `OPEN` hold only. A second capture is a no-op that returns the original result. A release after capture is rejected. A capture after release is rejected.

## Commands

| Command | Who | Effect |
| --- | --- | --- |
| `AdminCredit` | `ROLE_ADMIN` | `CREDIT`. Amount, reason, and idempotency key required. |
| `PlaceHold` | order service, internal key | `HOLD` for one order. Fails if available balance is short. |
| `CaptureHold` | order service, internal key | `CAPTURE` of that hold. |
| `ReleaseHold` | order service, internal key | `RELEASE` of an open hold. |
| `RefundCapture` | order service, internal key | `REFUND` up to the captured amount, once per capture unless a later phase allows partial refunds. |

No command accepts a new balance. No command accepts a negative amount.

## Concurrency

Credit, hold, capture, release, and refund lock the wallet row, re-read available balance, insert the entry, and commit in one database transaction. Two concurrent holds for the same wallet must serialize. The test is the same shape as the stock race: two transactions, one pool of funds, exactly one winner, no negative available balance.

## Idempotency

The same idempotency key and the same body return the original entry. The same key and a different amount, currency, wallet, or order id return `409`. Gateway callbacks will use this rule later. Admin credit uses it now.

## Currency

The deployment has one currency, set in configuration, defaulting to the code chosen in [[W0 Decisions]]. Amounts are `long` minor units. The existing order `BigDecimal` scale of 2 is a demo habit and must not leak into the ledger. Order totals passed into the wallet are converted at the boundary with an explicit scale check. A mismatch fails the command.

## Time and audit

Ledger rows are append-only at the JPA boundary. There is no delete endpoint. Admin UI lists entries. It does not offer an edit box for the balance.

## Failure

If the wallet is down, checkout does not create an order. A reserved stock result must not confirm an order until capture succeeds. If capture fails, the order goes `FAILED` and stock is released. Money and stock must not diverge.
