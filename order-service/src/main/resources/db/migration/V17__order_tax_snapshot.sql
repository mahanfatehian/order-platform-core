ALTER TABLE orders ADD COLUMN tax_rate_bps INTEGER NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN tax_amount_minor BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD CONSTRAINT ck_orders_tax_rate_bps CHECK (tax_rate_bps >= 0 AND tax_rate_bps <= 10000);
ALTER TABLE orders ADD CONSTRAINT ck_orders_tax_amount_minor CHECK (tax_amount_minor >= 0);
