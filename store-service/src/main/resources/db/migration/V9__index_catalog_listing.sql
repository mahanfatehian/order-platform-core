-- The storefront catalog is the most requested read in the platform. Its query
-- filters on the active flag and orders by created_at descending, and products
-- carries no index for either, so every page request is a sequential scan of the
-- table followed by a sort of everything that survived the filter.
--
-- A composite over both columns lets the planner satisfy the predicate and the
-- ordering from one index and stop once it has filled the requested page. This
-- mirrors idx_orders_status_created and ix_users_enabled_created_at, which cover
-- the equivalent listing in their own schemas.
--
-- The search term is a substring match and stays unindexed by design; this index
-- serves the unfiltered browse, which is the common path.
CREATE INDEX IF NOT EXISTS idx_products_active_created_at
    ON products (active, created_at DESC);

COMMENT ON INDEX idx_products_active_created_at IS
    'Serves the default storefront listing: active products, newest first.';
