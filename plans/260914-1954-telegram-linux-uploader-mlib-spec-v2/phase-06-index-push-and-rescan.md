---
phase: 6
title: "Index push and rescan"
status: pending
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
- [ ] After `add`, channel shows a pinned `library.db` whose `sets` count matches local
- [ ] `rm library.db && mediagram rescan` reproduces sets/parts rows equal to the previous DB minus timestamps
- [ ] Old index message is no longer pinned after a push

## Risk Assessment
- Rescan cost on large channels → paged iteration, only documents; acceptable for DR-only use.
- Multiple pins (Telegram allows many) → always unpin the previous explicitly; TV app must pick the newest `#mlib-index` pin.
