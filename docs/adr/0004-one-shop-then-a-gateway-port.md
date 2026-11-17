# ADR-0004: One shop, then a gateway port

- **Status:** Accepted
- **Date:** 2026-11-17
- **Decision owners:** Order platform maintainers

## Context

The fulfillment saga can reserve stock and move an order through human pack, ship, and deliver. It cannot yet be opened as a shop. The wallet in ADR-0003 is necessary and not sufficient. A customer still has nowhere to ship to, no receipt, no picture on a product, and no way to return a delivered order.

The task map is `vault/00 Index.md`. This ADR is the boundary. The vault does not override it.

## Decision

### 1. The last product is one shop

One brand, one warehouse, one country, one currency (`IRR`, as locked in ADR-0003). Customers are signed in. There is no guest checkout, no second catalog owner, and no marketplace.

### 2. The base tender is the wallet

Until a gateway adapter exists, the only way to pay is a wallet an administrator credited. No card number is stored. No order status `PAID` is added.

### 3. The commercial facts sit beside the saga

These are required before the shop is open, and each has one owner:

| Fact | Owner | Rule |
| --- | --- | --- |
| Ledger | `wallet-service` | ADR-0003 |
| Product picture, merchant category, stock receive | `store-service` | A receive adds on-hand stock. It does not edit a reservation. |
| Address snapshot | `order-service` | Copied at checkout. Later profile edits do not change it. |
| Status mail | a mail consumer | Replay of the same event id sends nothing again. Mail failure does not roll back the order. |
| Ship | `order-service` | Rejected without a snapshot and a non-empty tracking reference. |
| Return | `order-service` plus wallet and store commands | Only after `DELIVERED`. Cancel stays forbidden after `PACKAGED`. |

### 4. A gateway is a later adapter

A payment gateway (درگاه پرداخت) may prove a wallet top-up or an order payment. It calls the wallet applier. It does not ship inside the base Compose project.

### 5. Opening the door is a gate, not a deploy

`DEMO_MODE=false`, a restore drill for each database, and one saga that buys with wallet money and accepts a return are the exit. A domain, a tax authority, and a carrier contract are out of this decision.

## Consequences

- Wallet work may start only after this boundary is accepted. It must not be treated as the whole shop.
- Return is a new command. It must not reuse cancel.
- Mail and backup jobs must not become a second writer of order status or balances.

## Plan

`vault/08 Tracks.md`
