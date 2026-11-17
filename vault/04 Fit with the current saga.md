# Fit with the current saga

The order state machine in ADR-0001 does not grow a `PAID` status. Money is a side effect of the transitions that already exist.

## Checkout, before this plan

The BFF quotes the cart against `store-service`, then creates an order with an idempotency key. `order-service` commits `PENDING` and an outbox `OrderPlacedEvent`. Store reserves stock. `StockReservedEvent` confirms. `StockInsufficientEvent` fails. Cancel releases a reservation. Delivery consumes it.

## Checkout, after the wallet

1. BFF quotes the cart. Unchanged.
2. BFF calls `PlaceHold` with the checkout idempotency key and the quoted total in minor units.
3. If the hold is refused, checkout stops. No order row.
4. BFF creates the order and passes the hold id. The order stores that id.
5. If order creation fails after the hold, the BFF releases the hold with the same key family. A reconciliation job releases holds whose order never appeared.
6. `StockReservedEvent` handler calls `CaptureHold` before it commits `CONFIRMED`. Capture failure aborts confirmation. The order becomes `FAILED`, history records the reason, and stock is released.
7. `StockInsufficientEvent` and pending-order timeout release the hold, then fail the order as they do now.
8. Cancel from `PENDING` releases the hold. Cancel from `CONFIRMED` refunds the capture and still releases stock. Cancel after `PACKAGED` stays forbidden.
9. Delivery does not touch the wallet.

## Why capture waits for stock

Capturing at click time would take money for an order that stock can still reject. A hold already removes the funds from available balance, so the customer cannot spend them twice. Capture on `CONFIRMED` means money and stock become committed together.

## Authority

`PlaceHold`, `CaptureHold`, `ReleaseHold`, and `RefundCapture` use an internal API key, the same kind of boundary the store quote uses. The browser never calls the wallet ledger. The BFF may request a hold. Only `order-service` may capture, release, or refund, because only it commits the status those commands follow.

`AdminCredit` is an HTTP command on the wallet, role `ROLE_ADMIN`, through the gateway. It is not a Kafka message. After the credit commits, the wallet may publish `WalletCredited` as a fact for the UI. That fact must not be a second way to credit.

## What stays where

| Concern | Owner |
| --- | --- |
| Order status | `order-service` |
| Stock reservation | `store-service` |
| Ledger and holds | `wallet-service` |
| Cart and checkout orchestration | `web-ui-service` |
| Who the admin is | `user-service` roles, unchanged |
| Gateway proof, later | adapter module, then wallet applier |

`ROLE_ADMIN` still does not pack or ship. Crediting a wallet is a new admin power, not a shortcut into fulfillment.
