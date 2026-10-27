# Known Limitations & Technical Risks

| | |
|---|---|
| **Doc ID** | CRM-TD-09 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 |
| **Evidence basis** | Derived from repository inspection + prior audit reports (`docs\l3.4-p4-customer-health-readiness-audit.md`, `docs\hardening-fixes.md.txt`, FE_BE_WF19–21); each item cites evidence. This is a draft risk list for human review, not a formal register. |

## 1. Security / operational risks (**human review required — none altered in this pass**)

| ID | Finding | Evidence | Suggested next step (owner) |
|---|---|---|---|
| R-01 | Sensitive files present in working tree (untracked, but on disk): `.env.production`, `prod_backup.dump`, `backend\e2e-commercial-key.txt`, `backend\e2e-commercial-key-b.txt`, `backend\rbac10-tokens.json`, `backend\wf58_token.txt`, `backend\tokens_test.txt`, `docs\pashucare credential.pdf` | `git status --porcelain`; git ls-files confirms untracked | Review + secure-delete/rotate; tighten `.gitignore` (You/Manager) |
| R-02 | Empty `.env.example` (1 byte) — template not maintained ⇒ env-provisioning knowledge lives only in heads/notes | root `.env.example` | Fill template (no secrets) from real dev setups |
| R-03 | Token blacklist default BYPASS on Redis outage — availability over strictness (documented only) | `TokenBlacklistService` | Confirm with security owner whether BYPASS or DENY is the accepted standby (may need ADR) |
| R-04 | Webhook ingress rate limiting / replay windows not found in code (per `hardening-fixes.md` memo list) | `docs\hardening-fixes.md.txt`, controllers | Confirm roadmap item or implement later |
| R-05 | No dependency/vulnerability scanning (OWASP dependency-check) or security-scan config in repo | `backend\pom.xml` | Add at CI stage |
| R-06 | Ansible/prod DB backup: `prod_backup.dump` created manually at root; **no documented backup/restore procedure** | absence of ops/runbook docs | Document backup/restore (ops owner) |

## 2. Architecture / product design risks

| ID | Finding | Evidence / Ref | Impact |
|---|---|---|---|
| R-07 | Tenant isolation is per-repository manual work (`tenantId` filters) — no generic Hibernate filter/base repo; one missing clause = leak | `Data model` + `SECURITY_AND_TENANT_ISOLATION.md §4` | High severity; needs review whenever new endpoint added; consider introducing a tenant-aware base repository check or ArchUnit test |
| R-08 | `activities` polymorphic table has **no tenant_id** — security relies entirely on parent guards | V7 schema, `ActivityAccessGuard` [uncommitted] | Any future entity type without a guard could leak timeline rows; guard registration is currently convention-based |
| R-09 | Customer-health thresholds are hard-coded in `RiskSignalService` — policy-as-code (deliberate); changing thresholds = code release | `MODULE_FEEDBACK_CUSTOMER_HEALTH.md` | SLA/risk-policy governance needed if policies vary per tenant |
| R-10 | Deal dashboard stale-deal heuristic hard-coded 30 days; dashboard/ reports aggregates recalculate live (no caching) | `SalesDashboardService`, `Customer360Service` | Performance at scale untested |
| R-11 | Demo-data installer runs against live tenant via real service calls (no test coverage) | `MODULE_PLATFORM_OPERATIONS.md` §3 | Restrict to non-prod or add tenant-safety tests |
| R-12 | Notification module directory empty; functionality lives in reminder module — naming trap for new devs | `modules\notification\` | Rename/deduplicate in a refactor |

## 3. Code quality / completeness gaps

| ID | Finding | Evidence | Impact |
|---|---|---|---|
| R-13 | Large uncommitted change-set (≈120 staged-only files: Customer 360, activity guards, ops tooling, voice stack, auth hardening; migrations V65–V74) untested in CI and not committed | `git status --porcelain` (~300 entries); committed baseline counts per CRM-TD-08 §1 | Current baseline unstable; commit-and-verify with care |
| R-14 | No CI/CD pipeline — so nothing prevents untested-code commits or unverified deploy | no `.github\workflows\` | Repeatable build/test/deploy evidence missing (CMMI-relevant) |
| R-15 | Test coverage absent for ingestion mapping/transform/CSV logic, analytics scope matrix, commercial external API, tenant/user modules | `TESTING_AND_VERIFICATION_BASELINE.md §4` | Regression risk on high-value features |
| R-16 | No coverage metrics (JaCoCo/Playwright) and no OpenAPI spec file generated/committed | `backend\pom.xml`, API_CATALOG §4 | Traceability/perf gaps |
| R-17 | Single-developer history, mostly direct pushes; very few reviewed merges | git log: 97 commits, 89 by one author; earliest 4 were checks-pr merges | peer-review/quality-ownership process required |
| R-18 | No ADRs, plans, requirements doc, risk register, release notes, runbooks | repository-wide search | CMMI-relevant process-evidence gap; documentation work planned |
| R-19 | Stray migration copy file `V20__add_audit_columns_to_connector_webhook_events copy.sql`; `docs\db-entity-relationship.java` deleted in working tree (replacement ERD not produced) | migration folder | Data-design artifact churn — needs doc ownership |
| R-20 | Commercial integration — no automated tests; only manual E2E evidence files | `MODULE_COMMERCIAL_INTEGRATION.md §6` | Highest-risk unverified external API |

## 4. Product behavior explicitly **unverified in production** (code exists, deployed behavior unknown)

- Selling workflow: **full production deployment via `docker-compose.prod.yml` has not been performed/verified as part of this review** — no deployment record, no runbook, no smoke test post-deploy recorded in repo.
- Voice softphone (sip.js) runtime [uncommitted] — not verified against real SIP infrastructure (loss of acceptability check: `diagnostics` instrumentation exists but no traces).
- Commercial integration live endpoints — verify with provider before go-live (R-20).
- Register-first provisioning flow for tenants via public sign-up — behavior verified only by code + unit tests; no integration test.

## 5. Documented deliberate exclusions (from `readme.md` contract section)

- No composite health score, no churn probability, no engagement analytics beyond feedback-driven signals, no auto-renew execution (flag stored but unused), no weighting/escalation across signal types — explicitly implemented as policy/status briefs rather than process docs; see l3.4 audit report for formal "READY WITH CAVEATS"/"NO-GO" verdicts.

## 6. Refs

- Internal audits: `docs\l3.4-p4-customer-health-readiness-audit.md` (H1–H5, M1–M13, L1–L6 findings), `docs\hardening-fixes.md.txt`.
- Useful pointers: `MODULE_CATALOG.md` per-module gaps.
