# B3 — Project Planning and Monitoring

| | |
|---|---|
| **Doc ID** | CRM-WSB-B3 |
| **Version / Status** | 0.2 — PROPOSED — NOT APPROVED (activation draft; supersedes 0.1 skeleton from Phase 1.6) |
| **Prepared** | 2026-10-26 (Phase 1.7) |
| **Proposed owner role** | OWNER TO BE ASSIGNED (proposal: owner/PM with agent maintaining plan) |
| **Revision history** | 0.1 skeleton 2026-10-26 · 0.2 activation draft 2026-10-26 (Gate B) |
| **Approval** | PROPOSED — NOT APPROVED — adoption at first Workstream B review meeting |

## 1. Purpose and scope

Turn B2 `REQ-` items into planned work with owners, sequence, status, and blockers, and keep the running plan visible and honest. Scope: the CRM project's development activity only; excludes risk management (B4) and measurement of process performance (B9).

## 2. Historical practice vs proposed process

- Historical (evidence-class): milestone-driven sequencing via workflow phases (WF17…WF21 chain, docs\ audits), planning sections in `docs\l3.4-p4-customer-health-readiness-audit.md`, "door-closing" gate protocol in `readme.md`. **No plan file with estimates/owners/dates exists; none are fabricated here.**
- Proposed: iteration plan file replaces remembered context; gates continue as code-checks plus recorded planning rows.

## 3. Trigger and required inputs

- Triggers: iteration start; scope change (B1); new blocked item; release-candidate need (B8).
- Inputs: B2 register rows (`PROPOSED`/confirmed), B4 open risks touching delivery, B1 snapshot for module boundaries, team availability (owner + agent capacity statement from owner).

## 4. Procedure (proposed)

1. **Backlog drafting** (agent, from B2): each confirmed REQ becomes ≥1 work item `ITEM-<nn>`; items carry estimate only if owner provides one — else `EST: NOT PROVIDED` (no invented values).
2. **Iteration plan creation** (owner decides): select items, order, target dates only where owner fixes them; document explicit dependencies (e.g., `ITEM-7 blocked by ITEM-4`).
3. **Execute and update** (responsible: agent implements; owner decides): status per item ∈ {Backlog, In-progress, Blocked, Review, Done}; every status change dated; Done requires gate evidence (B6 pass or logged gap).
4. **Blocked/dependency decision point** (owner, same-day): owner unblocks, re-scopes, or escalates to B4 risk; recorded in item row.
5. **Variance handling** (owner): when iteration target date passes without Done: variance row logged (`target vs actual`, reason, impact) — reason must reference real evidence (gate failure, missing infra, decision latency). Not treated as accusation, treated as signal for B9.
6. **Iteration close** (owner + agent): every item reaches terminal state; plan checked against B1 snapshot for scope drift; learnings → B9 improvement backlog.

## 5. Outputs, records, storage

| Record | Identification | Storage (proposed) | Status |
|---|---|---|---|
| Project backlog | `ITEM-<nn>` rows | `docs/process/records/b3-backlog.md` | NOT AVAILABLE (starts for new work) |
| Iteration plan | `PLAN-<YYYYQn><seq>` file | `docs/process/records/` | NOT AVAILABLE |
| Variance log rows | appended in plan file | same | NOT AVAILABLE |
| Historical milestones | gate-chain reports | existing reports | EXISTS (evidence-class, dated) |

## 6. Review and approval requirements (proposed)

Plan reviewed by owner before execution of each iteration. Deviations > band agreed in plan re-review via B8 gates. Reporting artifact = the plan itself (no separate status reports exist; format kept minimal deliberately).

## 7. Exception and escalation

Unplanned emergency work (production incident (not possible yet per D-09), urgent security decision): items inserted mid-iteration are marked `UNPLANNED-INSERT`, reason recorded; any element that could destabilize release scope (B8) escalates to owner immediately and B1 re-baseline is triggered if scope changes.

## 8. Related documents and current evidence status

CRM-TD-09; B1, B2, B4, B8, B9. Evidence: historical plan/estimates NOT AVAILABLE; gate-chain record EXISTS (evidence-class).

## 9. Approval block

| Role | Name | Date | Decision |
|---|---|---|---|
| Owner (adopts procedure) | _TBD_ | _TBD_ | _NOT SIGNED_ |
