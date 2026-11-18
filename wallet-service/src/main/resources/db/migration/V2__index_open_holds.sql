CREATE INDEX idx_holds_open_created
    ON holds (created_at, id)
    WHERE status = 'OPEN';

COMMENT ON INDEX idx_holds_open_created IS
    'Serves the reconciliation scan of open holds older than the pending-order deadline.';
