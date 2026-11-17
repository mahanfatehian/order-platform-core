# Product end state

The fulfillment core stays. The shop around it is catalog, wallet, address, mail, ship, and return. Money has one owner, the wallet, until a gateway is added on purpose.

## The shop a customer can use

A signed-in customer searches a catalog with pictures, live price, and live stock. They pay only from a wallet an administrator credited. Checkout quotes the cart, places a hold for the exact quoted total in minor units, and creates one order only if the hold succeeds. The order stores the ship-to address as it was at that moment. If stock cannot be reserved, the hold is released and the order fails. If stock is reserved, the hold is captured and the order becomes `CONFIRMED`. There is no `PAID` status.

Pack, ship, and deliver stay human commands. Ship requires the snapshotted address and a non-empty tracking reference. The customer receives one mail at confirm, ship, deliver, fail, and refund. Cancel before capture releases the hold. Cancel from `CONFIRMED` refunds the wallet and releases stock. After `PACKAGED`, cancel stays forbidden. After `DELIVERED`, a return can bring stock and money back, once.

The customer can read every credit, hold, capture, release, and refund. An administrator can credit a wallet, and cannot edit or delete history.

## What a merchant can do

An administrator creates a product without seed data: name, description, SKU, price, a category they named, stock received by command, and at least one picture. They credit a wallet with a reason. They cannot pack or ship unless they also hold those roles.

## The shop another developer can extend

A gateway (درگاه پرداخت) is an adapter, not a second ledger. The same port accepts two intents:

| Intent | What the gateway proves | What the wallet does after a verified callback |
| --- | --- | --- |
| `WALLET_TOPUP` | The payer paid this amount for this top-up | Credits the wallet once |
| `ORDER_CHECKOUT` | The payer paid this amount for this order | Captures that order's hold, or pays the order if the base rule is later opened |

The base product implements neither intent with a real gateway. The only top-up command in the base product is `AdminCredit`. It goes through the same ledger post function a future gateway will call after verification.

## What "done" means

- Currency is `IRR`, minor unit 1, one deployment, no float
- Available balance cannot go below zero, and two checkouts cannot spend the same rial
- A failed, cancelled, or returned order cannot keep the money or the stock
- A product can be created, pictured, found, and bought without the demo seed
- Every shipped order has an address snapshot
- Status mail is sent once per fact
- A delivered order can be returned once
- `DEMO_MODE=false` still completes the saga, and a backup of each database can be restored
- A fake gateway proves the port. A real درگاه is not in this repository

## What "done" does not mean

A public domain, tax invoices, a carrier API, guest checkout, a marketplace, or a second currency. Those stay out until [[P1 Open the door]] is green.
