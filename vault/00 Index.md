# Shop plan

This vault is the build plan for a shop that can be opened on this repo. The wallet is one track inside that shop, not the whole product.

Open this folder as an Obsidian vault. Non-negotiable rules live in ADRs. This vault is the task map. If a note and an ADR disagree, the ADR wins until it is amended.

## Read in this order

1. [[06 What already exists]]
2. [[07 Shop boundary]]
3. [[01 Product end state]]
4. [[08 Tracks]]
5. [[02 Non-negotiable wallet]]
6. [[03 Gateway port]]
7. [[04 Fit with the current saga]]
8. [[05 Phases and tasks]]

## The last product

One brand, one warehouse, one country. A signed-in customer browses a catalog a merchant edited, pays only from a wallet an administrator funded, and receives the order at an address stored on that order. The warehouse packs, ships with a tracking reference, and delivers. The customer gets a receipt and a mail at each status. A delivered order can be returned. Stock and wallet both move back. No card number is stored here.

A later developer maps their own payment gateway (درگاه پرداخت) onto the wallet port. That adapter can prove a wallet top-up or an order payment. It cannot write a balance or an order status.

## What is not the last product

A marketplace, guest checkout, a second currency, a tax engine, a carrier contract, a real درگاه inside this repository, a mobile app, or a public launch before [[P1 Open the door]] is green.
