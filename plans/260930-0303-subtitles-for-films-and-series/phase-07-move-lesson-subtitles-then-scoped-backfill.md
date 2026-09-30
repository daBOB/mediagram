# Phase 07 — Move the inline lesson subtitles out; film/series backfill in the measured scope

## Context links
- Matcher and dry run: phase 07a (`crates/mediagram/src/commands/subtitles/`). Writer: phase 06 (`subtitles::attach`, `index::subtitles::record`).
- `ChannelRemote::send_document` / `download` (`crates/mediagram/src/channel_index/remote.rs`, `telegram_remote.rs:82-93`).
- Inline rows: `assets` kind `subtitle`. The local index has 1,235 rows, the channel has **1,266** (en 184): 31 were written by the other machine. Every row is `tut`, one per set, begins `WEBVTT`, and the largest is 612 KB.
- The real backup is old index snapshots, which are only unpinned, never deleted (`crates/mediagram/src/channel_index/unpin.rs:17-30`). The sidecars are **not** a full copy: "Wall Street Story" (864 inline rows) has 0 `.vtt` beside its 884 `.mp4`, and `<course working folder>` holds 990 `.vtt` for 1,235 bodies (red team: security F1).
- Every index caption carries the pushing build's `schema` (`crates/mlib-spec/src/index_caption.rs:22-41`); phase 02 turns it into a refusal and a stale-uploader alarm.
- Locks: `crates/mediagram/src/upload/lock.rs:48` (`acquire`), `:118-124` (`is_held`, slot 0 only). `upload_slots` is 1 on this machine (no key in `~/.config/mediagram/config.toml`; default `crates/mediagram/src/config.rs:100-102`). The other machine is unknown.
- Memory notes: two uploaders share the channel index; release sessions need the installed uploader; resume pending uploads after merge; never pin in probes.
- Red team: security F1, failure-mode F5, scope-critic F6, assumption-destroyer F1.

## Overview
Priority P1. Effort 1d code + unattended runs. Version: next **minor** (sending mode, `move-inline`, `--redo`). Status: pending.
**Gates:**
- Phase 06 is installed on both uploaders.
- Phases 03, 04 and 05 are live, because `move-inline` deletes the inline rows the old readers use.
- The other machine is verified at schema ≥ 13 by the caption of its own push.
- The pre-move snapshot message id is recorded.

**Film/series scope (user, 2026-09-30, from 07a's counts):** extract from the *uploaded copies* through the channel — MP4 first (~2,164 sets, ≈1 min each), then MKV (~692, full reads) — paced and resumable; local sources where present. Picture-only titles (188 films) stay without subtitles. Part B below is re-planned around this (2026-09-30).

## Part A — `mediagram subtitles move-inline [--dry-run] [--no-push]` (always in scope)
- Runs on **this machine only**, after `pull-index`, so it covers all 1,266 rows including the other machine's 31.
- For every set with inline rows and no bundle:
  1. Build the bundle: track n = n-th row by `lang`; label German/English/Subtitles; `source` sidecar, `codec` vtt.
  2. `send_document`, then **read it back**: `remote.download(message_id)`, and check that the sha256 matches and the decode equals the bodies.
  3. Only then `record`, which removes the inline rows.
- A mismatch records nothing and is reported; the run stops after three.
- Pace: 2 s between sends. One publish at the end.
- Numbering follows `ORDER BY lang`, the same order the readers' inline fallback uses, so nothing a viewer remembered changes meaning.

## Part B — `mediagram subtitles backfill <FOLDER>... [--dry-run] [--accept-fallback <file>]... [--redo <set>]... [--no-push]`
- **Minimum, whatever 07a decides:** the sending mode and `--redo`. Phase 06 relies on them to heal a crash between completion and recording.
- The 07a matcher is used unchanged. A matched file goes to `subtitles::attach`.
  - Fallback matches are sent only when named with `--accept-fallback <file>`.
  - `--redo <set>` ignores an existing bundle, records the new one, then deletes the old message.
- Holds the upload lock for the whole run, and refuses to start when `upload_slots` > 1 (it holds only slot 0) until the config says 1.
- Resumable: a set whose bundle `uploaded_at` is newer than the run start is skipped. Ctrl-C finishes the current record, then stops.
- **Channel source (re-planned 2026-09-30):** `backfill --channel [--mkv] [--limit N]` takes no folders. It walks the index for complete non-`doc` sets whose facts list a `de`/`en` subtitle language and that have no bundle — MP4 sets (`container`/file name) before MKV, MKV only with `--mkv` — and extracts from the uploaded copy:
  - One process, one Telegram session: the command starts the `serve` router (`serve::routes::router` over a read-only index + `TelegramSource` from the **same** `Tg` client that sends) on `127.0.0.1:0` in a spawned task, and hands `attach` the input `http://127.0.0.1:<port>/sets/<id>/stream` (phase 06's `Input::Url`; no sidecars). Never a second client on the same key (lessons 2026-09-22).
  - ffprobe/ffmpeg read through HTTP Range: MP4 range-reads the subtitle samples (~1 min/set measured in 07a); MKV is a full read.
  - A set whose uploaded copy has no de/en text track (picture-only, none) is noted in `meta` as `subs-none:<set>` so later runs skip it; `--redo <set>` clears it.
  - Pace: 2 s between sets; `--limit N` bounds a night's run. Same lock, slot check, resume and Ctrl-C rules as the folder mode. One publish at the end (and every 100 sets, so a crash loses little).
- Folder mode (local sources, 07a's matcher) stays for the ~62 local sets and new uploads' crash repair.

## Runbook (lead; one machine at a time; no upload running on either)
1. **This machine:**
   - `cargo install --path crates/mediagram --locked`; check that `mediagram --version` equals `main`.
   - Other machine: confirm it is idle and on ≥ phase 06, and that its last push printed a message id whose caption reads `"schema":13`.
2. `mediagram pull-index`. Record in `reports/rollout-log.md` the channel index message id pulled (`meta.pulled_index_message_id`): that is the **pre-move backup** snapshot.
3. `mediagram subtitles move-inline --dry-run`, then `mediagram subtitles move-inline`.
4. Local sources, with the media folders from 07a (never drive roots): `backfill --dry-run`, review, then `backfill`. Then the channel: `backfill --channel --dry-run` (counts only), `backfill --channel --limit 20` (spot-check the first bundles on web/tablet), then unattended `backfill --channel` until nothing is left, then `--channel --mkv`.
5. **Other machine:** steps 1–2 and the *folder* half of 4 with its own folders. Never `move-inline` or `--channel` there (this machine covers every set in the channel).
6. Record the totals, the remaining gap list (`reports/`) and the channel index size in `reports/rollout-log.md`.

## Related code files
- Modify: `crates/mediagram/src/commands/subtitles/{mod.rs,dry_run.rs}` (sending mode), `crates/mediagram/src/cli.rs`, `crates/mediagram/src/index/assets.rs` (all inline subtitle rows of a set, in `lang` order), `README.md` (commands), `docs/system-architecture.md`, `docs/project-changelog.md`.
- Create: `crates/mediagram/src/commands/subtitles/{backfill.rs,move_inline.rs,move_inline_tests.rs,backfill_tests.rs}`.

## Implementation steps
1. `move_inline.rs` + tests with `FakeChannel`:
   - rows gone only after a matching read-back, numbering kept, summaries untouched;
   - a mismatched read-back leaves the rows in place.
2. `backfill.rs` sending mode + `--accept-fallback` + `--redo` + slot check, with fake-channel tests:
   - resume skips finished sets;
   - a fallback without opt-in is not sent;
   - redo replaces and deletes the old message;
   - the dry run writes nothing.
3. CLI; `scripts/check.sh`; bump; docs.
4. Runbook.

## Todo
- [ ] move-inline with read-back verification + tests
- [ ] backfill sending mode, `--accept-fallback`, `--redo`, slot check + tests
- [ ] CLI, check.sh, manifests, docs
- [ ] pre-move snapshot id recorded; other machine verified at v13
- [ ] move-inline run here
- [ ] backfill runs in the 07a scope (both machines)
- [ ] totals, gaps, index size recorded

## Success criteria
- Channel follower copy (read-only):
  - `SELECT COUNT(*) FROM assets WHERE kind='subtitle'` = 0.
  - `SELECT COUNT(*) FROM subtitle_files` = 1,266 plus the backfilled titles.
  - The file is ~28 MiB smaller.
- Web (API) and tablet + TV box (test profile): a lesson and, if in scope, a backfilled film/episode list their tracks and serve VTT.
- Tablet, a course opened once online: its other lessons' subtitles load in airplane mode (phase 04's course hold).
- `reports/rollout-log.md` holds the pre-move snapshot id, the totals and the remaining gaps.

## Tests
| Level | What |
|---|---|
| Unit | move-inline read-back + numbering; backfill resume, opt-in fallback, redo, slot check |
| Manual | dry runs reviewed before real runs; spot checks above |

## Risk assessment
| Risk | L × I | Mitigation |
|---|---|---|
| A stale (pre-v13) uploader publishes after the move → subtitles vanish on every client | Med × High | Gate: the other machine is verified by its own push's caption `schema`. Phase 02's alarm re-publishes on this machine's next pull. Readers keep the inline path until phase 09, so lessons fall back to whatever inline rows the stale push still carries, while bundled titles vanish until the next v13 push |
| A bundle is recorded but unreadable | Low × Med | Read-back before the delete (move-inline); `--redo` |
| Wrong file matched | Low × Med | 07a's rules; fallback only per-file opt-in; dry run reviewed first |
| Both machines send at once | Low × Med | Runbook: one at a time; upload lock; slot check |
| Runs take nights | Certain × Low | Resumable; paced; scope limited by 07a |

## Security
Reads only named media folders; no shell; unpinned documents only; nothing leaves the account's channel.

## Rollback
- Bundles are additive; before `move-inline`, reverting is a no-op for readers.
- After it, every body sits in a verified bundle and in the pre-move snapshot recorded in step 2. Restoring would mean a small script (bundle or snapshot → `assets`); it is not planned (YAGNI).
- The sidecars are not a complete copy (see Context).

## Next
- Phase 09 removes the inline read path once the channel shows 0 inline rows and both uploaders are ≥ 06.
- Follow-ups outside this plan:
  - re-upload the audio-damaged sets from phase 01;
  - bucket U extraction, if chosen;
  - restore rows from `#mlib-subs` captions during `rescan`;
  - OCR for picture-based subtitles.
