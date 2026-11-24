# P1 Open the door

This is the check that the shop, not only the wallet, can run without the demo. It is not a public launch by itself. Do not start it until the money track, catalog, address, mail, ship, return, and gateway port exit tests are green.

## Build

- `DEMO_MODE=false` hides persona passwords and does not seed products or credits by SQL
- An administrator can still register, create a product, receive stock, and credit a wallet through commands
- Each service database has a documented backup and a restore drill. The drill is a script in the repo, run once against a scratch database, not a production host
- Wallet, store, and order secrets come from the env file, not from source
- Static pages exist for the operator to fill: terms, privacy, and a contact address. The repo does not invent legal text
- The acceptance saga creates a product, credits a wallet, buys, packs, ships, delivers, and accepts a return. Stock and balance end where they started. One receipt mail was captured

## Tests

- The saga above, on Compose, with `DEMO_MODE=false`
- A second run of the saga does not double-capture or double-refund
- The gateway list in the default Compose project is empty
- Restore of a backup can read the ledger row the saga wrote

## Not this phase

A real domain, a real درگاه, a tax authority, or a carrier contract. The repo now has the demo-mode gate, the three operator pages, and a scratch restore drill. The saga in the wallet tests proves the stock and balance return. It does not boot Compose.
