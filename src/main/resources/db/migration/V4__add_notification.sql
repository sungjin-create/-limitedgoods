CREATE TABLE notification (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    source_event_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(100) NOT NULL,
    message VARCHAR(500) NOT NULL,
    link VARCHAR(500),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    read_at TIMESTAMP WITHOUT TIME ZONE,

    CONSTRAINT fk_notification_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

    CONSTRAINT uq_notification_source_event
    UNIQUE (source_event_id),

    CONSTRAINT ck_notification_type
    CHECK (type IN (
              'ORDER_PAID',
              'ORDER_REFUNDED',
              'ORDER_EXPIRED',
              'PRODUCT_SALE_STARTED'
    ))
);

CREATE INDEX idx_notification_user_created
    ON notification (user_id, created_at DESC, id DESC);

CREATE INDEX idx_notification_user_unread
    ON notification (user_id, id DESC)
    WHERE is_read = FALSE;