ALTER TABLE store_outbox_events
    ADD COLUMN next_attempt_at TIMESTAMPTZ;

DROP INDEX IF EXISTS idx_store_outbox_ready;
CREATE INDEX idx_store_outbox_ready
    ON store_outbox_events (COALESCE(next_attempt_at, created_at), created_at)
    WHERE published = FALSE AND dead_lettered = FALSE;

COMMENT ON COLUMN store_outbox_events.next_attempt_at IS
    'Publisher retry gate. A dead-lettered event intentionally blocks later events for the same aggregate.';
