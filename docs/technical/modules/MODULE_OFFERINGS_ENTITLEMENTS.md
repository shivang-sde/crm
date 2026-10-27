# Module: Offerings (Catalog) & Entitlements

| | |
|---|---|
| **Doc ID** | CRM-TD-M05 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 · covers `modules\catalog`, `modules\entitlement` |
| **Evidence basis** | Repo inspection at commit `0fb9361`. |

## 1. Purpose & scope

- **Catalog:** tenant-scoped offering/product master (`Offering`) with `currencyCode`, billing type/interval.
- **Entitlements:** customer entitlement lifecycle (PENDING→ACTIVE→…→EXPIRED), renewal follow-up tasks, won-deal provisioning, expiry activities. **Excluded:** automatic payments/subscription billing; contract PDFs (none).

## 2. API surface

| Controller | Base | Endpoints |
|---|---|---|
| `OfferingController` | `/api/v1/offerings` | POST; GET /{id}, GET; PUT /{id}; PATCH /{id}/activate, /{id}/deactivate; DELETE /{id} (`offering` permissions) |
| `CustomerEntitlementController` | `/api/v1/entitlements` | GET, GET /upcoming-renewals, GET /{id}; PUT /{id}; PATCH /{id}/activate, /{id}/suspend, /{id}/terminate |

Entitlement **assign** is a separate permission (`entitlement:assign`, seeded V68; AN tests `EntitlementAssignmentAuthorizationTest`, seed coverage `rbac\config\EntitlementAssignSeedTest`).

## 3. Classes & services

- `OfferingService` (+ ` OfferingEventPublicationTest` — OFFERING events: CREATED/UPDATED/ACTIVATED/DEACTIVATED; deletion lifecycle respected in `OfferingDeletionLifecycleTest`).
- `CustomerEntitlementService` — entitlement CRUD + provisioning entry `provisionFromWonDeal(deals)`; `EntitlementProvisioningService` (deal→entitlement derivation, one per line item, idempotent).
- `EntitlementExpiryService`, `EntitlementExpiryActivityService` — expiry transitions and activity rows.
- `EntitlementRenewalTaskService` + `EntitlementRenewalTaskPersistenceService` — renewal tasks for upcoming renewals (not yet proven scheduled to workflow; uses own persistence).
- Schedulers with property gates (`app.entitlements.*`, `EntitlementExpiryProperties`):
  - `EntitlementLifecycleScheduler.expireDueEntitlements()` — daily default fixed-delay.
  - `EntitlementRenewalTaskScheduler.processDueRenewalFollowUps()` — daily default.
- Fields on `CustomerEntitlement`: status enum PENDING/ACTIVE/SUSPENDED/CANCELLED/TERMINATED/EXPIRED, renewedFrom/ToEntitlementId, renewalDealId, renewable, renewalDueDate, autoRenew, endDate.

## 4. Database

- `offerings` (V25, plus permission seeds), `customer_entitlements` (V28), access-scope fix V67 (`fix_employee_entitlement_access_scope`), assign-permission seed V68.

## 5. Events & jobs

- ENTITLEMENT events: CREATED, UPDATED, STATUS_CHANGED, EXPIRED (outbox → workflow).
- Jobs: 2 daily schedulers listed above (both property-gated, designed multi-instance-safe via persistence deduplication — see `EntitlementSchedulerTenantIsolationTest`).

## 6. Auth, permissions, tenant isolation

- `offering` and `entitlement` module permissions; entitlement assign at MANAGER/ADMIN level via seeds; tenanted repos `CustomerEntitlementRepository` `findByIdAndTenantId...` + Specifications.
- No public ingress.

## 7. Tests

`modules\catalog\service\{OfferingDeletionLifecycleTest, OfferingEventPublicationTest}`; `modules\entitlement\service\{CustomerEntitlementRelationshipIntegrityTest, EntitlementAssignmentAuthorizationTest, EntitlementCreationEventTest, EntitlementEventPublicationTest, EntitlementLifecycleReconciliationTest, EntitlementSchedulerTenantIsolationTest}`.
**Gaps:** no tests for `PATCH /{id}/terminate` state-machine edge combos; no E2E for renewal task operator flow; customer-health expiry linkage tested only in feedback module persistence tests.

## 8. Dependencies & integration points

- **Deal won → provisioning** — `DealService` → `EntitlementProvisioningService` (see `MODULE_DEALS_PIPELINE.md`).
- **Customer Health** — feeding rule ENTITLEMENT_APPROACHING_EXPIRY/ENTITLEMENT_EXPIRED (see feedback doc).
- Workflow: no context provider/adapter for entitlement (see workflow doc "coverage matrix").

## 9. Limitations & risks

- `autoRenew` is stored but not acted upon by any scheduler (per `readme.md` customer-health section: "stored but not consulted") — business gap to confirm.
- Renewal task service creates tasks but does **not** link back to workflow automation actions.
