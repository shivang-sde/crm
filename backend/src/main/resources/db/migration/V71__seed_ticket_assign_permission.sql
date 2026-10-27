-- ============================================================================
-- L3.1-P2: ticket:assign permission
-- ============================================================================
--
-- Why this migration is required
-- ------------------------------
-- V70 seeded ticket:read, ticket:write and ticket:delete only. L3.1-P2 introduces
-- POST /api/v1/tickets/{id}/assign as the one canonical way to change Ticket
-- ownership, gated on its own 'ticket:assign' permission.
--
-- 'assign' is the established action vocabulary in this schema, not a new concept:
-- V4 granted it for lead/contact/account/deal/activity, V12 for
-- task/call/meeting, and V68 added entitlement:assign precisely because
-- entitlement was the one module letting 'entitlement:write' imply reassignment.
-- Ticket would repeat that defect if assignment rode on 'ticket:write'.
--
-- Grants
-- ------
--   SUPERADMIN : ALL
--   ADMIN      : ALL   (matches every other module's admin grant)
--   MANAGER    : TEAM  (matches V28's MANAGER entitlement grant)
--   EMPLOYEE   : NOT granted.
--
-- EMPLOYEE is deliberately excluded, matching V68 and DefaultRoleConfig
-- .EMPLOYEE_ACTIONS = {read, write}. An employee may raise a ticket and set an
-- INITIAL owner at creation, but cannot route an existing one to somebody else.
-- Neither seeding path grants it: not this backfill, not the trigger re-declared
-- below, and not the Java default-role config.
--
-- P1 boundary preserved
-- ---------------------
-- V70's ticket:read/write/delete rows and all of its existing grants are untouched.
-- P2 adds a permission; it does not widen or renumber anything.
--
-- V70 is NOT edited
-- -----------------
-- V70 may already be applied in an environment, so it is left exactly as written
-- and this forward migration carries the new permission instead.
--
-- Idempotency
-- -----------
-- ON CONFLICT (module, action) DO NOTHING on the permission row, and
-- ON CONFLICT (role_id, permission_id) DO NOTHING on each grant. This matches
-- V64/V70 and is safe to re-run. Note role_permissions carries a soft-delete
-- 'deleted' column (V6) and its UNIQUE is on (role_id, permission_id) regardless,
-- so ON CONFLICT correctly no-ops whether or not a prior row was soft-deleted.
--
-- Verification (run after applying)
-- ---------------------------------
-- SELECT r.name, r.level, rp.access_scope
-- FROM role_permissions rp
-- JOIN roles r ON r.id = rp.role_id
-- JOIN permissions p ON p.id = rp.permission_id
-- WHERE p.module = 'ticket' AND p.action = 'assign'
--   AND COALESCE(rp.deleted, FALSE) = FALSE
-- ORDER BY r.name;
-- Expected: SUPERADMIN=ALL, ADMIN=ALL, MANAGER=TEAM. EMPLOYEE must NOT appear.
-- ============================================================================

INSERT INTO permissions (id, module, action, description)
VALUES
    ('aaaa1111-aaaa-aaaa-aaaa-000000000004', 'ticket', 'assign',
     'Assign support ticket ownership to an agent')
ON CONFLICT (module, action) DO NOTHING;


-- SUPERADMIN: all ticket permissions
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
JOIN permissions p
  ON p.module = 'ticket'
WHERE r.name = 'SUPERADMIN'
  AND r.level = 'PLATFORM'
  AND r.tenant_id IS NULL
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ADMIN: all ticket permissions
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
JOIN permissions p
  ON p.module = 'ticket'
WHERE r.name = 'ADMIN'
  AND r.level = 'TENANT'
  AND r.tenant_id IS NOT NULL
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- MANAGER: assignment at TEAM, matching this module's existing MANAGER grants.
-- The action is not filtered because 'assign' does not exist for any other ticket
-- action, but p.action = 'assign' is stated anyway so the grant stays correct if
-- more ticket actions are added later.
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'TEAM'
FROM roles r
JOIN permissions p
  ON p.module = 'ticket'
WHERE r.name = 'MANAGER'
  AND r.level = 'TENANT'
  AND r.tenant_id IS NOT NULL
  AND p.action = 'assign'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ============================================================================
-- Future tenant provisioning
-- ============================================================================
--
-- V70's create_tenant_default_roles() already grants MANAGER the ticket module
-- with p.action IN ('read','write','assign','export'), so new tenants would pick up
-- ticket:assign automatically. The function is re-declared here anyway for two
-- reasons:
--
--   1. It is the documented place that owns the tenant-default role policy, and
--      V68's precedent is to keep the whole function current in one place.
--   2. It states the EMPLOYEE exclusion explicitly. The trigger selects
--      p.action IN ('read','write') for EMPLOYEE, so 'assign' is excluded there
--      already; restating the intent next to it means a future edit that broadens
--      the EMPLOYEE action list cannot silently hand every employee ticket
--      assignment.
--
-- The function body is otherwise identical to V70's. Only the ADMIN module list,
-- the MANAGER module list and the EMPLOYEE module list are reproduced here, and
-- they are unchanged from V70 apart from being restated.
-- ============================================================================

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

        -- ADMIN: every permission in every module, including ticket:assign.
        INSERT INTO role_permissions (role_id, permission_id, access_scope)
        SELECT admin_role_id, p.id, 'ALL'
        FROM permissions p
        WHERE p.module IN ('lead', 'contact', 'account', 'deal', 'activity', 'task', 'call', 'meeting',
                          'report', 'workflow', 'admin', 'offering', 'entitlement', 'acquisition', 'records',
                          'ticket')
        ON CONFLICT (role_id, permission_id) DO NOTHING;

        -- MANAGER: ticket is included and the action list contains 'assign', so new
        -- tenants grant ticket:assign at TEAM. Must stay in sync with
        -- DefaultRoleConfig.MANAGER_ACTIONS.
        INSERT INTO role_permissions (role_id, permission_id, access_scope)
        SELECT manager_role_id, p.id, 'TEAM'
        FROM permissions p
        WHERE p.module IN ('lead', 'contact', 'account', 'deal', 'activity', 'task', 'call', 'meeting',
                          'report', 'offering', 'entitlement', 'acquisition', 'ticket')
          AND p.action IN ('read', 'write', 'assign', 'export')
        ON CONFLICT (role_id, permission_id) DO NOTHING;

        -- EMPLOYEE: explicitly {read, write}. 'assign' is absent by construction —
        -- this is what keeps an employee from routing tickets to other agents.
        -- Must stay in sync with DefaultRoleConfig.EMPLOYEE_ACTIONS.
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
