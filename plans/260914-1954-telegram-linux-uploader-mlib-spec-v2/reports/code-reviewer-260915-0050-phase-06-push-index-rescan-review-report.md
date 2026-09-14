# Phase 6 review: push-index, rescan, add/resume hook

Commit b1373e5. Files: `crates/mediagram/src/index/{snapshot,rescan,mod}.rs`, `src/commands/{push_index,rescan,add,resume}.rs`, `tests/{index_rescan,index_snapshot}.rs`. ~1,100 LOC incl. tests.

Verified this session: `cargo clippy -p mediagram --all-targets -D warnings` clean; `tests/index_rescan.rs` 5/5, `tests/index_snapshot.rs` 1/1 pass. No live Telegram calls possible here.

## Overall

Solid, small, well-factored. `apply_seen` is pure and unit-tested in both duplicate orders; send uses FLOOD_WAIT-only retry while pin/unpin (idempotent) use full retry; `#mlib-index` is excluded by `is_mlib` (verified `caption_codec.rs:70-71,107`); batches are bounded and transactional. No trust-boundary or data-loss defects. The issues below are about honesty of the phase status, pin-rotation durability, a temp-file race, and summary accuracy.

## grammers 0.10.0 API verification (check a)

| Call | Source | Status |
|---|---|---|
| `upload_stream(&mut file, size, "library.db".to_string())` | `client/files.rs:388-392` `(stream: &mut S, size: usize, name: String)` | matches `push_index.rs:94` |
| `.mime_type(..).text(..).document(..)` | `message/input_message.rs:382-385` doc: "should be called before setting any media"; `document()` at `:236` reads `self.mime_type` | order correct |
| `pin_message(peer, i32)` / `unpin_message(peer, i32)` | `client/messages.rs:1200-1225`; both call `update_pinned` with `silent: true, pm_oneside: false` hardcoded (`:1233-1237`) | no `silent`/`one_side` variant exists or is needed; pin is already silent |
| `iter_messages(peer)` with no `.limit()` | `iter_buffer.rs:74-84`: `limit == None` → each page requests `MAX_LIMIT` (100); terminates on `last_chunk` (`messages.rs:205`) or empty page | pages the whole history, newest first |

## Findings (severity ranked)

### High

**H1. Success criteria marked `[x]` without the live gate.** `phase-06-index-push-and-rescan.md:41-43` ticks all three criteria; `:56` says "no live Telegram calls made". Criteria 1 (pinned `library.db` in channel, sets count matches) and 3 (old index unpinned) and the real-channel half of 2 are unverified. Mark them "pending live gate" as phase 5 did, and add a live checklist: push twice, confirm exactly one pinned `#mlib-index`, `rm library.db && rescan`, diff rows.

**H2. Tree is not green: untracked `crates/mediagram/tests/edge_cases_probe_index_rescan.rs` (18 tests, 2 failing).** `cargo test --workspace` fails on this tree (`malformed_sha256_still_gets_recorded`, `deleting_a_part_from_new_scan_does_not_revert_to_pending`). Same parallel-tester pattern as phases 2-5. Either delete it or promote the useful probes into `tests/index_rescan.rs` before anything else lands. Probe 8 encodes the opposite of the implemented rescan semantics (see M3), so the decision must be made explicitly.

**H3. A failed unpin permanently orphans the old pin.** `push_index.rs:132-135`: `unpin_previous` warns on failure, then `set_meta(index_message_id, new_id)` overwrites the only record of the old id. A transient failure (timeout, max_attempts exhausted) leaves the old `library.db` pinned forever; no later push will retry it. The plan's risk row says "always unpin the previous explicitly" and "TV app must pick the newest pin", so the reader side tolerates it, but the uploader should not give up silently. Fix: on unpin failure other than `MESSAGE_ID_INVALID`/not-found, keep the old id under `stale_index_message_id` and retry it on the next push (or return the error and leave `index_message_id` untouched so the next push retries; the new pin already supersedes). Not-found errors are 400s, so `with_retry` correctly does not retry them (`retry.rs:29`) and the warn path is right for that case.

### Medium

**M1. Temp snapshot path is shared and not unique → concurrent pushes corrupt the upload.** `push_index.rs:41` uses a fixed `data_dir/library.push.db`; `snapshot_to` deletes and rewrites it (`snapshot.rs:32-40`). Two overlapping pushes (e.g. `add` finishing while the user runs `push-index`, or two `add`s on different files — there is no cross-process lock, carried from the phase-5 review) mean one process removes/rewrites the file the other is streaming; grammers only errors on EOF before the last chunk, so a truncated or mixed `library.db` can be uploaded and pinned. Use `tempfile::Builder::new().prefix("library.push.").tempfile_in(&data_dir)` or a pid-suffixed name; keep the "remove on every exit" behaviour. Cleanup today: push failure → removed (`push_index.rs:46`); `VACUUM INTO` failure → partial file left but replaced next run; SIGKILL → left, replaced next run. All acceptable once the name is unique.

**M2. Rescan summary double-counts across batches.** `commands/rescan.rs:93-97` sums per-batch `RescanSummary`s. A set whose parts straddle a 500-message boundary is counted in `sets_seen` twice and reported as both incomplete (batch 1) and complete (batch 2); `parts_seen` also re-counts a part re-observed in a later batch (same message id is not a duplicate by design, `rescan.rs:138`). For a DR tool the printed counts are the user's only signal. Fix: keep `parts_seen`/`duplicates_skipped` running totals, but derive `sets_seen/complete/incomplete` from one `SELECT status, COUNT(*) FROM sets GROUP BY status` (or over the union of touched set ids) after the loop.

**M3. Decision to record: rescan is additive; it never demotes or deletes.** `parts_complete` (`rescan.rs:187-196`) counts local `parts` rows, which persist from the original `add`. On an existing DB: a set whose channel messages were deleted stays `complete` (its local rows are still `done`); sets absent from the channel are untouched. In the intended DR flow (`rm library.db`) this is correct. Not a bug, but the behaviour differs from what the probe test author assumed and from the phrase "rebuild" in the plan. Document in the module doc and `rescan` help text: "additive; use `verify` to demote missing parts", or add `--fresh` that requires an empty DB. Flagging for the lead, not auto-changing.

**M4. Push failure after a completed set exits non-zero with no hint that the set is safe.** `add.rs:135-137`, `resume.rs:38-39`: "set X added" is printed first (requirement met: set id is not hidden, set status is untouched — `run_set` already wrote `complete` at `pipeline.rs:75-77`), but the error is e.g. "connecting to Telegram: ..." and the process exit code says `add` failed. Scripts will treat it as a failed add. Add `.with_context(|| format!("set {set_id} is complete but the index push failed; run `mediagram push-index`"))`. Same for `resume` (all sets in that batch are complete).

**M5. Unparseable mlib captions are dropped silently.** `rescan.rs:52-54`: `is_mlib` matches `#mlib v=3`/malformed JSON, `parse` fails, message skipped with no counter or log. In DR this is exactly what the user needs to know about. Add an `unparsed` counter to `RescanSummary` and `tracing::warn!(message_id, ...)` (message id only; caption text is user data and may be long, but contains no secrets — either is fine).

### Low

**L1. `last_push_at` is written before the push and survives a failed push.** `snapshot.rs:29`. Per the plan ("sets meta.last_push_at first") so the snapshot carries its own timestamp — the snapshot does contain it (test `index_snapshot.rs:70-79` verifies). Locally it means "last attempted", not "last pushed". Acceptable; name it accordingly in a doc comment, or restore the previous value on failure.

**L2. Checkpoint is not what makes the snapshot consistent.** `snapshot.rs:13-14` says the checkpoint is needed "so VACUUM INTO captures every committed write"; `VACUUM INTO` reads through the pager and already includes committed WAL frames (that is why `last_push_at`, written after the checkpoint at `push_index.rs:40-42`, still lands in the snapshot). The `TRUNCATE` checkpoint can also return `busy=1` and do nothing (`execute_batch` ignores the row — `rusqlite lib.rs:555-559`, `if false` guard) when `add`'s still-open connection at `add.rs:105` holds a read. Harmless; reword the comment to "shrinks the WAL before copying". Also: `VACUUM INTO` needs SQLite >= 3.27; rusqlite links the system `libsqlite3` (`Cargo.toml:22-26`), so note the minimum in the README/deploy notes.

**L3. Orphan index document on pin failure.** `push_index.rs:124-130`: send succeeded, pin exhausted retries → error, `index_message_id` unchanged (correct: previous pin id is not lost). The sent, unpinned `library.db` lingers and is never referenced; `rescan` skips it, so no data impact. Include `new_id` in the error context so the user can delete it.

**L4. `resume` pushes only if the entire batch succeeds.** `resume.rs:28-39`: set 3 of 5 failing means sets 1-2 completed in this run are not pushed until a later `push-index`/`add`. Documented intent in the doc comment; fine, just noting.

**L5. Second Telegram handshake per `add`.** `add.rs:124-127` shuts the client down, then `push_after_set` reconnects (`push_index.rs:70`), writing the session file twice. Functionally fine; passing `&Tg` through would need `push_snapshot` to take a client. YAGNI unless the double login shows up in the live gate.

**L6. `chat_id` derivation duplicated.** `commands/rescan.rs:24-28` copies `TelegramTransport::chat_id` (`transport.rs:156-161`). Extract a free `fn chat_id(peer: PeerRef) -> i64` in `telegram::client` when either is next touched.

**L7. `parts_complete` vs `PLAYABLE_SQL`.** `rescan.rs:190-196` sums `byte_length` over `done` rows only; `schema.rs:43` sums all rows. Equivalent for rescan-written rows (always `done`); diverges only if a local `pending` row survives, where rescan's stricter version is the safer one. No change needed; noted so nobody "fixes" it toward `PLAYABLE_SQL`.

**L8. Caption idx outside `[0, n)` is not rejected.** `mlib_spec::parse` does not check `part.i < part.n`; a caption with `i=5, n=3` would insert a row and, if lengths happen to sum to `total`, mark the set complete. Self-produced captions make this theoretical; a one-line guard in `apply_seen` (`caption.part.i >= caption.part.n → skip + count`) closes it.

**L9. File sizes at the limit.** `index/rescan.rs` 197 lines, `tests/index_rescan.rs` 198. The next edit to either must split (e.g. move `ExistingPart`/`upsert_part`/`write_part_done` to `index/rescan_parts.rs`).

## Explicit checks

- (a) grammers calls: verified above. Live criteria: unverified, must be un-ticked (H1).
- (b) Snapshot: checkpoint then `VACUUM INTO` on the same open connection is valid; `last_push_at` is set after checkpoint, before vacuum, and is in the snapshot (test-verified). Temp cleanup on success and push failure; leftovers from crashes are replaced (`snapshot.rs:32-35`, test `snapshot_overwrites_a_stale_destination`). Race on the shared name: M1.
- (c) Rescan: all part fields written (`rescan.rs:159-180`: offset, length, chat_id, message_id, doc_id, sha256, status). Set fields from the first caption via `SetRow::from_caption` (`created_at` = rescan time, unavoidable). Idempotent (test). Duplicates: higher message id wins in both arrival orders (test). Index/plain captions skipped (test). Batches of 500 in one tx each (`commands/rescan.rs:67-71,87-91`); memory bounded (500 `Seen` + grammers 100-message page). `apply_seen` decides completeness from `parts` directly (`rescan.rs:184-196`), not `PLAYABLE_SQL`. `set_hash` only when complete (`rescan.rs:73-76`). Missing-in-channel / absent sets: additive, not demoted — M3 (decision).
- (d) Pin rotation: `index_message_id` written only after pin success (`push_index.rs:125-135`); failed push leaves the previous id intact; unpin not-found is a 400 → warned, not retried, not fatal. Unpin transient failure loses the old id — H3.
- (e) Hook: `add` pushes once iff completed and `!no_push` (`add.rs:136`); `resume` once after the batch iff `!no_push` (`resume.rs:38`). Set status is written by `run_set` before the push and never touched by it; set id printed before the push. Error messaging — M4.
- (f) Files < 200 (L9 at the edge); no plan/phase refs in code (grep clean); logs carry paths, ids and grammers errors only — no secrets; snake_case throughout.

## Positive observations

- `is_mlib` marker prefix cleanly separates part captions from `#mlib-index`; tested in both crates.
- Correct retry split: `with_flood_wait_only` for the non-idempotent send, `with_retry` for idempotent pin/unpin.
- `Uploaded` name decoupled from the temp file name, so the channel document is always `library.db`.
- Pure `apply_seen` over `Seen` reuses the phase-5 transport type; tests need no Telegram.
- `parts_complete` deliberately avoids `PLAYABLE_SQL`'s circular precondition and says why.

## Recommended actions

1. Remove or fold in the untracked probe file; re-run `cargo test --workspace` (H2).
2. Un-tick live criteria in the phase file; run the live gate (H1).
3. Keep/retry the old pin id on unpin failure (H3).
4. Unique temp snapshot name (M1).
5. Compute set counts once after the loop (M2); add `unparsed` counter (M5).
6. Record the "rescan is additive" decision in the module doc and CLI help (M3); add push-failure context in `add`/`resume` (M4).

## Metrics

- Committed tests: 6 phase-6 integration + 2 inline unit; clippy `-D warnings` clean; fmt not re-run here.
- Type coverage: n/a (Rust). Linting issues: 0 on committed files.

## Unresolved questions

1. Should `rescan` on a non-empty DB demote sets whose parts are absent from the channel (M3)? Plan wording "rebuild" vs implementation "additive".
2. Should `last_push_at` mean "last attempt" or "last successful push" (L1)?
