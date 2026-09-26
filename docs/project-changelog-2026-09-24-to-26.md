# Project changelog — 2026-09-24 to 2026-09-26 (archive)

Unreleased versions built and merged into `main` on 2026-09-26, moved out of
`project-changelog.md` to keep it within the documentation budget. Newer work
is in that file.

## Unreleased — 0.61.0

Merged into `main` on 2026-09-26: `feat/settings-menu`.

A Settings surface on both the web player and Android: read-only Telegram
connection info, switching library without a restart, editing the api
id/hash, signing in or out, a live cache budget, and an active-sessions list
with remote revoke. Android's Settings screen and the Rust core account/session
exports landed earlier; this entry covers the web side and the
sessions feature that spans all three.

**Added**

- `#/settings` on the web player: locked behind this household's own network
  plus an admin token (`~/.local/share/mediagram-player/admin-token`, minted
  on first start unless `MEDIAGRAM_ADMIN_TOKEN` is set). Telegram section
  (account, library, datacenter, session), Change library, Application
  id/hash, Sign in/out, a Cache section with a live size field, and an Active
  sessions list.
- The account (api id/hash, session, chosen channel) now lives in
  `~/.local/share/mediagram-player/telegram.json` once Settings has been
  opened once; `web/.env` stays the bootstrap. The cache budget lives in the
  state database's new `settings` table (schema v10) and overrides
  `MEDIAGRAM_CACHE_MAX`.
- The web player's Telegram client is swappable at runtime (`TelegramConnection`):
  sign in, sign out and an api id/hash change all restart it without a
  process restart, never running two clients on one auth key. A channel
  switch reuses the same client with a different channel — no restart at all.
- Active sessions: `crates/mediagram-core::api::sessions` and
  `web/src/settings/sessions.ts` list and revoke this app's Telegram logins
  (`account.getAuthorizations`/`resetAuthorization`), scoped to this app's
  `api_id` plus the current row. Both ports read the same shared fixture
  (`web/test/fixtures/authorizations/cases.json`), and Android's Settings
  screen gained an Active sessions section over the same core calls.

**Deliberate surface difference**

- The web Settings page sits behind the admin-token gate described above;
  Android's does not, because the phone is the account holder's own device
  and nothing else can reach it.

## Unreleased — 0.60.0

Merged into `main` on 2026-09-26: `feat/system-page-stats` — four new groups
on the web player's System page (`GET /api/status`).

**Added**

- **Telegram link.** Per-DC request counts, bytes, latency percentiles over
  the last 256 requests, flood waits and main-connection reconnects, from a
  `TelegramClient` subclass measuring around `invoke` — the one seam every
  download passes through.
- **Watching now.** Each open player POSTs its own reading (mode, codecs,
  bitrate, buffer health, dropped frames) every 5s to the new
  `POST /api/status/playback`; the server keeps the most recent one per
  viewer for 15s and drops the viewer id before it reaches the snapshot. The
  one group the server cannot measure itself, since the System page and the
  player it describes are usually different devices.
- **Conversion progress.** Speed, fps and output position from ffmpeg's own
  `-progress pipe:1`, CPU time from `/proc`, segment counts, and how many
  re-encodes/copies/HEVC copies have run since the process started.
  Conversions now need ffmpeg 4.4 or newer.
- **Host.** Resident and heap memory, event-loop lag over the last complete
  10s window, free disk under the cache and transcode directories, and the
  Bun version.

`memoryBytes` at the top level of the snapshot moves to `host.rssBytes`, a
breaking JSON change (this project is pre-release; welcomed rather than
avoided). None of the above touches the hot byte path: a download counts a
request and writes a ring-buffer slot, once per `upload.GetFile`, and
nothing else runs per chunk.

**Owed to Android:** per-DC link stats and host memory/free-disk, in a
separate Android/core plan — not built here. See `docs/web-player.md`
"Differences from Android" for the full parity record.

## Unreleased — 0.59.0

Merged into `main` on 2026-09-26: `fix/sync-watched-removal` and
`perf/web-streaming-and-startup`.

**Changed**

- The web player starts and streams faster. A cold read is served as soon as
  it arrives from Telegram and cached behind it; eviction runs one scan at a
  time; cached chunks are checked with `stat` instead of being read; Telegram
  media lookups are cached briefly and shared between concurrent requests.
  Static files and `/api/sets` are compressed (gzip/brotli) and carry an
  ETag, so an unchanged catalog answers `304`. Startup runs its independent
  requests together, links stylesheets straight from `index.html` instead of
  a `style.css` `@import` chain, and loads the player modules in the
  background rather than before the first shelf.

**Fixed**

- Un-marking a title as watched now survives a sync round, on new builds,
  the moment they merge with each other — an un-mark heard only by devices
  that already run this fix. The removal travels as its own row, on its own
  key (`unwatched`, not a `removed` flag on the existing `watched` row): a
  build that predates this one does not recognise that key and drops it, so
  it goes on reporting its unchanged, always-older live mark rather than
  turning the removal into a live mark of its own at the same moment — which
  is what a `removed` flag on the same row would have let it do, and what
  would have handed a same-time tie to device id instead of to what actually
  happened. A device on an old build still shows the title as watched until
  it updates; once it does, the next merge resolves correctly on its own,
  with no extra un-mark needed. A later re-mark always wins over an older
  removal, even one with a future timestamp from a device whose clock runs
  ahead — and the reverse holds too, an un-mark always wins over an older
  mark. Taking a mark back never touches a position, on this device or any
  other — matching what marking one has always done; a removal instead
  carries the finish it took the mark from, so a position from before that
  finish stays suppressed while a genuine rewatch made since survives.
  Ported identically to the web
  (`web/src/state/*`) and the Rust core Android uses
  (`crates/mediagram-core/src/state/*`, `state.db` schema v9 on the web, v6 in the core), held
  together by shared fixtures covering both merge orders and mixed
  old/new documents. Android's `WatchStateRepository` already accepted
  `finished = false`; no screen calls it yet, so nothing changes there for
  now.
- A local write on the web now reaches another device within a few seconds,
  not up to five minutes, for the writes worth telling another device about
  soon: watched, the watchlist, Kids, a profile change, forgetting a
  position outright, and the final progress save on leaving a title or
  pausing — which now marks itself `?final=1` rather than being inferred
  from the HTTP method, since an older browser that refuses `sendBeacon` a
  JSON body falls back to the same PUT the ten-second autosave tick already
  uses, and the method alone could not tell the two apart. That tick does
  not trigger a round — one every ten seconds would repeat the flood limit
  this project has already hit once — and neither does a preference, which
  is per-device and never synced. A debounced sync round runs a few seconds
  after the last such write settles, alongside the
  existing start, timer, push and shutdown rounds — matching Android's own
  `WatchSync.soon()` after leaving a title.

## Unreleased — 0.58.0

Merged into `main` on 2026-09-26: `feat/android-lan-cache` (it includes
`feat/android-cache-volume`, `feat/android-chunk-reads` and
`feat/mediagram-cache-server`), `feat/android-featured-and-paging`, and
`feat/android-tv-ui`.

**Added**

- An Android TV interface (`ui-tv`), driven by the remote:
  - browse shows, courses and lists; open titles and walk back;
  - watch with the remote, with up next and autoplay;
  - subtitles, playback settings, marks and playback stats;
  - search and genre pages;
  - offline marks, "mark finished" and profile removal.

  Phone and TV share the catalog logic in `feature:catalog`, including the
  tab labels and the title facts.
- DTS and TrueHD play with sound on Android devices that have no decoder for
  them. A Google TV box had played 269 DTS films silently. The app now carries
  Media3's FFmpeg audio decoder, built from FFmpeg 6.0.1 (LGPL 2.1+) with only
  the `dca`, `truehd` and `mlp` decoders, by `scripts/build-android-ffmpeg.sh`.
  It is used only where the device's own decoders and passthrough decline a
  track. Phone and television share it.
- A home cache server and a cache that can live on another volume, for
  Android. `mediagram_cache` is a new workspace binary: a LAN chunk store
  with no Telegram session, found over mDNS. Chunk writes are signed with a
  pairing token that never crosses the wire. On unmetered Wi-Fi the phone
  asks it for each 1 MiB chunk before Telegram, and shares what it fetched,
  so a title one device has played is not downloaded again by the next.
  Settings pairs the device and shows the server. System counts the chunks
  the server served. Settings can also move the cache to an SD or USB
  volume ("Where"). The size list grows to that volume's capacity, and a
  cache that fails mid-play (full disk, pulled card) now falls through to
  the network instead of stopping playback. Reads are now whole aligned
  1 MiB chunks. The phone asks for two new permissions:
  `ACCESS_LOCAL_NETWORK` (runtime, API 37+) and
  `CHANGE_WIFI_MULTICAST_STATE`. Plan:
  `plans/260925-2046-external-cache-volume-and-lan-chunk-server/`.
- The Featured reel and a paged Movies shelf on the phone, as on the web
  (merged from `feat/android-featured-and-paging`). Featured sits above the
  Movies shelf beside List · Grid and opens a dark, full-screen run of up to
  twelve unwatched films with posters. The Movies shelf shows 48 films a page
  with the web's page links. Picks and page links are ports of
  `featured-picks.js` and `pager.js` with the web's test cases.
- The phone's shelves keep their place under a title: coming back from a film,
  a show or the player finds the same tab, page and scroll position, as the
  web's back button does.

**Changed**

- One port of the web's `pickFeatured` on Android (`FeaturedPicks.kt`), shared by
  the phone's Featured reel and the magazine cover. The two branches had
  each ported it.
- The System page's Cache block keeps its **Where** (cache volume) and
  **Source** (LAN server) rows after `SystemRows` moved to `feature:system`.

**Fixed**

- `CoreLoadsTest` (an on-device test) passes the `deviceName` the core's
  constructor has required for a while; it no longer compiled.
