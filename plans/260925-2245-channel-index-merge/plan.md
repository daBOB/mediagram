# Channel index merge (tech-debt #12)

Status: decisions made 2026-09-25; implementing. Source:
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

1. Extract `download_channel_index(tg, scratch) -> Option<PathBuf>` from `index_guard.rs`
   (shared by the guard and the merge).
2. `index/merge.rs`: `merge_from` plus tests. Cover:
   - a channel-only set with its parts and assets;
   - a shared set where local wins;
   - a v7 channel index without popularity;
   - a pending channel set, which is skipped;
   - `shows` NULL-fill;
   - `meta` left untouched;
   - running it twice changes nothing;
   - foreign keys hold.
3. The removal rule (decision 2), behind a closure so it can be tested.
4. `pull-index` and `push-index --merge`. Docs; the version bump is minor.
5. A live run: `pull-index --dry-run` on both machines, then a real pull on one, `push`,
   and verify the web player serves the union (1164 + 21 expected).

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
