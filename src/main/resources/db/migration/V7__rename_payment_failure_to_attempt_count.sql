ALTER TABLE daily_order_funnel_projection
    RENAME COLUMN payment_failure_count
    TO payment_failure_attempt_count;

ALTER TABLE daily_order_funnel_projection
    RENAME CONSTRAINT ck_daily_order_funnel_payment_failure_count
    TO ck_daily_order_funnel_payment_failure_attempt_count;