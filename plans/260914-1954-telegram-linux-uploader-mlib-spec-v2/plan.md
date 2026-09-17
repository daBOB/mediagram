---
title: "mediagram Linux uploader and mlib spec v2"
status: in-progress
created: 2026-09-14
source: plans/reports/brainstorm-260914-1954-telegram-linux-uploader-and-mlib-spec-v2-report.md
blockedBy: []
blocks: []
---

# mediagram: Linux uploader + mlib caption/index spec v2

Rust CLI that stores a personal video library in ONE private Telegram channel (Premium, 4 GB cap): raw-byte split into 3.5 GiB parts, structured `#mlib v=2` caption on every part, local SQLite `library.db` canonical + pushed as pinned document. Future Android TV app consumes the same spec crate.

Stack: Rust 1.98 edition 2024, grammers-client 0.10.0, rusqlite (bundled), clap, tokio, reqwest, ffmpeg/ffprobe via subprocess.

## Phases

| # | Phase | Status | Priority | Effort | Depends on |
|---|-------|--------|----------|--------|------------|
| 1 | [Workspace and mlib-spec crate](phase-01-workspace-and-mlib-spec-crate.md) | completed | P1 | 1d | - |
| 2 | [Config and Telegram auth](phase-02-config-and-telegram-auth.md) | completed | P1 | 0.5d | 1 |
| 3 | [Media inspect and faststart remux](phase-03-media-inspect-and-faststart-remux.md) | completed | P2 | 0.5d | 1 |
| 4 | [TMDB metadata resolution](phase-04-tmdb-metadata-resolution.md) | completed | P2 | 1d | 1 |
| 5 | [Streaming part upload with resume](phase-05-streaming-part-upload-with-resume.md) | complete | P1 | 2d | 2,3,4 |
| 6 | [Index push and rescan](phase-06-index-push-and-rescan.md) | complete | P2 | 1d | 5 |
| 7 | [Verify command and project docs](phase-07-verify-command-and-project-docs.md) | complete | P2 | 1d | 6 |

Phases 2, 3, 4 are independent of each other and can run in parallel after phase 1.

## Locked decisions (from brainstorm, do not re-litigate)
- One private channel; no per-series channels.
- grammers `upload_stream` over a seeked `File.take(len)`: parts never written to disk.
- Part size 3,758,096,384 B (3.5 GiB), config-overridable, must be a multiple of 1 MiB.
- Per-part sha256 computed during upload; `set_hash = sha256(concat(part_sha256 hex strings in idx order))`. No whole-file hash.
- Full caption record on EVERY part; `part.off` is authoritative for order; set id is a ULID minted at `add` time.
- Local `library.db` is canonical; channel copy is a snapshot pushed after each completed set; `rescan` rebuilds from captions (DR only).
- Ambiguous TMDB hits prompt interactively; `--manual` allows non-TMDB titles; explicit `--tmdb/--tvdb/--imdb` never prompts.
- TMDB only in v1; TVDB ids stored when supplied by flag, never fetched (confirmed in validation).
- Parts table stores the Telegram document id as `doc_id INTEGER` (replaces `file_unique_id TEXT` from the brainstorm).
- `resume` adopts unrecorded parts by scanning the last 3 × part_count channel messages and parsing captions, not by server text search.
- Index is pushed after every completed set; `--no-push` exists only for explicit bulk sessions.
- Caption budget 1,024 UTF-16 code units, as Telegram counts (Premium-independent); line 2 is minified UTF-8 JSON with no entities or custom emoji.

## Phase-1 gate — PASSED 2026-09-15 00:00
Live smoke upload of a 3,758,096,384-byte random file to the private channel "Mediagram" (Premium account, release build):

| Measure | Result |
|---|---|
| Upload + send + delete | success, message id 2, deleted |
| Elapsed | 1,187 s (≈3.2 MB/s, ≈25 Mbit/s, upstream-bound) |
| FLOOD_WAIT / retries | none observed |
| Errors | none |

Conclusions: 3.5 GiB parts are safe on this account; no throttling seen for a single 7,168-part upload, so `throttle_ms` stays 0 by default and the retry wrapper remains the only defence. Login via `!` in-session fails (no TTY); login must run in a real terminal once.

Before building phases 5-7 in full, run a real single-part upload smoke test against the private channel (phase 2 success criterion) to confirm the 4 GB enforcement and FLOOD_WAIT surfacing assumptions.

## Key dependencies
- Telegram api_id/api_hash (my.telegram.org) and a private channel where the account is admin.
- TMDB API key (free tier).
- ffmpeg + ffprobe on PATH (present on host).

## Success (whole plan)
See phase 7 success criteria: 10 GB MKV → 3 parts; kill -9 mid-part then `resume` finishes with no duplicates; `verify --full` matches all hashes; `rm library.db && rescan` reproduces rows; MP4 with trailing moov is remuxed so part 0 holds moov; `cargo test` green; source files under 200 lines.

## Validation Log

### Session 1 — 2026-09-14

### Verification Results
- Tier: Full (7 phases); claims checked: 24 (grammers-client/-mtsender/-session 0.10.0 crate sources, host toolchain)
- Verified: 18 | Failed: 4 | Unverified: 2
- Failures (all corrected in phase files, marked `<!-- Updated: Validation Session 1 -->`):
  - phase 2: `Client::connect(Config)`, `Session::load_file_or_create`, `save_to_file` do not exist in 0.10.0 → `SqliteSession::open` + `SenderPool::new` + `Client::new(handle)` (examples/echo.rs:60-67)
  - phase 2: `get_chat` admin check does not exist → requirement documented, not checked
  - phase 5: `iter_messages(..).query()` does not exist; `query` is on `search_messages` (messages.rs:392/520) → adopt now scans history instead
  - phase 7: `Downloadable` is a trait, not an enum (media/downloadable.rs:12; `impl Downloadable for Document` media.rs:446)
- Unverified (need the live smoke test): exact 4 GB enforcement boundary; FLOOD_WAIT frequency during multi-GB uploads
- Verified highlights: `upload_stream` AsyncRead + SaveBigFilePart workers (files.rs:388-420), `Document::id() -> i64` and `size()`, `pin_message`/`unpin_message`, `RpcError{name,value}` FLOOD_WAIT parsing (mtsender errors.rs:79-100), ffmpeg/ffprobe/cargo present on host

### Decisions
| # | Question | Decision |
|---|----------|----------|
| 1 | Apply grammers 0.10 API corrections | Apply all |
| 2 | Resume adoption strategy | Scan last 3 × part_count messages, parse captions |
| 3 | Document identity column | Rename `file_unique_id TEXT` → `doc_id INTEGER` |
| 4 | Index push cadence | After every completed set |
| 5 | TVDB in v1 | TMDB-only; `--tvdb` stores id verbatim |

### Phase propagation
- phase-01: schema amendment (doc_id, verified_at)
- phase-02: client construction, retry matching, deps, admin check removed
- phase-05: adopt strategy, doc_id identity, risk row removed
- phase-06: push/unpin steps concretized
- phase-07: download via Downloadable trait

### Whole-Plan Consistency Sweep
- Searched all plan files for `file_unique_id`, `Client::connect`, `load_file_or_create`, `save_to_file`, `get_chat`, `Downloadable::`, `.query(`, `search channel`: zero stale occurrences after propagation (see sweep command output in session).
- Brainstorm report `plans/reports/brainstorm-260914-1954-...` still shows the pre-validation schema; it is a historical record, plan files are authoritative.
- Unresolved contradictions: none.

### Implementation log — phases 2-4 (2026-09-14)
- Implemented in parallel by three worktree agents on disjoint modules; merged fast-forward, then a `lib.rs` target replaced the `#[path]` module includes in integration tests.
- Combined review found: TMDB api_key in reqwest error URLs (critical), unprotected libsql WAL/SHM sidecars, u64 wrap in the atom scanner, server-side year filter defeating the ±1 rule, `--imdb` not normalised, permanent RPC errors retried, partial remux output on failure, empty search pages cached, Debug on Config. All fixed in commit `30d7a5b`.
- Deferred (documented, not blocking): `--tmdb` kind inference calls `/movie/{id}` for a show when no season/episode markers are given (user passes `--season/--episode`, which phase 5's `add` requires for `t=ep`); `test_fixtures` compiled into the release binary; `with_retry` around `send_message` is not idempotent, so phase 5 must scan-before-resend as already planned.
- Test count after phases 1-4: 131 (unit + fixture-based integration + probe suites). Reports: `reports/code-reviewer-260914-2203-*.md`, `reports/tester-260914-2203-*.md`.

### Implementation log — phase 5 (2026-09-15)
- Built by a worktree agent, merged in `565d5e9`; review found three high-severity resume edge cases (temp deletion by suffix, unvalidated source size, retry-induced duplicate sends), fixed in `be70497`. Tester added 18 probes, no defects. 161 tests. Status stays in-progress until the live 3-part `add` runs (needs `tmdb_key`).

### Implementation log — phase 6 (2026-09-15)
- Built by a worktree agent, merged in `b1373e5`. Review: no data-loss defects; fixed stale-pin retry, per-process snapshot temp name, double-counted rescan summaries, unparsed-caption counter, push-failure context. Decision recorded: rescan is additive. Tester added 18 probes, no defects. 187 tests. Live push/unpin criteria wait for the live `add` (needs `tmdb_key`).

### Implementation log — phase 7 (2026-09-15)
- Built by a worktree agent, merged in `c5828a3`. Review + tester rounds ran
  afterwards (`reports/code-reviewer-260915-1915-*`, `reports/tester-260915-1915-*`):
  36 edge-case probes added, no defects from the probes; the review found
  3 high, 7 medium, 10 low, no critical.
- Fixed in this round: `Document::id()` panics on a document-media message
  whose document field is absent (guarded centrally in
  `telegram/document.rs`, applied to `verify`, `rescan` and `transport`);
  one unreachable part or transient download error no longer discards a
  whole `--full` run (per-part verdicts, per-set printing); `verify` now
  honours `parts.chat_id` and reports a cross-chat part as such instead of
  "missing"; a truncated download fails loudly instead of masquerading as a
  hash mismatch; a failing part's stale `verified_at` is cleared; `--all`
  skips sets that are still uploading; byte sums are checked for overflow.
- `--since` was implemented rather than cut: the phase file required a
  resumable `--full`, and the roadmap's justification for dropping it
  quoted text that does not exist in any plan file. Quote removed.
- Decisions recorded: caption line 2 is **UTF-8** (plan lock wins over the
  phase file's "ASCII rule" wording, and matches `caption_codec::to_text`);
  `verified_at` means "result of the last verification", so it is cleared on
  failure rather than kept as a high-water mark.
- Tests 240 passing + 1 ignored; clippy and fmt clean; every `src/` file
  under 200 lines. Live gates unchanged: still need the real channel run.

## Open live gates (blocked on a real terminal, not on TMDB)
`add --manual` takes metadata by hand and needs no TMDB key, so the
acceptance run is not blocked by the empty `tmdb_key` in `config.toml`. It
does need a real TTY (manual entry prompts) and roughly an hour of upstream
for a 10 GB file at the measured 3.2 MB/s.
