ALTER TABLE orders ADD COLUMN ship_recipient VARCHAR(200);
ALTER TABLE orders ADD COLUMN ship_line1 VARCHAR(200);
ALTER TABLE orders ADD COLUMN ship_line2 VARCHAR(200);
ALTER TABLE orders ADD COLUMN ship_city VARCHAR(100);
ALTER TABLE orders ADD COLUMN ship_region VARCHAR(100);
ALTER TABLE orders ADD COLUMN ship_postal_code VARCHAR(20);
ALTER TABLE orders ADD COLUMN ship_phone VARCHAR(32);
ALTER TABLE orders ADD COLUMN ship_country VARCHAR(2);

ALTER TABLE orders ADD CONSTRAINT ck_orders_ship_country
    CHECK (ship_country IS NULL OR ship_country = 'IR');
