-- V2 predated profile-aware seeding and inserted demo catalog rows in every
-- environment. Remove only the untouched deterministic demo records. The dev
-- profile recreates them idempotently after Flyway completes.
DELETE FROM inventory i
WHERE i.product_id IN (
    'a1111111-1111-1111-1111-111111111111',
    'a2222222-2222-2222-2222-222222222222',
    'a3333333-3333-3333-3333-333333333333',
    'a4444444-4444-4444-4444-444444444444',
    'a5555555-5555-5555-5555-555555555555'
)
AND NOT EXISTS (
    SELECT 1 FROM inventory_reservations r WHERE r.product_id = i.product_id
)
AND EXISTS (
    SELECT 1 FROM products p
    WHERE p.id = i.product_id
      AND p.sku IS NULL
      AND p.name IN ('Wireless Headphones', 'Cotton T-Shirt', 'Coffee Table', 'Mystery Novel', 'Ceramic Mug')
);

DELETE FROM products p
WHERE p.id IN (
    'a1111111-1111-1111-1111-111111111111',
    'a2222222-2222-2222-2222-222222222222',
    'a3333333-3333-3333-3333-333333333333',
    'a4444444-4444-4444-4444-444444444444',
    'a5555555-5555-5555-5555-555555555555'
)
AND p.sku IS NULL
AND p.name IN ('Wireless Headphones', 'Cotton T-Shirt', 'Coffee Table', 'Mystery Novel', 'Ceramic Mug')
AND NOT EXISTS (
    SELECT 1 FROM inventory_reservations r WHERE r.product_id = p.id
);
