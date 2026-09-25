# Channel index merge (tech-debt #12)

Status: phases 1-4 implemented 2026-09-25 (merge, pull-index, push-index --merge; all
tests and clippy clean; one dry run verified read-only). Phase 5 (the real pull, second
machine, and push) not run. Source:
`plans/reports/tech-debt-260925-2230-mediagram-web-and-pipeline-report.md` #12, research
brief of 2026-09-25 (this session).

## Why

Two machines publish `library.db` to one channel, and a push replaces it wholesale. The
guard (`telegram/index_guard.rs`, `7a72d99`) now refuses a push that would drop sets, but
refusing is not publishing. On 2026-09-25 this machine lacked 265 channel sets and the
uploader lacked 21 of this machine's, so **neither** could push the full library. A merge
brings the channel's rows into the local index first, so either machine can publish
everything.

## Facts it rests on (verified by research, with file refs)

- `set_id` is a fresh ULID per upload (`upload/prepare_set.rs:77`) and is globally unique.
  `parts.chat_id/message_id` point into the shared channel, so they are valid on both
  machines.
- Tables: `sets` (no updated_at), `parts` (FK, cascade), `assets` (FK), `shows` (no link
  to sets, v7/v8 columns optional), `meta` (**machine-local only**: pins, `last_push_at`,
  `source:`/`tmp:` keys, schema_version).
- `edit` rewrites captions and then the local row; the other machine stays stale until it
  rescans. `remove` is a hard delete, with **no tombstone**.
- `index_guard.rs` already downloads the channel's newest index; `index/rescan.rs`
  `insert_set_if_new` is the existing insert-if-missing precedent.

## Design

`merge_from(local, channel)`, pure and in one transaction, via `ATTACH` on the
`db::open` connection:

| Table | Rule |
|---|---|
| `sets` | Insert channel-only sets whose `status = 'complete'`. Shared set_id: **local wins**, and differences are reported. |
| `parts` | All rows of the added sets. |
| `assets` | Rows of added sets; for shared sets, only missing `(kind, lang)`. |
| `shows` | Insert missing keys. On shared keys, fill local NULLs (`COALESCE`), column by column, only for columns both sides have. |
| `meta` | Never touched. |

Commands:
- `mediagram pull-index [--dry-run]`: download, back up to
  `library.before-channel-merge-<date>.db`, merge, then print what was added and any
  conflicts.
- `mediagram push-index --merge`: pull then push. With the merge done, the guard passes.

## Phases

1. [x] Extract `download_channel_index(tg, scratch) -> Option<PathBuf>` from
   `index_guard.rs` (`telegram/download_index.rs`; the guard now calls it too).
2. [x] `index/merge.rs`: `merge_from` plus tests (`index/merge_tests.rs`, 10 cases). Covers:
   - a channel-only set with its parts and assets;
   - a shared set with differing metadata, reported as a conflict and not overwritten
     (the "local wins" fallback from the design table above was superseded by the
     2026-09-25 decision below — merge_from picks neither side, and leaves the caption
     re-read to `merge_conflicts::resolve_from_captions`, also tested);
   - a v7 channel index without popularity;
   - a pending channel set, which is skipped without reaching the removal check;
   - `shows` NULL-fill (and that an already-filled value is never replaced);
   - `meta` left untouched;
   - running it twice changes nothing;
   - foreign keys hold (`pragma_foreign_key_check`).
3. [x] The removal rule (decision 2): `merge_from` takes a `keep_if_live` closure,
   called once with every channel-only complete candidate; `commands/pull_index/keep_live.rs`
   backs it with one batched Telegram lookup, tests back it with a fixed answer.
4. [x] `pull-index [--dry-run]` and `push-index --merge` (`commands/pull_index/`,
   `commands/push_index.rs`). `--dry-run` runs the merge against a throwaway copy of
   the local index so nothing is written; a real run backs up first via
   `library.before-channel-merge-<YYMMDD-HHMM>.db` (`clock::backup_timestamp`). Docs
   and the version bump are a separate phase (own file ownership); not done here.
5. [ ] A live run: `pull-index --dry-run` on both machines, then a real pull on one,
   `push`, and verify the web player serves the union. One dry run done this session
   (269 sets would be added, 272 shows added, 0 conflicts) — read-only, confirmed
   `library.db`'s mtime unchanged and no scratch files left behind. The real pull,
   the second machine's dry run, and the push are left to the lead with the user.

## Decisions for the user

1. **Edit conflicts** (a set on both sides whose metadata differs): local wins
   (recommended; they are reported), channel wins, or re-read that set's captions (the
   captions are the truth)?
2. **Removed sets.** A set removed on one machine is still in the other's index, and a
   merge would bring it back. Options:
   - (a) Before adding a channel-only set, check its part messages still exist in
     Telegram (recommended; no schema change).
   - (b) A local `removed:` marker in `meta`.
   - (c) A synced tombstones table (schema v9).
3. **Should `push-index` merge by default** instead of refusing? (Recommended: keep
   refusing, and offer `--merge`, so publishing never silently changes the local index.)

## Risks

- A wrong merge corrupts the index every player reads. Mitigations: the backup file before
  every merge, `--dry-run`, one transaction, and the idempotence test.
- Resurrecting removed titles; see decision 2.

## Decisions (user, 2026-09-25)

- **Edit conflicts: re-read captions.** For a set on both sides whose metadata differs,
  fetch its parts' captions from the channel and rebuild the row from them. The captions
  are the truth; neither index wins by default.
- **Removals: check Telegram.** A channel-only set is added only if its part messages
  still exist in the channel.
- **`push-index` keeps refusing** by default; `--merge` does pull + push. (Recommendation
  taken; not contested.)

## First use (user, 2026-09-25)

The 21 sets only this machine holds are **its own unfinished uploads** (all `pending`):
19 *30 Rock* episodes from seasons 4–7 (0 parts sent; sources present on
`/media/DBI/HiDrive`), *Ben Hur* (2/9 parts) and *Ghost in the Shell* (4/6). The user
resumes them **from this machine** once the merge exists. Uploads run from one machine at a
time, never in parallel.

Order, on this machine:
1. `mediagram pull-index --dry-run`, then `mediagram pull-index`: take in the uploader's
   265 sets, with a backup first.
2. `mediagram resume`: finishes the 21; its automatic publish now passes the guard,
   because the local index holds everything the channel does.
3. Verify the web player serves the union.

## Review (code-reviewer, 2026-09-25), fixed before first use

- **H1:** `push --merge` would have been refused by the guard for sets the merge
  rightly skipped. Fixed: the guard counts only complete channel sets, and
  `push --merge` passes the proven-removed ids through as exceptions.
- **H2:** backup names were minute-precision and overwritable. Fixed: the name also
  carries the pid, and an existing file is never replaced.
- **M1:** captions are re-read in part order. **M2:** the report prints before the
  re-read, and one set's fetch failure is a warning. **M3:** fails closed, so a set is
  live only with a message for every part, all in this channel (also L2).
  **M4:** show text is filled only from the same `lang`. **L1:** a failed commit
  rolls back.
- **Skipped, with reasons:**
  - L3 (column quoting): names come only from `main`'s own schema, intersected
    with the channel's.
  - L4: the dry run opening the real db migrates it, as every command does.
  - L5: a snapshot older than v6 fails closed, with a rollback.
  - L6: backup pruning.
  - L7: parts are not reconciled for shared sets.
- Live dry run after the fixes: would add 271 sets and 274 shows; `library.db`
  untouched; no leftovers.
