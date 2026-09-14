-- ============================================================================
-- WF-48: Record Webhook Control Plane
-- Tenant-scoped incoming webhook definitions for Records module
-- ============================================================================

CREATE TABLE record_webhooks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(200) NOT NULL,
    webhook_key VARCHAR(100) NOT NULL,
    description TEXT,
    record_type_id UUID NOT NULL REFERENCES record_types(id) ON DELETE CASCADE,
    mapping_profile_id UUID REFERENCES record_mapping_profiles(id) ON DELETE SET NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID,
    owner_user_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    CONSTRAINT uq_record_webhooks_tenant_key UNIQUE (tenant_id, webhook_key),
    CONSTRAINT chk_webhook_key_format CHECK (webhook_key ~ '^[a-z][a-z0-9_-]*$')
);

CREATE INDEX idx_record_webhooks_tenant ON record_webhooks(tenant_id);
CREATE INDEX idx_record_webhooks_tenant_type ON record_webhooks(tenant_id, record_type_id) WHERE deleted = FALSE;
CREATE INDEX idx_record_webhooks_tenant_active ON record_webhooks(tenant_id, is_active) WHERE deleted = FALSE;
CREATE INDEX idx_record_webhooks_type ON record_webhooks(record_type_id);
CREATE INDEX idx_record_webhooks_mapping ON record_webhooks(mapping_profile_id) WHERE mapping_profile_id IS NOT NULL;
