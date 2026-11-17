# Shop boundary

These limits are how this repo becomes a shop without becoming a platform for every shop shape at once.

## In the last product

- One brand. No vendor accounts, no commission, no second catalog owner.
- One warehouse. Stock stays in `store-service`. No transfer between sites.
- One country and one currency. The currency lock is in ADR-0003: `IRR`, minor unit 1 (rial). Order prices stay decimal at the catalog boundary and convert to minor units only when the wallet is called.
- Signed-in customers only. Registration already exists. Guest checkout is out.
- The only tender in the base app is the wallet. An administrator credits it. A customer cannot type a card number.
- Pack, ship, and deliver stay human commands with the roles they have now.
- Notices are mail to the profile email. No SMS provider in the base app.
- Returns are a new command after `DELIVERED`, not a reuse of cancel.

## Out until a later plan

- A real درگاه in this repository. The port is in. The provider is not.
- Split tender, partial capture, or paying part by wallet and part by card.
- Multi-currency wallets, tax invoices for a named authority, and carrier rate shopping.
- SEO pages, a separate storefront app, and a mobile client.
- Opening the door before [[P1 Open the door]] passes.

## Rule

A feature that needs a second brand, a second currency, or a browser posting money is not a small addition. It is a new plan.
