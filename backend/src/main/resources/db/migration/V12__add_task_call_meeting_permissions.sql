-- ============================================
-- Add Task, Call, Meeting Module Permissions
-- ============================================

-- Add permissions for Task, Call, and Meeting modules
INSERT INTO permissions (id, module, action, description) VALUES
    -- Task module
    ('cccccccc-cccc-cccc-cccc-000000000041', 'task', 'read', 'View task records'),
    ('cccccccc-cccc-cccc-cccc-000000000042', 'task', 'write', 'Create and edit tasks'),
    ('cccccccc-cccc-cccc-cccc-000000000043', 'task', 'delete', 'Delete tasks'),
    ('cccccccc-cccc-cccc-cccc-000000000044', 'task', 'assign', 'Assign task ownership'),
    ('cccccccc-cccc-cccc-cccc-000000000045', 'task', 'export', 'Export task data'),
    
    -- Call module
    ('cccccccc-cccc-cccc-cccc-000000000046', 'call', 'read', 'View call records'),
    ('cccccccc-cccc-cccc-cccc-000000000047', 'call', 'write', 'Create and edit calls'),
    ('cccccccc-cccc-cccc-cccc-000000000048', 'call', 'delete', 'Delete calls'),
    ('cccccccc-cccc-cccc-cccc-000000000049', 'call', 'assign', 'Assign call ownership'),
    ('cccccccc-cccc-cccc-cccc-000000000050', 'call', 'export', 'Export call data'),
    
    -- Meeting module
    ('cccccccc-cccc-cccc-cccc-000000000051', 'meeting', 'read', 'View meeting records'),
    ('cccccccc-cccc-cccc-cccc-000000000052', 'meeting', 'write', 'Create and edit meetings'),
    ('cccccccc-cccc-cccc-cccc-000000000053', 'meeting', 'delete', 'Delete meetings'),
    ('cccccccc-cccc-cccc-cccc-000000000054', 'meeting', 'assign', 'Assign meeting ownership'),
    ('cccccccc-cccc-cccc-cccc-000000000055', 'meeting', 'export', 'Export meeting data')
ON CONFLICT DO NOTHING;

-- Grant task, call, meeting permissions to ADMIN role (ALL scope)
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r, permissions p
WHERE r.name = 'ADMIN' 
  AND p.module IN ('task', 'call', 'meeting')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp 
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Grant task, call, meeting read/write/assign/export permissions to MANAGER role
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 
    CASE 
        WHEN p.action IN ('read', 'write', 'assign', 'export') THEN 'TEAM'
        ELSE 'OWN'
    END
FROM roles r, permissions p
WHERE r.name = 'MANAGER' 
  AND p.module IN ('task', 'call', 'meeting')
  AND p.action IN ('read', 'write', 'assign', 'export')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp 
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Grant task, call, meeting read/write permissions to EMPLOYEE role (OWN scope)
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'OWN'
FROM roles r, permissions p
WHERE r.name = 'EMPLOYEE' 
  AND p.module IN ('task', 'call', 'meeting')
  AND p.action IN ('read', 'write')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp 
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
