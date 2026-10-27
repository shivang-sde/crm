# Module: Platform Operations, Analytics, Demo Data

| | |
|---|---|
| **Doc ID** | CRM-TD-M13 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 · covers `modules\operations`, `modules\analytics`, `modules\demo` (`modules\notification` empty—logic in reminder module) |
| **Evidence basis** | Repo inspection; **operations module classes/files are staged additions (git status `A`), not committed as of 2026-10-26**. Analytics/demo classes are in commit `0fb9361`, except `SalesDashboardService`-adjacent grouped items noted separately. |

## 1. Operations (operator recovery tooling)

- Endpoint surface (`OperatorMessageController`, base `/api/v1/operations`):
  - GET `/rabbitmq/dlq`, GET `/rabbitmq/dlq/{messageId}` (non-destructive browse)
  - POST `/rabbitmq/dlq/{messageId}/redrive` (explicit re-drive)
  - GET `/outbox/dead`, GET `/outbox/dead/{eventId}` (terminal outbox rows)
  - POST `/outbox/dead/{eventId}/requeue` (delegates to `OutboxEventClaimService.requeueDead`)
  - GET `/actions` (operator action audit)
- Classes: `RabbitManagementApiClient` (management-HTTP queue browse with `ack_requeue_true` only; **no destructive endpoints**; `fetchAndRemove` exists uncalled), `DeadLetterQueueInspector`, `DeadLetterRedriveService`, `DeadLetterMessageMapper`, `OutboxDeadEventService`, `OperatorScopeResolver` (platform vs tenant visibility), `OperatorActionAuditService` (writes `operator_message_actions` rows — identifiers and sanitized text only, no payload/credential columns).
- Migrations: V65 (`crm_event_outbox` DEAD state), V66 (`operator_message_actions` table + seeds `admin:operations_read/write` for SUPERADMIN + tenant ADMIN).
- RBAC note: controller is on `RbacFilter` excluded prefixes because the URL-derived module name differs from the DB module (`admin:operations-*`); **annotations are the authoritative check** (deliberate design; documented in config).
- Tests: `OutboxDeadEventServiceTest`, `OperatorMessageRecoverySafetyTest`, `DeadLetterRedriveServiceTest`, `DeadLetterQueueInspectorTest`, `DeadLetterMessageMapperTest`.
- UI: `(tenant)\admin\operations\rabbitmq` + `...\outbox` pages + `components\operations\{DeadLetterQueueTable,OutboxDeadEventTable,RecoveryConfirm}`; api/hooks `lib\{api,hooks}\operations.ts`.
- No scheduled auto-recovery exists; everything is explicitly operator-triggered and audited (design invariant).

## 2. Analytics

- `AnalyticsController` (base `/api/v1/analytics`; guard `report:read`, exports need `report:export`):
  - GET `/summary`, `/trends`
  - GET `/leads/source`, `/conversion/owner`, `/conversion/period`
  - GET `/pipeline/stage`, `/pipeline/owner`, `/pipeline/account`, `/pipeline/forecast-category`
  - GET `/deals/aging`, `/deals/current-stage-age`
  - GET `/accounts/owner`, `/contacts/account`
  - GET `/activity/rates`, `/calls/status`, `/calls/duration`
  - GET `/export/summary|trends|grouped` (CSV streaming via `AnalyticsExportService`/`CsvWriter`)
- Scope model: derived from the `report:read` **access scope** (PLATFORM→resellers→tenant users ALL/TEAM/OWN); drill-down cannot widen scope (`INVALID_ANALYTICS_SCOPE` exceptions). No backing tables — computes across operational tables with `AnalyticsScopeResolver`.
- Frontend: `(tenant)\dashboard` (`SalesDashboard` — uncommitted revisions), `(tenant)\reports` (OperationalReports; uncommitted), admin `(plateform)` dashboards; `lib\{api,hooks}\analytics.ts`, `components\dashboard\*`.
- **Tests: none** for analytics scoping (largest gap in permission-sensitive logic); recommend adding scope matrix tests (Technical Risks).

## 3. Demo data

- `DemoDataController` base `/api/v1/demo-data`: GET `/status`, POST `/install`, DELETE (reset) — permission `workflow` read/write (deliberate dev-tooling gate).
- `DemoDataService` orchestrates creation via real services (accounts, contacts, leads, deals+stages+line items, tasks, calls, meetings, offerings, entitlements) and tracks created entities for reset; tables `tenant_demo_installations`, `demo_data_records` (V29).
- **Tests: none.** Run production risk: uninstall path should stay tenant-scoped (`TenantDemoInstallationRepository`); treat as non-production tooling until then.

## 4. Cross-module notes

- `InfrastructureMetrics` (shared\resilience) aggregates ops metrics (outbox, rabbit, redis, throttle) that surface in grafana-style dashboards externally (micrometer-prometheus dependency); operations module only administers message-level recovery.
- All three modules are gated behind RBAC beyond regular user roles; no public ingress.
