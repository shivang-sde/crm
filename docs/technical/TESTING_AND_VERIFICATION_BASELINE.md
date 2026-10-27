# Testing & Verification Baseline

| | |
|---|---|
| **Doc ID** | CRM-TD-08 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 |
| **Evidence basis** | Test code counts verified 2026-10-26; test-run artifacts inspected (not re-executed by this documentation pass). |

## 1. Counts & verification performed in this documentation pass

### Baseline split (working tree vs committed, verified 2026-10-26)

- **Working tree:** 115 backend test files (~1,790 `@Test` occurrences), 65 controllers, 76 repositories, 74 migrations, 93 route pages, 199 components, 28 test spec/check files, 12 `@Scheduled` mentions / 10 real jobs.
- **Committed baseline (`origin/main` = `0fb9361`):** **64 migrations, 60 controllers, 38 backend test files** (`git ls-tree` verified). The delta (migrations V65–V74 and ~77 test files incl. tickets, feedback/health, operations, Customer 360 suites) is **staged but uncommitted** — treat committed baseline facts separately from working-tree facts everywhere in this doc set.

### Verification performed in this documentation pass

| Check | Command/Action | Result |
|---|---|---|
| Test infra availability | TCP probe of `localhost:5433` (required test PostgreSQL per `application-test.yaml`) | **Not listening** — automated test suites were **NOT executed** |
| Existing reports inspection | Read artifact files listed below | Recorded; dates noted |
| Repo counts | `Get-ChildItem`/`git` verification | Working-tree and committed-baseline counts as per the baseline split above |

Reason for not running tests: backend tests require a real PostgreSQL running at `localhost:5433` (docker compose infra not up for this session); Playwright requires full backend+frontend+infra. Running them was **not safe/practical** in this pass; no claim is made about current pass/fail state beyond the artifacts below.

## 2. Existing test evidence (dated artifacts — stale-dated, treat accordingly)

| Artifact | Path | Date | Status |
|---|---|---|---|
| Backend JUnit reports | `backend\surefire-reports\` (115 result files) | 2026-10-23 | **Passing** per report content (per module; expect staleness) |
| Focused full-suite claim | `docs\l3.4-p4-customer-health-readiness-audit.md` §7 (41 focused / 1739 total) | 2026-10-22 | Documented claim; auditor = automated |
| Playwright last run | `frontend\test-results\.last-run.json` | 2026-10-23 | `passed`, `failedTests: []` |
| Playwright report shell | `frontend\playwright-report\index.html` | 2026-10-23 | Viewer only — no embedded traces kept |
| Earlier failing run | `test-results\` (~2026-09-01) | 5 failed tests + error-context md's | Historical failures, since fixed (see WF19–21 chain) |
| E2E acceptance audits | `frontend\FE_BE_WF19_REPORT.md` (73 Playwright tests, PARTIALLY READY) → `WF20` (remediation) → `WF21` (live stack closure) | 2026-09-01 | Gate chain documented |
| Manual QA | `manual_test_guide.md` + `backend\wf58_*` curl traces | Sept–Oct 2026 | SellSpark call flow + workflow-run debugging (manual, uncommitted) |

## 3. Test architecture

- **Backend:** JUnit (Spring Boot), modules under `backend\src\test\java\...\modules\<module>\`. Real PostgreSQL `localhost:5433`, Flyway ON, `ddl-auto: none` (`backend\src\test\java\...\application-test.yaml`); **no H2/Testcontainers**. Migration correctness pinned by `*MigrationContractTest` classes (assert migration SQL text). Rabbit/Redis mocked or embeddedInstanceState — not full MQ integration tests.
- **Frontend:** Playwright (`frontend\playwright.config.ts`: baseURL configurable via env; chromium; CI-mode retries configured but **no CI workflow exists**); 3 `.cjs` contract-check scripts wired into package.json (customer360, customer-health, voice-stats); E2E how-to: `frontend\E2E_TESTING.md`.
- **No coverage tooling** — JaCoCo not configured in `backend\pom.xml`; frontend coverage not configured in Playwright (see Technical Risks metric gap).

## 4. Coverage by module (from test classes; pass status only as dated above)

| Module | Committed tests | Notable uncommitted tests | Evidence gaps |
|---|---|---|---|
| workflow | 10 classes | WorkflowRabbitConfigTest, registry/event validation | recovery/wait E2E untested |
| integration | 12 mapping/webhook/security | — | no MQ through-path E2E |
| records | 12 | — | no migration-contract test, no chain E2E |
| ticket | 17 | — | first_response stamping unverified |
| feedback/health | 10 (+2 untracked persistence) | — | some rule branches controller-test-only |
| entitlement | 6 | — | termination edge cases absent |
| deal | 11 | SalesDashboardServiceTest | dashboard E2E absent |
| catalog | 2 | — | — |
| call | 2 | — | — |
| operations | 5 | — | — |
| auth/rbac | 6 | — | no login E2E test in tree |
| shared | 10 | — | — |
| account/contact/activity | 0 | 8 (Customer360, guards) | none committed '' |
| lead/acquisition/form | 0 | — | ingestion logic (mapping/import) untested |
| analytics | 0 | — | scope matrix untested |
| demo/user/tenant | 0 | — | — |
| commercial | 0 | — | external-facing API untested (UI manual only) |
| dialer | 1 | — | — |

## 5. Manual verification procedures (kept, evidence-class)

- `manual_test_guide.md` — SellSpark click-to-call E2E: prerequisites, click-to-call, connect webhook simulation, CDR webhook simulation, expected Call status transitions (PLANNED → HELD), live polling notes.
- `backend\wf58_*` artifacts — workflow-run manual debugging traces (uncommitted; hygiene risk documented in Technical Risks).
- `backend\e2e-commercial-test.js` + `e2e-commercial-results.json` — external commercial API manual E2E (test matrix per contract doc).

## 6. Verification gaps summary (→ feeds CRM-TD-09 / risk register)

1. **No CI execution** — no GitHub Actions/Jenkins; builds/tests/integration verification run only locally by developer.
2. **No DB-automated test infra** — Postgres at :5433 convention; unproven cross-platform reproducibility; container/test container path absent.
3. **No code-coverage measurement** for backend or frontend (no jacoco/coverage config).
4. Module test gaps per table (especially lead/acquisition ingestion, analytics scope, commercial external API, tenant/user).
5. Historical claims (1739 tests passing 2026-10-22/23) are **artifacts, not continuous evidence**; current working tree includes sizeable uncommitted code + tests not included in those artifacts.
6. E2E reports exist but no release-tagged verifiable Test report template — every run should eventually be captured as a repeatable artifact.
