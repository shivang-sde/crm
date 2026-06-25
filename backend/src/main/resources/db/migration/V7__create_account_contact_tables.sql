-- ============================================================================
-- Account and Contact Module - Database Schema
-- Version: 1.0
-- Description: Creates accounts, contacts, and custom field tables; fixes generic entity notes foreign key
-- ============================================================================

-- ============================================================================
-- 1. ACCOUNT CUSTOM FIELDS
-- ============================================================================
CREATE TABLE account_custom_fields (
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

    CONSTRAINT unique_account_custom_field_key UNIQUE (tenant_id, field_key),
    CONSTRAINT fk_account_custom_fields_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE INDEX idx_account_custom_fields_tenant_id ON account_custom_fields(tenant_id);
CREATE INDEX idx_account_custom_fields_active ON account_custom_fields(tenant_id, is_active);

-- ============================================================================
-- 2. CONTACT CUSTOM FIELDS
-- ============================================================================
CREATE TABLE contact_custom_fields (
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

    CONSTRAINT unique_contact_custom_field_key UNIQUE (tenant_id, field_key),
    CONSTRAINT fk_contact_custom_fields_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE INDEX idx_contact_custom_fields_tenant_id ON contact_custom_fields(tenant_id);
CREATE INDEX idx_contact_custom_fields_active ON contact_custom_fields(tenant_id, is_active);

-- ============================================================================
-- 3. ACTIVITIES
-- ============================================================================
CREATE TABLE activities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    tenant_id UUID NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    description TEXT,
    performed_by UUID NOT NULL,
    metadata JSONB,

    CONSTRAINT fk_activities_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_activities_performed_by FOREIGN KEY (performed_by) REFERENCES users(id)
);

CREATE INDEX idx_activity_tenant ON activities(tenant_id);
CREATE INDEX idx_activity_entity ON activities(entity_type, entity_id);
CREATE INDEX idx_activity_type ON activities(activity_type);
CREATE INDEX idx_activity_created_at ON activities(created_at);

-- ============================================================================
-- 4. ACCOUNTS
-- ============================================================================
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    tenant_id UUID NOT NULL,
    owner_user_id UUID,
    created_by UUID NOT NULL,

    name VARCHAR(255) NOT NULL,
    website VARCHAR(255),
    industry VARCHAR(100),
    phone VARCHAR(50),
    email VARCHAR(255),
    annual_revenue NUMERIC,
    employee_count INTEGER,
    description TEXT,
    country VARCHAR(100),
    state VARCHAR(100),
    city VARCHAR(100),
    address_line1 VARCHAR(255),
    postal_code VARCHAR(20),
    lead_id UUID,
    custom_data JSONB,
    is_active BOOLEAN DEFAULT TRUE,
    updated_by UUID,

    CONSTRAINT fk_accounts_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_accounts_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES users(id),
    CONSTRAINT fk_accounts_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_accounts_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);

CREATE INDEX idx_account_tenant ON accounts(tenant_id);
CREATE INDEX idx_account_owner ON accounts(owner_user_id);
CREATE INDEX idx_account_name ON accounts(name);
CREATE INDEX idx_account_industry ON accounts(industry);
CREATE INDEX idx_account_created_at ON accounts(created_at);
CREATE INDEX idx_account_is_active ON accounts(tenant_id, is_active);
CREATE INDEX idx_account_lead_id ON accounts(lead_id);
CREATE INDEX idx_account_custom_data ON accounts USING GIN(custom_data);

-- ============================================================================
-- 4. CONTACTS
-- ============================================================================
CREATE TABLE contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    tenant_id UUID NOT NULL,
    owner_user_id UUID,
    created_by UUID NOT NULL,

    account_id UUID NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    email VARCHAR(255),
    phone VARCHAR(50),
    mobile VARCHAR(50),
    job_title VARCHAR(150),
    department VARCHAR(100),
    lead_id UUID,
    custom_data JSONB,
    is_primary BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    updated_by UUID,

    CONSTRAINT fk_contacts_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_contacts_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES users(id),
    CONSTRAINT fk_contacts_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_contacts_updated_by FOREIGN KEY (updated_by) REFERENCES users(id),
    CONSTRAINT fk_contacts_account_id FOREIGN KEY (account_id) REFERENCES accounts(id)
);

CREATE INDEX idx_contact_tenant ON contacts(tenant_id);
CREATE INDEX idx_contact_account ON contacts(account_id);
CREATE INDEX idx_contact_owner ON contacts(owner_user_id);
CREATE INDEX idx_contact_email ON contacts(email);
CREATE INDEX idx_contact_phone ON contacts(phone);
CREATE INDEX idx_contact_is_primary ON contacts(is_primary);
CREATE INDEX idx_contact_is_active ON contacts(tenant_id, is_active);
CREATE INDEX idx_contact_custom_data ON contacts USING GIN(custom_data);

-- ============================================================================
-- 5. GENERIC ENTITY NOTES FIX
-- ============================================================================
ALTER TABLE entity_notes DROP CONSTRAINT IF EXISTS fk_entity_notes_entity_id;

CREATE INDEX IF NOT EXISTS idx_entity_notes_entity ON entity_notes(entity_type, entity_id);
