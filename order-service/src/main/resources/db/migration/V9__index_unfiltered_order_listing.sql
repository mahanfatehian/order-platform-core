-- The administrator order list defaults to newest-first with no status or customer filter, and neither existing
-- index can serve it. A composite btree is only usable when its leading column is constrained, and both lead
-- with something the unfiltered listing does not mention:
--
--   idx_orders_status_created   (status, created_at DESC)
--   idx_orders_user_created     (user_id, created_at DESC)
--
-- Postgres therefore reads every order and sorts the result to return one page. The cost grows with the whole
-- table while the page size stays at twenty rows.
--
-- Ordering the index descending matches the default sort exactly, so the planner walks it forwards and stops at
-- the page size instead of sorting. A filtered listing keeps using the composite that already covers it.
CREATE INDEX IF NOT EXISTS idx_orders_created_at
    ON orders (created_at DESC);

COMMENT ON INDEX idx_orders_created_at IS
    'Serves the unfiltered administrator order listing: newest first, no predicate.';
