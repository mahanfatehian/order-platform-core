# W2 Admin credit

The admin page and the credit command are on the integration branch. Checkout still does not spend the wallet.

## Build

- `POST /api/wallets/{userId}/credits` through the gateway, `ROLE_ADMIN` only
- Body: amount, reason, idempotency key
- Customer and warehouse tokens receive 403
- Admin workspace page: pick a user, enter amount and reason, submit, read the new entry back
- The page shows entries. It does not show an editable balance field
- Demo script or Compose seed may credit `johndoe` once, through the command, not through SQL

## Tests

- Admin credit is visible on a following read
- Repeat with the same key does not double the balance
- Missing reason or zero amount is 400
- Non-admin is 403
- UI test: the credit form posts the command and renders the returned entry

## Done when

An operator can fund a demo customer without a database console, and the ledger row is the only record of that act.
