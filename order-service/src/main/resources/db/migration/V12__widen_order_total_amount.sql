-- orders.total_amount was DECIMAL(10,2), the same width as a single unit price, but it stores the sum of every
-- line: unit_price multiplied by quantity, across every item on the order.
--
-- The API's own validation permits far more than that column can hold. OrderItemRequest allows a quantity up to
-- 10000, the cart allows up to 100 distinct products, and products.price is itself DECIMAL(10,2). One line of
-- 10000 units at 10000.00 already totals 100,000,000.00, which is more than DECIMAL(10,2) can represent.
--
-- The order is therefore accepted, priced by an authoritative quote, and only then fails on insert with a numeric
-- overflow, surfacing as a 500 after the request looked valid at every earlier check.
--
-- NUMERIC(19,2) is the usual width for a money total and holds the worst case the validation permits with room
-- to spare. Widening a numeric is a metadata-only change in PostgreSQL when the scale is unchanged, so this does
-- not rewrite the table. unit_price stays at (10,2): it mirrors products.price and is a single unit, not a sum.
ALTER TABLE orders
    ALTER COLUMN total_amount TYPE NUMERIC(19,2);

COMMENT ON COLUMN orders.total_amount IS
    'Order total. Wider than unit_price because it aggregates quantity times price across every line.';
