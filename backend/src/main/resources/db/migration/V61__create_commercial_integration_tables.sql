-- ============================================================================
-- V61__create_commercial_integration_tables.sql
-- Commercial Integration V1: isolated mapping + API key auth
-- ============================================================================

-- 1. Integration external records mapping table
CREATE TABLE integration_external_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),

    external_type VARCHAR(30) NOT NULL CHECK (external_type IN ('CUSTOMER','QUOTATION','INVOICE')),
    external_id VARCHAR(255) NOT NULL,

    account_id UUID REFERENCES accounts(id),
    contact_id UUID REFERENCES contacts(id),
    deal_id UUID REFERENCES deals(id),

    external_updated_at TIMESTAMPTZ,
    last_synced_hash VARCHAR(128),
    last_synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_external_record_tenant_type_id UNIQUE (tenant_id, external_type, external_id)
);

CREATE INDEX idx_ext_rec_tenant_type ON integration_external_records(tenant_id, external_type);
CREATE INDEX idx_ext_rec_account ON integration_external_records(account_id) WHERE account_id IS NOT NULL;
CREATE INDEX idx_ext_rec_contact ON integration_external_records(contact_id) WHERE contact_id IS NOT NULL;
CREATE INDEX idx_ext_rec_deal ON integration_external_records(deal_id) WHERE deal_id IS NOT NULL;

-- 2. Commercial integration API keys (per-tenant)
CREATE TABLE commercial_integration_api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL UNIQUE REFERENCES tenants(id),
    key_hash VARCHAR(128) NOT NULL,
    key_prefix VARCHAR(12) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_commercial_key_prefix ON commercial_integration_api_keys(key_prefix);
CREATE UNIQUE INDEX uq_commercial_key_hash ON commercial_integration_api_keys(key_hash);
