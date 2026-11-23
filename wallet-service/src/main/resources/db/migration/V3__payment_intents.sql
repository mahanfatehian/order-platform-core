CREATE TABLE payment_intents (
    id UUID PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    wallet_id UUID NOT NULL REFERENCES wallets (id),
    order_id UUID,
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    provider_reference VARCHAR(200),
    ledger_entry_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_payment_intents_type CHECK (type IN ('WALLET_TOPUP', 'ORDER_CHECKOUT')),
    CONSTRAINT ck_payment_intents_status CHECK (status IN ('CREATED', 'VERIFIED', 'REJECTED')),
    CONSTRAINT ck_payment_intents_currency CHECK (currency = 'IRR')
);
