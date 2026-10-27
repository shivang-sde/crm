# Documentation Change Log

| | |
|---|---|
| **Doc ID** | CRM-TD-13 |
| **Version / Status** | 0.3 — DRAFT, UNREVIEWED (extended by Phase 1.7 quality gate and Phase 1.8 management-review prep) |
| **Scope** | Changes to the CRM documentation sets (docs\technical\, docs\) |

## 2026-10-26 — Phase 1.8 (management-review prep; documentation-only)

| Addition | File(s) | Note |
|---|---|---|
| New | `docs\process\MANAGEMENT_DECISION_REGISTER.md` (CRM-WSB-DEC) | 9 decisions DEC-01…09, all `OWNER/MANAGEMENT DECISION REQUIRED`, fields blank |
| New | `docs\process\PROCESS_ADOPTION_REVIEW_PACK.md` (CRM-WSB-PACK) | PROPOSED — NOT APPROVED; no meeting held, no minutes fabricated |
| New | `docs\process\RECORD_TEMPLATES.md` (CRM-WSB-TPL) | 8 template families T-1…T-8, all `TEMPLATE — NO EXECUTION RECORDED`; reuses B-doc record IDs; candidate external sources listed as unverified |
| New | `docs\process\PROCESS_READINESS_BOUNDARIES.md` (CRM-WSB-BND) | Task-5 boundaries (docs ≠ adoption; no reconstruction; CMMI/Six Sigma claim rules) |
| Update | `docs\technical\SECURITY_HYGIENE_REVIEW.md` | §6.7 Phase 1.8 status re-check added (fact-distinct, no status change, closure rules stated) |
| New | `docs\technical\PHASE_1_8_HANDOFF.md` (CRM-TD-15) | phase handoff record |

No application/code/config/migration/test changes; nothing staged or committed; no verified status change to any security artifact (states re-checked 2026-10-26).

## 2026-10-26 — Phase 1.7 quality-gate corrections (Gate A4)

All verified against repo state on 2026-10-26; counts for committed/working-tree baselines were re-measured (64/60/38 committed vs 74/65/115 working tree — confirmed).

| Finding Q-01 | `WSB_B4` | record row cited a non-existent artifact `docs\l3.4-p2-auditors-technical-findings.md` | replaced with real sources: `docs\l3.4-p4-customer-health-readiness-audit.md` + WF19→21 gate-chain reports (existence verified on disk) | evidence: directory listing |
| Finding Q-02 | `DOCUMENTATION_REVIEW_REPORT.md` | header said "documents NOT yet corrected (awaiting approval)" — stale after Phase 1.6 corrections | status line updated to state D-01…D-09 applied 2026-10-26; findings text kept verbatim | evidence: CRM-TD-13 prior section + applied diffs |
| Finding Q-03 | `NEXT_PHASE_PROPOSAL.md` | header said "no process documents created yet" — stale (B docs exist) | annotated with Phase 1.6 creation fact; proposal/approval status unchanged | evidence: docs\process\ contents |
| Finding Q-04 | `DOCUMENT_INDEX.md` | "none started in Phase 1" clause stale; table spacing glitch after TD-13 row | updated to record Phase 1.6/1.7 skeletons under docs\process\; spacing restored | evidence: path listing |
| Finding Q-05 | B1–B9, B0 | Prose expanded from skeleton to full activation template (procedures, decision points, escalation, approval blocks); all status lines now `PROPOSED — NOT APPROVED` | Gate B; content derived from already-verified facts; no new claims | docs\process\ diffs |
| Addition | `docs\process\WORKSTREAM_B_ACTIVATION_PLAN.md` | new (Gate C) | |
| Addition | `docs\technical\PHASE_1_7_QUALITY_GATE_REPORT.md` | new (Gate report) | |
| Update | `QUALITY_METRICS_READYNESS.md` | Phase 1.7 re-check note; no data changes | Gate A2 result |

Integrity checks performed (Gate A2, details in PHASE_1_7 report): all 37 doc IDs unique; § cross-references resolve; no unsupported CMMI/Six Sigma/production claims found; baseline counts consistent; no secrets printed; staging/history untouched.

## 2026-10-26 — 0.1 → 0.2 (approved factual corrections applied)

Applied per the phase 1.6 owner instruction: "apply only corrections that are unambiguous, evidence-supported, and consistent with the existing source." Findings IDs reference `docs\technical\DOCUMENTATION_REVIEW_REPORT.md` (CRM-TD-10). All 23 original docs (CRM-TD-01…09, MODULE_CATALOG & core set, M01–M13) bumped to **0.2 — DRAFT, UNREVIEWED**.

| Finding | File(s) | Change applied | Evidence basis |
|---|---|---|---|
| D-01 | `CRM_SYSTEM_OVERVIEW.md` §1 | "campaigns/lead capture" → lead capture; added note that no campaign entity exists (only `campaignSource` on deals) | code search (no Campaign module); `DealCreateRequest.java` |
| D-02 | `SECURITY_AND_TENANT_ISOLATION.md` §5; `modules\MODULE_COMMERCIAL_INTEGRATION.md` Security | AES-256 claim restricted to connector/outbound credentials; commercial API keys stated as **hashed, shown once** | `CommercialIntegrationAdminController.java:45–47` comment; grep found zero encryption references in commercial module |
| D-03 | `DATA_MODEL_AND_MIGRATIONS.md` header; `TESTING_AND_VERIFICATION_BASELINE.md` §1 (new "Baseline split"); `MODULE_CATALOG.md` evidence basis | Explicit working-tree (74 migrations / 65 controllers / 115 tests) vs committed-baseline (`origin/main`: 64 / 60 / 38) split | `git ls-tree -r origin/main` counts; `git diff --cached` |
| D-04 | `modules\MODULE_TICKETS_SUPPORT.md`, `MODULE_FEEDBACK_CUSTOMER_HEALTH.md`, `MODULE_PLATFORM_OPERATIONS.md`, `MODULE_DEALS_PIPELINE.md` headers; `MODULE_CATALOG.md` rows | Evidence-basis lines reworded: features exist in **staged working tree, not committed** | `git status --porcelain` (`A`/`AM`); `git ls-tree origin/main` |
| D-05 | `INTEGRATIONS_AND_ASYNC_PROCESSING.md` §6 mail row | "config present" → dependencies-only wording | `backend\pom.xml` (mail/thymeleaf starters); no mail/thymeleaf keys in `application.yaml`/`application-prod.yml` |
| D-06 | `API_CATALOG.md` | Removed unverified "includes Swagger metadata mappings" parenthetical; kept plain annotation-grep count | annotation grep only |
| D-07 | `modules\MODULE_LEADS_ACQUISITION.md` §3 | Acquisition service names listed with actual `LeadIngestion*` prefixes (13 files) | `modules\acquisition\service\` listing 2026-10-26 |
| D-08 | `ARCHITECTURE.md` §4 | Duplicate scheduler table removed; single source of truth now `INTEGRATIONS_AND_ASYNC_PROCESSING.md` §5 with cross-reference | duplication observed in 1.5 review |
| D-09 | `CRM_SYSTEM_OVERVIEW.md` §2 Deploy row; `ARCHITECTURE.md` §7 heading | Added explicit "never executed in any recorded deployment documented in this repo" clause | absence of deployment records/artifacts |
| General | all 23 files | Header standardized to `0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26…)` | phase 1.6 task 3 |
| Addition | `DOCUMENT_INDEX.md` | Registered CRM-TD-10…13 | this log |

## Corrections deferred (need owner/management decision — NOT applied)

| Item | Why deferred |
|---|---|
| History-remediation of `docs/pashucare credential.pdf` (mock per owner; still in index + pushed tree/history) | History rewrite/visibility changes not authorized; deletion commit is an owner action |
| Repo visibility / secret-scanning settings | Repo owner decision |
| Local security-artifact cleanup (`e2e-commercial-key*.txt`, token JSON/TXT files) | Owner decision; files preserved |
| CMMI tailoring of Workstream B skeletons (model version/scope/appraisal type unknown) | Management + advisor |
| Any production-behavior claims | None made; production verification evidence does not exist |

## 2026-10-26 — new draft documents created

- `docs\process\WSB_B1…B9_*.md` — Workstream B skeletons (B1–B9), 0.1 DRAFT skeletons, NOT approved.
- `docs\process\EVIDENCE_AND_DOCUMENT_REGISTER.md` — artifact/evidence register.
- `docs\process\QUALITY_METRICS_READYNESS.md` — Six Sigma readiness data inventory (kept separate, per task 9; no invented metrics).
