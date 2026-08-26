-- ============================================================================
-- Seed Acquisition (Lead Ingestion) RBAC Permissions
-- Module: acquisition
-- Actions map from HTTP methods via RbacFilter: GET=read, POST/PUT=write, DELETE=delete
-- ============================================================================

INSERT INTO permissions (id, module, action, description)
VALUES
    ('ffffffff-ffff-ffff-ffff-000000000001', 'acquisition', 'read',   'View lead ingestion configs, mappings and events'),
    ('ffffffff-ffff-ffff-ffff-000000000002', 'acquisition', 'write',  'Create and edit lead ingestion configs and mappings'),
    ('ffffffff-ffff-ffff-ffff-000000000003', 'acquisition', 'delete', 'Delete lead ingestion configs and mappings')
ON CONFLICT (module, action) DO NOTHING;


-- Backfill SUPERADMIN (platform)
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
JOIN permissions p
  ON p.module = 'acquisition'
WHERE r.name = 'SUPERADMIN'
  AND r.level = 'PLATFORM'
  AND r.tenant_id IS NULL
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- Backfill all existing tenant ADMIN roles
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
JOIN permissions p
  ON p.module = 'acquisition'
WHERE r.name = 'ADMIN'
  AND r.level = 'TENANT'
  AND r.tenant_id IS NOT NULL
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- Backfill all existing tenant MANAGER roles
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'TEAM'
FROM roles r
JOIN permissions p
  ON p.module = 'acquisition'
WHERE r.name = 'MANAGER'
  AND r.level = 'TENANT'
  AND r.tenant_id IS NOT NULL
  AND p.action IN ('read', 'write')
ON CONFLICT (role_id, permission_id) DO NOTHING;
