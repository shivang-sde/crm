-- =========================================================
-- Add Task, Call and Meeting permissions
-- Backfill existing roles
-- Update future tenant role provisioning
-- =========================================================

-- 1. Insert permissions
INSERT INTO permissions (id, module, action, description)
VALUES
    -- Task
    ('cccccccc-cccc-cccc-cccc-000000000041', 'task', 'read',   'View task records'),
    ('cccccccc-cccc-cccc-cccc-000000000042', 'task', 'write',  'Create and edit tasks'),
    ('cccccccc-cccc-cccc-cccc-000000000043', 'task', 'delete', 'Delete tasks'),
    ('cccccccc-cccc-cccc-cccc-000000000044', 'task', 'assign', 'Assign task ownership'),
    ('cccccccc-cccc-cccc-cccc-000000000045', 'task', 'export', 'Export task data'),

    -- Call
    ('cccccccc-cccc-cccc-cccc-000000000046', 'call', 'read',   'View call records'),
    ('cccccccc-cccc-cccc-cccc-000000000047', 'call', 'write',  'Create and edit calls'),
    ('cccccccc-cccc-cccc-cccc-000000000048', 'call', 'delete', 'Delete calls'),
    ('cccccccc-cccc-cccc-cccc-000000000049', 'call', 'assign', 'Assign call ownership'),
    ('cccccccc-cccc-cccc-cccc-000000000050', 'call', 'export', 'Export call data'),

    -- Meeting
    ('cccccccc-cccc-cccc-cccc-000000000051', 'meeting', 'read',   'View meeting records'),
    ('cccccccc-cccc-cccc-cccc-000000000052', 'meeting', 'write',  'Create and edit meetings'),
    ('cccccccc-cccc-cccc-cccc-000000000053', 'meeting', 'delete', 'Delete meetings'),
    ('cccccccc-cccc-cccc-cccc-000000000054', 'meeting', 'assign', 'Assign meeting ownership'),
    ('cccccccc-cccc-cccc-cccc-000000000055', 'meeting', 'export', 'Export meeting data')
ON CONFLICT (module, action) DO NOTHING;

-- 2. Backfill SUPERADMIN
-- SUPERADMIN must receive permissions inserted after the original seed.
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
JOIN permissions p
  ON p.module IN ('task', 'call', 'meeting')
WHERE r.name = 'SUPERADMIN'
  AND r.level = 'PLATFORM'
  AND r.tenant_id IS NULL
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 3. Backfill all existing tenant ADMIN roles
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
JOIN permissions p
  ON p.module IN ('task', 'call', 'meeting')
WHERE r.name = 'ADMIN'
  AND r.level = 'TENANT'
  AND r.tenant_id IS NOT NULL
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 4. Backfill all existing tenant MANAGER roles
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'TEAM'
FROM roles r
JOIN permissions p
  ON p.module IN ('task', 'call', 'meeting')
WHERE r.name = 'MANAGER'
  AND r.level = 'TENANT'
  AND r.tenant_id IS NOT NULL
  AND p.action IN ('read', 'write', 'assign', 'export')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 5. Backfill all existing tenant EMPLOYEE roles
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'OWN'
FROM roles r
JOIN permissions p
  ON p.module IN ('task', 'call', 'meeting')
WHERE r.name = 'EMPLOYEE'
  AND r.level = 'TENANT'
  AND r.tenant_id IS NOT NULL
  AND p.action IN ('read', 'write')
ON CONFLICT (role_id, permission_id) DO NOTHING;