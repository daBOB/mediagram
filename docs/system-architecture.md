# System architecture

`mediagram` is a Rust CLI (edition 2024) that uploads a personal video
library into one private Telegram channel and keeps a local SQLite index of
it. It is split into four crates so the wire format, the TMDB client and the
byte path can be reused by other clients — the Android app among them (see
[§9](#9-backend-portability)).

## 1. Crates

```
crates/
├── mlib-spec/        the contract: caption, part plan, filename grammar,
│                     index schema and caption, package format — pure data
│                     and parsing, no IO, no Telegram dependency
├── mediagram-tmdb/   the TMDB client (with its disk cache and localizing
│                     wrapper), provider details, and poster download
├── mediagram-core/   the portable client: the UniFFI surface the Android
│                     app calls, the catalog store, the range-planned byte
│                     path over Telegram, and the one HTTP client both
│                     programs build (ring + webpki roots)
└── mediagram/        the CLI: media inspection, upload pipeline, local
                      index, verify, and `serve`
```

Dependencies run one way: `mediagram` → `mediagram-core` → `mediagram-tmdb`
→ `mlib-spec`, with `mediagram` also using the two below directly; nothing
depends on `mediagram`. The web
player ([§7](#7-playback-the-web-player)) lives in `web/` and depends on
neither: it reimplements what it needs of the format in TypeScript. The
wire format itself is documented normatively in
[`docs/mlib-spec.md`](mlib-spec.md); this document covers how the CLI
is put together around it.

## 2. Module map (`crates/mediagram/src`)

```
main.rs            clap CLI surface; parses args, loads config, dispatches
lib.rs             re-exports every module below for the binary and tests
config.rs          TOML config + MEDIAGRAM_* env overrides, secret redaction,
                   and the one TMDB client every command builds from it
paths.rs           XDG config/data directory resolution, owner-only dirs/files
clock.rs           wall-clock time as the index records it
term.rs            drawing a line that rewrites itself, and the percentages
                   and durations on it; a no-op off a terminal, so `prepare`
                   and `upload` stay readable in a pipe

commands/          one module per subcommand, each exposing `run(...)`; thin
                   entry points over the domain modules below
  add.rs             turn flags into a NewSet, upload::plan_set it, then hand
                     the bytes to finish_set (--watch) or a background process
  add_show/          walk a series folder, survey what will play badly, plan
                     and upload each episode, push the index once at the end
  add_course.rs      walk a course folder, upload each lesson then each
                     document, push the index once at the end
  finish_set.rs      upload one planned set, then push: what `add` runs, in
                     this process or a background one
  background.rs      re-runs this binary detached, so an upload outlives the
                     terminal that started it
  resume.rs          finish every set left `pending`
  push_index.rs      snapshot + upload + pin library.db (which pins are
                     current is kept by index/pins.rs)
  rescan.rs          rebuild the index from channel captions (disaster recovery)
  verify.rs          metadata check, or (--full) re-download + hash
  edit.rs            correct a set's metadata in place (see edit/)
  remove.rs          destroy a set in the channel and the index (see remove/)
  status/            what the library holds and what is still going in
  prepare/           drop unwanted tracks / convert a file before adding it
  metadata.rs        record TMDB descriptions for every title in the index
  posters.rs         fetch cover art beside library.db for a local player
  export_package.rs  build, encrypt and optionally publish the metadata
                     package (see export/)
  serve.rs           the local playback API (see serve/)
  setup.rs           first-run config: prompts for api_id/api_hash/channel/tmdb_key
  accept_login.rs    approve the player's QR login from this session
  login_code.rs      read the login code Telegram just sent to the account
  login.rs / whoami.rs / smoke_upload.rs
  args.rs            clap argument structs for the larger subcommands

upload/            getting a file into the channel. Planning: new_set (what
                   to add), plan_set (inspect → resolve → remux → record, for
                   a video), plan_document (a course PDF, no probe or remux),
                   plan (the one transaction that records a set). Sending:
                   hashing byte-range reader (part_reader), the Transport
                   trait + its Telegram implementation, the resumable per-set
                   pipeline, finish (one set) and finish_set (the lock, the
                   source deletion, and the Uploader a bulk command holds),
                   adoption (resume-without-reupload), the progress note and
                   terminal line, and the flock that makes uploads take turns
media/             probe (the one ffprobe runner and report), inspect (what a
                   caption records), streams (the stream model), HDR/quality
                   classification, file_names (video/document/number rules),
                   MP4 trailing-moov detection and faststart remux, the
                   direct-play policy, prepare/ (plan, paths, check), and the
                   reader that turns `ffmpeg -progress` into a terminal line
metadata/          interactive resolution of provider ids over
                   `mediagram-tmdb`: search, lookup, and the prompt for an
                   ambiguous match
course/            reading a course folder: which files are lessons and which
                   are documents, the numbers inferred from their names, its
                   identity (title, collection id), the sidecars beside a
                   lesson, and the dry-run table. Chapter numbers come from
                   the folders holding video and only those — a document-only
                   folder that joined the numbering would shift every lesson's
                   identity and make a re-run upload the whole course again
index/             library.db: open/migrate (after letting the session store
                   configure SQLite, see sqlite_init), sets/parts CRUD and
                   counts, typed set/part status, set labels, pin
                   bookkeeping, rescan folding, snapshot/vacuum
edit/              planning and applying a metadata correction: one caption
                   rewrite per part
remove/            planning and applying a set's destruction
export/            staging, posters, archive, pointer and publishing of the
                   metadata package
serve/             the playback HTTP API: routes and Range responses
telegram/          grammers client construction + login flow, retry policy,
                   unpinning
verify/            report (pure verdict logic) + download_hash (Telegram
                   download → SHA-256 streaming)
```

Every source file stays under 200 lines (enforced by
`crates/mediagram/tests/code_standards.rs`); a module that would grow past
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
uses `teleproto` (the maintained fork of the archived GramJS) on Bun. Two, not
three: the Android app ([§8](#8-playback-the-android-app)) reuses the Rust one
through UniFFI rather than growing a Kotlin client. They are not a port of one
another and never share code — what they share is the wire
format in `mlib-spec` and the index schema, which is exactly the boundary
[§9](#9-backend-portability) says a second client reuses. The Rust
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

status/            what the player is doing: the startup facts worth keeping,
                   a pure snapshot builder, and a route only a local viewer
                   is answered on
telegram/          teleproto client, and turning planned reads into bytes
cache/             512 KiB chunks on disk: keys, store with quota, reader,
                   the readahead tracker behind MEDIAGRAM_CACHE_READAHEAD, and
                   which sets are held in full, for the offline badge
package/           the mlib-package-v1 reader: pointer, cipher, tar, refresh,
                   and the artwork a package carries
transcode/         ffmpeg arguments, encoder probe, session registry, the
                   runner and its supervision, and serving what it produced
public/            the page: the start page, shelves, the player dialog,
                   hls.js when needed, and the buffer watch that converts
                   down on a slow link
```

### Where the player opens

`#/home`, and the rows on it are decided in `public/lib/home-shelves.js` and
drawn in `public/lib/home-view.js` — the same split every view here has, and
the reason the rules are testable without a DOM.

Two of those rows answer "what now?" from the two facts the library actually
has. **Next up** carries one card per show or course underway: the episode in
progress if there is one, otherwise the first unwatched episode after the one
finished most recently, shows ordered by when they were last watched. **The
latest rows** count arrival rather than release, and rank a show by its
newest episode so a series still being uploaded keeps its place.

Both needed a fact that was being thrown away. A catalog row carries
`addedAt`, because `groupLibrary()` sorts by title and the arrival order the
query produced does not survive it. And `/state` serves `watched` as
`[{setId, finishedAt}]`: finishing an episode *clears* its position, so
without the completion's date a show watched to the end of an episode has no
timestamp anywhere the page can see, and would rank behind one glanced at
months ago.

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

Five now, which is the reason the schema and the caption format are specified
rather than implied:

| Consumer | Reads | Writes |
|---|---|---|
| `mediagram` (add, resume, edit, remove, rescan, verify) | `library.db` | `library.db` |
| `mediagram export-package` | `library.db`, read-only | the package |
| `mediagram serve` | `library.db`, read-only | nothing |
| the web player | a package's `library.db`, or a local one, read-only | nothing |
| the Android app | a channel snapshot's `library.db`, read-only | its own sidecars |

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

### Saying what it is doing

The player works a great deal out at startup and only ever printed it: which
catalog opened and whether the refresh actually succeeded, which encoder
initialises, what the cache may hold. That terminal is usually on another
machine, in another room, or gone. `status/` keeps those facts instead
(`facts.ts`), folds them with what has to be read at the moment of asking
(`snapshot.ts`), and serves the result at `/api/status`.

The one fact worth naming is the refresh verdict. A player quietly serving a
package it could not refresh looks exactly like one serving a current package,
and nothing else the page shows would say otherwise.

**The route answers this household's own devices and 404s everyone else** —
404 rather than 403, because a 403 confirms to a caller from outside that
there is something here worth a second request, and this API has no
authentication of its own. The address is checked before the method, so
"wrong method" and "nothing here" are indistinguishable from outside. The
page follows the same rule: the only way to `#/system` is the masthead entry,
and it is unhidden only after `/api/status` has answered a `HEAD`. A viewer
from outside never learns the page is there.

Own devices, not the local network, and the difference is the tailnet.
`client-reach.ts` answers two questions about an address, and they part over
100.64/10: `isLocalAddress` says what the *link* can carry, so a tailnet peer
is remote there and its titles are converted; `isOwnNetwork` says whose
device is asking, and a Tailscale peer had to be admitted to the tailnet
before it could send a packet, so it is the same phone that would be on the
sofa if it were home. Only this route asks the second question.

What it reports beyond the startup facts: the cache's hits, misses and
evictions; bytes fetched upstream, and the failed reads that are the one
upstream problem a viewer feels and cannot see; what the conversions are
holding on disk, which unlike the cache has no budget and no eviction beyond
the idle reaper; resident memory; and the uptime. The *rate* upstream is not
in the snapshot: the server does not know how often it is being asked, and an
average since startup is not the number anyone watching a stall wants. Two
readings and the seconds between them go to the page, which subtracts.

The panel polls every two seconds, which is why both directory measurements
sit behind a fifteen-second memo (`status/dir-bytes.ts` for the transcodes,
`ChunkCache.sizeOnDisk` for the cache): counting bytes on disk means statting
every file, and a twenty-gigabyte cache is some forty thousand of them. The
two scans run together rather than one after the other, so the slow case is
the longer of them and not their sum.

What a reading *says* is in `public/lib/status-lines.js`, apart from where its
nodes go in `status-view.js`, for the reason `buffer-health.js` is apart from
`adapt-playback.js`: only the first can be tested without a browser.

Three smaller readings come from measurements that were already being taken
and thrown away — the buffer's fill rate in the HUD (`buffer-health.js`
measured it; `preload-readout.js` showed only the depth), the reason a title
is being converted, now on the shelf badge as well as in the player, and the
technical line under a title, whose average bitrate is what makes the
`needs transcode` badge legible.

### The codec policy

Which profiles are handed to the browser as they are, and which are converted
first, is in
[`docs/running-the-player.md`](running-the-player.md#what-plays-directly-and-what-is-converted).
The lists live once, in `web/public/lib/playable.js`, and a test fails if the
document stops matching them.

## 8. Playback: the Android app

The second viewing surface, and the one built the other way round. The web
player is a Bun server that speaks MTProto and serves a browser; the phone has
no server at all. `mediagram-core` — Rust, grammers, bound into Kotlin with
[UniFFI](https://mozilla.github.io/uniffi-rs/) — *is* the client, and the app
is a Compose UI over fourteen methods.

### Module map (`android/`)

| Module | Holds |
|---|---|
| `app` | the single activity, and the phone-or-television branch |
| `core:rust` | the generated UniFFI binding over `mediagram-core` |
| `core:data` | `CoreClient`, `CatalogRepository`, and settings in `EncryptedSharedPreferences` |
| `core:playback` | `MlibDataSource`, `CacheProvider`, `PlayerFactory` |
| `core:model` | `MediaSet` and `Kind`, shared by every surface |
| `core:designsystem` | theme and spacing |
| `feature:{catalog,player,setup,system}` | view models and UI state |
| `ui-mobile` | every screen the phone has |
| `ui-tv` | a `build.gradle.kts` and no source — see below |

Direction is `ui → feature → core:data → core:rust`, with
`core:playback → core:data`. A feature module never imports another.

### Where the catalog comes from

The phone reads **the newest index snapshot the chosen channel holds**, not the
published package the web player reads. Two consequences follow and both are
deliberate: the phone needs a Telegram login of its own, and the index carries
no artwork, so the device fetches its own posters and synopses from TMDB
through `fetch_missing` rather than receiving them with the catalog.

### Where the bytes come from

`MlibDataSource` is an ExoPlayer `BaseDataSource` over `core.read(set_id,
offset, len)`, addressed by an opaque `mlib://set/<id>` URI — no chat or
message id ever reaches Kotlin. It reads ahead 1 MiB per fetch to amortise
Telegram's 512 KiB chunking, and media3's `CacheDataSource` wraps it over a
`SimpleCache` on disk. A cache hit never reaches the core.

**Nothing is transcoded.** The phone decodes natively, so the whole conversion
apparatus of §7 — ffmpeg, HLS, the bitrate ladder, the encoder registry — has
no counterpart here, and the System screen has no Conversion block rather than
an empty one. Playback is latency-bound rather than throughput-bound: the link
outruns the bitrate, and what costs is the round trip per read.

### What it does not have yet

Parity with the web player is partial and tracked, not assumed. The phone has
no watch state of any kind — no resume, no watched marks, no watchlist, no
lists — because `mediagram-core` exposes nothing that touches progress; no
audio-track or subtitle selection; no search; no notes. The plan that closes
these, in order, is
[`plans/260922-0124-android-web-parity/`](../plans/260922-0124-android-web-parity/plan.md),
and the deliberate differences that will *not* be closed are recorded in
`docs/superpowers/specs/2026-09-20-android-system-menu-and-playback-stats-design.md` §9.

**`ui-tv` is empty.** The television surface is a registered Gradle module with
no source in it, so a Fire Stick or a TV box installs the app and gets a
placeholder. Everything in `core:` is surface-independent and waiting for it.

## 9. Backend portability

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

The Android app ([§8](#8-playback-the-android-app)) is the third consumer and
reads the same index the same way. The `UniFFI` friction this section once
predicted — `Episode::Range([u32; 2])`, which UniFFI cannot carry inside an
enum variant — never had to be resolved in `mlib-spec`: `mediagram-core`'s
`dto.rs` flattens a set's episode into an `episode_first`/`episode_last` pair
at the boundary, so `Episode` stays an implementation detail of the crate that
parses captions and no player ever sees it.

## 10. On-disk layout

All paths come from `directories::ProjectDirs::from("", "", "mediagram")`
(XDG on Linux):

| Path | Contents |
|---|---|
| `$XDG_CONFIG_HOME/mediagram/config.toml` | User config (see `config.example.toml`). Written by `mediagram login` on first run — api_id, api_hash, channel, tmdb_key — with the directory `chmod 0700` and the file `chmod 0600`, since it holds api_hash. |
| `$XDG_DATA_HOME/mediagram/session.sqlite` | Telegram auth session (grammers `SqliteSession`). Directory `chmod 0700`, file `chmod 0600` — it holds the account's auth key. |
| `$XDG_DATA_HOME/mediagram/library.db` | The canonical index (WAL mode). |
| `$XDG_DATA_HOME/mediagram/tmdb-cache/*.json` | Disk-cached TMDB responses, keyed by `sha256(path + sorted query)`. |
| `$XDG_DATA_HOME/mediagram/upload.lock` | Held (`flock`) by whichever process is uploading, so the others queue behind it. |
| `$XDG_DATA_HOME/mediagram/upload-progress.json` | How far the part in flight has got, for `status` to read. Rewritten every 2s, meaningless once stale. |
| `$XDG_DATA_HOME/mediagram/background.log` | Output of the detached uploads `add` starts. |

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

## 11. Telegram limits relied on

- 4 GB per-message document cap on Telegram Premium; parts default to 3.5
  GiB to stay comfortably clear of it however the cap is actually enforced
  server-side.
- Captions are limited to 1,024 UTF-16 code units on the free tier; the
  uploader targets that limit so parts stay postable on any account tier.
- `get_messages_by_id` returns at most 100 messages per call; `verify`
  batches accordingly.
- `FLOOD_WAIT_n` RPC errors carry the exact server-requested backoff in
  seconds; `telegram::retry` sleeps that plus one second of slack.
