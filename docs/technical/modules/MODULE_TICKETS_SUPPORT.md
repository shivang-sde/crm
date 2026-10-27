# Module: Tickets (Support & SLA)

| | |
|---|---|
| **Doc ID** | CRM-TD-M07 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 · covers `modules\ticket` |
| **Evidence basis** | **Staged working tree, uncommitted as of 2026-10-26**: migrations V70–V73 and all ticket code are present in the index/worktree (`git status` categories `A`/`M`) but **not in commit `0fb9361`** (verified via `git ls-tree origin/main`). Deploy status unverified. |

## 1. Purpose & scope

Support case management: tickets with tenant-scoped routing, internal/customer-visible message threads, SLA deadlines + breach detection + single-hop escalation. **Excluded:** multi-hop escalation, ticket templates, satisfaction surveys beyond `TicketFeedback` UI component [uncommitted], external-portal customer self-service.

## 2. API surface

| Controller | Base | Endpoints |
|---|---|---|
| `TicketController` | `/api/v1/tickets` | POST; GET, GET /{id}; PATCH /{id}; POST /{id}/assign (`ticket:assign`); POST /{id}/status; DELETE /{id}; GET /{id}/activities |
| `TicketMessageController` | `/api/v1/tickets/{ticketId}/messages` | POST; GET, GET /{messageId}; PATCH /{messageId}; DELETE /{messageId} |

## 3. Classes & behavior

- `TicketService` — CRUD + status transitions; on create snapshots SLA deadlines from `TicketSlaProperties` (UTC clock bean `slaClock`);
- `TicketMessageService` — messages with `TicketMessageVisibility`: INTERNAL | CUSTOMER_VISIBLE (two states only by design).
- `TicketSlaBreachService` + `TicketSlaBreachScheduler` (60s default fixed-delay, property-gated `app.tickets.sla.breach-scan-*`): stamps idempotent `slaBreached` flag, publishes `TICKET.SLA_BREACHED`. **Late-config change never rewrites already-imposed deadline snapshots.**
- `TicketEscalationService` — breached ticket → owner's manager (one hop only via `users.manager_id`; no re-escalation; missing/foreign manager = silent no-op).
- `TicketTimelineService` — ticket-scoped activity reads (separate from generic `/api/v1/activities` so record scope holds).

## 4. Database

- `tickets` (V70) with read/write/delete permission seeds + tenant trigger; `ticket:assign` seed (V71) ADMIN/MANAGER only (EMPLOYEE explicitly excluded); `ticket_messages` (V72); SLA columns `response_due_at`, `resolution_due_at`, `first_response_at`, `sla_breached` (V73).

## 5. Auth, permissions, tenant isolation

- Module permissions `ticket` read/write/delete + assign; seeded per role via `DefaultRoleConfig` ( Employees read/write at OWN scope, no delete).
- Tenant scoping via `TicketRepository`/`TicketSpecifications`; `requireReadable` used by other modules (activity guard, feedback signals).
- Tenant-owned `Ticket` entities carry standard audit columns.

## 6. Events & schedulers

- TICKET: CREATED, UPDATED, STATUS_CHANGED, OWNER_CHANGED, DELETED, SLA_BREACHED; TICKET_MESSAGE: CREATED (outbox → workflow/health).
- One scheduled scan: `TicketSlaBreachScheduler` every `breach-scan-delay-ms` (60s default).

## 7. Tests

`modules\ticket\`: contract/migration tests `TicketContractTest`, `TicketMessageContractTest`, `TicketMigrationContractTest`, `TicketMessageMigrationContractTest`, `TicketAssignPermissionMigrationTest`, `TicketSlaContractTest`; service tests `TicketAssignmentTest`, `TicketAuthorizationTest`, `TicketEscalationServiceTest`, `TicketLifecycleAndEventTest`, `TicketMessageServiceTest`, `TicketRelationshipIntegrityTest`, `TicketSlaBreachServiceTest`, `TicketSlaIntegrationTest`, `TicketTimelineTest`.
**Gaps:** no E2E; first-response-at stamping logic (who writes `first_response_at`) not proven by a named test here — recorded as unverified behavior.

## 8. Dependencies & integration

- Workflow: no context provider/adapter yet (cannot drive workflows from ticket events beyond trigger support) — see workflow doc coverage matrix.
- Feedback: `ComplaintTicketService` creates tickets (see feedback doc); Customer Health consumes `SLA_BREACHED_TICKET`, `SUPPORT_*` rules via `ticket:read`.

## 9. Limitations & risks

- First-response stamping mechanism unclear from inspection — **unverified**; recommend confirming where `first_response_at` gets written (channel integration?).
- Escalation exactly one hop — documented in code; not a configurable multi-hopper.
