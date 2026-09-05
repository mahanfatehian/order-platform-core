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
-- The pending count is already covered by idx_store_outbox_ready. The per-status reservation gauge is left
-- alone deliberately: a grouped count has to visit every row whatever index it uses, and measuring it showed
-- only 32.6ms to 23.1ms, which does not pay for another index on a table written to on every order.
CREATE INDEX IF NOT EXISTS idx_store_outbox_dead_lettered
    ON store_outbox_events (id)
    WHERE dead_lettered = TRUE;

COMMENT ON INDEX idx_store_outbox_dead_lettered IS
    'Serves the dead-lettered gauge; holds only dead-lettered rows, so it is empty when nothing is stuck.';
