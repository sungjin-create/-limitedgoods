ALTER TABLE daily_order_funnel_projection
    RENAME COLUMN canceled_order_count
    TO refunded_order_count;

ALTER TABLE daily_order_funnel_projection
    RENAME CONSTRAINT ck_daily_order_funnel_canceled_order_count
    TO ck_daily_order_funnel_refunded_order_count;