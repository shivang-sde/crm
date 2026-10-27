# B0 — Evidence and Document Register (Workstream B)

| | |
|---|---|
| **Doc ID** | CRM-WSB-B0 |
| **Version / Status** | 0.2 — DRAFT, NOT APPROVED (activation draft; supersedes 0.1 skeleton from Phase 1.6) |
| **Prepared** | 2026-10-26 (Phase 1.7) |
| **Proposed owner role** | OWNER TO BE ASSIGNED (proposal: owner approves annual; agent maintains) |
| **Revision history** | 0.1 skeleton 2026-10-26 · 0.2 full attribute-table draft 2026-10-26 (Gate B) |
| **Approval** | PROPOSED — NOT APPROVED — adoption at first Workstream B review meeting |

Registers every evidence artifact the Workstream B documents rely on. An artifact in this register is evidence; a template is not an approval. Record IDs: `EVD-<nn>`.

| ID | Artifact / process name | Required / Optional | Owner role | Source or storage location | Current status | Evidence period / date | Reviewer / approval status | Confidentiality / access | Related REQ / RISK / CHG / REL |
|---|---|---|---|---|---|---|---|---|---|
| EVD-01 | Committed-baseline counts (64 migrations / 60 controllers / 38 backend test files) | REQUIRED for baseline claims | OWNER TO BE ASSIGNED (verification) | `git ls-tree -r origin/main` @ `0fb9361` | VERIFIED | 2026-10-26 (re-checked Phase 1.7) | PROPOSED — NOT APPROVED | none (counts only, no content) | CRM-TD-08 §1 |
| EVD-02 | Working-tree baseline counts (74 / 65 / 115) | REQUIRED | OWNER TO BE ASSIGNED | `Get-ChildItem`/`git status` categories | VERIFIED | 2026-10-26 | PROPOSED — NOT APPROVED | none | CRM-TD-08 §1, R-13 |
| EVD-03 | Staged-but-uncommitted change-set (≈120 files: Customer 360, tickets, feedback/health, operations, RBAC; V65–V74) | REQUIRED context | OWNER (state owned by them) | `git status --porcelain` (`A`/`AM`) | VERIFIED (uncommitted) | 2026-10-26 | n/a (state, not approval) | follow track rules of affected code; do not reprint contents | B1 classification, R-13 |
| EVD-04 | Scheduler/job inventory (10 real jobs + intervals) | OPTIONAL (single-source reference) | agent-maintained | CRM-TD-07 §5 | VERIFIED | 2026-10-26 | PROPOSED — NOT APPROVED | none | TD-02 (D-08 pointer) |
| EVD-05 | Commercial API-key hashing evidence | REQUIRED for security statements | OWNER (decision) including hash/encryption scope | `CommercialIntegrationAdminController.java:45–47` comment + module grep | VERIFIED | 2026-10-26 | PROPOSED — NOT APPROVED | none (no values shown) | CRM-TD-06 §5, TD-M06 |
| EVD-06 | Backend surefire-reports (115 files) | OPTIONAL (historical only) | agent | `backend\surefire-reports\` | EXISTS (historical; NOT re-executed Phase 1.7) | 2026-10-23 | n/a (artifact) | none | TD-08 §2, B6 historical |
| EVD-07 | Playwright last-run pass | OPTIONAL (historical only) | agent | `frontend\test-results\.last-run.json` | EXISTS (historical) | 2026-10-23 | n/a (artifact) | none | TD-08 §2 |
| EVD-08 | Focused full-suite claim (41/1739) | OPTIONAL (documented claim) | auditor (automated, historical) | `docs\l3.4-p4-customer-health-readiness-audit.md` §7 | EXISTS (claim, not re-verified) | 2026-10-22 | n/a (prior report) | none | TD-08 §2 |
| EVD-09 | WF19→WF20→WF21 gate chain reports | OPTIONAL (historical; risk/issue source) | agent | `frontend\FE_BE_WF19_REPORT.md` etc. | EXISTS (dated) | 2026-09–10 | n/a (prior reports) | none | B4 source refs, B6 gates |
| EVD-10 | Manual QA traces (`manual_test_guide.md`, `backend\wf58_*` artifacts) | OPTIONAL | agent | listed files (working tree) | EXISTS (manual, uncommitted) | Sept–Oct 2026 | n/a | none except wf58 token files — see EVD-13 | TD-08 §5 |
| EVD-11 | `docs/pashucare credential.pdf` tracking state | REQUIRED (pending owner/management decision) | OWNER / management | git index + `origin/main` tree & reachable history; local copy DELETED unstaged by owner | PENDING DECISION (mock content per OWNER STATEMENT — not independently verified) | 2026-10-26 state | SEE CRM-TD-11 §6 | CONFIDENTIAL handling: never open/print contents; history decisions reserved to owner | CRM-TD-11, B4 security entries |
| EVD-12 | Deployment configuration (Dockerfile, compose prod, nginx) | REQUIRED for release readiness claims | agent | `backend\Dockerfile`, `docker-compose.prod.yml`, `nginx\default.conf` | VERIFIED config-only | 2026-10-26 | PROPOSED — NOT APPROVED | none | TD-02 §7, B8 — NO deploy evidence exists (D-09) |
| EVD-13 | Local security artifacts (`e2e-commercial-key*.txt`, `rbac10-tokens.json`, `wf58_token.txt`, `tokens_test.txt`, result JSON/JS) | OPTIONAL (owner to disposition) | OWNER | working tree, untracked | EXISTS locally — DO NOT commit; no values in docs | 2026-10-26 | OWNER decision pending | CONFIDENTIAL: never print/copy; referenced by path only | CRM-TD-11 §4 |
| EVD-14 | Technical doc set CRM-TD-01…13 + M01–M13 (v0.2 drafts) | REQUIRED | OWNER (approval pending) | `docs\technical\` | DRAFT, UNREVIEWED | 2026-10-26 | PROPOSED — NOT APPROVED | none | all Workstream B docs |
| EVD-15 | Workstream B process docs B0–B9 (this set) | REQUIRED for activation | OWNER (adoption pending) | `docs\process\` | 0.2 PROPOSED — NOT APPROVED | 2026-10-26 | PROPOSED — NOT APPROVED | none | B1–B9 cross-refs |
| EVD-16 | Six Sigma readiness inventory (SSM) | OPTIONAL (recorded decision pending) | OWNER / management | `docs\process\QUALITY_METRICS_READYNESS.md` | 0.1 DRAFT, NOT APPROVED | 2026-10-26 | PROPOSED — NOT APPROVED | none | B9 separation rule |
| EVD-17 | `frontend\.env.test` | OPTIONAL (tracked; disposition pending) | OWNER | tracked & pushed | EXISTS in repo — contents NOT inspected (values never printed); CRM-TD-11 H-06 = human content review required | tracked in history (verified 2026-10-26) | CRM-TD-11 H-06 open | CONFIDENTIAL handling: never print values; `.gitignore` env rule missing (H-05) | CRM-TD-11 §3–4 |

Rules: (1) Template existence never implies approval. (2) Historical artifacts stay dated inline and are never re-presented as fresh results. (3) Security-content artifacts are referenced by path only; contents are never displayed, copied, or logged. (4) Every factual correction cites an evidence link (CRM-TD-13 discipline). (5) Future captured records (TEC-, CHG-, REQ-, REL-, DES-, MEAS- families, per B1–B9) are added here when created, NOT before.
