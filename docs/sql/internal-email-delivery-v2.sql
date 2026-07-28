-- Upgrade an existing internal_email_delivery table to the current entity shape.
-- Run this migration before deploying the claim-token based email worker.

BEGIN;

ALTER TABLE internal_email_delivery
    ADD COLUMN IF NOT EXISTS template_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS template_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS claim_token UUID,
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS lease_expired_count INTEGER NOT NULL DEFAULT 0;

-- Preserve values written by the previous schema, where the column was named template.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = current_schema()
           AND table_name = 'internal_email_delivery'
           AND column_name = 'template'
    ) THEN
        EXECUTE '
            UPDATE internal_email_delivery
               SET template_type = template
             WHERE template_type IS NULL
        ';
    END IF;
END
$$;

-- The current worker only creates PAYMENT_COMPLETED rows. This fallback protects
-- rows created while an intermediate schema was in use.
UPDATE internal_email_delivery
   SET template_type = 'PAYMENT_COMPLETED'
 WHERE template_type IS NULL;

ALTER TABLE internal_email_delivery
    ALTER COLUMN template_type SET NOT NULL;

-- A PROCESSING row created by the previous worker has no ownership token. It must
-- be released instead of being adopted by a new worker with ambiguous ownership.
UPDATE internal_email_delivery
   SET status = 'FAILED',
       next_attempt_at = CURRENT_TIMESTAMP,
       processing_started_at = NULL,
       claim_token = NULL,
       last_error = 'Released during claim-token migration'
 WHERE status = 'PROCESSING'
   AND claim_token IS NULL;

-- Clear stale ownership metadata from terminal or retryable states before adding
-- the ownership invariant.
UPDATE internal_email_delivery
   SET processing_started_at = NULL,
       claim_token = NULL
 WHERE status <> 'PROCESSING'
   AND (processing_started_at IS NOT NULL OR claim_token IS NOT NULL);

ALTER TABLE internal_email_delivery
    DROP CONSTRAINT IF EXISTS ck_internal_email_delivery_template_type,
    DROP CONSTRAINT IF EXISTS ck_internal_email_delivery_template_version,
    DROP CONSTRAINT IF EXISTS ck_internal_email_delivery_status,
    DROP CONSTRAINT IF EXISTS ck_internal_email_delivery_retry_count,
    DROP CONSTRAINT IF EXISTS ck_internal_email_delivery_attempt_count,
    DROP CONSTRAINT IF EXISTS ck_internal_email_delivery_lease_expired_count,
    DROP CONSTRAINT IF EXISTS ck_internal_email_delivery_processing_ownership;

ALTER TABLE internal_email_delivery
    ADD CONSTRAINT ck_internal_email_delivery_template_type
        CHECK (template_type IN ('PAYMENT_COMPLETED', 'ORDER_CANCELED', 'ORDER_EXPIRED')),
    ADD CONSTRAINT ck_internal_email_delivery_template_version
        CHECK (template_version >= 1),
    ADD CONSTRAINT ck_internal_email_delivery_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'DEAD')),
    ADD CONSTRAINT ck_internal_email_delivery_retry_count
        CHECK (retry_count >= 0),
    ADD CONSTRAINT ck_internal_email_delivery_attempt_count
        CHECK (attempt_count >= 0),
    ADD CONSTRAINT ck_internal_email_delivery_lease_expired_count
        CHECK (lease_expired_count >= 0),
    ADD CONSTRAINT ck_internal_email_delivery_processing_ownership
        CHECK (
            (status = 'PROCESSING' AND processing_started_at IS NOT NULL AND claim_token IS NOT NULL)
            OR
            (status <> 'PROCESSING' AND processing_started_at IS NULL AND claim_token IS NULL)
        );

DROP INDEX IF EXISTS idx_internal_email_delivery_claim;

CREATE INDEX idx_internal_email_delivery_claim
    ON internal_email_delivery (status, next_attempt_at, processing_started_at, created_at, id)
    INCLUDE (retry_count, attempt_count);

-- Drop the legacy column only after its values have been copied successfully.
ALTER TABLE internal_email_delivery
    DROP COLUMN IF EXISTS template;

COMMIT;
