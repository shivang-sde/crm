# Six Sigma / Quality Metrics Readiness Inventory

| | |
|---|---|
| **Doc ID** | CRM-WSB-SSM |
| **Version / Status** | 0.1 — DRAFT, NOT APPROVED · Re-checked 2026-10-26 (Phase 1.7 Gate A): no measurable degradation; data-availability statements unchanged; no figures added. Unresolved decision remains: formal Six Sigma program/belts vs informal DMAIC-based quality improvement (see CRM-TD-12). |
| **Prepared** | 2026-10-26 · Owner: _TBD (management + owner)_ · Approval: _NOT APPROVED_ |
| **Revision history** | 0.1 created 2026-10-26. Kept separate from Workstream B per task #9. |

This doc inventories **what real, verifiable data exists today** as candidate inputs for Six Sigma-style improvement projects. It deliberately contains **no invented figures**: no DPMO, no sigma level, no CTQ targets — those require measured data and a defined defect definition, plus management approval of a formal Six Sigma approach (see CRM-TD-12 conclusion).

## 1. Candidate measurement domains (with actual data sources)

| Domain | Candidate metrics | Existing data source | Currently measurable? |
|---|---|---|---|
| Build/test quality | test pass rate on current build; suite runtime; flake rate | test reports (already-used infra); surefire/Playwright artifacts | PARTIAL — point-in-time artifacts only; no CI, no history |
| Requirements delivery | requirement→test trace coverage; iteration escape rate | B2 trace matrix (to build); gate reports (WF chain) | PARTIAL |
| Process stability | working-tree vs committed baseline drift; unmerged work ageing | `git status`/`ls-tree` counts (CRM-TD-08 §1 pattern) | YES (verified 2026-10-26; needs cadence) |
| Defect management | open defect ageing, fix turnaround, reopen rate | B4 register (to activate) | NOT YET |
| Release/deploy excellence | deploy record completeness, steps Actual+Variance+Improvement log | B8 release records (none exist yet) | NOT YET |
| Support quality (post-live) | ticket SLA compliance; SLA-breach rate | `TicketSlaBreachScheduler` / ticket data (module exists staged) | NOT YET (feature not committed/deployed) |

## 2. Prerequisites before any Six Sigma claim

1. Management decision: adopt Six Sigma formally vs reuse its toolset informally (DMAIC) — see CRM-TD-12 questions.
2. Define defects/outcomes per CTQ and collect baseline data over a stable period.
3. Controls in place: B6 evidence discipline, B8 release records, B9 measurement cadence.

## 3. Honest current state

- No continuous metric history, no formal defect definitions, no deployed-and-measured release line.
- Historical improvement narratives exist (WF21 chain, docs\l3.4-p4 audits) — evidence-class only.
- Any future sigma-level/DPMO statement must show: defect definition, denominator, data window, collection method. Per workstream rules, none exists in this documentation.
