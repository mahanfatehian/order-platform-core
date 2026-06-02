-- Sample products
INSERT INTO products (id, name, description, price, category) VALUES
                                                                  ('a1111111-1111-1111-1111-111111111111', 'Wireless Headphones', 'Noise-cancelling over-ear headphones', 149.99, 'ELECTRONICS'),
                                                                  ('a2222222-2222-2222-2222-222222222222', 'Cotton T-Shirt', 'Classic fit cotton t-shirt in navy blue', 19.99, 'CLOTHING'),
                                                                  ('a3333333-3333-3333-3333-333333333333', 'Coffee Table', 'Solid oak coffee table with storage', 249.00, 'HOME'),
                                                                  ('a4444444-4444-4444-4444-444444444444', 'Mystery Novel', 'Bestselling crime thriller paperback', 12.50, 'BOOKS'),
                                                                  ('a5555555-5555-5555-5555-555555555555', 'Ceramic Mug', 'Handmade ceramic coffee mug, 350ml', 8.95, 'OTHER')
    ON CONFLICT (id) DO NOTHING;

-- Sample inventory (set initial stock and no reservations)
INSERT INTO inventory (product_id, quantity, reserved_quantity, last_updated) VALUES
                                                                                  ('a1111111-1111-1111-1111-111111111111', 100, 0, NOW()),
                                                                                  ('a2222222-2222-2222-2222-222222222222', 250, 0, NOW()),
                                                                                  ('a3333333-3333-3333-3333-333333333333', 30, 0, NOW()),
                                                                                  ('a4444444-4444-4444-4444-444444444444', 500, 0, NOW()),
                                                                                  ('a5555555-5555-5555-5555-555555555555', 150, 0, NOW())
    ON CONFLICT (product_id) DO NOTHING;