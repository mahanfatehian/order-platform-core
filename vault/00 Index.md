# Wallet and commerce plan

This vault is the build plan for turning Order/flow into a shop whose money has one owner: the wallet.

Open this folder as an Obsidian vault. The engineering decision that code must obey is [[ADR-0003 wallet is the only money authority]] in `docs/adr`. This vault is the task map. If they disagree, the ADR wins until it is amended.

## Read in this order

1. [[01 Product end state]]
2. [[02 Non-negotiable wallet]]
3. [[03 Gateway port]]
4. [[04 Fit with the current saga]]
5. [[05 Phases and tasks]]

## What is in scope now

The base app takes money only from a wallet. An administrator charges that wallet. A customer cannot type a card number, and no payment gateway (درگاه) ships in the base modules.

## What is deliberately later

Another developer can add a gateway that either charges the wallet or pays for one order. That adapter must call the wallet port. It must not write a balance, a ledger row, or an order status.

## Do not start with

- A public launch
- Stripe, Zarinpal, IDPay, or any other gateway inside `wallet-service`
- A second order status such as `PAID`
- A balance column that admin screens edit in place
