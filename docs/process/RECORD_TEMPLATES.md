# Prospective Record Templates (Workstream B)

| | |
|---|---|
| **Doc ID** | CRM-WSB-TPL |
| **Version / Status** | 0.1 — TEMPLATE — NO EXECUTION RECORDED |
| **Prepared** | 2026-10-26 (Phase 1.8) |
| **Rule** | Each template = blank start-state for future activity. No row may be pre-populated with historical/"reconstructed" data. Every family's rules live in its owning B-doc (linked); duplication is avoided — this file is the copy-out sheet, the B-docs define procedure and storage paths. |

## T-1 Requirements & acceptance traceability (owner doc: B2)

| REQ-ID | Date raised | Source (verbatim) | Statement | Acceptance criteria | Affected modules | CHG-ID | VERIFY evidence (test class + TEC-ID + date of run) | RELEASE | Status |
|---|---|---|---|---|---|---|---|---|---|

Candidate external sources to **verify before relying on** (existence/sufficiency not verified in Phase 1.8): any issue tracker linked to the GitHub remote (`github.com/shivang-sde/crm`), branch/PR metadata. Local repo verification found **no CI logs and no tag records** (`git tag` empty at `0fb9361`).

## T-2 Work-item planning & status (owner doc: B3)

| ITEM-ID | Date created | REQ ref | Statement | Owner | Estimate (only if owner-provided; else `NOT PROVIDED`) | Dependencies | Status + last change date | Blockers / variance ref |
|---|---|---|---|---|---|---|---|---|

## T-3 Risk & issue register (owner doc: B4) — copy of starting template from B4 §5

| ID | Type | Statement | Source ref | Discovered | Probability | Impact | Severity | Owner | Mitigation decision (mitigate/accept/defer) | Due | Evidence link | Status |
|---|---|---|---|---|---|---|---|---|---|---|---|---|

## T-4 Design decisions & technical review (owner doc: B5)

| DES-ID | Date | REQ/CHG ref | Context | Options considered | Decision | Tenant/data/security considerations | Reviewer name + date + comment | Review stamp (`UNREVIEWED` until stamped) |
|---|---|---|---|---|---|---|---|---|

Historical design reasoning already exists as migration narratives/module docs (B5 §5) — do not re-transcribe it into DES rows.

## T-5 Test execution & follow-up (owner doc: B6)

| TEC-ID | Date | Build descriptor (git HEAD at run) | Environment notes (Postgres 5433 state etc.) | Suites executed | Results per suite (actual, as observed) | Failures → ISSUE-ID | Regression scope tag | Attached artifact paths |
|---|---|---|---|---|---|---|---|---|

Defect follow-up lives in T-3 (`ISSUE-<nn>`); T-5 references, never duplicates it.

## T-6 Change review & authorization (owner doc: B7)

| CHG-ID | Date | REQ/DES/ISSUE refs | Purpose | Affected items | Risk class (incl. security-critical zone check) | Verification plan (TEC-IDs) | Commit hash (human-approved) | Approved by + date | Status |
|---|---|---|---|---|---|---|---|---|---|

Existing evidence sources already in use: git history itself (`0fb9361` + later), owner-controlled index state. CI: **does not exist** (verified) — gap stays visible in CRM-TD-08 §6.

## T-7 Release readiness, deployment, smoke, rollback (owner doc: B8)

| REL-ID | Date | Candidate commits/tag | Backup taken (name/size/sha only) | Migrations applied | Deploy steps as actually performed | Smoke checks (actual observed results) | Rollback action + cause (if any) | Operator (human) | Owner sign-off | 24–48h addendum |
|---|---|---|---|---|---|---|---|---|---|---|

`TEMPLATE — NO EXECUTION RECORDED`: no release record exists now; the first row is written at the **next actual deployment** only.

## T-8 Measurement collection & validation (owner doc: B9 — definitions MEAS-01…06)

| MEAS-ID | Collection date | Value (as extracted) | Source record refs | Validated? (traceable per-row: Y/N + check date) | Above threshold? (thresholds remain `NOT SET` until ≥2 periods of data) | Action taken (IMPROV-/ISSUE ref) |
|---|---|---|---|---|---|---|

## Cross-record consistency rules

1. One system of record per family (owning B-doc §5 path); TPL rows are created in the family's register file, not duplicated here.
2. All blank templates above are stamped `TEMPLATE — NO EXECUTION RECORDED` and must remain empty until a real event supplies a row.
3. Any future record produced elsewhere (issue tracker, PR system, CI) may be **referenced by ID** in the family register after its existence and sufficiency are verified by the owner — never assumed.
4. Evidence confidentiality: artifact paths only; contents of credential/token-bearing files are never printed into any record (CRM-TD-11 rule).
