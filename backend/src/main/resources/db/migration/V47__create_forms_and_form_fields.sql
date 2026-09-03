-- ============================================================================
-- LEAD-FORM-1: CRM Form Builder foundation
-- ============================================================================

CREATE TABLE forms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    public_key VARCHAR(255),
    acquisition_config_id UUID,
    settings JSONB,
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    CONSTRAINT fk_forms_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_forms_acquisition_config_id FOREIGN KEY (acquisition_config_id) REFERENCES lead_ingestion_configs(id),
    CONSTRAINT uq_forms_public_key UNIQUE (public_key)
);

CREATE INDEX idx_forms_tenant_id ON forms(tenant_id);
CREATE INDEX idx_forms_tenant_status ON forms(tenant_id, status);
CREATE INDEX idx_forms_public_key ON forms(public_key) WHERE public_key IS NOT NULL;

CREATE TABLE form_fields (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    form_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    field_key VARCHAR(100) NOT NULL,
    type VARCHAR(30) NOT NULL,
    label VARCHAR(200) NOT NULL,
    placeholder VARCHAR(200),
    help_text TEXT,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    order_index INTEGER NOT NULL,
    default_value TEXT,
    options JSONB,
    crm_target_type VARCHAR(30),
    crm_target_field VARCHAR(100),
    transform_type VARCHAR(30) NOT NULL DEFAULT 'NONE',
    transform_config JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    CONSTRAINT fk_form_fields_form_id FOREIGN KEY (form_id) REFERENCES forms(id) ON DELETE CASCADE,
    CONSTRAINT fk_form_fields_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uq_form_fields_form_key UNIQUE (form_id, field_key)
);

CREATE INDEX idx_form_fields_form_id ON form_fields(form_id);
CREATE INDEX idx_form_fields_form_order ON form_fields(form_id, order_index);
