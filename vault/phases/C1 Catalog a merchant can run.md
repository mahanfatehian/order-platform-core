# C1 Catalog a merchant can run

Admin can already create a product with a name, description, SKU, decimal price, one of five fixed categories, and an active flag. Catalog search and stock quantity already exist. This track finishes the catalog. It does not add a second storefront.

## Build

- A picture on the product, stored by this platform, not a URL pasted from somewhere else. At least one picture is required before the product can be active.
- Categories a merchant can name. The five-value enum does not remain the catalog.
- A stock-receive command that adds on-hand quantity with a reason and an idempotency key. It must not edit reserved quantity, and it must not be a SQL update.
- The existing catalog search still finds the new product by name and SKU.
- The demo seed may remain for `DEMO_MODE=true`. A shop run does not depend on it.

## Tests

- Create, upload a picture, receive stock, and find the product in catalog search
- An active product with no picture is rejected
- Two receives with the same key add stock once
- A receive does not change reserved quantity
- Checkout of that product still re-quotes live price and quantity

## Out of scope

Variants, bundles, and a separate SEO site.
