# W3 Pay orders from the wallet

Checkout places a hold before the order row and captures it on confirm. A short balance creates no order. Cancel after capture is still the next money slice.

## Build

- Checkout computes the quoted total in minor units
- `PlaceHold` before order create, keyed by the existing checkout idempotency key
- Order stores `walletHoldId`
- Short balance: no order, cart message, no saga
- Confirm path calls `CaptureHold` before committing `CONFIRMED`
- Capture failure: order `FAILED`, stock released, history reason recorded
- Insufficient stock and pending timeout: `ReleaseHold`, then the existing fail path
- Cancel from `PENDING`: `ReleaseHold`
- Customer order page shows the amount held or captured

## Tests

- Quoted total above balance does not create an order
- Happy path: hold open at `PENDING`, captured at `CONFIRMED`, available balance reduced by the total
- `StockInsufficientEvent` leaves available balance unchanged and stock unreserved
- Repeating confirm does not capture twice
- A customer token cannot call capture
- Saga acceptance script, still on Compose, funds the wallet as admin and completes deliver with balance captured once

## Done when

The five-minute walkthrough in the README still works, and the only way the customer paid is the wallet.
