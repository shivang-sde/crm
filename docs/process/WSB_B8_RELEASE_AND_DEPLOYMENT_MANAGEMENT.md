# B8 — Release, Build, and Deployment Management

| | |
|---|---|
| **Doc ID** | CRM-WSB-B8 |
| **Version / Status** | 0.2 — PROPOSED — NOT APPROVED (activation draft; supersedes 0.1 skeleton from Phase 1.6) |
| **Prepared** | 2026-10-26 (Phase 1.7) |
| **Proposed owner role** | OWNER TO BE ASSIGNED (proposal: owner deploys; agent prepares readiness checklist) |
| **Revision history** | 0.1 skeleton 2026-10-26 · 0.2 activation draft 2026-10-26 (Gate B) |
| **Approval** | PROPOSED — NOT APPROVED — adoption at first Workstream B review meeting |

## 1. Purpose and scope

Define how a release candidate becomes a deployed release: readiness, build, migration/backup, approval, smoke checks, rollback, incident handling, post-deployment verification, and **release records**. This phase records deployment evidence as explicitly UNAVAILABLE — no historical releases are claimed or reconstructed.

## 2. Historical practice vs proposed process

- Historical evidence-class (configuration): `backend\Dockerfile`, `docker-compose.prod.yml`, `nginx\default.conf` verified by direct inspection; database migration discipline verified in CRM-TD-04; PG snapshot precedent exists (`prod_backup.dump` present as an ignored working-tree file — contents never inspected, provenance NOT VERIFIED). **No release records, no runbooks, no post-deploy verification evidence — none fabricated here (D-09 standing correction).**
- Proposed: deployment becomes recorded with the record scheme below, effective for the **next actual deployment only**.

## 3. Trigger and required inputs

- Triggers: release-candidate statement by owner; CHG batch closed (B7) and verified (B6).
- Inputs: commit/tag identity at candidate (B7 tagging rule), B6 fresh execution-record IDs (on current build), migration inventory diff (new V## files since last deployed state — TODAY: entire history is "since baseline zero" from this process's perspective), environment state facts (PostgreSQL full, Docker volumes, env-file state referencing non-secret keys only).

## 4. Procedure (proposed)

1. **Readiness checklist** (agent drafts; owner approves): candidate commits tagged; B6 execution records **on the candidate build** (fresh run, not historical artifacts); B4 security-critical open items resolved/accepted; B1 snapshot updated if scope changed; env config reviewed (deployment-referenced secrets remain **unprinted** — values handled only in the actual environment).
2. **Backup decision point** (owner): pre-deploy database snapshot (`pg_dump`-based like existing precedent), stored outside repo, recorded by file name + timestamp + size + sha256 — content never printed into docs.
3. **Migration step** (agent reviews list; owner executes): new migrations V## applied by startup-Flyway; contract-test coverage must exist for each (CRM-TD-04 §7); a migration that fails blocks release (forward-only stance — rollback = forward-fix).
4. **Build & deploy** (owner executes; agent records): build via verified `backend\Dockerfile` + `docker-compose.prod.yml`; deploy steps actually performed are transcribed verbatim into the release record (no cleanup-decorated narrative).
5. **Smoke checks** (immediately post-deploy, owner runs, agent records): health endpoints; one representative tenant-isolation check (CRM-TD-06); representative write-flow per touched module; check written into release record with pass/fail **as actually observed**.
6. **Failure/rollback decision point**: failure or incident → rollback code image to previous, schema per forward-fix rule (never reverse-migrate ad-hoc); incident becomes `ISSUE-<nn>` (B4); a post-mortem sub-record attached to the release record.
7. **Post-deployment verification window**: within 24-48h (owner selects ticking scheme): monitor scheduled-job health (CRM-TD-07 §5 job set), integration-call health (dialer/commercial/fetchers), plus any explicit V&V follow-up; record observed state in release-record addendum.
8. **Close/release record** (agent finalizes; owner signs): record gets final `APPROVED`/`REJECTED` stamp signifying owner declaration; releases feed B9 metric candidates.

## 5. Outputs, records, storage

| Record | Identification | Storage (proposed) | Status |
|---|---|---|---|
| Release record | `REL-<YYYYQn>.<seq>` file: build identifiers, migrations applied, smoke results, operator notes, sign-off, addenda | `docs/process/records/releases/` | NOT AVAILABLE — none exist; first one for **next actual deployment** |
| Post-deploy verification | part of release record | same | NOT AVAILABLE (per D-09 no executed deployment documented) |
| Historical release/runbook records | — | — | NOT AVAILABLE (never existed) |

## 6. Review and approval requirements (proposed)

Release is owner-approved at: (a) readiness signing-off (step 1), (b) failure/rollback decision (step 6), (c) final release-record signing (step 8). Agent role restricted to preparation and transcription — never executes deploy.

## 7. Exception and escalation

- Incident mid-deploy: stop, rollback per step 6, notify owner; external integration breakage (dialer/commercial vendor): operational pause and B4 entry, do not hack around within release path.
- Dispute over release readiness: release postponed by owner decision (no argument-based overrides).

## 8. Related documents and current evidence status

CRM-TD-02 §7 (verified deploy configuration), CRM-TD-04 §7 (migration discipline), CRM-TD-06 (isolation checks), CRM-TD-09 (R-09/R-11 deploy/backup risks), CRM-TD-11 (secret handling rules); B4, B6, B7, B9. Evidence: config EXISTS; deploy/release records NOT AVAILABLE (unavailable-state stated, not fabricated); backup precedent file EXISTS in working tree (ignored; provenance NOT VERIFIED).

## 9. Approval block

| Role | Name | Date | Decision |
|---|---|---|---|
| Owner (adopts procedure) | _TBD_ | _TBD_ | _NOT SIGNED_ |
