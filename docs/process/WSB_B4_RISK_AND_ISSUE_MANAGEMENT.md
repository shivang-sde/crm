# B4 — Risk and Issue Management

| | |
|---|---|
| **Doc ID** | CRM-WSB-B4 |
| **Version / Status** | 0.2 — PROPOSED — NOT APPROVED (activation draft; supersedes 0.1 skeleton from Phase 1.6) |
| **Prepared** | 2026-10-26 (Phase 1.7) |
| **Proposed owner role** | OWNER TO BE ASSIGNED (proposal: owner decides; agent drafts entries) |
| **Revision history** | 0.1 skeleton 2026-10-26 · 0.2 activation draft 2026-10-26 (Gate B) |
| **Approval** | PROPOSED — NOT APPROVED — adoption at first Workstream B review meeting |

## 1. Purpose and scope

Operate one register where risks and issues are identified, classified, assigned, mitigated or accepted, tracked, and closed with evidence. Scope: technical, security, process, and delivery risks. Existing audit findings enter as **documented technical findings** — they are not automatically accepted organizational risks.

## 2. Historical practice vs proposed process

- Historical (evidence exists): technical findings were produced iteratively (WF19→WF20→WF21 remediation chain; customer-health audit report 2026-10-22; this pass's CRM-TD-09 register R-01…R-13 + CRM-TD-11 security findings). No formal organizational risk register with closure decisions exists (NOT AVAILABLE).
- Proposed: CRM-TD-09/CRM-TD-11 findings become inputs; acceptance/mitigation decisions made here by the owner; not automatically transplanted as "risks" unless the owner assigns an owner, probability/impact, and mitigation decision.

## 3. Trigger and required inputs

- Triggers: B3 variance, B6 verification failure, B8 release-prep discovery, B7 change impact review, doc/security review finding, owner statement.
- Inputs: finding source reference (report/turn record + date), affected scope (module/B1 snapshot item), proposed mitigation options with costs/effort levels.

## 4. Procedure (proposed)

1. **Entry creation** (responsible: whoever finds it; agent merges): unique ID `RISK-<nn>` (uncertainty, may materialize) or `ISSUE-<nn>` (happened, e.g., failed gate, security artifact present). Minimum fields: statement, source reference, discovery date, affected scope, owner = `UNASSIGNED` until owner assigns (decision point 1).
2. **Classification** (owner): probability + impact (H/M/L), severity = matrix class; type ∈ {technical, security, process, delivery}. H-impact security items escalate to owner same-day (§7).
3. **Mitigation decision** (owner decision point 2 — for each entry exactly one): mitigate (actions + due date assigned to executor), transfer (external party - rare), accept (statement of rationale + exposure acknowledgment), defer-with-decision (explicit deferral date).
4. **Actions execution & review cadence** (executor assigned by owner; agent tracks): each action tracked to completion with evidence link; review cadence: weekly while delivery is active, min. biweekly; every entry re-checks: still real? owner still right? date still valid?
5. **Closure** (owner decision point 3): only when closure criteria below are met and evidence link is recorded; a closed entry may be re-opened if evidence contradicts its closure (e.g., new artifact shows risk materialized).
6. **Historical entry handling** — CRM-TD-09 R-01…R-13 and CRM-TD-11 items are prepared so the owner classifies each (decision point 2 applies equally); no historical finding is announced closed without evidence.

## 5. Outputs, records, storage

| Record | Identification | Storage (proposed) | Status |
|---|---|---|---|
| Risk/issue register | `RISK-<nn>`/`ISSUE-<nn>` rows | `docs/process/records/b4-register.md` | NOT AVAILABLE (starting register = table template below; existing findings cross-listed by reference) |
| Historical findings (evidence-class, not automatically risks) | various published reports | as-is | EXISTS (dated) |

**Starting register template:**

| ID | Type | Statement | Source ref | Discovered | Probability | Impact | Severity | Owner | Mitigation decision | Due | Evidence link | Status |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| (empty — to be filled by owner) | | | | | | | | | | | | |

## 6. Review and approval requirements (proposed)

Register reviewed on each cadence instance (#4); acceptance decisions recorded per entry. Report roll-up: count open/closed by class, only counts — no metrics claims (B9).

## 7. Exception and escalation

H-Impact security findings: owner notified immediately (same session), entry created same day; needed decision authority beyond owner (repo visibility change, history rewrite) → management/CEO per CRM-TD-11 §6 — these paths are closed until explicitly re-authorized. Delivery-affecting items sync into B3 (blocked items) and B8 gates.

## 8. Related documents and current evidence status

CRM-TD-09 (R-01…R-13), CRM-TD-10, CRM-TD-11 §3–6; B3, B6, B8, B9. Evidence: verified technical findings EXISTS (drafts); historical issue closure records NOT AVAILABLE.

## 9. Approval block

| Role | Name | Date | Decision |
|---|---|---|---|
| Owner (adopts procedure) | _TBD_ | _TBD_ | _NOT SIGNED_ |
