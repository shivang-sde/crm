-- ============================================================================
-- Offering Catalog Module
-- ============================================================================

CREATE TABLE offerings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    tenant_id UUID NOT NULL,
    owner_user_id UUID,
    created_by UUID NOT NULL,

    name VARCHAR(255) NOT NULL,
    code VARCHAR(100) NOT NULL,
    description TEXT,
    offering_type VARCHAR(40) NOT NULL,
    billing_type VARCHAR(40) NOT NULL,
    billing_interval VARCHAR(40),
    default_price NUMERIC(19,2),
    currency_code VARCHAR(3),
    default_term_days INTEGER,
    renewable BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    custom_data JSONB,
    updated_by UUID,

    CONSTRAINT fk_offerings_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_offerings_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES users(id),
    CONSTRAINT fk_offerings_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_offerings_updated_by FOREIGN KEY (updated_by) REFERENCES users(id),
    CONSTRAINT uq_offerings_tenant_code UNIQUE (tenant_id, code)
);

CREATE INDEX idx_offerings_tenant ON offerings(tenant_id);
CREATE INDEX idx_offerings_type ON offerings(tenant_id, offering_type);
CREATE INDEX idx_offerings_active ON offerings(tenant_id, is_active);
CREATE INDEX idx_offerings_owner ON offerings(tenant_id, owner_user_id);
CREATE INDEX idx_offerings_name ON offerings(tenant_id, name);
CREATE INDEX idx_offerings_custom_data ON offerings USING GIN(custom_data);


-- ============================================================================
-- Seed Offering RBAC Permissions
-- ============================================================================

INSERT INTO permissions (id, module, action, description)
VALUES
    ('dddddddd-dddd-dddd-dddd-000000000001', 'offering', 'write', 'Create catalog offerings'),
    ('dddddddd-dddd-dddd-dddd-000000000002', 'offering', 'read', 'View catalog offerings'),
    ('dddddddd-dddd-dddd-dddd-000000000003', 'offering', 'delete', 'Delete catalog offerings'),
    ('dddddddd-dddd-dddd-dddd-000000000004', 'offering', 'export', 'Export catalog offerings')
ON CONFLICT (module, action) DO NOTHING;
