ALTER TABLE orders ADD COLUMN wallet_hold_id UUID;
ALTER TABLE orders ADD COLUMN wallet_amount_minor BIGINT;
ALTER TABLE orders ADD COLUMN wallet_capture_entry_id UUID;

ALTER TABLE orders ADD CONSTRAINT ck_orders_wallet_amount_positive
    CHECK (wallet_amount_minor IS NULL OR wallet_amount_minor > 0);

COMMENT ON COLUMN orders.wallet_hold_id IS
    'Wallet hold placed before this order row. Null only for orders placed before wallet tender.';
COMMENT ON COLUMN orders.wallet_amount_minor IS
    'Quoted total converted to wallet minor units. The order total column stays decimal.';
COMMENT ON COLUMN orders.wallet_capture_entry_id IS
    'Capture entry written when the order was confirmed. Null while the hold is still open.';
