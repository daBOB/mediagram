# Code Review: Phase 5 — streaming part upload with resume

Commit `0ca2392` (merged `565d5e9`). Branch `main`. Build, `clippy -D warnings`, `fmt` clean on the committed tree; 143 tests pass, 1 ignored (`live_add`). All grammers claims below were checked against `~/.cargo/registry/src/*/grammers-client-0.10.0` and `grammers-session-0.10.0`.

Note: `crates/mediagram/tests/edge_cases_probe_upload_pipeline.rs` is untracked and was being written by a parallel agent during this review (it failed one test, then passed on re-run, and broke `clippy --all-targets`). It is not part of the reviewed commit. Do not trust a "clippy green" from this worktree until that file is either committed clean or removed.

## Scope
- `src/index/{mod,db,sets,parts}.rs`, `src/upload/{mod,part_reader,transport,pipeline}.rs`, `src/commands/{add,resume}.rs`, `src/lib.rs`, `Cargo.toml`, `tests/{upload_pipeline,index_state,live_add}.rs`; contract crate `mlib-spec` read-only. ~1,050 LOC under review.

## Overall assessment
The core integrity path is correct: `PartReader` hashes exactly the bytes grammers pulls (`part_reader.rs:47-51` hashes `filled()[before..]` only on `Ready(Ok)`, so partial and final short reads are covered); the per-part caption carries the real hash and the DB row's `off/len/i/n/total` (`pipeline.rs:116-122`, `transport.rs:88-92`); `set_hash` is over `done` hashes in `idx` order (`parts.rs:89`); `mark_done` and `set_hash_and_complete` are single-statement updates so a crash between `send_message` and `mark_done` is covered by the adopt scan, which matches on set id and `part.i`, skips non-mlib and `#mlib-index` messages, and keeps the newest duplicate (newest-first iteration + `or_insert`). Memory is bounded: nothing buffers a whole part; grammers holds at most `WORKER_COUNT (4) × MAX_CHUNK_SIZE (512 KiB)` in flight (`files.rs:31-34,405-433`).

The gaps are at the edges: the source file is trusted blindly on resume, the temp-file cleanup keys on a filename suffix, and the retry wrapper can still duplicate a message. Details below, severity-ranked.

## Critical
None.

## High

### H1. `remove_faststart_tmp` can delete the user's original file
`pipeline.rs:236-241` deletes whatever `source_path` is when its name ends in `.faststart.mp4`. `remux::ensure_faststart` returns `src` unchanged when the file already passes the check or `--no-remux` is set (`remux.rs:24-25`). A user who already remuxed with ffmpeg themselves and named the result `Movie.faststart.mp4` (a natural name) runs `add` and, on completion, loses the original. Related: with no `tmp_dir`, the remux writes to `src.parent()/<stem>.faststart.mp4` with `-y` (`remux.rs:28-36,41`), so an existing sibling of that name is overwritten too.

Fix: make "is a generated temp" explicit state, not a name pattern. Either have `ensure_faststart` return `(PathBuf, bool)` / a small `Source { path, generated }` and record `tmp:<set_id>` in `meta` alongside `source:<set_id>` at `add.rs:102-108`; `run_set` (or the two callers) then deletes only a path recorded under `tmp:`. Drop the suffix test entirely.

### H2. Resume trusts the source file; a shrunk file can produce a short, "done" part
`resume.rs:51` only checks `exists()`. Nothing compares `metadata().len()` with `set.total`, and nothing verifies that a part upload consumed exactly `byte_length` bytes.

Consequence, traced through grammers: if the file is shorter than `off + len`, `Take<File>` returns EOF early. `PartStream::next_part` (`files.rs:545-566`) errors with `UnexpectedEof` only when EOF lands before the *last* 512 KiB chunk; inside the last chunk it `break`s and uploads the short chunk. So up to 512 KiB−1 bytes can go missing from a part while `InputFileBig.parts` still equals `total_parts`. The caption then says `len = byte_length` with a sha256 over fewer bytes, `mark_done` records the planned `byte_length`, and `PLAYABLE_SQL` (schema.rs:41-43) sums the *planned* column, so the set is marked complete with a short part. A same-size replacement file is worse: hashes from two different files get mixed into one `set_hash` with no error at all.

Fix (cheap, three lines each):
1. `run_set` entry (or `resume_one`): `bail!` if `tokio::fs::metadata(source_path).len() != set.total`.
2. `PartReader` tracks `bytes_read: u64`; `send_part` checks `reader.bytes_read() == len` after `upload_stream` and before `send_message` (`transport.rs:86-88`), so a short stream never becomes a message.
3. Optional: store `mtime`/`len` in the `source:<set_id>` meta value at `add` and compare on resume.

### H3. Retried `send_message` can duplicate a part message
`transport.rs:97-115` wraps `send_message` in `with_retry`, and `is_retryable` (`retry.rs:27-32`) returns `true` for every non-RPC error (I/O, dropped connection, deserialize). If the server committed the message but the response was lost, the retry sends again with a fresh `random_id` (`messages.rs:600`), producing two identical 3.5 GiB messages; the DB records the second, the first is an orphan. The adopt scan runs only at `run_set` start (`pipeline.rs:41-46`) so it cannot see this. The phase success criterion says "never duplicated".

Fix options (pick one):
- Before each retry of `send_message` (non-FLOOD_WAIT path only), call `recent_messages(3)` and adopt a message whose caption matches `set` + `part.i`; return it as `Sent` instead of resending.
- Or restrict `send_message` retries to `FLOOD_WAIT`/5xx (a rejected request cannot have created a message) and let transport errors surface; the user's next `resume` adopts. Simplest; slightly worse UX.

## Medium

### M1. No cross-process lock: `add` and `resume` in parallel duplicate parts
`add` takes hours for a 10 GB file; a user opening a second terminal and running `resume` for an older set will also pick up the in-flight set (`list_pending`, `sets.rs:201-209`), scan the channel, not find the part being uploaded, and upload it again. SQLite WAL lets both processes proceed. Fix: `flock` a `<data_dir>/library.lock` at `db::open` (or at the start of `add`/`resume`) and fail fast with "another mediagram process is running".

### M2. Caption budget is validated only after the part upload; overflow strands the set
`to_text` runs at `transport.rs:92`, after `upload_stream` finished. If marker + JSON exceeds 1,024 UTF-16 units (long title/show, many languages), the part upload is wasted, the error repeats on every `resume`, and the set can never leave `pending` without DB surgery. Fix: in `add.rs` before the insert transaction, dry-run `mlib_spec::to_text` on the caption with the widest possible part block (`i = n-1`, `off = total`, `len = part_size`, `sha256 = "0".repeat(64)`) and bail with a clear message. Also move the `to_text` call in `send_part` above `upload_stream` (it depends on the hash, so keep the placeholder render as a pre-check only).

### M3. rusqlite without `bundled`: portability and a symbol-interposition caveat
`Cargo.toml` links the system `libsqlite3` (`libsqlite3-sys 0.38.2` via pkg-config, `Cargo.lock:1395-1402`) while `libsql-ffi 0.9.30` statically links its own sqlite fork with the same `sqlite3_*` symbol names. Consequences:
- Every build host needs `libsqlite3-dev` + `pkg-config` (CI, Docker, cross-compiles); every runtime host needs `libsqlite3.so`. Document in README and CI.
- ELF symbol resolution prefers definitions inside the executable over `DT_NEEDED` libraries, so rusqlite's calls most likely bind to libsql's static copy at runtime, not the system library. It works because the C API is compatible, but it means the "system sqlite" is a linker pacifier, not what actually runs, and any libsql behavioural divergence hits `library.db`. Could not confirm with `nm` here (the `target/` dir is hook-blocked); author should run `nm target/release/mediagram | grep ' T sqlite3_open_v2'` and `nm -D … | grep ' U sqlite3_open_v2'` and record the result in the phase file.
- Cleaner long-term options: `grammers-session` with `default-features = false` and a custom `Session` storage over rusqlite (one sqlite in the binary), or `libsql` for `library.db` too. Neither is a phase-5 blocker; the pragmatic path is to document the requirement and keep the deviation.

### M4. Adoption trusts the scanned caption's `sha256`/`off`/`len` without checking them against the pending row
`pipeline.rs:212-222` keys only on `set` and `part.i`. A tampered or hand-edited caption (or a future spec bug) with a wrong `off/len` or an empty/short `sha256` is adopted and folded into `set_hash`. Add: `caption.part.off == part.byte_offset && caption.part.len == part.byte_length && caption.part.n == total_parts && sha256.len() == 64 && all hex`, otherwise skip (log at warn). Same cheap guard as `done_hashes`: `parts.rs:93-95` silently drops NULL hashes; return an error instead so a broken row cannot yield a shorter `set_hash` and a `complete` set.

## Low

- **L1** `transport.rs:148-153`: `bot_api_dialog_id()` is `None` only for the self-user (`peer.rs:243-244`), which can never be the channel; the fallback to `bare_id_unchecked()` would silently store a *different id format* in `parts.chat_id`. Use `.expect("library channel always has a dialog id")` or bail, so a format mix can't happen.
- **L2** `add.rs:19-31`: `tmdb_key` check runs after `inspect` (ffprobe). Move it to the top so the error is instant.
- **L3** `add.rs:110` connects to Telegram after the set + meta are already committed. If login is missing, the user is left with a pending set and must `resume` after logging in. Works, but connecting before the insert (after remux) gives a clean failure with nothing to clean up.
- **L4** Crash between `set_hash_and_complete` (`pipeline.rs:85`) and `delete_meta` (`add.rs:117`, `resume.rs:64`) leaves an orphan `source:<id>` key. Harmless; folding the delete into the completion transaction removes it.
- **L5** `pipeline.rs:78-80` throttles after the last part too. Trivial.
- **L6** `sets.rs:105-106` `unwrap_or_default()` silently turns corrupt `alang/slang` JSON into `[]`. Prefer surfacing the error.
- **L7** `db.rs:19` sets WAL on `library.db`. Forward-looking for phase 6: a byte copy of the file misses un-checkpointed commits; run `PRAGMA wal_checkpoint(TRUNCATE)` (or the backup API) before pushing.
- **L8** `classify::container_from_ext` returns `""` for an extension-less file → caption `container: ""`, mime `application/octet-stream`, part names ending in `.p000`. Inspect probably rejects such inputs; if not, bail in `add`.
- **L9** File-size rule: `pipeline.rs` 247 and `sets.rs` 212. A clean split exists and matches the plan's own architecture: move `Adopted` + `adoption_map` (`pipeline.rs:19-25,201-225`) to `upload/adopt.rs` (also makes the matcher unit-testable directly) and `mime_for` + `remove_faststart_tmp` to a small `upload/source.rs`; pipeline lands near 180. For `sets.rs`, move `SetRow` + `from_caption`/`from_row` to `index/set_row.rs` and keep the SQL functions in `sets.rs` (~95 lines). Both mechanical; not blocking.
- **L10** Test gaps worth adding to `tests/upload_pipeline.rs`: adopt picks the newest of two duplicates; non-mlib and `#mlib-index` messages in the scan are ignored; source shorter than `total` fails before any send (after H2); a `send_part` error after the message exists is recovered by the next `run_set` without a second send.

## Explicit checks
- **(a) Success criteria**: three marked done are backed by the cited tests. RSS unmeasured, honestly marked. Boundedness confirmed by reading: `PartReader` = `Take<File>` + hasher; grammers ≤ 4 × 512 KiB in flight; `read_to_end` appears only in tests.
- **(b) Integrity**: verified as described in the assessment; the one hole is H2 (short last chunk accepted by grammers, never detected locally). `PLAYABLE_SQL` can't catch it because it sums planned lengths.
- **(c) Resume**: `source:<set_id>` written in the same transaction as the set/parts (`add.rs:102-108`) with a canonicalized path; read and deleted correctly in `resume.rs:43-49,63-66`. Moved file → clear "missing" error. Changed size → H2. Temp lifecycle → H1.
- **(d) grammers 0.10.0**: `upload_stream(reader, len as usize, name)` size equals part length ✓ (`transport.rs:84`); `mime_type` must precede `document` — confirmed, `document()` calls `get_file_mime` at `input_message.rs:236-244,388-396` ✓; `send_message` in `with_retry` ✓ (but see H3); `iter_messages(..).limit(n)` caps total yielded, `next()` returns `None` at the limit (`messages.rs:302-319`) and iterates newest-first ✓; `Media::Document(doc).id()` is the i64 document id (`media.rs:279-284`) ✓; `Uploaded` is `Clone` (`media.rs:39`) and consumed immediately after upload ✓; `Message::text()` is the caption for media messages (`message.rs:352-356`) ✓. `document()` does not attach `DocumentAttributeVideo`, so parts are stored as plain files — correct for raw byte splits.
- **(e) Config/CLI**: `--no-push` on both commands ✓ (`args.rs:44-46`, `main.rs:30-34`); `tmdb_key` missing + not manual → clear error ✓ (`add.rs:29-31`, after ffprobe, L2); `--alang/--slang/--hdr` applied ✓ (`add.rs:70,74,75`); container from the file actually uploaded ✓ (`add.rs:71` uses `source_path`, the remux output when one happened); names via `mlib_spec::part_name` ✓ (`pipeline.rs:113-115`).
- **(f) Repo rules**: no plan/finding references or secrets in phase-5 code or logs (grep clean); snake_case throughout; two files over 200 lines (L9).

## Positive observations
- `Transport` trait with `FakeTransport` gives real end-to-end coverage of the resume/adopt path without Telegram.
- Single read pass serves both upload and hash; caption sent after the upload as the risk table requires.
- The "message sent but media missing" case (`transport.rs:117-120`) fails *after* the send, which is the correct failure mode: the next `resume` adopts it.
- Set/parts/meta inserted in one transaction; `set_hash_and_complete` is one statement.

## Recommended actions (priority order)
1. H1: replace the `.faststart.mp4` suffix test with recorded temp-path state (`tmp:<set_id>` meta or a `generated` flag).
2. H2: size check on resume + `bytes_read == len` check in `send_part` before `send_message`.
3. H3: re-scan before resending, or restrict `send_message` retries to FLOOD_WAIT/5xx.
4. M1: process lock in `data_dir`.
5. M2: caption-budget dry run in `add` before inserting the set.
6. M3: document the `libsqlite3` build/runtime requirement; author to confirm which sqlite actually binds with `nm`.
7. M4 + L1: cheap validation guards on adopted captions, `done_hashes`, and `chat_id`.
8. L9 split when convenient; L10 tests alongside H2/H3.

## Plan follow-ups
Phase-5 todo items are complete or honestly marked pending (live run, RSS). Recommend keeping phase 5 "completed" but recording H1-H3 as fix-forward items before the live 10 GB run, since that run is the first time H2/H3 can bite for real.

## Unresolved questions
1. Which sqlite implementation does rusqlite bind to at runtime in the release binary (M3)? Needs `nm` on the built binary.
2. Is the parallel probe file (`tests/edge_cases_probe_upload_pipeline.rs`) intended to be committed? If so it must pass `clippy -D warnings` and `fmt` first.
