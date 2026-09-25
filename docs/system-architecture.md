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
  add.rs             turn flags into a NewSet, prepare and record it, then hand
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
  push_index.rs      report the result of telegram/index_publish; bulk
                     uploads share that publisher (pins live in index/pins.rs)
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

upload/            getting a file into the channel. Preparation: new_set (what
                   to add), prepare_set (inspect → resolve → remux → record,
                   for a video), record_document (a course PDF, no probe/remux),
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
every `pending` set oldest-first. Missing or changed local sources are
reported and left pending while later sets continue. A transport or database
failure stops the run. Completed sets are published once before reporting an
aggregate failure, unless `--no-push`; successful work remains available even
when other sets need attention.

## 5. `rescan`: disaster recovery

If `library.db` is lost but the channel still holds every part, `rescan`
pages through the entire channel history (`iter_messages`, 500 messages
per transaction) and folds every parseable `#mlib v=` caption into
`sets`/`parts`. It is **additive only**: it inserts sets/parts it hasn't
seen and recomputes each touched set's `complete`/`pending` status, but it
never demotes or deletes a set that local data already had recorded, and
it never re-uploads anything — `verify` is the tool for detecting
mismatches against what is already indexed, not `rescan`.

A set it inserts is dated by **the earliest of its parts' messages** — when
its upload began — never by when the scan ran. Dating by the scan gave every
set one scan found the same second, and every player's "Latest" row sorts on
that column: a batch of hundreds tied, and whichever title broke the tie led.
A set the index already had keeps its date, like everything else it had.

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

The pure comparison logic (`verify::report`) takes no Telegram types.
`verify::source` supplies message metadata and chunk streams;
`verify::download_hash` batches retrieval and hashes streamed bytes. Tests
exercise the production session against injected streams and temporary SQLite
rows, including failed downloads and stale-success removal. See
[`docs/code-standards.md`](code-standards.md) for module boundaries.

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
                    ├─ catalog:  a published package, else the channel's pinned index
                    │            (this machine's library.db only as a fallback)
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
index.ts           executable entry point and application service composition
application/       catalog selection/following, subscription catch-up, ordered
                   shutdown, listener address reporting, and import-safe startup
config.ts          MEDIAGRAM_* environment, with secrets redacted in the log
server.ts          the listener, on node:http rather than Bun.serve
routes.ts          HTTP dispatch and feature-router composition
response.ts        byte-range planning and shared buffered-response framing
http/              request/response contracts, browser-write checks, static
                   files, and streaming with explicit range headers
catalog/           catalog/search presentation, metadata readers, asset and
                   artwork endpoints, and audio-track probing
range.ts           byte ranges to per-part reads, and the 4 KiB alignment
login.ts           issues this host's session; writes web/.env, mode 600
catalog.ts         library.db queries; PLAYABLE_SQL, mirrored from mlib-spec
client-reach.ts    a viewer on this network, or one across an uplink

status/            what the player is doing: the startup facts worth keeping,
                   a pure snapshot builder, and a route only a local viewer
                   is answered on
telegram/          teleproto client, turning planned reads into bytes, and
                   dependency-free caption conventions shared by channel policy
cache/             512 KiB chunks on disk: keys, store with quota, reader,
                   the readahead tracker behind MEDIAGRAM_CACHE_READAHEAD, and
                   which sets are held in full, for the offline badge
package/           the mlib-package-v1 reader: pointer, cipher, tar, refresh,
                   and the artwork a package carries
transcode/         playback HTTP negotiation, ffmpeg arguments, encoder probe,
                   session registry, process supervision, and HLS delivery
public/            the page: the start page, shelves, the player dialog,
                   hls.js when needed, and the buffer watch that converts
                   down on a slow link
```

Within `public/lib/`, `playback/` owns the player and its controls, with
`playback/streaming/` owning adaptation and HLS resources and `playback/notes/`
owning note loading, parsing and rendering. `catalog/` owns shelf and detail
rendering; `status/` owns the system
panel. Shared catalog, state, formatting, and playback-policy helpers remain
at the library root. The installed HLS client is still served at `/lib/hls.mjs`.

Application shutdown closes admission to speculative cache reads and waits for
existing warming to finish before disconnecting Telegram. The HTTP listener
also drains routing, streaming and cancellation cleanup; the shared source
remains available until those owners release it.

### Pages of the Movies shelf

The Movies shelf draws 48 films a page, which fills the last row of plates
at every column count the grid uses. The page is part of the address
(`#/movies/page/3`; page one stays the plain `#/movies`), so back, reload and
a shared link all land on it. The library is already whole in the page:
`public/lib/catalog/pager.js` only slices it and draws the links, and the
router matches `page` before any collection name. Series and Tutorials shelves
are short enough not to page, and the Android catalog does not page yet.

### The Featured reel

The Movies heading's Featured button opens `<dialog id="featured">`, drawn by
`public/lib/catalog/featured-reel.js`. Which films it shows is the pure rule in
`featured-picks.js`: films with a poster that the profile has not watched,
shuffled, at most twelve. Each slide is built when shown and crossfaded over
the last; the drift, fade and rising text are CSS, so reduced motion is the
stylesheet's global rule. Opening pushes a history entry: back closes the
reel, and Play or Details wait for that entry to be popped before acting, so
the film's page is not undone by it. Taglines and scores come from
`/api/shows/:key`, asked once per film and for the next film while one holds.
The phone has the same reel (`FeaturedReel`, `FeaturedSlide` in
`ui.catalog`), over the posters it fetches itself: `pickFeatured` is a port of
`featured-picks.js`, a tap on the poster stands in for Space, and back closes
it as the dialog's own dismissal.

### Where the player opens

`#/home`, and the rows on it are decided in `public/lib/catalog/home-shelves.js` and
drawn in `public/lib/catalog/home-view.js` — the same split every view here has, and
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

The main dispatcher and the extracted catalog and HTTP handlers each stay under
200 lines; the state router remains a larger module. A catalog swap rebuilds
catalog presentation and state routing together; requests already in flight
retain the router and database they started with. Buffered responses share one
framing helper, including HEAD responses. State, preload and HLS session deletion
share the same Origin/Host checks; body-bearing writes also require JSON.
Media workers obtain their internal HTTP address from the bound listener, so
OS-assigned ports and specific IPv4 or IPv6 binds work for audio probing,
transcoding and thumbnail generation.

Thumbnail generation first checks the held-title badge, then reads
`GET /api/sets/:id/cached-stream`, which also supports HEAD and the same single
Range framing as `/stream`. This route reads disk chunks only, with no upstream
fetch or readahead. Missing or truncated chunks fail the response body, so a
failed generation removes its partial sheet instead of publishing it. The
ordinary `/stream` route still fills cache misses from Telegram; without a cache,
the cached-stream route answers 404 and thumbnail generation is disabled.

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
| the web player | a package's `library.db`, the channel's snapshot, or a local one, read-only | its installed channel snapshots |
| the Android app | a channel snapshot's `library.db`, read-only | its own sidecars |

Every read-only consumer opens SQLite with `SQLITE_OPEN_READ_ONLY` rather
than merely not issuing writes: a writable handle would let it checkpoint the
WAL or replay a migration on an index the uploader owns.

### Adapting to the link

Which titles are converted is decided twice. Once before playback, from the
catalog: codecs a browser cannot decode, and — for a viewer the server places
outside the local network — a bitrate above the uplink budget. That decision
is made from numbers, and numbers about a link are frequently wrong.

So the page also measures, in `public/lib/playback/streaming/`:

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

What a reading *says* is in `public/lib/status/status-lines.js`, apart from where its
nodes go in `status-view.js`, for the reason `buffer-health.js` is apart from
`adapt-playback.js`: only the first can be tested without a browser.

Three smaller readings come from measurements that were already being taken
and thrown away — the buffer's fill rate in the HUD (`buffer-health.js`
measured it; `preload-readout.js` showed only the depth), the reason a title
is being converted, now on the shelf badge as well as in the player, and the
technical line under a title, whose average bitrate is what makes the
`needs transcode` badge legible.

### Updates Telegram pushes

Every session of the account hears about a message sent, edited or pinned in
the library channel within milliseconds (measured: 0–41 ms on teleproto and
grammers alike). `telegram/channel-events.ts` listens for those, reduces each
to a small shape, and `telegram/updates.ts` decides whether it matters:
another device's `#mlib-state` document is a **state** event, a newly sent
`#mlib-index` or a pin is an **index** event, and everything else — this
device's own writes, part captions sharing the `#mlib` hashtag, unpins, pin
notices, other channels — is nothing. Events of a kind are released at most
once per five seconds, timed from the first, so an upload's burst of part,
index, pin and unpin is one event and a long upload cannot starve it.

An event is a hint, never data. A state event runs the ordinary sync round,
which reads the pin list itself; the five-minute timer stays, and a missed
update costs exactly the wait it always did. Nothing missed while the
connection was down is replayed — a `StringSession` keeps no update state —
so the listener is subscribed before the start-up round, which covers what
came before it. The listener runs whether or not state is shared.

An index event installs the channel's newest snapshot, chosen as the Android
core chooses it (`channel-index/pick-newest-index.ts`, pinned to core's
`pick_index` by `web/test/fixtures/pick-index/`): proven to be a library while
staged, then swapped in under `MEDIAGRAM_CHANNEL_INDEX_DIR`. The server builds
its router again over the new handle, rescans which titles are held, and tells
every open page over `GET /api/events` — server-sent events, one way, with a
heartbeat. The page never speaks to Telegram: it reads `/api/sets` again when
told, and whenever its event stream reconnects, since an event sent while it
was away is not sent again. A title playing defers the redraw to its close.
The snapshot has descriptions but no artwork, so the server then runs the
uploader's own `mediagram posters --index <snapshot>` — one TMDB client, not a
third — and sends a second notice once the covers are on disk.
A configured package is still the catalog; the channel's pins do not override
it. The state documents the listener reads are written by the Android app too,
in the same format; see [§8](#8-playback-the-android-app).

The rule is shared with the Android core and pinned by one set of fixtures
both read, `web/test/fixtures/channel-updates/`.

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
is a Compose UI over the core API. The watch-state device-ID accessor
suspends while Rust runs its SQLite work on the blocking pool, following
the catalog-read accessors; the Kotlin caller does not read it on the UI thread.

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
The `setup.login` package owns the phone, code, and password sign-in state
machine; catalog owns profile selection and library browsing.
Inside `ui-mobile`, screens live in `ui.catalog`, `ui.player`, `ui.profile`,
`ui.setup`, `ui.settings`, and `ui.system`. App composition and navigation
remain in `ui`; shared row presentation and byte formatting live in
`ui.components` and `ui.formatting`.

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

### Updates Telegram pushes

The core listens the way the web player does, with the same rule and the
same fixtures (`mediagram-core/src/updates.rs`, `api/events.rs`), and hands
Kotlin one call: `nextLibraryEvent(handle, ownDevice)`, which waits and
answers `STATE` or `INDEX`. Before it takes the connection's update receiver
— one per connection, for the app's life — it asks Telegram for the update
state itself and stores it: a connection that has made no such request is
never pushed anything, and grammers only asks when the session already knows
its own user, which a key-only in-memory session does not. Measured the hard
way: without it the stream opened and stayed silent.

Android shares one native event subscription between the visible catalog and
foreground watch-state sync. It follows both the current core and selected
library, cancelling the previous wait when either changes. The subscription
ends after the last consumer leaves, with a short grace period; failed settings
reads and interrupted connections retry without crashing the app.

`LibraryUpdateCoordinator` in `core:data` owns refresh, local catalog reading,
optional artwork fetch and shelf regrouping as an awaited operation.
`CatalogViewModel` presents its progress and keeps held shelves visible when a
read fails. A manual update still fetches missing artwork when a channel refresh
fails; a pushed update fetches quietly only after a successful refresh. Compose
renders these states and submits requests; it does not infer completion from
intermediate UI emissions. The web server listens continuously and always checks
for a newer catalog after subscribing, covering updates missed during startup.

Measured on the tablet: a new index pushed by an uploader on another machine
was installed within about three seconds, and the new title's poster was on
its card within the same minute, with nothing touched.

Each card looks its poster up when the shelves are built, and a fetch always
finishes after the read that built them; so once a fetch lays down artwork
the shelves are rebuilt from the catalog on the device, without asking the
channel again.

### Kids profiles

A profile made with "Kids profile" ticked sees only titles rated FSK 12 or
under, plus unrated titles someone marked for Kids by hand; everything else —
FSK 16 and 18, unrated titles, and so every course unless marked — is hidden.
The rule is `forKidsProfile` in `web/public/lib/age-rating.js`, ported to
`android/core/model/src/main/kotlin/AgeRating.kt`, and each surface applies
it once, where it takes in its catalog (`applyCatalog` in `app.js`,
`CatalogViewModel` on the phone), so every shelf, search, reel and title page
inherits it.

It is a filter, not a lock: anyone can choose another profile, the server
does not know which profile is asking, and a direct stream URL still plays.
The chunk cache is device-wide and shared by every profile.

The flag is `profiles.kids` (web state v7, core state v3) and travels as an
optional `"kids": true` on the profile in the sync record — written only when
true, `format` still 1. Merging is "any device says yes": no device's
document can switch it off, so the flag is set at creation and never changed.
The kids flag is therefore permanent for that profile name across every
device on the account: a sync record cannot express a deletion, so once
another device has synced the name, that device's record still says
`"kids": true` and re-imports it on the next sync — an ordinary profile
recreated under the same name is upgraded straight back. Removing and
recreating a mistaken profile only works while no other device has synced
it (and even then, only from the web; the phone cannot remove profiles).
The dependable correction is a new profile under a different name.
A device that has not been updated reads the key as absent and shows that
profile everything until it is.

### Watch state

Positions, finished titles, watchlist, kids and collections live in the core's
own `state.db`, beside the catalog and never inside the directory a refresh
replaces. The record and the merge are ports of the web player's
(`crates/mediagram-core/src/state/`), held to it by the shared fixtures in
`web/test/fixtures/watch-state/` in both merge orders; the resume point and
Next up are Kotlin ports held to the same fixtures. Nothing is decided twice:
Android computes what the web computes, from the same data.

Sync is the web's channel sync. Each device keeps one pinned
`#mlib-state v=1 device=<id>` document in the library channel, edited in
place; a round reads every device's, merges them with its own export, takes
in what is newer and sends its own only when something changed. A first
document whose pin is refused is taken back and the round fails, since an
unpinned document is invisible and the next round would send another. Lists
travel as rows with times, and a removal as a tombstone, so a merge cannot
bring back what was taken off. A kids profile also carries `kids: true`; see
Kids profiles. `WatchSync` runs a round on start, every five minutes while
the app is in front, when a film is left, when the app goes to the
background, and within seconds of another device's write, heard through
the shared push listener; rounds never overlap. Picker callers join work for
the current core and library, while a pushed update during a round retains one
follow-up read. Changing identity invalidates the old work. The device id is a
random UUID in `state.db`, never the host name.

Receiving another device's progress remains useful even if this device cannot
publish its own. Both sync engines keep a successful import when sending fails,
but roll back an import whose local writes fail. The outcome preserves the
committed row count so the player can refresh its shelves while reporting the
network failure; the next round still retries the send. Imported profile creation
counts as a change on both surfaces, even when the profile has no watched titles.

Android's provider serializes closing and clearing account storage with core
construction. `DefaultCoreClient.close()` first calls `retireLocalState()`: the
native database mutex closes any open connection and permanently rejects that
core's queued or later local-state operations. Releasing a UniFFI handle alone
cannot guarantee this, because queued blocking work owns a separate native
reference. Retirement preserves files for ordinary core replacement; account
reset deletes them only after retirement. This boundary does not drain network
operations. Profile state is invalidated after a completed reset, and asynchronous
reads publish only while their originating core and selection are current.

Initial provisioning refuses to overwrite an installed core. Application
replacement reloads watch-state ownership before reporting success. If the
replacement is refused after retiring the old core, Settings restores ownership
using the retained credentials and preserves the refusal. A failed reload offers
a separate profile-read retry without submitting replacement credentials again.

"Who's watching?" chooses among the account's profiles, which arrive from the
other devices' documents, and removes one as the web does: locally, taking its
rows with it. Neither surface renames. A removed profile that another device's
document still names comes back with the next round that pulls it, on both
surfaces alike — the record has no tombstone for a profile. Deliberate
differences from the web, not gaps: sync is on by default, where the web player
needs `MEDIAGRAM_SYNC_STATE`; "Add to list" is a checklist rather than the
web's numbered prompt; the Films and Series shelves open as posters, where the
web opens on its list.

### Parity with the web player

Reached by
[`plans/260924-0139-android-web-parity/`](../plans/260924-0139-android-web-parity/plan.md),
which superseded the unbuilt half of
[`plans/260922-0124-android-web-parity/`](../plans/260922-0124-android-web-parity/plan.md):
search and genre pages, audio and subtitle choice, speed and framing, up next
and queues, fullscreen gestures, picture-in-picture and a media session,
series preload with offline badges, notes, profile removal and the List/Grid
shelf toggle. The Featured reel and the 48-a-page Movies shelf followed in
0.54.0 (`FilmPages.kt` ports `pager.js`); where the web keeps the page in the
address, the phone keeps it in the shelves' saved state, which a title opened
over them no longer clears. What differs on purpose, and why, is recorded in
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

The index queries and schema, media handling, metadata, and `mlib-spec` are
independent of Telegram transport. The uploader has one SQLite bootstrap
exception: [`index/sqlite_init.rs`](../crates/mediagram/src/index/sqlite_init.rs)
initializes the session store's shared SQLite library before any index
connection opens it. Reversing that order can abort the process. This uses
an in-memory session and makes no Telegram request; the ordering is covered
by [`sqlite_init_order.rs`](../crates/mediagram/tests/sqlite_init_order.rs).

Check that other reusable modules do not import grammers with:

```sh
rg -l 'grammers_' crates/mediagram/src/{index,media,metadata} crates/mlib-spec/ --glob '!sqlite_init.rs'
```

The command should produce no matches.

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
- Pinning is flood-limited hard: a handful of pin and unpin calls drew
  `FLOOD_WAIT_633` (over ten minutes) for the account. Pins are how every
  reader finds the index and each device's watch state, so nothing
  experimental may spend them.
- Updates are pushed only to a connection that has asked for its update
  state, and they arrive within milliseconds at every session of the account.
  Catching up after a disconnect replays no channel messages, so a listener
  is only ever a hint beside the reader it wakes.
