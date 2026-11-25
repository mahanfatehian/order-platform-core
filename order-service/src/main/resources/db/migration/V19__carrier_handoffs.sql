CREATE TABLE carrier_handoffs (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders (id),
    tracking_reference VARCHAR(100) NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL
);
