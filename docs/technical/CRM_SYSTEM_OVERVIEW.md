# CRM System Overview

| | |
|---|---|
| **Doc ID** | CRM-TD-01 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 by primary developer |
| **Evidence basis** | Repo commit `0fb9361` (main, 2026-09-24) **plus large uncommitted working tree** (see Verification Baseline doc). Claims marked [uncommitted] come from working-tree code not yet committed. |

## 1. Purpose and scope

Multi-tenant SaaS CRM ("SellSpark") covering the full customer lifecycle:

1. **Prospect lifecycle** — lead capture: leads, lead sources/statuses, activities (tasks/calls/meetings), inbound acquisition (webhook ingestion, public forms, CSV import, polling), outbound commercial-system integration. (No campaign entity exists in the code; campaign attribution appears only as a `campaignSource` field on deals — `deal\dto\DealCreateRequest.java`.)
2. **Sales lifecycle** — accounts, contacts, deals with tenant-defined stages, line items, custom fields, offerings catalog, sales dashboard, analytics/reports.
3. **Fulfillment/customer lifecycle** — deal-won provisioning of customer entitlements, renewal lifecycle, ticketing with SLA, feedback collection (NPS/CSAT/CES/complaints), customer-health risk signals.
4. **Cross-cutting** — workflow automation engine (graph executions over canonical domain events), click-to-call/telephony (softphone UI, provider webhooks, call open events), generic records platform (custom record types/fields/webhooks), RBAC with record-level scopes, platform operations tooling (RabbitMQ DLQ / outbox dead-letter inspection and recovery), demo-data installer.

Source: `readme.md` (product narrative), `backend\src\main\java\com\shivang\crm\modules\` (29 modules), `frontend\src\app\` (93 route pages).

## 2. Technology stack (verified)

| Layer | Technology | Reference |
|---|---|---|
| Backend | Java 21, Spring Boot 4.0.6, Spring Modulith 2.0.6 | `backend\pom.xml` |
| Persistence | PostgreSQL, Flyway (74 migrations), JPA/Hibernate | `backend\src\main\resources\db\migration\` |
| Async/queueing | RabbitMQ + transactional outbox (`crm_event_outbox`), manual-ack workflow consumer | `backend\...\shared\event\`, `modules\workflow\config\WorkflowRabbitConfig.java` |
| Cache/monitoring | Redis (permissions, login throttle, token blacklist, lease/claim), `RedisAvailabilityGuard` fail-open modes | `modules\auth\security\*, shared\resilience\` |
| Frontend | Next.js 16.2.6, React 19, TypeScript strict, TanStack Query/Table, react-hook-form + Zod, Tailwind 4, shadcn/radix, `@xyflow/react` (workflow builder), `sip.js` (softphone) | `frontend\package.json`, `frontend\tsconfig.json:7` |
| E2E | Playwright 1.62 (23 spec files, smoke/acceptance/regression sets) + 3 `.cjs` contract checks | `frontend\tests\`, `frontend\playwright.config.ts` |
| Deploy | Docker (non-root backend image), docker-compose prod stack, nginx reverse proxy — **configuration verified; never executed in any recorded deployment documented in this repo** | `backend\Dockerfile`, `docker-compose.prod.yml`, `nginx\default.conf` |

## 3. Implemented capability inventory (verified entry points)

- **Auth/identity** — JWT access tokens (HS256), refresh-token rotation, login throttling, token blacklist, tenant provisioning (platform-level), profile self-service. (`modules\auth`, `MODULE_AUTH_IDENTITY`)
- **RBAC** — role/permission catalog in DB, 8 seeded permission modules extended over migrations V64–V74, roles ADMIN/MANAGER(+RESELLER)/EMPLOYEE(OWN scope), URL filter + method security, Redis-cached permission contexts. (`modules\rbac`)
- **Sales core** — accounts (soft delete, nested contacts/activities/notes, Customer 360 view [uncommitted]), contacts, leads with convert, tasks/calls/meetings, unified activity timeline (guarded reads).
- **Pipeline** — deals, tenant-configurable stages, atomic deal+line-item creation, won-deal entitlement provisioning chain, sales dashboard.
- **Commerce** — offerings catalog (currency, billing type/interval), entitlements (PENDING→ACTIVE renewal lifecycle, renewal tasks), external commercial-system sync API (API-key auth, customer/document sync, `backend\docs\COMMERCIAL_INTEGRATION_API.md` contract).
- **Service** — tickets + internal/customer-visible messages, SLA snapshots with 60s breach scan + manager escalation, feedback capture, complaint→ticket creation, customer-health risk signals (12 rule types, scope-gated).
- **Marketing/ingestion** — lead ingestion configurations (webhook/direct/form/polling transports), field mapping with previews, CSV import, public form builder (`modules\form`).
- **Automation** — workflow engine: versioned graph definitions (trigger/condition/branch/action/wait/end nodes), action executors: create task, update entity field, assign owner, click-to-call, HTTP API call, set context, no-op; outbox → RabbitMQ canonical events; idempotency, lease/claim, retry, recovery, replay; runtime-debug UI.
- **Telephony** — click-to-call action route, call opening events (softphone polling), provider webhooks (public ingress, HMAC-verified, normalized call mapping), generic-SIP connection settings, browser softphone with popup runtime + audio diagnostics [uncommitted].
- **Records platform** — custom record types/fields/display configs, record CRUD with validation and mapping profiles, inbound record webhooks (public HMAC endpoint, idempotent, delivery log) feeding workflow RECORD.RECEIVED triggers (WF-42..WF-53 lineage).
- **Platform ops** — operator tooling: RabbitMQ DLQ browse/redrive, outbox dead-event requeue, operator action audit log (V65–V66).
- **Admin/platform** — tenant listing/management (`UserManagementController`), platform superadmin/reseller provisioning, notifications, analytics (scope-resolved `/api/v1/analytics/*` + CSV exports), demo-data install/reset per tenant.

## 4. Explicit exclusions / not implemented

- `modules\notification\` is an **empty directory**; notification functionality actually lives in the reminder module (`NotificationController`).
- Quoting/invoice module: only a roadmap exists (`backend\docs\devlopment\modules\quote-invoice\roadmap.md`); no code.
- Deal `/won` and `/lost` dedicated endpoints are commented out in `DealController` (won path driven via stage change + line items).
- No composite customer-health score (deliberate product decision documented in `readme.md`).
- No CI pipeline (see Verification Baseline + Technical Risks).

## 5. Additional technical documents

See `docs\technical\DOCUMENT_INDEX.md` for the full register (architecture, module catalog, API/data/security/async/testing/limitations docs).

## 6. Draft-status disclaimer

This is a technical baseline, not a requirements or process document. No CMMI compliance, readiness, or Six Sigma claims are made. Nothing here asserts production behavior: code paths are verified by inspection + dated test artifacts; production verification is a known open item.
