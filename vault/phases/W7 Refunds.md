# W7 Refunds

Cancel after capture must return money. Cancel before capture must not refund. The cancel command calls the existing refund on this branch.

## Build

- Cancel from `CONFIRMED` calls the existing refund on this branch for the full captured amount, then the existing stock release
- Cancel from `PENDING` only releases the hold
- Cancel from `PACKAGED`, `SHIPPED`, or `DELIVERED` stays rejected
- Refund entry links to the capture entry
- Customer history shows the refund

## Tests

- Confirm then cancel: available balance returns to the pre-order figure, stock hold is released, one refund row
- Repeating cancel does not refund twice
- Cancel of a delivered order does not refund
- Refund without a capture fails

## Done when

An admin or the customer can undo a confirmed, unpaid-by-card order and both stock and wallet agree.
