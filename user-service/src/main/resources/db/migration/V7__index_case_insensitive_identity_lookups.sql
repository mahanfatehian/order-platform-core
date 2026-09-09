-- Every login, every registration and every profile rename resolves an account
-- through findByUsernameIgnoreCase / findByEmailIgnoreCase. Spring Data renders
-- an IgnoreCase keyword with CriteriaBuilder.upper, so the SQL that actually
-- reaches Postgres is
--
--   where upper(username) = upper($1)
--
-- The identity indexes added in V3 are on lower(btrim(username)) and
-- lower(btrim(email)). Those are the right expressions for enforcing
-- uniqueness, but an index on lower(...) cannot answer a predicate on
-- upper(...), so none of these lookups had a usable index at all.
--
-- Measured on postgres:16 with 200k users, warm cache:
--
--   upper(username) = upper($1)      parallel seq scan   33.4 ms
--   with the index below             index scan           0.1 ms
--
-- That cost was paid on the login path, on the uniqueness check of every
-- registration, and on both sides of a username or email change.
CREATE INDEX IF NOT EXISTS ix_users_upper_username
    ON users (upper(username));

CREATE INDEX IF NOT EXISTS ix_users_upper_email
    ON users (upper(email));

COMMENT ON INDEX ix_users_upper_username IS
    'Serves findByUsernameIgnoreCase and existsByUsernameIgnoreCase, which emit upper(username).';

COMMENT ON INDEX ix_users_upper_email IS
    'Serves findByEmailIgnoreCase and existsByEmailIgnoreCase, which emit upper(email).';
