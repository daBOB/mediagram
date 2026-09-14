# Brainstorm: mediagram Linux uploader + mlib caption/index spec v2

Date: 2026-09-14 | Status: agreed, ready for /ck:plan
Scope this round: Linux uploader CLI + shared spec crate. Android TV player = later round, consumes spec.

## 1. Problem
Store a personal video library in ONE private Telegram channel (Premium account, 4 GB cap) so a future Android TV app streams it via TDLib. Files >4 GB must be raw-byte split; every message must carry machine-readable metadata; a pinned SQLite index is the fast-path source of truth. Need a Linux tool that does split + upload + caption + index, with TMDB lookup, ffprobe inspection, faststart remux, resume, verify.

## 2. Decisions (user-confirmed)
| Decision | Choice | Why |
|---|---|---|
| Round scope | Uploader + spec crate | TV app separate project; spec must exist first |
| TG stack | Rust + grammers-client 0.10.0 | pure Rust, static binary, upload_stream(AsyncRead) → stream byte ranges, no split parts on disk |
| Account | Premium | 4 GB cap → 3.5 GiB parts (3,758,096,384 B, 1 MiB aligned) |
| Uploader jobs | TMDB/TVDB lookup, ffprobe, faststart remux, resume + verify | all in |
| Hashing | per-part sha256 during upload; `set_hash = sha256(concat(part_sha256s))` | one pass, resumable verify. Whole-file sha256 DROPPED from spec |
| Ambiguity | interactive pick list; `--manual` for non-TMDB titles; explicit `--tmdb/--tvdb/--imdb` skips prompts | scriptable + safe |
| Spec | lock v2 now, doc in docs/ | uploader is sole writer |
| State model | local `library.db` canonical; channel copy = pushed snapshot; captions = DR layer | single writer, no consistency problem |
| Caption placement | full record on EVERY part | self-healing even if part 0 lost |
| Topology | one private channel (from prior doc) | 500-chat / 50-per-day limits kill per-series channels |

## 3. Verified facts (grammers-client 0.10.0 source, crates.io tarball)
- `Client::upload_stream<S: AsyncRead+Unpin>(&mut S, size: usize, name: String) -> Uploaded` — files.rs:388
- >10 MB → `SaveBigFilePart`, `WORKER_COUNT` concurrent part workers, `MAX_CHUNK_SIZE = 512*1024`, `total_parts = ceil(size/512K)`. No hard size cap. 3.5 GiB = 7168 parts < 8000 (4 GiB / 512 KiB).
- `InputMessage::document(Uploaded)`, `.mime_type()`, `.attribute()`, `.text()/.markdown()` for caption — message/input_message.rs
- `send_message`, `edit_message`, `pin_message`, `get_pinned_message`, `get_messages_by_id` — client/messages.rs
- Auth: `request_login_code`, `sign_in`, `check_password` (2FA). Session via grammers-session file.
- Host has cargo, rustc 1.98.1, ffmpeg, ffprobe.

Unverified → smoke test in phase 1: (a) 4 GB enforced as 4e9 vs 4 GiB (3.5 GiB dodges either); (b) FLOOD_WAIT surfacing from grammers during multi-GB upload; (c) caption text length limit as sent via grammers (design for 1,024).

## 4. Approaches evaluated
A) **Rust + grammers, stream byte ranges, SQLite local canonical** — CHOSEN. Single binary, no temp parts, one toolchain shared with future UniFFI spec crate.
B) Rust + tdlib-rs — same lib as TV app, TDLib handles retries/file refs, but needs native TDLib build, local TDLib DB, and either split parts on disk (2x space) or inputFileGenerated plumbing. Rejected: heavier for no gain on the uploader side.
C) Python + Telethon — fastest to write, mature; rejected: second toolchain, user is Rust/Kotlin.
State: captions-canonical + rebuild-on-scan vs local-DB-canonical + push snapshot → chose latter (single writer; rescan kept as DR command).

## 5. Final design

### 5.1 Workspace
```
mediagram/
  Cargo.toml            # workspace
  crates/mlib-spec/     # caption types, part block, filename fallback grammar, sqlite schema+migrations. NO telegram deps. Future UniFFI target.
  crates/mediagram/     # CLI: auth, inspect(ffprobe), remux(ffmpeg faststart), metadata(tmdb), upload(grammers), index(rusqlite bundled), verify
  docs/mlib-spec-v2.md  # normative spec
```
Files <200 lines each per dev rules; one module per concern.

### 5.2 CLI
```
mediagram login                                  # code + optional 2FA, session file in XDG config
mediagram add <file> [--tmdb ID|--tvdb ID|--imdb ID] [--season N --episode N|--abs N] [--variant S] [--manual] [--no-remux]
mediagram resume                                 # finish pending sets
mediagram push-index                             # upload library.db, pin, unpin previous
mediagram verify <set-id> [--full]               # default: metadata + part count/size; --full: download+hash every part
mediagram rescan                                 # rebuild library.db from channel captions (DR)
```
Config: api_id/api_hash, channel id, tmdb key, part_size (default 3.5 GiB), via config file + env.

### 5.3 `add` pipeline
1. ffprobe → container, duration, vcodec, acodec, q (height→2160p/1080p…), hdr (DV/HDR10/SDR from side data), alang[], slang[]
2. Resolve IDs: explicit flag → TMDB `/find` or `/movie|/tv/{id}` (+external_ids); else filename fallback grammar → TMDB search → prompt if >1 hit; `--manual` → no ids
3. Remux: if MP4 and moov not at front → `ffmpeg -c copy -movflags +faststart` to temp; MKV untouched
4. Part table: n = ceil(total/part_size); off/len per part; ULID set id
5. Insert set `pending` + parts `pending` into local DB
6. Per part: `File::seek(off)` + `.take(len)` wrapped in hashing reader → `upload_stream(name="<Title (Year)[ - sNNeYY]>.<ext>.pNNN")` → `send_message(channel, document + caption)` → store message_id, file_unique_id, sha256 → part `done`
7. All parts done → compute set_hash → set `complete` → `push-index`
Crash anywhere → `resume` reads DB state. Retry wrapper handles FLOOD_WAIT (sleep exactly the reported seconds) + transient net errors with backoff.

### 5.4 Caption spec v2 (line 1 marker, line 2 minified JSON, rest human)
```
#mlib v=2
{"t":"movie","ids":{"tmdb":693134,"imdb":"tt15239678"},"title":"Dune: Part Two","year":2024,"q":"2160p","hdr":"DV","container":"mkv","vcodec":"hevc","acodec":"truehd","alang":["en","de"],"slang":["en"],"dur":9960,"variant":null,"set":"01JQ8F2K9M4XZ","part":{"i":0,"n":18,"off":0,"len":3758096384,"sha256":"<part hash>"},"total":62914560000}
🎬 Dune: Part Two (2024) • 4K DV • part 1/18
```
Episode: `"t":"ep","show":..,"s":2,"e":1` or `"e":[1,2]`; anime: `"abs":1075,"s":null,"e":null`; specials `"s":0`.
Rules: line 2 plain ASCII JSON, no custom emoji/entities; every part carries full record; `off` authoritative for order; budget ≤1,024 chars; single-part files still have `part:{i:0,n:1}`.

### 5.5 SQLite index (library.db, also the local state DB)
```sql
CREATE TABLE sets(set_id TEXT PRIMARY KEY, kind TEXT, tmdb INT, tvdb INT, imdb TEXT, show TEXT, title TEXT, year INT,
  season INT, episode TEXT, abs INT, quality TEXT, hdr TEXT, container TEXT, vcodec TEXT, acodec TEXT, alang TEXT, slang TEXT,
  duration INT, variant TEXT, group_key TEXT, total INT, part_count INT, set_hash TEXT, status TEXT, created_at INT, spec_version INT);
CREATE TABLE parts(set_id TEXT, idx INT, byte_offset INT, byte_length INT, chat_id INT, message_id INT, file_unique_id TEXT,
  sha256 TEXT, status TEXT, PRIMARY KEY(set_id, idx));
CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);   -- schema_version, last_push_at
```
Playable invariant (TV app + verify): status='complete' AND count(parts done)=part_count AND sum(byte_length)=total.

## 6. Risks
| Risk | Mitigation |
|---|---|
| FLOOD_WAIT / account limits under multi-GB bulk upload | honor wait seconds, cap concurrency (grammers WORKER_COUNT), `--throttle` option, one set at a time |
| 4 GB enforcement ambiguity | 3.5 GiB parts; smoke test 1 part before building rest |
| grammers less battle-tested than Telethon | pin 0.10.0, integration test uploading 1 real part on day 1; fallback = tdlib-rs (same spec crate, only upload module swaps) |
| Caption >1,024 chars on long titles/many langs | enforce budget in mlib-spec serializer; truncate human line first, never JSON |
| Pinned index staleness across devices | push after every completed set; TV app compares `meta.last_push_at`; `rescan` rebuilds |
| Uploaded-but-not-recorded part (crash between send and DB write) | idempotent resume: before re-uploading part i, search channel for caption `set`+`part.i`; adopt if found |
| Temp space for faststart remux | needs 1x file size temp; `--no-remux` escape; MKV needs none |
| Legal/ToS | out of scope; tool is content-neutral |

## 7. Success criteria
- `add` on a 10 GB MKV → 3 parts in channel, captions parse, library.db complete, pinned doc updated
- kill -9 mid-part → `resume` finishes without duplicate parts
- `verify --full` re-downloads and matches all part hashes
- `rm library.db && rescan` reproduces identical rows (minus timestamps)
- 1080p MP4 with moov at end → remuxed, part 0 contains moov
- all crates compile, `cargo test` green, files <200 lines

## 8. Next steps
1. /ck:plan from this report → phases: workspace+spec crate, auth+config, inspect+remux, metadata, upload+resume, index push/rescan, verify, docs
2. Phase 1 gate: real single-part upload smoke test against the private channel
3. Later round: Android TV app consuming mlib-spec (UniFFI) + VirtualConcatDataSource

## Unresolved questions
- TVDB API v4 requires a subscription key; is TMDB-only acceptable for v1 (TVDB id still stored if supplied manually)?
- Should `push-index` gzip library.db? (few MB either way; default no)

## Post-validation note (2026-09-14)
Both open questions resolved in plan validation: TMDB-only v1; no gzip on push. Schema amendment: `parts.file_unique_id TEXT` → `parts.doc_id INTEGER`. Authoritative plan: `plans/260914-1954-telegram-linux-uploader-mlib-spec-v2/plan.md`.
