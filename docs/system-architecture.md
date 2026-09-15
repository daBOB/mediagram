# System architecture

`mediagram` is a Rust CLI (edition 2024) that uploads a personal video
library into one private Telegram channel and keeps a local SQLite index of
it. It is split into two crates so the wire format can be reused by other
clients (see [§7](#7-backend-portability)).

## 1. Crates

```
crates/
├── mlib-spec/   caption, part-plan, filename grammar, index schema — pure
│                data + parsing, no IO, no Telegram dependency
└── mediagram/   the CLI: Telegram client, media inspection, TMDB lookup,
                 upload pipeline, local index, verify
```

`mediagram` depends on `mlib-spec`; nothing depends on `mediagram`. The
wire format itself is documented normatively in
[`docs/mlib-spec-v2.md`](mlib-spec-v2.md); this document covers how the CLI
is put together around it.

## 2. Module map (`crates/mediagram/src`)

```
main.rs            clap CLI surface; parses args, loads config, dispatches
lib.rs             re-exports every module below for the binary and tests
config.rs          TOML config + MEDIAGRAM_* env overrides, secret redaction
paths.rs           XDG config/data directory resolution

commands/          one module per subcommand, each exposing `run(...)`
  add.rs             inspect → resolve → remux → plan → upload → index → push
  resume.rs          finish every set left `pending`
  push_index.rs       snapshot + upload + pin library.db
  rescan.rs          rebuild the index from channel captions (disaster recovery)
  verify.rs          metadata check, or (--full) re-download + hash
  login.rs / whoami.rs / smoke_upload.rs
  args.rs            `add`'s clap argument struct

media/             ffprobe inspection, HDR/quality classification,
                   MP4 trailing-moov detection and faststart remux
metadata/          TMDB search/lookup, disk-cached HTTP, interactive prompt
                   for ambiguous matches
upload/            hashing byte-range reader (part_reader), the Transport
                   trait + its Telegram implementation, the resumable
                   per-set pipeline, and adoption (resume-without-reupload)
index/             library.db: schema open/migrate, sets/parts CRUD,
                   rescan folding, snapshot/vacuum
telegram/          grammers client construction + login flow, retry policy
verify/            report (pure verdict logic) + download_hash (Telegram
                   download → SHA-256 streaming)
```

Every source file stays under 200 lines; a module that would grow past
that is split (e.g. `media/mp4_atoms.rs` carries the atom-scanning detail
out of `media/remux.rs`, `verify/report.rs` carries the decision logic out
of `commands/verify.rs`).

## 3. `add`: inspect → resolve → remux → plan → index → upload → push

```
file ──▶ inspect (ffprobe) ──▶ resolve (TMDB / --manual / explicit ids)
                                        │
                     ┌──────────────────┘
                     ▼
        faststart remux (MP4 with trailing moov only)
                     │
                     ▼
        plan_parts (raw byte split, 3.5 GiB default)
                     │
                     ▼
   insert `sets` (pending) + `parts` (pending) rows, in one transaction
                     │
                     ▼
   upload::pipeline::run_set: for each pending part, adopt-or-upload,
   mark_done; once every part is `done`, compute set_hash, mark `complete`
                     │
                     ▼
        push_index (unless --no-push): snapshot + pin
```

`inspect` runs `ffprobe` to read container/codec/duration/language tracks
and classifies quality/HDR from stream metadata. `resolve` turns a file
name plus any explicit `--tmdb/--tvdb/--imdb` flags into provider ids and
titles: an explicit id never prompts, `--manual` skips TMDB entirely, and
an ambiguous filename match prompts interactively. The remux step only
touches MP4s whose `moov` atom trails `mdat` (detected by scanning atom
headers, not by re-encoding) — MKV and already-faststart MP4 files pass
through untouched. Every part carries the caption
[described in the spec](mlib-spec-v2.md#1-part-caption); the caption's own
size is validated against Telegram's 1,024-UTF-16-unit budget before any
upload starts, using a worst-case (highest part index, longest field
values already known) probe caption.

## 4. Resume and adopt

An interrupted `add` or `resume` leaves some parts `done` and some
`pending` in the index; re-running the pipeline picks up exactly where it
left off. Before uploading a set's remaining pending parts, the pipeline
scans the last `3 × part_count` channel messages and parses their
captions (`upload::adopt::adoption_map`), matching by `(set_id, part.i)`.
A part found this way is recorded via `mark_done` without ever being
re-uploaded — this is what makes `resume` after a `kill -9` mid-upload
produce no duplicate parts, even though `send_message` itself is not
retried on anything but `FLOOD_WAIT` (a lost response after a committed
send is exactly the case adoption exists to catch). `resume` iterates
every `pending` set oldest-first and pushes the index once at the end
unless `--no-push`.

## 5. `rescan`: disaster recovery

If `library.db` is lost but the channel still holds every part, `rescan`
pages through the entire channel history (`iter_messages`, 500 messages
per transaction) and folds every parseable `#mlib v=` caption into
`sets`/`parts`. It is **additive only**: it inserts sets/parts it hasn't
seen and recomputes each touched set's `complete`/`pending` status, but it
never demotes or deletes a set that local data already had recorded, and
it never re-uploads anything — `verify` is the tool for detecting
mismatches against what is already indexed, not `rescan`.

## 6. `verify`

Two modes, selected on one set (by id) or `--all`:

- **Default (metadata check):** for each part, if the local index doesn't
  even show it `done`, that's an immediate failure; otherwise the part's
  message is fetched by id (`get_messages_by_id`, batched to Telegram's
  100-per-call limit) and its document's size is compared to
  `parts.byte_length` — mismatch or a missing message is a failure, a
  changed document id (e.g. after a forward) is a warning only. No bytes
  are downloaded.
- **`--full`:** additionally streams every part's bytes back down
  (`iter_download`, chunked straight into a SHA-256 hasher — never buffered
  whole in memory) and compares against `parts.sha256`; a match records
  `parts.verified_at = now`. The total byte count and part count are
  printed before any download starts, since this mode re-downloads the
  entire set.

The pure comparison logic (`verify::report`) takes no Telegram types and is
unit-tested directly; `verify::download_hash` is the only piece that talks
to Telegram. See [`docs/code-standards.md`](code-standards.md) for why that
split matters for testing.

## 7. Backend portability

The index (`library.db`, described fully in
[`docs/mlib-spec-v2.md`](mlib-spec-v2.md#6-local-index-librarydb)) and the
caption format it mirrors are Telegram-agnostic in shape: `parts` stores a
`chat_id`/`message_id`/`doc_id` triple as opaque identifiers, not anything
grammers-specific, and the caption JSON carries no Telegram types. `grammers_*` imports are confined to the Telegram-facing layer —
`telegram/`, `upload/transport.rs`, `verify/download_hash.rs`,
`verify/session.rs` and the `commands/*` files that drive them — and never
appear in `index/`, `media/`, `metadata/` or the `mlib-spec` crate, which
are the parts a second client would reuse. A future Android TV player (or any other
client) can read `library.db` and the caption spec directly; the planned
`UniFFI` binding over `mlib-spec` would need a UniFFI-friendly shape for
`Episode::Range([u32; 2])` (UniFFI does not support fixed-size array
enum payloads) — tracked in
[`docs/development-roadmap.md`](development-roadmap.md).

## 8. On-disk layout

All paths come from `directories::ProjectDirs::from("", "", "mediagram")`
(XDG on Linux):

| Path | Contents |
|---|---|
| `$XDG_CONFIG_HOME/mediagram/config.toml` | User config (see `config.example.toml`). |
| `$XDG_DATA_HOME/mediagram/session.sqlite` | Telegram auth session (grammers `SqliteSession`). Directory `chmod 0700`, file `chmod 0600` — it holds the account's auth key. |
| `$XDG_DATA_HOME/mediagram/library.db` | The canonical index (WAL mode). |
| `$XDG_DATA_HOME/mediagram/tmdb-cache/*.json` | Disk-cached TMDB responses, keyed by `sha256(path + sorted query)`. |

Both `config.toml` and `data_dir` can be overridden (`--config`,
`MEDIAGRAM_DATA_DIR`, or the config's `data_dir` key).

## 9. Telegram limits relied on

- 4 GB per-message document cap on Telegram Premium; parts default to 3.5
  GiB to stay comfortably clear of it however the cap is actually enforced
  server-side.
- Captions are limited to 1,024 UTF-16 code units on the free tier; the
  uploader targets that limit so parts stay postable on any account tier.
- `get_messages_by_id` returns at most 100 messages per call; `verify`
  batches accordingly.
- `FLOOD_WAIT_n` RPC errors carry the exact server-requested backoff in
  seconds; `telegram::retry` sleeps that plus one second of slack.
