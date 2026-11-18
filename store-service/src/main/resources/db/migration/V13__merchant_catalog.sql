-- Merchant-named categories replace the five-value enum. Pictures are bytes in this
-- database. A stock receipt is the receive command record, not a balance edit.

CREATE TABLE product_categories (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_product_categories_name_ci ON product_categories (LOWER(name));

INSERT INTO product_categories (id, name, created_at) VALUES
    ('c1000000-0000-4000-8000-000000000001', 'Electronics', NOW()),
    ('c1000000-0000-4000-8000-000000000002', 'Clothing', NOW()),
    ('c1000000-0000-4000-8000-000000000003', 'Home', NOW()),
    ('c1000000-0000-4000-8000-000000000004', 'Books', NOW()),
    ('c1000000-0000-4000-8000-000000000005', 'Other', NOW());

ALTER TABLE products ADD COLUMN category_id UUID;

UPDATE products SET category_id = 'c1000000-0000-4000-8000-000000000001' WHERE category = 'ELECTRONICS';
UPDATE products SET category_id = 'c1000000-0000-4000-8000-000000000002' WHERE category = 'CLOTHING';
UPDATE products SET category_id = 'c1000000-0000-4000-8000-000000000003' WHERE category = 'HOME';
UPDATE products SET category_id = 'c1000000-0000-4000-8000-000000000004' WHERE category = 'BOOKS';
UPDATE products SET category_id = 'c1000000-0000-4000-8000-000000000005' WHERE category = 'OTHER';

ALTER TABLE products ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE products ADD CONSTRAINT fk_products_category
    FOREIGN KEY (category_id) REFERENCES product_categories (id);
ALTER TABLE products DROP CONSTRAINT IF EXISTS ck_products_category;
ALTER TABLE products DROP COLUMN category;

CREATE TABLE product_pictures (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products (id),
    content_type VARCHAR(100) NOT NULL,
    bytes BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_product_pictures_product ON product_pictures (product_id, created_at);

CREATE TABLE stock_receipts (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products (id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    reason VARCHAR(500) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_stock_receipts_idempotency_key UNIQUE (idempotency_key)
);
