CREATE TABLE inventory_order_lifecycle (
    order_id UUID PRIMARY KEY,
    state VARCHAR(16) NOT NULL,
    last_event_id UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_inventory_order_lifecycle_state CHECK (state IN ('ACTIVE', 'RELEASED', 'CONSUMED'))
);

INSERT INTO inventory_order_lifecycle (order_id, state, last_event_id, updated_at)
SELECT order_id,
       CASE
           WHEN bool_or(status = 'RESERVED') THEN 'ACTIVE'
           WHEN bool_or(status = 'CONSUMED') THEN 'CONSUMED'
           ELSE 'RELEASED'
       END,
       min(id::text)::uuid,
       max(updated_at)
FROM inventory_reservations
GROUP BY order_id;
