-- ============================================================================
-- L3.0-P1 Change B: EMPLOYEE entitlement access scope ALL -> OWN
-- ============================================================================
--
-- Defect
-- ------
-- V28__create_customer_entitlements_table.sql granted EMPLOYEE the
-- entitlement:read and entitlement:write permissions at access_scope 'ALL'
-- (see that migration's EMPLOYEE insert, which selected 'ALL').
--
-- V50__fix_tenant_roles_trigger_acquisition.sql later re-declared the
-- create_tenant_default_roles() trigger to grant EMPLOYEE entitlement at
-- scope 'OWN', and its backfill also used 'OWN'. However that backfill is an
-- INSERT ... ON CONFLICT DO NOTHING, so for every tenant provisioned while
-- V28's grant was already present the existing 'ALL' row was left untouched.
--
-- Result: EMPLOYEE could read and mutate every entitlement in the tenant,
-- contradicting the documented EMPLOYEE contract ("own records only") and the
-- Java source of truth DefaultRoleConfig.EMPLOYEE_ACCESS_SCOPE = 'OWN'.
--
-- Fix
-- ---
-- Correct only the incorrect rows. No INSERT, no DELETE, no permission or role
-- recreation, and no change to ADMIN, MANAGER, or any other module.
--
-- Idempotency
-- -----------
-- Guarded by `access_scope = 'ALL'`, so re-running the migration is a no-op.
--
-- Verification (run after applying)
-- ---------------------------------
-- SELECT r.name, r.level, p.module, p.action, rp.access_scope
-- FROM role_permissions rp
-- JOIN roles r ON r.id = rp.role_id
-- JOIN permissions p ON p.id = rp.permission_id
-- WHERE r.name = 'EMPLOYEE'
--   AND p.module = 'entitlement'
--   AND COALESCE(rp.deleted, FALSE) = FALSE
-- ORDER BY r.level, p.action;
-- Expected: access_scope = 'OWN' on every row.
-- ============================================================================

UPDATE role_permissions rp
SET access_scope = 'OWN',
    updated_at = NOW()
FROM roles r, permissions p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.name = 'EMPLOYEE'
  AND r.level = 'TENANT'
  AND r.tenant_id IS NOT NULL
  AND p.module = 'entitlement'
  AND p.action IN ('read', 'write')
  AND rp.access_scope = 'ALL'
  AND COALESCE(rp.deleted, FALSE) = FALSE;