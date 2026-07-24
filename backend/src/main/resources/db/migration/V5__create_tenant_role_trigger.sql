-- ============================================
-- Auto-create default tenant roles on tenant creation
-- ============================================
-- IMPORTANT: This trigger logic MUST remain in sync with:
-- Java class: com.shivang.crm.modules.rbac.config.DefaultRoleConfig
-- Java method: TenantService.createDefaultRolesManually()
--
-- Last synced: 2026-05-29
-- Config version: 1.0
-- ============================================

-- Error log table for tracking trigger errors
CREATE TABLE IF NOT EXISTS trigger_errors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trigger_name VARCHAR(255) NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    record_id UUID,
    error_message TEXT,
    error_detail TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Enhanced trigger function with error logging
-- 
-- Permission configuration (must match DefaultRoleConfig.java):
-- ADMIN: modules = {lead, contact, account, deal, activity, report, workflow, admin}
-- MANAGER: modules = {lead, contact, account, deal, activity, report} 
--          AND actions = {read, write, assign, export}
-- EMPLOYEE: modules = {lead, contact, account, deal, activity}
--           AND actions = {read, write}
--
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
        
        -- Assign ADMIN permissions (matches DefaultRoleConfig.ADMIN_MODULES)
        INSERT INTO role_permissions (role_id, permission_id, access_scope)
        SELECT admin_role_id, p.id, 'ALL'
        FROM permissions p
        WHERE p.module IN ('lead', 'contact', 'account', 'deal', 'activity', 'task', 'call', 'meeting',
                          'report', 'workflow', 'admin')
        ON CONFLICT (role_id, permission_id) DO NOTHING;
        
        -- Assign MANAGER permissions (matches DefaultRoleConfig.MANAGER_MODULES + MANAGER_ACTIONS)
        INSERT INTO role_permissions (role_id, permission_id, access_scope)
        SELECT manager_role_id, p.id, 'TEAM'
        FROM permissions p
        WHERE p.module IN ('lead', 'contact', 'account', 'deal', 'activity', 'task', 'call', 'meeting', 'report')
          AND p.action IN ('read', 'write', 'assign', 'export')
        ON CONFLICT (role_id, permission_id) DO NOTHING;
        
        -- Assign EMPLOYEE permissions (matches DefaultRoleConfig.EMPLOYEE_MODULES + EMPLOYEE_ACTIONS)
        INSERT INTO role_permissions (role_id, permission_id, access_scope)
        SELECT employee_role_id, p.id, 'OWN'
        FROM permissions p
        WHERE p.module IN ('lead', 'contact', 'account', 'deal', 'activity', 'task', 'call', 'meeting')
          AND p.action IN ('read', 'write')
        ON CONFLICT (role_id, permission_id) DO NOTHING;
        
        -- Log success for debugging
        RAISE NOTICE 'Created default roles (ADMIN, MANAGER, EMPLOYEE) for tenant %', NEW.id;
        
    EXCEPTION WHEN OTHERS THEN
        -- Log error to table
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

-- Drop existing trigger if it exists
DROP TRIGGER IF EXISTS tenant_creation_trigger ON tenants;

-- Create trigger on tenant insert
CREATE TRIGGER tenant_creation_trigger
    AFTER INSERT ON tenants
    FOR EACH ROW
    EXECUTE FUNCTION create_tenant_default_roles();

-- ============================================
-- VERIFICATION QUERY
-- Use this to verify configuration matches Java
-- ============================================
-- SELECT 
--     module,
--     action,
--     COUNT(*) as permission_count
-- FROM permissions
-- WHERE module IN ('lead', 'contact', 'account', 'deal', 'activity', 'report', 'workflow', 'admin')
-- GROUP BY module, action
-- ORDER BY module, action;