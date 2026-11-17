# A1 Address on the order

`Order` today stores a user id, a total, and a tracking string. It does not store where the box goes. A later edit of the customer's profile must not rewrite an order already placed.

## Build

- A customer address book: recipient, lines, city, region, postal code, phone. One default.
- Checkout requires a selected address. The order stores a snapshot of those fields, not a live link.
- Pack and ship read the snapshot. They do not call the profile.
- The customer can read the snapshot on the order page.
- Country is the deployment country from [[07 Shop boundary]]. A second country is rejected.

## Tests

- Checkout without an address creates no order and no wallet hold
- Changing the address book after checkout does not change the order snapshot
- Another customer's address cannot be selected
- Ship still works when the address book row is later deleted

## Out of scope

Address autocomplete, a map, and international formats.
