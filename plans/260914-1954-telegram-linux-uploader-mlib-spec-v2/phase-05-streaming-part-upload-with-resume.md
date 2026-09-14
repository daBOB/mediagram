---
phase: 5
title: "Streaming part upload with resume"
status: completed
priority: P1
effort: "2d"
dependencies: [2, 3, 4]
---

# Phase 5: Streaming part upload with resume

## Overview
The core: `mediagram add` and `mediagram resume`. Plans parts, records a pending set in `library.db`, streams each byte range straight from the source file through a hashing reader into `upload_stream`, sends it as a document with the full caption, records message id and document id, and marks progress so any crash is resumable and idempotent.

## Requirements
- Functional: `add <file> [--tmdb|--tvdb|--imdb] [--season --episode|--abs] [--variant] [--manual] [--no-remux] [--alang --slang --hdr]`; `resume` finishes every set with status `pending`; before re-uploading part i, search the channel for an existing message whose caption has the same `set` and `part.i` and adopt it.
- Non-functional: parts uploaded sequentially (one set at a time); configurable `throttle_ms` pause between parts; memory bounded to grammers' 512 KiB chunk buffers; temp remux file deleted only after the set completes.

## Architecture
```
crates/mediagram/src/
  index/db.rs          # open(data_dir/library.db), run mlib_spec::schema::MIGRATIONS
  index/sets.rs        # insert_set, set_status, get_set, list_pending
  index/parts.rs       # insert_parts, mark_done(set, idx, message_id, doc_id: i64, sha256), pending_parts
  upload/part_reader.rs # PartReader: File seeked to off, .take(len), wraps AsyncRead, feeds Sha256; exposes finalize() -> hex
  upload/send_part.rs   # upload_stream(reader, len, part_name) → InputMessage::document(uploaded).mime_type(..).text(caption) → send_message → extract message_id + document id
  upload/adopt.rs       # scan recent history: iter_messages(channel).limit(3 * part_count), parse each #mlib caption, match set id + part.i; returns Option<(message_id, doc_id)>
  upload/pipeline.rs    # run_set(set): for each pending part → adopt or upload → mark_done → throttle; on all done → set_hash → status complete → phase-6 push hook
  commands/add.rs       # inspect → ensure_faststart → resolve → plan_parts → insert set/parts → run_set
  commands/resume.rs
```
Caption for part i is built from the same `Caption` value with `part` replaced; sha256 is known only after streaming, so the caption is sent AFTER the upload finishes (upload_stream returns before send_message, so this is natural).

<!-- Updated: Validation Session 1 - grammers 0.10 API corrections -->
Document identity: `parts.doc_id INTEGER` stores grammers `Document::id()` (i64, verified in media.rs). The Android app matches by `(chat_id, message_id)` first and `doc_id` second; TDLib's `remote.unique_id` is derived from the same Telegram document id. The brainstorm's `file_unique_id TEXT` column is renamed to `doc_id INTEGER` in the spec schema.

## Related Code Files
- Create: files above; `crates/mediagram/tests/part_reader_hash.rs` (compare with `sha256sum` on a generated 5 MiB file, offset + length window); `crates/mediagram/tests/db_state.rs`
- Create: `crates/mediagram/tests/live_upload.rs` — `#[ignore]` integration test, runs only with `MEDIAGRAM_LIVE=1`, uploads a 2-part 3 MiB file with `part_size = 2 MiB` override and verifies captions
- Modify: `Cargo.toml` deps: rusqlite (bundled), sha2, hex, ulid, futures

## Implementation Steps
1. `index/db.rs` + migrations; `meta.schema_version`.
2. `PartReader` implementing `tokio::io::AsyncRead` over `tokio::fs::File` with `seek` + `take`, hashing every filled buffer.
3. `send_part.rs`: mime from container (`video/x-matroska`, `video/mp4`); part name from `mlib_spec::part_name`; attach caption via `.text(caption)`; parse returned `Message` for id and `Media::Document`.
4. `adopt.rs`: `iter_messages(channel).limit(3 * part_count)`; for each message with `media()` = `Media::Document` and caption starting `#mlib v=2`, parse and match `set` + `part.i`; deterministic, no server text search.
5. `pipeline.rs`: state machine; wrap each part in `telegram::retry::with_retry`; throttle; on completion compute `set_hash`, update status, delete temp remux, call phase-6 `push_index` (stub until phase 6).
6. `commands/add.rs`: wire phases 3 and 4; allow `part_size` override via config only (not CLI) to avoid accidental mixed sizes.
7. `commands/resume.rs`: iterate pending sets, `run_set`.
8. Tests listed above; live test executed manually once against the real channel with a small part size.

## Success Criteria
- [x] `add` on a 10 GB MKV creates 3 channel messages, 3 `parts` rows `done`, set `complete` — verified against `FakeTransport` in `tests/upload_pipeline.rs::uploads_all_parts_and_completes_set` (3×1 MiB fixture, 3 parts); the real-channel case needs `tests/live_add.rs` run manually (see Completion notes)
- [x] `kill -9` during part 2 then `resume` → part 2 adopted or re-uploaded, never duplicated — verified in `tests/upload_pipeline.rs::resumes_via_adoption_without_duplicate_upload` (part 0 pre-recorded done, part 1 present on the fake channel but unrecorded, part 2 pending; `send_part` is called exactly once, for part 2)
- [x] PartReader sha256 equals `sha256sum` of `dd if=src bs=1M skip=K count=N` — verified in `src/upload/part_reader.rs::hash_matches_direct_sha256_of_the_same_window` (direct `Sha256` over the same byte window)
- [ ] Peak RSS during upload < 200 MB — not measured; no Telegram credentials in this sandbox. `PartReader` never buffers more than one `tokio::io::ReadBuf` chunk, so this should hold, but it needs the live run to confirm

## Risk Assessment
- grammers `Uploaded` handle expiry ("less than a day") → send immediately after upload; no batching.
- Crash between `send_message` and `mark_done` → adopt step makes resume idempotent.
- FLOOD_WAIT mid-set → retry wrapper; set stays `pending`; user can re-run `resume`.

## Completion notes

- **`InputMessage` builder order matters.** `document()` reads `self.mime_type`
  at the moment it runs (`grammers-client-0.10.0/src/message/input_message.rs:236-241,388-396`),
  so `.mime_type(..)` must be called *before* `.document(..)` or the override
  is silently dropped in favor of guessing from the file name. `send_part`
  builds the message as `.mime_type(&mime).text(text).document(uploaded)`.
- **`Transport::send_part` takes a `Caption` template, not a rendered string.**
  The per-part sha256 is only known after `upload_stream` has fully drained
  the reader, but the full caption (including that hash) has to be sent in
  the same `send_message` call as the document. `send_part` resolves this by
  taking `caption: &Caption` with a placeholder `part.sha256`, calling
  `reader.finalize()` internally right after the upload completes, and
  rendering the final caption text from `caption.with_part(..)` before
  sending — one read pass over the part's bytes serves both the upload and
  the hash, and the caption is still sent after the upload as the risk
  assessment above requires.
- **`rusqlite` cannot use the `bundled` feature here.** `grammers-session`
  already statically links its own sqlite3 via `libsql-ffi`; with rusqlite's
  `bundled` feature also linking a second copy, the final binary fails to
  link with `duplicate symbol: sqlite3_*` errors. `Cargo.toml` now takes
  plain `rusqlite = "0.40.2"`, which links the system `libsqlite3` (present
  on this host via pkg-config) instead of vendoring a second copy.
- **sqlite has no native `u64`.** `byte_offset`/`byte_length`/`total`/`tmdb`/
  `tvdb` are stored as `i64` (sqlite's native integer width) and cast back to
  `u64` on read in `index::parts`/`index::sets`; values never approach 2^63.
- No live Telegram credentials are available in this sandbox, so
  `tests/live_add.rs::add_uploads_a_three_part_file` is `#[ignore]`d and was
  not executed here. Run it manually once against the real channel with:
  `MEDIAGRAM_LIVE=1 MEDIAGRAM_PART_SIZE=1048576 cargo test -p mediagram --test live_add -- --ignored --nocapture`
  (needs a real `config.toml` and a prior `mediagram login`; `--manual`
  prompts on the terminal, so this is a manual/interactive run, not a CI one).
- `src/upload/pipeline.rs` is 247 lines and `src/index/sets.rs` is 212,
  both modestly over the 200-line guideline; both are a single cohesive
  concern (resumable part upload; the `sets` row/SQL mapping for 27 mirrored
  Caption fields) and splitting further looked like it would add file-hopping
  without reducing complexity, so left as is.
