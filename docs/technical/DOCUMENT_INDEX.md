# Technical Documentation Index

| | |
|---|---|
| **Doc ID** | CRM-TD-00 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 |
| **Purpose** | Register of all Phase-1 technical baseline documents. Phase 1 is documentation-only: nothing here is an approved policy/process document, and no CMMI/Six Sigma claim is attached. |

## Reading order

Start → `CRM_SYSTEM_OVERVIEW.md`, then `ARCHITECTURE.md`; use per-module docs as needed.

## Core documents

| ID | Document | Covers |
|---|---|---|
| CRM-TD-01 | `CRM_SYSTEM_OVERVIEW.md` | Product purpose, stack, capability inventory, exclusions |
| CRM-TD-02 | `ARCHITECTURE.md` | Layered architecture, outbox/Rabbit pipeline, scheduling, frontend + deploy topology |
| CRM-TD-03 | `MODULE_CATALOG.md` | 29 modules + shared infra table with pointers |
| CRM-TD-04 | `API_CATALOG.md` | Endpoint families, ingress whitelist, conventions |
| CRM-TD-05 | `DATA_MODEL_AND_MIGRATIONS.md` | 74 migration index, relationships, hygiene findings |
| CRM-TD-06 | `SECURITY_AND_TENANT_ISOLATION.md` | Identity chain, RBAC, tenant mechanism, secrets, risks |
| CRM-TD-07 | `INTEGRATIONS_AND_ASYNC_PROCESSING.md` | Event model, Rabbit topology, scheduled jobs, external integration inventory |
| CRM-TD-08 | `TESTING_AND_VERIFICATION_BASELINE.md` | What was/wasn't executed, artifacts, per-module test coverage table |
| CRM-TD-09 | `KNOWN_LIMITATIONS_AND_TECHNICAL_RISKS.md` | R-01…R-20 evidence-based risk list |
| CRM-TD-00 | this file | register |
| CRM-TD-10 | `DOCUMENTATION_REVIEW_REPORT.md` | Phase-1.5 review findings D-01–D-09, verification method, correction-ready wording |
| CRM-TD-11 | `SECURITY_HYGIENE_REVIEW.md` | Phase-1.5 security-hygiene audit incl. post-clarification update (§6, v0.2) |
| CRM-TD-12 | `NEXT_PHASE_PROPOSAL.md` | Workstream B proposal (Part C) + Six Sigma readiness inventory (Part D) |
| CRM-TD-13 | `CHANGELOG.md` | Documentation change log: corrections applied (0.1→0.2), deferred items |

## Module documents (`docs\technical\modules\`)

| ID | Document | Modules covered |
|---|---|---|
| CRM-TD-M01 | `MODULE_AUTH_IDENTITY.md` | auth, rbac, tenant, user |
| CRM-TD-M02 | `MODULE_ACCOUNTS_CONTACTS_ACTIVITIES.md` | account, contact, activity (+ Customer 360 [uncommitted]) |
| CRM-TD-M03 | `MODULE_LEADS_ACQUISITION.md` | lead, acquisition, form |
| CRM-TD-M04 | `MODULE_DEALS_PIPELINE.md` | deal |
| CRM-TD-M05 | `MODULE_OFFERINGS_ENTITLEMENTS.md` | catalog, entitlement |
| CRM-TD-M06 | `MODULE_COMMERCIAL_INTEGRATION.md` | commercial |
| CRM-TD-M07 | `MODULE_TICKETS_SUPPORT.md` | ticket |
| CRM-TD-M08 | `MODULE_FEEDBACK_CUSTOMER_HEALTH.md` | feedback + risk signals |
| CRM-TD-M09 | `MODULE_ACTIVITIES_COMMUNICATION.md` | task, meeting, call, recurrence, reminder/notification |
| CRM-TD-M10 | `MODULE_WORKFLOW_ENGINE.md` | workflow |
| CRM-TD-M11 | `MODULE_INTEGRATIONS_TELEPHONY.md` | integration, dialer, voice stack |
| CRM-TD-M12 | `MODULE_RECORDS_PLATFORM.md` | records |
| CRM-TD-M13 | `MODULE_PLATFORM_OPERATIONS.md` | operations, analytics, demo |

## Relationship to existing documentation

- **Complements, does not replace:** `readme.md` (product/health contract — remains the artifact of record for Customer-Health semantics), `backend\docs\COMMERCIAL_INTEGRATION_API.md` (external API contract), `docs\CRM Research docs.md` (decision rationale), FE_BE_WF19–21 and l3.4 audit reports (evidence artifacts).
- **Supersedes structurally:** `docs\db-entity-relationship.java` (deleted in working tree) — DATA_MODEL doc now carries schema documentation (a generated ERD is recommended as follow-up).
- **Next phases (explicitly out of scope here):** requirements/traceability, project planning, SDLC/process docs (CMMI), quality-improvement work (Six Sigma) — Phase 1.6/1.7 created draft skeletons for these under `docs\process\` (see `EVIDENCE_AND_DOCUMENT_REGISTER.md`); none adopted/approved yet.

## Evidence conventions used in this doc set

- *Verified* = direct code/artifact inspection recorded in docs.
- *[uncommitted]* = present in working tree but not yet committed (`git status` categories M/A/??).
- *Unverified* = plausibly correct behavior without run/proof; listed in CRM-TD-08/09.
- Test results only stated for artifacts that exist with dates; tests were **not re-run** in this pass (reason documented).
- No secrets, tokens, credentials, or personal data copied from the repo.
