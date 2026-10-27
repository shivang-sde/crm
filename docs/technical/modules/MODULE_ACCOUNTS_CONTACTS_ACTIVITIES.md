# Module: Accounts, Contacts, Activities (+ Customer 360)

| | |
|---|---|
| **Doc ID** | CRM-TD-M02 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 · covers `modules\account`, `modules\contact`, `modules\activity` |
| **Evidence basis** | Repo inspection at commit `0fb9361`; **sections below describe a large uncommitted working-tree change (Customer 360 + nested-record guards + activity guard)** — marked [uncommitted]. |

## 1. Purpose & scope

Account (company) and Contact (person) master records with custom fields, notes, and per-entity activity timelines; Customer 360 aggregation view. **Excluded:** billing/invoicing (not implemented), organization charts (none), duplicate detection beyond search (see limitations).

## 2. API surface

| Controller | Base | Endpoints |
|---|---|---|
| `AccountController` | `/api/v1/accounts` | POST; GET /search, /{id}/360 [uncommitted], /{id}, /{id}/contacts, /{id}/activities, /{id}/notes; PUT /{id}; DELETE /{id} (soft delete [uncommitted]); POST /{id}/notes; DELETE /{id}/notes/{noteId} |
| `AccountCustomFieldController` | `/api/v1/account-custom-fields` | GET, POST; PUT/DELETE /{id} |
| `ContactController` | `/api/v1/contacts` | POST; GET /search, /{id}, /{id}/activities, /{id}/notes; PUT/DELETE /{id}; POST /{id}/notes; DELETE /{id}/notes/{noteId} |
| `ContactCustomFieldController` | `/api/v1/contact-custom-fields` | GET, POST; PUT/DELETE /{id} |
| `ActivityController` | `/api/v1/activities` | GET (requires entityType+entityId), GET /{entityType}/{entityId}, GET /{entityType}/{entityId}/recent [all reads uncommitted-guarded] |

Request/response DTOs under respective `dto\` packages; search via `*Specifications` (deleted-aware [uncommitted]). Customer 360 returns sectioned payload (`Customer360Response`/`Customer360Section`), each section paginated (default 20, max 100).

## 3. Classes & services

- `AccountService`, `AccountCustomFieldService`, `Customer360Service` (live aggregation delegating to ContactService, DealService, CustomerEntitlementService, TicketService, FeedbackService), `Customer360TimelineService` + `Customer360TimelineQueryRepository` [all uncommitted].
- `ContactService` — creates validate parent via `shared\service\CustomerRelationshipValidator.requireLiveAccount` (tenant-blind FK guard) [uncommitted].
- `ActivityService` + **`ActivityAccessGuard`** [uncommitted] — authorize polymorphic timeline reads by delegating to parent modules' scope guards: `AccountService.assertAccountAccessible`, `ContactService.assertContactAccessible`, `DealService.assertDealAccessible`, `LeadService.assertLeadAccessible`, `TicketService.requireReadable`; unsupported type → explicit exception.
- Ownership/scope guards: `assertAccountAccessible` / `assertContactAccessible` implement owner/creator RECORD-scope checks (mirrors deal guard) [uncommitted].
- Workflow hooks (in `modules\workflow\service\`): `Account/ContactWorkflowEntityContextProvider`, `*EntityUpdateAdapter`, `*OwnerAssignmentAdapter`.

## 4. Database

- `tables: accounts, contacts, account_custom_fields, contact_custom_fields, activities` → `V7__create_account_contact_tables.sql`. `activities` is polymorphic: `entity_type` + `entity_id`, **no FK to parents** (cross-module design constraint).
- Migrations: V7 only; no schema change for Customer 360 (aggregated live, not projected) — noted explicitly in `Customer360Service`.
- `entities_notes`/`entity_history` engine tables live in lead module (V6) and are consumed here — see `MODULE_LEADS_ACQUISITION.md`.

## 5. Authentication, permissions, tenant isolation

- All endpoints `@PreAuthorize("@rbac.has(authentication,'account'|'contact'|'activity', read|write|delete')")`.
- Tenant fix: entities extend `TenantOwnedEntity`; repositories use `findByIdAndTenantId...` / `...AndDeletedFalse`.
- Record-level: parent-access scope enforced before serving nested contacts/activities/notes [uncommitted]: `NestedAccountContactControllerWiringTest` asserts the guard runs before child data access.

## 6. Events

`CanonicalCrmEventPublisher`: ACCOUNT (CREATED, UPDATED, OWNER_CHANGED), CONTACT (CREATED, UPDATED, OWNER_CHANGED) — via outbox → RabbitMQ (see async doc). Activity module publishes none (row producer only).

## 7. Tests

Uncommitted additions: `modules\account\controller\Customer360EndpointContractTest`, `NestedAccountContactControllerWiringTest`; `repository\Customer360TimelineQueryRepositoryTest`; `service\{Customer360ServiceTest,Customer360TimelineServiceTest,NestedAccountContactAuthorizationTest}`; `modules\activity\service\ActivityAccessGuardTest`, `controller\ActivityControllerAuthorizationTest`; `modules\contact\service\ContactAccountRelationshipIntegrityTest`.
**Gaps:** no tests in committing history for customize fields pagination; no integration test proving cross-tenant nesting blocked via HTTP; activity table lacks tenant_id so guard correctness is test-inferred only.

## 8. Dependencies

- Delegates for 360: contact/deal/entitlement/ticket/feedback services (read-path integration; scope-aware).
- Notes engine from lead module; `EntityResolverService` for tenant-safe entity analysis used by tasks/calls/meetings writers of activities rows.

## 9. Limitations & risks

- [uncommitted] Customer 360 is **live aggregation** by design — every 360 view triggers multi-service reads (costs proportional to sections; no caching). Risk for high-traffic dashboards (perf review pending).
- `activities` has no tenant_id column by schema; all isolation rides on parent authorization. A future entity type added without a guard would leak timeline rows — mitigated by explicit unsupported-type exception, but new callers must register a guard.
- Account soft delete does not cascade children (deliberate; child check moves to validators) — orphan activity rows remain.
