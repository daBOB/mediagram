# Project changelog

Dated entries summarizing what shipped, grouped by day. Commit hashes refer
to `main`. Full phase-by-phase detail lives in
`plans/260914-1954-telegram-linux-uploader-mlib-spec-v2/plan.md`'s
"Implementation log" sections.

## 2026-09-17

**Shipped**

- `mediagram edit <set-id>`: correct a set's metadata without re-uploading
  its bytes. Every part carries the whole record, so a correction rewrites one
  caption per part; `--refresh` asks the provider again in the configured
  language, and `--dry-run` shows the change first. The channel is written
  before the index, so a run that dies between them leaves `rescan` able to
  reconcile from the side that now holds the truth.
- `tmdb_language`: TMDB answers in English unless asked, so a German library
  got "Forsaken" where the file said "Verlassen". Added ahead of the disk
  cache, since the cache keys on the query and a language behind it would
  leave two languages sharing one entry.
- TMDB v4 read tokens (`Authorization: Bearer`) alongside v3 API keys. Sent
  the wrong way a read token answers 401 with nothing to say why.
- Caption v4 carries the folders a set came from, so a course nesting one to
  four levels deep keeps its shape. Schema v3 stores it; schema v4 adds an
  assets table for subtitles and summaries.
- A Bun player backend and web UI: catalog, HTTP Range streaming over a set's
  concatenated parts, and shelves for Movies, Series and Tutorials.

**Fixed**

- `SPEC_VERSION` had drifted from the caption marker, which would have put the
  wrong version in every published package. A test now pins them together, as
  another pins the player's expected schema to the uploader's.

## 2026-09-16

**Shipped**

- `mediagram serve`: a local HTTP API a player can use. `GET /sets` lists what
  `PLAYABLE_SQL` matches, with the codecs a browser needs to decide between
  direct play and a transcode. `GET /sets/{id}/stream` serves a set's parts as
  one virtual file with Range support, seeking into the right Telegram message
  by skipping 512 KiB chunks rather than downloading from zero.
- `db::open_read_only`: the serving process never checkpoints or migrates the
  uploader's index.
- `serve_addr` in config, loopback by default. The API has no authentication
  of its own; exposing it is a later, deliberate step.

Verified live against the channel: five 2,000-byte ranges of a
7,011,563,463-byte two-part film — including one crossing the part boundary —
came back byte-identical to the local source file, as did a 400 MiB slice; a
whole one-part lesson matched its recorded part hash; seeks anywhere in the
6.5 GB file cost 0.15-0.21 s; memory moved 164 kB over 800 MiB streamed; and
`library.db` was byte-identical throughout, including after shutdown.

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

## 2026-09-15 (prebuilt metadata package)

**Added**

- `mediagram export-package [--publish] [--dry-run] [--out <dir>]`: copies
  the index, gathers posters from the TMDB responses already cached on disk,
  writes a manifest, packs a gzipped tar and encrypts it with AES-256-GCM.
  With `--publish` it hands the package and then its pointer to a configured
  command.
- `docs/mlib-package-v1.md`, the normative format: layout, manifest, pointer,
  cipher framing, reader algorithm and security model. Its examples are
  generated by a test, so the document cannot drift from the code.
- Config: `package_key`, `publish_cmd`, `publish_base_url`. The key is
  redacted from `Debug` like the API hash and TMDB key.

**Design points worth remembering**

- The pointer's five identifying fields are the cipher's associated data, so
  an archive replayed under an edited pointer fails its tag. The four
  download fields cannot be authenticated, because one of them is the digest
  of the ciphertext the tag protects; a reader must therefore never decide
  "I already have this" from `sha256`.
- Publishing runs argv directly, never a shell, because a shell reports the
  exit status of the last element of a pipeline and would call a failed
  upload a success.

**Fixed**

- The export wrote to `library.db`. Copying checkpointed the write-ahead log
  first, which folds pages into the main file and changes it. Only visible
  with a second connection open, which is the case the design exists for.
  The checkpoint is gone and the connection opens read-only.
- The reader's size ceiling was only checked against an estimate that
  over-counts the index and under-counts posters; it is now checked against
  the bytes actually produced.
- `poster_key_is_valid` had no caller; it now gates every key that reaches a
  path. Poster downloads have a timeout and a size cap. Packing skips
  symlinks that would otherwise pull outside files into the package.

## 2026-09-15 (tutorials, and a parser that validates)

**Added**

- Caption `v3`: a third kind `tut` for course lessons, plus `chap` (chapter
  title) and `cid` (a general collection id for sets with no provider id).
  Course, chapter and lesson reuse `show`, `s` and `e`, so nothing downstream
  needed a second vocabulary. `v2` captions stay readable and are never
  rewritten.
- `mediagram add-course <dir>`: subdirectories are chapters, videos inside
  them are lessons, leading digits are numbers and the rest is the title.
  `--dry-run` shows every inference before anything uploads. Re-running skips
  finished lessons by identity (collection id plus the two numbers), so it
  survives renaming or moving the folder, and reports unfinished ones for
  `resume` instead of uploading them twice.
- `add` gained `--course`, `--cid`, `--chapter`, `--chap` and `--lesson`. The
  tutorial path never contacts TMDB, which has no courses, so it needs no key.
- Schema version 2: `sets.chap`, with `group_key` finally carrying the
  collection id.

**Changed**

- Migrations are applied by version in one transaction instead of replaying
  every statement on each open. The old shape relied on every statement being
  `CREATE ... IF NOT EXISTS`, which cannot express adding a column.

**Fixed**

- The caption parser validated nothing, so any message in the channel could
  introduce an arbitrary `set` id, including one containing path separators,
  which `rescan` would write into the index as a primary key. `set`, part
  indices and lengths are now checked at the boundary. `sha256` deliberately
  is not: it never reaches a path, and refusing it would make rescan drop a
  part it could otherwise recover.
