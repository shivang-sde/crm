# B5 — Technical Solution and Design

| | |
|---|---|
| **Doc ID** | CRM-WSB-B5 |
| **Version / Status** | 0.2 — PROPOSED — NOT APPROVED (activation draft; supersedes 0.1 skeleton from Phase 1.6) |
| **Prepared** | 2026-10-26 (Phase 1.7) |
| **Proposed owner role** | OWNER TO BE ASSIGNED (proposal: primary developer author; secondary reviewer comment) |
| **Revision history** | 0.1 skeleton 2026-10-26 · 0.2 activation draft 2026-10-26 (Gate B) |
| **Approval** | PROPOSED — NOT APPROVED — adoption at first Workstream B review meeting |

## 1. Purpose and scope

Capture design reasoning persistently and review it: options considered, decision, architecture/data/tenant-isolation/impact framing. Scope: design affecting code structure, interfaces, data model, integration surfaces, security posture. Excludes coding standards already encoded in style/architecture docs (CRD conventions from CRM-TD-02 §4).

## 2. Historical practice vs proposed process

- Historical (evidence exists): design decisions were embedded in migration narratives (V1…V74 layers, CRM-TD-04 §4), module docs, iteration audit reports, and decisions referenced in `docs\CRM Research docs.md`. **No formal design review records/ADR trail exists. NOT retroactively created.**
- Proposed: lightweight decision records for future changes + explicit cross-links to already-captured architectural facts.

## 3. Trigger and required inputs

- Triggers: B2 REQ decomposition needing design; new module/table/integration; security-relevant behavior change (auth/RBAC/tenant/commercial gateway); data-model change (migrations V##); structural rework (B1).
- Inputs: REQ-ID, architecture facts from CRM-TD-02/04/06/07, B1 scope snapshot, current module docs (M##).

## 4. Procedure (proposed)

1. **Design note** (responsible: agent drafts, developer reviews): record context, options (≥2 for non-trivial), decision, consequences; capability to reference existing verified architecture facts instead of restating them. Include explicit considerations for: data options, failure modes, tenant/isolation impact (CRM-TD-06) when multi-tenant data touched.
2. **Decision point: approve as designed** (owner/designated reviewer): either approve, request options loop (re-run step 1), or reject with rationale. No design starts coding until this point; trivial changes may use the fast path (one-line decision note) — called out in the note header.
3. **Design review record** (with B6): lightweight record = the note itself + reviewer `[comment, date, decision]` stamped. A note without this stamp is `UNREVIEWED`.
4. **Implementation trace** (B7): CHG-ID references the design note; migration-linked designs must reproduce the migration-contract test update expectation (CRM-TD-04 §7).
5. **Interface & doc updates are part of DoD** (agent): module docs, API catalog (CRM-TD-05) updated in the same CHG; otherwise B6 gate fails the change.
6. **Maintenance**: a design note becomes `OBSOLETE` (never deleted from history) when superseded; the replacing note links backward.

## 5. Outputs, records, storage

| Record | Identification | Storage (proposed) | Status |
|---|---|---|---|
| Design note | `DES-<nnn>` | `docs/process/records/` | NOT AVAILABLE (starts for new work) |
| Review stamp | inside DES record | same | NOT AVAILABLE |
| Historical design reasoning | migration narratives, module docs, iteration audits | as-is | EXISTS (evidence-class; explicitly **no design-review records exist** for past work) |

## 6. Review and approval requirements (proposed)

Larger design = annotated note accepted by secondary reviewer + owner. Reviewer role assignment happens in the owner decision at §4-2; role not yet assigned = `OWNER TO BE ASSIGNED`.

## 7. Exception and escalation

Emergency hot-path design (e.g., data-loss risk mid-iteration, security issue in B4): design note may be written **after** the fix, marked `RETROSPECTIVE`, with the fixing CHG referenced — not silently omitted; disagreement between reviewers escalates to owner; still-disputed items surface as B4 process risk.

## 8. Related documents and current evidence status

CRM-TD-02, CRM-TD-04, CRM-TD-05, CRM-TD-06, CRM-TD-07, module docs M01–M13; B2, B6, B7. Evidence: design narratives EXISTS (as migration/module docs, dated); formal ADR/design-review trail NOT AVAILABLE.

## 9. Approval block

| Role | Name | Date | Decision |
|---|---|---|---|
| Owner (adopts procedure) | _TBD_ | _TBD_ | _NOT SIGNED_ |
