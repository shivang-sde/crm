Final Role Structure Confirmation
Based on SKILL-03 and your input:

Role	Level	Scope	Description
Superadmin	Platform	ALL (platform-wide)	Manages resellers, tenants, platform
Reseller	Platform	ALL (their tenants only)	White-label partners managing sub-tenants
Admin	Tenant	ALL (tenant-wide)	Full access to all tenant records
Manager	Tenant	TEAM	Access team members' records
Employee	Tenant	OWN	Own records only (read + write)
Note: You merged Sales Rep + Employee into just Employee role with OWN scope for read/write.

Default Permissions per Role (from SKILL-03)
Role	Module	Action	Scope
Superadmin	ALL	ALL	ALL
Reseller	tenant, user, report	ALL	ALL (their tenants only)
Admin	ALL (lead, contact, account, deal, activity, report, workflow, admin)	ALL	ALL
Manager	lead, contact, account, deal, activity, report	read, write, assign, export	TEAM
Employee	lead, contact, account, deal, activity	read, write	OWN
Employee cannot: delete, assign, export, access reports/workflows/admin modules.



Whenever assigning a role:

update users.role_id
update user_roles



# update seed sql migration later before moving to production 


-- Add role_read permission
INSERT INTO permissions (id, module, action, description) 
VALUES (
    'cccccccc-cccc-cccc-cccc-000000000040', 
    'admin', 
    'role_read', 
    'View role details'
) ON CONFLICT DO NOTHING;

-- Grant role_read to RESELLER
INSERT INTO role_permissions (role_id, permission_id, access_scope)
SELECT r.id, p.id, 'ALL'
FROM roles r, permissions p
WHERE r.name = 'RESELLER' 
  AND p.module = 'admin' 
  AND p.action = 'role_read'
ON CONFLICT DO NOTHING;


But for authorization and data filtering, stop checking role names and start checking:

permission + scope

That scales to unlimited custom roles and matches how mature CRMs like Salesforce, HubSpot, and Zoho CRM handle record visibility. The role grants permissions, and the permission carries a visibility scope (OWN, TEAM, ALL).