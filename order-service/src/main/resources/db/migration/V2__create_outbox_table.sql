CREATE TABLE IF NOT EXISTS outbox_events (
                                             id UUID PRIMARY KEY,
                                             aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                             published BOOLEAN NOT NULL DEFAULT FALSE
                             );

CREATE INDEX idx_outbox_events_published ON outbox_events(published);