-- ============================================================================
-- Demo Data Module
-- ============================================================================

CREATE TABLE IF NOT EXISTS tenant_demo_installations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    template_key VARCHAR(100) NOT NULL,
    template_version INTEGER NOT NULL,
    installed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    installed_by UUID NOT NULL,
    summary JSONB,
    CONSTRAINT uq_tenant_demo_installation UNIQUE (tenant_id, template_key, template_version)
);

CREATE TABLE IF NOT EXISTS demo_data_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    template_key VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_demo_data_records_tenant ON demo_data_records(tenant_id);
CREATE INDEX IF NOT EXISTS idx_demo_data_records_tenant_template ON demo_data_records(tenant_id, template_key);
CREATE INDEX IF NOT EXISTS idx_demo_data_records_tenant_entity ON demo_data_records(tenant_id, entity_type);
