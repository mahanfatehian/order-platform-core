-- The admin user listing pages identifiers with ORDER BY createdAt, id -- the
-- id tiebreaker is there because a non-unique sort column makes LIMIT/OFFSET
-- pages repeat and skip rows. createdAt desc is the default the endpoint uses
-- when no sort is given.
--
-- The nearest existing index, ix_users_enabled_created_at, leads with enabled,
-- so it cannot serve the unfiltered listing, and it does not carry id, so it
-- cannot satisfy the tiebreaker either. Every page therefore read the whole
-- table and sorted it.
--
-- Measured on postgres:16 with 200k users, warm cache:
--
--   first page   parallel seq scan + top-N sort   38.8 ms  ->  0.1 ms
--   page 250     parallel seq scan + top-N sort   56.3 ms  ->  1.0 ms
--
-- Both directions are served: Postgres scans this index backwards for the
-- ascending sort. Selecting only id makes it an index-only scan with no heap
-- fetches. ix_users_enabled_created_at stays for listings filtered by enabled.
CREATE INDEX IF NOT EXISTS ix_users_created_at_id
    ON users (created_at DESC, id DESC);

COMMENT ON INDEX ix_users_created_at_id IS
    'Serves the admin user listing sort, including its id tiebreaker, in both directions.';
