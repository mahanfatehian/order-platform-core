# W8 Production gate

This is not a launch. It is the proof that the base shop can run without demo money tricks.

## Build

- `DEMO_MODE=false` hides persona passwords and does not seed credits by SQL
- A documented admin credit is still how a wallet is funded
- Secrets for the wallet internal key come from the env file, not from source
- Wallet health is part of Compose `--wait`
- README walkthrough says the customer pays with wallet balance, and names the admin credit step
- Acceptance saga funds the wallet through the admin API, then buys, packs, ships, and delivers

## Tests

- Saga ends with stock reduced by one and wallet captured by the order total
- Saga replay does not capture again
- Gateway list is empty in the default Compose project

## Not this phase

A real domain, a real درگاه, tax, or a carrier. Those are new plans after this gate is green.
