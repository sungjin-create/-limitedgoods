ALTER TABLE daily_sales_projection
    ADD COLUMN refunded_quantity BIGINT NOT NULL DEFAULT 0;

ALTER TABLE daily_sales_projection
    ADD CONSTRAINT ck_daily_sales_projection_refunded_quantity
        CHECK (refunded_quantity >= 0);