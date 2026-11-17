# ADR-0003: The wallet is the only money authority

- **Status:** Proposed
- **Date:** 2026-11-17
- **Decision owners:** Order platform maintainers

## Context

Checkout creates an order and reserves stock. It does not take payment. A later shop needs a wallet that customers spend, that an administrator can fund, and that another developer can connect to their own payment gateway (درگاه پرداخت) without writing a second ledger.

The order lifecycle in ADR-0001 already has one writer. A payment provider must not become a second writer of order status, and an admin screen must not become a second writer of a balance column.

The task map lives in the Obsidian vault at `vault/`. This ADR is the rule the code has to satisfy. The vault does not override it.

## Decision

### 1. A new service owns money

`wallet-service` owns `walletdb`. Only that service inserts ledger rows. Order, store, the BFF, and any future gateway adapter call commands. They do not connect to the wallet database.

### 2. The ledger is the balance

Balances are derived. Entries are append-only: `CREDIT`, `HOLD`, `CAPTURE`, `RELEASE`, `REFUND`. Amounts are positive minor units in one deployment currency. There is no command that sets a new balance, and no float.

The currency code is not locked in this ADR. Lock it in an amendment before the first migration. Do not reuse the order total's decimal scale as the ledger unit.

### 3. Checkout spends a hold, not a new status

There is no `PAID` status. The BFF places a hold before creating the order. `order-service` captures that hold before committing `CONFIRMED`. Failure, timeout, and cancel-before-capture release the hold. Cancel from `CONFIRMED` refunds the capture and still releases stock. Cancel after `PACKAGED` stays forbidden. Delivery does not move money.

If the wallet cannot hold or capture, no order is confirmed.

### 4. Admin credit is the only base top-up

`ROLE_ADMIN` may credit a wallet with an amount, a reason, and an idempotency key. Repeating the key does not credit twice. Customers cannot top up themselves in the base product.

### 5. A gateway verifies; the wallet posts

A future adapter may start a `WALLET_TOPUP` or an `ORDER_CHECKOUT` intent. After server-side verification, it calls one applier. The applier posts through the same ledger function as admin credit and capture. A callback amount that differs from the stored intent posts nothing. The base Compose stack ships no real gateway.

## Consequences

- Wallet races need the same kind of concurrency test the stock reservation already has.
- Confirm becomes a two-service step. Reconciliation must release orphan holds and must not confirm an order whose hold is still open.
- Gateway work is a new module after the port exists, not a change to these rules.
- This ADR is proposed until W0 records the currency and the first ledger migration starts. Until then, no payment code should land under a different design.

## Plan

`vault/00 Index.md`
