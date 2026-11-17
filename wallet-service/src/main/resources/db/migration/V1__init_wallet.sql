CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    currency VARCHAR(3) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_wallets_currency_irr CHECK (currency = 'IRR')
);

CREATE TABLE holds (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallets (id),
    order_id UUID NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_holds_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_holds_status CHECK (status IN ('OPEN', 'CAPTURED', 'RELEASED'))
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallets (id),
    entry_type VARCHAR(16) NOT NULL,
    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    actor_id VARCHAR(64) NOT NULL,
    actor_role VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    order_id UUID,
    hold_id UUID REFERENCES holds (id),
    related_entry_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ledger_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_ledger_currency_irr CHECK (currency = 'IRR'),
    CONSTRAINT ck_ledger_type CHECK (entry_type IN ('CREDIT', 'HOLD', 'CAPTURE', 'RELEASE', 'REFUND'))
);

CREATE TABLE processed_commands (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    entry_id UUID NOT NULL REFERENCES ledger_entries (id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_ledger_wallet ON ledger_entries (wallet_id);
CREATE INDEX idx_holds_wallet_status ON holds (wallet_id, status);
