-- ============================================================================
-- Lead Management Module - Database Schema
-- Version: 1.0
-- Description: Creates all tables required for lead management functionality
-- ============================================================================

-- V6__add_soft_delete_columns.sql

ALTER TABLE users
ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS deleted_by UUID;

ALTER TABLE roles
ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS deleted_by UUID;

ALTER TABLE permissions
ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS deleted_by UUID;

ALTER TABLE tenants
ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS deleted_by UUID;

ALTER TABLE user_roles
ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS deleted_by UUID;

ALTER TABLE permissions
ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS deleted_by UUID;

ALTER TABLE role_permissions
ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS deleted_by UUID;

-- add remaining tables


-- ============================================================================
-- 1. LEAD STATUSES - Tenant-specific lead statuses
-- ============================================================================
CREATE TABLE lead_statuses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    
    name VARCHAR(100) NOT NULL,
    color VARCHAR(20),
    display_order INTEGER DEFAULT 0,
    is_default BOOLEAN DEFAULT FALSE,
    is_closed BOOLEAN DEFAULT FALSE,
    
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),


    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    
    CONSTRAINT fk_lead_statuses_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT unique_tenant_status_name UNIQUE(tenant_id, name)
);

CREATE INDEX idx_lead_statuses_tenant_id ON lead_statuses(tenant_id);
CREATE INDEX idx_lead_statuses_is_default ON lead_statuses(tenant_id, is_default);

-- ============================================================================
-- 2. LEAD SOURCES - Where leads come from
-- ============================================================================
CREATE TABLE lead_sources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    
    name VARCHAR(100) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    
    CONSTRAINT fk_lead_sources_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT unique_tenant_source_name UNIQUE(tenant_id, name)
);

CREATE INDEX idx_lead_sources_tenant_id ON lead_sources(tenant_id);
CREATE INDEX idx_lead_sources_is_active ON lead_sources(tenant_id, is_active);

-- ============================================================================
-- 3. LEAD CUSTOM FIELDS - Definition of custom fields per tenant
-- ============================================================================
CREATE TABLE lead_custom_fields (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    
    field_key VARCHAR(100) NOT NULL,
    field_label VARCHAR(200) NOT NULL,
    field_type VARCHAR(50) NOT NULL,  -- TEXT, TEXTAREA, NUMBER, EMAIL, PHONE, DATE, BOOLEAN, SELECT, MULTISELECT, URL
    
    is_required BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    display_order INTEGER DEFAULT 0,
    
    -- JSON array for SELECT/MULTISELECT options: [{"label":"Option 1","value":"opt_1"},...]
    options_json JSONB,
    
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- delete
    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    
    CONSTRAINT fk_lead_custom_fields_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT unique_tenant_field_key UNIQUE(tenant_id, field_key)
);

CREATE INDEX idx_lead_custom_fields_tenant_id ON lead_custom_fields(tenant_id);
CREATE INDEX idx_lead_custom_fields_active ON lead_custom_fields(tenant_id, is_active);

-- ============================================================================
-- 4. LEADS - Main lead table with standard CRM fields + JSONB for custom data
-- ============================================================================
CREATE TABLE leads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    
    -- Standard CRM Fields
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100),
    email VARCHAR(255),
    phone VARCHAR(20),
    company VARCHAR(200),
    
    -- Status & Source
    status_id UUID NOT NULL,
    source_id UUID,
    
    -- Ownership & Audit
    owner_user_id UUID,
    created_by UUID NOT NULL,
    updated_by UUID,
    
    -- Lead Tracking
    score INTEGER DEFAULT 0,
    
    -- Conversion Tracking
    is_converted BOOLEAN DEFAULT FALSE,
    converted_at TIMESTAMP,
    
    -- CRITICAL: Custom fields stored in JSONB (NOT EAV pattern)
    -- Structure: {"field_key": "value", "vehicle_type": "SUV", "budget": "1500000"}
    custom_data JSONB DEFAULT '{}',
    
    -- Audit Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),


    -- delete 
    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    
    CONSTRAINT fk_leads_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_leads_status_id FOREIGN KEY (status_id) REFERENCES lead_statuses(id),
    CONSTRAINT fk_leads_source_id FOREIGN KEY (source_id) REFERENCES lead_sources(id),
    CONSTRAINT fk_leads_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES users(id),
    CONSTRAINT fk_leads_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_leads_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);

-- Performance indexes
CREATE UNIQUE INDEX uq_lead_email ON leads(tenant_id, email) WHERE deleted = false;
CREATE UNIQUE INDEX uq_lead_phone ON leads(tenant_id, phone) WHERE deleted = false;
CREATE INDEX idx_leads_tenant_id ON leads(tenant_id);
CREATE INDEX idx_leads_status_id ON leads(status_id);
CREATE INDEX idx_leads_owner_user_id ON leads(owner_user_id);
CREATE INDEX idx_leads_email ON leads(tenant_id, email);
CREATE INDEX idx_leads_phone ON leads(tenant_id, phone);
CREATE INDEX idx_leads_created_at ON leads(tenant_id, created_at DESC);
CREATE INDEX idx_leads_is_converted ON leads(tenant_id, is_converted);
CREATE INDEX idx_leads_search ON leads(tenant_id, first_name, last_name, email, phone);

-- JSONB index for custom field queries
CREATE INDEX idx_leads_custom_data ON leads USING GIN(custom_data);

-- ============================================================================
-- 5. LEAD ACTIVITIES - Immutable audit trail of all lead actions
-- ============================================================================
CREATE TABLE lead_activities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    lead_id UUID NOT NULL,
    
    activity_type VARCHAR(50) NOT NULL,  -- LEAD_CREATED, STATUS_CHANGED, OWNER_CHANGED, NOTE_ADDED, etc.
    description TEXT,
    performed_by UUID NOT NULL,
    
    -- Metadata for storing old/new values, call details, email info, etc.
    metadata JSONB DEFAULT '{}',
    
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- delete 
    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    
    CONSTRAINT fk_lead_activities_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_lead_activities_lead_id FOREIGN KEY (lead_id) REFERENCES leads(id) ON DELETE CASCADE,
    CONSTRAINT fk_lead_activities_performed_by FOREIGN KEY (performed_by) REFERENCES users(id)
);

-- Performance indexes
CREATE INDEX idx_lead_activities_lead_id ON lead_activities(lead_id);
CREATE INDEX idx_lead_activities_tenant_id ON lead_activities(tenant_id);
CREATE INDEX idx_lead_activities_created_at ON lead_activities(created_at DESC);
CREATE INDEX idx_lead_activities_activity_type ON lead_activities(activity_type);
CREATE INDEX idx_lead_activities_performed_by ON lead_activities(performed_by);


-- ============================================================================
-- 6. LEAD NOTES - Simple note management for leads
-- ============================================================================
CREATE TABLE entity_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,

    entity_type VARCHAR(50) NOT NULL,  -- LEAD, CONTACT, ACCOUNT, etc.
    entity_id UUID NOT NULL ,
    
    note TEXT NOT NULL,
    created_by UUID NOT NULL,
    updated_by UUID,
    
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    
    CONSTRAINT fk_entity_notes_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_entity_notes_entity_id FOREIGN KEY (entity_id) REFERENCES leads(id) ON DELETE CASCADE,
    CONSTRAINT fk_entity_notes_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_entity_notes_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);

-- Performance indexes
CREATE INDEX idx_entity_notes_entity_id ON entity_notes(entity_id);
CREATE INDEX idx_entity_notes_tenant_id ON entity_notes(tenant_id);
CREATE INDEX idx_entity_notes_created_at ON entity_notes(created_at DESC);



-- ============================================================================
-- LEAD HISTORY - Audit trail for lead changes
-- ============================================================================

CREATE TABLE entity_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,

    tenant_id UUID NOT NULL,

    event_type VARCHAR(50) NOT NULL,
    description TEXT,

    performed_by UUID NOT NULL,

    changes JSONB DEFAULT '{}',

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),



    CONSTRAINT fk_entity_history_performed_by
        FOREIGN KEY (performed_by)
        REFERENCES users(id)
);

-- ============================================================================
-- Indexes
-- ============================================================================

CREATE INDEX idx_entity_history_tenant_id
    ON entity_history(tenant_id);

CREATE INDEX idx_entity_history_entity_id
    ON entity_history(entity_id);

CREATE INDEX idx_entity_history_event_type
    ON entity_history(event_type);

CREATE INDEX idx_entity_history_performed_by
    ON entity_history(performed_by);

CREATE INDEX idx_entity_history_created_at
    ON entity_history(created_at DESC);

CREATE INDEX idx_entity_notes_lookup
ON entity_notes(entity_type, entity_id);

-- Useful for timeline queries
CREATE INDEX idx_entity_history_entity_created
    ON entity_history(entity_id, created_at DESC);

-- Optional: if you'll query inside changes JSON
CREATE INDEX idx_entity_history_changes
    ON entity_history
    USING GIN(changes);

-- ============================================================================
-- Default seed data for first lead status
-- ============================================================================

-- This will be populated by a separate V7__seed_lead_defaults.sql migration
-- after we understand the tenant structure better.

-- ============================================================================
-- END OF MIGRATION
-- ============================================================================
