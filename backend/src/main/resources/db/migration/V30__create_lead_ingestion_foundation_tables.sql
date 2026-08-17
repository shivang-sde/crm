-- ============================================================================
-- Lead ingestion persistence foundation (provider-agnostic)
-- ============================================================================

CREATE TABLE lead_ingestion_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    transport_type VARCHAR(30) NOT NULL,
    public_key VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    settings JSONB,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT fk_lead_ingestion_configs_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uq_lead_ingestion_configs_public_key UNIQUE (public_key)
);

CREATE INDEX idx_lead_ingestion_configs_tenant_id ON lead_ingestion_configs(tenant_id);

CREATE TABLE lead_ingestion_field_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    ingestion_config_id UUID NOT NULL,

    source_path VARCHAR(500) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_field VARCHAR(100) NOT NULL,

    transform_type VARCHAR(30) NOT NULL DEFAULT 'NONE',
    transform_config JSONB,

    default_value TEXT,

    required BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT fk_lead_ingestion_field_mappings_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_lead_ingestion_field_mappings_config_id FOREIGN KEY (ingestion_config_id) REFERENCES lead_ingestion_configs(id)
);

CREATE INDEX idx_lead_ingestion_field_mappings_tenant_config ON lead_ingestion_field_mappings(tenant_id, ingestion_config_id);

CREATE TABLE lead_ingestion_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    ingestion_config_id UUID NOT NULL,

    external_event_id VARCHAR(255),
    idempotency_key VARCHAR(1024),

    raw_payload JSONB NOT NULL,
    headers JSONB,

    status VARCHAR(30) NOT NULL,

    lead_id UUID,

    error_code VARCHAR(100),
    error_message TEXT,

    received_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT fk_lead_ingestion_events_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_lead_ingestion_events_config_id FOREIGN KEY (ingestion_config_id) REFERENCES lead_ingestion_configs(id)
);

CREATE INDEX idx_lead_ingestion_events_tenant_config ON lead_ingestion_events(tenant_id, ingestion_config_id);
CREATE INDEX idx_lead_ingestion_events_tenant_status ON lead_ingestion_events(tenant_id, status);
CREATE INDEX idx_lead_ingestion_events_lead_id ON lead_ingestion_events(lead_id);