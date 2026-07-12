ALTER TABLE orders ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(500);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);

ALTER TABLE orders ADD CONSTRAINT ck_orders_status
    CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED', 'CANCELLED'));
ALTER TABLE orders ADD CONSTRAINT ck_orders_total_non_negative CHECK (total_amount >= 0);
ALTER TABLE order_items ADD CONSTRAINT ck_order_items_quantity_positive CHECK (quantity > 0);
ALTER TABLE order_items ADD CONSTRAINT ck_order_items_price_non_negative CHECK (unit_price >= 0);

CREATE UNIQUE INDEX ux_orders_user_idempotency_key
    ON orders(user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_orders_status_created ON orders(status, created_at DESC);
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);

-- Explicit text storage avoids treating an already-serialized JSON document as
-- a PostgreSQL JSONB value and lets the publisher reconstruct an allow-listed type.
ALTER TABLE outbox_events ALTER COLUMN payload TYPE TEXT USING
    CASE WHEN jsonb_typeof(payload) = 'string' THEN payload #>> '{}' ELSE payload::text END;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS topic VARCHAR(200) NOT NULL DEFAULT 'order.events';
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS dead_lettered BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS last_error VARCHAR(500);

DROP INDEX IF EXISTS idx_outbox_events_published;
CREATE INDEX idx_order_outbox_ready
    ON outbox_events(created_at)
    WHERE published = FALSE AND dead_lettered = FALSE;
CREATE INDEX idx_order_outbox_aggregate_order
    ON outbox_events(aggregate_id, created_at);

CREATE TABLE processed_kafka_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    topic VARCHAR(200) NOT NULL,
    partition_number INTEGER NOT NULL,
    record_offset BIGINT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_order_processed_topic_position UNIQUE (topic, partition_number, record_offset)
);
