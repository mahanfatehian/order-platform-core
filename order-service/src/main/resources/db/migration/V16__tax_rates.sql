CREATE TABLE tax_rates (
    country VARCHAR(2) PRIMARY KEY,
    rate_bps INTEGER NOT NULL CHECK (rate_bps >= 0 AND rate_bps <= 10000),
    CONSTRAINT ck_tax_rates_country CHECK (country = 'IR')
);
