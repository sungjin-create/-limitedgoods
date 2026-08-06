INSERT INTO product (
    name,
    description,
    price,
    initial_stock,
    stock,
    sold_count,
    max_purchase_quantity,
    type,
    status,
    sale_start_at,
    sale_end_at,
    updated_at
)
SELECT
    'loadtest-normal-' || LPAD(sequence::text, 2, '0'),
    'k6 browse and mixed scenario product',
    10000 + sequence,
    10000,
    10000,
    0,
    NULL,
    'NORMAL',
    'ACTIVE',
    NULL,
    NULL,
    CURRENT_TIMESTAMP
FROM generate_series(1, 20) AS sequence
WHERE NOT EXISTS (
    SELECT 1
    FROM product
    WHERE name = 'loadtest-normal-' || LPAD(sequence::text, 2, '0')
);

INSERT INTO product (
    name,
    description,
    price,
    initial_stock,
    stock,
    sold_count,
    max_purchase_quantity,
    type,
    status,
    sale_start_at,
    sale_end_at,
    updated_at
)
SELECT
    'loadtest-limited-' || LPAD(sequence::text, 2, '0'),
    'k6 distributed queue scenario product',
    20000 + sequence,
    10000,
    10000,
    0,
    1,
    'LIMITED',
    'ACTIVE',
    CURRENT_TIMESTAMP - INTERVAL '1 day',
    CURRENT_TIMESTAMP + INTERVAL '7 days',
    CURRENT_TIMESTAMP
FROM generate_series(1, 10) AS sequence
WHERE NOT EXISTS (
    SELECT 1
    FROM product
    WHERE name = 'loadtest-limited-' || LPAD(sequence::text, 2, '0')
);

INSERT INTO product (
    name,
    description,
    price,
    initial_stock,
    stock,
    sold_count,
    max_purchase_quantity,
    type,
    status,
    sale_start_at,
    sale_end_at,
    updated_at
)
SELECT
    'loadtest-hot-product',
    'k6 hot product scenario',
    30000,
    100,
    100,
    0,
    1,
    'LIMITED',
    'ACTIVE',
    CURRENT_TIMESTAMP - INTERVAL '1 day',
    CURRENT_TIMESTAMP + INTERVAL '7 days',
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM product WHERE name = 'loadtest-hot-product'
);

SELECT
    type,
    STRING_AGG(id::text, ',' ORDER BY id) AS product_ids
FROM product
WHERE name LIKE 'loadtest-normal-%'
   OR name LIKE 'loadtest-limited-%'
GROUP BY type
ORDER BY type;

SELECT id AS hot_product_id
FROM product
WHERE name = 'loadtest-hot-product';
