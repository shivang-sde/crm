# B1 — Project and Product Scope / Baseline

| | |
|---|---|
| **Doc ID** | CRM-WSB-B1 |
| **Version / Status** | 0.2 — PROPOSED — NOT APPROVED (activation draft; supersedes 0.1 skeleton from Phase 1.6) |
| **Prepared** | 2026-10-26 (Phase 1.7) |
| **Proposed owner role** | OWNER TO BE ASSIGNED (proposal: owner / PM, with AI-agent acting as editor) |
| **Revision history** | 0.1 skeleton 2026-10-26 · 0.2 activation draft 2026-10-26 (Gate B) |
| **Approval** | PROPOSED — NOT APPROVED — adoption at first Workstream B review meeting |

## 1. Purpose and scope

Maintain one approved scope/baseline statement for the CRM: modules and deliverables (in scope), explicit exclusions, assumptions, external dependencies, and how the baseline is changed. Covers the product (code) and its container (project). Excludes: requirements detail (B2), planning/estimates (B3).

## 2. Historical practice vs proposed process

- Historical (evidence exists): informal scope = Mia-driven feature contracts in `readme.md` + module implementation; verified technical inventory = CRM-TD-01/03/08 (v0.2). No scope-approval records exist (NOT AVAILABLE).
- Proposed: the historical practice becomes recorded: scope snapshot regenerated from the verified doc set at each baseline change, then explicitly approved.

## 3. Trigger and required inputs

- Triggers: work iteration start (B3), major feature acceptance, structural rework (module added/removed), migration-series completion, scheduled quarterly re-baseline.
- Inputs: CRM-TD-01 (system overview), CRM-TD-03 (module catalog), CRM-TD-08 §1 (committed vs working-tree baseline split), CRM-TD-09 (risk register), requester statement if change-driven.

## 4. Procedure (proposed)

1. **Draft snapshot** (responsible: B1 owner, via agent) — refresh "capability inventory" table from CRM-TD-03; classify each module item: `COMMITTED` (in `origin/main`), `STAGED-UNCOMMITTED` (git index), `UNTRACKED`, `PLANNED` (roadmap doc reference only — never "implemented").
2. **Dependency/assumption sweep** (owner + secondary reviewer _TBD_): list external dependencies (DB at 5433, Rabbit, integration endpoints, Docker infra) and assumptions; anything unverifiable gets `NOT VERIFIED`.
3. **Owner review decision point** — owner accepts the snapshot or returns items. Decision recorded in the meeting/entry below.
4. **Freeze and reference** — accepted snapshot file stored per §5; B2/B3/B8 reference B1 snapshot ID in their work; road-map items enter planning only via B3.
5. **Re-baseline on change** (trigger §3) — repeat 1–3; deltas since last snapshot listed in a baseline-change log row; disputes escalate per §7.

## 5. Outputs, records, storage

| Record | Identification scheme | Storage (proposed) | Status |
|---|---|---|---|
| Scope/baseline snapshot | `SCOPE-<YYYYQn>` with version | `docs/process/records/` (NOT CREATED YET — convention only) | NOT AVAILABLE (historical: none) |
| Baseline-change log row | appended to snapshot file | same | NOT AVAILABLE |

## 6. Review and approval requirements (proposed)

Accepted max twice-yearly minimum, and on every trigger. Approval = owner sign-on recorded in the snapshot header (`Approved by / date`). Until first approval, all snapshots are `PROPOSED — NOT APPROVED`.

## 7. Exception and escalation

If the working tree and expectations diverge (e.g., staged-only feature claimed as deliverable): owner decision within 3 business days; unresolved → owner decides, record in B4 as a risk/issue; prepare-deploy disagreements escalate to release readiness (B8 gate) immediately.

## 8. Related documents and current evidence status

CRM-TD-01, CRM-TD-02, CRM-TD-03, CRM-TD-08, CRM-TD-09; B2, B3, B8. Evidence: verified inventory EXISTS (drafts); historical scope-approval records NOT AVAILABLE.

## 9. Approval block

| Role | Name | Date | Decision |
|---|---|---|---|
| Owner (accepts scope/baseline) | _TBD_ | _TBD_ | _NOT SIGNED_ |
