# MASTER SKILL: CRM Project Context & Agent Rules
> **Version**: 1.0 | **Stack**: Java + Spring Boot + React + PostgreSQL  
> **Read this ENTIRE file before writing a single line of code or answering any question about this project.**

---

## ⚡ WHO THIS IS FOR

Any AI agent, developer, or assistant working on this CRM project. This file is the single source of truth. It defines what we're building, all architectural decisions, all rules, and what NEVER to do. If you follow this file, your output will always be consistent with the rest of the system.

---

## 🧭 SECTION INDEX

| # | Skill Area | Topics Covered |
|---|------------|----------------|
| 1 | Project Overview | What we're building, scope, phases |
| 2 | Core Data Model | Entities, relationships, ownership |
| 3 | Multi-Tenancy | Tenant isolation, tenant_id rules |
| 4 | RBAC & Authorization | 4-layer access model, permissions |
| 5 | Auth & JWT | Token architecture, security rules |
| 6 | Workflow Engine | Event-driven automation |
| 7 | API Design | URL patterns, response format |
| 8 | Tech Stack | All approved technologies |
| 9 | Custom Fields | Customization engine |
| 10 | Integrations | Webhooks, CPaaS, external APIs |
| 11 | Notifications | In-app, email, real-time alerts |
| 12 | Audit & Data Ops | Soft delete, search, import/export |
| 13 | Global Rules | Things NEVER to do |

---

## 1. PROJECT OVERVIEW

### What We're Building
A **multi-tenant SaaS CRM** — similar in ambition to Salesforce/HubSpot but built from scratch. It serves multiple companies (tenants) from a single application, with complete data isolation between them.

### Two-Layer Architecture

**Platform Core** (must build first):
- Multi-tenancy engine
- RBAC & permissions
- Workflow automation engine
- Notification system
- Integration framework
- Audit & activity tracking

**Business Modules** (plug on top of platform):
- Sales CRM (Leads → Contacts → Accounts → Deals)
- Marketing
- Support / Ticketing
- Tasks / Projects
- Communication (CPaaS)

### Build Phases
```
Phase 1 (MVP): Monolith → PostgreSQL + Redis + RabbitMQ
Phase 2: Extract Workflow Service + Notification Service
Phase 3: Full microservices + Kafka + Elasticsearch
```

---

## 2. CORE DATA MODEL

### The Golden Rule
```
Account (Company) → Contact (Person) → Deal (Money Opportunity)
Lead = raw unqualified prospect that CONVERTS INTO the above
Owner = the user responsible for that record
```

### Entity Definitions

**LEAD** — Unqualified prospect. Not yet real. Converts → Account + Contact + Deal.
- Fields: `name, email, phone, company, source, status, owner_id, tenant_id`
- Lifecycle: `New → Contacted → Qualified → Converted | Disqualified`

**ACCOUNT** — A company you do business with.
- 1 Account → Many Contacts, Many Deals
- Fields: `name, industry, website, address, revenue, owner_id, tenant_id`

**CONTACT** — A person at an Account.
- Many Contacts → 1 Account; Contact links to Deals
- Fields: `first_name, last_name, email, phone, job_title, account_id, owner_id, tenant_id`

**DEAL** — A potential sale. This is the revenue unit.
- Pipeline stages: `Prospecting → Qualification → Proposal → Negotiation → Closed Won / Closed Lost`
- Fields: `name, value, stage, probability, close_date, account_id, owner_id, tenant_id`

**ACTIVITY** — Calls, meetings, tasks, notes — linked to any entity.
- Fields: `type, subject, due_date, status, entity_type, entity_id, owner_id, tenant_id`

### Ownership Model

| Entity | Owner Meaning |
|--------|---------------|
| Lead | Who is following up |
| Account | Who manages company relationship |
| Contact | Who handles person communication |
| Deal | Who is closing the sale |

`owner_id` = responsible person (permanent). `assigned_to` = current handler (optional/temporary).

### Mandatory Fields on EVERY Table
```sql
tenant_id    -- isolation (NEVER nullable)
owner_id     -- accountability
created_by   -- audit
created_at   -- timestamp
updated_at   -- timestamp
is_deleted   -- soft delete flag (NEVER hard delete)
```

### ER Overview
```
Tenant
 ├── Users ── Roles ── Permissions
 │     └── Teams
 ├── Leads
 ├── Accounts ── Contacts
 │        └── Deals ── Pipeline Stages
 ├── Activities (linked to all entities)
 ├── Custom Fields → Values
 ├── Workflows → Triggers → Actions → Executions
 ├── Notifications
 ├── Webhooks / Integrations
 └── Audit Logs / Files
```

### Lead Conversion Flow
```
Lead (raw) → qualify → Account + Contact + Deal created
After conversion: lead.converted = true (inactive)
```

---

## 3. MULTI-TENANCY

### Strategy: Shared DB + tenant_id

Every table has `tenant_id UUID NOT NULL`. Every query filters by it. No exceptions.

### Tenant Resolution (server-side ONLY)

```
Subdomain: companyA.yourcrm.com → tenantId resolved from domain
JWT: tenant_id claim in token → resolved server-side
```

**⚠️ RULE #1: Frontend NEVER sends tenant_id. Backend ALWAYS resolves it.**

### Every Query Must Include Tenant Filter
```sql
-- ✅ CORRECT
SELECT * FROM deals WHERE tenant_id = :tenantId AND id = :id;

-- ❌ WRONG — critical security failure
SELECT * FROM deals WHERE id = :id;
```

### Request Flow
```
1. Request arrives
2. Extract tenant from subdomain OR JWT (never from body/params)
3. Attach to TenantContext (thread-local)
4. All queries auto-filter by tenant_id
5. TenantContext cleared after response
```

### Tenant Tables
```
tenants, tenant_settings, tenant_domains
```

### Tenant Onboarding
```
Register → Create tenant → Create settings → Create subdomain → Create admin user → Seed default roles
```

---

## 4. RBAC & AUTHORIZATION

### 4-Layer Access Model (ALL must pass)
```
Layer 1: Module Access  → Can user access the "Deals" module?
Layer 2: Action Access  → Can user READ | WRITE | DELETE | EXPORT?
Layer 3: Record Access  → Can user see THIS specific deal?
Layer 4: Field Access   → Can user see/edit THIS field?
```

### Core Tables
```sql
roles(id, tenant_id, name, parent_role_id)
permissions(id, module, action)
role_permissions(role_id, permission_id, access_scope)  -- access_scope: ALL|TEAM|OWN|NONE
user_roles(user_id, role_id)
field_permissions(id, role_id, module, field_name, can_read, can_write)
teams(id, tenant_id, name, manager_id)
team_members(team_id, user_id)
record_teams(entity_type, entity_id, team_id)
```

### Access Scope Values

| Scope | Meaning |
|-------|---------|
| ALL   | All tenant records |
| TEAM  | Own team's records |
| OWN   | Only records where owner_id = current user |
| NONE  | No access |

### Default Role Scopes
| Role | Module | Action | Scope |
|------|--------|--------|-------|
| Sales Rep | Deal | READ | OWN |
| Manager | Deal | READ | TEAM |
| Admin | Deal | READ | ALL |

### Role Hierarchy
```
Admin → Manager → Sales Rep → Employee
```
Manager can access all subordinates' records.

### Master Record Access Query
```sql
SELECT * FROM deals d
WHERE d.tenant_id = :tenantId
AND (
    d.owner_id = :userId                                    -- OWN
    OR d.owner_id IN (                                      -- TEAM
        SELECT tm.user_id FROM team_members tm
        WHERE tm.team_id IN (:userTeamIds))
    OR EXISTS (                                             -- SHARED
        SELECT 1 FROM record_teams rt
        WHERE rt.entity_id = d.id AND rt.team_id IN (:userTeamIds))
    OR d.owner_id IN (:subordinateUserIds)                  -- HIERARCHY
    OR :hasAllAccess = true                                 -- ALL
)
```

### Permission Evaluation Flow
```
Request → JWT Auth → Load permissions (Redis) → Module check → Record filter → Field filter → Return
```

### Redis Caching (Required for Performance)
Cache: user roles, permissions, team memberships, role hierarchy
Key: `rbac:{tenantId}:{userId}:permissions`
Invalidate: on role or permission change

---

## 5. AUTH & JWT

### Stack: JWT + Spring Security (Phase 1), Keycloak (Phase 2)

### Two-Token Architecture

| Token | Lifetime | Storage | Purpose |
|-------|----------|---------|---------|
| Access Token | 10–30 min | Memory only (JS variable) | Every API call |
| Refresh Token | 7–30 days | HTTP-only cookie | Get new access token |

### JWT Payload (keep it minimal)
```json
{ "sub": "user_id", "tenant_id": "uuid", "role_id": "uuid", "iat": ..., "exp": ... }
```
**DO NOT put permissions list in JWT.**

### Security Rules
- ✅ Access token in memory only
- ✅ Refresh token in HTTP-only cookie
- ✅ Rotate refresh token on every use
- ✅ Invalidate on logout (Redis blacklist)
- ❌ Never localStorage/sessionStorage for tokens
- ❌ Never trust tenant_id from request body

### Filter Chain
```
JwtAuthFilter → TenantResolutionFilter → RbacFilter → Controller
```

---

## 6. WORKFLOW ENGINE

### Core Flow
```
CRM Action → Event Published (async) → Trigger Matched → Conditions Evaluated → Actions Executed
```

### Event Types
```
LEAD_CREATED, LEAD_UPDATED, LEAD_DELETED
DEAL_CREATED, DEAL_UPDATED, STAGE_CHANGED
CONTACT_CREATED, CONTACT_UPDATED
ACTIVITY_COMPLETED
```

### Standard Event Structure
```json
{
  "eventType": "DEAL_UPDATED",
  "tenantId": "uuid",
  "entity": "deal",
  "entityId": "uuid",
  "eventId": "unique-uuid",
  "payload": { "old": {...}, "new": {...} },
  "timestamp": "ISO-8601"
}
```

### Action Types
`EMAIL | SMS | WHATSAPP | ASSIGN | WEBHOOK | CREATE_TASK | NOTIFY`

### DB Tables
```
workflows, workflow_triggers, workflow_conditions, workflow_actions,
workflow_executions, scheduled_actions
```

### Execution Flow
```
1. Event arrives in queue
2. Find matching active workflows (tenant + entity + event_type)
3. Check idempotency: UNIQUE(event_id, workflow_id) in executions table
4. Evaluate conditions against event.payload.new
5. Execute actions in execution_order
6. Log result (SUCCESS|FAILED|RETRY)
7. On failure: exponential backoff retry (1min → 5min → 15min → DLQ)
```

### Condition Engine
Supports operators: `=, !=, >, <, >=, <=, IN, NOT_IN, CONTAINS, IS_NULL`
Supports grouping: `AND / OR` via `logical_group` field

### Critical Rules
- ✅ ALWAYS async (never sync in API thread)
- ✅ Idempotency via eventId + workflowId unique constraint
- ✅ Retry with exponential backoff
- ✅ Execution depth limit (max 5) to prevent infinite loops
- ✅ Rate limit actions per tenant per hour
- ❌ Never hardcode workflow logic in code

---

## 7. API DESIGN STANDARDS

### URL Pattern
```
GET    /api/v1/{module}           → list (paginated)
POST   /api/v1/{module}           → create
GET    /api/v1/{module}/{id}      → get one
PUT    /api/v1/{module}/{id}      → full update
PATCH  /api/v1/{module}/{id}      → partial update
DELETE /api/v1/{module}/{id}      → soft delete
```

### Module Paths
```
/api/v1/auth, /api/v1/leads, /api/v1/contacts, /api/v1/accounts,
/api/v1/deals, /api/v1/activities, /api/v1/workflows, /api/v1/notifications,
/api/v1/integrations, /api/v1/reports, /api/v1/users, /api/v1/tenant
```

### Standard Response Format

✅ Success:
```json
{ "success": true, "data": {...}, "meta": { "page": 1, "size": 20, "total": 120 } }
```

❌ Error:
```json
{ "success": false, "error": { "code": "ACCESS_DENIED", "message": "..." } }
```

### Pagination Params
`page` (default: 1), `size` (default: 20, max: 100), `sort`, `order` (asc|desc)

### Rules
- Every request carries JWT — stateless
- Frontend NEVER sends tenant_id
- All IDs: UUID format
- Dates: ISO 8601
- No verbs in URLs (`/getLeads` ❌)
- Always proper HTTP status codes
- Never expose stack traces or DB errors

---

## 8. TECH STACK (APPROVED — DO NOT DEVIATE)

| Layer | Technology |
|-------|------------|
| Backend | Java + Spring Boot + Spring Security + Hibernate |
| Frontend | React + Next.js + Tailwind + shadcn/ui + TanStack Query + Zustand |
| Database | PostgreSQL (primary) |
| Cache | Redis |
| Messaging Phase 1 | RabbitMQ |
| Messaging Phase 2 | Apache Kafka |
| Search | Elasticsearch |
| File Storage | Amazon S3 |
| Auth Phase 1 | JWT + Spring Security |
| Auth Phase 2 | Keycloak |
| CPaaS | Twilio (calls, SMS, WhatsApp) |
| Email | AWS SES or SendGrid |
| DevOps | Docker → Kubernetes |

---

## 9. CUSTOM FIELDS

Every module (lead, contact, account, deal, activity) supports tenant-defined custom fields.

### DB Tables
```
custom_fields(id, tenant_id, module, field_name, label, field_type, is_required, options, display_order)
custom_field_values(id, entity_id, entity_type, field_id, value, tenant_id)
```

OR use JSONB on entity table: `custom_data JSONB DEFAULT '{}'`

### Field Types
`text | number | date | boolean | select | multi_select | url | email`

### In API Responses
```json
{
  "id": "lead-uuid",
  "name": "Amit Kumar",
  "customFields": { "budget_range": "10L-50L", "source_campaign": "Q1 Google Ads" }
}
```

### Other Customizable Items
- `pipelines` + `pipeline_stages` (deal pipeline)
- `module_statuses` (custom lead/activity statuses)
- `layouts` (field layout per role)

---

## 10. INTEGRATIONS

### Types
- Outgoing webhooks (CRM → external)
- Incoming webhooks (external → CRM leads/contacts)
- CPaaS: Twilio (calls, SMS, WhatsApp)
- Email: SES/SendGrid
- REST API with API token auth

### DB Tables
```
webhooks, webhook_tokens, webhook_logs, integration_configs (encrypted), api_tokens
```

### Webhook Security
- HMAC-SHA256 signature on all outgoing: `X-CRM-Signature: sha256=<hash>`
- Validate incoming webhook tokens
- Block private IP ranges in webhook URLs (SSRF protection)
- Encrypt credentials at rest

### Rules
- ✅ All webhook calls async (via queue)
- ✅ Retry failed deliveries
- ✅ Log all delivery attempts
- ❌ Never call external URLs in API request thread

---

## 11. NOTIFICATIONS

### Channels
`In-App (WebSocket/SSE) | Email (SES/SendGrid) | SMS (Twilio) | WhatsApp (Twilio)`

### DB Tables
```
notifications, notification_templates, user_notification_settings
```

### In-App Real-Time
WebSocket push → if user offline, notification sits in DB, shown on next login.

### Notification API
```
GET  /api/v1/notifications
POST /api/v1/notifications/{id}/read
POST /api/v1/notifications/read-all
GET  /api/v1/notifications/count
```

### Rules
- ✅ Respect user's notification preferences
- ✅ Respect quiet hours
- ✅ All delivery is async
- ❌ Never hardcode notification messages (use templates)

---

## 12. AUDIT, SEARCH & DATA OPERATIONS

### Audit Logging
Every entity change → `audit_logs(entity_type, entity_id, action, old_values, new_values, user_id)`
Implement via Spring AOP — never rely on manual calls.

### Activity Timeline (per record)
`activity_logs` — shown on every record detail page.

### Soft Delete
All entities use soft delete. NEVER hard delete.
```sql
is_deleted = false (filter on every query)
deleted_at, deleted_by (set on delete)
```
Trash API: list/restore/purge.

### Global Search (Elasticsearch)
```
GET /api/v1/search?q=infosys&modules=leads,contacts
```
Always include `tenant_id` filter. Sync via event consumer (async).

### Import/Export
- CSV import: async (upload → S3 → background job → notify on completion)
- Export: RBAC-governed, async for large sets, always audit-logged

### Duplicate Detection
Check email + phone before creating lead/contact. Offer merge for duplicates.

### Tagging
`tags(id, tenant_id, name, color, module)` + `entity_tags(entity_type, entity_id, tag_id)`

---

## 13. GLOBAL RULES — NEVER VIOLATE THESE

### Architecture
- ❌ NEVER query without `tenant_id` filter
- ❌ NEVER accept `tenant_id` from frontend
- ❌ NEVER hard-delete CRM records (always soft delete)
- ❌ NEVER run workflows synchronously in API thread
- ❌ NEVER hardcode workflow logic in code
- ❌ NEVER skip the event/messaging system
- ❌ NEVER store business logic in controllers

### Security
- ❌ NEVER store access token in localStorage
- ❌ NEVER put full permissions in JWT
- ❌ NEVER skip RBAC at DB + service layer
- ❌ NEVER allow webhooks to internal/private IPs (SSRF)
- ❌ NEVER log credentials or auth tokens
- ❌ NEVER trust frontend for sensitive context (tenant, permissions)

### Performance
- ❌ NEVER skip DB indexes on tenant_id, owner_id, status, created_at
- ❌ NEVER skip Redis cache for RBAC (expensive queries)
- ❌ NEVER return unbounded list queries (always paginate)

### Data Integrity
- ❌ NEVER mix Lead and Contact concepts
- ❌ NEVER skip `owner_id` on entity tables
- ❌ NEVER skip duplicate detection on lead/contact create
- ❌ NEVER expose raw DB errors or stack traces in API responses

### Must Always Do
- ✅ Audit every entity change (create/update/delete)
- ✅ tenant_id on every table
- ✅ owner_id on every entity
- ✅ All external calls async via event queue
- ✅ Retry with exponential backoff for all async operations
- ✅ Idempotency keys for workflow executions
- ✅ Encrypt integration credentials at rest

---

## 📁 INDIVIDUAL SKILL FILES (for deep dives)

| File | Topic |
|------|-------|
| SKILL-01-data-model.md | Entity definitions, ER diagram, ownership |
| SKILL-02-multi-tenancy.md | tenant_id rules, request flow, isolation |
| SKILL-03-rbac.md | 4-layer access, permission tables, queries |
| SKILL-04-auth-jwt.md | Token architecture, Spring Security, flows |
| SKILL-05-workflow-engine.md | Event system, conditions, actions, retry |
| SKILL-06-api-design.md | URL patterns, response format, pagination |
| SKILL-07-tech-stack.md | Full approved stack, scaling roadmap |
| SKILL-08-custom-fields.md | Custom fields, pipelines, layouts |
| SKILL-09-integrations.md | Webhooks, CPaaS, API tokens |
| SKILL-10-notifications.md | In-app, email, user preferences |
| SKILL-11-audit-search-data.md | Audit logs, soft delete, search, import |
