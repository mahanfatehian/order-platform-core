# What already exists

Checked against the code, not the README alone. Do not rebuild these.

## Keep

- Order status machine in ADR-0001. Commands are HTTP. Kafka carries facts after commit. There is no `PAID` status, and this plan does not add one.
- Stock reservation: `RESERVED`, `RELEASED`, `CONSUMED`. A late reservation cannot revive a cancelled order.
- Checkout re-quotes live price, product state, and quantity before it creates an order.
- Roles `USER`, `WAREHOUSE`, `DELIVERY`, `ADMIN`. A customer cannot pack. Admin does not implicitly pack or ship.
- Registration, sign-in, refresh rotation, logout, captcha after repeated failures, and a profile email field.
- Admin product create and update: name, description, SKU, price, one of five categories, active flag.
- Catalog search by text, in-stock filter, and pages.
- Inventory quantity and reserved quantity. Available is quantity minus reserved.
- An order stores a tracking reference string. Ship already requires a delivery role.
- `DEMO_MODE` hides persona passwords on the login page.
- Each business service has its own Postgres, Flyway, outbox, and inbox.

## Missing, and in this plan

| Gap | Evidence | Track |
| --- | --- | --- |
| No payment, invoice, or refund | No payment, mail, or refund types in the services | [[05 Money track]] |
| No picture on a product | `Product` has no image field | [[C1 Catalog a merchant can run]] |
| Categories are a fixed enum of five | `Product.Category` | [[C1 Catalog a merchant can run]] |
| No ship-to address | `Order` has user id, total, and tracking reference only | [[A1 Address on the order]] |
| No mail or receipt | No mail sender in the services | [[N1 Receipts and status mail]] |
| Tracking is free text, with no address check | `tracking_reference` column | [[S1 Ship to an address]] |
| Delivered is the end | Cancel is refused after `CONFIRMED` | [[R1 Return after delivery]] |
| No backup runbook in the repo | `DEMO_MODE` is the production switch that exists | [[P1 Open the door]] |
| No gateway port | Planned only | [[W6 Gateway port]] |

## Do not treat as done

Admin product forms and catalog search are a merchant start, not a catalog. A tracking string is not shipping. A profile email is not a receipt.
