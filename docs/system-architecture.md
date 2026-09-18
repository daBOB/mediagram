# System architecture

`mediagram` is a Rust CLI (edition 2024) that uploads a personal video
library into one private Telegram channel and keeps a local SQLite index of
it. It is split into two crates so the wire format can be reused by other
clients (see [§8](#8-backend-portability)).

## 1. Crates

```
crates/
├── mlib-spec/   caption, part-plan, filename grammar, index schema — pure
│                data + parsing, no IO, no Telegram dependency
└── mediagram/   the CLI: Telegram client, media inspection, TMDB lookup,
                 upload pipeline, local index, verify
```

`mediagram` depends on `mlib-spec`; nothing depends on `mediagram`. The web
player ([§7](#7-playback-the-web-player)) lives in `web/` and depends on
neither: it reimplements what it needs of the format in TypeScript. The
wire format itself is documented normatively in
[`docs/mlib-spec.md`](mlib-spec.md); this document covers how the CLI
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
  setup.rs           first-run config: prompts for api_id/api_hash/channel/tmdb_key
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
[described in the spec](mlib-spec.md#1-part-caption); the caption's own
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

## 7. Playback: the web player

`web/` is a second program in a second language, and the first question a
reader will have is why.

`mediagram serve` (Rust, `serve/`) answers Range requests over a set's
concatenated parts and is complete and verified, but it can only run where
the uploader runs: it needs the uploader's `library.db` and the uploader's
session file, both on local disk. A player is wanted on a phone, on a
television, on a small host somewhere else. So the player is a separate
process that needs nothing from the uploader's filesystem — and that means it
has to speak MTProto itself.

```
browser ──HTTP──> Bun (web/src) ──MTProto──> the channel
                    │
                    ├─ catalog:  a published package, or a local library.db
                    ├─ bytes:    Range over the parts, through a disk cache
                    └─ ffmpeg:   HLS, for what the browser will not decode
```

**Two MTProto implementations, on purpose.** Rust uses `grammers`; the player
uses `teleproto` (the maintained fork of the archived GramJS) on Bun. They are
not a port of one another and never share code — what they share is the wire
format in `mlib-spec` and the index schema, which is exactly the boundary
[§8](#8-backend-portability) says a second client reuses. The Rust
implementation stays as the reference, and the player's bytes are checked
against ground truth rather than against it agreeing with itself: a set
streamed out of the player hashes to the `parts.sha256` the uploader recorded,
whether fetched whole or reassembled from separate ranges. That check is run
by hand against the live channel rather than in the test suite — there is no
channel in CI — and its results are recorded in
[`docs/project-changelog.md`](project-changelog.md).

The consequence to keep in mind is that a player holds an **auth key for an
account with access to the channel**. Not a read token, not a scoped
credential — the account. One auth key also cannot serve two clients at once,
so a host running both needs two. Both facts are why the player binds to
loopback and belongs behind something that authenticates:
[`docs/running-the-player.md`](running-the-player.md).

### Module map (`web/src`)

```
index.ts           startup: catalog, Telegram, cache, encoder, server, signals
config.ts          MEDIAGRAM_* environment, with secrets redacted in the log
server.ts          the listener, on node:http rather than Bun.serve
routes.ts          request description in, response description out — pure
response.ts        status and headers for a Range request (RFC 9110)
range.ts           byte ranges to per-part reads, and the 4 KiB alignment
listen-address.ts  which addresses a bind actually reaches, and the warning
login.ts           issues this host's session; writes web/.env, mode 600
catalog.ts         library.db queries; PLAYABLE_SQL, mirrored from mlib-spec
assets.ts          summaries and subtitle tracks out of the assets table
client-reach.ts    a viewer on this network, or one across an uplink

telegram/          teleproto client, and turning planned reads into bytes
cache/             512 KiB chunks on disk: keys, store with quota, reader,
                   and the readahead tracker behind MEDIAGRAM_CACHE_READAHEAD
package/           the mlib-package-v1 reader: pointer, cipher, tar, refresh,
                   and the artwork a package carries
transcode/         ffmpeg arguments, encoder probe, session registry, the
                   runner and its supervision, and serving what it produced
public/            the page: shelves, the player dialog, hls.js when needed,
                   and the buffer watch that converts down on a slow link
```

The 200-line rule [§2](#2-module-map-cratesmediagramsrc) states holds here
too, with one exception worth naming rather than hiding: `routes.ts` is over
twice that, having collected the catalog, stream, asset, poster, transcode and
static-file routes as each was added. Splitting the asset and static routes out
of the byte path is the obvious cut and has not been made yet.

`routes.ts` deliberately builds a description rather than a `Response`:
`Bun.serve` replaces a manually set `Content-Length` with chunked encoding for
any streamed body, and ffmpeg cannot seek an HTTP source without one — it
reads from byte zero instead, which would quietly make every conversion start
at the beginning of the film. Framing is therefore stated in one place and
written verbatim by `server.ts`.

Nothing the browser is served ever carries a `chat_id`, a `message_id` or a
`doc_id`. The browser is told what it may play, never where the bytes live.

### Consumers of the index

Four now, which is the reason the schema and the caption format are specified
rather than implied:

| Consumer | Reads | Writes |
|---|---|---|
| `mediagram` (add, resume, edit, remove, rescan, verify) | `library.db` | `library.db` |
| `mediagram export-package` | `library.db`, read-only | the package |
| `mediagram serve` | `library.db`, read-only | nothing |
| the web player | a package's `library.db`, or a local one, read-only | nothing |

Every read-only consumer opens SQLite with `SQLITE_OPEN_READ_ONLY` rather
than merely not issuing writes: a writable handle would let it checkpoint the
WAL or replay a migration on an index the uploader owns.

### Adapting to the link

Which titles are converted is decided twice. Once before playback, from the
catalog: codecs a browser cannot decode, and — for a viewer the server places
outside the local network — a bitrate above the uplink budget. That decision
is made from numbers, and numbers about a link are frequently wrong.

So the page also measures, in `public/lib/`:

```
buffer-health.js   seconds buffered ahead, and the rate it is filling at
adapt-bitrate.js   given a measurement, what to switch to — or nothing
adapt-playback.js  the loop: watch the element, act, do not thrash
```

Split three ways because the parts fail differently. A measurement is wrong
when it misreads a satisfied player as a starving one; a decision is wrong
when it restarts playback for a gain nobody would notice; a loop is wrong when
it does either of those every few seconds. Each is pure enough to test on its
own, and the two properties that took a live run to find are written down in
`buffer-health.js`: a full buffer looks exactly like a slow download, and a
stalled player looks exactly like a healthy one unless the rate is measured
against the wall clock.

The switch is a conversion at a requested bitrate — `?maxrate=` on the
transcode route, clamped between a floor and the configured cap, and part of
what identifies a session, since two viewers wanting different rates want
different encodes.

### The codec policy

Which profiles are handed to the browser as they are, and which are converted
first, is in
[`docs/running-the-player.md`](running-the-player.md#what-plays-directly-and-what-is-converted).
The lists live once, in `web/public/lib/playable.js`, and a test fails if the
document stops matching them.

## 8. Backend portability

The index (`library.db`, described fully in
[`docs/mlib-spec.md`](mlib-spec.md#6-local-index-librarydb)) and the
caption format it mirrors are Telegram-agnostic in shape: `parts` stores a
`chat_id`/`message_id`/`doc_id` triple as opaque identifiers, not anything
grammers-specific, and the caption JSON carries no Telegram types. The package export (`commands/export_package.rs`, `export/`) is a second,
read-only consumer of the index. It copies `library.db` with `VACUUM INTO`
over a read-only connection, so it never writes to the index and never
disturbs `last_push_at`, which belongs to the channel push. It reaches
Telegram not at all: the only network it touches is TMDB's image CDN, and a
configured command does the uploading. See
[`docs/mlib-package-v1.md`](mlib-package-v1.md).

That boundary is no longer hypothetical: the web player ([§7](#7-playback-the-web-player))
is the second client, and it reuses the schema and the caption format while
sharing no code at all.

The property that makes that possible is stated as a negative, because the
negative is the one that has to hold: `grammers_*` appears **nowhere** in
`index/`, `media/`, `metadata/` or the `mlib-spec` crate. Those are the parts
a second client reuses, and nothing in them knows Telegram exists. Which
Telegram-facing files import grammers changes as commands are added — `edit/`,
`remove/` and `serve/` all do now — and is not worth enumerating; that no
reusable module does is worth enforcing, and

```sh
grep -rl grammers crates/mediagram/src/{index,media,metadata} crates/mlib-spec/
```

is how to check it.

A future Android TV app can read `library.db` and the caption spec the same
way the web player does; the planned
`UniFFI` binding over `mlib-spec` would need a UniFFI-friendly shape for
`Episode::Range([u32; 2])` (UniFFI does not support fixed-size array
enum payloads) — tracked in
[`docs/development-roadmap.md`](development-roadmap.md).

## 9. On-disk layout

All paths come from `directories::ProjectDirs::from("", "", "mediagram")`
(XDG on Linux):

| Path | Contents |
|---|---|
| `$XDG_CONFIG_HOME/mediagram/config.toml` | User config (see `config.example.toml`). Written by `mediagram login` on first run — api_id, api_hash, channel, tmdb_key — with the directory `chmod 0700` and the file `chmod 0600`, since it holds api_hash. |
| `$XDG_DATA_HOME/mediagram/session.sqlite` | Telegram auth session (grammers `SqliteSession`). Directory `chmod 0700`, file `chmod 0600` — it holds the account's auth key. |
| `$XDG_DATA_HOME/mediagram/library.db` | The canonical index (WAL mode). |
| `$XDG_DATA_HOME/mediagram/tmdb-cache/*.json` | Disk-cached TMDB responses, keyed by `sha256(path + sorted query)`. |

Both `config.toml` and `data_dir` can be overridden (`--config`,
`MEDIAGRAM_DATA_DIR`, or the config's `data_dir` key).

The player keeps its own, on whatever host it runs on, all overridable by
`MEDIAGRAM_*`:

| Path | Contents |
|---|---|
| `web/.env` | The player's configuration, including its session string. Mode 0600; gitignored. |
| `~/.cache/mediagram-player/` | Chunk cache, bounded by `MEDIAGRAM_CACHE_MAX`. |
| `~/.cache/mediagram-hls/` | Segments of running conversions. Cleared at startup. |
| `~/.cache/mediagram-catalog/` | Decrypted packages, one directory per version, `current` a symlink to the live one. |

The three cache directories hold nothing canonical: delete any of them and the
player rebuilds what it needs on the next start, more slowly. `web/.env` is
not like that — losing it means logging in again or exporting a session from
the uploader, because a session string cannot be recovered from anywhere
else.

## 10. Telegram limits relied on

- 4 GB per-message document cap on Telegram Premium; parts default to 3.5
  GiB to stay comfortably clear of it however the cap is actually enforced
  server-side.
- Captions are limited to 1,024 UTF-16 code units on the free tier; the
  uploader targets that limit so parts stay postable on any account tier.
- `get_messages_by_id` returns at most 100 messages per call; `verify`
  batches accordingly.
- `FLOOD_WAIT_n` RPC errors carry the exact server-requested backoff in
  seconds; `telegram::retry` sleeps that plus one second of slack.
