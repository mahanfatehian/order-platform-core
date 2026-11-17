# S1 Ship to an address

Ship already requires `ROLE_DELIVERY` and stores a tracking string. That string can be empty of meaning, and nothing checks that the order has anywhere to go.

## Build

- Ship is rejected unless the order has an address snapshot and a non-empty tracking reference
- The ship command still belongs to `ROLE_DELIVERY`. Admin does not gain it
- The tracking reference is shown to the customer with the address snapshot
- A carrier adapter is not built. The base shop's carrier is the person who types the reference

## Tests

- Ship without an address returns 409 and does not change status
- Ship with a blank tracking reference returns 400
- A customer token cannot ship
- Repeating ship with the same reference is idempotent. A different reference on an already shipped order returns 409

## Out of scope

Rate shopping, labels, and a named carrier API. Those are a later plan after the door is open.
