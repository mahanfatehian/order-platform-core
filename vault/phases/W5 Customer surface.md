# W5 Customer surface

The customer reads only the wallet named by their token. The home page and checkout show available balance and open holds. A short balance disables confirm and does not create an order. There is no top-up control.

## Build

- Customer home shows available balance and open holds
- A ledger page lists credits, holds, captures, releases, and refunds, newest first
- Checkout shows the total and the balance before the customer confirms
- Insufficient balance disables confirm and does not call order create
- No top-up button that pretends a gateway exists

## Tests

- The balance on the page equals the ledger query
- A hold in flight is visible as unavailable, not as spent
- Another user's wallet is not readable

## Done when

A demo customer can see why checkout will succeed or fail without an admin reading the database.
