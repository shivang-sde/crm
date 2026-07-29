-- ============================================================================
-- Align connector_webhook_events schema
-- ============================================================================

ALTER TABLE connector_webhook_events
    ADD COLUMN IF NOT EXISTS created_by UUID,
    ADD COLUMN IF NOT EXISTS updated_by UUID,
    ADD COLUMN IF NOT EXISTS event_headers JSONB,
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(1024);

CREATE INDEX IF NOT EXISTS idx_connector_webhook_events_idempotency
    ON connector_webhook_events (
        tenant_id,
        connector_instance_id,
        idempotency_key
    );

CREATE UNIQUE INDEX IF NOT EXISTS uq_connector_webhook_events_idempotency
    ON connector_webhook_events (
        tenant_id,
        connector_instance_id,
        idempotency_key
    )
    WHERE idempotency_key IS NOT NULL
      AND deleted = FALSE;

-- ============================================================================
-- Support system-generated activities
-- ============================================================================

ALTER TABLE activities
    ALTER COLUMN performed_by DROP NOT NULL;

ALTER TABLE activities
    ADD COLUMN IF NOT EXISTS actor_type VARCHAR(30) NOT NULL DEFAULT 'USER',
    ADD COLUMN IF NOT EXISTS actor_source VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_activities_actor_type
    ON activities(actor_type);

CREATE INDEX IF NOT EXISTS idx_activities_actor_source
    ON activities(actor_source);

-- Allow calls to be created by external/system actors
ALTER TABLE calls
    ALTER COLUMN created_by DROP NOT NULL;

ALTER TABLE calls
    ADD COLUMN IF NOT EXISTS actor_type VARCHAR(30) NOT NULL DEFAULT 'USER',
    ADD COLUMN IF NOT EXISTS actor_source VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_calls_actor_type
    ON calls(actor_type);

CREATE INDEX IF NOT EXISTS idx_calls_actor_source
    ON calls(actor_source);