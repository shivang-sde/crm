-- ============================================================================
-- WF-42: Records Platform Foundation
-- Tenant-defined Record Types, Fields, and Records (JSONB)
-- ============================================================================

-- 1. RECORD TYPES
CREATE TABLE record_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    type_key VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID,
    owner_user_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    CONSTRAINT uq_record_types_tenant_key UNIQUE (tenant_id, type_key),
    CONSTRAINT uq_record_types_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT chk_record_types_key_format CHECK (type_key ~ '^[a-z][a-z0-9_]*$')
);

CREATE INDEX idx_record_types_tenant ON record_types(tenant_id);
CREATE INDEX idx_record_types_tenant_active ON record_types(tenant_id, is_active) WHERE deleted = FALSE;
CREATE INDEX idx_record_types_tenant_deleted ON record_types(tenant_id, deleted);

-- 2. RECORD FIELDS
CREATE TABLE record_fields (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    record_type_id UUID NOT NULL REFERENCES record_types(id) ON DELETE CASCADE,
    field_key VARCHAR(100) NOT NULL,
    field_label VARCHAR(200) NOT NULL,
    field_type VARCHAR(30) NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 0,
    options_json JSONB,
    default_value TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    CONSTRAINT uq_record_fields_type_key UNIQUE (record_type_id, field_key),
    CONSTRAINT chk_record_fields_key_format CHECK (field_key ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT chk_record_fields_type CHECK (field_type IN (
        'TEXT','LONG_TEXT','INTEGER','DECIMAL','BOOLEAN','DATE','DATETIME','ENUM','URL','PHONE','EMAIL','JSON'
    ))
);

CREATE INDEX idx_record_fields_tenant ON record_fields(tenant_id);
CREATE INDEX idx_record_fields_type ON record_fields(record_type_id);
CREATE INDEX idx_record_fields_type_active ON record_fields(record_type_id, is_active) WHERE deleted = FALSE;
CREATE INDEX idx_record_fields_tenant_key ON record_fields(tenant_id, field_key);

-- 3. RECORDS (actual instances)
CREATE TABLE records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    record_type_id UUID NOT NULL REFERENCES record_types(id),
    data JSONB NOT NULL DEFAULT '{}'::jsonb,
    owner_user_id UUID,
    created_by UUID NOT NULL,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID
);

CREATE INDEX idx_records_tenant ON records(tenant_id);
CREATE INDEX idx_records_tenant_type ON records(tenant_id, record_type_id) WHERE deleted = FALSE;
CREATE INDEX idx_records_type ON records(record_type_id);
CREATE INDEX idx_records_created_at ON records(created_at DESC);
CREATE INDEX idx_records_data_gin ON records USING GIN(data);
