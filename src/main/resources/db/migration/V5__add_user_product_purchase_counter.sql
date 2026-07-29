ALTER TABLE order_items
    ADD COLUMN purchase_limit_at_order INTEGER;

UPDATE order_items oi
SET purchase_limit_at_order = p.max_purchase_quantity
    FROM product p
WHERE p.id = oi.product_id
  AND p.max_purchase_quantity IS NOT NULL;

CREATE TABLE user_product_purchase_counter (
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,

    reserved_quantity BIGINT NOT NULL DEFAULT 0,
    paid_quantity BIGINT NOT NULL DEFAULT 0,
    purchase_limit BIGINT NOT NULL,

    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, product_id),

    CONSTRAINT fk_purchase_counter_user
    FOREIGN KEY (user_id)
    REFERENCES users(id)
    ON DELETE CASCADE,

    CONSTRAINT fk_purchase_counter_product
    FOREIGN KEY (product_id)
    REFERENCES product(id)
    ON DELETE CASCADE,

    CONSTRAINT ck_purchase_counter_reserved
    CHECK (reserved_quantity >= 0),

    CONSTRAINT ck_purchase_counter_paid
    CHECK (paid_quantity >= 0),

    CONSTRAINT ck_purchase_counter_limit
    CHECK (purchase_limit > 0),

    CONSTRAINT ck_purchase_counter_total
    CHECK (reserved_quantity + paid_quantity <= purchase_limit)
);