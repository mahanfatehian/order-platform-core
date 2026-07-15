-- Fulfillment roles are domain configuration and are available in every
-- environment. Demo users remain dev-profile only and are created by
-- DevDataInitializer.
INSERT INTO roles (id, name) VALUES
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'ROLE_WAREHOUSE'),
    ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'ROLE_DELIVERY')
ON CONFLICT (name) DO NOTHING;

-- Staff members can manage their own profile while operational access is
-- authorized separately through their role.
INSERT INTO role_authorities (role_id, authority_id)
SELECT role.id, authority.id
FROM roles role
CROSS JOIN authorities authority
WHERE role.name IN ('ROLE_WAREHOUSE', 'ROLE_DELIVERY')
  AND authority.name IN ('profile:read', 'profile:update')
ON CONFLICT DO NOTHING;
