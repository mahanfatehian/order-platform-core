-- A delivered order permanently consumes its reservation. Total on-hand and
-- reserved quantities are decremented together by the consumer transaction.
ALTER TABLE inventory_reservations
    ADD COLUMN IF NOT EXISTS consumed_at TIMESTAMPTZ;

ALTER TABLE inventory_reservations
    DROP CONSTRAINT IF EXISTS inventory_reservations_status_check;

ALTER TABLE inventory_reservations
    DROP CONSTRAINT IF EXISTS ck_inventory_reservations_status;

ALTER TABLE inventory_reservations
    ADD CONSTRAINT ck_inventory_reservations_status
        CHECK (status IN ('RESERVED', 'RELEASED', 'CONSUMED'));

ALTER TABLE inventory_reservations
    ADD CONSTRAINT ck_inventory_reservations_terminal_timestamp
        CHECK (
            (status = 'RESERVED' AND released_at IS NULL AND consumed_at IS NULL)
            OR (status = 'RELEASED' AND released_at IS NOT NULL AND consumed_at IS NULL)
            OR (status = 'CONSUMED' AND released_at IS NULL AND consumed_at IS NOT NULL)
        );
