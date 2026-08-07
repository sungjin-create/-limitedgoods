ALTER TABLE outbox_event DROP CONSTRAINT ck_outbox_status;

ALTER TABLE outbox_event ADD COLUMN dead_at TIMESTAMP WITHOUT TIME ZONE;

ALTER TABLE outbox_event ALTER COLUMN next_attempt_at DROP NOT NULL;

UPDATE outbox_event
SET next_attempt_at = NULL
WHERE status = 'PUBLISHED';

ALTER TABLE outbox_event
    ADD CONSTRAINT ck_outbox_status
        CHECK (status IN (
                          'PENDING',
                          'FAILED',
                          'PUBLISHED',
                          'DEAD'
            ));

ALTER TABLE outbox_event ADD CONSTRAINT ck_outbox_attempts CHECK (attempts >= 0);

ALTER TABLE outbox_event
ADD CONSTRAINT ck_outbox_next_attempt
    CHECK (
        (
            status IN ('PENDING', 'FAILED')
                AND next_attempt_at IS NOT NULL
            )
            OR
        (
            status IN ('PUBLISHED', 'DEAD')
                AND next_attempt_at IS NULL
            )
        );

CREATE INDEX idx_outbox_dead
ON outbox_event (dead_at, created_at)
WHERE status = 'DEAD';