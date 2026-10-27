-- ============================================================================
-- L3.1-P1: Support Ticket aggregate
-- ============================================================================
--
-- Purpose
-- -------
-- A Ticket is a tenant-owned customer-support record. It attaches to the
-- existing customer identity (Account, optionally Contact) and introduces no
-- competing Customer entity: Account remains canonical, which is also why
-- account_id is NOT NULL here and contact_id is nullable.
--
-- Why account_id is NOT NULL but contact_id is nullable
-- ------------------------------------------------------
-- Contact.account_id is itself NOT NULL (V7), so every Contact already belongs
-- to exactly one Account. That makes Account the root of any customer-linked
-- record. A Ticket may be raised by a customer with no named contact on file
-- (a phone call, an anonymous portal submission), so contact_id stays optional
-- and is validated against account_id by CustomerRelationshipValidator.
--
-- Nullability mirrors DealLineItem.currency_code (V69) in that nothing here is
-- defaulted on the application's behalf.
--
-- Soft delete
-- ----------
-- deleted / deleted_at / deleted_by implement the repository-wide soft-delete
-- convention introduced for Account/Contact/Deal in L3.0-P4. A Ticket is
-- customer correspondence: a hard delete would either fail on the FKs below or
-- destroy the record of what the customer reported. Hard delete is never used.
--
-- Idempotency
-- -----------
-- CREATE TABLE IF NOT EXISTS, and every permission/grant insert is guarded with
-- ON CONFLICT DO NOTHING, so re-running cannot fail or double-grant.
--
-- Tenant safety
-- -------------
-- Every query against tickets is tenant-scoped AND deleted = false. Note the
-- foreign keys below are deliberately TENANT-BLIND, exactly like every other
-- customer-domain FK in this schema (deals.account_id, deal_line_items.offering_id,
-- customer_entitlements.deal_id). That is why L3.0-P2 introduced
-- CustomerRelationshipValidator: a known UUID from another tenant would
-- otherwise be accepted by the FK. Ticket creation/update calls that validator
-- rather than relying on these constraints.
--
-- Reversibility
-- -------------
-- DROP TABLE tickets; and delete the three (module='ticket') permission rows
-- together with their role_permissions grants.
-- ============================================================================

CREATE TABLE IF NOT EXISTS tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id UUID NOT NULL,
    owner_user_id UUID,
    created_by UUID NOT NULL,
    updated_by UUID,

    account_id UUID NOT NULL,
    contact_id UUID,

    subject VARCHAR(255) NOT NULL,
    description TEXT,

    status VARCHAR(30) NOT NULL,

    resolved_at TIMESTAMP,
    closed_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by UUID,

    CONSTRAINT fk_tickets_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_tickets_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES users(id),
    CONSTRAINT fk_tickets_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_tickets_updated_by FOREIGN KEY (updated_by) REFERENCES users(id),
    CONSTRAINT fk_tickets_account_id FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_tickets_contact_id FOREIGN KEY (contact_id) REFERENCES contacts(id),
    CONSTRAINT fk_tickets_deleted_by FOREIGN KEY (deleted_by) REFERENCES users(id)
);

-- Indexes. Each maps to a query that actually exists:
--   (tenant_id, status)          list filtered by status
--   (tenant_id, owner_user_id)   OWN / TEAM record-scope filter
--   (tenant_id, account_id)      account-scoped list (Customer 360 readiness)
--   (tenant_id, created_at DESC) default list ordering
CREATE INDEX IF NOT EXISTS idx_tickets_tenant_status ON tickets(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_tickets_tenant_owner ON tickets(tenant_id, owner_user_id);
CREATE INDEX IF NOT EXISTS idx_tickets_tenant_account ON tickets(tenant_id, account_id);
CREATE INDEX IF NOT EXISTS idx_tickets_tenant_created_at ON tickets(tenant_id, created_at DESC);

-- ============================================================================
-- RBAC: ticket:read / ticket:write / ticket:delete
-- ============================================================================
--
-- Module: ticket, via RbacFilter's verb mapping (GET=read, POST/PUT/PATCH=write,
-- DELETE=delete). ticket:assign is deliberately NOT seeded here; assignment is
-- L3.1-P2. Until it exists, no URL can resolve to it.
--
-- PermissionEvaluatorService is fail-closed on an undefined permission, so these
-- rows must exist before /api/v1/tickets is reachable by anyone but SUPERADMIN.

INSERT INTO permissions (id, module, action, description)
VALUES
    ('aaaa1111-aaaa-aaaa-aaaa-000000000001', 'ticket', 'read', 'View support tickets'),
    ('aaaa1111-aaaa-aaaa-aaaa-000000000002', 'ticket', 'write', 'Create and edit support tickets'),
    ('aaaa1111-aaaa-aaaa-aaaa-000000000003', 'ticket', 'delete', 'Delete support tickets')
ON CONFLICT (module, action) DO NOTHING;

-- Backfill SUPERADMIN (platform) - follows the V40 acquisition / V64 records pattern
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
JOIN permissions p
  ON p.module = 'ticket'
WHERE r.name = 'SUPERADMIN'
  AND r.level = 'PLATFORM'
  AND r.tenant_id IS NULL
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Backfill all existing tenant ADMIN roles
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
JOIN permissions p
  ON p.module = 'ticket'
WHERE r.name = 'ADMIN'
  AND r.level = 'TENANT'
  AND r.tenant_id IS NOT NULL
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Backfill existing tenant MANAGER roles at TEAM scope. Support work is
-- team-visible by default, matching every other customer-domain module
-- (deal / entitlement / account) which is granted to MANAGER.
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'TEAM'
FROM roles r
JOIN permissions p
  ON p.module = 'ticket'
WHERE r.name = 'MANAGER'
  AND r.level = 'TENANT'
  AND r.tenant_id IS NOT NULL
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Backfill existing tenant EMPLOYEE roles at OWN scope. The action filter is the
-- same {read, write} set used everywhere else, so EMPLOYEE never acquires
-- ticket:delete through any seeding path.
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'OWN'
FROM roles r
JOIN permissions p
  ON p.module = 'ticket'
WHERE r.name = 'EMPLOYEE'
  AND r.level = 'TENANT'
  AND r.tenant_id IS NOT NULL
  AND p.action IN ('read', 'write')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Update trigger for future tenant provisioning to include 'ticket'
-- Keep in sync with DefaultRoleConfig.ADMIN_MODULES / MANAGER_MODULES /
-- EMPLOYEE_MODULES. Skipping this is the failure mode where existing tenants
-- have ticket permissions but newly provisioned tenants never do.
CREATE OR REPLACE FUNCTION create_tenant_default_roles()
RETURNS TRIGGER AS $$
DECLARE
    admin_role_id UUID;
    manager_role_id UUID;
    employee_role_id UUID;
BEGIN
    BEGIN
        INSERT INTO roles (id, name, level, tenant_id, parent_role_id, description)
        VALUES (gen_random_uuid(), 'ADMIN', 'TENANT', NEW.id, NULL,
                'Tenant administrator - full access to all tenant records')
        RETURNING id INTO admin_role_id;

        INSERT INTO roles (id, name, level, tenant_id, parent_role_id, description)
        VALUES (gen_random_uuid(), 'MANAGER', 'TENANT', NEW.id, NULL,
                'Manager - access to team records')
        RETURNING id INTO manager_role_id;

        INSERT INTO roles (id, name, level, tenant_id, parent_role_id, description)
        VALUES (gen_random_uuid(), 'EMPLOYEE', 'TENANT', NEW.id, NULL,
                'Employee - own records only')
        RETURNING id INTO employee_role_id;

        INSERT INTO role_permissions (role_id, permission_id, access_scope)
        SELECT admin_role_id, p.id, 'ALL'
        FROM permissions p
        WHERE p.module IN ('lead', 'contact', 'account', 'deal', 'activity', 'task', 'call', 'meeting',
                          'report', 'workflow', 'admin', 'offering', 'entitlement', 'acquisition', 'records',
                          'ticket')
        ON CONFLICT (role_id, permission_id) DO NOTHING;

        INSERT INTO role_permissions (role_id, permission_id, access_scope)
        SELECT manager_role_id, p.id, 'TEAM'
        FROM permissions p
        WHERE p.module IN ('lead', 'contact', 'account', 'deal', 'activity', 'task', 'call', 'meeting',
                          'report', 'offering', 'entitlement', 'acquisition', 'ticket')
          AND p.action IN ('read', 'write', 'assign', 'export')
        ON CONFLICT (role_id, permission_id) DO NOTHING;

        INSERT INTO role_permissions (role_id, permission_id, access_scope)
        SELECT employee_role_id, p.id, 'OWN'
        FROM permissions p
        WHERE p.module IN ('lead', 'contact', 'account', 'deal', 'activity', 'task', 'call', 'meeting',
                          'entitlement', 'ticket')
          AND p.action IN ('read', 'write')
        ON CONFLICT (role_id, permission_id) DO NOTHING;

        RAISE NOTICE 'Created default roles (ADMIN, MANAGER, EMPLOYEE) for tenant %', NEW.id;

    EXCEPTION WHEN OTHERS THEN
        INSERT INTO trigger_errors (trigger_name, table_name, record_id, error_message, error_detail)
        VALUES (
            'trigger_create_tenant_roles',
            'tenants',
            NEW.id,
            SQLERRM,
            SQLSTATE
        );
        RAISE WARNING 'Error creating default roles for tenant %: % (SQLSTATE: %)',
                      NEW.id, SQLERRM, SQLSTATE;
    END;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tenant_creation_trigger ON tenants;
CREATE TRIGGER tenant_creation_trigger
    AFTER INSERT ON tenants
    FOR EACH ROW
    EXECUTE FUNCTION create_tenant_default_roles();
