ALTER TABLE orders DROP CONSTRAINT ck_orders_status;

ALTER TABLE orders ADD CONSTRAINT ck_orders_status
    CHECK (status IN (
        'PENDING',
        'CONFIRMED',
        'PACKAGED',
        'SHIPPED',
        'DELIVERED',
        'CANCELLED',
        'FAILED'
    ));
