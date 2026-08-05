CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    token_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE product (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    price INTEGER NOT NULL,
    initial_stock INTEGER NOT NULL,
    stock INTEGER NOT NULL,
    sold_count INTEGER NOT NULL DEFAULT 0,
    max_purchase_quantity INTEGER,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    sale_start_at TIMESTAMP WITHOUT TIME ZONE,
    sale_end_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE,

    CONSTRAINT ck_product_price CHECK (price >= 0),
    CONSTRAINT ck_product_initial_stock CHECK (initial_stock >= 0),
    CONSTRAINT ck_product_stock CHECK (stock >= 0),
    CONSTRAINT ck_product_sold_count CHECK (sold_count >= 0),
    CONSTRAINT ck_product_max_purchase_quantity
        CHECK (max_purchase_quantity IS NULL OR max_purchase_quantity > 0),
    CONSTRAINT ck_product_type CHECK (type IN ('NORMAL', 'LIMITED')),
    CONSTRAINT ck_product_status CHECK (
        status IN ('DRAFT', 'PREPARING', 'SCHEDULED', 'ACTIVE', 'PAUSED', 'HIDDEN', 'ARCHIVED')
    ),
    CONSTRAINT ck_product_sale_period CHECK (
        sale_start_at IS NULL OR sale_end_at IS NULL OR sale_end_at > sale_start_at
    )
);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    total_price BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    paid_at TIMESTAMP WITHOUT TIME ZONE,
    failed_at TIMESTAMP WITHOUT TIME ZONE,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    fail_reason VARCHAR(255),
    cancel_requested_at TIMESTAMP WITHOUT TIME ZONE,
    refunded_at TIMESTAMP WITHOUT TIME ZONE,
    cancel_fail_reason VARCHAR(255),
    checkout_token VARCHAR(255) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,

    CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uq_orders_user_checkout_token UNIQUE (user_id, checkout_token),
    CONSTRAINT ck_orders_total_price CHECK (total_price >= 0),
    CONSTRAINT ck_orders_status CHECK (
        status IN (
            'CREATED', 'PAYMENT_PENDING', 'PAYMENT_APPROVED', 'PAID',
            'PAYMENT_FAILED', 'CANCEL_REQUESTED', 'CANCEL_FAILED',
            'REFUNDED', 'CANCELED', 'COMPLETED', 'EXPIRED'
        )
    ),
    CONSTRAINT ck_orders_request_fingerprint_length
        CHECK (char_length(request_fingerprint) = 64)
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    price INTEGER NOT NULL,
    line_total_price BIGINT NOT NULL,
    purchase_limit_at_order INTEGER,

    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product
        FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE RESTRICT,
    CONSTRAINT uq_order_items_order_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_price CHECK (price >= 0),
    CONSTRAINT ck_order_items_line_total_price CHECK (line_total_price >= 0),
    CONSTRAINT ck_order_items_purchase_limit
        CHECK (purchase_limit_at_order IS NULL OR purchase_limit_at_order > 0)
);

CREATE TABLE order_status_history (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    changed_by_user_id BIGINT NOT NULL,
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_order_status_history_order
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_status_history_user
        FOREIGN KEY (changed_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_order_status_history_from_status CHECK (
        from_status IS NULL OR from_status IN (
            'CREATED', 'PAYMENT_PENDING', 'PAYMENT_APPROVED', 'PAID',
            'PAYMENT_FAILED', 'CANCEL_REQUESTED', 'CANCEL_FAILED',
            'REFUNDED', 'CANCELED', 'COMPLETED', 'EXPIRED'
        )
    ),
    CONSTRAINT ck_order_status_history_to_status CHECK (
        to_status IN (
            'CREATED', 'PAYMENT_PENDING', 'PAYMENT_APPROVED', 'PAID',
            'PAYMENT_FAILED', 'CANCEL_REQUESTED', 'CANCEL_FAILED',
            'REFUNDED', 'CANCELED', 'COMPLETED', 'EXPIRED'
        )
    )
);

CREATE TABLE payment_attempt (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    pg_transaction_id VARCHAR(255),
    failure_code VARCHAR(255),
    failure_reason VARCHAR(255),
    requested_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    approved_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    approved_amount BIGINT,
    compensation_required BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_payment_attempt_order
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT uq_payment_attempt_order_key UNIQUE (order_id, idempotency_key),
    CONSTRAINT uq_payment_attempt_pg_transaction UNIQUE (pg_transaction_id),
    CONSTRAINT ck_payment_attempt_amount CHECK (amount >= 0),
    CONSTRAINT ck_payment_attempt_status
        CHECK (status IN ('PROCESSING', 'APPROVED', 'DECLINED', 'UNKNOWN')),
    CONSTRAINT ck_payment_attempt_fingerprint_length
        CHECK (char_length(request_fingerprint) = 64),
    CONSTRAINT ck_payment_attempt_idempotency_key
        CHECK (char_length(idempotency_key) BETWEEN 8 AND 100)
);

CREATE TABLE refund_attempt (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    payment_attempt_id BIGINT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    pg_transaction_id VARCHAR(255) NOT NULL,
    pg_refund_id VARCHAR(255),
    failure_code VARCHAR(255),
    failure_reason VARCHAR(500),
    requested_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    refunded_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_refund_attempt_order
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_refund_attempt_payment
        FOREIGN KEY (payment_attempt_id) REFERENCES payment_attempt(id) ON DELETE RESTRICT,
    CONSTRAINT uq_refund_attempt_order_key UNIQUE (order_id, idempotency_key),
    CONSTRAINT uq_refund_attempt_pg_refund UNIQUE (pg_refund_id),
    CONSTRAINT ck_refund_attempt_amount CHECK (amount > 0),
    CONSTRAINT ck_refund_attempt_status
        CHECK (status IN ('PROCESSING', 'APPROVED', 'DECLINED', 'UNKNOWN'))
);

CREATE TABLE user_product_purchase_counter (
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    reserved_quantity BIGINT NOT NULL DEFAULT 0,
    paid_quantity BIGINT NOT NULL DEFAULT 0,
    purchase_limit BIGINT NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, product_id),
    CONSTRAINT fk_purchase_counter_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_counter_product
        FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE,
    CONSTRAINT ck_purchase_counter_reserved CHECK (reserved_quantity >= 0),
    CONSTRAINT ck_purchase_counter_paid CHECK (paid_quantity >= 0),
    CONSTRAINT ck_purchase_counter_limit CHECK (purchase_limit > 0),
    CONSTRAINT ck_purchase_counter_total
        CHECK (reserved_quantity + paid_quantity <= purchase_limit)
);

CREATE INDEX idx_product_status_id ON product (status, id);
CREATE INDEX idx_product_scheduled_start ON product (sale_start_at, id)
    WHERE status = 'SCHEDULED';
CREATE INDEX idx_product_name_trgm ON product USING GIN (upper(name) gin_trgm_ops);
CREATE INDEX idx_product_description_trgm ON product USING GIN (upper(description) gin_trgm_ops);

CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC, id DESC);
CREATE INDEX idx_orders_user_status_id ON orders (user_id, status, id);
CREATE INDEX idx_orders_expirable ON orders (expires_at, id)
    WHERE status IN ('CREATED', 'PAYMENT_FAILED');

CREATE INDEX idx_order_items_product_order ON order_items (product_id, order_id);
CREATE INDEX idx_order_status_history_order_created
    ON order_status_history (order_id, created_at, id);

CREATE INDEX idx_payment_attempt_reconciliation ON payment_attempt (updated_at, id)
    WHERE status IN ('PROCESSING', 'UNKNOWN');
CREATE INDEX idx_payment_attempt_refund_lookup
    ON payment_attempt (order_id, approved_at DESC, id DESC)
    WHERE status = 'APPROVED' AND pg_transaction_id IS NOT NULL;
