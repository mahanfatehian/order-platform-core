-- Mirrors the order-service change. The catalog listing now orders by its sort column and then by id, so the
-- supporting index has to carry id as well; otherwise the planner falls back to an incremental sort and has to
-- read every product sharing a created_at before it can return the first page.
--
-- Products are especially exposed to this: they are seeded in bulk, and CURRENT_TIMESTAMP is the transaction
-- start time, so an entire seeded catalog can share one created_at value.
DROP INDEX IF EXISTS idx_products_active_created_at;
CREATE INDEX IF NOT EXISTS idx_products_active_created_at_id
    ON products (active, created_at DESC, id DESC);

COMMENT ON INDEX idx_products_active_created_at_id IS
    'Default storefront listing: active products, newest first, with the id tiebreaker the query orders by.';
