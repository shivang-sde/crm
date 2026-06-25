-- ============================================
-- Seed Platform Roles & Permissions
-- ============================================

-- Platform Roles
INSERT INTO roles (id, name, level, tenant_id, parent_role_id, description)
VALUES
(
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'SUPERADMIN',
    'PLATFORM',
    NULL,
    NULL,
    'Platform owner - full system access across all tenants'
),
(
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    'RESELLER',
    'PLATFORM',
    NULL,
    NULL,
    'White-label partner managing sub-tenants'
)
ON CONFLICT DO NOTHING;

-- Permissions - All modules and actions (Using fixed UUIDs for critical permissions)
INSERT INTO permissions (id, module, action, description) VALUES
    -- Lead module
    ('cccccccc-cccc-cccc-cccc-000000000001', 'lead', 'read', 'View lead records'),
    ('cccccccc-cccc-cccc-cccc-000000000002', 'lead', 'write', 'Create and edit leads'),
    ('cccccccc-cccc-cccc-cccc-000000000003', 'lead', 'delete', 'Delete leads'),
    ('cccccccc-cccc-cccc-cccc-000000000004', 'lead', 'assign', 'Assign lead ownership'),
    ('cccccccc-cccc-cccc-cccc-000000000005', 'lead', 'export', 'Export lead data'),
    
    -- Contact module
    ('cccccccc-cccc-cccc-cccc-000000000006', 'contact', 'read', 'View contact records'),
    ('cccccccc-cccc-cccc-cccc-000000000007', 'contact', 'write', 'Create and edit contacts'),
    ('cccccccc-cccc-cccc-cccc-000000000008', 'contact', 'delete', 'Delete contacts'),
    ('cccccccc-cccc-cccc-cccc-000000000009', 'contact', 'assign', 'Assign contact ownership'),
    ('cccccccc-cccc-cccc-cccc-000000000010', 'contact', 'export', 'Export contact data'),
    
    -- Account module
    ('cccccccc-cccc-cccc-cccc-000000000011', 'account', 'read', 'View account records'),
    ('cccccccc-cccc-cccc-cccc-000000000012', 'account', 'write', 'Create and edit accounts'),
    ('cccccccc-cccc-cccc-cccc-000000000013', 'account', 'delete', 'Delete accounts'),
    ('cccccccc-cccc-cccc-cccc-000000000014', 'account', 'assign', 'Assign account ownership'),
    ('cccccccc-cccc-cccc-cccc-000000000015', 'account', 'export', 'Export account data'),
    
    -- Deal module
    ('cccccccc-cccc-cccc-cccc-000000000016', 'deal', 'read', 'View deal/opportunity records'),
    ('cccccccc-cccc-cccc-cccc-000000000017', 'deal', 'write', 'Create and edit deals'),
    ('cccccccc-cccc-cccc-cccc-000000000018', 'deal', 'delete', 'Delete deals'),
    ('cccccccc-cccc-cccc-cccc-000000000019', 'deal', 'assign', 'Assign deal ownership'),
    ('cccccccc-cccc-cccc-cccc-000000000020', 'deal', 'export', 'Export deal data'),
    
    -- Activity module
    ('cccccccc-cccc-cccc-cccc-000000000021', 'activity', 'read', 'View activity records'),
    ('cccccccc-cccc-cccc-cccc-000000000022', 'activity', 'write', 'Create and edit activities'),
    ('cccccccc-cccc-cccc-cccc-000000000023', 'activity', 'delete', 'Delete activities'),
    ('cccccccc-cccc-cccc-cccc-000000000024', 'activity', 'assign', 'Assign activity ownership'),
    ('cccccccc-cccc-cccc-cccc-000000000025', 'activity', 'export', 'Export activity data'),
    
    -- Report module
    ('cccccccc-cccc-cccc-cccc-000000000026', 'report', 'read', 'View reports and dashboards'),
    ('cccccccc-cccc-cccc-cccc-000000000027', 'report', 'export', 'Export reports'),
    
    -- Workflow module
    ('cccccccc-cccc-cccc-cccc-000000000028', 'workflow', 'read', 'View workflow definitions'),
    ('cccccccc-cccc-cccc-cccc-000000000029', 'workflow', 'write', 'Create and edit workflows'),
    ('cccccccc-cccc-cccc-cccc-000000000030', 'workflow', 'delete', 'Delete workflows'),
    
    -- Admin module (tenant-level)
    ('cccccccc-cccc-cccc-cccc-000000000031', 'admin', 'user_manage', 'Manage users in tenant'),
    ('cccccccc-cccc-cccc-cccc-000000000032', 'admin', 'role_manage', 'Manage roles in tenant'),
    ('cccccccc-cccc-cccc-cccc-000000000033', 'admin', 'settings', 'Manage tenant settings'),
    
    -- User module (platform-level)
    ('cccccccc-cccc-cccc-cccc-000000000034', 'user', 'read', 'View users across platform'),
    ('cccccccc-cccc-cccc-cccc-000000000035', 'user', 'write', 'Create and edit users'),
    ('cccccccc-cccc-cccc-cccc-000000000036', 'user', 'delete', 'Delete users'),
    
    -- Tenant module (platform-level)
    ('cccccccc-cccc-cccc-cccc-000000000037', 'tenant', 'read', 'View tenants'),
    ('cccccccc-cccc-cccc-cccc-000000000038', 'tenant', 'write', 'Create and edit tenants'),
    ('cccccccc-cccc-cccc-cccc-000000000039', 'tenant', 'delete', 'Delete tenants'),

    ('cccccccc-cccc-cccc-cccc-000000000040', 'admin', 'role_read', 'View role details') 
ON CONFLICT DO NOTHING;

-- Grant role_read to RESELLER
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r, permissions p
WHERE r.name = 'RESELLER' 
  AND p.module = 'admin' 
  AND p.action = 'role_read'
ON CONFLICT DO NOTHING;


-- Create default SUPERADMIN user
-- Password: SuperAdmin@123
-- BCrypt hash generated with: BCryptPasswordEncoder().encode("SuperAdmin@123")
INSERT INTO users (
    id,
    tenant_id,
    email,
    password_hash,
    first_name,
    last_name,
    role_id,
    is_active,
    email_verified
)
VALUES
(
    '11111111-1111-1111-1111-111111111111',
    NULL,
    'superadmin@crm.com',
    -- Correct BCrypt hash for "SuperAdmin@123"
    '$2a$10$wz956geeDgpdlkKmzX8s6e1dn2SoupTyX.ZoO3DaiKsRmgd7doOYK',
    'Super',
    'Admin',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    true,
    true
)
ON CONFLICT DO NOTHING;

-- Assign SUPERADMIN role to user
INSERT INTO user_roles (user_id, role_id, tenant_id)
SELECT 
    u.id, 
    r.id, 
    NULL::UUID
FROM users u 
CROSS JOIN roles r
WHERE u.email = 'superadmin@crm.com' 
  AND r.name = 'SUPERADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM user_roles ur 
    WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

-- Assign ALL permissions to SUPERADMIN role with ALL scope
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'SUPERADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp 
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Assign tenant management permissions to RESELLER role
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'RESELLER' 
  AND p.module IN ('tenant', 'user', 'report')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp 
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );