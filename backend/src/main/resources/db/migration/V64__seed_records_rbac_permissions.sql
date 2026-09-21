-- ============================================================================
-- WF-63: Seed Records RBAC Permissions and backfill ADMIN/SUPERADMIN
-- Module: records (read, write, delete) via RbacFilter GET=read, POST/PUT=write, DELETE=delete
-- Idempotent via ON CONFLICT
-- ============================================================================

INSERT INTO permissions (id, module, action, description)
VALUES
    ('77777777-7777-7777-7777-000000000001', 'records', 'read', 'View records and record type data'),
    ('77777777-7777-7777-7777-000000000002', 'records', 'write', 'Create and edit records and record data'),
    ('77777777-7777-7777-7777-000000000003', 'records', 'delete', 'Delete records')
ON CONFLICT (module, action) DO NOTHING;

-- Backfill SUPERADMIN (platform) - follows V40 acquisition pattern
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
JOIN permissions p
  ON p.module = 'records'
WHERE r.name = 'SUPERADMIN'
  AND r.level = 'PLATFORM'
  AND r.tenant_id IS NULL
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Backfill all existing tenant ADMIN roles
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
JOIN permissions p
  ON p.module = 'records'
WHERE r.name = 'ADMIN'
  AND r.level = 'TENANT'
  AND r.tenant_id IS NOT NULL
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Update trigger for future tenant provisioning to include 'records'
-- Keep in sync with DefaultRoleConfig.ADMIN_MODULES
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
                          'report', 'workflow', 'admin', 'offering', 'entitlement', 'acquisition', 'records')
        ON CONFLICT (role_id, permission_id) DO NOTHING;

        INSERT INTO role_permissions (role_id, permission_id, access_scope)
        SELECT manager_role_id, p.id, 'TEAM'
        FROM permissions p
        WHERE p.module IN ('lead', 'contact', 'account', 'deal', 'activity', 'task', 'call', 'meeting',
                          'report', 'offering', 'entitlement', 'acquisition')
          AND p.action IN ('read', 'write', 'assign', 'export')
        ON CONFLICT (role_id, permission_id) DO NOTHING;

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

DROP TRIGGER IF EXISTS tenant_creation_trigger ON tenants;
CREATE TRIGGER tenant_creation_trigger
    AFTER INSERT ON tenants
    FOR EACH ROW
    EXECUTE FUNCTION create_tenant_default_roles();
