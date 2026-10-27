-- ============================================================================
-- L3.0-P3: entitlement:assign permission
-- ============================================================================
--
-- Why this migration is required
-- ------------------------------
-- CustomerEntitlementService.update() accepted owner_user_id under the generic
-- 'entitlement:write' permission. Every other owned CRM module in this
-- repository separates assignment from generic write with its own '<module>:assign'
-- permission (see V4 for lead/contact/account/deal/activity, V12 for
-- task/call/meeting). The entitlement module was the one module missing it, so
-- anyone who could edit an entitlement field could also hand that entitlement to
-- another user.
--
-- 'assign' is therefore the established action vocabulary being reused, not a
-- new permission concept. No new module, no new table, no change to the RBAC
-- schema.
--
-- Grants
-- ------
--   SUPERADMIN / ADMIN : ALL  (matches every other module's admin grant)
--   MANAGER            : TEAM (matches V28's MANAGER entitlement grant)
--   EMPLOYEE           : NOT granted.
--
-- EMPLOYEE is deliberately excluded. DefaultRoleConfig.EMPLOYEE_ACTIONS is
-- {read, write} and the create_tenant_default_roles() trigger grants EMPLOYEE
-- only those two actions, so EMPLOYEE never acquires entitlement:assign through
-- either seeding path. P1's correction (EMPLOYEE entitlement read/write = OWN)
-- is untouched by this migration.
--
-- Idempotency
-- -----------
-- ON CONFLICT (module, action) DO NOTHING on the permission row, and
-- ON CONFLICT (role_id, permission_id) DO NOTHING on each grant, matching the
-- convention used by V25/V27/V28/V50/V64.
-- ============================================================================

INSERT INTO permissions (id, module, action, description)
VALUES
    ('eeeeeeee-eeee-eeee-eeee-000000000004', 'entitlement', 'assign',
     'Assign customer entitlement ownership')
ON CONFLICT (module, action) DO NOTHING;


-- SUPERADMIN: all entitlement permissions
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'SUPERADMIN'
  AND p.module = 'entitlement'
  AND p.action = 'assign'
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
  AND p.action = 'assign'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
        AND rp.deleted = FALSE
  );


-- MANAGER: assignment at TEAM, matching this module's existing MANAGER grants
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'TEAM'
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'MANAGER'
  AND p.module = 'entitlement'
  AND p.action = 'assign'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
        AND rp.deleted = FALSE
  );


-- Verification (run after applying)
-- ---------------------------------
-- Expected: SUPERADMIN=ALL, ADMIN=ALL, MANAGER=TEAM, EMPLOYEE absent.
--
-- SELECT r.name, rp.access_scope
-- FROM role_permissions rp
-- JOIN roles r ON r.id = rp.role_id
-- JOIN permissions p ON p.id = rp.permission_id
-- WHERE p.module = 'entitlement' AND p.action = 'assign'
--   AND COALESCE(rp.deleted, FALSE) = FALSE
-- ORDER BY r.name;
--
-- EMPLOYEE must not appear above.
-- ============================================================================