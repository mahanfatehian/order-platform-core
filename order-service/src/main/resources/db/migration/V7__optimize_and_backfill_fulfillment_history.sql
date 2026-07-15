DROP INDEX IF EXISTS idx_order_status_history_timeline;

CREATE INDEX idx_order_status_history_timeline
    ON order_status_history(order_id, recorded_at);

-- Existing orders receive one truthful baseline entry. This records only the
-- status observed during migration and does not invent earlier transitions.
INSERT INTO order_status_history (
    id, order_id, event_id, from_status, to_status, actor_user_id, actor_role,
    reason, correlation_id, occurred_at, recorded_at
)
SELECT
    md5('history:' || orders.id::text)::uuid,
    orders.id,
    md5('history-event:' || orders.id::text)::uuid,
    NULL,
    orders.status,
    NULL,
    'SYSTEM_MIGRATION',
    'Current state recorded during fulfillment history migration',
    'migration-v7-' || orders.id::text,
    orders.updated_at,
    CURRENT_TIMESTAMP
FROM orders
WHERE NOT EXISTS (
    SELECT 1
    FROM order_status_history history
    WHERE history.order_id = orders.id
);
