-- Mirrors the order-service retention indexes. The nightly job deletes published
-- outbox rows and expired inbox rows by timestamp, and both statements scanned
-- their whole table because idx_store_outbox_ready is partial on the rows the
-- sweep does not touch and no index covered processed_at at all.
CREATE INDEX IF NOT EXISTS idx_store_outbox_retention
    ON store_outbox_events (published_at)
    WHERE published = TRUE;

CREATE INDEX IF NOT EXISTS idx_store_inbox_retention
    ON processed_kafka_events (processed_at);

COMMENT ON INDEX idx_store_outbox_retention IS
    'Serves the retention sweep over published outbox rows.';

COMMENT ON INDEX idx_store_inbox_retention IS
    'Serves the retention sweep over expired inbox rows.';
