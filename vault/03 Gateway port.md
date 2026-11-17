# Gateway port

A payment gateway (درگاه پرداخت) proves that an external payer paid a frozen amount. The wallet decides what that proof is allowed to do.

## The port

A gateway adapter implements one interface. It does not import the ledger repository.

```text
start(intent) -> redirect or client secret
verify(callback) -> VerifiedPayment
```

`VerifiedPayment` contains the intent id, the amount in minor units, the currency, the provider reference, and nothing else. The wallet posts money only if that amount and currency equal the intent that was stored before the redirect.

## Two intents, one verifier

| Intent | Created by | After verify |
| --- | --- | --- |
| `WALLET_TOPUP` | customer top-up, later | `AdminCredit`'s post function, with actor `GATEWAY` and the intent id |
| `ORDER_CHECKOUT` | checkout, later | capture of the order hold, or a direct order payment through the same capture command |

The base app creates neither intent. It still has the tables and the verify function, and `AdminCredit` is the only caller of the credit post function. A later adapter is a new module that depends on `wallet-api`, not on `wallet-service` internals.

## What an adapter must not do

- Insert into `ledger_entries` or update a balance
- Call `order-service` to set a status
- Trust the amount in the callback body without comparing it to the stored intent
- Treat "pending" or "user closed the page" as paid
- Retry a verify by inserting a second credit

## Callback rule

The callback is public and untrusted. Verification uses the provider's server-side confirm call where the provider has one. The result is applied with the intent's idempotency key. A second callback with the same payment returns the first ledger entry. A callback whose amount differs is stored as a failed attempt and posts nothing.

## Mapping guide for a later developer

1. Implement the port in a new module, for example `gateway-zarinpal`.
2. Register it by name. Do not edit the ledger.
3. Choose `WALLET_TOPUP`, `ORDER_CHECKOUT`, or both.
4. Freeze the amount in minor units before redirect.
5. On verify, call `WalletPaymentApplier.apply(verifiedPayment)` and stop.
6. Add a test that a replayed callback does not credit twice, and a test that a mismatched amount credits nothing.

The base Compose stack does not start a gateway container. Adding one is a profile, not a change to checkout's money rule.
