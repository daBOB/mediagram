# Project changelog

Dated entries summarizing what shipped, grouped by day. Commit hashes refer
to `main`. Full phase-by-phase detail lives in
`plans/260914-1954-telegram-linux-uploader-mlib-spec-v2/plan.md`'s
"Implementation log" sections.

## 2026-09-14

**Shipped**

- Workspace scaffolding and the `mlib-spec` crate: caption struct + codec
  (marker line, minified JSON, 1,024-UTF-16-unit budget), raw byte-range
  part planning, ≤60-char part file naming grammar, the fallback filename
  parser, `set_hash`, and the `library.db` SQLite schema
  (`5a45684`).
- Telegram client construction on grammers 0.10 (`SqliteSession` +
  `SenderPool` + `Client`), interactive login (phone/code/2FA), `whoami`,
  and a hidden `smoke-upload` debug command (`cbf9610`).
- Media inspection (`ffprobe`), HDR/quality classification, MP4
  trailing-moov detection and faststart remux (`3cdc6dd`).
- TMDB metadata resolution: search, disk-cached HTTP client, interactive
  disambiguation prompt for ambiguous matches (`c034fef`).
- A `lib.rs` library target replacing `#[path]` test includes, so
  integration tests import the crate normally (`a39e1c8`).

**Review fixes** (`30d7a5b`): TMDB API key no longer leaks into `reqwest`
error URLs; unprotected libsql WAL/SHM session sidecars now inherit the
session directory's restricted permissions; a `u64` wrap fixed in the MP4
atom scanner; TMDB's server-side year filter no longer defeats the ±1-year
matching rule; `--imdb` input is normalized (`tt`-prefix, digits-only); the
retry wrapper no longer retries permanent RPC errors; a failed remux no
longer leaves partial output on disk; empty TMDB search result pages are
no longer cached; `Config`'s `Debug` impl redacts secrets instead of
deriving it.

**Phase-1 live gate — PASSED 2026-09-15 00:00** (recorded same day as the
work, timestamped after midnight): a real 3,758,096,384-byte (3.5 GiB)
file uploaded to a Premium account's private channel, sent, and deleted
successfully in 1,187 s (≈3.2 MB/s / ≈25 Mbit/s, upstream-bound), with no
`FLOOD_WAIT` and no retries observed. Confirms 3.5 GiB parts are safe on
this account and that `throttle_ms = 0` is a safe default. Also confirmed
in this session: interactive login cannot run through a `!`-prefixed
in-session shell (no TTY) and must be run in a real terminal.

Test count at end of day: 131 (unit + fixture integration + probe suites,
phases 1-4).

## 2026-09-15

**Shipped**

- Streaming part upload pipeline with resume: a hashing byte-range reader
  (`part_reader`), the `Transport` trait plus its Telegram implementation,
  adoption-based resume (scans the last `3 × part_count` channel messages
  and matches parsed captions instead of re-uploading), and the `add`
  command's full inspect → resolve → remux → plan → upload → index → push
  flow (`0ca2392`).
- Channel title resolution made case-insensitive and extended to
  supergroups, not just broadcast channels (`5757a59`).
- Index push and rescan: `push-index` (WAL checkpoint → `VACUUM INTO` →
  upload → pin → unpin previous), and `rescan` (pages the whole channel
  history, folds `#mlib v=` captions into `sets`/`parts` additively)
  (`b1373e5`).
- `verify` command (metadata check by default, `--full` re-download +
  streamed SHA-256 hash per part) plus this documentation set (this
  change).

**Review fixes**

- Upload/resume (`be70497`): temp-file deletion now matches by suffix
  instead of an assumption that could delete the wrong file; source file
  size is re-validated before resuming a set (catches a source that
  changed since `add`); a retry-induced duplicate send is now caught by
  the adoption scan instead of silently producing two messages for the
  same part.
- Index push/rescan (`3231740`): a previously-failed unpin is now retried
  on the next push (`stale_index_message_id`); concurrent pushes use a
  per-process snapshot temp file name so they cannot clobber each other;
  rescan summary counts (`sets_complete`/`sets_incomplete`) are no longer
  double-counted across batches; the unparsed-caption counter is now
  populated; push failures carry operation context.
- `3bd76f9`: rescan's part-row helpers moved into their own module to
  respect the 200-line-per-file limit.
- `723a423`: config probe tests that mutate `MEDIAGRAM_*` process
  environment variables now run serialized, removing a source of
  parallel-test flakiness.

**Test counts:** 161 after phase 5's review fixes, 187 after phase 6, 198
passing (+1 `#[ignore]`d live test) after phase 7's `verify` command, 240
after phase 7's review and test round.

## 2026-09-15 (phase 7 review round)

**Fixed**

- Reachable panic on remote data: `grammers`'s `Document::id()` unwraps an
  optional field that `Media::from_raw` never checks, so a message carrying
  stripped or expired document media could panic a run. `telegram/document.rs`
  is now the only accessor, used by `verify`, `rescan` and `upload/transport`.
- `verify --full` no longer discards a long run: a download failure becomes
  a per-part verdict and each set prints as it completes, instead of every
  result being withheld until all sets succeeded.
- `verify` honours `parts.chat_id` and names both chats when a part was
  recorded elsewhere, instead of reporting "message not found", which reads
  as data loss and invites a re-upload.
- A truncated download reports "download ended after N of M bytes" rather
  than a hash mismatch, which read as corruption on Telegram.
- A failing part's stale `verified_at` is cleared, so the index snapshot
  pushed to the channel cannot advertise an old success beside a failure.
- `verify --all` skips sets that are still uploading rather than failing
  while `resume` still has work; byte sums use `checked_add`.
- An empty `tmdb_key = ""` in `config.toml` now loads as absent, so `add`
  gives the "set a key or pass --manual" message instead of a late TMDB
  authentication failure.

**Added**

- `verify --since <unix>`: skips parts already verified at or after the
  timestamp, making an interrupted `--full` sweep resumable. It was a
  written phase-7 requirement; the roadmap had recorded it as cut, citing a
  quote that appears in no plan file.
- 36 edge-case probes for the verify decision layer plus 6 new unit tests.

**Docs**

- Caption line 2 is documented as UTF-8, matching the plan's locked
  decision and `caption_codec::to_text`; the spec previously said ASCII.
- Spec example part count corrected (`n:18` → `n:17`, the value the total
  implies) in both the document and the fixture it is copied from; index
  caption key order corrected to what `serde_json` actually emits;
  `verified_at` semantics documented as "result of the last verification".
- The grammers import boundary, the one-`run`-per-command rule, the
  200-line rule's scope, and the commit-trailer rule now describe the code
  as it is.

**Status:** phases 1-7 are code-complete. The phase-1 live smoke gate
above passed; the remaining live acceptance gates (live 3-part `add`, kill
mid-upload + `resume`, `verify --full` hash match and tamper detection,
`rescan` reproduction) need a real terminal against the live channel. They
do **not** need a TMDB key: `add --manual` takes metadata by hand. See
[`docs/development-roadmap.md`](development-roadmap.md#open-live-gates).
