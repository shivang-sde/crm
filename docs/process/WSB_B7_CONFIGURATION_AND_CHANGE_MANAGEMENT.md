# B7 — Configuration and Change Management

| | |
|---|---|
| **Doc ID** | CRM-WSB-B7 |
| **Version / Status** | 0.2 — PROPOSED — NOT APPROVED (activation draft; supersedes 0.1 skeleton from Phase 1.6) |
| **Prepared** | 2026-10-26 (Phase 1.7) |
| **Proposed owner role** | OWNER TO BE ASSIGNED (proposal: owner approves; agent prepares change records) |
| **Revision history** | 0.1 skeleton 2026-10-26 · 0.2 activation draft 2026-10-26 (Gate B) |
| **Approval** | PROPOSED — NOT APPROVED — adoption at first Workstream B review meeting |

## 1. Purpose and scope

Controlled handling of code, docs, schema, and configuration: what is a controlled item, how changes are requested/reviewed/approved, how baselines are tagged, and how releases relate to commits. Note: this phase performs **no** staging/commits — this document defines future rules only.

## 2. Historical practice vs proposed process

- Historical (evidence exists): 97-commit main history; committed baseline `origin/main` = `0fb9361`; working-tree discipline documented in Phase 1 audit (314 staged items, owner-controlled staging, explicit no-agent-staging rule); migration discipline Flyway-never-modify + contract tests (CRM-TD-04 §7); CI: none exists (`.github/workflows/` absent — verified).
- Proposed: carry the historical discipline forward as recorded rules + add per-change records for future work.

## 3. Trigger and required inputs

- Triggers: approved requirement/design (B2/B5); verification failure fix (B6/B4); doc correction (CRM-TD-13-led); scope/baseline change (B1); security decision (B4).
- Inputs: REQ-ID or design note, affected-module list from CRM-TD-03, current `git status` state, B1 classification rules (`COMMITTED` / `STAGED-UNCOMMITTED` / `UNTRACKED`).

## 4. Procedure (proposed)

1. **Change request (CR)** (responsible: requester/agent drafts; owner approves): unique `CHG-<nnn>`; fields: purpose statement, REQ/DES/ISSUE refs, affected items (files/dirs), risk class (trivial/major/security-critical per CRM-TD-06 zones), verification plan (B6 form). Decision point 1: owner approves CR, requests clarification, or rejects with reason.
2. **Impact check** (agent, checked at approval): what else references the touchzone (grep controllers/repos/tests), does a migration or config change enter the deployment footprint (B8 flag), does B1 snapshot classification change for any item.
3. **Implementation** (developer + agent): code + docs updated in same change (module docs/API catalog are DoD, per B5); no staged-token-bearing files enter any staging action per CRM-TD-11; migration additions follow V##-once discipline; existing staged fields remain **owner-controlled** — agent never stages (standing rule from Phase 1 audit, still in force).
4. **Verification** (B6): run evidence acquired per verification plan from step 1; failures restart at step 3 (or loop to B5 if design flaw).
5. **Commit/tagging** (owner decision, human-executed by policy): an approved, verified change becomes a commit referencing `CHG-<nnn>` `[REQ-, DES-]`; commit message structure proposed: `feat|fix|chore(scope): summary [CHG-nn]`. Baseline tags at releases (`REL-<YYYYQn>.<seq>`, B8) — no automated flow exists; tagging is a manual rule.
6. **Change record closure** (agent): record status → `CLOSED` with commit hash + verification evidence IDs; disputed/incomplete elements return to step 1 as a new CR referencing predecessor.

## 5. Outputs, records, storage

| Record | Identification | Storage (proposed) | Status |
|---|---|---|---|
| Change register | `CHG-<nnn>` rows | `docs/process/records/b7-register.md` | NOT AVAILABLE (starts for new work) |
| Baseline tag list | git tags (proposed scheme) | repo metadata | NOT AVAILABLE (no tags checked on HEAD) |
| Historical change trail | git history itself | repository | EXISTS (97 commits, `0fb9361` head; verified) |

**Starting change-register template:**

| CHG-ID | Date | REQ/DES/ISSUE refs | Purpose | Affected items | Risk class | Verification plan | Commit | Status |
|---|---|---|---|---|---|---|---|---|
| (empty) | | | | | | | | |

## 6. Review and approval requirements (proposed)

Every CHG approval by owner (step 1); commit action by human (owner); CI absence noted as accepted operational risk until decided (B4 candidate); doc corrections carry evidence links (per CRM-TD-13 discipline in phases 1.5–1.7).

## 7. Exception and escalation

Emergency fix path: apply → immediate B6 minimal evidence → CR raised retrospectively same day marked `EMERGENCY`; relevance to release scope escalates to owner directly; security-critical zones (auth/RBAC/commercial/tenant) always require owner review even for trivial classes.

## 8. Related documents and current evidence status

CRM-TD-02 (repo layout), CRM-TD-03 (module map), CRM-TD-04 (migration discipline), CRM-TD-06 (security zones), CRM-TD-08 §1 (baseline split evidence), CRM-TD-11 (staging safety rules); B1, B2, B5, B6, B8. Evidence: git history EXISTS; CI automation NOT AVAILABLE; change-register records NOT AVAILABLE.

## 9. Approval block

| Role | Name | Date | Decision |
|---|---|---|---|
| Owner (adopts procedure) | _TBD_ | _TBD_ | _NOT SIGNED_ |
