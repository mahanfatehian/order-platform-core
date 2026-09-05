-- The order listings now sort by their chosen column and then by id, so a page boundary cannot land in the
-- middle of a group of rows that share a timestamp. Without the id the ordering inside such a group is whatever
-- the plan happens to produce, and two pages served by different plans can repeat a row and drop another.
--
-- Extending the indexes to carry id keeps that ordering satisfied by the index itself. Left as they were, the
-- planner has to add an incremental sort and drain every row sharing a timestamp before it can return a page.
DROP INDEX IF EXISTS idx_orders_created_at;
CREATE INDEX IF NOT EXISTS idx_orders_created_at_id
    ON orders (created_at DESC, id DESC);

DROP INDEX IF EXISTS idx_orders_status_created;
CREATE INDEX IF NOT EXISTS idx_orders_status_created_id
    ON orders (status, created_at DESC, id DESC);

DROP INDEX IF EXISTS idx_orders_user_created;
CREATE INDEX IF NOT EXISTS idx_orders_user_created_id
    ON orders (user_id, created_at DESC, id DESC);

COMMENT ON INDEX idx_orders_created_at_id IS
    'Unfiltered administrator listing, newest first, with the id tiebreaker the query orders by.';
COMMENT ON INDEX idx_orders_status_created_id IS
    'Fulfillment and status-filtered listings, with the id tiebreaker the query orders by.';
COMMENT ON INDEX idx_orders_user_created_id IS
    'A customer reading their own orders, with the id tiebreaker the query orders by.';
