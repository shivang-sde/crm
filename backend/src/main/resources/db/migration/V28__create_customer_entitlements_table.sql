-- ============================================================================
-- Customer Entitlements Module
-- ============================================================================

CREATE TABLE IF NOT EXISTS customer_entitlements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    tenant_id UUID NOT NULL,
    owner_user_id UUID,
    created_by UUID NOT NULL,

    account_id UUID,
    contact_id UUID,
    offering_id UUID,
    deal_id UUID,
    deal_line_item_id UUID,
    name VARCHAR(255),
    code VARCHAR(100),
    description TEXT,
    status VARCHAR(30),
    start_date DATE,
    end_date DATE,
    quantity NUMERIC(19,4),
    agreed_price NUMERIC(19,2),
    currency_code VARCHAR(3),
    renewable BOOLEAN,
    auto_renew BOOLEAN,
    renewal_notice_days INTEGER,
    renewal_due_date DATE,
    renewed_from_entitlement_id UUID,
    renewed_to_entitlement_id UUID,
    renewal_deal_id UUID,
    custom_data JSONB,
    updated_by UUID,

    CONSTRAINT fk_customer_entitlements_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_customer_entitlements_owner FOREIGN KEY (owner_user_id) REFERENCES users(id),
    CONSTRAINT fk_customer_entitlements_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_customer_entitlements_updated_by FOREIGN KEY (updated_by) REFERENCES users(id),
    CONSTRAINT uq_customer_entitlements_deal_line_item UNIQUE (tenant_id, deal_line_item_id)
);

CREATE INDEX IF NOT EXISTS idx_customer_entitlements_tenant ON customer_entitlements(tenant_id);
CREATE INDEX IF NOT EXISTS idx_customer_entitlements_account ON customer_entitlements(tenant_id, account_id);
CREATE INDEX IF NOT EXISTS idx_customer_entitlements_contact ON customer_entitlements(tenant_id, contact_id);
CREATE INDEX IF NOT EXISTS idx_customer_entitlements_offering ON customer_entitlements(tenant_id, offering_id);
CREATE INDEX IF NOT EXISTS idx_customer_entitlements_deal ON customer_entitlements(tenant_id, deal_id);
CREATE INDEX IF NOT EXISTS idx_customer_entitlements_status ON customer_entitlements(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_customer_entitlements_end_date ON customer_entitlements(tenant_id, end_date);
CREATE INDEX IF NOT EXISTS idx_customer_entitlements_status_end ON customer_entitlements(tenant_id, status, end_date);
CREATE INDEX IF NOT EXISTS idx_customer_entitlements_owner ON customer_entitlements(tenant_id, owner_user_id);

INSERT INTO permissions (id, module, action, description)
VALUES
    ('eeeeeeee-eeee-eeee-eeee-000000000001', 'entitlement', 'read', 'View customer entitlements'),
    ('eeeeeeee-eeee-eeee-eeee-000000000002', 'entitlement', 'write', 'Update customer entitlements'),
    ('eeeeeeee-eeee-eeee-eeee-000000000003', 'entitlement', 'delete', 'Delete customer entitlements')
ON CONFLICT (module, action) DO NOTHING;




-- SUPERADMIN: all entitlement permissions
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'SUPERADMIN'
  AND p.module = 'entitlement'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
        AND rp.deleted = FALSE
  );


-- ADMIN: all entitlement permissions
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.module = 'entitlement'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
        AND rp.deleted = FALSE
  );


-- MANAGER: read + write
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'TEAM'
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'MANAGER'
  AND p.module = 'entitlement'
  AND p.action IN ('read', 'write')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
        AND rp.deleted = FALSE
  );


-- EMPLOYEE: read only
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'EMPLOYEE'
  AND p.module = 'entitlement'
  AND p.action IN ('read', 'write')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
        AND rp.deleted = FALSE
  );