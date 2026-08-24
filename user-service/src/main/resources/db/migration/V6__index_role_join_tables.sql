-- Postgres creates an index for the referenced side of a foreign key but never
-- for the referencing side. Each join table is therefore covered in one
-- direction only, by the leading column of its composite primary key:
--
--   user_roles         indexed on user_id, nothing on role_id
--   role_authorities   indexed on role_id, nothing on authority_id
--
-- Two paths pay for the missing half. Reading from the role side scans the whole
-- join table, which is what countActiveAdmins does on every status or role
-- change to confirm the last administrator is not being removed. Deleting or
-- re-keying a role or an authority also scans it, because the constraint has to
-- prove no rows still reference the row being removed.
--
-- Both scans grow with the size of the join table rather than with the number of
-- matching rows. order-service already indexes its foreign key columns; this
-- brings the identity schema in line.
CREATE INDEX IF NOT EXISTS ix_user_roles_role_id
    ON user_roles (role_id);

CREATE INDEX IF NOT EXISTS ix_role_authorities_authority_id
    ON role_authorities (authority_id);

COMMENT ON INDEX ix_user_roles_role_id IS
    'Covers lookups and foreign key checks that start from a role.';

COMMENT ON INDEX ix_role_authorities_authority_id IS
    'Covers lookups and foreign key checks that start from an authority.';
