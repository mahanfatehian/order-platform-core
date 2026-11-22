# N1 Receipts and status mail

The profile already has an email. Mail is a fact consumer, not a second writer of orders or wallets. The capture sink and the five mails are on this branch.

## Build

- One mail when an order becomes `CONFIRMED`, `SHIPPED`, `DELIVERED`, or `FAILED`, and one when a refund entry is posted
- The confirm mail is the receipt: order id, lines, total in rials, address snapshot, and wallet entry id
- The send is recorded with the event id. A Kafka replay does not send a second mail
- A missing mail provider fails the send record and retries. It does not roll back the order or the ledger
- Local Compose uses a capture sink, not a real inbox

## Tests

- Each status sends one mail
- Replaying the same event id does not send again
- A down sink leaves the order `CONFIRMED` and the capture posted
- The receipt total matches the captured minor units

## Out of scope

SMS, marketing mail, and a template editor.
