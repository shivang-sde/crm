# Security Hygiene Review (Phase 1.5)

| | |
|---|---|
| **Doc ID** | CRM-TD-11 |
| **Version / Status** | 0.2 — DRAFT findings file; contains **no secret values**; requires authorized human decision before any remediation. §6 records owner-provided clarification (2026-10-26). |
| **Prepared** | 2026-10-26 |
| **Method** | `git ls-files` / `git status` / `git check-ignore -v` / `git log --all --diff-filter` / `git ls-tree origin/main` / `git merge-base --is-ancestor` / `git for-each-ref`; public-repo visibility confirmed by fetching `https://github.com/shivang-sde/crm` (read-only, no API auth used). Nothing deleted, modified, rotated, or staged. |

## 1. Confirmed facts

- Remote `origin = https://github.com/shivang-sde/crm.git` and the repo is **PUBLIC** (page renders logged-out; "Public" badge, 0 forks/stars/watchers).
- Local branch `main` is fully synced with `origin/main` (0 unpushed, 0 behind) — **anything in main's history is already public**.

## 2. Path classifications

| Path | Exists | Tracked now | Ignore rule | Git history | Risk | Notes |
|---|---|---|---|---|---|---|
| `.env.production` | Yes | No (untracked) | `.gitignore:28` `*.env.*` — **effective** | Never committed (0 commits) | OK as stored; uncommitted risk of manual override only | Contains real production values per Phase 0 audit; do not move into repo |
| `prod_backup.dump` | Yes | No | `.gitignore:45` `*.dump` — **effective** | Never committed | OK locally; 456 KB production DB dump on developer workstation | Recommend moving off workspace to access-controlled backup store |
| `backend/e2e-commercial-key.txt` | Yes | No | **None** (untracked, unprotected) | **Tracked & added in commit `0402f89` (2026-10-14)** | **HIGH (local)** | Blob lives only in local agent-checkpoint ref `refs/agents/5c99955…/checkpoints/turn/0` — **NOT** ancestor of `main`/`origin/main` (verified via `merge-base --is-ancestor` → not ancestor). Not on GitHub today, but locally referenced |
| `backend/e2e-commercial-key-b.txt` | Yes | No | None | Same as above (commit `0402f89`) | **HIGH (local)** | Same blob location |
| `backend/rbac10-tokens.json` | Yes | No | None | Same (`0402f89`) | **HIGH (local)** | Auth tokens residue |
| `backend/wf58_token.txt` | Yes | No | None | Same (`0402f89`) | **HIGH (local)** | Auth tokens residue |
| `backend/tokens_test.txt` | Yes | No | None | Same (`0402f89`) | **HIGH (local)** | Token residue |
| `backend/e2e-commercial-results.json` / `e2e-commercial-test.js` | Yes | No | None | Same (`0402f89`) | MEDIUM (local) | Session-test output likely containing key references |
| `docs/pashucare credential.pdf` | — (deleted in working tree by owner; see §6) | **Yes — still tracked in index/HEAD** | None | 2 commits; **present in pushed `origin/main` tree** | **Exposure existed (confirmed by Git metadata); actual credential compromise unconfirmed (see §6.3)** | Contents were never inspected by this audit (no value printed). Owner reports mock credentials — see §6 |
| `frontend/.env.test` | Yes | **Yes — tracked & pushed** | None (frontend `.gitignore` has no env rule) | tracked in history | MEDIUM | Test environment file in public repo; contents NOT inspected (values not printed). Human review needed |

## 3. Root causes (observed, not attributed)

1. No ignore rules for scratch/test `*.txt`, `*.json` token files in `backend\` — untracked residue accumulates and can be swept into future commits (one commit, `0402f89`, an agent "baseline checkpoint" from a local ref, already did exactly this once).
2. `frontend\.gitignore` covers build/test outputs but not `.env*` (root `.gitignore` does, frontend does not).
3. No pre-commit secret scanning (no gitleaks/secret-push-protection evidence).

## 4. Recommended next steps (authorized human action required — NOT executed here)

| # | Action | Notes |
|---|---|---|
| H-01 | **Replaced by §6.4 mitigation decision**: owner states contents were mock. Decide whether (a) the deletion is staged + committed as a normal change (metadata-only removal going forward) and (b) whether any history remediation (rewrite/force-push) is even warranted for mock data | History rewrite affects all clones and requires repo-owner execution with team coordination; per audit constraints no history change was made |
| H-02 | Delete the now-untracked local artifacts (`e2e-commercial-key*.txt`, `rbac10-tokens.json`, `wf58_token.txt`, `tokens_test.txt`, `e2e-commercial-results.json`) **after** business value check, and rotate any live key they contained | The blobs still reachable via `refs/agents/...` local checkpoint ref; cleanup GC optional after confirm |
| H-03 | Add ignore rules: `backend/**/*.txt` token patterns or dedicated `*.local.json`, plus `frontend/.env*` (like root rules) | `.gitignore` edit = config change; not done in this phase |
| H-04 | Optionally drop the local `refs/agents/...` checkpoint ref + `git gc` once H-02 confirmed | Removes any historic blob reference from local clone |
| H-05 | Enable GitHub secret scanning/push protection on the repo (Settings → Code security) | Free for public repos; purely a repo-settings action by the owner |
| H-06 | Review `frontend/.env.test` for real credentials (human, content inspection outside audit) | If it contains real secrets, treat like H-01 |
| H-07 | Decide whether the repo should be public at all (product code + business narrative in `readme.md`) | Management decision |

## 5. What was NOT done

No file deleted, no `.gitignore` changed, no history rewritten, no secrets printed or copied, nothing staged/committed, no credential rotated. All findings persist as-is pending decisions above.

## 6. Update after owner clarification (2026-10-26, post-review)

### 6.1 Owner-provided statement (recorded as a clarification, not independently verified evidence)

The owner states that `docs/pashucare credential.pdf` contained **mock credentials** and has **moved/deleted the file**. This audit never opened the PDF (per no-content-printing rules), so the mock-only claim is **owner-reported and cannot be independently confirmed from this clone** without inspecting contents (which remains a restricted action unless separately authorized). It is recorded here as *owner-provided clarification*, classified distinctly from Git-metadata facts in 6.2.

### 6.2 Independently verified facts (Git metadata only; no contents printed)

| Verification point | Command basis | Result |
|---|---|---|
| Working-tree presence | `Test-Path` / `git status --porcelain -- <path>` | **Deleted from working tree** by owner (` D` — unstaged deletion; file absent on disk) |
| Tracking status (index) | `git ls-files -- <path>` | **Still tracked in the index** — the deletion has not been staged/committed (and will not be, per this audit's constraints) |
| Current remote tree | `git ls-tree origin/main -- docs/` | **Still present in pushed `origin/main` tree** |
| History | `git log`; `git log --diff-filter=D` | Added in commit `e548930` (“feat: core modules lead, contact account, deal and user + rbac completed”); **no deletion commit exists in the pushed lineage** → the blob remains in reachable public history of `origin/main` |
| Remote head | `git ls-remote origin main` | `origin/main` = `0fb9361` (same as local main; **remote not rewound/altered**) |

Note: `git log --all` also lists the agent-checkpoint commit `0402f89` touching this path, via the local ref `refs/agents/…` (not part of `main`).

### 6.3 Credential-compromise assessment

- **Confirmed: historical public exposure of the PDF** (it was, and remains, committed and pushed to a public repository until removal is committed).
- **Owner-reported: contents were mock/fictional.**
- **Actual credential compromise: NOT established.** The audit has no basis to treat any value as real; by the same limitation, the audit cannot fully rule out that some value in the PDF matches or was reused as a real credential (e.g., account/demo credentials re-used by a human), because contents were never inspected.

### 6.4 Conditional next steps (do not treat fictional values as real credentials)

1. **No rotation recommendation is issued.** Rotation is warranted **only if** an authorized person (with content-inspection rights) identifies any value in the PDF that matches or was reused as a real system credential. If such a match exists, treat it as disclosed and rotate immediately; if all values are confirmed fictional and non-reused, no rotation is needed.
2. **Removal going forward:** the deletion is present in the working tree but uncommitted. Committing the deletion (a normal `git rm`-style commit by the owner) removes it from the working/index state and from the tree of *future* commits; it does **not** remove it from history. The remote tree will shed the file on the next push that includes the commit.
3. **History state:** history rewrite/force-push is explicitly **not performed and not recommended as a default action** for mock data; if the owner nonetheless opts for history scrubbing, it must be coordinated with all system stakeholders and clones of the repo (affects every published hash).
4. **Harm-limitation synthesis:** conditional on the owner's mock-only statement being true, residual risk drops from *credential exposure* to *repository hygiene/information-disclosure noise*; this should be re-evaluated only if the content-inspection in step 1 finds a real-match value.

### 6.5 Post-clarification risk classification (change from v0.1)

- v0.1 classification: CRITICAL — public credential document.
- v0.2 classification: **MEDIUM (conditional)** — confirmed historical public exposure; contents owner-reported as mock; compromise unconfirmed; pending owner content-inspection verdict (real-match value present ⇒ escalate to HIGH compromised-credential handling).
### 6.7 Phase 1.8 status re-check (2026-10-26)

Individual facts stay distinct: (a) PDF present in pushed `origin/main` + reachable history (verified this phase via `git ls-tree origin/main` = 1 match); (b) still tracked in index (`git ls-files` = 1); (c) working-tree file absent (owner's unstaged deletion persists); (d) contents described as mock **by owner statement only** - never independently inspected. **No status change occurred in Phase 1.8**; dispositions remain open as DEC-07/08/09 in `docs\process\MANAGEMENT_DECISION_REGISTER.md`. The issue is **not** closed: closure requires the owner-approved disposition to be documented AND the applicable repository state verified afterwards. Local credential artifacts (all still present on disk, referenced by path only: `e2e-commercial-key*.txt`, `rbac10-tokens.json`, `wf58_token.txt`, `tokens_test.txt`) and the `frontend\.env.test` content review (tracked and pushed, contents never inspected) are tracked separately under the same rule. No contents were printed or copied in any phase.