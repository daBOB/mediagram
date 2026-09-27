# Code review: set lifecycle (candidate C)

Branch `refactor/set-lifecycle` (worktree `../mediagram-channel-index`), base d22e8a75.
Decisions (user, 2026-09-27): Q1 lifecycle only; Q2 `index::lifecycle` with
record_source/source_of/complete (one transaction)/forget; Q3 failed temp delete logged;
Q4 no migration. Reviewer: code-reviewer agent.

Verdict: no Critical/High. Transaction covers complete + both key deletes; `run_set`
returns true only after commit; stored values and key spellings unchanged; no other
code spells the keys.

## Findings and disposition

| # | Finding | Disposition |
|---|---------|-------------|
| M1 | No test pins the literal `source:`/`tmp:` spellings existing indexes resume through | **Fixed**: raw-key test in `tests/index_lifecycle.rs` |
| M2 | `rescan` completes sets outside lifecycle, leaving source/tmp keys (stale paths in snapshots); calling `complete` there would nest transactions | **Fixed**: rescan calls `lifecycle::forget` inside its batch transaction; test; mutation-checked |
| L1 | Comment claimed the remux lives in the temp dir; default is beside the original | **Fixed**: comment + warning says delete by hand |
| L2 | Crash between completion commit and `owe_publish` leaves a set complete, unowed | **Fixed**: `complete` owes the publish in the same transaction; session no longer owes separately; test |
| L3 | `complete` on a set removed mid-upload matches 0 rows, reports "added", may delete the original | **Fixed**: `ensure!(changes == 1)`; test; mutation-checked |
| L4 | Leftover bare block in pipeline | **Fixed** |
| L5 | `finish_from` is a pass-through | **Fixed**: removed; session calls `run_set` |
| L6 | Temp deletion by `run_set` untested | **Fixed**: test in `tests/upload_pipeline.rs` |
| L7 | Test files over 200 lines | Pre-existing; tests are exempt |
| L8 | Confusing fixture path | **Fixed** |

Version: 0.68.3 (0.68.2 is the web series-preload fix on its own branch).

## Unresolved questions
- None.
