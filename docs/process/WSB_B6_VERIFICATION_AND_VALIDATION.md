# B6 — Verification and Validation

| | |
|---|---|
| **Doc ID** | CRM-WSB-B6 |
| **Version / Status** | 0.2 — PROPOSED — NOT APPROVED (activation draft; supersedes 0.1 skeleton from Phase 1.6) |
| **Prepared** | 2026-10-26 (Phase 1.7) |
| **Proposed owner role** | OWNER TO BE ASSIGNED (proposal: primary developer executes; auditor role per iteration report as historically practiced) |
| **Revision history** | 0.1 skeleton 2026-10-26 · 0.2 activation draft 2026-10-26 (Gate B) |
| **Approval** | PROPOSED — NOT APPROVED — adoption at first Workstream B review meeting |

## 1. Purpose and scope

State what is verified when, with which methods, what evidence is kept, and how failures/regressions are handled. Scope: backend JUnit, Playwright E2E, contract check scripts, manual QA procedures, migration-contract tests, acceptance/iteration "door-closing" gates. Distinguishes historical test reports from tests executed in future recorded runs. Excludes post-deployment verification (B8) and monitoring.

## 2. Historical practice vs proposed process

- Historical (evidence exists, dated): test infra real-PostgreSQL `localhost:5433`, Flyway-on test runs (surefire reports 2026-10-23), Playwright runs (`frontend\playwright.config.ts`, `.last-run.json` 2026-10-23), contract check `.cjs` scripts, "door-closing" gate protocol (readme), gate-chain reports WF19→21, manual QA traces (`manual_test_guide.md`, `backend\wf58_*`).
- Proposed: same practice made explicit with records-on-current-build discipline added (historical artifacts remain valid **as historical artifacts only**).

## 3. Trigger and required inputs

- Triggers: B3 task Done-candidate; CHG implementation complete (B7); release candidate (B8); destructive/manual suite need; periodic regression (set by B9 measurement review).
- Inputs: touched-module set from CHG (B7); CRM-TD-08 §4 module test-suite inventory; test environment availability (PostgreSQL 5433, Rabbit, Playwright deps).

## 4. Procedure (proposed)

1. **Plan** (responsible: agent drafts, developer approves): for the CHG/iteration, select test segments using CRM-TD-08 §4: unit → integration (per applicable modules) → migration-contract where schema touched → E2E Playwright if user flows touched → manual destructive suites if token/external-tooling involved. Record the plan inside the work item (B3 row reference).
2. **Environment check decision point** — smoke-verify prerequisites (Postgres 5433 reachable, app builds); fail → environment unavailability documented in the run record `ENV: UNAVAILABLE`, stop (do not bypass silently).
3. **Execute** (developer + agent; recordings where tool-based): each segment produces an **execution record** with: date, build descriptor (git HEAD at run time), environment note (5433 PostgreSQL version/config reference), suite list, per-suite result.
4. **Failure handling decision point**: failure → `ISSUE-<nn>` in B4 with trace attachment; fix proceeds per B5/B7 design-fix loop; re-run; a failed suite is never marked pass on the same run record.
5. **Regression scope**: any B4 issue or B1 scope change re-triggers minimum suite set for the touched module; repeat step 1–4 subset with `REGRESSION:` tag in run record.
6. **Gate validation ("door-closing")** (owner decision, as historically): every gate statement annotated with the execution-records IDs satisfying it; missing evidence = remain open, escalate (B3 variance / B4 entry).
7. **Evidence retention** (agent): execution records stored per §5; DANGER artifact hygiene: execution-records of runs producing tokens/result JSONs still reference the storage path (never inline); token-bearing files must not be committed (aligned with security review CRM-TD-11).

## 5. Outputs, records, storage

| Record | Identification | Storage (proposed) | Status |
|---|---|---|---|
| Test execution record | `TEC-<nn>` header block inside a per-run file | `docs/process/records/test-execution/` | NOT AVAILABLE for future runs |
| Historical run artifacts | surefire-reports, Playwright reports, gate reports | existing locations | EXISTS (2026-09-01→2026-10-23, dated; historical only — NOT executed this phase) |
| Manual QA evidence | manual_test_guide.md + traces | as-is | EXISTS (evidence-class) |

## 6. Review and approval requirements (proposed)

Release-affecting gates validated by owner; other gates by agent cross-check with developer confirmation. Negative results treated as findings, never hidden; no parsing/verification of statuses without date-stamps.

## 7. Exception and escalation

Environment unavailability remainder after retry: owner informed (same session) → OWNER TO BE ASSIGNED decides whether gate stays-content-open or defer-with-decision (B4 entry removes ambiguity); dispute (test-adequacy disagreement): escalate owner; disrupted evidence (run artifacts corrupted/deleted): re-run, not repair.

## 8. Related documents and current evidence status

CRM-TD-08 (test baseline/inventory/gaps), CRM-TD-09 (verification risk links), module docs M## §6 test coverage; B2, B3, B4, B8, B9. Evidence: dated run artifacts EXIST (historical); fresh run evidence NOT AVAILABLE (this phase runs none; Postgres 5433 reached? state = last verified IN this pass: not re-probed, follow records).

## 9. Approval block

| Role | Name | Date | Decision |
|---|---|---|---|
| Owner (adopts procedure) | _TBD_ | _TBD_ | _NOT SIGNED_ |
