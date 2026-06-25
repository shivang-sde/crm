# SKILL-02: Multi-Tenancy Design & Enforcement

## PURPOSE
Defines how tenant isolation works in this CRM. Every agent writing queries, APIs, or services MUST follow these rules. Tenant data leakage is a critical security failure.

---

## 1. TENANCY STRATEGY

**Approach: Shared Database + tenant_id column on every table**

- Phase 1 (current): All tenants share one PostgreSQL DB, isolated by `tenant_id`
- Phase 2 (future): Schema-per-tenant migration path available
- Phase 3 (enterprise): DB-per-tenant for highest isolation needs

---

## 2. TENANT IDENTIFICATION

Tenant is resolved server-side via **two mechanisms**:

| Method     | When Used                    |
|------------|------------------------------|
| Subdomain  | `companyA.yourcrm.com`       |
| JWT claim  | `tenant_id` inside JWT token |

**⚠️ CRITICAL RULE: Frontend NEVER sends tenant_id. Backend ALWAYS resolves it.**

---

## 3. REQUEST FLOW (EVERY API CALL)

```
1. Request hits API Gateway
2. Extract tenant from subdomain OR JWT (server-side only)
3. Attach tenant_id to request context / thread-local
4. All service methods receive tenant_id from context
5. All DB queries filter: WHERE tenant_id = :resolvedTenantId
6. Response returned (never expose other tenants' data)
```

---

## 4. DATABASE RULES

Every table in the system MUST have:
```sql
tenant_id UUID NOT NULL REFERENCES tenants(id)
```

Every query MUST include tenant filter:
```sql
-- ✅ CORRECT
SELECT * FROM leads WHERE tenant_id = :tenantId AND id = :id;

-- ❌ WRONG — never query without tenant filter
SELECT * FROM leads WHERE id = :id;
```

Create a DB index on tenant_id for every table:
```sql
CREATE INDEX idx_leads_tenant ON leads(tenant_id);
```

---

## 5. TENANT DB TABLES

```
tenants
  - id, name, slug, plan, is_active, created_at

tenant_settings
  - tenant_id, key, value   (feature flags, configs)

tenant_domains
  - tenant_id, domain, is_primary
```

---

## 6. TENANT CONTEXT IN CODE

Use a request-scoped context object. Example pattern:
```java
// Set on every request via JWT filter
TenantContext.setTenantId(resolvedTenantId);

// Access anywhere in service layer
String tenantId = TenantContext.getTenantId();
```

Always clear context after request completes (thread safety).

---

## 7. TENANT ONBOARDING FLOW

```
1. Register tenant (name, domain, admin email)
2. Create tenant record
3. Create tenant_settings (defaults)
4. Create subdomain mapping
5. Create first admin user for that tenant
6. Seed default roles (Admin, Manager, Employee)
7. Return access credentials
```

---

## 8. DATA ISOLATION CHECKLIST (run before every PR)

- [ ] Every new table has `tenant_id` column
- [ ] Every query filters by `tenant_id`
- [ ] No API endpoint accepts `tenant_id` from request body/params
- [ ] JWT validation extracts and verifies `tenant_id`
- [ ] Integration webhooks validate tenant ownership before processing

---

## 9. NEVER DO THESE

- ❌ Trust `tenant_id` from request body or query params
- ❌ Write queries without `tenant_id` filter
- ❌ Create tables without `tenant_id`
- ❌ Return data without tenant filter (cross-tenant data leak)
- ❌ Hardcode tenant IDs in code
