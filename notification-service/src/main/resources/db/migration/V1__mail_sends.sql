CREATE TABLE mail_sends (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    kind VARCHAR(16) NOT NULL,
    recipient VARCHAR(320) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    body VARCHAR(8000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    CONSTRAINT ck_mail_sends_kind CHECK (kind IN ('CONFIRMED', 'SHIPPED', 'DELIVERED', 'FAILED', 'REFUND')),
    CONSTRAINT ck_mail_sends_status CHECK (status IN ('SENT', 'FAILED'))
);
