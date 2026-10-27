# B9 — Measurement, Monitoring, Control and Causal Improvement

| | |
|---|---|
| **Doc ID** | CRM-WSB-B9 |
| **Version / Status** | 0.2 — PROPOSED — NOT APPROVED (activation draft; supersedes 0.1 skeleton from Phase 1.6) |
| **Prepared** | 2026-10-26 (Phase 1.7) |
| **Proposed owner role** | OWNER TO BE ASSIGNED (proposal: owner reviews measures monthly; agent extracts data) |
| **Revision history** | 0.1 skeleton 2026-10-26 · 0.2 activation draft 2026-10-26 (Gate B) |
| **Approval** | PROPOSED — NOT APPROVED — adoption at first Workstream B review meeting |

## 1. Purpose and scope

Run a small, honest measurement plan over the activated Workstream B processes: what is measured, from which data source, how validated, and what actions thresholds trigger. Works with — but stays separate from — the Six Sigma readiness inventory (`docs/process/QUALITY_METRICS_READYNESS.md`). **No metric is calculated unless the underlying data supports it.**

## 2. Historical practice vs proposed process

- Historical (evidence-class): point-in-time verifications only (test artifacts 2026-09-01→2026-10-23, baseline counts 2026-10-26 verified CRM-TD-08 §1, original audit-reports). No regular metric collection, no improvement backlog log exists.
- Proposed: start collecting **only** metrics whose data the team already produces naturally (list below); anything else stays in the Six Sigma readiness inventory as future data-need, not reported as current.

## 3. Trigger and required inputs

- Triggers: monthly review (proposed cadence, owner-adjustable); B3 iteration close; B4 entry closure; B8 release-record signing.
- Inputs: B7 change register, B6 execution records, B0 register, B3 backlog/status rows, B4 register, B8 release records.

## 4. Procedure (proposed)

1. **Data extraction** (responsible: agent, automated where possible): pull current values for the metric set below from the records; every value carries source-reference + collection date.
2. **Data validation** (agent cross-checks): value traceable to a real record; if not, value is `NOT COLLECTABLE` — never estimated.
3. **Control threshold check (decision point)** (owner): compare against thresholds (agent proposes thresholds only after 2+ collection points exist; a threshold without collected history is `NOT SET` — invented thresholds are forbidden until data exists).
4. **Action trigger** (owner decision): threshold breach or validated signal → corrective action entry (B4 issue or B3 item), improvement proposal logged.
5. **Causal analysis** (where escape found — defect found by B8/B6 in a stage that should have caught it): record root-cause note; output feeds the improvement backlog (step 6).
6. **Improvement backlog** (agent maintains): `IMPROV-<nn>` proposals → owner adoption decision → adoption implemented as a process change to the owning B-doc (its revision history row updated) or resources/automation item (e.g., introducing CI, coverage tooling — real candidates already logged in CRM-TD-08 §6).
7. **Reporting** (agent): 3-line summary in the records folder: this-period counts, validation state, actions; NOT a dashboard, deliberately minimal.

## 5. Proposed measurement plan (start-state)

| Metric ID | Definition | Data source | Frequency | Owner | Validation | Threshold basis |
|---|---|---|---|---|---|---|
| MEAS-01 | Change records completed with verification evidence / total CHG closed | B7 register | monthly | OWNER TO BE ASSIGNED | per-row check vs B6 record IDs | NOT SET until ≥2 periods |
| MEAS-02 | Chaos-gate: count open ISSUE/RISK entries beyond agreed due date | B4 register | monthly | OWNER TO BE ASSIGNED | register date fields | NOT SET |
| MEAS-03 | Requirement (new work) with ≥1 verification evidence / total TRACE-closed REQs | B2 trace matrix | at each iteration close | OWNER TO BE ASSIGNED | matrix vs B6 | NOT SET |
| MEAS-04 | Test execution records produced vs plan rows (B6 plans) | B6 records | per iteration + monthly | OWNER TO BE ASSIGNED | record presence | NOT SET |
| MEAS-05 | Release-record completeness (settings: signing, smoke results, addenda) for every executed release | B8 records | per release + monthly | OWNER TO BE ASSIGNED | record fields | NOT SET |
| MEAS-06 | Doc-change churn: count of doc revisions/quality corrections per period | CRM-TD-13 CHANGELOG + doc version rows | monthly | OWNER TO BE ASSIGNED | changelog rows | NOT SET |

## 6. Outputs, records, storage

| Record | Identification | Storage (proposed) | Status |
|---|---|---|---|
| Monthly measure summary | `MEAS-SUMMARY-<YYYYMM>` file | `docs/process/records/measurements/` | NOT AVAILABLE (first period after activation) |
| Improvement backlog | `IMPROV-<nn>` rows | `docs/process/records/b9-improvement-backlog.md` | NOT AVAILABLE (seeding candidates: CI, coverage tooling, testcontainers/full-MQ tests — per CRM-TD-08 §6) |
| Causal-analysis notes | attached to B4 issue/escape records | same | NOT AVAILABLE |

## 7. Review and approval requirements (proposed)

Owner reviews summaries; metric definitions/threshold changes go through the improvement-backlog decision (step 6-adopted). Introduction of a new metric = owner approval + data-source proof before collection starts.

## 8. Exception and escalation

Data source lost/unreliable → metric continues as `NOT COLLECTABLE` flagged; falsification discovered (value without record) → B4 process issue immediately; owner may pause any metric (entry in the summary noting pause + reason).

## 9. Related documents and current evidence status

CRM-TD-08 §6 (real known gaps — improvement candidates), CRM-TD-10/13; B0, B2–B8; QUALITY_METRICS_READYNESS. Evidence: point-in-time artifacts EXIST; regular metric history NOT AVAILABLE.

## 10. Approval block

| Role | Name | Date | Decision |
|---|---|---|---|
| Owner (adopts procedure) | _TBD_ | _TBD_ | _NOT SIGNED_ |
