-- Preserve migration immutability while upgrading deterministic rows created
-- by the previous dev initializer. Missing showcase products are still
-- created exclusively by the dev-profile initializer, never in production.
UPDATE products
SET name = 'Aurora ANC Wireless Headphones',
    sku = 'ELEC-AUR-1001',
    description = 'Over-ear Bluetooth headphones with adaptive noise cancellation, multipoint pairing, and up to 35 hours of battery life.',
    price = 179.99,
    category = 'ELECTRONICS',
    updated_at = NOW()
WHERE id = 'a1111111-1111-1111-1111-111111111111'
  AND name = 'Wireless Headphones'
  AND (sku IS NULL OR sku = 'ELEC-001');

UPDATE products
SET name = 'Harbor Organic Cotton Oxford Shirt',
    sku = 'CLTH-HBR-2001',
    description = 'Midweight organic-cotton Oxford shirt with a tailored everyday fit, reinforced seams, and pearl-finish buttons.',
    price = 64.00,
    category = 'CLOTHING',
    updated_at = NOW()
WHERE id = 'a2222222-2222-2222-2222-222222222222'
  AND name = 'Cotton T-Shirt'
  AND (sku IS NULL OR sku = 'CLOTH-001');

UPDATE products
SET name = 'Hearthstone Enameled Dutch Oven',
    sku = 'HOME-HRT-3001',
    description = 'Five-quart enameled cast-iron Dutch oven designed for even heat retention, oven use, and easy cleanup.',
    price = 94.99,
    category = 'HOME',
    updated_at = NOW()
WHERE id = 'a3333333-3333-3333-3333-333333333333'
  AND name = 'Coffee Table'
  AND (sku IS NULL OR sku = 'HOME-001');

UPDATE products
SET name = 'Nimbus 8-in-1 USB-C Hub',
    sku = 'ELEC-NIM-1002',
    description = 'Compact aluminum hub with 4K HDMI, Gigabit Ethernet, SD and microSD readers, USB-A, and 100W power delivery.',
    price = 69.95,
    category = 'ELECTRONICS',
    updated_at = NOW()
WHERE id = 'a4444444-4444-4444-4444-444444444444'
  AND name = 'Mystery Novel'
  AND (sku IS NULL OR sku = 'BOOK-001');

UPDATE products
SET name = 'Vale Washed Linen Duvet Cover Set',
    sku = 'HOME-VLE-3002',
    description = 'Breathable European-flax linen duvet cover with two matching shams, corner ties, and a hidden button closure.',
    price = 139.00,
    category = 'HOME',
    updated_at = NOW()
WHERE id = 'a5555555-5555-5555-5555-555555555555'
  AND name = 'Ceramic Mug'
  AND (sku IS NULL OR sku = 'OTHER-001');
