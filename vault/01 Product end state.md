# Product end state

Order/flow stays the fulfillment core. The shop around it has one closed money loop until a gateway is added on purpose.

## The shop a customer can use

A signed-in customer sees a catalog with live price and stock, holds a wallet balance, and can buy only what that balance can cover. Checkout quotes the cart, places a hold for the exact quoted total, and creates one order. If stock cannot be reserved, the hold is released and the order fails. If stock is reserved, the hold is captured and the order becomes `CONFIRMED`. Pack, ship, and deliver stay human commands. Cancel before capture releases the hold. Cancel after capture refunds the wallet and releases stock, and only from `CONFIRMED`.

The customer can read every credit, hold, capture, release, and refund. An administrator can credit a wallet, and cannot edit or delete history.

## The shop another developer can extend

A gateway (درگاه پرداخت) is an adapter, not a second ledger. The same port accepts two intents:

| Intent | What the gateway proves | What the wallet does after a verified callback |
| --- | --- | --- |
| `WALLET_TOPUP` | The payer paid this amount for this top-up | Credits the wallet once |
| `ORDER_CHECKOUT` | The payer paid this amount for this order | Captures that order's hold, or pays the order if the base rule is later opened |

The base product implements neither intent with a real gateway. The only top-up command in the base product is `AdminCredit`. It goes through the same ledger post function a future gateway will call after verification.

## What "done" means for the base product

- One currency per deployment, stored as minor units (integer), never a float
- Available balance cannot go below zero
- Two checkouts cannot spend the same unit
- A failed or cancelled order cannot keep the money
- A delivered order cannot be refunded by accident through the cancel command
- Repeating an admin credit, a callback, or a capture does not move money twice
- The demo login still works with `DEMO_MODE=true`, and a wallet can be credited for `johndoe` without a gateway
- `mvn verify` covers the ledger races the same way it covers the stock races

## What "done" does not mean

It does not mean a public domain, tax invoices, a carrier, guest checkout, or a marketplace. Those stay out until the wallet rules above are true in tests and in the Compose saga.
