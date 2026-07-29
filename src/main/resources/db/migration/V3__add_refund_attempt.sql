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

    reconcile_count INTEGER NOT NULL DEFAULT 0,
    next_reconcile_at TIMESTAMP WITHOUT TIME ZONE,
    manual_review_required BOOLEAN NOT NULL DEFAULT FALSE,

    requested_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    refunded_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT fk_refund_attempt_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_refund_attempt_payment
        FOREIGN KEY (payment_attempt_id)
        REFERENCES payment_attempt(id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_refund_attempt_order_key
        UNIQUE (order_id, idempotency_key),

    CONSTRAINT uq_refund_attempt_pg_refund
        UNIQUE (pg_refund_id),

    CONSTRAINT ck_refund_attempt_amount
        CHECK (amount > 0),

    CONSTRAINT ck_refund_attempt_status
        CHECK (
            status IN (
                'PROCESSING',
                'APPROVED',
                'DECLINED',
                'UNKNOWN'
            )
        )
);

CREATE INDEX idx_refund_attempt_reconciliation
    ON refund_attempt (
        manual_review_required,
        next_reconcile_at,
        updated_at,
        id
    )
    WHERE status IN ('PROCESSING', 'UNKNOWN');

ALTER TABLE payment_attempt
    ADD COLUMN approved_amount BIGINT,
    ADD COLUMN compensation_required BOOLEAN NOT NULL DEFAULT FALSE;