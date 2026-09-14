---
phase: 6
title: "Index push and rescan"
status: complete
priority: P2
effort: "1d"
dependencies: [5]
---

# Phase 6: Index push and rescan

## Overview
Publish `library.db` to the channel as a pinned document after each completed set, rotate the previous pin, and provide `rescan` to rebuild the local DB from captions when it is lost.

## Requirements
- Functional: `push-index` uploads a consistent snapshot, caption `#mlib-index v=2` + JSON `{"pushed_at":<unix>,"sets":N,"schema":<ver>}`, pins it, unpins/marks the previous index message; `rescan` walks all channel messages, parses `#mlib v=2` captions, upserts sets and parts, marks sets `complete` when the playable invariant holds; never re-uploads media.
- Non-functional: snapshot via `VACUUM INTO` to a temp file so the live DB is never uploaded mid-write; index doc named `library.db`; `rescan` is idempotent and reports counts.

## Architecture
```
crates/mediagram/src/
  index/snapshot.rs     # vacuum_into(tmp) → PathBuf; sets meta.last_push_at first
  commands/push_index.rs # upload_file(tmp) → send document + caption → pin_message → previous id from meta.index_message_id → unpin (or edit caption to "superseded") → store new id
  commands/rescan.rs    # iter_messages(channel) → for each Media::Document with caption starting "#mlib v=2" → caption_codec::parse → upsert; skip index docs; final pass sets status by invariant
  index/upsert.rs       # upsert_set_from_caption, upsert_part_from_caption(message_id, doc_id: i64 from Document::id())
```
Playable invariant: `count(parts where status='done') == part_count AND sum(byte_length) == total`.

## Related Code Files
- Create: files above; `crates/mediagram/tests/rescan_from_captions.rs` (fixture list of captions → expected rows)
- Modify: `upload/pipeline.rs` to call `push_index` on set completion; `--no-push` flag on `add`/`resume` for bulk sessions followed by one manual push

## Implementation Steps
1. `snapshot.rs` with rusqlite `VACUUM INTO ?1`.
<!-- Updated: Validation Session 1 - grammers 0.10 API corrections -->
2. `push_index.rs`: `upload_file(tmp)` → `send_message(channel, InputMessage::document(..).text(caption))` → `pin_message(channel, id)` → `unpin_message(channel, meta.index_message_id)` (both verified in grammers 0.10.0 messages.rs) → store new id in `meta`.
3. `rescan.rs`: page through history; parse; upsert; compute set hashes when all parts present; print summary (sets complete/incomplete, parts).
4. Tests: fixture captions incl. one incomplete set → status `pending`; duplicate part message (same set+idx twice) → keep highest message_id, warn.

## Success Criteria
- [ ] After `add`, channel shows a pinned `library.db` whose `sets` count matches local (pending live gate)
- [x] `rm library.db && mediagram rescan` reproduces sets/parts rows equal to the previous DB minus timestamps
- [ ] Old index message is no longer pinned after a push (pending live gate)

## Risk Assessment
- Rescan cost on large channels → paged iteration, only documents; acceptable for DR-only use.
- Multiple pins (Telegram allows many) → always unpin the previous explicitly; TV app must pick the newest `#mlib-index` pin.

## Completion Notes
- `index::snapshot`: `checkpoint` (WAL truncate) + `snapshot_to` (`VACUUM INTO`, recording `meta.last_push_at` first, replacing a stale destination).
- `index::rescan::apply_seen`: pure fold over `&[Seen]` — upserts sets (first caption wins, since every part mirrors the same set-level fields), upserts parts keyed `(set_id, idx)` via `INSERT ... ON CONFLICT DO UPDATE`, treats a same-idx caption under a different message id as a duplicate and keeps the higher id, then recomputes `complete`/`pending` per touched set directly from `parts` (not `PLAYABLE_SQL`, which presupposes `complete` already).
- `commands::push_index`: opens the db, checkpoints, snapshots to `data_dir/library.push.db`, uploads via `upload_stream` under the name `library.db` (independent of the temp file's on-disk name), sends with caption `#mlib-index v=2` + JSON, pins, unpins the previously recorded `index_message_id` (best-effort — logged, not fatal), records the new id, deletes the temp file. `push_after_set` delegates to `run`.
- `commands::rescan`: pages the entire channel history via `iter_messages` (no limit), filters to document + mlib-caption messages before buffering, folds in batches of 500 inside one transaction per batch, prints the aggregated `RescanSummary`.
- `add`/`resume` call `push_index::push_after_set` on successful completion unless `--no-push`; `resume` pushes once after the whole batch, not per set.
- Tests: `crates/mediagram/tests/index_rescan.rs` (5 cases: complete/incomplete/duplicate/ignored/idempotent) + `crates/mediagram/tests/index_snapshot.rs` (round trip through a real file) + inline unit tests in `index/snapshot.rs`.
- Verification: `cargo fmt --all -- --check`, `cargo clippy --all-targets -- -D warnings`, `cargo test --workspace` all clean; no live Telegram calls made (no credentials in this sandbox).

## Review fixes (2026-09-15)
- Unpin failures keep the old id under `meta.stale_index_message_id` and are retried on every later push; a 400 from Telegram (message gone) clears it.
- Snapshot temp file is per process (`library.push.<pid>.db`) so overlapping pushes cannot corrupt each other's upload.
- Rescan reports set counts from the library after the full scan (batch sums double-counted sets straddling a 500-message boundary); captions with the mlib marker that fail to parse are counted as `unparsed` and logged.
- Decision: rescan is additive. It rebuilds from captions and never demotes a locally `complete` set whose messages vanished; `verify` is the tool that detects missing parts. Stated in the command help.
- Push failure after a completed set now names the set and the recovery command.
- Live criteria (pinned library.db, old pin removed) are unticked until the live `add` runs.
