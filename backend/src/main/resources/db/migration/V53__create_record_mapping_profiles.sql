-- ============================================================================
-- WF-46: Mapping Profile Foundation
-- Tenant-scoped reusable profiles that map external payload -> canonical RecordType fields
-- ============================================================================

CREATE TABLE record_mapping_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    record_type_id UUID NOT NULL REFERENCES record_types(id) ON DELETE CASCADE,
    mapping_key VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    mode VARCHAR(20) NOT NULL CHECK (mode IN ('DIRECT','CUSTOM')),
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID,
    owner_user_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    CONSTRAINT uq_mapping_profiles_tenant_key UNIQUE (tenant_id, mapping_key),
    CONSTRAINT chk_mapping_key_format CHECK (mapping_key ~ '^[a-z][a-z0-9_]*$')
);

CREATE INDEX idx_mapping_profiles_tenant ON record_mapping_profiles(tenant_id);
CREATE INDEX idx_mapping_profiles_tenant_type ON record_mapping_profiles(tenant_id, record_type_id) WHERE deleted = FALSE;
CREATE INDEX idx_mapping_profiles_tenant_active ON record_mapping_profiles(tenant_id, is_active) WHERE deleted = FALSE;
CREATE INDEX idx_mapping_profiles_type ON record_mapping_profiles(record_type_id);
