CREATE TABLE order_returns (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders (id),
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    refund_entry_id UUID,
    requested_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    decided_by UUID,
    CONSTRAINT ck_order_returns_status CHECK (status IN ('REQUESTED', 'ACCEPTED', 'REJECTED'))
);
