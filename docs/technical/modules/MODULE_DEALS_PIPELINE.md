# Module: Deals (Pipeline)

| | |
|---|---|
| **Doc ID** | CRM-TD-M04 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 · covers `modules\deal` |
| **Evidence basis** | Repo inspection at commit `0fb9361`; `SalesDashboardService`/`SalesDashboardServiceTest` are uncommitted-stage additions confirmed in git status as M. |

## 1. Purpose & scope

Sales pipeline: deals with tenant-defined lifecycle stages, line items (currency-aware), custom fields, assignment, staged won/lost handling with downstream provisioning, sales dashboard, pipeline activity/notes. **Excluded:** quote/invoice generation (roadmap only, `backend\docs\devlopment\modules\quote-invoice\roadmap.md`), multi-currency conversion (currency snapshotted, not converted).

## 2. API surface

| Controller | Base | Endpoints |
|---|---|---|
| `DealController` | `/api/v1/deals` | POST; GET, GET /dashboard, GET /{id}; PUT /{id}; DELETE /{id}; PATCH /{id}/stage; PUT /{id}/assign; GET /{id}/activities; GET/POST /{id}/notes; DELETE /{id}/notes/{noteId} — `/{id}/won` and `/{id}/lost` endpoints are **commented out** (won path via stage + atomic line-item creation) |
| `DealStageController` | `/api/v1/deal-stages` | POST; GET, GET /{id}; PUT/DELETE /{id} |
| `DealLineItemController` | `/api/v1/deals/{dealId}/line-items` | GET, GET /{lineItemId}, POST, PUT /{lineItemId}, DELETE /{lineItemId} |
| `DealCustomFieldController` | `/api/v1/deal-custom-fields` | GET, POST; PUT/DELETE /{id} |

Permissions: `deal` read/write/delete per endpoint via `@PreAuthorize("@rbac.has(authentication,'deal',...)")`.

## 3. Classes & domain model

- `DealService` — deal + line items created **atomically**; won-Deal creation then **provisions entitlements** via `EntitlementProvisioningService.provisionFromWonDeal` (idempotent per line; reconciliation path iterates `findByTenantIdAndRecordCategoryAndDeletedFalse(CLOSED_WON)`).
- `DealStageService` — `DealStage` rows: name, color, displayOrder, isDefault, isClosed, RecordCategory (OPEN/CLOSED_WON/CLOSED_LOST), defaultProbability, defaultForecastCategory. Gate: `DealStageAuthorizationTest`, direct-creation-in-category-CLOSED_WON disallowed (`DealWonProvisioningLifecycleTest`).
- `DealLineItemService` — quantity, unitPrice, discountAmount, taxAmount, lineTotal, serviceStart/EndDate, renewable, renewalNoticeDays, customData, **`currencyCode` ISO-4217 snapshot from `Offering.currencyCode` at creation** (nullable, never invented — V69).
- `DealCustomFieldService` — fieldKey/fieldType, optionsJson; per-deal `customData` JSON map.
- `SalesDashboardService` — `GET /api/v1/deals/dashboard`: summary, stage breakdown, owner breakdown, lead funnel, closing metrics; "stale deal" heuristic = OPEN + no update 30 days (hard-coded constant); visibility reuses `DealSpecifications.visibleToUser` with `deal:read` scope.
- Workflow hooks: `DealWorkflowEntityContextProvider`, `DealWorkflowEntityUpdateAdapter`, `DealWorkflowOwnerAssignmentAdapter`.
- Parent-guard: `assertDealAccessible` used by activity guard [uncommitted see accounts doc].

## 4. Database

- `V10__...` deals/stages/custom fields; `V26__...` deal_line_items; `V42__...` stage_entered_at; `V69__add_deal_line_item_currency_code.sql`.
- Deal fields: stageEnteredAt, closedDate, wonReason/lostReason, amount (BigDecimal, no currency), owner/creator columns from TenantOwnedEntity, soft delete).

## 5. Events

DEAL (CREATED, UPDATED, STAGE_TRANSITIONED, OWNER_CHANGED), DEAL_LINE_ITEM (CREATED, UPDATED, DELETED) — canonical outbox events; `ClosedWonDealEventChainTest` verifies DEAL.WON chain against the outbox without MQ.

## 6. Tests

`backend\src\test\java\...\modules\deal\`: `DealContractNamingTest`; `service\{ClosedWonDealEventChainTest, CustomerDomainSoftDeleteTest, DealAtomicCreationTest, DealCustomerRelationshipIntegrityTest, DealLineItemEventTest, DealLineItemLifecycleTest, DealStageAuthorizationTest, DealUpdatedEventTest, DealWonProvisioningLifecycleTest, SalesDashboardServiceTest[uncommitted]}`.
**Gaps:** no E2E on dashboard endpoint; currency-code conflict when offering changes after line creation (snapshot semantics relied upon by test only).

## 7. Dependencies

- `catalog\OfferingService` (currency + product tie), `entitlement` provisioning chain, `lead` optional conversion relationship, `workflow` adapters, shared `CustomerRelationshipValidator`.

## 8. Limitations & risks

- Dashboard stale-deal threshold is hard-coded (30 days), not configurable — technical risk for cross-tenant expectations.
- Deal-level currency absent — reporting across mixed `currencyCode` line items needs aggregation in `(tenant)\reports`.
- A won/lost delete path exists but no cascade-less child cleanup documented — see SoftDelete test for chosen semantics.
