# Order payment capture

A verified order payment may capture the hold already placed for that order. The verified amount must match the hold. A second call uses the same provider reference, so the ledger returns the first capture. A top-up is not a capture. A released hold is not captured. This command does not set an order status and does not credit the wallet.
