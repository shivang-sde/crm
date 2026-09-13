-- ============================================================================
-- WF-41: Fix tenant default role trigger drift (acquisition/offering/entitlement)
-- Java source of truth: DefaultRoleConfig.java
--   ADMIN_MODULES: lead, contact, account, deal, activity, task, call, meeting,
--                  report, workflow, admin, offering, entitlement, acquisition
--   MANAGER_MODULES: lead, contact, account, deal, activity, task, call, meeting,
--                    report, offering, entitlement, acquisition
--                    + actions {read, write, assign, export}
--   EMPLOYEE_MODULES: lead, contact, account, deal, activity, task, call, meeting,
--                     entitlement  + actions {read, write}
-- Previous trigger (V5) was last synced 2026-05-29 and lacked offering/entitlement/acquisition.
-- This migration:
--   1) Replaces create_tenant_default_roles() with parity-correct module lists
--   2) Backfills existing tenant ADMIN/MANAGER/EMPLOYEE roles idempotently
--   3) Does NOT touch PLATFORM roles (SUPERADMIN/RESELLER) or custom tenant roles
--   4) Uses ON CONFLICT DO NOTHING for idempotency, no DELETEs
-- ============================================================================

CREATE OR REPLACE FUNCTION create_tenant_default_roles()
RETURNS TRIGGER AS $$
DECLARE
    admin_role_id UUID;
    manager_role_id UUID;
    employee_role_id UUID;
BEGIN
    BEGIN
        -- Create ADMIN role for this tenant
        INSERT INTO roles (id, name, level, tenant_id, parent_role_id, description)
        VALUES (gen_random_uuid(), 'ADMIN', 'TENANT', NEW.id, NULL,
                'Tenant administrator - full access to all tenant records')
        RETURNING id INTO admin_role_id;

        -- Create MANAGER role for this tenant
        INSERT INTO roles (id, name, level, tenant_id, parent_role_id, description)
        VALUES (gen_random_uuid(), 'MANAGER', 'TENANT', NEW.id, NULL,
                'Manager - access to team records')
        RETURNING id INTO manager_role_id;

        -- Create EMPLOYEE role for this tenant
        INSERT INTO roles (id, name, level, tenant_id, parent_role_id, description)
        VALUES (gen_random_uuid(), 'EMPLOYEE', 'TENANT', NEW.id, NULL,
                'Employee - own records only')
        RETURNING id INTO employee_role_id;

        -- Assign ADMIN permissions (matches DefaultRoleConfig.ADMIN_MODULES = all actions for those modules)
        INSERT INTO role_permissions (role_id, permission_id, access_scope)
        SELECT admin_role_id, p.id, 'ALL'
        FROM permissions p
        WHERE p.module IN ('lead', 'contact', 'account', 'deal', 'activity', 'task', 'call', 'meeting',
                          'report', 'workflow', 'admin', 'offering', 'entitlement', 'acquisition')
        ON CONFLICT (role_id, permission_id) DO NOTHING;

        -- Assign MANAGER permissions (matches DefaultRoleConfig.MANAGER_MODULES + MANAGER_ACTIONS)
        INSERT INTO role_permissions (role_id, permission_id, access_scope)
        SELECT manager_role_id, p.id, 'TEAM'
        FROM permissions p
        WHERE p.module IN ('lead', 'contact', 'account', 'deal', 'activity', 'task', 'call', 'meeting',
                          'report', 'offering', 'entitlement', 'acquisition')
          AND p.action IN ('read', 'write', 'assign', 'export')
        ON CONFLICT (role_id, permission_id) DO NOTHING;

        -- Assign EMPLOYEE permissions (matches DefaultRoleConfig.EMPLOYEE_MODULES + EMPLOYEE_ACTIONS)
        INSERT INTO role_permissions (role_id, permission_id, access_scope)
        SELECT employee_role_id, p.id, 'OWN'
        FROM permissions p
        WHERE p.module IN ('lead', 'contact', 'account', 'deal', 'activity', 'task', 'call', 'meeting',
                          'entitlement')
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

-- Ensure trigger exists (re-create idempotently)
DROP TRIGGER IF EXISTS tenant_creation_trigger ON tenants;
CREATE TRIGGER tenant_creation_trigger
    AFTER INSERT ON tenants
    FOR EACH ROW
    EXECUTE FUNCTION create_tenant_default_roles();

-- ============================================================================
-- Backfill existing tenant roles that were created while trigger was stale
-- Target: only default TENANT-level ADMIN/MANAGER/EMPLOYEE roles (tenant_id IS NOT NULL)
-- Never touches PLATFORM roles (SUPERADMIN/RESELLER where tenant_id IS NULL)
-- ============================================================================

-- Backfill ADMIN: offering, entitlement, acquisition (ALL actions for those modules, scope ALL)
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
CROSS JOIN permissions p
WHERE r.level = 'TENANT'
  AND r.name = 'ADMIN'
  AND r.tenant_id IS NOT NULL
  AND p.module IN ('offering', 'entitlement', 'acquisition')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Backfill MANAGER: offering, entitlement, acquisition (only actions in MANAGER_ACTIONS, scope TEAM)
-- offering: read/write/export (delete excluded), entitlement/acquisition: read/write
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'TEAM'
FROM roles r
CROSS JOIN permissions p
WHERE r.level = 'TENANT'
  AND r.name = 'MANAGER'
  AND r.tenant_id IS NOT NULL
  AND p.module IN ('offering', 'entitlement', 'acquisition')
  AND p.action IN ('read', 'write', 'assign', 'export')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Backfill EMPLOYEE: entitlement only, read/write, scope OWN
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'OWN'
FROM roles r
CROSS JOIN permissions p
WHERE r.level = 'TENANT'
  AND r.name = 'EMPLOYEE'
  AND r.tenant_id IS NOT NULL
  AND p.module = 'entitlement'
  AND p.action IN ('read', 'write')
ON CONFLICT (role_id, permission_id) DO NOTHING;
