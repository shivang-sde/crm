-- ===========================================================================
-- Create connector_webhook_mappings table and add event_headers + idempotency_key
-- ===========================================================================

-- Create mappings table if not exists
CREATE TABLE IF NOT EXISTS connector_webhook_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_instance_id UUID NOT NULL,
    trigger_key VARCHAR(100) NOT NULL,
    source_path VARCHAR(500) NOT NULL,
    target_scope VARCHAR(100) NOT NULL,
    target_path VARCHAR(200) NOT NULL,
    transform_type VARCHAR(100),
    default_value TEXT,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID
);

CREATE INDEX IF NOT EXISTS idx_connector_webhook_mappings_tenant ON connector_webhook_mappings(tenant_id);
CREATE INDEX IF NOT EXISTS idx_connector_webhook_mappings_connector ON connector_webhook_mappings(connector_instance_id);
CREATE INDEX IF NOT EXISTS idx_connector_webhook_mappings_connector_trigger ON connector_webhook_mappings(connector_instance_id, trigger_key);
CREATE INDEX IF NOT EXISTS idx_connector_webhook_mappings_connector_trigger_active ON connector_webhook_mappings(connector_instance_id, trigger_key, is_active);

-- Add event_headers column to connector_webhook_events if missing
ALTER TABLE connector_webhook_events
    ADD COLUMN IF NOT EXISTS event_headers JSONB;

-- Add idempotency_key column for deduplication
ALTER TABLE connector_webhook_events
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(1024);

-- Add index/unique constraint for idempotency key per tenant + connector
CREATE INDEX IF NOT EXISTS idx_connector_webhook_events_idempotency ON connector_webhook_events(tenant_id, connector_instance_id, idempotency_key);
CREATE UNIQUE INDEX IF NOT EXISTS uq_connector_webhook_events_idempotency ON connector_webhook_events(tenant_id, connector_instance_id, idempotency_key) WHERE idempotency_key IS NOT NULL AND deleted = false;
