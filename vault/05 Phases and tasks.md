# Phases and tasks

Do these in order. A later phase that starts before the previous phase's tests exist is out of sequence.

| Phase | Outcome | Note |
| --- | --- | --- |
| [[W0 Decisions]] | Currency, module boundary, and the capture rule are written down | No code |
| [[W1 Ledger]] | Wallet service, tables, and the race test | No UI |
| [[W2 Admin credit]] | Admin can credit, and cannot edit history | Base top-up |
| [[W3 Pay orders from the wallet]] | Checkout holds, confirm captures, failure releases | Buying |
| [[W4 Reconciliation]] | Orphan holds and missed captures cannot stick | Safety |
| [[W5 Customer surface]] | Balance and entries visible in the existing UI | Still no gateway |
| [[W6 Gateway port]] | Intent and applier exist, with a fake adapter only | For later developers |
| [[W7 Refunds]] | Cancel after confirm returns money and stock | Still no carrier returns |
| [[W8 Production gate]] | Demo mode off is safe, saga script pays with a wallet | Not a public launch |

## Exit of the base product

W0 through W7 are the base app: admin charges wallets, customers buy with that balance, and a gateway author can plug in without touching the ledger. W8 is the check that this still runs as one Compose stack.

## Explicitly not in these phases

- A real درگاه inside this repository
- Guest checkout
- Partial refunds, split tender, or paying part by wallet and part by card
- Multi-currency wallets
- A `PAID` order status
- Letting the browser post a ledger entry
