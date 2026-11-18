# A1 Address on the order

Checkout copies a snapshot of the selected address onto the order. Pack and ship read that snapshot and do not call the profile. A later edit or delete of the address book does not change the order.

## Build

- A customer address book: recipient, lines, city, region, postal code, phone, country `IR`. One default.
- Checkout requires an address id that belongs to the ordering user. It copies those fields onto the order in the same transaction as the insert, after the wallet hold. The order does not store an address id.
- Pack and ship read the snapshot already on the order. They do not call the profile. An order written before this change can still be packed and shipped.
- The customer can read the snapshot on the order page.
- Country is the deployment country from [[07 Shop boundary]]. A second country is rejected.

## Tests

- Checkout without an address creates no order and no wallet hold
- Changing the address book after checkout does not change the order snapshot
- Another customer's address cannot be selected
- Ship still works when the address book row is later deleted

## Out of scope

Address autocomplete, a map, and international formats.
