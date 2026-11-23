# R1 Return after delivery

Cancel stays forbidden after pack. A delivered order has its own return record: requested, accepted, or rejected. Accept puts stock back and refunds the capture once. The order status stays `DELIVERED`.

## Build

- `RequestReturn` by the customer who owns the order, only from `DELIVERED`, once
- `AcceptReturn` by `ROLE_WAREHOUSE`. Accepting adds the sold quantity back to on-hand stock and asks the wallet to refund the capture
- Reject leaves stock and money where they are
- A second accept does not refund twice and does not add stock twice
- The order status stays `DELIVERED`. A return record carries `REQUESTED`, `ACCEPTED`, or `REJECTED`. No new order status

## Tests

- Return of a shipped-but-not-delivered order is rejected
- Accept puts stock back and posts one refund
- Replay of accept is a no-op
- Cancel of a delivered order still returns 409 and does not refund

## Out of scope

Partial returns, exchanges, and return shipping labels.
