# Module Catalog

| | |
|---|---|
| **Doc ID** | CRM-TD-03 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 |
| **Evidence basis** | Verified directory/class inspection; detailed modules have separate docs under `docs\technical\modules\`. Counts below are **working-tree counts, verified 2026-10-26** (65 controllers, 76 repositories, 74 migrations, 115 test files). **Committed baseline `origin/main` = `0fb9361` holds only 60 controllers / 64 migrations / 38 backend test files** — the delta (tickets, feedback/health, operations, records RBAC seeds, Customer 360, and tests/migrations V65–V74) is staged but uncommitted and flagged per module doc. |

Status legend: **DETAILED** = has its own technical doc. All counts are working-tree counts (see evidence basis above for the committed-baseline split).

## Domain modules (29 under `backend\src\main\java\com\shivang\crm\modules\`)

| Module | Purpose | Status | Head classes / detail doc |
|---|---|---|---|
| auth | Login/JWT/refresh/throttle/blacklist/provisioning; security filter chain | DETAILED | `MODULE_AUTH_IDENTITY.md` (with rbac/tenant/user) |
| rbac | Roles, permissions, scopes, caches, default role config | DETAILED | `MODULE_AUTH_IDENTITY.md` |
| tenant | Tenant read/update API (provisioning lives in auth) | DETAILED | `MODULE_AUTH_IDENTITY.md` |
| user | User management (create/deactivate/roles) | DETAILED | `MODULE_AUTH_IDENTITY.md` |
| account | Accounts, custom fields, notes via EntityNoteService, **Customer 360 [uncommitted]** | DETAILED | `MODULE_ACCOUNTS_CONTACTS_ACTIVITIES.md` |
| contact | Contacts, custom fields, relationship validation | DETAILED | `MODULE_ACCOUNTS_CONTACTS_ACTIVITIES.md` |
| activity | Polymorphic activity timeline, guarded reads (`ActivityAccessGuard` [uncommitted]) | DETAILED | same |
| lead | Leads, statuses/sources, convert, entity notes/history engine | DETAILED | `MODULE_LEADS_ACQUISITION.md` |
| acquisition | Lead ingestion: configs, events, mappings, polling, csv import, public ingress | DETAILED | same |
| form | Form builder consumed by acquisition public submit | DETAILED | same |
| deal | Deals, stages, line items (currency V69), custom fields, dashboard | DETAILED | `MODULE_DEALS_PIPELINE.md` |
| catalog | Offerings (currency, billing type/interval) | DETAILED | `MODULE_OFFERINGS_ENTITLEMENTS.md` |
| entitlement | Entitlement lifecycle, renewal scheduling, won-deal provisioning | DETAILED | same |
| commercial | External commercial-system sync (API keys, customers/documents) | DETAILED | `MODULE_COMMERCIAL_INTEGRATION.md` |
| ticket | Tickets + messages + SLA + escalation (V70–V73) | DETAILED | `MODULE_TICKETS_SUPPORT.md` |
| feedback | Feedback (NPS/CSAT/CES/COMPLAINT) + analytics + complaint→ticket + **Customer Health risk signals** | DETAILED | `MODULE_FEEDBACK_CUSTOMER_HEALTH.md` |
| task | Tasks CRUD, complete/reopen | DETAILED | `MODULE_ACTIVITIES_COMMUNICATION.md` (grouped) |
| meeting | Meetings CRUD | DETAILED | same |
| call | Call records, dispositions, click-to-call, opening events API | DETAILED | same |
| recurrence | Recurring schedule generation (headless creative) | DETAILED | same |
| reminder | Reminder planning/delivery + user notifications (`NotificationController`) — **`modules\notification\` is empty; logic lives here** | DETAILED | same |
| workflow | Graph workflow engine + executors + reliability | DETAILED | `MODULE_WORKFLOW_ENGINE.md` |
| integration | Providers/connector instances/credentials, webhook ingress + normalized mapping, generic outbound HTTP | DETAILED | `MODULE_INTEGRATIONS_TELEPHONY.md` (with dialer) |
| dialer | Call provider links, opening events, connect triggers, layout | DETAILED | same |
| records | Generic records platform: types/fields/display/mapping profiles/record webhooks (WF-42..WF-53 lineage) | DETAILED | `MODULE_RECORDS_PLATFORM.md` |
| operations | Operator recovery: RabbitMQ DLQ inspect/redrive, outbox dead requeue, audit (V65–V66) | DETAILED | `MODULE_PLATFORM_OPERATIONS.md` (with analytics/demo) |
| analytics | Scope-aware analytics + CSV export (reports read scope) | DETAILED | same |
| demo | Per-tenant demo-data install/reset (V29) | DETAILED | same |

## Shared infrastructure (`com\shivang\crm\shared\`)

| Area | Classes | Detail |
|---|---|---|
| base | `BaseEntity`, `TenantOwnedEntity` | tenant_id/owner_id non-null contract |
| event | `CanonicalCrmEvent{Publisher,OutboxService}`, `OutboxEvent{Publisher,ClaimService}`, `CrmEventRabbitConfig`, `ReturnedMessageTracker` | `INTEGRATIONS_AND_ASYNC_PROCESSING.md` |
| service | `CustomerRelationshipValidator`, `EntityResolverService`, `EntityPhoneResolver`, `PermissionService` | cross-module tenant-safe helpers |
| resilience | `RedisAvailabilityGuard`, `InfrastructureMetrics` | fail-open/fail-closed modes |
| config | Redis/Rabbit configs, `RabbitPublisherReturnConfig`, `GlobalExceptionHandler` | error contract JSON |

## Controller/repository/test coverage quick facts

- 65 `*Controller.java`, 76 `*Repository.java` (verified counts).
- Modules **without** the module's own concrete tests (as of inspection) include: lead (covered via lead-related workflow tests), acquisition, form, tenant, user, analytics, demo, commercial, dialer (1 service test), account/contact/task/meeting (service/controller tests added but uncommitted, e.g. `Customer360*Test`, `NestedAccountContact*`, `ActivityAccessGuardTest`, `ContactAccountRelationshipIntegrityTest`).
- Modules with the densest module test suites: workflow, integration, records, ticket, feedback, entitlement, deal, operations, auth (see `TESTING_AND_VERIFICATION_BASELINE.md`).
