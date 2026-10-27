# Workstream B Activation Plan

| | |
|---|---|
| **Doc ID** | CRM-WSB-ACT |
| **Version / Status** | 0.1 — PROPOSED — NOT APPROVED (draft for management review) |
| **Prepared** | 2026-10-26 (Phase 1.7, Gate C) |
| **Basis** | B0–B9 activation drafts (docs\process\), verified evidence state in `EVIDENCE_AND_DOCUMENT_REGISTER.md` |

## 1. Recommended activation order (low-dependency first)

| Order | Process | Why this position | Hard dependencies |
|---|---|---|---|
| 1 | B0 register | nothing else records evidence coherently without it | owner role assignment only |
| 2 | B2 register (forward-only) | traceability pays if it starts at the next requirement | owner confirmation of REQ intake |
| 3 | B6 execution records | next natural test run produces records immediately | test environment (existing infra, already understood) |
| 4 | B4 register | security items from CRM-TD-11 need a home as soon as decisions start | owner role |
| 5 | B8 release records | retrofits nothing; activates at the next actual deployment | deployment occasion (next real release) |
| 6 | B7 change register | starts with the next deliberate change batch | owner adoption |
| 7 | B3 planning | planning benefits from REQ intake being steady-state | REQ flow exists |
| 8 | B5 design notes | activated with next non-trivial design decision | reviewer role |
| 9 | B9 measurement | metrics meaningful only after ≥1 period of records exists | B2/B6/B7/B8 records |

## 2. Decisions required from management

| Decision | Blocks | Reference |
|---|---|---|
| Adopt (or amend/reject) each B0–B9 document as the team process | all activation | each doc §approval |
| Assign process owner roles (B1–B9 owner, B5 reviewer) | all | each doc header |
| CMMI model/version + appraisal scope + Lead Appraiser ( undecided ) | tailoring language, formalization depth | CRM-TD-12 |
| Six Sigma: formal program/belts vs informal DMAIC practice | SSM doc direction | SSM §2 |
| `docs/pashucare credential.pdf` disposition (deletion-commit path, history rewrite, Secret-scanning) | security closure | CRM-TD-11 §6 |
| Local security artifacts (`EVD-13`) disposition + `frontend\.env.test` content review (H-06) | hygiene closure | B0 EVD-13/17 |

## 3. Roles that must be assigned

- **Process owner** (single person acceptable initially): decision authority for all B-doc decision points.
- **Secondary reviewer** (B5 design stamp, B7 emergency attest) — may be owner initially.
- **Release operator** (B8): must be a human other than documentation-agent.
- **Agent roles already ongoing**: record keeping, data extraction, doc maintenance (per B-doc responsibilities sections).

## 4. Records the team can begin capturing immediately (no blockers)

1. B0 register rows (already populated to current state — future rows as new evidence appears).
2. B2 requirement register rows (`REQ-`) for new incoming requirements (template in B2 §5).
3. B6 execution records (`TEC-`) for any test run that happens from activation onward.
4. B4 risk/issue entries (`RISK-`/`ISSUE-`) for newly identified findings (starting with CRM-TD-09/11 disposition).
5. B7 change-register rows (`CHG-`) for the next deliberate change batch.

## 5. Records that depend on infrastructure/management decisions

- B8 release records → wait for next actual deployment; blocked on deploy-approval authority + D-09 baseline (no historical reconstruction).
- B9 metrics → blocked until ≥1 period of B2/B4/B6/B7/B8 records exists; thresholds deliberately `NOT SET`.
- B1 baseline approval → blocked on owner sign-off decision mechanism chosen by management.
- Security remediation actions → blocked on management decisions (§2 last three rows).

## 6. First review meeting agenda (proposed)

1. Walk through B0 evidence register; confirm every `VERIFIED` row is true per participant knowledge.
2. Decide adoption per B-doc (or amendments; record in revision history of each).
3. Assign owner roles + B5 reviewer.
4. Walk CRM-TD-09 R-01…R-13 + CRM-TD-11 findings → classify into B4 (probability/impact/mitigation decision each).
5. Set the CMMI and Six Sigma decisions in motion (who, when, model/scope questions list from CRM-TD-12).
6. Security decisions (PDF disposition, EVD-13 artifacts, H-06 env.test review).
7. Verify the "already captured from now" list (§4) enters use with the next work item.

## 7. Entry and exit criteria (per process, proposed)

| Process | Entry criterion | Exit criterion (per cycle/iteration) |
|---|---|---|
| B1 | scope-snapshot trigger fired or quarterly due | snapshot approved + baseline-change log current |
| B2 | new requirement or trace review owed | every open REQ has owner decision; matrix rows complete for touched work |
| B3 | iteration start or plan deviation | all items terminal or carried with variance row |
| B4 | finding/issue/uncertainty raised | every entry has owner + mitigation decision; cadence review done |
| B5 | design decision needed or change requires DES | design note reviewed for affected CHG (or trivial fast-path noted) |
| B6 | CHG verified gate or iteration gate due | execution records exist for the gate; failures logged (ISSUE) |
| B7 | change batch occurred or CR raised | CHG rows carry commit + verification links; no orphan changes |
| B8 | release candidate exists or deployment executed | release record signed (deploy executed and recorded) or readiness formally withheld |
| B9 | month-end, or period data available | summary written; validation pass done; actions assigned |
| B0 | new evidence or record appears | register row added/updated |

## 8. 30-day rollout plan (PROPOSED — NOT APPROVED)

- **Week 1** — decision meeting (§6 agenda); role assignment; B0/B2/B4/B6/B7 registers live; first REQ entries and first CHG row for the next deliberate change batch (chosen by owner).
- **Week 2** — first B4 cadence review (disposition of CRM-TD-09/11 entries); B3 backlog seeded from confirmed REQs; doc corrections freeze rule reviewed.
- **Week 3** — B1 snapshot drafted from CRM-TD-03 + approved per mechanism chosen; B5 design-note flow run once on a real pending decision; measurement definitions (MEAS set) confirmed.
- **Week 4** — B9 records first extraction (**counts only**, thresholds still `NOT SET`); CMMI/Six Sigma decisions returned from management and recorded; hygiene decision list advanced (PDF, EVD-13, H-06); retrospective note for B-doc amendments, if any.

Exits week 4 if: all B-docs adopted or amendment-list exists; owner roles assigned; ≥1 capture of everything in §4 recorded.
