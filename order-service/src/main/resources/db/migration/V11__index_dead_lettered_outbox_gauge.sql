-- order_platform_outbox_dead_lettered is a Prometheus gauge, so the snapshot refreshes it on a timer whether or
-- not anything changed. Its query is count(*) where dead_lettered = true, and no index covered that predicate,
-- so each refresh scanned the entire outbox table to answer a number that is almost always zero.
--
-- A partial index contains only the dead-lettered rows. In a healthy system that is an empty index, so the count
-- becomes a single page read instead of a scan whose cost grows with retained history.
--
-- Measured on 300k rows with no dead letters:
--   before  Parallel Seq Scan, 5,770 buffers, 24.7 ms
--   after   Index Only Scan,        1 buffer,  0.06 ms
--
-- The other two gauges are already covered: the pending count matches idx_order_outbox_ready, and the per-status
-- order counts lead with status in idx_orders_status_created_id.
CREATE INDEX IF NOT EXISTS idx_order_outbox_dead_lettered
    ON outbox_events (id)
    WHERE dead_lettered = TRUE;

COMMENT ON INDEX idx_order_outbox_dead_lettered IS
    'Serves the dead-lettered gauge; holds only dead-lettered rows, so it is empty when nothing is stuck.';
