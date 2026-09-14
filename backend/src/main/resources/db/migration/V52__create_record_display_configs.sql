-- ============================================================================
-- WF-44: Record Display Configuration
-- Tenant-owned per-RecordType display layout (List + Detail) stored as JSONB document
-- ============================================================================

CREATE TABLE record_display_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    record_type_id UUID NOT NULL REFERENCES record_types(id) ON DELETE CASCADE,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    CONSTRAINT uq_record_display_configs_type UNIQUE (record_type_id)
);

CREATE INDEX idx_record_display_configs_tenant ON record_display_configs(tenant_id);
CREATE INDEX idx_record_display_configs_type ON record_display_configs(record_type_id);
CREATE INDEX idx_record_display_configs_tenant_type ON record_display_configs(tenant_id, record_type_id) WHERE deleted = FALSE;
