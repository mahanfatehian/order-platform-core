# T1 Tax table

One country, `IR`. The rate is an integer in basis points, from 0 to 10000. A missing row means 0. Checkout adds that tax to the wallet hold and stores the rate and the tax amount on the order. A later rate change does not rewrite the order. The table does not name a tax authority, and it does not post a second ledger entry.
