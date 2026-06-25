# SKILL-03: RBAC & Authorization Model

## PURPOSE
Defines the full RBAC (Role-Based Access Control) system for this CRM. Every agent writing access checks, queries, or API guards MUST follow this 4-layer model. This is the most complex and critical security system in the project.

---

## 1. THE 4-LAYER ACCESS MODEL

All 4 layers must pass before data is returned:

```
Layer 1: Module Access   → Can this user access the "Deals" module at all?
Layer 2: Action Access   → Can this user READ / WRITE / DELETE / EXPORT?
Layer 3: Record Access   → Can this user see THIS specific deal?
Layer 4: Field Access    → Can this user see/edit THIS specific field?
```

**If any layer fails → reject with 403 ACCESS_DENIED**

---

## 2. DB SCHEMA (Core RBAC Tables)

```sql
-- Roles (per tenant, with hierarchy)
roles: id, tenant_id, name, parent_role_id

-- Permissions (module + action combos)
permissions: id, module, action
-- module: lead | contact | account | deal | activity | report | workflow | admin
-- action: read | write | delete | assign | export | import

-- Role ↔ Permission mapping with SCOPE
role_permissions: role_id, permission_id, access_scope
-- access_scope: ALL | TEAM | OWN | NONE

-- User ↔ Role mapping
user_roles: user_id, role_id

-- Field-level security
field_permissions: id, role_id, module, field_name, can_read, can_write

-- Team structure
teams: id, tenant_id, name, manager_id
team_members: team_id, user_id

-- Record shared with teams
record_teams: entity_type, entity_id, team_id
```

---

## 3. ACCESS SCOPE VALUES

| Scope | Meaning                                        |
|-------|------------------------------------------------|
| ALL   | Access all records in the tenant               |
| TEAM  | Access records owned by their team members     |
| OWN   | Only their own records (owner_id = current user) |
| NONE  | No access (explicitly blocked)                 |

### Scope by Default Role

| Role      | Module | Action | Scope |
|-----------|--------|--------|-------|
| Sales Rep | Deal   | READ   | OWN   |
| Manager   | Deal   | READ   | TEAM  |
| Admin     | Deal   | READ   | ALL   |

---

## 4. ROLE HIERARCHY

```
Admin
  ↓
Manager
  ↓
Sales Rep
  ↓
Employee
```

Rule: A manager can access **subordinates' records** via role hierarchy.

```sql
-- Precompute subordinate user IDs for performance (cache in Redis)
SELECT user_id FROM users WHERE role IN (child_roles_of_manager)
```

---

## 5. MASTER RECORD ACCESS QUERY

This is the canonical query for any entity (example: deals):

```sql
SELECT * FROM deals d
WHERE d.tenant_id = :tenantId
AND (
    -- OWN scope
    d.owner_id = :userId

    -- TEAM scope (team members' records)
    OR d.owner_id IN (
        SELECT tm.user_id FROM team_members tm
        WHERE tm.team_id IN (:userTeamIds)
    )

    -- SHARED records (explicitly shared with user's team)
    OR EXISTS (
        SELECT 1 FROM record_teams rt
        WHERE rt.entity_id = d.id
        AND rt.entity_type = 'deal'
        AND rt.team_id IN (:userTeamIds)
    )

    -- ROLE HIERARCHY (manager sees subordinates' records)
    OR d.owner_id IN (:subordinateUserIds)

    -- ALL scope (admin)
    OR :hasAllAccess = true
)
```

---

## 6. PERMISSION EVALUATION FLOW (every API request)

```
Request
  ↓
1. Authenticate JWT → extract user_id, tenant_id, role_id
  ↓
2. Load user roles + permissions (from Redis cache)
  ↓
3. Check MODULE permission → does role have DEAL_READ?
   If NO → 403
  ↓
4. Apply RECORD filter (OWN / TEAM / ALL query above)
  ↓
5. Apply FIELD filter → strip hidden fields from response
  ↓
6. Return filtered data
```

---

## 7. FIELD-LEVEL SECURITY

```java
// Example: Sales rep cannot see deal.value
// Backend removes field from response if can_read = false
{
  "deal_name": "ABC Corp Deal",
  "value": null,  // hidden — field_permissions says can_read = false for this role
  "stage": "Negotiation"
}
```

Field permissions table example:

| Role       | Field        | Read | Write |
|------------|--------------|------|-------|
| Sales Rep  | deal.value   | ❌   | ❌    |
| Manager    | deal.value   | ✅   | ✅    |
| Admin      | deal.value   | ✅   | ✅    |

---

## 8. CACHING STRATEGY (CRITICAL FOR PERFORMANCE)

Cache in Redis (invalidate on role/permission change):
- User roles list
- User's effective permissions (module + action + scope)
- User's team memberships
- Role hierarchy tree (precomputed subordinate IDs)

Cache key pattern: `rbac:{tenantId}:{userId}:permissions`

---

## 9. DEFAULT ROLES TO SEED ON TENANT CREATION

| Role Name  | Description                              |
|------------|------------------------------------------|
| Admin      | Full ALL access to everything            |
| Manager    | TEAM access, can view reports            |
| Sales Rep  | OWN access to leads/contacts/deals       |
| Employee   | Read-only OWN access                     |

---

## 10. NEVER DO THESE

- ❌ Only role-based without record-level access (critical gap)
- ❌ No team sharing support
- ❌ No role hierarchy (managers can't see their team's data)
- ❌ Hardcoding permissions in code
- ❌ Storing full permission list inside JWT
- ❌ Skipping field-level security for sensitive fields
- ❌ No Redis caching (RBAC queries are expensive)
