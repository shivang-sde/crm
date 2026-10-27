# Phase 2 Preparation Proposal (Workstream B + Six Sigma Readiness)

| | |
|---|---|
| **Doc ID** | CRM-TD-12 |
| **Version / Status** | 0.1 — PROPOSAL ONLY, not approved, not implemented. Update 2026-10-26: Workstream B skeleton documents (B1–B9, B0 register, QUALITY_METRICS_READYNESS) were created per this proposal during Phase 1.6; adoption/approval of any proposal content remains pending. |
| **Prepared** | 2026-10-26 |
| **Purpose** | Part C (Workstream B — process/documentation package) and Part D (Six Sigma readiness) per Phase 1.5 review request. Reuses CRM-TD-01…09 technical baseline. |

## Part C — Workstream B proposed documents (outlines; creation pending approval)

Candidate set deliberately consolidated. **No document here is asserted to be universally mandatory under any CMMI model** — final tailoring depends on model version/scope (unresolved; see §C.5).

| # | Proposed doc (docs/process/) | Outline | Records that already exist | New records required going forward | Reconstruction type |
|---|---|---|---|---|---|
| B1 | `CRM_PRODUCT_BASELINE_AND_REQUIREMENTS_TRACEABILITY.md` | Functional baseline per module (from CRM-TD-01/M01–M13); requirement IDs mapped to code modules/tests | Partial: feature inventory verifiable in code + `backend\docs\required_changes\*.md` informal specs; FE_BE acceptance reports | Formal requirement IDs + acceptance criteria; traceability matrix | **Retrospective reconstruction** from verified features — must be labeled as reconstructed |
| B2 | `PROJECT_PLAN_AND_RISK_REGISTER.md` | Scope, milestones, schedule, status cadence; risk register seeded from CRM-TD-09 R-01…R-20 | None formal; incidental evidence: sprint prompts, commit/branch names, audit finding lists | Plan, schedule records, status reports, risk entries with owner/mitigation | **Retrospective skeleton + contemporaneous from now** |
| B3 | `SDLC_PROCEDURE.md` | Phased lifecycle (requirements→design→build→review→test→release→ops) with roles, entry/exit criteria, tailoring rules | Informal: `backend\docs\devlopment\MODULE_DEVELOPMENT_WORKFLOW.md`; actual practiced flow inferable from git history | Approved procedure + tailoring guidelines | Retrospective codification of practiced reality, then contemporaneous adherence |
| B4 | `CODE_REVIEW_PROCEDURE.md` | PR/merge rules, review checklist, review-record format | Historic PR merges #1–#4 only; last ~15 months = direct pushes | Review records per change going forward | **Process change**, not doc-only |
| B5 | `VERIFICATION_AND_VALIDATION_PLAN.md` | Test approach (unit/integration/E2E/manual/contract checks), coverage targets, release-qualification criteria; evidence templates | Rich existing test assets (CRM-TD-08) but no coverage measurement, no CI | Coverage metrics, release test reports, V&V log | Mixed: plan as new record; artifacts accumulate forward |
| B6 | `CONFIGURATION_AND_CHANGE_CONTROL.md` | Repo conventions, branching, migration/version discipline (Flyway), change request→approval implementation, CM audit | Partial: Flyway history, conventional commits, .env conventions (CRITICAL security caveat: pashucare PDF exposure — CRM-TD-11) | Change-request/approval log; CM audit notes | Process change + forward records |
| B7 | `RELEASE_AND_DEPLOYMENT_PROCEDURE.md` | Build→package→migrate→deploy→smoke→rollback; runbook from `docker-compose.prod.yml`/`application-prod.yml`/`nginx\default.conf` | Deployment configs exist; **no deployment record, release notes, or rollback evidence** | Release checklist, deployment log, rollback procedure | Operational change + forward records |
| B8 | `MEASUREMENT_AND_PROCESS_IMPROVEMENT.md` | Small metric set (see Part D), collection points, improvement loop | InfrastructureMetrics exists (runtime telemetry); no project-level metrics | Metric baselines, improvement actions log | Forward from baseline (never invent past data) |
| B9 | `EVIDENCE_REGISTER_AND_DOCUMENT_CONTROL.md` | Document ID/version/owner/approval scheme (pattern already proven in CRM-TD docs), evidence index, retention | Register format exists informally in CRM-TD-00/10/11/12 | Approved register; per-item evidence pointers | Contemporaneous |

Sequencing recommendation (same as Phase-0 report): B3+B4 → B1/B2 → B5–B7 as operational changes land (CI first) → B8/B9 close.

### C.5 Unresolved management/stakeholder decisions (blocking)

1. CMMI model version and reference materials access (Development? V2.2 or V3.0?) — decides PA tailoring of B3/B4/B6.
2. Appraisal scope: this CRM project only (pilot) vs whole organization (Level 3 ratings are org-wide).
3. Target outcome: real Benchmark appraisal rating vs internal improvement exercise; Lead Appraiser engagement.
4. Business driver: customer contract requirement? Determines depth (B1 traceability rigor especially).
5. Repo visibility policy + credential-rotation decision (H-01/H-07 in CRM-TD-11) — blocks B6 approval coupling.
6. Who are the named owners/approvers for B2–B9 (currently only "primary developer" role exists)?
7. Six Sigma: sponsor, target metric, and relation to CMMI measurements (avoid duplicate data collection).

## Part D — Six Sigma readiness assessment

> Goal framing: Six Sigma style improvement needs a measurable baseline (defects/failures per unit of work) before/after interventions. **No improvement claim is made** — no intervention has occurred yet.

### D.1 Available quality-data sources (verified inventory)

| Source | Period available | Data usable for |
|---|---|---|
| `backend\surefire-reports\` (115 dated result files) | 2026-10-23 (+ older snapshots) | Unit/integration test pass rate at point-in-time |
| `frontend\test-results\.last-run.json` + older failing `test-results\` (5 failures ~2026-09-01) | 2026-09-01 → 2026-10-23 | E2E failure rate snapshots (sparse) |
| `frontend\FE_BE_WF19/20/21_REPORT.md` chain | 2026-09-01 | Defects found in acceptance audits (P1 counts), rework loop evidence |
| `docs\l3.4-p4-customer-health-readiness-audit.md` (H1–H5, M1–M13, L1–L6 = 24 findings) | 2026-10-22 | Static-audit defect density snapshot per module |
| `docs\hardening-fixes.md.txt` memo | undated (pre-launch memo) | Candidate issue list, not measured |
| `backend\test_out.txt` (2026-10-07, compile failure: `cannot find symbol ... lombok`-style entries) | 2026-10-07 | One recorded **failed build** event |
| Commit history (97 commits, conventional, one author) | 2026-06?–2026-10 (first commit date not yet computed) | Change volume proxy; `fix:` ratio proxy — **weak, single-dev, direct pushes** |
| Runtime telemetry: `InfrastructureMetrics` (outbox/rabbit/redis) | Unknown — no metric scrape/panel archived in repo | Future operational defect signals once scraped/retained |

### D.2 Metrics reliably calculable today, with limitations

| Metric | Feasible? | Observation period | Limitation |
|---|---|---|---|
| Test pass rate per dated artifact (backend/frontend) | ✔ | 2026-10-23 single point (+ 2026-09-01 failure sample) | Point-in-time, sparse; not CI-continuous |
| Defects introduced vs found (escape rate) | ✖ | — | No defect tracker with origin/closure metadata |
| Defect density by module | Partial | 2026-10-22 audit snapshot only | One audit cycle; module sizes non-uniform |
| Rework/rebuild frequency | Partial | commit log scan for `fix:`-type commits | Labels self-applied; no link to failures |
| Lead time / cycle time | Partial | commit/date diffs | Single developer, no work-item dates → proxy at best |
| Incident/release failure rate | ✖ | — | No release records exist |
| DPMO / Sigma level | ✖ | — | **No defect-opportunity counts, no unit model. Baseline NOT establishable from current data — do not invent one** |
| Coverage-based metrics | ✖ | — | No JaCoCo/coverage instrumentation |

### D.3 Data limitations (root causes)

1. No CI ⇒ no reproducible, stamped run history (only stray artifacts).
2. No defect tracker ⇒ no defect origin/close/cost records.
3. No historical claims allowed beyond dated artifacts — the "1739 tests passing" claim exists for 2026-10-22/23 only.
4. Test infra depends on local Postgres (port 5433) — no containerized stable runs.

### D.4 Improvement candidates (hypotheses only — require before/after measurement)

- **Op-1:** Stand up CI with stamped test/coverage artifacts → enables run-over-run pass-rate, flake rate, coverage trends (first measurable baseline).
- **Op-2:** Add defect-tracking with origin/closure → enables escape-rate measurement (input for any sigma-style metric later).
- **Op-3:** Remediate highest-density audit cluster (ingestion tests, R-15) → measurable via targeted defect density before/after.
- **Op-4:** Use existing `InfrastructureMetrics` → archive scraped counters (outbox DEAD rate, DLQ depth) → operational defect stream.

Priority: D.3 item 1 (CI) is prerequisite to everything else; it is also CMMI-relevant (per prior phase assessment) — one investment, two objectives.

### D.5 Management decisions required (duplicate of C.5.7 for emphasis)

- Whether Six Sigma is a formal initiative (Yellow/Green/Black-belt infrastructure) or an informal data-driven improvement practice; affects B8 design only.
