-- The nightly retention job deletes published outbox rows and expired inbox rows
-- by timestamp. Neither predicate had an index, so both statements read their
-- whole table while holding delete locks, on the two tables that grow fastest.
--
-- The existing outbox indexes do not help. idx_order_outbox_ready is partial on
-- published = FALSE, which is the exact complement of what the sweep deletes,
-- and idx_order_outbox_aggregate_order leads with aggregate_id.
--
-- Each index below mirrors its statement: partial on the published rows for the
-- outbox, plain on processed_at for the inbox, which the sweep filters alone.
CREATE INDEX IF NOT EXISTS idx_order_outbox_retention
    ON outbox_events (published_at)
    WHERE published = TRUE;

CREATE INDEX IF NOT EXISTS idx_order_inbox_retention
    ON processed_kafka_events (processed_at);

-- idx_outbox_events_published dates from before the partial indexes existed. A
-- single boolean column is too weak to plan with once most rows share a value,
-- and both of its values are now covered by a partial index that also carries
-- the timestamp the queries actually filter on. Keeping it only costs write
-- amplification on the hottest insert path in the service.
DROP INDEX IF EXISTS idx_outbox_events_published;

COMMENT ON INDEX idx_order_outbox_retention IS
    'Serves the retention sweep over published outbox rows.';

COMMENT ON INDEX idx_order_inbox_retention IS
    'Serves the retention sweep over expired inbox rows.';
