-- Product fields required by the catalog and administrative UI.
ALTER TABLE products ADD COLUMN IF NOT EXISTS sku VARCHAR(100);
ALTER TABLE products ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE UNIQUE INDEX IF NOT EXISTS ux_products_sku_normalized
    ON products (UPPER(sku))
    WHERE sku IS NOT NULL;

-- Hibernate maps @Enumerated(EnumType.STRING) as varchar. Convert the original
-- PostgreSQL enum without changing the already-applied V1 migration.
ALTER TABLE products ALTER COLUMN category DROP DEFAULT;
ALTER TABLE products ALTER COLUMN category TYPE VARCHAR(32) USING category::text;
ALTER TABLE products ALTER COLUMN category SET DEFAULT 'OTHER';
DROP TYPE IF EXISTS product_category;
ALTER TABLE products ADD CONSTRAINT ck_products_category
    CHECK (category IN ('ELECTRONICS', 'CLOTHING', 'HOME', 'BOOKS', 'OTHER'));

ALTER TABLE products DROP CONSTRAINT IF EXISTS products_price_check;
ALTER TABLE products ADD CONSTRAINT ck_products_price_non_negative CHECK (price >= 0);

-- Store all instants consistently as UTC-aware timestamps.
ALTER TABLE products ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE products ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';
ALTER TABLE products ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE products ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE inventory ALTER COLUMN last_updated TYPE TIMESTAMPTZ USING last_updated AT TIME ZONE 'UTC';
ALTER TABLE inventory ALTER COLUMN last_updated SET NOT NULL;

ALTER TABLE inventory ADD CONSTRAINT ck_inventory_reserved_not_above_total
    CHECK (reserved_quantity <= quantity);

-- Durable per-order reservation ownership. Aggregate reserved_quantity remains
-- the fast availability counter; these rows are the release source of truth.
CREATE TABLE inventory_reservations (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('RESERVED', 'RELEASED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    released_at TIMESTAMPTZ,
    CONSTRAINT ux_inventory_reservation_order_product UNIQUE (order_id, product_id)
);

CREATE INDEX idx_inventory_reservations_order_status
    ON inventory_reservations(order_id, status);

-- Consumer inbox makes Kafka processing atomic and idempotent with inventory.
CREATE TABLE processed_kafka_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    topic VARCHAR(200) NOT NULL,
    partition_number INTEGER NOT NULL,
    record_offset BIGINT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_store_processed_topic_position UNIQUE (topic, partition_number, record_offset)
);

-- Store-side transactional outbox closes the DB/Kafka dual-write window.
CREATE TABLE store_outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id VARCHAR(100) NOT NULL,
    topic VARCHAR(200) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    dead_lettered BOOLEAN NOT NULL DEFAULT FALSE,
    last_error VARCHAR(500)
);

CREATE INDEX idx_store_outbox_ready
    ON store_outbox_events(created_at)
    WHERE published = FALSE AND dead_lettered = FALSE;

CREATE INDEX idx_store_outbox_aggregate_order
    ON store_outbox_events(aggregate_id, created_at);
