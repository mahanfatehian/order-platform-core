# Tracks

Do the tracks in this order. A later track may start only when the previous track's exit test is green. Money is first because a shop that cannot take and return money is still a demo.

| Order | Track | Exit test |
| --- | --- | --- |
| 1 | [[05 Money track]] | Admin credits a wallet. Checkout spends it. Failure and cancel put the money back. No gateway binary in the repo. |
| 2 | [[C1 Catalog a merchant can run]] | A product with a picture and a merchant category can be created, found, and bought. Seed data is not required. |
| 3 | [[A1 Address on the order]] | An order stores the address that was current at checkout. Editing the profile later does not change that order. |
| 4 | [[N1 Receipts and status mail]] | The customer receives a mail at confirm, ship, deliver, fail, and refund. Repeating the event does not send a second mail. |
| 5 | [[S1 Ship to an address]] | Ship is rejected without a snapshotted address and a non-empty tracking reference. |
| 6 | [[R1 Return after delivery]] | A delivered order can be returned once. Stock comes back. The wallet is refunded once. |
| 7 | [[W6 Gateway port]] | A fake adapter can prove a top-up and an order payment. A mismatched amount posts nothing. |
| 8 | [[P1 Open the door]] | `DEMO_MODE=false` skips seed, the scratch restore reads the ledger row, and the saga returns stock and balance. Not a launch. |
| 9 | [[T1 Tax table]] | Checkout holds goods plus the IR rate. The order stores that rate. No tax authority. |
| 10 | [[K1 Carrier port]] | A carrier may reject a tracking reference. It cannot set status or write the wallet. |

## Already written inside the money track

[[W0 Decisions]] through [[W8 Production gate]] stay. W8 is the money-track check, not the shop opening. The shop opening is [[P1 Open the door]].

## Parallel work that is still forbidden

Do not build a carrier client, a tax table, or a real درگاه while the money track is open. Those wait for the port and the exit tests above.
