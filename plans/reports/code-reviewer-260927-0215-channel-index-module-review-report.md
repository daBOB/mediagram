# Code review: channel_index module (candidate B)

Branch `refactor/channel-index` (worktree `../mediagram-channel-index`), uncommitted.
Plan: `plans/260927-0146-channel-index-module/`. Reviewer: code-reviewer agent, 2026-09-27.

Verdict: no Critical/High. Old behaviour preserved (dry run, backups, conflict re-read,
live-set check, unpin read-back, flood-wait-only send, per-process temp names). No
deadlock (upload lock → publish lock only).

## Findings and disposition

| # | Finding | Disposition |
|---|---------|-------------|
| M1 | Uploader read only pins; core + web also search `#mlib-index` (50) to catch snapshots an interrupted publish left unpinned. Docs claimed parity. | **Fixed.** `ChannelRemote::candidates()` = pins (100) + marker search (50, allowed to fail), as core. Test `a_snapshot_left_unpinned_is_still_pulled`. Spec §7, `CONTEXT.md`, arch doc, changelog updated. |
| M2 | Pulled-id shortcut safe but its preconditions untested. | **Fixed.** Tests: dry run records nothing; failed pull records nothing; two publishes on one machine take turns (fake yields so they interleave). Each mutation-checked. |
| M3 | Unresolved conflicts no longer retried before publish. | **Fixed.** Pulled id recorded only when every conflict resolved. |
| L1 | `ATTEMPTS` doc off by one. | Fixed wording. |
| L2 | `publish.lock` missing from on-disk table. | Fixed. |
| L3 | `pull-index --dry-run` printed counts only (old `--check` named ids). | Fixed: names first 5 added sets. |
| L4 | Lock taken after connecting; waiter idles on a connection. | Skipped: harmless. |
| L5 | Pulled id has no chat id; re-pointing one data dir at another channel could skip a pull. | Skipped: old code was already wrong there; revisit if multi-channel data dirs become a thing. |
| L6 | `CONTEXT.md` + CLAUDE.md versioning rule live uncommitted on `main`. | Must land with the branch; flagged to user. |
| L7 | Nits (long comment line, pub `TelegramRemote`). | Line fixed; `TelegramRemote` stays pub for candidate A (session passes its own connection). |

Also split: `telegram_remote.rs` hit 202 lines; command entry points moved to `mod.rs`.

Final: `cargo test --workspace` 1334 passed / 0 failed; `cargo clippy --workspace
--all-targets -D warnings` clean; fmt clean for touched files (main has pre-existing drift).

## Unresolved questions
- None blocking. L5 deferred by choice.
