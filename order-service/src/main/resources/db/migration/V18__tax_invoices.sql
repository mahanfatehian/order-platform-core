CREATE TABLE tax_invoices (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders (id),
    user_id UUID NOT NULL,
    goods_minor BIGINT NOT NULL CHECK (goods_minor >= 0),
    rate_bps INTEGER NOT NULL CHECK (rate_bps >= 0 AND rate_bps <= 10000),
    tax_minor BIGINT NOT NULL CHECK (tax_minor >= 0),
    charged_minor BIGINT NOT NULL CHECK (charged_minor >= 0),
    issued_at TIMESTAMPTZ NOT NULL
);
