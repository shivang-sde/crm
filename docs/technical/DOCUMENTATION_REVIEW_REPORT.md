# Documentation Review Report (Phase 1.5)

| | |
|---|---|
| **Doc ID** | CRM-TD-10 |
| **Version / Status** | 0.2 — DRAFT review findings; correction-disposition recorded: D-01…D-09 were applied to the technical docs on 2026-10-26 (see CRM-TD-13 CHANGELOG). Findings text retained verbatim as the review record. |
| **Prepared** | 2026-10-26 |
| **Scope** | Read-only verification of the 23 documents created as CRM-TD-01…CRM-TD-09 and MODULE_*. No files were rewritten by this review. |
| **Method** | Automated existence-resolution of every backend class, test class, controller mapping, property, and count quoted in the documents; PowerShell `git ls-*/log/tree` checks; no secrets printed; no code or runtime behavior modified. |

## 1. Verification results summary (what held up)

| Check | Result |
|---|---|
| 114 named backend test classes cited across module docs | **114/114 resolved** in `backend\src\test\java` (incl. untracked persistence tests) |
| 224 backend class names cited (filters resolved incl. prefixed classes) | **All resolved** |
| Controller base paths & salient endpoints (AuthController incl. commented `/reset-password`, FormController publish/unpublish/duplicate, AnalyticsController `/api/v1/analytics` + `report:read`, DemoDataController status/install/delete, WorkflowHttpConnectionController + WorkflowCredentialController bases, `TicketSlaProperties` 240/1440/60s) | **Verified in code** |
| Commercial filter headers `X-API-Key` / `X-Commercial-Token`, `CommercialIntegrationProperties` feature flag ("all integration APIs return FEATURE_DISABLED") | **Verified** |
| Counts in working tree: 29 modules, 65 controllers, 76 repositories, 74 migrations, 115 test files, 93 pages, 199 components, 28 test specs, 12 `@Scheduled` mentions / 10 real jobs | **Verified** |
| `modules\notification` is an empty directory (0 files) | **Verified** |
| `.env.example` is 1 byte (empty) | **Verified** |
| springdoc-openapi 3.0.2 in `backend\pom.xml` | **Verified** |
| Uncommitted-feature markers in most module docs (Customer 360, activity guards, voice, ops tooling) | **Verified consistent with `git status`** |

## 2. Findings requiring corrections (proposed — NOT applied)

| ID | Severity | Document(s) | Finding | Evidence | Proposed correction |
|---|---|---|---|---|---|
| **D-01** | **HIGH** | `CRM_SYSTEM_OVERVIEW.md` §1 | "Prospect lifecycle — campaigns/lead capture" implies a campaign feature | Full-code search finds **no Campaign entity/module**; only `campaignSource` field on deals (`backend\...\deal\dto\DealCreateRequest.java`) | Reword to "lead ingestion + public forms"; note campaign attribution is a single deal field |
| **D-02** | **HIGH** | `SECURITY_AND_TENANT_ISOLATION.md` §5, `MODULE_COMMERCIAL_INTEGRATION.md` §5 | States commercial API keys are "stored encrypted (AES-256)" via `DefaultCredentialEncryptionService` | `CommercialIntegrationAdminController.java:46–47` comment: "Key is stored as hash; raw never persisted"; zero `encrypt|CredentialEncryption` references inside `modules\commercial` | Correct to: keys **hashed** (raw shown once at rotation); AES-256 claim applies to connector/outbound credentials only |
| **D-03** | **HIGH** | `DATA_MODEL_AND_MIGRATIONS.md` §1, `TESTING_AND_VERIFICATION_BASELINE.md` §1 | Count statements imply "74 migrations / 115 tests / 65 controllers" hold at commit `0fb9361`; pushed baseline actually contains **64 migrations, 60 controllers, 38 backend test files** | `git ls-tree -r origin/main`: 64 / 60 / 38; `git diff --cached`: 120 staged-only files (tickets, feedback+health, operations, Customer 360, tests, migrations V65–V74) | Add a "baseline split" note to both docs: working-tree vs. committed (`origin/main`) counts, and mark tickets/feedback/operations/Migrations V65–V74 as **staged, not committed** |
| **D-04** | MEDIUM | `MODULE_TICKETS_SUPPORT.md`, `MODULE_FEEDBACK_CUSTOMER_HEALTH.md`, `MODULE_PLATFORM_OPERATIONS.md` headers | "Evidence basis: commit `0fb9361`" while the covered code is staged-only (tickets `A`, feedback `AM`, operations `A`, migrations `V65–V74` `A`) | `git status --porcelain` (from Phase 1) | Reword evidence-basis lines: "staged working tree, uncommitted as of 2026-10-26" |
| **D-05** | MEDIUM | `INTEGRATIONS_AND_ASYNC_PROCESSING.md` §6 | "Spring Mail/Thymeleaf **config present**" | Only pom dependencies exist (`spring-boot-starter-mail`, `spring-boot-starter-thymeleaf`); **no mail/thymeleaf keys in `application.yaml`/`application-prod.yml`** | Reword: "dependency present; runtime configuration not found in repo" |
| **D-06** | LOW | `API_CATALOG.md` §2 | "(includes Swagger metadata mappings)" is unverified speculation about the 419 count | annotation grep only | Delete parenthetical or re-measure precisely |
| **D-07** | LOW | `MODULE_LEADS_ACQUISITION.md` §3 | Acquisition services listed bare (`IngressService`, `MappingService`…) | Actual classes are prefixed `LeadIngestion*` (`modules\acquisition\service\` — verified 13 files) | List prefixed class names |
| **D-08** | LOW | `ARCHITECTURE.md` §4 vs `INTEGRATIONS_AND_ASYNC_PROCESSING.md` §5 | Scheduler table duplicated verbatim | both files | Keep the detailed table only in TD-07; cross-reference from TD-02 |
| **D-09** | LOW | `CRM_SYSTEM_OVERVIEW.md` §4 / `ARCHITECTURE.md` §7 | Deployment claims are config-derived only ("prod stack, localhost-only ports") | verified `docker-compose.prod.yml` text (334 lines read earlier) | Acceptable as written since marked config-derived; add one clause: "never executed in any recorded deployment" |

## 3. Consistency / duplication checks

- All module docs repeat the count legend "65 controllers / 76 repositories / 74 migrations" only in `MODULE_CATALOG` and overviews — single source retained except D-08 duplicate.
- Uncommitted markers present in M02, M08, M11, M13 and core docs; missing on M04 (dashboard service), M07, M13 (per D-03/D-04) — correction D-03/04 fixes.
- Traceability: every module doc's test table was resolved 114/114 — no phantom test citations found.
- Production-behavior separation: maintained (all status disclaimers in §Test/Verification docs); historical artifacts dated 2026-09-01…2026-10-23 marked as artifacts, not current truth.

## 4. Sign-off status

No changes were applied to any existing document. Corrections pending reviewer approval.

## 5. Correction-ready text for D-03 / D-04 (proposed wording, pending approval — NOT applied)

### D-03 baseline-split clause (to be added to `DATA_MODEL_AND_MIGRATIONS.md` §1 evidence note and `TESTING_AND_VERIFICATION_BASELINE.md` §1)

> "Counts in this document describe the **working tree** (74 migrations, 65 controllers, 76 repositories, 115 backend test files, verified 2026-10-26). The committed baseline (`origin/main` = `0fb9361`, 97 commits, verified 2026-10-26 via `git ls-tree`) contains **64 migrations, 60 controllers, 38 backend test files**. The delta (migrations V65–V74, ticket/feedback/operations/Customer-360 code, and ~77 test files) is **staged but uncommitted** and must be committed before it belongs to any release baseline."

### D-04 evidence-basis lines (replacement wording for `MODULE_TICKETS_SUPPORT.md`, `MODULE_FEEDBACK_CUSTOMER_HEALTH.md`, `MODULE_PLATFORM_OPERATIONS.md` headers)

> "Evidence basis: **staged working tree (uncommitted as of 2026-10-26)**; migrations V70–V73 and all ticket code are present in the index/worktree but not in commit `0fb9361` (verified via `git status --porcelain` categories `A`/`AM` and `git ls-tree origin/main`)."
>
> (Analogous lines for feedback: V74 + feedback/risk classes `A`/`AM`/untracked; operations: V65–V66 + operations classes `A`.)
