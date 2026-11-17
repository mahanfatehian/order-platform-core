# Money track

This note is only the wallet. The shop around it is [[08 Tracks]]. Do these phases in order. A later phase that starts before the previous phase's tests exist is out of sequence.

| Phase | Outcome | Note |
| --- | --- | --- |
| [[W0 Decisions]] | Currency, module boundary, and the capture rule are written down | No code |
| [[W1 Ledger]] | Wallet service, tables, and the race test | No UI |
| [[W2 Admin credit]] | Admin can credit, and cannot edit history | Base top-up |
| [[W3 Pay orders from the wallet]] | Checkout holds, confirm captures, failure releases | Buying |
| [[W4 Reconciliation]] | Orphan holds and missed captures cannot stick | Safety |
| [[W5 Customer surface]] | Balance and entries visible in the existing UI | Still no gateway |
| [[W6 Gateway port]] | Intent and applier exist, with a fake adapter only | After the shop tracks, before opening |
| [[W7 Refunds]] | Cancel after confirm returns money and stock | Not a post-delivery return |
| [[W8 Production gate]] | Demo mode off is safe for the money path | Not the shop opening |

W8 checks the wallet on Compose. The shop is not open until [[P1 Open the door]].

## Not in this track

- Pictures, categories a merchant names, addresses, mail, and post-delivery returns. Those are the other tracks.
- A real درگاه inside this repository
- Guest checkout, split tender, or a second currency
- A `PAID` order status
- Letting the browser post a ledger entry
