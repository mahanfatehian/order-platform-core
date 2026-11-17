# W4 Reconciliation

Holds and captures can be left behind if a process dies between two services. This phase closes those gaps. It does not add features.

## Jobs

- Open hold older than the pending-order deadline, with no order row: release it
- Order `FAILED` or `CANCELLED` with an `OPEN` hold: release it
- Order `CONFIRMED` or later with an `OPEN` hold: capture it, or fail closed and alert if capture is no longer possible
- Order `PENDING` past deadline: existing fail path must also release the hold

## Tests

- Kill between hold and order create: the job releases the hold
- Kill between stock reserved and capture: the job captures once, or the confirm retry captures once, not both
- Job replay does not double-release

## Done when

A crash in checkout cannot freeze a customer's money, and a crash after stock reservation cannot confirm an unpaid order.
