ALTER TABLE orders ADD COLUMN tracking_reference VARCHAR(100);

CREATE TABLE order_status_history (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    event_id UUID NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    actor_user_id UUID,
    actor_role VARCHAR(64) NOT NULL,
    reason VARCHAR(500),
    correlation_id VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_order_status_history_event UNIQUE (event_id),
    CONSTRAINT ck_order_status_history_from_status CHECK (
        from_status IS NULL OR from_status IN (
            'PENDING', 'CONFIRMED', 'PACKAGED', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'FAILED'
        )
    ),
    CONSTRAINT ck_order_status_history_to_status CHECK (
        to_status IN (
            'PENDING', 'CONFIRMED', 'PACKAGED', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'FAILED'
        )
    )
);

CREATE INDEX idx_order_status_history_timeline
    ON order_status_history(order_id, occurred_at, recorded_at);
