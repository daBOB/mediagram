# System architecture

`mediagram` is a Rust CLI (edition 2024) that uploads a personal video
library into one private Telegram channel and keeps a local SQLite index of
it. It is split into six crates so the wire format, the TMDB client and the
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
├── mediagram-game/   game night: the party-game host (rooms, the buzzer quiz,
│                     the poster game, the race board game, the phone and TV
│                     pages); depends on
│                     nothing else in the workspace
├── spieltisch-core/  the Spieltisch app's UniFFI surface over `mediagram-game`:
│                     start, step back and stop a night, and the game
│                     catalogue its shelf is built from
├── mediagram/        the CLI: media inspection, upload pipeline, local
│                     index, verify, and `serve`
└── mediagram-cache/  the LAN chunk store (§12); depends on none of the
                      above, shares nothing with the uploader's index
```

Dependencies run one way: `mediagram` → `mediagram-core` → `mediagram-tmdb`
→ `mlib-spec`, with `mediagram` also using the two below directly (and
`mediagram-core` and `spieltisch-core` using `mediagram-game`); nothing
depends on `mediagram`. The web
player ([§7](#7-playback-the-web-player)) lives in `web/` and depends on
neither: it reimplements what it needs of the format in TypeScript. The
wire format itself is documented normatively in
[`docs/mlib-spec.md`](mlib-spec.md); this document covers how the CLI
is put together around it.

## 2. Module map (`crates/mediagram/src`)

```
main.rs            parses args, loads config, dispatches
cli.rs             clap's argument struct and subcommand enum: the whole CLI
                   surface, a module of the binary only
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
  add_show/          walk a series folder, survey what will play badly, hand
                     the episodes to an upload session
  add_course.rs      walk a course folder and hand it to course::upload
  add_docu/          upload a documentary: one file (a one-item upload
                     session), or a folder that collection.rs hands to
                     add_course.rs's own run, so it is walked and grouped
                     exactly as a course is — `Kind::Docu` episodes instead
                     of `Kind::Tut` lessons
  artwork.rs         set or clear a title's custom poster/backdrop
                     (index/artwork.rs), resolved from a set id or a title
  finish_set.rs      a one-item upload session over the set `add` planned,
                     in this process or a background one
  background.rs      re-runs this binary detached, so an upload outlives the
                     terminal that started it
  resume.rs          an upload session over every set left `pending`
  push_index.rs      publish through channel_index (`--force` skips the pull)
  pull_index.rs      pull through channel_index (`--dry-run` only reports)
  sync_index.rs      pull, describe, fetch artwork, publish
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
  publish_app.rs     send a signed APK to the channel as the newest app
                     release and pin it (see app_release/)
  subtitles/         give titles their German and English subtitle bundles:
                     move_inline (lessons' inline rows into bundles),
                     backfill (sources matched in local folders, by
                     match_source), backfill_channel (read from the copy in
                     the channel, through loopback: the player's streaming
                     router on a loopback port), dry_run with its report and
                     totals (measures only), and session (the lock, the one
                     connection and the publish every sending run shares)
  serve.rs           the reference Range server (see serve/)
  setup.rs           first-run config: prompts for api_id/api_hash/channel/tmdb_key
  accept_login.rs    approve the player's QR login from this session
  login_code.rs      read the login code Telegram just sent to the account
  login.rs / whoami.rs / smoke_upload.rs
  args/              clap argument structs for the larger subcommands, with
                     docu and edit as children

channel_index/     pulling the channel index into the local index and
                   publishing the local index as the channel index, the
                   whole round trip behind `ChannelIndex::{pull, publish}`:
                   choosing the current channel index among the pins and
                   the marker search (spec §7; the players' candidates and
                   rule, through mlib-spec), download, backup, merge,
                   re-reading conflicts, a publish lock per machine, pulling
                   again when another machine publishes mid-publish, send,
                   pin, and clearing the pins it replaces. The channel is
                   reached through the ChannelRemote port: Telegram in
                   production (telegram_remote), in memory in the tests
upload/            getting a file into the channel. Preparation: new_set (what
                   to add), prepare_set (inspect → resolve → remux → record,
                   for a video), record_document (a course PDF, no probe/remux),
                   plan (the one transaction that records a set). Sending:
                   hashing byte-range reader (part_reader), the Transport
                   trait + its Telegram implementation, the resumable per-set
                   pipeline, finish (one set), adoption
                   (resume-without-reupload), the progress note and terminal
                   line, and the flock that makes uploads take turns.
                   session/ is the upload session every uploading command
                   walks its items through: per item the upload lock, a
                   re-read of what the index holds (by the identity its
                   planning data carries), planning, the source check, one
                   lazy connection (the Link port: Telegram, or the tests'
                   fake), sending and source deletion; then at most one
                   publish, deferred to another running upload and owed
                   (`publish_owed`) when it cannot happen now
media/             probe (the one ffprobe runner and report), inspect (what a
                   caption records), streams (the stream model), HDR/quality
                   classification, file_names (video/document/number rules),
                   MP4 trailing-moov detection and faststart remux, the
                   direct-play policy, prepare/ (plan, paths, check), and the
                   reader that turns `ffmpeg -progress` into a terminal line
subtitles/         at set completion: which German/English text tracks and
                   sidecars to keep (select, sidecars), forced by flag,
                   title or sparse cues and the duplicate rules (arrange),
                   the one ffmpeg pass (extract), and sending and recording
                   the bundle (attach)
metadata/          interactive resolution of provider ids over
                   `mediagram-tmdb`: search, lookup, and the prompt for an
                   ambiguous match
course/            reading a course folder: which files are lessons and which
                   are documents, the numbers inferred from their names, its
                   identity (title, collection id), the sidecars beside a
                   lesson, the dry-run table, and upload.rs — the walk handed
                   to an upload session, shared by add-course and add-docu. Chapter numbers come from
                   the folders holding video and only those — a document-only
                   folder that joined the numbering would shift every lesson's
                   identity and make a re-run upload the whole course again
index/             library.db: open/migrate (after letting the session store
                   configure SQLite, see sqlite_init), sets/parts CRUD and
                   counts, typed set/part status, set labels, pin
                   bookkeeping, a set's lifecycle (lifecycle.rs: where a
                   pending set uploads from, the remux to delete, and
                   completing it in one transaction that forgets both),
                   rescan folding, snapshot/vacuum, custom poster/backdrop
                   bytes (artwork.rs), provider facts per title (shows.rs),
                   hand-set department categories (categories.rs) and anime
                   decisions (anime_overrides.rs), and recorded subtitle
                   bundles (subtitles.rs). merge/ brings a channel snapshot
                   into the local index: mod.rs runs it over children that
                   find the channel-only sets (candidates), copy rows over
                   the columns both sides have (copy, columns), detect
                   shared sets that differ (diff), and merge the shows,
                   credits, artwork, anime_overrides, categories and
                   subtitles tables; conflicts rewrites a set the two sides
                   disagree on from its own captions
edit/              planning and applying a metadata correction: one caption
                   rewrite per part
remove/            planning and applying a set's destruction
export/            staging, posters, archive, pointer and publishing of the
                   metadata package
app_release/       the Android app's releases in the library channel (spec
                   §7a): badging (what `aapt2 dump badging` says about an
                   APK) and publish (sending a signed APK as the newest
                   release)
serve/             the reference twin of the web player's routes and Range
                   responses; its router also backs the loopback server of
                   `subtitles backfill --channel`
telegram/          grammers client construction + login flow, retry policy,
                   batched message retrieval by id (messages)
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
   (not for documents) subtitles::attach, from the file the person named:
   ffprobe → sidecars → select → one ffmpeg pass into a temp dir → bundle →
   `send_document("#mlib-subs v=1")` → `subtitle_files`/`subtitle_tracks`
   recorded in one transaction (publish owed). Any failure: one warning,
   the set stays complete without a bundle. Runs before `--delete-source`
                     │
                     ▼
        upload session end: publish once (unless --no-push, or another
        upload is running and publishes when it ends), pull, snapshot, pin
```

Subtitles are read at completion, never at planning, in the process that
uploads (for `add`, the background `finish-set`), so the terminal never waits
for them. Planning records the file the person named under `orig:<set>` in
`meta` because completion deletes the remux and forgets the source; a set
planned by an older build falls back to its recorded source. `attach` takes a
file (sidecars are found in its folder) or a URL (none are): the backfill
reads the uploaded copy through the player's loopback server.

`mediagram subtitles` (`commands/subtitles/`) does the same after the fact,
holding the upload lock (one slot) and one Telegram session for the whole run,
with a pause between sends and a publish every 100 recorded sets and at the
end. `move-inline` builds a bundle from a set's inline `assets` subtitle rows
(track n is the n-th row by `lang`, so numbering is unchanged), sends it,
downloads it again and records it only if the hash and the decoded bodies
match. `backfill <folder>` feeds `attach` the file of a size (or, opted in
with `--accept-fallback`, name-and-duration) match; `--redo` records the new
bundle and `ChannelRemote::delete_message` removes the old one only after the
index naming the new one is published. A channel read that ends short
(ffmpeg run with `-xerror`; a wanted track missing) is a failure, never a
short bundle or a `subs-none` mark.
`backfill --channel` starts `serve::routes::router` on `127.0.0.1:0` over a
read-only index and a `TelegramSource` built from the sending client, and
gives `attach` `http://127.0.0.1:<port>/sets/<id>/stream`: one client, never a
second on the same auth key. Its candidates are complete, non-document sets
with German or English in `slang`, no bundle and no `subs-none:<set>` mark,
MP4 before the rest.

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
`telegram::messages` batches the retrieval and `verify::download_hash`
hashes the streamed bytes. Tests
exercise the production session against injected streams and temporary SQLite
rows, including failed downloads and stale-success removal. See
[`docs/code-standards.md`](code-standards.md) for module boundaries.

## 7. Playback: the web player

The Bun player in `web/`: its module map, the home page, the Featured reel,
how it follows the channel, link adaptation, the System page and the codec
policy are in [`docs/web-player.md`](web-player.md). UI work runs against
`bun run preview` (copies of the library and watch state, no Telegram),
never against the real player. `POST /api/report` sends a viewer's
broken-video report (§8, Reporting a broken video).

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
| `core:data` | `CoreProvider`, `CatalogRepository`, and settings in `EncryptedSharedPreferences` |
| `core:playback` | `MlibDataSource`, `CacheProvider`, `PlayerFactory` |
| `core:ffmpeg` | Media3's FFmpeg audio decoder, vendored, for DTS and TrueHD |
| `core:model` | `MediaSet` and `Kind`, shared by every surface |
| `core:update` | self-update (every release build; phones and tablets need Play Protect's app scan off). Plain Android library: no Hilt, no `:core:data`, no media core. The app gives `AppUpdater` a `ReleaseSource` (where the newest release is and how it is fetched, verified) and a `PlaybackActivity`. The media app's source is the core on the channel's pinned `#mlib-app` release (`CoreReleaseSource`, app module). Spieltisch's is `MediaReleaseSource`: it asks the media app's `GameNightProvider` (`/release` row, `/release.apk` file, behind the `GAME_NIGHT` signature permission), copies the APK, checks size and sha256 against the row, and waits out a night's stop under way before an install (a check or download may run during a night). The media app's provider reads the channel's `#spieltisch-app` posts (`SpieltischReleaseExport`); `/release.apk` waits for the core's download, and a cancelling caller (CancellationSignal) ends it |
| `core:designsystem` | theme and spacing |
| `core:testing` | `FakeCore`, the one fake of the generated core's `CoreInterface`, and the contract suite run against it and against the real core |
| `feature:{catalog,player,setup,stats,system}` | view models and UI state, surface-independent |
| `ui-mobile` | every screen the phone has |
| `ui-common` | composables and pure rules shared by phone and TV (formatters, position model, player lifecycle) |
| `ui-tv` | television surface: rail, departments bar, home, catalog, player, system and settings, driven by remote |
| `baselineprofile` | the D-pad journey from launch to a title page that generates the app's baseline profile, run on a signed-in TV |

Direction is `ui → feature → core:data → core:rust`, with
`core:playback → core:data`. A feature module never imports another.
`core:testing` sits outside this chain, depended on only by other modules'
test sources (`core:rust`'s `androidTest` included), never by any main source
set. Every ViewModel and repository above `core:data` reaches the generated
core through its own `CoreInterface`, not a hand-written wrapper: `CoreProvider`
hands one out, and owns closing it.
The `setup.login` package owns the phone, code, and password sign-in state
machine; catalog owns profile selection and library browsing.
Inside `ui-mobile`, screens live in `ui.catalog`, `ui.player`, `ui.profile`,
`ui.setup`, `ui.settings`, and `ui.system`. App composition and navigation
remain in `ui`; the library's chrome (departments bar, rail, header, a pushed
frame's bar and the overflow menu) lives in `ui.chrome`, and shared row
presentation in `ui.components`. Byte
formatting is `core:model`'s `ByteSize.kt`, shared with the TV. `ui-common`
declares `ui.common.*` and `ui-tv` declares `ui.tv.*`, so a package names the
module that owns it: a bare `ui.*` is always the phone's.

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

**High-bitrate sets fetch several chunks at once.** Playback is latency-bound
rather than throughput-bound: one fetch is one Telegram round trip, about
0.5 s per 1 MiB chunk on the TV box — some 16 Mbit/s however fast the link —
and a 4K film averages 27–31 Mbit/s with peaks of 40–65. The player's read
session therefore keeps a window of fetches in flight ahead of its reader
(`ChunkWindow`), as wide as twice the set's average bitrate needs —
`ceil(2 × bitrate / 16 Mbit/s)`, capped at four (~64 Mbit/s), the worker
count grammers itself uses. Sets up to 8 Mbit/s, or of unknown duration, stay
at one fetch at a time. The first chunk of every session is fetched alone, so
neither the first frame nor a seek waits for the window. Every fetch goes
through `ChunkMemo`, which lets one fetch per chunk be in flight and shares it
with every reader, and then the LAN-first path, so a chunk fetched ahead lands
on the LAN server like any other. Any failed fetch — the core reports a flood
wait grammers would not sleep through as an ordinary network error — drops
every set to one at a time until 60 s pass without another. Preloads stay
sequential: nobody is waiting on them.

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
tested so far decode or pass them through themselves.

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

A profile made with "Kids profile" ticked sees only titles rated at or under
its age limit — FSK 6 or FSK 12, set by its parent (the profile roles spec,
`docs/superpowers/specs/2026-09-28-profile-roles-design.md`) — plus unrated
titles someone marked for kids of that age; everything else — FSK 16 and 18,
unrated titles, and so every course unless marked — is hidden.
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

#### Daily watch limit

A kid can also have a daily limit: 30, 45, 60, 90, 120 or 180 minutes, or
none, the same every day, set beside the age limit in Manage profiles by
whoever may set that (the kid's parent, or the admin for a kid with none).
The time counted is the time the viewing stats already record —
`SUM(stats_days.seconds)` for the profile and today's local date over every
device's rows — so every screen spends the one budget, and nothing new is
counted. The web server's day is its own local date, a phone's or a
television's its own.

- **Five minutes left:** one notice per opened title, over the picture,
  "Only a few minutes of watching left today. This one can finish."
- **None left:** nothing opens — a shelf, a resume, Next up, the next
  episode (its button and its autoplay), joining watch together — and the
  viewer stays where they were with "That's all the watching for today. See
  you tomorrow." What is already playing is never stopped: it plays to its
  end. A grown-up who wants more time today raises the limit.
- **In watch together**, a kid who cannot follow the session to its next
  title leaves the session with those words, as one does whose age limit
  the new title is above; a refused open also drops a Join still waiting
  to happen, so the next title anyone opens on that screen does not join.

The rule, its words and its choices are written once in
`web/public/lib/daily-limit.js`, ported to
`android/core/model/src/main/kotlin/DailyLimit.kt`, and both are held to
`web/test/fixtures/watch-state/daily-limit.json`. The browser asks the
server (`GET /api/profiles/:id/today` before a title opens; the progress
`PUT` answers a limited kid with the same `{ limitMinutes, watchedSeconds }`)
and never counts by itself; Android reads the core's `watched_today`.

It is the age filter's kind of rule, a household's and not a lock: switching
to a sibling's profile is as free as before, a server or core that cannot be
asked lets the title open, and another device's minutes arrive with its next
sync round — up to about five minutes late while it plays (web state sync
must be on, `MEDIAGRAM_SYNC_STATE`), so a kid moving between screens can run
a few minutes over.

The limit is `profiles.daily_limit` and `daily_limit_updated_at` (web state
v14, core state v10) and travels as `dailyLimit: { minutes: number | null,
updatedAt }` on the kid in the sync record, `format` still 1. The newest
`updatedAt` wins, ties by device id; `null` — taken off — is kept as a value
so it goes on winning over the limit it replaced; a grown-up never carries
one; a document without the key never unsays one.

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

**The subtitle choices travel too, as an optional `preferences` list on each
profile** of the document: `{scope, name, value, updatedAt}` for `subtitle`,
`cue-size`, `cue-backing` and `cue-offset` in every scope (`key:`, `show:`,
`set:`, and `profile` for the preferred language). Audio, speed and framing
stay on the device. No format bump: a build that predates the key drops it,
keeps syncing positions and rebuilds its document from its own rows. The merge
is newest-write-wins per (profile, scope, name) with the device-id tie-break
every other row uses; an import writes only a row newer than the local one (or
an equal-time one the tie-break chose differently). A local write is stamped
`max(now, stored + 1)`, so a row's time never goes backwards on one device.
Synced names are never deleted — every writer stores a value (`off`, a track
key, a size), because a delete would come back from any device still holding
the row and no tombstone exists for it. One known difference: the scope of an
untagged manual film or episode is keyed differently on the two surfaces (the
web slugs any kind, the core only courses and documentaries), so such a
title's per-show choices do not meet across them; the profile-wide language
always does.

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
profile writes; so does a preference (only the subtitle names travel, but
the route does not look at the name, and the round is debounced anyway).
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
construction. `StoredCoreProvider`'s close fence first calls `retireLocalState()`:
the native database mutex closes any open connection and permanently rejects
that core's queued or later local-state operations. Releasing a UniFFI handle
alone cannot guarantee this, because queued blocking work owns a separate
native reference. Retirement preserves files for ordinary core replacement;
account reset deletes them only after retirement. This boundary does not
drain network operations. Profile state is invalidated after a completed
reset, and asynchronous reads publish only while their originating core and
selection are current.

The fence lives in `CoreProvider`, not in a wrapper around the generated
`Core`: the generated `CoreInterface` carries no `close()` of its own, so
`StoredCoreProvider` is generic over a type that is both `CoreInterface` and
`AutoCloseable` — the real `Core` is both already, and `core:testing`'s
`FakeCore` implements the same pair (as `FakeCoreHandle`) so a lifecycle test
needs no native library.

Initial provisioning refuses to overwrite an installed core. Application
replacement reloads watch-state ownership before reporting success. If the
replacement is refused after retiring the old core, Settings restores ownership
using the retained credentials and preserves the refusal. A failed reload offers
a separate profile-read retry without submitting replacement credentials again.

"Who's watching?" chooses among the account's profiles, which arrive from the
other devices' documents, and removes one as the web does: locally, taking its
rows with it. Neither surface offers a rename yet, though sync already keeps a
renamed profile as one person (below). A removed profile that another device's
document still names comes back with the next round that pulls it, on both
surfaces alike — the record has no tombstone for a profile. Deliberate
differences from the web, not gaps: sync is on by default, where the web player
needs `MEDIAGRAM_SYNC_STATE`; "Add to list" is a checklist rather than the
web's numbered prompt; the Films and Series shelves open as posters, where the
web opens on its list.

**A rename travels as two more optional keys on a profile's entry,
`renamedAt` and `formerNames`.** `renamedAt` is when the profile took the
name it has now, written only above 0; `formerNames` is every name it had
before, normalised, sorted, each once, at most 32, written only when not
empty. Nobody renamed, a document is byte-for-byte what it was, and
`SYNC_FORMAT` stays 1. Before the merge, a pre-pass (`renames.ts`,
`merge/renames.rs`) makes one person of every name joined through a former
name, gives it the spelling of the newest `renamedAt` (ties to the greater
device id, as every row's), and rewrites each entry's name, each kid's parent
and each household's admin to it; the merge then runs untouched. The import
never rekeys rows, because only the wire uses names: the first local row of
the merged name and of each former name are that person; the oldest
(`created_at`, then the smaller id) is kept and renamed in place
(`renamed_at`, `former_names`; web schema v17, core v13), and any other is
folded into it — its rows, its lists by id, a kid's `parent_id`, a
household's `admin_id`, the core's chosen profile — inside the import's one
transaction. A former name is taken for good on the install, as a current one
is: an older build, or a retired device's last document, still writes it for
the renamed person. The rename action itself ships only once every device in
use runs this merge.

An older build drops both keys on reading. Without a hand-built list it shows
both names: the new one as a second profile carrying the person's history, the
old one taking in nothing new, and what it still writes under the old name is
folded into the person on every current build. With a list, live or deleted,
its import of the new name inserts list ids its old row already holds; the
primary key refuses them, the round rolls back, and it takes in and publishes
nothing, for every profile, until it updates. Nothing is lost: its first round
on a current build renames the old row in place, or folds the two. Both are
pinned by `web/test/state-profile-rename-sync.test.ts` and the core's
`exchange_tests.rs`. Design: `docs/superpowers/specs/2026-10-20-profile-rename-design.md`.

**Viewing stats travel as two more optional keys on each profile,
`titleStats` and `dayStats`.** A title row is `{setId, device, startedAt,
lastWatchedAt, seconds, againAt?, updatedAt}`, a day row `{day, device,
seconds, updatedAt}`. Each device records only its own rows, inside its own
position write: the seconds between two writes of the same title, counted only
when the position advanced and capped at 15 s per step, so a seek, a pause or a
sleep never counts; a title started over after it was finished stamps
`againAt`. Positions merged in from other devices are never recorded. A
document carries every device's rows, not only the sender's, so minutes
survive a device that goes away. The merge keeps the newest `updatedAt` per
(profile, set or day, device), tie-broken like every other row, so it is
order-independent; totals are sums across devices of rows each device owns, so
a merge can never double-count. An import writes only a row newer than the
local one, and never deletes. `SYNC_FORMAT` stays 1: an older reader drops the
keys and keeps syncing everything else. A parse drops a bad row, never the
document: stamps above 2^53 - 1, a day over 86400 s, an unparseable day. An
own row's stamp is `MAX(now, stored + 1)`, so a clock stepping back cannot let
an older copy overwrite newer seconds. Both engines hold to the web's fixtures
(`web/test/fixtures/watch-state/stats-*.json`).

Known ceiling: rows are kept forever and every device's document carries every
device's rows, so a document grows with each device copy (a title row is about
190 B, a day row about 110 B). The upgrade path is to send only this device's
rows plus those of devices whose own document is gone, or to compress.

The Stats page (a rail item between Genres and Settings, on the web, the phone
and the television) is a summary computed from those rows plus the finished
titles: week, month and all-time minutes, 30 daily bars and a history list.
Achievements (films finished, a whole series, genres, hours, streaks,
episodes in a day) are derived from the same rows and the library on every
read, never stored or synced, held to the web by `achievements.json`; a kids
profile earns only finishing and exploring ones. Which achievements this
device has shown is a per-device `seen` set (the dot on the rail item), also
unsynced. The core serves `stats` and `achievements` over uniffi and
`set_progress` takes the local day from Kotlin. Plan:
`plans/260928-0306-viewing-stats-web-and-android/`.

### Watch together

Screens signed in to one library play the same title in step: play, pause,
seek, speed and the next episode on one screen happen on the others within
about a second. Anyone in the session controls it. The transport is one
unpinned channel message per session, edited in place (`docs/mlib-spec.md`
§7b); there is no relay of our own.

**The web is the reference.** The rules (parse and write, expected position,
liveness, `follow`'s decision, join and leave on `members`) are plain
JavaScript in `web/public/lib/together/`, which the browser loads and the
server imports. The Kotlin port in `android/core/model/.../together/` is held
to the same `web/test/fixtures/together/`, and the Rust core ships none of it:
`crates/mediagram-core/src/api/together.rs` only does the I/O (send, edit,
delete, read history and a message by id) and hands the message text up.

**Push.** A `#mlib-together` new or edited message is classified `together`
by `updates.rs` on both surfaces and handed on at once, skipping the 5 s
debouncer that state and index events wait out. The web server tells open
pages with a `together` event on `/api/events`, carrying `serverNow`, so the
browser corrects its own clock from the server's and never compares against
it. Android receives a payload-less `LibraryEvent::Together` (`TOGETHER` in
Kotlin) and then reads `together_heard()`, a slot in the core that holds only
the newest together message heard and is not cleared, so two pushes can read
the same message (`TogetherReads.kt` takes it as news once). This device hears its own edits
too, and the echo rule drops them.

**The echo rule.** An edit whose `by` is this screen is ignored only while the current session is still this screen's own write (`stillMine` in `follow.js`); if another screen's edit landed in between, the later own echo becomes the session. A change a
screen makes because it is following is never sent. Only the viewer's own
actions are: on the web, the player's viewer call paths; on Android,
`ViewerPlayer` plus the speed and restart hooks.

**Writing.** Writes are serialised per hub (web) or sender (Android). A write
in flight is the newest known state, so a join or leave composes from it.
What is queued is each screen's *move* (act: its playback fields; join; leave;
end), and the moves waiting for one message are composed in the order they
were made, so a queued join survives a later act; each goes out re-applied to
the newest known session, and is dropped if the session is gone or ended. On a
flood wait the move goes again when Telegram's wait ends, ahead of any made
since. An edit that throws, as while Telegram can't be reached, restores the
last state the channel is known to hold and is tried again after 30 s, never
sooner, ahead of any move made since (`EDIT_RETRY_MS` in `web/src/together/sender.ts`
and `TogetherSender.kt`). After three throws (`EDIT_TRIES`) the move is given
up on and logged, and the screens are shown what the channel holds with a
`lost` mark: the web's `together` event carries `lost: true`, Android's
`LiveSession.lost` is set. A screen takes a `lost` session even when it is its
own echo, which `follow` would otherwise ignore, so the screen that made the
move follows the channel again rather than stay on a state nobody else has.
A last leave or end that throws is retried the same way, so it is still written
and the message deleted if Telegram is back within the three tries; one given
up with its bundle is not re-queued yet, which can leave the departed screen
listed until the session goes stale (a known gap). On Android a thrown edit also
makes `available` false until a read or an edit answers, and a screen in a
session says "Telegram isn't reachable" when it does. The web lets act, leave
and end queue while Telegram is unreachable, and an act answered then carries
the reason; only start and join, which read the channel, are refused with a
503. Starting is serialised: one start in flight,
and on both surfaces a lost start race becomes a join of the winner's session (`DefaultTogetherLive.kt` returns `Started(conflict = true)`, `TogetherJoining.kt` then joins). The lock covers only starts from the same server or process, so a start from another device can still race. Leave and end
are answered once queued, so a client cannot tell when the leave reached
Telegram. Cross-device races stay inherent: last edit wins and the loser
follows.

**Discovery.** The newest 100 messages of the channel plus the last session
heard (read by id). Search is never used.

**Deliberate differences** (the web is the reference; each of these is a
decision, not drift):

- *Converted titles trail.* On the web, a title served through ffmpeg
  conversion is never drift-seeked: a seek on a converted stream restarts
  ffmpeg and would loop. After a heard seek it runs behind by ffmpeg's
  start-up (1-3 s). A gentle rate nudge is the known fix, not built.
- *A hidden browser tab in a session keeps its event stream open* (one SSE per
  such tab), unlike a hidden tab outside one, which closes it. Background
  keeps a screen in the session.
- *Join is hidden, not greyed, when Telegram is unreachable,* on Home and on
  title pages: the server cannot know of a session then. The player's own
  Watch together button is greyed, with the reason. (The design spec said
  greyed everywhere.)
- *A title picked inside a session starts at the session's place,* not at the
  profile's resume point; a half-watched episode picked from the sidebar
  starts at the top for a new title.
- *Home's Join row on Android stays on the session this device is in;* the
  web's Home switches to a newer session on push.
- *The Android follower lives in `feature/player`,* not in a `together`
  feature module, because a feature module may not depend on another.
- *`TogetherSender` and the live state live in `core/data` on `@AppScope`,* as
  the twin of the web server's hub: leave and end must outlive the player's
  view model, and Android has no server to take the hub's role.
- *The Rust core keeps no rules.* It reads and writes bytes; every decision is
  in Kotlin (and JavaScript).
- *The Kotlin JSON reader is lenient on non-JSON tokens* that the JavaScript
  one rejects. Only the two strict writers ever write the message, so no
  reachable input differs.
- *The TV plate takes Home's focus only when the viewer comes back within
  5 s;* on a cold start Home's focus stays on the cover and the plate is one
  Down away. The 5 s is the offer's `SharingStarted.WhileSubscribed(5_000,
  replayExpirationMillis = 0)` in
  `android/feature/catalog/src/main/kotlin/CatalogViewModel.kt`: once Join has
  not been shown for 5 s the offer is dropped, so on a later return Home's
  focus lands on the cover.
- *Dismissing picture-in-picture on Android leaves the session*
  (`PlayerViewModelPip.kt`): the title stays open and paused, but nothing is
  on screen to follow with. The web player has no such dismissal.
- *A start or a join on Android gives up after 10 s* (`ASK_MS` in
  `TogetherJoining.kt`) and says it could not start; one answered later
  leaves again. The web waits on the server's answer, whose own reads and
  sends have no limit of their own.
- *Unreachable is learned differently.* The web server knows whether its
  connection is up, so every move answered while it is down says why. Android
  learns it from a read or an edit that fails, says so then, and on each
  move after, until a read or an edit answers.
- *The notice wording grew beyond the spec.* The words are in
  `web/public/lib/playback/together-words.js` and
  `android/feature/player/.../TogetherWords.kt` (`Watch together ended`,
  `Next episode by <screen>`, `That title isn't on this profile`, ...); they
  are copied word for word, and "episode" is also used for a course lesson.

### Cast to the TV

A viewer on the phone, the tablet or the web player picks a title and the TV
plays it; the small screen becomes the remote. It is a watch-together session
with a remote member in it (`docs/mlib-spec.md` §7b, `v=2`), so it needs no
transport of its own: sender, channel message, TV receiver, player.

**Sender to TV.** *Play on TV* writes an offer session (`cast.state: "offer"`,
the sender as a `remote` member, `cast.profile` and `cast.household`). Every
TV with Mediagram open hears it through the push path. A TV of another
household, or of none, ignores it; the right TV answers by editing the message:
`refused`, `busy` (the sender then asks "Replace it?") or `on`. Nothing
registers a TV, and a sleeping or closed TV does not answer: after 5 s the
sender says Mediagram isn't open on the TV.

**TV as the caster.** The TV plays under `cast.profile`, not its own chosen
profile: progress, Continue, stats, a kid's age limit and daily limit all
belong to the caster. On Android that is a "playing as" profile carried through
the player. Afterwards the TV returns to its own Home. The rules (`offered`,
`answerOf`, stale requests, the end rule for remote members) are
`web/public/lib/together/cast.js`, with the Kotlin port in
`android/core/model/.../together/` held to `web/test/fixtures/together/`. The
web server's side is `web/src/together/cast-desk.ts` and `cast-routes.ts`
(`offer`, `replace`, `cancel`, `tracks` on `POST /api/together`).

**Household.** A device offers a cast only when it has joined a household, and
only a TV of that household answers. A TV answers by ignoring, not refusing,
what is not its own: with a TV open in each household, a refusal from the wrong
one would overwrite the answer of the right one. A TV holds the newest request
of each household, so another household's offer never hides its own. The
design assumes one TV per household: with two of one household open, both
answer the same offer.

**The remote and the bar.** The remote is the player screen with the title's
backdrop where the video would be. While a session with `cast.state: "on"` is
live, a bar on every phone, tablet and web player of the household brings the
viewer back to it. A cast of another household's TV shows no bar and no Join.
The TV remote keeps working: pausing or seeking there is an edit like any
other, and Back on the TV ends the cast.

**Miracast (Android phone and tablet).** The other way to a TV, for one
without the box. Play on TV asks which: the TV plays it (the cast above), or
this device plays it. For the latter, while a wireless display is connected
(Android reports a presentation display), the player puts the video on it
through a `Presentation`; the device shows the remote, driving its own player,
and nothing is written to the channel. At most 1080p, no HDR. Connecting is
Android's job (its Cast screen); the app cannot start it. The connection
dropping brings the video back to the device, paused.

**AirPlay is not in this release.** A web player in Safari sending a stream to
an AirPlay TV is deferred until it can be probed on an Apple device; there is no
AirPlay code.

### Game night

The TV box hosts party games for phones on the same network: a buzzer quiz, a
poster-guessing game, two board games (Heimlauf, Grand Allee) and three card games
(Mau-Mau, Schwimmen, Durak). It is a feature of the TV app only, and it is the one
place the app serves something rather than only fetching.

**The host lives in the core.** `mediagram-game` is a self-contained crate (an
axum server with the React phone and TV pages embedded in the binary) that
`mediagram-core` links; Kotlin starts and stops it through
`Core::start_game_night`, `game_night_back` and `stop_game_night`
(`crates/mediagram-core/src/api/game_night.rs`). Starting binds port 8080, or
any free port when that is taken, and returns the TV page's address
(`http://127.0.0.1:<port>/tv`) and the join address with the box's current LAN
IP, since DHCP moves it. A second start stops the first, so the port is free.
A stop gives open requests 2 s and then cuts them off (`host.rs`): axum sets
no header-read timeout, and one connection stalled mid-request would otherwise
hold the stop, and with it every later night, which waits on the same slot.
Nothing runs in the background: the Games screen opening starts the host,
leaving it, or the app going to the background, stops it
(`android/ui-tv/.../games/TvGamesFrame.kt`, a full-screen WebView of the
loopback TV page that keeps the screen on). The background stop is called from
the lifecycle's ON_STOP itself, since Compose draws no frame while the app is
stopped and the screen's dispose may only run on return. `GameNightViewModel`
runs the start off the main thread (UniFFI runs it on the calling thread, and
it reads the whole catalog), and a start waits out any stop still under way,
so leaving and coming straight back never has the old night's stop end the new
one or show its page. Back first steps a running game to the lobby and leaves
only when there was nothing to step back from.

**Phones join by browser; the TV page is the box's alone.** The lobby's QR code
(`/qr.svg`) points at the join address; a phone types a name and gets a seat
and colour, up to 8. A reconnect token in the phone's storage returns a phone
that slept to its seat until the game ends. The first to join is the starter
and picks the game and settings; the TV remote's OK can start as well. The
`tv` role on the WebSocket (`/ws`) and the images (`/img/{i}/{which}`) are
served to loopback connections only (`room.rs`, `server.rs`): a phone that
claimed to be the TV would otherwise see every answer, and phones are never
sent an image, so nobody can zoom into a poster. Everything the players see is
German.

**Which titles, and the kids rule.** `crates/mediagram-core/src/game_titles.rs`
maps the catalog to titles: a film is one title per set, a series one title per
show (episodes grouped by `show`, as the web player groups them), and courses
and documents are left out. A series counts as kid-safe only when every one of
its episode sets is, because a question shows the show and cannot show only its
safe half. *Nur für Kinder* is decided in Kotlin, never in Rust
(`android/feature/games/.../GameNightConfig.kt`, using `forKidsProfile`): a kids
profile gets its own limit with the toggle locked on, since kids profiles have
no PIN and an open toggle would be a way past it; a grown-up gets limit 12 with
the toggle free and off to start. A profile that is missing after a reload is
treated as locked at 12. Core receives the set ids that pass and a locked flag,
and with the toggle on every title used (answers, wrong options, images) is
one of them. Sections the profile hides stay hidden here too: Kotlin passes
the set ids in the sections it sees (`sectionKey()` against `hiddenFrom`, as
the library, Cast and Watch together filter), and a series is kept when any of
its episodes is, as the library builds its shows from the visible sets. A game
night writes nothing to profile state: no positions, history, stats or
daily-limit time.

**Buzzer quiz.** 8 questions; the question shows 3.5 s, then all four answer
buttons go live at once for 12 s; a right answer scores 1000 down to 500 by
speed. Taps carry the phone's estimate of host time, trusted at most 400 ms
before arrival. Film questions (`film_questions.rs`) are built from the library
on the box: a still, the year, the director, the cast; wrong options come from
the same kind of title. The general pack (`questions/allgemein.json`, 311
German questions, each with the source it was checked against) is embedded in
the binary. *Filme*, *Allgemein* and *Gemischt* choose the mix (`quiz_mix.rs`).

**Poster game.** 10 rounds. The poster is pixelated in 8 sharpness steps, one
every 2 s, then stays sharp for 3 s grace; a right tap scores 1000 at the first
step down to 500 at the last, and a wrong tap only locks that player out of the
round. The earliest claimed right tap wins, within the same 400 ms claim lag.
Titles with a poster only. A title readable on the artwork would give itself
away before the last step, so `game_posters.rs` fetches TMDB's textless poster
into `{data_dir}/game-posters/` (one fetch per title at a time, so the
prefetch and the image route never write one file together), and the normal
poster is used when there is none, no TMDB key, or the textless one is not on
disk within 300 ms (`poster_lookup.rs`): a cached file answers at once, and a
fetch still under way would otherwise leave the round's first steps blank. A
title counts as playable when its kind has four distinct poster-bearing names,
counted in one pass, since the box runs the check on every start. The TV page
draws each round on a fresh canvas, so the last poster never shows while the
next loads.

**Race board game (Heimlauf).** 2–4 players (the lobby blocks other counts),
four pieces each, the classic cross-and-circle race rules in `race_rules.rs`:
a 6 must bring a piece out while the own start field is free, a piece on the
start field must clear it while others wait at home, no landing on an own
piece, landing on an opponent sends it home (not forced), the goal needs an
exact count with no jumping over own pieces, a 6 rolls again, and with nothing
movable a player gets three tries for a 6. The first with all four in the goal
wins and the game ends there; the rest rank by pieces in the goal, then by
fields walked. A turn (`race.rs`) is an untimed "Würfeln" button, the die's
flight on the TV (1.15 s), then the player picks a move; a single legal move
plays itself and none passes. Pieces are told apart by marker, not number: the
TV rings each movable piece and its target in the colour and shape of the
matching phone button. An absent player's turns play themselves after 1.5 s,
so a sleeping phone never stalls the table. `PhoneScreen::Choice` gained an
optional `closes_at` for this: without it the phone shows no clock.

**The race board is three.js in the same WebView.** The TV page loads
`tv/race/` as its own chunk (three.js stays out of the other pages) and draws
the wooden board from the look test measured on the box (`plans/reports/`
benchmark 261105-1611): the 2048² board print, a lacquered table, a turned
pawn per piece in the player colour, the die rolling on the table beside the
board so it never passes through a piece, and the tags and banner as quads
inside the canvas. The box rules from that test hold: no HTML over the canvas
during play, every tag and banner state drawn and uploaded at load, the big
flat surfaces unlit with their light baked in, no alpha-tested cut-outs, no
blur filters or per-dot noise on big canvases. Everything moving is derived
from the scene and the host clock, so a TV that reconnects mid-turn picks up
the throw or hop where it is. Measured on the box: 59.9 fps at 1080p with
MSAA, the board up 3.4 s after the start. The wood textures are Poly Haven's
(CC0), shrunk to 1024 px in `client/public/race/`. The game end shows the
winner's banner on the board for 4 s, then the shared final ranking.

**The race board has sound, on the TV only.** `race-sound-cues.ts` turns each
stage into timed cues from the host's times alone (the die's clatter at its
first touch of the table, a knock per hop, thunk and jingle at a capture, a
jingle into the goal, the winner's jingle); `race-sound.ts` schedules them
once per stage on the Web Audio clock and skips any already past, so a TV
that reconnects never replays a backlog. Each sound starts early by the
speaker delay, read every frame from `getOutputTimestamp()` and smoothed: the
box's WebView takes ~370 ms (±50) from start to speaker. That is longer than
a hop, so the host starts a move `lead_ms` (200) after deciding it; without
that lead the first knock of every move could only come late. The background music is an `<audio>`
element fed blob URLs, two tracks in turn: decoded into Web Audio, three
minutes of music would hold ~77 MB on the box. Sound loads beside the board
and never holds it back; if it fails the board plays silent. The TV app's
WebView sets `mediaPlaybackRequiresUserGesture = false`, since nobody taps a
TV; a desktop browser acting as the TV starts sound at its first key or click.
The files are in `client/public/race/sound/`: Kenney's Casino Audio, Impact
Sounds and Music Jingles; the Wilhelm scream for a capture (Wikimedia
Commons, from the SSE library on the Internet Archive); the winner's jingle
is "Classic fanfare lick" by fvcalderan with "Well Done" applause by qubodup
mixed in (OpenGameArt, CC0; Kenney's falling sax jingle sounded sad); "Apple
Cider" by Zane Little Music and "Cozy Puzzle In-Game 2" by MintoDog from
OpenGameArt. Game
night is a personal project, so a sound is picked by fit, not licence; its
source is still written here. The music is normalised to
-18 LUFS and Opus 56 kbps (2.6 MB in all, embedded with the client in each
ABI's core library).

**Card games share one table.** Mau-Mau (2–8), Schwimmen (2–8) and Durak
(2–6) are traditional games and keep their names; one `GameKind` each, so the
lobby lists them beside the board games. `cards.rs` holds a French-suited pack
of 52 and every game deals a subset by lowest rank (`deck_from`: 7 → 32, 6 →
36). The hand is hidden information, and the `Game` trait already keeps it so:
`phone_screen(player)` gives each phone its own hand and `tv_scene` never
carries one (Schwimmen's reveal is the only public moment). Phones stay thin:
`PhoneScreen::Cards { desk }` is a generic desk — hand, selectable table cards,
buttons that say whether they need a hand card, several, or a hand card plus a
table card — answered with `Action::Cards { button, hand, table }`; nothing on
the phone knows the rules. The TV gets `TvScene::Cards` (seats with card counts,
open cards and a marker, centre piles, deck, trump, note, banner, the last four
events with server times). Where the rules would let the table read a hand — a
Durak player asked to throw in only when a rank fits, a Mau-Mau turn pausing
only when the drawn card fits — every player gets the same prompt with the
button disabled instead (user decision 2026-11-07: hidden information beats
pace). The house plays for absent players after the grace, like Heimlauf. The TV
table (`tv/cards/`) reuses Heimlauf's lacquered table, music and settings; the
52 faces and the back are one canvas atlas drawn at load, and every banner,
note and seat text is pre-drawn (`cards-texts.ts`), so no texture is made
mid-game. Rules and decisions per game: `plans/261107-0207-card-games-and-bauherr/`.

**Grand Allee, the hotel game.** An own game in the hotel-building family
(plots, a permit die, entrances, guests paying per night): own name, ten hotels,
board and prices. 2–8 players, currency Taler. `src/grand_allee/` holds the rules
as plain data and pure table operations (`board.rs`: a ring of 40 fields with ten
hotels; `rules.rs`, `building.rs`, `money.rs`, `offers.rs`), the live game on
the `Game` trait (`live*.rs`), and a simulator that tunes the numbers by playing
that live game (`play.rs`, `bots.rs`): a bot in every seat answers each question
after an assumed think time, and the clock jumps to the stage's next deadline
(`due`), so the simulator has no move order of its own. A game lasts 280 turns shared by the
players (30 to 120 rounds) or until one player is left; net worth is the score.
Every decision has a 20 s clock, after which the house bot decides, as it does at
once for an absent player and for the rest of a turn whose clock ran out. A player
who cannot pay sells whole properties in a sealed debt auction that opens on every
other phone at once; the bank buys what nobody bids on. A question that a tap opens
opens 400 ms later, so a double tap cannot answer it. A Doppelt permit the mover
cannot pay asks whether to auction that hotel (the permit then costs nothing) or
other properties until it is paid; the house takes the first, a simulator bot the
second when its other properties at the bank's price cover the shortfall. The
phone uses `PhoneScreen::Sheet` (`client/src/phone/PhoneSheet.tsx`): cash and
standing on top, the prompt with its clock (`closes_at`), options answered by
`Action::Choose`, and the player's hotels as cards in each hotel's colour. In a
debt auction the sheet carries a `BidRange` instead of options: the bidder sets a
sealed bid in steps of 100 up to their cash (− / + keys, quick shares of the
value, `SheetBid.tsx`) and sends it with `Action::Bid` (0 passes).

The TV board (`client/src/tv/grand-allee/`, `TvGrandAllee.tsx`) is three.js
drawing models built and baked headless in Blender by `tools/grand-allee-models/`
(see its README): `board.glb` (a winding road along a river and the sea, the Bank,
the Rathaus and a small town) and one GLB per hotel whose stages are
`stage_<n>_body` and `stage_<n>_tint` meshes. The evening light is baked into the
atlases and the materials are unlit; owner colours are the tint meshes, baked white
and multiplied on the TV, so one bake serves every player. `board_path.py` writes
`public/grand-allee/layout.json`, the one map from the rules' fields and hotels to
positions: the curve the pawns walk, each field's outline and each plot's land.
The hotel and board atlases are Basis ETC1S KTX2 with mipmaps, transcoded in a
worker (`public/basis/`, three's transcoder), about 19 MB of GPU memory for all
fourteen. Pawns are the players' avatars as sprites; the HUD is drawn into the
canvas. On the Skyworth box the board is up 2.6–3.6 s after the start and a whole
bot game runs at 58.6 fps. Plans and box measurements:
`plans/261108-0455-grand-allee/`.

**Every player has an avatar.** The host owns the catalogue of 22
(`avatars.rs`; ids are the folder names under `assets/game-night/avatars/`)
and keeps them unique: a hello may carry the phone's remembered avatar, which
it gets if free, else a random free one; `Action::PickAvatar` swaps in the
lobby only; a reconnect keeps it and leaving the room frees it.
`PlayerInfo.avatar` names it and `PhoneScreen::Lobby.avatars` lists all 22
with who-else-has-it, for the phone's picker grid. Every player marker is the
one `Token` component, so the head replaced the initial everywhere at once;
the race board draws it into its canvas tags. The pictures are rendered once,
offline, from the TV copies of the 3D models
(`tools/render-avatar-images.mjs`, matte, `Idle` pose): a 256² head and a
256x384 standing figure per avatar, WebP, 458 KB in all, in
`client/public/avatars/`.

**Every game ends in an awards ceremony.** `TvFinal` is the ceremony for the
quiz, the poster game and Heimlauf: the title above, a three.js band in the
middle (`tv/ceremony/`, its own lazy chunk) with a podium for the top three,
and the points strip with heads below; no HTML lies over the canvas. A red
velvet curtain (`ceremony-curtain.ts`) covers the band while the 3D avatars
load (GLTFLoader with MeshoptDecoder, shaders compiled and textures uploaded
behind it), so the low-detail flat figures are never seen (the user's call). It
opens once every avatar has arrived or failed, at the latest after 8 s (measured
on the box: all three in after 3.8–5.6 s), and draws aside in 1.6 s; the places'
clips and the jingle start with it. The curtain is two unlit panels hung from
the camera inside the canvas, hidden once open: 43.6 fps with it, as without.
A flat figure still stands in for an avatar that fails or comes later than the
curtain. The avatars have a
studio look (`ceremony-studio-look.ts`): the light of the model-viewer page they
were approved in (RoomEnvironment at exposure 1.1, diffuse only, no reflections
— the user's call). It is rendered once onto a white sphere as the camera sees
it, into an 8-bit sRGB picture holding half the light (half-float pictures
dropped the box to 25 fps). A MeshMatcapMaterial then draws colour × that
light, front faces only, under neutral tone mapping. With the camera fixed this
matches real environment lighting, which ran at 14–17 fps. Meshy's untouched
files ran at 17–20 fps even so (their scattered texture pieces are slow to
sample), so the ceremony copies keep the re-laid maps.
The winner plays `victory` and now and then `Happy_Jump`, second
`Cheer_With_One_Hand_Up`, third `Shrug`, then `Idle_15` (Meshy's plain
`Idle` turns the body by up to 81° and left avatars side-on); confetti and the
winner jingle (through the TV settings) open it as the curtain parts. The 22 ceremony avatars are
cut from the 2K game copies (`<id>.glb`, not the 15k-triangle TV copies,
whose ETC1S colour blotched and whose faces looked faceted close up) by
`tools/make-ceremony-avatars.mjs`: five clips, all ~51k triangles,
re-unwrapped by xatlas into fewer charts with gaps between them, with the 4K
master's colour and normal maps baked onto them (`tools/rebake-texture-atlas.mjs`;
Meshy's thousands of edge-to-edge islands bled into each other at every
smaller mip level). Each normal is turned into the new chart's frame, the frame
three.js derives on screen for a mesh without tangents. The output is a 2048
WebP colour map and a 1024 normal map, with meshopt: 40 MB in all, into
`android/app/src/main/assets/game-avatars/` — generated,
not committed, like the native libraries — and the TV app's WebView answers
`/game-avatars/<id>.glb` from its APK assets (`TvGamesFrame.kt`), so they are
in the app once instead of in the core per ABI, and the phones and the Rust
host never see them. Quality comes first, with 30 fps as the floor (the
user's call). The canvas draws at full resolution with MSAA, and the camera
stands back so the winner and its block fill about two thirds of the band.
Measured on the box with three avatars: 42–43 fps. The scale steps down by
0.25, to 1.25 at the lowest, if 90 frames average under ~32 fps, leaving out
the slowest tenth of them. Triangles, per-pixel lights, MSAA and double-sided
faces set the cost, not vertices.

**The phone page cannot keep the screen on; players switch the lock off.**
The page is plain LAN HTTP, so the Wake Lock API is missing. The NoSleep
library's fallback video was never attached to the page and kept nothing on;
our own looping clip in the page (0.146.1, full-screen behind the content)
did not hold the screen on the user's phones either, so it was removed in
0.146.2. The phone lobby asks players to switch off the automatic screen
lock instead. Only HTTPS (a real Wake Lock) would change this. The phone page
goes fullscreen (`phone/phone-fullscreen.ts`): browsers allow it only from a
gesture, so the join tap enters it and a later tap re-enters it after a swipe
out; iPhones have no page fullscreen.

**TV settings live on the TV.** `tv/settings/tv-settings.ts` holds *Ton*,
*Musik*, *Effekte* (0–10) and *Spielfiguren* (pawns or avatars) in the TV
page's localStorage, with a small subscribe store; the defaults reproduce the
mix the sound was tuned with (music 4, effects 8). The panel opens from the
lobby's *Einstellungen* button or with OK on the race board, and it catches
the remote's keys in the capture phase while open. The TV app's Back asks the
page first (`window.tvBack()`), so Back closes an open panel instead of
leaving the game. The race board builds both figure styles up front (a pawn
and an avatar sprite on a colour disc per piece), loads every picture and
compiles both before the first frame, and switches them with visibility only:
nothing is created mid-game.

**The TV page scales with the screen.** The box's WebView lays the page out at
960x540 CSS pixels (density 2), so the TV root font size follows the viewport
alone (`styles.css`) and every scene is the 1920x1080 layout at half scale. A
quiz still sits beside its question rather than above it; stacked, a still,
two lines of question and eight seats are taller than a 16:9 screen.

**Tools.** `crates/mediagram-game/tools/smoke.mjs` plays a night with bot
phones against the dev binary `game-night-dev`.

### Spieltisch, the games' own app

The games are becoming an Android TV app of their own, **Spieltisch**
(`com.mediagram.spieltisch`, `android/spieltisch`), so a game release no longer
rebuilds and re-installs the media app. It has its own version line
(`crates/mediagram-game`, `crates/spieltisch-core`, the client's `package.json`,
its `versionName`, and the phone controller), starting at 0.175.0. Until the
media app hands over its Games row, both apps carry the games. Each stops its
night when it leaves the screen, and either takes another port when 8080 is
taken.

**Its own native core.** `crates/spieltisch-core` links `mediagram-game`, not
`mediagram-core`, and exports `start_night(config, images)`, `night_back`, `stop_night` and
`game_catalogue` (`libspieltisch_core.so`, four ABIs, `scripts/build-spieltisch-core.sh`;
bindings by `scripts/generate-spieltisch-bindings.sh`, committed under
`android/spieltisch/src/main/kotlin/uniffi/`). One night per process, held in a
static slot as `Core` holds the media app's. `scripts/check.sh` regenerates the
bindings from a host build and fails on a difference.

**The library is the media app's.** Spieltisch holds no Telegram session, TMDB
key or profile; the games' decision log goes to its files folder. At each
night's start `MediaLibraryBridge` reads `content://com.mediagram.android.games/night`
from the media app's `GameNightProvider` (`android/feature/games`), off the
main thread and for at most 8 s: one row, `kids_locked` (1 or 0) and `titles`,
JSON in `GameTitle`'s shape (`crates/mediagram-game/tests/fixtures/night-titles.json`
holds both sides to it). The provider answers for the active profile under the
in-app night's rules (`gameNightConfig`, then `Core::game_night_titles`, which
leaves out every title that is not kid-safe when the lock is on); no core yet,
nobody chosen, or any failure answers locked with `[]`. To Spieltisch a missing
media app, a refusal, a row that is not a night, or no answer within 8 s are
one answer: locked, no films, and a line on the shelf that the library's films
aren't available. Only an explicit `0` unlocks, and a locked host drops every
title that is not kid-safe again. JSON the core cannot read means no films,
never no night.

```text
Spieltisch                                  media app (signature permission)
MediaLibraryBridge --query /night---------> GameNightProvider -> GameNightExport
  -> start_night(titles, kids_locked)          files/game-night/night.snapshot, if built for
                                               the active profile; else gameNightConfig +
                                               Core::game_night_titles, and kept
host /img/{i}/{which} -- own path unreadable
  -> ImageLookup / textless PosterLookup
  -> MediaImages.fetch (spawn_blocking)
  -> MediaImageCache --openFile----------> /image/{key}/{poster|still}, /textless/{key}
     copy in cache/media-images/                         only titles tonight's night holds
```

**Images cross as files.** The titles' poster and still paths point into the
media app's storage, which Spieltisch cannot open. When the `/img` route cannot
read a title's own file it asks the embedder's `ImageLookup`
(`mediagram-game/src/image_files.rs`; the media app's own night passes none and
reads its files directly), and the textless lookup takes the same way:
`spieltisch-core` turns both into the Kotlin `MediaImages.fetch(key, poster|still|textless)`,
called on `spawn_blocking` since Kotlin blocks on the provider. `MediaImageCache`
opens `/image/{key}/{poster|still}` or `/textless/{key}`, copies the file once
into `cache/media-images/` (named by kind and the encoded key, moved into place
whole) and answers the copy's path; the route types a copy without an
extension by its first bytes. The provider hands out a file only while the
active profile's night holds that title, so a key cannot fetch a picture the
profile may not see. It is read-only.

**One permission guards it.** `com.mediagram.android.permission.GAME_NIGHT`,
`protectionLevel="signature"`, declared by both apps, so Spieltisch holds it
whichever is installed first; the provider requires it in the media app's
manifest and checks it again in code. For Android 11+ package visibility,
Spieltisch queries the provider's authority. The provider reaches the repositories through a
Hilt `@EntryPoint`, since a provider is created before `Application.onCreate`;
a Spieltisch ask may be what starts the media app's process.

**The night is answered from a snapshot, by its fingerprint.** Building it
lists the whole library and builds every title, 2.5–4.7 s on the emulator with
a 28,038-set library (1,764 titles, 606 KB of JSON). So the media app keeps
the last night built, in memory and in `files/game-night/night.snapshot`
(`NightSnapshotStore`: a header line, `app_version`, `fingerprint` and
`kids_locked`, then the titles' JSON, written to a temp file and renamed into
place; a file another version of the app wrote is not read, since an update
may change the rules). The fingerprint (`NightInputs`) is a SHA-256 over
everything that decides a night, read from the core itself on every ask, not
from a repository's memory:
- the chosen profile's id, kids flag, kids limit and hidden sections (as
  `hiddenFrom` applies them)
- the household's kids marks (`snapshot(id)`)
- a catalog marker from `catalogFacts()`: the installed index's origin and
  `publishedAt`, its set and poster counts, and `described`
- the app's `longVersionCode`

The marker's parts each cover something:
- **The index:** it is opened read-only, and every install moves `current` to a
  version directory of its own, so a new set or a changed rating always comes
  with a new version.
- **The poster count:** notices artwork that arrived.
- **`described`:** a digest of every row and column of this device's fetched
  descriptions (`details.db`). Those fill in genres and original language
  where the index has none, so they can move a title into Anime without a new
  index; a row corrected in place moves the digest as much as a new one.
- **An unreadable version:** an installed catalog whose version cannot be read
  has no fingerprint and answers locked.

The marker is the costly part of a fingerprint: 225–400 ms on the emulator,
mostly counting the playable sets. The description digest adds no measurable
time with 2,000 described titles (7 ms on the host); the profile and the marks
take under 25 ms.

An equal fingerprint serves the snapshot. Anything else builds the night:
- **Single-flight:** under one `Mutex` with the fingerprint, read and write, so
  asks that come together build once.
- **The core in hand:** the build lists the sets from the core the inputs were
  read from; looking it up again could wait for the next sign-in.
- **Time limit:** the locked work gets 30 s, after which the ask answers
  locked and lets the next one in.
- **Kept only if unchanged:** a build is kept only if the fingerprint, read
  again afterwards, still equals the one it started from. Otherwise the ask is
  answered locked with `[]`.
- **Failure:** any failure, including a build that fails after the rules
  tightened, answers locked with `[]`, never the night held from before. Nothing rebuilds in the background:
progress and other watch state are not inputs, and a night is built only when
an ask finds the inputs changed. Image and textless checks use the night
`/night` last served, recorded under the same lock and held in memory, so a running night keeps the titles it
started with, as a night hosted here does; a process with none serves one
first. Spieltisch also asks for `/night` once as its process starts
(`NightSession`, the answer dropped), so the media app is up, and a build
already under way, when the night's own ask comes; each night still asks for
itself. The host remembers, per night, the titles found to have no textless
poster (`spieltisch-core/src/media_images.rs`), since every poster request
asks for one first; the asking runs in a task of its own, so a miss that
answers after the image route stopped waiting (300 ms) is remembered too.

Measured on the emulator (`plans/261110-2230-spieltisch-app/phase-02-media-app-provider.md`,
Result): served from the snapshot, a warm media app answers `/night` in
0.40–0.60 s, a cold one in 0.93–1.34 s with Spieltisch's warm-up and
1.08–1.87 s without it. The first ask after an input changed builds: 4.2 s
warm, 4.5 s cold with the warm-up (the night's ask joins the warm-up's build),
5.0 s with no readable snapshot at all, all inside the 8 s limit. A first image
check answers in 5–134 ms.

**Signing keys.** Both apps declare the permission, which works in either
install order when both are signed with the same key. An app signed with
another key than the one that declared the permission first fails to install
with `INSTALL_FAILED_DUPLICATE_PERMISSION` rather than being refused by the
provider. It cannot happen on this machine: its debug and release builds use
the same key, `~/.android/debug.keystore`.

**The shelf comes from the host.** `game_catalogue()` lists `CATALOGUE` in the
lobby's order (kind as the pages name it, name, seat range), so a game added
there shows up on the shelf with no Kotlin change. Art is keyed by kind
(`ShelfGames.kt`); a game without any shows its name. The screen is the media
app's `TvGamesFrame`, copied and adapted: Back from a game asks the page first,
from the lobby returns to the poster that opened it, and on the shelf leaves
the app, which stops the host; going to the background does the same, and the
activity handles configuration changes in place so a gamepad connecting does
not end the night. No Hilt: `SpieltischApp` holds one `NightSession` per
process (the core, the NSD announcer, the scope stops run on and the latest
stop), since leaving finishes the activity while its stop may still run. A
relaunch's night waits for that stop, and the stop withdraws the announcement
again after cancelling the start, so a start that finished late leaves no dead
table on the phones. On API 37 and up `MainActivity` asks for
`ACCESS_LOCAL_NETWORK` before the night starts; without it the phones can
neither reach nor find the host. The shelf's look (mediagram's colours, focus
and poster row) is copied as values, since the media app's TV theme lives in
`ui-tv`, which Spieltisch cannot depend on without the media core. Cleartext is
allowed to 127.0.0.1 only, and the page's client refuses navigation to any
other host; the ceremony avatars are served from the APK's assets as in the
media app (`make-ceremony-avatars.mjs --out=android/spieltisch/src/main/assets/game-avatars`).

### Reporting a broken video

Every player has a Report button (the top bar everywhere, and beside Retry on
the phone and TV failure screen): one of four reasons, an optional one-line
note of at most 200 characters, and it reads Reported until another title
opens. Kids may report. Nothing is queued: a failed send says "Couldn't send.
Try again later."

**Where it goes.** A plain-text reminder scheduled 60 s ahead into the
account's own Saved Messages, not silent. Every screen is the same Telegram
account, and Telegram notifies an account of its own message only when it is a
scheduled message in Saved Messages (verified 2026-10-17). It is never sent to
the library channel, and no reader parses `#mlib-report` (`docs/mlib-spec.md`
§7c).

**The text** is written once in `web/public/lib/report/report-text.js` and
ported to `ReportText.kt`; both are held to
`web/test/fixtures/report/report-text.json`. The title line is the player's
`titleLine` (a film without its year) and the set id on the last line
identifies the title.

**Web.** `POST /api/report` validates (400), answers 404 when the server has
no sender, 503 when Telegram is unreachable, the send fails or it has not
answered within 15 s, else 204. The browser waits 20 s, so it hears that 503
rather than giving up first. The bound cannot recall the send: teleproto
cannot cancel it, so one that answers after the 503 still lands, and a retry
then sends a second. The title and
the error are folded onto one line each, so no caller can write a line of its
own into the report.
`TelegramReportSender` calls `sendMessage("me", {schedule, parseMode: false})`.
The video `error` event and conversion errors are recorded per title, so the
report carries the last one. The preview harness logs reports instead of
sending.

**Android.** The Rust core exposes `report_send(text)` (grammers
`schedule_date`); `ReportChannel` calls it and the player keeps
`PlaybackException.errorCodeName` for the error line. A send that has not
answered in 20 s fails, as the web's does; the cancel drops the request only
if it has not reached Telegram yet. On the TV the note opens
in its own window on OK, so the D-pad never raises the keyboard.

### Parity with the web player

Reached by
`260924-0139-android-web-parity` (removed; in git history),
which superseded the unbuilt half of
`260922-0124-android-web-parity` (removed; in git history):
search and genre pages, audio and subtitle choice, speed and framing, up next
and queues, fullscreen gestures, picture-in-picture and a media session,
series preload with offline badges, notes, profile removal and the List/Grid
shelf toggle. The Featured reel and the 48-a-page Movies shelf followed in
0.54.0 (`FilmPages.kt` ports `pager.js`); where the web keeps the page in the
address, the phone keeps it in the shelves' saved state, which a title opened
over them no longer clears. What differs on purpose, and why, is recorded in
`docs/superpowers/specs/2026-09-20-android-system-menu-and-playback-stats-design.md` §9.

**Motion.** The phone and tablet move as the web player does: the web's tokens
(`--ease`, `--ease-out`, `--hover`) and its entrances, drifts, hover lifts and
nudges live once in `core/designsystem` (`Motion.kt`, `Entrance.kt`, `Drift.kt`,
`HoverMotion.kt`, `CatalogueIndication.kt`), and each screen applies them at its
own call sites. Ripple is gone: `MediagramTheme` provides the web's press
feedback (a quiet tint, a 2dp focus ring, a snapped 0.98 press), and Material's
buttons are drawn through `ui.chrome`'s `Catalogue*Button` wrappers, since they
hard-code their ripple. "Remove animations" is the web's reduced motion. Loading
is the film leader's 3·2·1 countdown on both surfaces, a shared feature: on the
web `web/public/styles/film-leader.css`, the `index.html` template and
`lib/film-leader.js`, on Android `core/designsystem`'s `CountdownLeader.kt`. What
differs on purpose:

- **The player's controls linger 4 s, not the web's 2.6 s.** A finger has to
  reach the controls; a mouse is already there.
- **The phone's compact header is quick-return.** It hides on scroll down and
  returns on any scroll up; the web's phone masthead only reappears at the top.
  The phone's screen is short.
- **The cover holds still for TalkBack** (`HomeCover.kt`).
- **The first page after a cold start rises in**, where the web's first draw
  is still.
- **The countdown leader also shows in the channel picker and the in-page
  "Loading your library…" waits**, in the page's inks. The web shows words only
  in its channel list and never shows in-page waits (it waits under one block
  before routing). A deliberate addition: loading is the leader on every surface.
- **The cold-start block shifts once when the library chrome arrives**: the
  setup check and the profile gate have no rail or bar to sit beside.
- **The TV gets the countdown leader later**; its loading screens live on the
  unmerged TV branch.
- **The library's "fetching" line** (`CatalogShelves.kt`) is still an
  indeterminate progress bar; the web's counterpart is to be checked before it
  changes.
- **Tab labels are the accent, the web's are ink** (`title-page.css:126,131-132`).
- **The tab row scrolls the chosen tab into view** (Material's, about 250 ms);
  the web's tab list never scrolls itself.
- **The player tool does not turn accent while its menu is open**
  (`player-card.css:82`).
- **The marks' popups (Add to list, Kids) do not hold the controls**; the web's
  `holding()` does (`player-hud.js:28-31`).
- **The resting player controls cannot be woken by TalkBack or a keyboard, and
  the controls do not hold while a control is focused**; the web holds on
  focusin (`player-hud.js:31-45`). A known gap, for a follow-up.
- **The player's top-bar marks and Notes have no outline**, so on hover or press
  their label turns the stage accent where the web's `.ghost` outline does
  (`web/public/styles/playback.css:97-99`).
- **GenreLinks looks unlike the web's `.genre` pill** (`catalog.css:96-97`).
- **SettingsIndex's chosen-row fill snaps**; the web has no counterpart.
- **The cover's tap shield is a timer**, not tied to the fade.
- **Under the Blurred backdrop the cover's drift is the one thing to watch**:
  see the changelog's 0.121.0 entry for the frame times.

### Settings and System

Settings is a two-pane index — Telegram, Storage, Appearance, Profile, System — on phone,
tablet and television alike (`260927-1731-android-settings-system-redesign`), set in
the web player's own dark tokens (`DESIGN.md`). Compact width shows the index or one
open section, never both, with Back returning to the index; expanded width (tablet
landscape, television) shows both panes together. System is a Settings entry, not a
screen of its own — the app menu also keeps its own direct shortcut to System, so
"what is this player doing right now" stays one tap away without detouring through
the index. Storage merges the local cache and the home cache server into one section;
Appearance offers Theme, Accent and Artwork (see `DESIGN.md` § Artwork). Profile
shows the chosen profile and its default subtitle language (Off, German or English,
stored as preference `profile`/`subtitle`, disabled until a profile is chosen), as the
web player's Profile panel does. Television
polls System every 2 seconds while it is visible, the same as the web player.

### Film preload (Android only)

`FilmPreloader` (`core:playback`) is a FIFO queue, one film writing at a
time, over the same `CacheDataSourceWriter` and `DownloadLane`
`SeriesPreloader` already shared — a film preload and a series preload can
never write concurrently, so the LAN PUTs to §12 that fall out of either
never race each other. A tap enqueues; the worker judges `fits` against the
live cache budget (`CacheProvider.occupancy`, not a stale read) only once a
film reaches the front of the queue, reserving whatever film is currently
open in the player if it differs from the one being judged — an open title
never pauses the judgement itself, only the writing that follows it.
`ActivePlayback` publishes what the app's one `ExoPlayer` has open by
listening to it directly (`Player.STATE_IDLE` is the only thing that clears
it — backgrounding the app or merely pausing does not), which is what lets
the worker pause for `PauseReason.Playing` without the two ever touching each
other's state. A `dataSync` foreground service (`PreloadService`) keeps the
queue alive while the app is backgrounded, within Android's own 6h/24h
ceiling for that service type; `POST_NOTIFICATIONS` stays undeclared, so its
notification is built but never shown, and the time limit pauses every film
the queue was holding (`FilmPreloader.pauseForTimeLimit`), reported apart
from the queue itself (`timeLimitPaused`) so a Preloads page can still name
them once the queue proper is empty. None of this is persisted — a process
death loses the queue and any paused-by-limit list alike, same as
`SeriesPreloader` before it.

The queue is visible in two places. A film page's own control
(`TitlePreloadViewModel`) shows that one film's state, its bar filled from
the engine's own progress callback (not from a poll — the bar is live), and
polls §12's `GET /v1/sets/{id}` every 5s only for the quiet "Home server:
x of y GB" line, which the bar itself never needs. A Preloads page
(phone/tablet and TV) shows every film at once, built from
`FilmPreloader.queueOverview` (one ordered list — the running film first,
with its held bytes and pause reason if paused, then every waiting film in
FIFO order — a pure projection over the same `QueueSnapshot`
`FilmPreloadQueue` already published, changing none of the engine's own
rules) merged with `timeLimitPaused`. A queued film's own label on its film
page reads the same list to name what it is waiting on: the running film's
own title and percent when it is immediately next, otherwise how many films
stand ahead of it. The control's own states and visual pair are
`DESIGN.md`'s (§ Pill).

### Television display: refresh rate, backdrop width, baseline profile

- **Refresh-rate matching is TV-only, and deliberately so.**
  - `TvDisplayModeMatch` (`ui-tv`) reads the selected video track's frame rate, asks `pickDisplayMode` (`core:playback`, pure and unit-tested) for a mode at the current resolution whose refresh is a whole multiple of it, and sets `preferredDisplayModeId`. It decides against the mode the screen had when the player opened, and restores the previous preference when nothing fits and when the player closes.
  - A mode that already fits is kept, because every switch blanks the screen while HDMI re-syncs.
  - `buildPlayer` turns Media3's own switching off on TV, so only one mechanism is in charge. Phones and tablets keep Media3's default, `ONLY_IF_SEAMLESS`. The web player cannot switch display modes at all.
- **Backdrop width follows the screen it fills.**
  - `DeviceBackdropWidth` asks TMDB for 1280 on a television or tablet and 780 on a phone. A TV reports 960 dp, but its hero spans 1920 px.
  - `poster_files.rs` records the width each backdrop was fetched at in `<key>.width`, and treats one held narrower than requested as missing.
  - Keys the artwork table holds (custom backdrops) are never fetched over.
- **The release build carries an app baseline profile** from `:baselineprofile`, a D-pad journey through Home, the Movies wall and a title page.
  - The journey only navigates. It never presses OK on a setting, never plays anything and never presses Back at the library root.
  - `gradle.properties` keeps the app installed after the connected run, because AGP would otherwise uninstall it.

### Subtitles (Android)

A set's tracks come from `catalog_subtitles` on the Rust side, one of two
places a reader never has to tell apart: `subtitle_tracks`, once the
uploader has extracted a bundle for the set, or, until then, the set's own
inline `assets` rows, oldest-reader compatible. `SetSummary` carries the
resolved list (`SubtitleTrack`, mapped to `model.SubtitleTrackInfo`) plus
`alang`/`slang` — the file's own audio and subtitle languages, kept apart
from the tracks a viewer can pick, which back a title's "Subtitles" fact
instead.

A bundled track's text is fetched on demand and cached on disk, one gzip'd
JSON document per set (`<data_dir>/subtitles/<sha>.json.gz`), sha-checked
and written through a `.tmp` + fsync + rename so a killed process never
leaves a half-written file where a real one is expected. The cache is
size-capped at 64 MiB (LRU, mtime touched on a hit) rather than pruned on
install: an index a step behind (no `subtitle_files` table yet) must not
wipe bundles this device already holds. `Core.holdSubtitles(setId)` warms
one set's bundle — `CacheDataSourceWriter.write()` calls it ahead of every
preload write, so both `SeriesPreloader` and `FilmPreloader` take a title's
subtitles along with its video, best-effort. `Core.holdCourseSubtitles(setId)`,
called when a lesson opens, warms that lesson's own bundle plus up to ten
that follow it in the course's own order (`group_key`, then chapter, then
lesson number) — never the whole course, which can run to hundreds of
lessons.

`SubtitleChoice.kt` (`feature:player`) ports the web's playback rule
exactly, checked against the shared fixture both surfaces are held to
(`web/test/fixtures/subtitles/choice-cases.json`): off by default; a
forced track in the playing audio's language shows regardless, even with
subtitles off; a per-show remembered choice wins over a per-profile
default; and a toggle-on picks this session's last regular track, then the
profile default, then a track in the audio language, then the first one.
`SubtitleChoiceController` resolves it against `AudioChoiceController`'s
own notion of the playing language (falling back to the set's `alang`
until ExoPlayer answers) and `PlayerPreferences`, loaded under the show's
own scope for "remembered" and a fixed `"profile"` scope for the default.

### The television surface

`:ui-tv` is a second renderer over the same `feature:*` ViewModels and UiState,
built on `androidx.tv:tv-material` 1.1.0. It shares the catalog logic (`feature:catalog`)
with the phone, including tab labels, shelf grouping, and title facts; the player
lifecycle is the same shared contract (`feature:player`). Shared pure rules that lived
`internal` inside `:ui-mobile` (≈30 formatters, the library-position model, player
state and control mechanics) moved into `ui-common` so both surfaces call one copy.

The TV surface's own responsibility is navigation and focus: a D-pad and remote
buttons (center/play-pause/left/right/back) steer every screen. Its chrome mirrors the
web player's own: a left rail (collapsed to icons until the remote reaches it, then
opening to My List, Continue, Latest, Genres, Settings, System and the
library's own tally) beside a departments bar across the top (Home, the shelves,
Collections, search, the viewer's avatar, ⋮) — see § Television differs, above, for
where the two surfaces deliberately part. Home draws the web's own magazine layout
(`ui-tv/.../catalog/home/`) as a `LazyColumn`: a cover story bleeding under the bar
(which reads translucent-to-opaque from the same list, through `ui.common.chrome.coverBlend`,
shared with the tablet's own hero pages), three feature cards, Continue watching beside
a pull-quote, Recently added beside This month, then Latest series and Latest courses —
each poster or resume-card row a plain, always-composed row (at most eight items, or
`HOME_ROW_LIMIT` for Continue/Next up), not a plate wall's own virtualised hundreds. A
department's own wall stays plain: 2:3 posters in a 6-column grid, no sideways scroll;
focus restores to the plate that was opened when Back returns. Settings and System are
reached straight from the rail; Settings there includes cache volume choice ("Where" —
a USB drive must be set up as *portable* storage to appear; adopted storage never
shows), and the home cache server status and pairing.

### TV themes

Settings › Appearance › Theme picks one of three TV styles (`designsystem.TvStyle`,
stored per device under `appearance_settings/style`): **mediagram** (the default),
**Apple TV style** and **Google TV style**. `ui.tv.TvTheme` provides it as
`LocalTvStyle` and builds the scheme from the style's `TvLook` neutrals and the type
from its face (`TvType` in `core/designsystem/TvTypeScale.kt` reads each role through
`inTvStyle()`; `TvType.Base` holds mediagram's). Focus (`ui/tv/TvFocusStyles.kt`,
`TvFocus`), colours (every TV colour reads the scheme, pinned by
`TvColoursComeFromTheSchemeTest`) and type follow the style everywhere except the
player, which keeps mediagram's look in every style (`TvMediagramPlayerTheme`; the spec
keeps the player as it was). Layout differs in three places only: the chrome
(`chrome/TvChromeBar.kt` selects `TvDepartmentsBar` with the rail, `TvCentredTabBar` or
`TvTopBar`; no rail outside mediagram, its rows in the avatar's menu, every rail-row
focus request through `TvChromeFocus.railTarget`), the Home cover (`TvCoverSlide` /
`TvInsetCardSlide`) and rows of titles (16:9 stills, `catalog/TvRowCards.kt`; walls
stay posters). No style blurs anything: TV blur measured too slow on the box
(2026-10-10).

**Deliberate difference:** the phone, the tablet and the web have no themes yet. The
phone and tablet store the setting and ignore it; the web does not have it. Each gets
its own design round, TV first at the user's choice (2026-10-10).

### Television differs from the web player

Deliberate differences between television and web, all in the context of the Surface
Parity rule (web is the reference; a gap on TV is a defect unless written here):

- **No list/grid toggle.** Shelves open as walls of plates only, with no list mode to
  switch to: a remote walks a grid of plates more easily than a dense list.
- **Text entry on a screen of its own.** Sign-in, the application id and hash, the TMDB
  key and the home cache server's address and token are each asked as one question with
  the system keyboard, rather than in a field inside a list. Pairing from the phone would
  be new machinery and is out of scope.
- **A blank TMDB key is ignored, not saved.** The phone clears a stored key when a
  blank one is saved; on a remote the keyboard's action key is also how a viewer who
  only came to look closes the keyboard, so the television ignores a blank answer and
  offers "Clear stored key" behind a confirmation instead.
- **Player card: controls hold their size under focus.** The television's 1.08 grow is
  dropped over the stage (the focus border stays); profile screens and the two mark
  dialogs keep it. The card follows the design rule that controls never lift or grow on hover
  or focus; the border alone says where focus is.
- **Player card: the remote opens the sidebar on "Now playing".** A pointer has no
  position to start from; Back, ✕ or picking a row leaves it.
- **Player card: Back closes one layer at a time** (menu, sidebar, up-next, notes, stats,
  card, then leave), since a remote has no tap-outside to dismiss.
- **Player card: stats wait while the episode sidebar is open.** Beside the 360dp sidebar the
  narrowed card wraps its transport onto a fourth line, and a 540dp-tall screen has no
  height left for the title, the five stats lines and that card together. The stats come
  back when the sidebar closes; Back still closes the sidebar first, then the stats.
- **Player card: top-bar marks and Notes are reached Up from the seek bar.** There is no
  pointer to hit them; each row's ends are fenced so focus never slides out sideways.
- **Player card (phone): ⓘ and ☰ sit beside the transport**, centred, not at the far
  right as on the web: at the far end they strand on a wide tablet card, away from the controls they accompany.
- **No picture-in-picture or gesture controls.** No touch input and no window to shrink
  into; these are phone affordances. The player does publish a media session, so the
  remote's play/pause, fast-forward and rewind keys reach it.
- **Always dark; Appearance offers the accent and artwork.** A television is watched in
  a dark room, where a light 10-foot page glares, so the theme choice (Dark/Light/Auto)
  is not ported; the seven accents are, at their dark values (user decision,
  2026-09-26), and so is the Artwork setting (Default/Blurred/Artwork/Solid) added
  2026-09-27 — a hero's art softens or drops the same way it does on phone and tablet.
- **The rail collapses to icons until the remote reaches it.** The web's own rail is
  always the width its counts and tally need; a television at ten-foot floors would
  spend 30% of a 960dp screen on it if it stayed that wide over every cover and hero, so
  it opens to the web's own width only while focused and stays icons-only (no counts, no
  tally) the rest of the time, matching the box's own launcher.
- **Continue, My List, Latest, Genres, Settings and System live on the rail,
  not the departments bar.** The web's own side rail carries the same six as utilities;
  the bar mirrors only its `nav.departments` row (Home, the shelves, Collections).
- **The avatar opens the menu; there is no ⋮.** The menu page offers Switch profile
  (the avatar's own job on the web and the tablet), then the phone's three
  Android-only actions (Update library, TMDB key…, Start over) plus Preloads. Seven
  departments, search, the avatar and ⋮ ran past the box's 960dp even at the web's
  sizes, and the web itself overflows at a window that narrow (user decision,
  2026-10-09).
- **Department pills carry no counts.** "Movies", not "Movies 1143": the counts were
  the rest of what the bar could not fit, and the rail's foot still carries the
  library's totals (user decision, 2026-10-09). A row that still overflows (a longer
  name, a larger font scale) scrolls and fades only the end it clips.
- **One scale, a quarter under the web.** Text and spacing read the web as a 1280px
  viewport on the 960dp screen, one CSS px to 0.75dp/sp (`TvScale`), with no size
  floor, since the web has none (user decision, 2026-10-10). Display titles keep
  the web's 1920px size halved through the same scale (45/42/34.5/21.6sp), since
  their `vw` clamps at 1280px would have grown them a third (DESIGN.md's
  Television Rule).
- **Pressing a department pill keeps the remote on the pill.** The page swaps and shows
  from its own top; Down is what steps the remote into it. Jumping straight to a plate
  the instant a pill is pressed would scroll a department's own hero away before a
  viewer had seen it.
- **A pushed frame (a title, a season, Search, Latest, Genres, Settings, System…) fills
  the whole screen, rail included.** The tablet keeps its rail beside a pushed frame;
  a television's pushed pages are laid out and focus-tested for the full 960dp, Settings
  and System already draw their own index rail for the same reason the tablet exempts
  them, and the player is full screen regardless. Back returns to whichever pill, rail
  row or plate opened the frame.
- **A title page's spread stands at least 380dp, not the web's `clamp(560px,76vh,780px)`.**
  At a television's fixed 540dp that clamp would fill the screen and hide the tab row,
  which is how a viewer learns the page goes on below the spread — the department hero's
  own reason for its fixed height.
- **A title page's tabs switch on OK, and the tab row rises to the top of the screen when
  the remote reaches it.** The web's tabs switch as the arrow keys move along them; on a
  remote that would swap the panel under the viewer at every step on the way to the tab
  they meant. The rise is because Details and About are facts with nothing in them a
  remote can rest on, and a remote cannot scroll a page it has no stop in: the panel has
  to be on screen whole the moment the tabs are. Up from a panel, and Down from the
  pills, land on the tab that is showing, as on the web.
- **A show's season picker is a row of pills, not a drop-down.** Each pill says what the
  web's option says ("Season 2 · eight episodes"); every season is in sight and one press
  away, where a drop-down would hide them behind an extra press and a list the remote
  then has to leave. Picking is OK, not focus, for the tabs' reason. The row is the only
  place the shown season is named, so it scrolls to keep that pill in sight, and the remote
  arriving from the tabs above lands on it, as Up from a panel lands on the showing tab.
- **The ⋯ beside a title's pills opens its choices in the pill row itself.** The web's
  list floats under the button; on television it would sit over the tab row the remote
  reaches with Down. Back, or a choice, closes it onto the ⋯.
- **No voice search.** Search is typed through the system keyboard.
- **Artwork needs a TMDB key on the device.** As on the phone, posters and backdrops come
  from TMDB; a device without a key shows initials on plain plates.
- **The local network permission is asked only on API 37 and up.** The home cache server
  block itself shows on every version; `ACCESS_LOCAL_NETWORK` does not exist below 37, so
  there is nothing to ask for there.
- **Notes links not followable, no ✕, no selection.** A remote has no pointer. Links
  stay as words; Back and the Notes button close the panel instead of a ✕.
- **Tools at the end of the marks row.** The remote has no top bar to press; Notes,
  settings and info sit at the row's end alongside marks, where focus can reach them.
- **Captions key and a CC button.** The remote's captions key (`KEYCODE_CAPTIONS`) toggles
  regular subtitles in every player state, like Next, and brings the controls up briefly;
  the CC button before the settings gear does the same for remotes without the key.
  The phone has the same CC button in its transport row, and the web its `c` key. Both
  show only for a title with a regular track (the key is a no-op elsewhere and never files a choice); forced tracks follow their own rule. The
  settings panel's size and sync rows show for any title with a track that can show,
  forced-only included; its language rows need a regular track.
- **Up-next card floats opaque, bottom-right.** The web's card is translucent; a
  countdown read through a bright scene is unreadable on a TV.
- **Mark finished as a row under a Continue plate.** The phone's long-press menu has
  no remote equivalent; the action sits one press down from the plate.
- **Previous key is previous-in-run, never restart.** The remote's Previous steps back
  through the episode run; the session's own "restart this title" is not exposed.
- **A PIN is typed on a pad, and goes at the fourth digit.** The web asks in a masked field
  with OK; a remote may have nothing but a D-pad, so the television shows a telephone grid
  (1–9, 0, Delete) the remote walks, starting on 1, with dots for what is typed. A remote
  with digit keys types with those too. Four digits are handed over at once, so there is no
  OK, and a new PIN's second entry is a step of its own headed as the web's second field is
  ("The new PIN again").
- **Manage profiles opens a person's choices in a dialog.** The web lays Reset PIN, the
  limit select and Remove along each row; on a remote, buttons along a row would make Down
  mean "next button" in one row and "next person" in another. Each row is one stop that
  opens its choices (a kid's FSK 6 / FSK 12, the current one holding the remote, then
  Daily limit, which opens its own choices, then Remove; a grown-up's Reset PIN, then
  Remove), and Remove still asks in the web's words. Adding asks the name on a screen of
  its own and then, for a kid, the age limit (FSK 6 first) and the daily limit, for the
  reason text entry has its own screen everywhere on the television.
- **The player's Kids choice is a dialog.** The web's select ("For kids": Not for kids,
  From 6, From 12) opens from the Kids mark as a dialog over the film, as Add to list does,
  so it outlasts the controls fading behind it.
- **Subtitles never rise above the title band.** Lifted clear of the controls and the
  up-next card as on phone, but capped under the statistics bar so cues over the title
  are not unreadable.
- **Home's cover has no pause button, no drift zoom.** The phone's cover pauses its own
  rotation on hover and slowly zooms its art; a television has no pointer to hover with
  (focus anywhere on the cover already holds rotation, so a button could only ever be
  pressed while it is already held) and a continuous redraw for the zoom is not worth it
  on a weak box. A focused dot in the cover's own pager still shows its film, focus-select
  rather than needing OK.
- **Continue's resume cards carry the offline badge; the phone's do not.** Continue was a
  plain plate row before this surface's own magazine layout, and that row's plates always
  showed it — kept on the new card rather than silently dropped, since a viewer relied on
  it to know a title would play from local storage before pressing Watch now.
- **A franchise's introduction is a stop, and a fresh visit lands on it.** The web
  sets TMDB's introduction inside the franchise hero and opens the page at its top with
  nothing focused; a remote has to rest somewhere, and these introductions average about
  350 characters — seven lines at the ten-foot reading size — so landing on the first
  film would scroll the franchise's own name off the screen before anyone had read it.
  The television keeps the introduction inside the hero (which grows to hold it rather
  than cutting it short) and puts the remote on it; Down is the first film, Up from a
  film reads it again, and coming back from a film lands on that film as every wall does.
- **"No lists yet." under Your lists.** With no lists, the web leaves an empty grid above
  "＋ New list"; television, like the phone, says so in one quiet line, because an empty
  band under a heading reads as something that failed to load.
- **A course's page keeps a resume line under its head.** The web's course page is its
  shelf head and its lessons, and so is television's — but under the head the television
  also offers the series pill's verb and the lesson by its number — "▶ Continue lesson 4",
  never a show's "S1 E4", since a lesson's season is only the chapter it was filed under;
  a lesson with no number goes by its name — one press up from the first lesson. A pointer reaches lesson 87 of a 162-lesson course in one fling, where its
  progress rule shows; a remote walks there a row at a time.
- **Home unmounts under a pushed frame and rebuilds on return, not kept alive.** Tried
  (2026-09-30): keep the root library composed and laid out under every pushed frame —
  hidden, inert, its catalogue state frozen — so Back would be a focus restore rather than
  a full compose/measure/place. Five box rounds chased the same "Back lands on the bar's
  first pill" symptom through five different Compose mechanisms, each landing on the same
  root re-entry fallback: the frame that removes a pushed frame reclaiming focus before an
  arrival's own request runs; a startup refresh releasing frozen state resetting a
  just-granted arrival a few frames later; a modifier's own presence toggling on the card
  an arrival had just landed on (fixed independently of keep-alive — every card now carries
  its own stable, always-attached `FocusRequester`, kept); a lazy layout deactivating that
  card's own slot out of frame, worked around with `PinnableContainer`; and, after all four,
  the cold-start first title-page return still losing focus, with neither the pin nor a
  generic bar-recovery rule catching it. Withdrawn rather than chasing a sixth cause — the
  benchmark build already returns to Home with 0 `Davey!`/`Choreographer … Skipped` frames
  without it, which was the phase's own real goal. Full account in
  `plans/260929-0215-tv-web-look-chrome-home-departments/phase-04-tv-home-kept-alive-measure-docs.md`.
- **Game night is television only.** The box hosts it, phones join through a browser
  page, and the web player never hosts one: it is not the table in the living room. The
  Games entry sits on the television rail; the `Games` label is English like the rest of
  the app, and everything inside a game is German.

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
| `$XDG_DATA_HOME/mediagram/publish.lock` | Held (`flock`) by whichever process is publishing the index, so two uploads finishing together publish one after the other. |
| `$XDG_DATA_HOME/mediagram/upload-progress-<set>.json` | How far each upload's part in flight has got, one file per set because uploads can run side by side, for `status` to read. Rewritten every 2s, meaningless once stale. |
| `$XDG_DATA_HOME/mediagram/background.log` | Output of the detached uploads `add` starts. |

Both `config.toml` and `data_dir` can be overridden (`--config`,
`MEDIAGRAM_DATA_DIR`, or the config's `data_dir` key).

The player keeps its own, on whatever host it runs on, all overridable by
`MEDIAGRAM_*`:

| Path | Contents |
|---|---|
| `web/.env` | The player's bootstrap configuration. Mode 0600; gitignored. Once Settings has been opened once, `telegram.json` (below) overrides its account fields; `web/.env` is what a first run before that has, and what a deleted `telegram.json` falls back to. |
| `~/.local/share/mediagram-player/telegram.json` | This account's api id/hash, session (`null` when signed out), and chosen channel — written by the Settings page, never by hand. Directory `chmod 0700`, file `chmod 0600`. File beats env, field by field, once it exists. |
| `~/.local/share/mediagram-player/admin-token` | The Settings page's own token, generated on first start unless `MEDIAGRAM_ADMIN_TOKEN` is set. Mode 0600; only its *path* is ever logged. |
| `~/.local/share/mediagram-player/state.db` | Watch state — positions, lists, Kids, editor's choice — and, since the `settings` table (schema v10), the live cache budget a viewer set from Settings. Overrides `MEDIAGRAM_CACHE_MAX` once set. |
| `~/.cache/mediagram-player/` | Chunk cache, bounded by the resolved budget (state DB, then `MEDIAGRAM_CACHE_MAX`). |
| `~/.cache/mediagram-hls/` | Segments of running conversions. Cleared at startup. |
| `~/.cache/mediagram-catalog/` | Decrypted packages, one directory per version, `current` a symlink to the live one. |
| `~/.cache/mediagram-channel-index/` | The env-configured channel's own index history, followed automatically. |
| `~/.cache/mediagram-channel-catalog/<chat id>/` | A channel chosen from Settings' index history, one subdirectory per channel so switching between more than two never compares one's `pushed_at` against an unrelated one's. |

The cache directories hold nothing canonical: delete any of them and the
player rebuilds what it needs on the next start, more slowly. `telegram.json`
and `web/.env` are not like that — losing both means logging in again, because
a session string cannot be recovered from anywhere else. `state.db` is the
only thing this process writes that cannot be refetched at all.

## 10.1. Index schema (library.db, v12)

The canonical index (`library.db`, in `mlib-spec` schema) carries:

- **shows** table: all titles (movies, TV series, tutorials). Gains in v9:
  `collection_id` (TMDB franchises), `collection_name`, `series_type` (tv types:
  Scripted, Miniseries, Documentary, Reality, News, Talk Show, Video; null for
  movies). Gains in v11: `original_language`, TMDB's ISO 639-1 code for the
  title (`ja`, `en`, …) — read back from the same cached details payload as
  every other `shows` column, so backfilling an existing library costs no
  request. Gains in v14: `original_title` (`Point Break` for `Gefährliche
  Brandung`), from the same payload, which search on web and Android matches
  beside the library's own title.
- **credits** table (new in v9): cast, crew, creators. Rows: `(source, kind, id,
  ord, person_id, name, role, dept, profile)` — `ord` is display order within
  a title (cast by billing, then directors, then creators); `dept` is 'cast' or
  'crew'; `role` is character name for cast, job for crew (e.g. 'Director',
  'Creator'); `profile` is the bare TMDB portrait path or null (so a device
  reading a channel snapshot needs no TMDB cache of its own).
- **franchises** table (new in v9): TMDB collections. Rows: `(source, id, name,
  overview)` — `id` is the TMDB collection id, same value as `shows.collection_id`.
  Scraped from `belongs_to_collection` on first metadata run.
- **artwork** table (new in v10): custom poster and backdrop bytes the uploader
  supplies directly. Rows: `(key, mime, bytes)` — `key` is either an existing
  `tmdb-…`/`tmdb-…-bg` poster key, which this table overrides, or `title-{slug}`/
  `title-{slug}-bg` for a title with no provider id (a documentary, or a course
  used as a tutorial's cover), the slug taken from its show or course name
  (`mlib_spec::package::title_art_key`, the same derivation `add-course` uses for
  its default collection id). Written by `mediagram artwork`, and picked up
  automatically from `poster.*`/`backdrop.*` at the root of a folder `add-show`,
  `add-course` or `add-docu` walks. The bytes ride the ordinary index push, so web
  and Android both get them with no extra Telegram round trip. On Android, one
  function resolves it either way: `store::resolve_with`
  (`crates/mediagram-core/src/api/store/resolve.rs`). `store::list_sets`/
  `store::media_set` (`.../store/listing.rs`) call it once per set for the
  poster, backdrop and (for an episode) season poster a listing carries,
  after reading which keys the table actually holds once for the whole pass
  (`crate::artwork::keys`) and remembering each key's answer, so a few
  hundred sets sharing one show's poster cost that key's resolution once, not
  once per set. Title credits, a person page and search-by-name results call
  the same function per portrait, over the one connection and key set that
  call already read, rather than reopening the index per name. A table hit
  materialises into `catalog/artwork/`, written beside its final name and
  renamed into place so a concurrent listing and single-set lookup resolving
  the same key can never leave a reader looking at a half-written file. The
  web player's poster route checks this table the same way, per request; its
  backdrop check additionally counts a table-only backdrop as present, which
  Android's disk-only backdrop check does not (daBOB/mediagram#1).
- **anime_overrides** table (new in v11): a hand-set decision that a title is,
  or is not, anime. Rows: `(source, kind, id, anime, set_at)` — `anime` is `1`
  (force in), `0` (force out) or `NULL` (back to the automatic genre/language
  rule, kept as a row so a merge can carry the clear to the other machine);
  `set_at` is Unix seconds, and a merge takes whichever side's row is newer,
  `anime` included. Kept apart from `shows` because that table's one writer
  replaces a row whole on every `metadata` run. Written by `mediagram edit
  <set-id> --anime yes|no|auto`, keyed to the TMDB title so one override
  covers every episode of a series, including ones uploaded later.
- **categories** table (new in v12): a hand-set label on a course, a
  documentary collection or a standalone documentary. Rows: `(department,
  item_key, category, set_at)` — `department` is `tutorials` (courses and
  their documents) or `documentaries`; `item_key` is
  `mlib_spec::package::title_art_key(show ?? title)`, the same key a unit's
  custom artwork already lives under, so a category survives a re-upload or
  a resume and follows a course uploaded from two folders under one title;
  `category` is the name, or `NULL` for cleared (kept as a row so a merge
  can carry the clear); `set_at` is Unix seconds, and a merge takes
  whichever side's row is newer, `category` included. Written by `mediagram
  edit <set-id> --category "<name>"`/`--clear-category`, or by `add-course`/
  `add-docu --category "<name>"` before the first upload. A name is
  trimmed, collapsed, refused empty or "Other" in any case, and a
  case-insensitive match of a spelling already used by another unit in the
  same department adopts that spelling instead — writer-side only; readers
  compare exactly.

`mediagram_core::shows::is_anime` — a line-for-line port of the web
player's own `isAnime` (`web/src/catalog/anime.ts`), held to the same
fixture cases (`shared_anime_fixtures.rs`) — decides `SetSummary.anime`
(`MediaSet.anime` on Android) from a title's genres, `original_language` and
`anime_overrides`, computed once per listing in `store::listing::enrich`
the same way `genres`/`fsk`/`tagline` are: the index's own facts first, this
device's fetched sidecar filling in what the index does not have. Only the
index's own overrides apply — a device never overrides on its own.

`SetSummary.category` (`MediaSet.category` on Android) is resolved the same
place, from `mediagram_core::catalog_categories::category_of` against
`mlib_spec::category_key::category_key(kind, show, title)` — the same key
the `categories` table above is written under, so a category set at upload
or by `edit` reaches every device without a caption rewrite. `None` for a
film, an episode, an unfiled unit, or an index older than v12; no
device-side fallback, unlike anime — a category is only ever set by hand, so
there is nothing for a sidecar to fetch. The web player reads the table
directly (`web/src/catalog/categories.ts`); the two are held to the same key
fixture (`web/test/fixtures/categories/keys.json`).

Version tracking: `SCHEMA_VERSION=12`, `OLDEST_READABLE_SCHEMA=6` (the floor
a reader checks; every newer schema is read too). Readers tolerant of v11 and earlier (optional
columns/tables); writers from the release introducing this table produce v12.
Both uploaders and Android installs must run that release or later before any
push/export; older Android builds refuse v12 packages.

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
| GET | `/v1/sets/{id}` | none | 200 `{"total","chunks_held","bytes_held"}` (`total` null if unrecorded) / 404 |

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
