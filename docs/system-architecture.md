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
├── mediagram/        the CLI: media inspection, upload pipeline, local
│                     index, verify, and `serve`
└── mediagram-cache/  the LAN chunk store (§12); depends on none of the
                      above, shares nothing with the uploader's index
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
  posters.rs         fetch cover art and backdrops (`<key>-bg`) beside
                     library.db for a local player
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

The Bun player in `web/`: its module map, the home page, the Featured reel,
how it follows the channel, link adaptation, the System page and the codec
policy are in [`docs/web-player.md`](web-player.md). UI work runs against
`bun run preview` (copies of the library and watch state, no Telegram),
never against the real player.

## 8. Playback: the Android app

The second viewing surface, and the one built the other way round. The web
player is a Bun server that speaks MTProto and serves a browser; the phone has
no server at all. `mediagram-core` — Rust, grammers, bound into Kotlin with
[UniFFI](https://mozilla.github.io/uniffi-rs/) — *is* the client, and the app
is a Compose UI over the core API. The watch-state device-ID accessor
suspends while Rust runs its SQLite work on the blocking pool, following
the catalog-read accessors; the Kotlin caller does not read it on the UI thread.

The core ships as `libmediagram_core.so` for four ABIs, built by
`scripts/build-android-core.sh`: `arm64-v8a` and `armeabi-v7a` for devices,
`x86_64` and `x86` for emulators. The 32-bit pair is not legacy padding: many
Google TV boxes pair a 64-bit CPU with a 32-bit userspace and load only 32-bit
libraries, so an APK without them installs and then fails with
`UnsatisfiedLinkError`. `core:rust`'s `verifyNativeCore` refuses to package an
APK missing any of the four. On 32-bit, `usize` is 32 bits, so byte positions
in the core stay `u64` throughout; only in-memory buffer lengths narrow.

### Module map (`android/`)

| Module | Holds |
|---|---|
| `app` | the single activity, and the phone-or-television branch |
| `core:rust` | the generated UniFFI binding over `mediagram-core` |
| `core:data` | `CoreClient`, `CatalogRepository`, and settings in `EncryptedSharedPreferences` |
| `core:playback` | `MlibDataSource`, `CacheProvider`, `PlayerFactory` |
| `core:ffmpeg` | Media3's FFmpeg audio decoder, vendored, for DTS and TrueHD |
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
an empty one.

**Audio the device cannot decode goes through FFmpeg.** Many devices, Google
TV boxes above all, have no DTS or TrueHD decoder, and ExoPlayer leaves a
track that no renderer takes unselected, so the film played with no sound and
no message. `core:ffmpeg` is Media3's `decoder_ffmpeg` extension, which Media3
does not publish to Maven. Its Java sources are vendored unchanged from the
`1.10.1` tag. Its native library is built by `scripts/build-android-ffmpeg.sh`
for all four ABIs, 16 KB aligned, and is not committed; a `verifyFfmpeg` guard
refuses to package an APK without it. `PlayerFactory` builds the player with
`DefaultRenderersFactory` in `EXTENSION_RENDERER_MODE_ON`. The platform's own
renderer comes first, so hardware decoding and passthrough still win wherever
they exist. `FfmpegAudioRenderer` comes after it and takes only the formats
nothing else will. This is decoding to PCM on the device, not the conversion
of §7. Nothing is re-encoded and no server is involved.

FFmpeg is **6.0.1**, under the **LGPL 2.1 or later**. It is built with no GPL,
version-3 or nonfree components and only the `dca` (DTS, DTS-HD core),
`truehd` and `mlp` decoders. The licence text and source pointer ship in
`android/core/ffmpeg/licenses/`. AC-3 and E-AC-3 are left out: the devices
tested so far decode or pass them through themselves. Playback is latency-bound rather than throughput-bound: the link
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

There is no Kids shelf. It listed the same titles a kids profile now shows,
so it was removed from both surfaces: a kids profile's library is that list.
The "Kids" mark in the player stays, on grown-up profiles only — it is how an
unrated title, such as a course, is let through.

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
unpinned document is invisible and the next round would send another. The
watchlist, Kids and collections travel as rows with times, and a removal as
a `removed` flag on the same row, so a merge cannot bring back what was
taken off. A kids profile also carries `kids: true`; see Kids profiles.

**Un-marking `watched` travels under its own key, `unwatched`, not a
`removed` flag.** A reader that predates this feature cannot both
understand that flag and not — and at the moment this shipped there were
builds in the fleet that did not. Such a reader, seeing
`{setId, updatedAt, removed: true}` on a `WatchedRow`, would drop the flag
it does not recognise and import the row as a *live* mark at that same
moment; the next merge, weighing that resurrected live row against the real
removal at an exact tie, would decide the outcome by device id rather than
by what happened — and since the old reader never learns of the removal, it
never stops re-exporting that same live row, so the wrong side of the tie
stays wrong forever. `unwatched` on its own key is what such a reader simply
does not know to look for and so drops, the same way it already drops
`kids` and its optional siblings, leaving its own live mark unchanged and
always older than a removal a new device holds. On a new device, a removal
wins whenever its `updatedAt` is at least as new as the live mark it is
weighed against — a tie goes to the removal outright, not to a device-id
tie-break, because the two rows are unrelated claims from different
devices, not two writes of the same kind. An `UnwatchedRow` also carries
`lastFinishedAt`, the completion it took the mark from: a position no newer
than that stays suppressed, exactly as it would under a live completion,
while a genuine rewatch made since survives. See
`crates/mediagram-core/src/state/merge/watched.rs` and
`web/src/state/watched-reconcile.ts` for the reconciliation, pinned by
shared fixtures in both merge orders and across mixed old/new documents.
Importing a removal agrees with that same tie rule: it is skipped only when
the local live mark it would replace is *strictly* newer, never merely as
new. Marking watched again, and taking it back, are each clamped to at
least one millisecond past whichever clock last touched the row — an
import can leave `finished_at` or `removed_at` set from another device's
clock, and this device's own clock running behind must not let that stale
value outlast a fresh local write to the same title.

The web runs a round on start, every five minutes while the process is up,
on shutdown, on another device's push, and — `WriteDebounce`
(`web/src/application/write-debounce.ts`, latched after `stop()` so a write
racing shutdown cannot re-arm it) — a few seconds after the last
*sync-worthy* local write. Not every write qualifies: a film playing sends a
position PUT every ten seconds, and a round on every one of those would
invite the same flood limit this project has already hit once, so a
position write only counts when it says so itself, with `?final=1`
(`writeWorthSyncing` in `routes.ts`) — not by HTTP method, since
`flushProgress`'s `sendBeacon` usually sends that as a POST but falls back
to the same PUT the periodic tick uses when a browser refuses `sendBeacon`
a JSON body, and inferring "final" from the verb would then silently drop
the trigger for that browser. Forgetting a position outright (`DELETE`)
always counts, no marker needed, and so do watched, watchlist, Kids and
profile writes; a preference, being per-device and unsynced, never does.
`WatchSync` mirrors that cadence on Android: a round on start, every five
minutes while the app is in front, when a film is left, when the app goes
to the background, and within seconds of another device's write, heard
through the shared push listener; rounds never overlap on either surface.
Picker callers join work for the current core and library, while a pushed
update during a round retains one follow-up read. Changing identity
invalidates the old work. The device id is a random UUID in `state.db`,
never the host name.

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

## 12. The LAN chunk server (`mediagram-cache`)

A separate binary, `mediagram_cache`, run as its own systemd service on the
always-on home box. It has no Telegram session, no index and no UI; a set id
is an opaque string to it. The user chose a separate process over folding
this into the web player for isolation: its lifecycle — restarts, crashes —
is independent of the player's. Android devices on the home network read and
write it so a chunk fetched from Telegram once is not fetched again by the
next device; installing it is optional, and a device with no LAN server
configured just talks to Telegram directly, as it always did.

### API (v1)

| Method | Path | Auth | Result |
|---|---|---|---|
| GET | `/v1/sets/{id}/chunks/{n}` | none | 200 bytes / 404 |
| HEAD | same | none | 200 + `Content-Length` / 404 |
| PUT | same, header `X-Set-Total: <bytes>` | signed, see below | 201 stored / 200 already held / 400 bad length / 401 / 409 total mismatch / 413 over one chunk |
| GET | `/v1/status` | none | `{"version","held_bytes","budget_bytes","chunks"}` |

`id` matches `^[A-Za-z0-9]{1,64}$` — the same shape the web player's
`STREAM_PATH` requires — and `n` is a plain decimal `u32`; either failing
its check is a 404 before any filesystem call, so no path traversal is
possible. A chunk is `rules::CHUNK` bytes (1 MiB — two Telegram 512 KiB
requests, and the fixed size Android's own playback path reads in), except a
set's final chunk, which is whatever remains of its `total`.

### Write authentication

Reads are open and unauthenticated — nothing this server returns identifies
where the bytes live in Telegram, so serving them to anyone on the LAN costs
nothing a viewer could not already get by asking the channel directly once
paired. A write is different: an unpaired device flooding the store with
garbage would evict everything a paired one worked to cache, so every PUT
carries `Authorization: MGC1 <hex>`, where the hex is
`HMAC-SHA256(token, "PUT\n{path}\n{X-Set-Total}\n" + hex(sha256(body)))` —
keyed on the token's 64 ASCII hex characters themselves, never hex-decoded,
`path` and the total each their plain decimal spelling, and no trailing
newline after the body's hash. Checked in that order too: id/`n` shape,
then the length rule, only then the signature, so a malformed PUT is never
charged a 401 it could just as well have gotten a 400 or 404 for. The
pairing token itself never crosses the wire — only this signature does — so
a look-alike server on another network, or a passive listener on this one,
learns nothing usable. The signature binds the body, so a PUT altered in
transit is rejected; it does not bind a nonce or timestamp, because a
replayed PUT can only rewrite the identical chunk, which first-write-wins
already ignores.

The token lives under the systemd `StateDirectory`, separate from the
`CacheDirectory` chunks live under, so clearing the cache to reclaim disk
space does not unpair every device. `mediagram_cache token` prints it for
pairing. The exact byte layout of the signed string, and one worked
example, are pinned in
[`docs/running-the-player.md`](running-the-player.md#home-cache-server) and
asserted in a Rust test (`token::tests::the_shared_test_vector_signs_to_the_published_signature`),
so the Android client's implementation and this server's cannot drift apart
silently.

### Storage: files on disk, an LRU index in memory

There is no SQLite. A chunk lives at `root/<id>/<n>`; a set's total byte
count — recorded by whichever PUT arrives first, `create_new` so a second
racing PUT sees "already there" rather than overwriting it — lives at
`root/<id>/total`. At startup the store scans `root` and rebuilds an
in-memory index ordered by each chunk file's mtime, oldest first; a GET
touches a chunk's mtime, so that order (and therefore what gets evicted
first) survives a restart without a database. Integrity cannot be checked
per chunk the way a part's `sha256` covers a multi-GB upload — a chunk is
a slice of one — so the guards are the pairing token on writes and the
length rule (`len == CHUNK`, or exactly what is left of `total` for the
final chunk) rather than a checksum. A wrong-but-right-length chunk from a
buggy paired client is not detected here; removing the set's directory by
hand is the remedy.

Two devices can race to write the same chunk — a phone preloading an
episode while a TV is already playing it. Each PUT stages its body under a
name unique to that call, then publishes it with a no-overwrite link
(`hard_link` then unlink the temp file): the loser sees "already exists"
and returns 200 without its bytes ever touching the chunk file the winner
published, so the two are never interleaved.
