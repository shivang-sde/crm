-- ============================================================================
-- Deal Management Module - Database Schema
-- Version: 1.0
-- Description: Creates all tables required for deal/opportunity management
-- ============================================================================

-- ============================================================================
-- 1. DEAL STAGES - Tenant configurable deal stages/pipeline
-- ============================================================================
CREATE TABLE deal_stages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,

    name VARCHAR(100) NOT NULL,
    color VARCHAR(20),
    display_order INTEGER DEFAULT 0,
    is_default BOOLEAN DEFAULT FALSE,
    is_closed BOOLEAN DEFAULT FALSE,
    record_category VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    default_probability INTEGER DEFAULT 0,
    default_forecast_category VARCHAR(30) DEFAULT 'PIPELINE',

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT fk_deal_stages_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT unique_tenant_stage_name UNIQUE(tenant_id, name)
);

CREATE INDEX idx_deal_stages_tenant_id ON deal_stages(tenant_id);
CREATE INDEX idx_deal_stages_is_default ON deal_stages(tenant_id, is_default);
CREATE INDEX idx_deal_stages_display_order ON deal_stages(tenant_id, display_order);
CREATE INDEX idx_deal_stages_record_category ON deal_stages(tenant_id, record_category);

-- ============================================================================
-- 2. DEALS - Main deal/opportunity table
-- ============================================================================
CREATE TABLE deals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,

    name VARCHAR(255) NOT NULL,

    -- Relationships
    account_id UUID,
    contact_id UUID,
    stage_id UUID NOT NULL,
    lead_id UUID,

    -- Financial & Timeline
    amount NUMERIC(18, 2),
    expected_close_date DATE,
    probability INTEGER DEFAULT 0,
    expected_revenue NUMERIC(18, 2),
    forecast_category VARCHAR(30),
    next_step TEXT,
    deal_type VARCHAR(30),
    lead_source VARCHAR(100),
    campaign_source VARCHAR(255),
    closed_date DATE,
    won_reason TEXT,
    lost_reason TEXT,

    description TEXT,

    -- Ownership
    owner_user_id UUID,
    created_by UUID NOT NULL,
    updated_by UUID,

    -- Custom Data (JSONB for extensibility)
    custom_data JSONB,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT fk_deals_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_deals_stage_id FOREIGN KEY (stage_id) REFERENCES deal_stages(id),
    CONSTRAINT fk_deals_account_id FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_deals_contact_id FOREIGN KEY (contact_id) REFERENCES contacts(id),
    CONSTRAINT fk_deals_lead_id FOREIGN KEY (lead_id) REFERENCES leads(id),
    CONSTRAINT fk_deals_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES users(id),
    CONSTRAINT fk_deals_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_deals_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);

CREATE INDEX idx_deal_tenant ON deals(tenant_id);
CREATE INDEX idx_deal_stage_id ON deals(stage_id);
CREATE INDEX idx_deal_owner_user_id ON deals(owner_user_id);
CREATE INDEX idx_deal_account_id ON deals(account_id);
CREATE INDEX idx_deal_contact_id ON deals(contact_id);
CREATE INDEX idx_deal_lead_id ON deals(lead_id);
CREATE INDEX idx_deal_created_at ON deals(created_at);
CREATE INDEX idx_deal_expected_close_date ON deals(expected_close_date);
CREATE INDEX idx_deal_closed_date ON deals(closed_date);
CREATE INDEX idx_deal_forecast_category ON deals(tenant_id, forecast_category);
CREATE INDEX idx_deal_custom_data ON deals USING GIN(custom_data);

-- ============================================================================
-- 3. DEAL ACTIVITIES - Deal-specific activity timeline
-- ============================================================================
CREATE TABLE deal_activities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    deal_id UUID NOT NULL,

    activity_type VARCHAR(50) NOT NULL,
    description TEXT,
    performed_by UUID NOT NULL,

    metadata JSONB,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_deal_activities_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_deal_activities_deal_id FOREIGN KEY (deal_id) REFERENCES deals(id),
    CONSTRAINT fk_deal_activities_performed_by FOREIGN KEY (performed_by) REFERENCES users(id)
);

CREATE INDEX idx_deal_activity_tenant ON deal_activities(tenant_id);
CREATE INDEX idx_deal_activity_deal_id ON deal_activities(deal_id);
CREATE INDEX idx_deal_activity_type ON deal_activities(activity_type);
CREATE INDEX idx_deal_activity_created_at ON deal_activities(created_at);


-- ============================================================================
-- Deal Custom Fields - Definition of custom fields per tenant
-- ============================================================================
CREATE TABLE deal_custom_fields (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    tenant_id UUID NOT NULL,
    field_key VARCHAR(100) NOT NULL,
    field_label VARCHAR(200) NOT NULL,
    field_type VARCHAR(50) NOT NULL,
    is_required BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    display_order INTEGER DEFAULT 0,
    options_json JSONB,

    CONSTRAINT unique_deal_custom_field_key UNIQUE (tenant_id, field_key),
    CONSTRAINT fk_deal_custom_fields_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE INDEX idx_deal_custom_fields_tenant_id ON deal_custom_fields(tenant_id);
CREATE INDEX idx_deal_custom_fields_active ON deal_custom_fields(tenant_id, is_active);
