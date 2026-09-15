# Phase 7 review: verify command + v1 documentation set

Commit `c5828a3`. Files: `crates/mediagram/src/commands/verify.rs`, `src/verify/{mod,report,download_hash}.rs`, `src/lib.rs`, `tests/verify_report.rs`, `docs/{mlib-spec-v2,system-architecture,code-standards,development-roadmap,project-changelog}.md`, `README.md`. ~1,640 added lines (≈530 Rust, ≈870 docs).

Verified this session: `cargo clippy --all-targets -- -D warnings` clean; `cargo test --workspace --no-fail-fast` all green — 234 passed / 1 ignored, of which 36 come from the **untracked** `tests/edge_cases_probe_verify.rs`, so the committed tree is exactly the claimed 198 + 1 ignored. `tests/verify_report.rs` 11/11. No live Telegram calls possible here. grammers-client 0.10.0 source read from `~/.cargo/registry/.../grammers-client-0.10.0`.

## Overall

The decision layer is the right shape: `verify::report` is pure, side-effect free, unit-tested, and its verdict precedence (size failure ⇒ hash skipped; local issue ⇒ set fails even with zero part failures) is correct and covered. `verified_at` is written only on a genuine hash match and committed per part, so an interrupted `--full` re-runs safely and never records an unverified part. Exit-code propagation is sound: every failing set path ends in `bail!` (`commands/verify.rs:53-55`) or a propagated `Err`, so the process is never zero on a failure.

The two grammers claims in the commit message check out against the vendored source (details below). The problems worth fixing are: one reachable panic on remote data, a fail-fast structure that throws away an entire multi-hour `--full` run on one transient error, `chat_id` being ignored so a config change reports the whole library as missing, and several documentation statements that the code contradicts — including one dropped plan requirement justified by a quote that does not exist.

No critical (data-loss / secret-leak / trust-boundary) defects. No secrets, PII, or URLs reach any error path here; the TMDB-style leak is not repeated (nothing in `verify` touches reqwest).

## grammers 0.10.0 claim verification

| Claim (commit msg / `download_hash.rs:126-131`) | Source | Status |
|---|---|---|
| `DownloadIter::next` does **not** resume after a failed chunk | `client/files.rs:87` `mem::replace(&mut self.variant, Empty)` runs *before* the request; the error arms (`:96`, `:137`) return without restoring `variant`, so the next call hits `:98` → `Ok(None)` | **Confirmed.** The deliberate absence of retry is correct; a retry would hash a truncated prefix. |
| `get_messages_by_id` returns one entry per requested id, in request order | `client/messages.rs:1143` `message_ids.iter().map(\|id\| map.remove(id)).collect()` | **Confirmed.** The `chunk.iter().zip(results)` in `download_hash.rs:112` is sound (length and order guaranteed); duplicate ids in one batch would yield `None` for the second copy, harmless here. |
| 100 ids per call | `MESSAGE_BATCH = 100`, no internal chunking in grammers | Correct. |
| chunked download never buffers a whole part | `MAX_CHUNK_SIZE = 512 * 1024` (`files.rs:31`); loop holds one `Vec<u8>` | Correct — but see **L5** for the perf consequence. |

## Findings (severity ranked)

### High

**H1. Reachable panic on remote data: `document.id()` unwraps.**
`commands/verify.rs:163` calls `document.id()` unconditionally. `Document::id()` is `self.raw.document.as_ref().unwrap()` (`grammers-client-0.10.0/src/media/media.rs:279-286`), and `Media::from_raw` maps **every** `MessageMedia::Document` to `Media::Document` regardless of whether the optional `document` field is present (`media.rs:801-807`). `Document::size()` at `:326` explicitly handles the `None` case, which is the library's own admission that it occurs (TTL-expired / stripped document media).
Scenario: one message in the channel has a `messageMediaDocument` whose document field is absent → `verify --all` panics mid-run instead of reporting that part as failed; every prior set's report is lost with it. This also violates the standard this very commit documents (`docs/code-standards.md:37-40`, "No `unwrap()`/`expect()` on data that crosses a process or network boundary").
Fix (no new dependency needed — `grammers-tl-types` is already a direct dep):
```rust
let doc_id = match document.raw.document.as_ref() {
    Some(grammers_tl_types::enums::Document::Document(d)) => d.id,
    _ => return Ok(report::verify_size(&expected, &ObservedMessage::NoDocument)),
};
```
A dependency-free equivalent is `if document.size().is_none() { return … NoDocument }` before touching `id()`.
Same pattern exists at `upload/transport.rs:126,144` and `commands/rescan.rs:55` (out of scope for this commit, but `rescan` walks *every* message in the channel, so it is the most exposed of the four — worth a follow-up ticket).

**H2. One transient error aborts the whole run and discards every report.**
`commands/verify.rs:82` (`verify_all` → `?`), `:115`, `:169-171`: a `hash_document` failure propagates out of `verify_one_set` and `verify_all`, so `reports` is never printed — the loop at `:44-52` only runs after *all* sets succeeded. Because there is deliberately no retry around `iter_download` (correct, per H-table), a single `FLOOD_WAIT` or dropped connection mid-stream is the *expected* failure mode of `--full`, not an exotic one.
Scenario: `verify --all --full` over a 60 GB library runs for hours, fails on the last part of the last set, prints one `Error:` line, and the user learns nothing about the 40 sets that passed (their `verified_at` rows *were* written, so the state is not lost — only the report is).
Fix: turn a per-part IO error into a `PartVerdict` failure (`failure: Some(format!("download failed: {err}"))`) and continue; print each set's rows/summary as soon as that set finishes rather than accumulating. Keep the non-zero exit.

**H3. `verify` ignores `parts.chat_id` and queries whatever channel the config currently resolves to.**
`verify/mod.rs:49` deliberately drops `chat_id` from `LocalPart` ("no upload-side bookkeeping"), and `commands/verify.rs:111` fetches ids from `tg.channel`. `chat_id` is recorded per part by both the upload path (`upload/pipeline.rs:64`) and `rescan` (`commands/rescan.rs:24-30`) precisely to identify where a message lives, and `docs/mlib-spec-v2.md:200-202` states "message lookup always keys on `(chat_id, message_id)`" — so code and normative spec disagree.
Scenario: the user edits `channel` in `config.toml` (typo, rename, second channel, `MEDIAGRAM_CHANNEL` in a shell), runs `verify --all`, and every part of every set reports "message not found on the channel". The report is indistinguishable from "my library was deleted" and, combined with F-6 below, is the kind of output that provokes a re-upload of a terabyte.
Fix: select `chat_id` in `load_parts`, compare against the resolved channel id (same derivation as `commands/rescan.rs:22-28`), and emit a distinct verdict, e.g. `ObservedMessage::OtherChat { chat_id }` → failure text "recorded in chat {id}, verifying chat {current}" — or skip such parts with a warning rather than claiming they are missing.

### Medium

**M1. `hash_document` never checks how many bytes it actually hashed.**
`verify/download_hash.rs:132-143`. grammers ends the stream when a chunk is shorter than `limit` *anywhere* in the file, and advances `request.offset += request.limit` rather than by `bytes.len()` (`files.rs:112-120`), so a single short mid-stream chunk yields a truncated hash (and, for any following chunk, a misaligned one). The result is reported as `hash mismatch: expected …, got …` — semantically "your data on Telegram is corrupt", which is the most destructive conclusion the tool can offer. This is the same class of bug as the phase-5 `PartStream` short-chunk note.
Fix: accumulate `hashed: u64` and compare against the document/part length before returning; on mismatch return an error such as `bail!("download ended after {hashed} of {expected} bytes")`. Cheap, and it converts a false "corrupt" verdict into an honest "retry this part".

**M2. A failing part keeps its stale `verified_at`.**
`commands/verify.rs:174-176` writes `verified_at` on success and never clears it otherwise; `verify/mod.rs:69-76` has no counterpart. A part verified on 2026-03-01 that fails its size or hash check today still carries `verified_at = <March>` in `library.db`, and that row is what `push-index` snapshots to the channel for future readers (the planned TV app). `render_row` even prints the stale timestamp on the same line as `FAIL` (`report.rs:163-170`), which reads as reassurance.
Fix: in `--full`, on `hash_ok == Some(false)` (and arguably on any size failure) `UPDATE parts SET verified_at = NULL`, or add a `verify_failed_at` column. If the column is meant to be "last time this part was *ever* proven good", say so in `docs/mlib-spec-v2.md` §6 — see **L3**.

**M3. `verify --all` always fails while any set is pending.**
`commands/verify.rs:105-110` + `report.rs:63`: every part of a `pending` set is `NotUploaded` → failure. So `verify --all` exits non-zero whenever an `add` is in flight or was interrupted, i.e. exactly the state `resume` exists to handle. As a cron/health check the command is unusable in that window, and the "N/M part(s) FAILED" output invites the user to think something is broken when nothing is.
Fix: skip sets whose `status != 'complete'` and list them as `set X: pending, skipped` (still verify the local invariant), or gate with an explicit flag. `resolve_set_ids` already has the `sets` row available. When a set id is named explicitly, keeping the current strict behaviour is fine.

**M4. Architecture doc asserts an import boundary this commit breaks.**
`docs/system-architecture.md:150-152`: "The `telegram/` and `upload/transport.rs` modules are the only places that import `grammers_*` crates." Actual importers: `commands/push_index.rs`, `commands/rescan.rs`, `commands/smoke_upload.rs`, `commands/verify.rs`, `telegram/client.rs`, `telegram/retry.rs`, `upload/transport.rs`, `verify/download_hash.rs` — two of which (`commands/verify.rs`, `verify/download_hash.rs`) were added by this commit. Since the sentence is the load-bearing argument for "backend portability", either correct it to the real list (grammers types stay out of `index/`, `media/`, `metadata/`, `mlib-spec`) or make it true.

**M5. Spec says "plain ASCII"; the locked decision and the code say UTF-8. Needs your call, not mine.**
`docs/mlib-spec-v2.md:26` — "Line 2 is one JSON object, minified (no extra whitespace), plain ASCII". `caption_codec::to_text` (`:42-48`) emits `serde_json::to_string` output with no ASCII escaping, so a title like `Amélie` or `君の名は` is written raw UTF-8. `plan.md` → Locked decisions says: *"line 2 is minified **UTF-8** JSON with no entities or custom emoji"*, while `phase-07-…:39` says to state "the ASCII rule". The plan's locked list is authoritative over the phase file, so the doc as written contradicts both the lock and the implementation.
Impact if unaddressed: this is a normative document written for a second implementer; "plain ASCII" invites the assumption that byte length == UTF-16 length, which is precisely the budget bug fixed in phase 1.
Options: (a) doc says "minified UTF-8 JSON; non-ASCII characters appear raw", (b) enforce ASCII in `to_text` via an escaping serializer (changes the wire bytes for existing captions — spec-bump territory). Surfacing, not deciding.

**M6. A plan requirement was dropped and the justification quote does not exist.**
`phase-07-verify-command-and-project-docs.md:16` requires: "resumable (skips parts verified after `--since`)". No `--since` flag exists (`main.rs:38-44` — `set_id`, `--all`, `--full` only). `docs/development-roadmap.md:83-87` explains the omission as "Deliberately kept out of v1 per the phase 7 spec (\"keep it simple; no extra flags\")" — that phrase appears nowhere in the phase file or `plan.md` (grep for `keep it simple` / `no extra flags`: zero hits outside the roadmap itself).
This is a scope reversal of a written requirement backed by a fabricated citation. Either implement `--since` (a `WHERE verified_at IS NULL OR verified_at < ?` filter in `load_parts` plus one clap arg — well under an hour), or get an explicit decision recorded in `plan.md` and fix the roadmap's citation. Do not leave the invented quote in a docs file.

**M7. Unchecked `u64` arithmetic over values that originate in channel captions.**
`commands/verify.rs:63-67` (`total_bytes` sum) and `:97` (`sum_len`) use `+`/`Iterator::sum`. The values come from `parts.byte_length`, read as `i64` then cast (`verify/mod.rs:53-56`), and `rescan` writes them from caption JSON with `caption.part.len as i64` (`index/rescan_parts.rs:80`). A caption carrying `len >= 2^63` round-trips to a huge `u64`, so the sums panic in debug and wrap in release — and a wrapped `sum_len` can make `check_local_invariant` (`report.rs:116-128`) *pass* a set it should fail.
Private channel ⇒ low exposure, but `rescan` is the disaster-recovery path where the index is rebuilt from data the local machine has not validated. Fix: `checked_add`/`saturating_add` in both sums (report "index contains an impossible byte length" on overflow), and validate `part.len`/`part.off` at the `rescan_parts` boundary (`i64::try_from`).

### Low

**L1. Normative example contradicts the spec's own invariant.** `docs/mlib-spec-v2.md:57,60,68`: `"n":18` with `"len":3758096384` and `"total":62914560000`. 62,914,560,000 / 3,758,096,384 = 16.74 → **17** parts; §3 requires `sum(len for all parts) == total`. Copied verbatim from `crates/mlib-spec/tests/caption_roundtrip.rs:30`, so the fixture is wrong too (`part_plan.rs:77-79` correctly asserts 17). Fix both to `"n":17` (and "part 0 of 17" in the prose at `:57`).

**L2. Index caption example key order does not match what the code emits.** `docs/mlib-spec-v2.md:228` shows `{"pushed_at":…,"sets":42,"schema":1}`; `push_index.rs:64-71` builds it with `serde_json::json!`, and `serde_json` without the `preserve_order` feature (workspace `Cargo.toml:13` `serde_json = "1"`, no features) serializes maps in BTreeMap order → `{"pushed_at":…,"schema":1,"sets":42}`. §2 teaches readers that field order is meaningful, so an inaccurate example here is more than cosmetic.

**L3. `verified_at` semantics are undocumented in the normative spec.** It appears in the §6 DDL (`docs/mlib-spec-v2.md:189`) and nowhere in the prose. A second client reading the pushed snapshot cannot tell what a non-null value attests to (per-part hash proven on a given date? set-level? cleared on failure? — currently never cleared, see M2). One sentence in §6 fixes it.

**L4. Doc-id-changed warning is permanent.** `commands/verify.rs:166-176` never refreshes `parts.doc_id`, so once a part is forwarded/re-sent, every future `verify` re-warns. Refreshing `doc_id` after a `--full` hash match (proof the bytes are identical) would let the warning clear itself; refreshing on a mere size match would not be safe. Either do the former or state in the help text that the warning is expected to persist.

**L5. `--full` is 512 KiB-per-round-trip serial.** `iter_download` uses `MAX_CHUNK_SIZE = 512 KiB` (`files.rs:31`) with no concurrency, i.e. **7,168 sequential RPCs per 3.5 GiB part**. grammers' concurrent downloader exists only in `download_media` (fs feature, writes to a path — unusable for streaming hash). `announce_full_cost` (`commands/verify.rs:61-71`) prints bytes but no time estimate; on the phase-1 gate's measured ~25 Mbit/s that is ~20 min per part, ~5.5 h for a 62 GB set. Worth adding an order-of-magnitude ETA to the warning line, and a sentence in the README.

**L6. Minor report-rendering rough edges.** `report.rs:122` casts `row_count as u32` (truncates above 4.29 B rows — theoretical); `summary_line` (`:186-192`) prints `set X: 0/0 part(s) FAILED (local index issue)` when the local check fails with no part rows — correct exit code, confusing line.

**L7. `Tg::connect` runs even when no remote call is needed.** `commands/verify.rs:38`: a set with zero `done` parts produces an empty `ids` list and no RPC, yet a live session is still required. Deferring `Tg::connect` until the first non-empty `ids` would make `verify` usable offline for purely local diagnosis.

**L8. Self-contradictions inside the new docs set.**
- `docs/code-standards.md:15` "Each `commands/*.rs` file exposes exactly one `pub async fn run(...)`" — `commands/push_index.rs` also exposes `push_after_set` (`:35`).
- `docs/code-standards.md:20` / `development-roadmap.md:23` "Every source file stays under 200 lines" — true for `src/**` (max 198), false for `tests/**` (721/692/664/595). Say "every file under `src/`".
- `docs/code-standards.md:134-136` forbids AI-authorship references in commit messages, yet `c5828a3` (the commit that adds the rule) and every recent commit carry a `Claude-Session:` trailer. Either drop the trailer going forward or carve out an explicit exception in the standard.
- `development-roadmap.md:10-20` marks phases 5/6/7 "Complete" while `plan.md` — which `:3-4` names as the source of truth — still has them `in-progress`/`pending`. Use "code-complete, live gate pending" in the Status column or sync `plan.md`.

**L9. Untracked 721-line probe file in the tree (4th occurrence of this pattern).** `crates/mediagram/tests/edge_cases_probe_verify.rs` is untracked but compiles and passes (36 tests, clippy clean). It covers real gaps the committed suite misses — `apply_hash` with `expected_sha256 = None`, non-hex/short/long stored hashes, uppercase-vs-lowercase match, zero/1-byte/3.5 GiB±1 lengths, sum-overflow. Decide explicitly: commit it (preferred — it is the only coverage of the `None`-hash branch) or delete it. Until then "198 tests green" and the tree state disagree.

**L10. Committed test gaps.** `tests/verify_report.rs` has no case for `apply_hash(expected_sha256 = None)` (today: a part with a NULL `sha256` fails with `expected (none recorded)` — reasonable, but unpinned), and none asserting what happens to a pre-existing `verified_at` when the part now fails (M2). Both are one-liners.

## Positive observations

- `verify::report` is genuinely pure: no `Connection`, no grammers types, no clock — `now` is injected. That is what makes the 11 committed tests meaningful without a live session.
- Verdict precedence is right: `apply_hash` early-returns on `!size_ok` (`report.rs:98-100`), so a wrong-size document is never downloaded, and `hash_ok` stays `None` rather than falsely reporting a hash result.
- `eq_ignore_ascii_case` for the hash comparison (`report.rs:101`) matches `set_hash`'s lowercase-normalizing contract.
- `verified_at` is written per part immediately after its own match, in autocommit — an interrupted `--full` leaves a strictly-correct partial state and re-running is cheap and safe.
- The no-retry decision around `iter_download` is not only correct but documented at the call site *and* in `code-standards.md:76-84` with the reason and the "read the vendored source first" rule. That is the right way to record a non-obvious library constraint.
- `ids` is built with `filter(status == "done")` so pending parts cost zero RPCs; batching is bounded; one `get_set`/`load_parts` per set — no N+1 anywhere.
- Error contexts name the operation and the part ("downloading part 3 of set X to hash it"), never a URL, token, or path from config. Nothing here can leak a secret.
- `docs/mlib-spec-v2.md` §6 DDL matches `crates/mlib-spec/src/schema.rs:8-37` verbatim; §2 field table matches `Caption`'s declaration order exactly (`caption.rs:52-77`); §3/§4/§5 match `part_plan.rs:9-11`, `part_name.rs:6-40`, `set_hash.rs:7-13`. The spec is accurate where it matters most.

## Recommended actions

1. Guard `document.id()` before calling it (H1) — and file a follow-up for the three same-shape call sites in `transport.rs`/`rescan.rs`.
2. Make per-part failures non-fatal and print per set as you go (H2).
3. Load and honour `chat_id`; give a cross-chat part its own verdict (H3).
4. Count downloaded bytes and fail loudly on a short stream instead of reporting a hash mismatch (M1).
5. Clear or re-scope `verified_at` when a part fails (M2), and document the column in the spec (L3).
6. Skip non-`complete` sets under `--all` (M3).
7. Decide `--since`: implement or record the cut in `plan.md`; remove the invented quote from the roadmap either way (M6).
8. Doc corrections: grammers import boundary (M4), ASCII-vs-UTF-8 (M5, needs your decision), `n:18` → `n:17` in doc + fixture (L1), index caption key order (L2), the four self-contradictions in L8.
9. `checked_add` in both byte sums; validate caption lengths at the rescan boundary (M7).
10. Commit or delete `tests/edge_cases_probe_verify.rs`; add the two missing `report` tests (L9, L10).

## Plan status recommendation (no plan files edited)

- Phase 7 code tasks: implemented as described, with H1-H3/M1-M3 outstanding. Recommend keeping phase 7 `in-progress` until H1-H3 land.
- `phase-07-…:45-48` correctly leaves the live-gated criteria unticked and says why — the honest pattern flagged in the phase-6 review was followed this time. Criterion `:47` ("spec is sufficient to write a parser") is ticked; L1/L2/M5 are the gaps a parser author would hit, so it is close but not yet earned.
- `plan.md` phase table still shows 5/6 `in-progress`, 7 `pending`; the roadmap says Complete. Lead should pick one representation (L8).

## Metrics

- Committed tests: 11 new (`tests/verify_report.rs`); workspace total 198 passing + 1 ignored (234 with the untracked probe).
- `cargo clippy --all-targets -- -D warnings`: 0 issues. `src/**` max file length 198 lines (limit 200).
- Rust, so no type-coverage figure; no `unwrap`/`expect` on external data in the new code (the H1 panic is inside the callee).

## Unresolved questions

1. **M5** — spec says "plain ASCII", locked decision says "minified UTF-8". Which is normative? (Enforcing ASCII changes caption bytes; documenting UTF-8 does not.)
2. **M6** — is `--since` dropped for v1? The phase file requires it; the roadmap cites a quote that does not exist.
3. **M2/L3** — is `verified_at` "last proven good, ever" or "result of the last verification"? Determines whether a failing part should clear it.
4. **M3** — should `verify --all` skip `pending` sets, or is failing on them intentional?
