# B2 — Requirements Management and Traceability

| | |
|---|---|
| **Doc ID** | CRM-WSB-B2 |
| **Version / Status** | 0.2 — PROPOSED — NOT APPROVED (activation draft; supersedes 0.1 skeleton from Phase 1.6) |
| **Prepared** | 2026-10-26 (Phase 1.7) |
| **Proposed owner role** | OWNER TO BE ASSIGNED (proposal: primary developer/PM; agent maintains register) |
| **Revision history** | 0.1 skeleton 2026-10-26 · 0.2 activation draft 2026-10-26 (Gate B) |
| **Approval** | PROPOSED — NOT APPROVED — adoption at first Workstream B review meeting |

## 1. Purpose and scope

Give every requirement a stable ID and make requirement → design/module → code/change → verification evidence → release traceable end-to-end. Scope: functional and non-functional requirements for future work; historical requirements are documented as **unavailable** unless source evidence exists.

## 2. Historical practice vs proposed process

- Historical (evidence exists): iteration contracts in `readme.md` (Customer-360/health/voice modules), module verification checklists in gate reports (FE_BE_WF19→21 chain), implementation-auditor checklists. **No requirement IDs, no trace matrix, no baseline requirement-set existed.** These are NOT reconstructed.
- Proposed: forward-only ID scheme; new requirements register requirements with unique IDs; traceability built for **new work only**.

## 3. Trigger and required inputs

- Triggers: new requirement from owner/stakeholder feedback (Mia conversations are input source, not evidence), audit/verification finding requiring corrective behavior, scope change (B1).
- Inputs: statement of need in owner words; affected modules from CRM-TD-03; existing module doc section to update.

## 4. Procedure (proposed)

1. **Request intake** (responsible: B2 owner) — requirement stated by owner/proposer is entered in the register with **REQ-ID `REQ-<nnn>`**, source ("stated by owner 2026-10-26: ..." — verbatim quote permitted), priority. Decision point: owner confirms entry is genuinely wanted (reject trivially by removing entry with note).
2. **Decompose/clarify** (owner + agent) — acceptance criteria written as testable statements; ambiguity → return to requester, not guessed.
3. **Assign design/module touchpoints** (B5 agent) — map each REQ to module docs affected (CRM-TD-M##); do not invent module behavior; if design needed, B5 procedure runs.
4. **Implementation & change link** (agent) — code work executes under a B7 change ID `CHG-<nnn>` that references REQ-ID; no REQ without eventual CHG; no CHG without reference to a REQ-ID or `IMPROV-` item (B7/B9).
5. **Verification evidence** (B6) — each acceptance criterion mapped to a test/checks (`VERIFY-`: test class names + dates for run evidence). Coverage gaps = explicit `NOT VERIFIED` rows, not silent omissions.
6. **Release link** (B8) — REQ closes only when its verification evidence is attached to a release record; release record ID cited in the REQ row.
7. **Review cadence** — trace matrix reviewed at each iteration close (with B3 gate); orphan REQs/CHGs escalate per §7.

## 5. Outputs, records, storage

| Record | Identification | Storage (proposed) | Status |
|---|---|---|---|
| Requirements register | `REQ-<nnn>` rows | `docs/process/records/requirements-register.md` | NOT AVAILABLE (empty template below) |
| Trace matrix | `REQ → module(s) → CHG → VERIFY → RELEASE` | same file or matrix per module | NOT AVAILABLE |
| Historical requirement set | — | — | NOT AVAILABLE (never captured; only evidence-class contracts exist) |

**Blank register template (start state):**

| REQ-ID | Date raised | Source (verbatim) | Statement | Acceptance criteria | Affected modules | CHG-ID | VERIFY evidence | RELEASE | Status |
|---|---|---|---|---|---|---|---|---|---|
| REQ-001 | _TBD_ | _TBD_ | _TBD_ | _TBD_ | _TBD_ | _TBD_ | _TBD_ | _TBD_ | _PROPOSED_ |

## 6. Review and approval requirements (proposed)

New REQ rows are `PROPOSED` until owner confirms (step 1 decision). Trace matrix completeness reviewed at iteration close; changes to acceptance criteria re-open the REQ (B7 change process).

## 7. Exception and escalation

Untraceable change discovered (CHG without REQ / REQ without verification): owner notified; classification = process defect (B4 issue, ID assigned); fix = add missing rows or roll back the change per B7. Disputes about priority escalate to owner at any time (same-day rule, since it's a single-team project).

## 8. Related documents and current evidence status

CRM-TD-03 (module map), CRM-TD-05 (API catalog), CRM-TD-08 (test inventory for VERIFY rows); B1, B5, B6, B7, B8. Evidence: registration scheme — new; historical records NOT AVAILABLE; iteration contracts EXISTS (evidence-class only, dated artifact).

## 9. Approval block

| Role | Name | Date | Decision |
|---|---|---|---|
| Owner (adopts procedure) | _TBD_ | _TBD_ | _NOT SIGNED_ |
