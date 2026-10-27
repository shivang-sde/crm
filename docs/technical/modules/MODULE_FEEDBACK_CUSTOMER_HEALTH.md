# Module: Feedback & Customer Health (Risk Signals)

| | |
|---|---|
| **Doc ID** | CRM-TD-M08 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 · covers `modules\feedback` |
| **Evidence basis** | **Staged/working tree, uncommitted as of 2026-10-26** (feedback classes `A`/`AM`; `RiskSignalQueryRepository`, `RiskSignalPage` untracked; not in commit `0fb9361`). Governance reference: `docs\l3.4-p4-customer-health-readiness-audit.md` (2026-10-22, automated auditor) and the ~450-line contract section of `readme.md`. |

## 1. Purpose & scope

Customer feedback capture (NPS/CSAT/CES/COMPLAINT/GENERAL, sources incl. WEBSITE/TYPEFORM/GOOGLE_FORMS/WHATsAPP/EMAIL/API/INTERNAL), feedback analytics, one-click complaint→ticket creation, and a deterministic **Customer Health risk-signal layer** (facts, not a score). **Deliberately absent (code-documented):** composite health score, churn probability, usage-based engagement signals, weighting/escalation.

## 2. API surface

| Controller | Base | Endpoints | Permission |
|---|---|---|---|
| `FeedbackController` | `/api/v1/feedback` | POST, GET, GET /{id}, GET /analytics, POST /{feedbackId}/ticket | `feedback:write`, createTicket needs `ticket:write` AND `feedback:write` |
| `RiskSignalController` | `/api/v1/accounts/{accountId}/health/risk`, `/api/v1/customer-health/risk-signals` | GET account-scoped signals (type/severity filters, paging on cross-account route) | `account:read` gate + per-domain read scopes for contributing domains |

## 3. Classes

- `FeedbackService`, `FeedbackAnalyticsService`, `FeedbackIdentityResolver` (identity attribution), `ComplaintTicketService` (one-to-zero-or-one ticket, pessimistic lock, delegates to `TicketService` so the ticket emits standard TICKET.CREATED + SLA), `RiskSignalService` (+ `RiskSignalQueryRepository`, `RiskSignalPage`), `FeedbackSpecifications`/`FeedbackRepository`/`FeedbackAnalyticsRepository`.
- Frontend: `components\feedback\*`, `components\customer-health\*` (untracked), routes `(tenant)\tickets\_components\TicketFeedback.tsx`, `(tenant)\customer-health\` (untracked dir), `lib\api\{feedback,customer-health}.ts` + hooks.

## 4. Risk-signal rules (12, as coded in `RiskSignalService`)

| Rule | Severity | Gate | Summary |
|---|---|---|---|
| NEGATIVE_NPS | HIGH | feedback:read | NPS 0–6 (7–10 excluded) |
| LOW_CSAT | MEDIUM | feedback:read | CSAT 1–2 |
| HIGH_CES | MEDIUM | feedback:read | CES 4–7 (inverted scale) |
| RECENT_COMPLAINT | HIGH | feedback:read | complaint inside window |
| ENGAGEMENT_FEEDBACK_SILENT | MEDIUM | feedback:read | no customer-origin feedback since window start |
| SLA_BREACHED_TICKET | HIGH | ticket:read | persisted slaBreached flag |
| SUPPORT_NO_RESPONSE_PAST_DUE | HIGH | ticket:read | open + no first response after due |
| SUPPORT_SLOW_FIRST_RESPONSE | MEDIUM | ticket:read | first response after due |
| SUPPORT_PAST_RESOLUTION_DUE | MEDIUM | ticket:read | unresolved past resolution due |
| ENTITLEMENT_APPROACHING_EXPIRY | MEDIUM | entitlement:read | ≤30-day horizon or renewal-decision window |
| ENTITLEMENT_EXPIRED | HIGH | entitlement:read | expired, no replacement suppression logic |
| RECENT_DEAL_LOSS | MEDIUM | deal:read | CLOSED_LOST inside window |

Full rule semantics (windowing `[from,to)`, exclusions, evidence fields, cross-account paging in SQL, explicit null accountId, coverage notices for unreadable domains) are specified in `readme.md` Customer-Health section — treat that as the API contract. Partial-data NOT plated as complete: response includes source-domain coverage notice.

## 5. Database

- `feedback` (V74) + `feedback` read/write permission seeds + tenant trigger. No risk-signal table (signals computed at request time; cross-account filtering/paging in SQL via `RiskSignalQueryRepository`).
- Migration contract test: `FeedbackMigrationContractTest`.

## 6. Events & schedulers

- FEEDBACK.RECEIVED published on create (outbox); complaint→ticket emits standard ticket events. No schedulers here (triage is user-driven platform queries).

## 7. Auth / permissions / tenant isolation

- Account route: `account:read` + parent-account visibility check before scoring; unknown/foreign account → NOT_FOUND; visible but out-of-scope → forbidden.
- Cross-account route: applies account `ALL/TEAM/OWN` scope + per-source-domain read scopes in SQL (feedback `feedback:read`, ticket `ticket:read`, entitlement `entitlement:read`, deal `deal:read`); signals only produced for rows the caller could already list in that domain.
- Tenant isolation + soft-delete exclusion applied to every source query (per contract + `RiskSignalPersistenceIntegrationTest`).

## 8. Tests (dense coverage here)

`FeedbackContractTest`, `FeedbackMigrationContractTest`, `FeedbackPersistenceIntegrationTest` (untracked), `RiskSignalControllerTest`, `RiskSignalPersistenceIntegrationTest` (untracked), `repository\FeedbackAnalyticsRepositoryTest`, `service\{FeedbackServiceTest, FeedbackAnalyticsServiceTest, FeedbackIdentityResolverTest, ComplaintTicketServiceTest}`.
Known documented residual (from l3.4 audit): SLA/support health + approaching-expiry rule branches still controller-level-tested only (persistence layer predicates verified for the main shared window logic).

## 9. Dependencies & risks

- Reads ticket/entitlement/deal/feedback (and account visibility) — read-only cross-module facade; workflow adapters do **not** include feedback.
- Risk: rules encode **current policy as code** (`thresh olds in `RiskSignalService`) — changing thresholds is a code change, not a tenant setting (explicit contract note); flag as a product-policy decision need (see Technical Risks doc).
