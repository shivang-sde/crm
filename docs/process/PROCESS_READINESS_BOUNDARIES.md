# Process / CMMI / Six Sigma Readiness Boundaries

| | |
|---|---|
| **Doc ID** | CRM-WSB-BND |
| **Version / Status** | 0.1 — PROPOSED — NOT APPROVED |
| **Prepared** | 2026-10-26 (Phase 1.8, Task 5) |

Non-negotiable interpretation boundaries for everything in `docs\technical\` and `docs\process\`:

1. **Documents are not adoption.** Existing process docs (B0–B9, DEC register, packs, templates) describe proposed workflows. They are **not proof** that processes have been adopted, and adoption cannot be inferred from their existence. Adoption happens only when authorized people record the decision (DEC register + minutes).
2. **No reconstruction of history.** Historical evidence must come from dated artifacts (reports, git state, test outputs). Missing records (CI — none exists, no release records, no defect/measurement history, no design-review trail, no scope approvals) are documented as `NOT AVAILABLE` and remain visible gaps; they must never be filled from assumptions or analogy.
3. **CMMI claims are blocked pending decisions.** The model/version (DEC-01) and scope (DEC-02) must be confirmed by management before any model-specific compliance or readiness statement is made. Until then, documents may only reference CMMI generically as "orientation model, undecided". No formal compliance, certification, or appraisal-readiness claim may appear in any document or communication.
4. **Six Sigma metrics require real operational data.** Any future DPMO, sigma level, defect rate, or capability figure requires an explicit defect definition, a unit model, a collection window, and collected data. The SSM inventory lists data needs; no such figure may be produced from estimates. Formal program vs informal DMAIC remains DEC-06.
5. **Gaps stay visible.** Missing CI, release, defect, and measurement records remain listed in CRM-TD-08 §6 / CRM-TD-09 / B0 EVD rows until actually addressed with verified artifacts. Closing a gap requires evidence, not intent.
6. **Evidence states are explicit.** Documents may be in exactly one of: PROPOSED → REVIEWED → ADOPTED/EFFECTIVE → RETIRED (with dates + authorizing decision reference). Phase 1.8 leaves everything in PROPOSED; nothing has advanced state because no authorized decision has been supplied.
