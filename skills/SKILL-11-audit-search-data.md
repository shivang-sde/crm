# SKILL-11: Audit, Search & Data Operations

## PURPOSE
Defines audit logging, global search, data import/export, soft delete, and duplicate management. Every agent building data operations, admin features, or compliance capabilities MUST follow this.

---

## 1. AUDIT LOGGING

### What to Log
Every create, update, delete on ANY CRM entity must produce an audit log entry.

```sql
audit_logs
  id, tenant_id, user_id, entity_type, entity_id,
  action (CREATE|UPDATE|DELETE|VIEW_EXPORT),
  old_values JSONB,    -- snapshot before change
  new_values JSONB,    -- snapshot after change
  changed_fields[],    -- which fields changed
  ip_address, user_agent, created_at
```

### Activity Timeline (per record)
Shown on every Lead/Deal/Contact detail page:
```sql
activity_logs
  id, tenant_id, entity_type, entity_id,
  event_type (field_changed|stage_changed|note_added|call_logged|assigned|comment),
  description TEXT,   -- human-readable: "Stage changed from Prospecting to Qualified"
  actor_id UUID,
  metadata JSONB,
  created_at
```

### Implementation Pattern
Use a Spring AOP aspect or Hibernate event listener to auto-capture changes. Never rely on developers manually adding audit calls — it will be missed.

---

## 2. SOFT DELETE

**All CRM entities use soft delete. Hard delete is never used.**

```sql
-- Every entity table has:
is_deleted    BOOLEAN DEFAULT false
deleted_at    TIMESTAMP
deleted_by    UUID
```

All queries automatically filter:
```sql
WHERE is_deleted = false AND tenant_id = :tenantId
```

### Trash / Recovery API
```
GET    /api/v1/{module}/trash         → list deleted records (last 30 days)
POST   /api/v1/{module}/{id}/restore  → restore a record
DELETE /api/v1/{module}/{id}/purge    → permanent delete (admin only, after 30 days)
```

---

## 3. GLOBAL SEARCH (Elasticsearch)

### Indexed Entities
- Leads (name, email, phone, company, notes)
- Contacts (name, email, phone, job_title)
- Accounts (name, industry, website)
- Deals (name, value, stage, notes)
- Activities (subject, notes)

### Search API
```
GET /api/v1/search?q=infosys&modules=leads,contacts&page=1&size=20
```

Response:
```json
{
  "success": true,
  "data": {
    "leads": [ { "id": "...", "name": "Infosys Lead", "highlight": "..." } ],
    "contacts": [],
    "accounts": [ { "id": "...", "name": "Infosys Ltd" } ]
  },
  "meta": { "total": 4, "query": "infosys" }
}
```

### Sync Strategy
Write to PostgreSQL first → publish event → Elasticsearch consumer syncs.
Never write to Elasticsearch synchronously in the API thread.

### Tenant Isolation in Search
All Elasticsearch queries MUST include `tenant_id` filter. Use index aliases per tenant at scale.

---

## 4. DATA IMPORT / EXPORT

### CSV Import
```
POST /api/v1/{module}/import
Content-Type: multipart/form-data
file: leads.csv
```

Import flow:
1. Upload CSV to S3
2. Return job ID immediately (async)
3. Background job: validate → deduplicate → insert
4. Notify user on completion (in-app + email)
5. Return import report: `{ success: 450, failed: 12, duplicates: 8 }`

CSV format:
- First row = headers (mapped to field names)
- Support custom field columns
- Support owner assignment by email

### Export
```
GET /api/v1/leads/export?format=csv&filters=...
```

Rules:
- RBAC-governed: user can only export records they can READ
- Async for large sets (returns download URL)
- Log export action in audit_logs (action = VIEW_EXPORT)
- Admin can restrict export per role via `permissions.action = export`

---

## 5. DUPLICATE DETECTION

### On Lead/Contact Create
Before saving, check for duplicates:
```sql
SELECT * FROM leads
WHERE tenant_id = :tenantId
AND is_deleted = false
AND (email = :email OR phone = :phone)
```

Response on duplicate found:
```json
{
  "success": false,
  "error": {
    "code": "DUPLICATE_DETECTED",
    "message": "A lead with this email already exists",
    "existing": { "id": "...", "name": "Amit Kumar" }
  }
}
```

Options: `force_create=true` param to override, or merge flow.

### Record Merge
```
POST /api/v1/leads/{primaryId}/merge
{ "mergeFromId": "duplicate-lead-id" }
```

Merge behavior:
1. Copy non-null fields from secondary to primary (if primary field is empty)
2. Move all activities, notes, files from secondary to primary
3. Soft-delete secondary with `merged_into = primaryId`
4. Audit log the merge

---

## 6. TAGGING SYSTEM

```sql
tags
  id, tenant_id, name, color, module

entity_tags
  entity_type, entity_id, tag_id, tenant_id
```

Usage: `GET /api/v1/leads?tag=vip,enterprise` → filter by tags

---

## 7. NEVER DO THESE

- ❌ Hard delete any CRM record (always soft delete)
- ❌ Skip audit logging on entity changes
- ❌ Allow export without RBAC check
- ❌ Run CSV import synchronously (blocks API, times out)
- ❌ Search without tenant_id filter in Elasticsearch
- ❌ Skip duplicate checking on lead/contact create
- ❌ Return deleted records in normal list queries (always filter `is_deleted = false`)
