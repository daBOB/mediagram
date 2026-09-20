# Android: a system menu, the metadata already on the device, and playback stats — design

The Android app can play a film and say where it is in it. It cannot say
anything else. There is no settings surface, no way to see what the app is
doing, no artwork, and no synopsis — while the database it downloads on every
refresh already contains the synopses, and the code that would display the
artwork is written, wired, and waiting for files nobody puts there.

This closes those three gaps together, because they share one thing the app
does not have: somewhere to put a control.

## 1. What is actually missing

Three of these four are not new capability. They are capability already built
and not reachable.

| | State before this work |
|---|---|
| Synopses, taglines, genres, ratings | **On the device.** The uploader writes them to a `shows` table (`crates/mediagram/src/index/shows.rs:93`); `refresh_library` downloads that database; `mediagram-core` has no query for it and `SetSummary` omits the columns. The web player reads them (`web/src/shows.ts:41`). |
| Poster art | **Plumbed end to end, and dead.** `SetSummary.poster_key` → `CoreClient.posterPath()` → `MediaSet.posterPath` → `AsyncImage`, with an initials fallback. Nothing writes `<catalog>/current/posters/*.jpg`, so `poster_path()` always answers `None`. |
| System information | **Does not exist.** The web's is a local-only `/api/status` the phone can never reach; Android's facts have to come from Android. |
| Playback stats | **Does not exist**, but every number is one property access away on the `Player` the UI already holds. |

### The poster decision this reopens

Phase 8 of the android-foundation plan chose the pinned channel index over the
published package, and recorded the cost:

> The cost is posters: the pinned `library.db` carries none, so cards show
> their titles without art. Accepted deliberately. Posters can come back later
> through the package mechanism, which stays in the code.

This is that later. It does **not** come back through the package: the package
path costs a URL and a 44-character key typed by hand, which is what phase 8
removed and what this work has no reason to reinstate. The device fetches its
own art from TMDB instead, using the provider id the catalog already carries.

`refresh_catalog` and `PackageSettings` stay exactly where they are —
built, replay-defended, unused. A library published as a package still carries
its posters, and nothing here changes that.

## 2. The decision that outlives the features

Poster fetching could live in Kotlin or in Rust. It goes in Rust, in a crate
both the uploader and the core depend on.

> **Whoever owns a directory writes to it.** `<data_dir>/catalog/current/` is
> staged and atomically swapped by `mediagram-core` during a refresh. Kotlin
> writing JPEGs into it would be writing into a directory that can be replaced
> underneath it mid-write.

That is the deciding argument, and it is a race rather than a preference. The
supporting one: `crates/mediagram/src/metadata/tmdb_client.rs` already
classifies a v3 key against a v4 JWT and sends each the one way TMDB accepts,
honours `Retry-After` on a 429, and caches payloads to disk. A Kotlin
re-implementation would be a second set of those rules, free to disagree with
the first.

So the TMDB client moves out of the uploader into `crates/mediagram-tmdb`, and
`mediagram` and `mediagram-core` both depend on it. Moving it is preferable to
copying it for the reason the project's own rules give: two copies of a rule
are two answers to one question.

## 3. Chrome

The app has no `NavHost`, no app bar, and no back stack — `MobileApp.kt` is a
recursive `when` over two `StateFlow`s, and `WithStartOver`'s bottom row is the
only persistent affordance in it.

Catalog and collection gain a `Scaffold` with a `TopAppBar`. The player does
not: it is fullscreen, and a bar over a film is the opposite of what the last
piece of work established. The overflow menu holds:

```
⋮ ─ System
  ─ Fetch posters…
  ─ TMDB key…
  ─ Start over
```

`StartOverAction` moves into it unchanged, wording and confirmation dialog
intact, and `WithStartOver` retires — its one job was hosting that button.

**Navigation stays a `when` tree.** This adds two flat destinations to three.
Navigation3 is a declared dependency and still unused; adopting it for two
leaves that never branch would be importing a back stack to hold a list. Each
new destination takes its own `onBack`, the shape `PlayerScreen(setId, onBack)`
already uses. When a destination needs to open another destination, that is
the moment to reach for Navigation3, and this design does not create one.

## 4. The title detail screen

The synopsis has nowhere to go: a card plays on tap and a film has no
collection screen. So a card opens detail, and detail plays.

```
┌──────────────────────────────┐
│ ←  Blade: Trinity            │
├──────────────────────────────┤
│  ┌────────┐                  │
│  │        │  2004 · 1h 53m   │
│  │ poster │  Action, Horror  │
│  │        │  ★ 5.9           │
│  └────────┘                  │
│                              │
│  "The final hunt begins."    │
│                              │
│  Dracula is awakened in the  │
│  Middle East…                │
│                              │
│  1080p · MKV · HEVC · EAC3   │
│  14.2 GB · 2 parts · 9.4Mbps │
│                              │
│  [ ▶ Play ]                  │
└──────────────────────────────┘
```

The tagline is quoted and the overview is not, because one is a line of
marketing and the other is a paragraph of description, and a viewer who cannot
tell them apart has been given a wall of text.

The technical line mirrors the web's `technicalLine` — the same fields in the
same order, so a viewer reading both surfaces reads one format. Note this
requires `SetSummary` to carry `container`, `vcodec`, `acodec`, `quality` and
`hdr`, which it currently omits; they are in the `sets` table already.

A collection's own screen gains the same header block above its tree, for the
show or course rather than an episode.

## 5. System information

Four blocks, in the page's own type rather than a monospace grid — the web's
`status-view.js` made that choice deliberately and the reason carries over: a
catalogue is a printed thing, and a telemetry table dropped into it reads as
somebody else's tool bolted on.

| Block | Rows | Source |
|---|---|---|
| Catalogue | Source, Holds, Schema | new core call over the installed catalog |
| Cache | Held of budget, Reads, Evicted | media3 `SimpleCache` + a `CacheDataSource.EventListener` |
| Upstream | Since starting, Failed reads | counters on `MlibDataSource` |
| This app | Version, Telegram, Uptime | `BuildConfig`, core, process start |

A row whose value is unknown is omitted rather than shown blank, matching
`renderStatus`.

**There is no Conversion block.** The web has one because it transcodes;
Android decodes natively and never will. See §9.

**Cache hits and misses need a listener nothing currently attaches.**
`PlayerFactory.cacheDataSourceFactory` builds a `CacheDataSource.Factory`
without an `EventListener`, so `onCachedBytesRead` and `onCacheIgnored` go
nowhere. Attaching one is the whole of it, and the same counters feed §7.

## 6. The TMDB key, and fetching posters

A new `TmdbSettings` follows the pattern the other three stores set exactly:
its own `EncryptedSharedPreferences` file, its own key constants in a
`private companion object`, an in-memory double for tests, opened `by lazy` so
a keystore failure cannot crash DI. It is cleared by start-over, and
`StartOverAction`'s wording gains it — that dialog names what it destroys, and
a credential it silently dropped would make it a lie.

The key is stored by Kotlin and used by Rust. It is never logged, and it never
appears in a `CoreError`: the core's existing rule is that an error names what
failed, never the secret that failed to work.

Fetching is an explicit action, as the uploader's own `mediagram posters`
command is. For each set with a `poster_key`, the core resolves the TMDB
payload for that id, reads `poster_path`, downloads `w342`, and writes
`<current>/posters/<key>.jpg`. Existing files are left alone, so a second run
fetches only what the first could not.

The action reports what happened — fetched, already held, skipped for want of
a provider id, failed — because "done" over a library of 540 sets tells a
viewer nothing about the eight that did not work.

**Two failure modes get named rather than swallowed.** A key TMDB rejects is
reported as a key problem, not a network one, or a viewer retries forever
against a wrong key. And a refresh landing mid-fetch swaps `current` beneath
the run; the fetch resolves its directory once at the start and writes only
there, so the worst case is art written to a catalog that has just been
replaced — wasted work, not a corrupted install.

## 7. Playback stats

An `ⓘ` glyph joins the transport bar, toggling an overlay. It appears and
fades with the bar, so it never covers the picture unasked, and it is a text
glyph because this module has no Material icons dependency and the bar's other
four controls are glyphs already.

```
┌──────────────────────────────┐
│ video   1920×800 HEVC 9.4Mbps│
│ audio   EAC3 5.1 German      │
│ buffer  1:23 ahead · 47 MB   │
│ cache   81% from disk        │
│ reads   34 fetches · 34 MB   │
│ dropped 0 frames             │
└──────────────────────────────┘
```

Everything here is read live from the `Player` the UI already holds —
`videoFormat`, `audioFormat`, `getTotalBufferedDuration()`,
`getVideoPlaybackQuality()` — plus the §5 cache counters. Nothing is threaded
through `PlayerUiState`, which the last piece of work established carries no
position for exactly this reason: a second copy of a number the player owns
can only be the same number later, or a different one wrongly.

**This is deliberately better than the web's, not merely equal.** The web
builds its technical line from recorded catalog values because a browser will
not say what it is decoding. ExoPlayer will. So Android reports the format
actually being decoded, which is the answer to the question someone opening a
stats overlay is really asking — and the two surfaces can legitimately
disagree, when the catalog's recorded value is wrong.

A zero is not shown where zero means "nothing to report": dropped frames
follow `preloadReadout`'s rule and stay hidden at zero.

## 8. Testing

| What | Where | How |
|---|---|---|
| TMDB key classification, retry, poster resolution | `crates/mediagram-tmdb` | the uploader's existing tests, moved with the code |
| The `shows` query and its DTO | `mediagram-core` | a fixture database, as `api_surface.rs` does |
| Poster fetch: idempotence, missing id, rejected key | `mediagram-core` | a stub HTTP layer; no live TMDB in CI |
| `TmdbSettings` read/write/clear | `:core:data` | Robolectric, as the other stores are |
| Cache counters | `:core:playback` | Robolectric against a real `SimpleCache` |
| Formatting decisions — stat rows, omitted zeroes | `:ui-mobile` | pure functions, as `clockTime` and `controlsShouldFade` are |
| Screens | a real phone | this module still has no Compose test rule |

The pure-function discipline the transport controls established holds: a
decision that can be stated as a function is stated as one and proved on the
JVM, and the composable is left with nothing to decide.

## 9. Deliberate differences from the web player

Recorded here because the Surface Parity rule requires a meant difference to
be written where someone will find it.

**No Conversion block.** The web transcodes and reports on it. Android decodes
natively and never transcodes; there is no encoder, no session, no queue. The
block has no Android counterpart, rather than an empty one.

**No local-only gate on the system screen.** The web hides `/api/status`
behind a local-address check and answers a non-local caller with a bare 404, so
a scanner learns nothing. That defends a server reachable over a network.
Android's system screen reads the device's own state and is reachable only by
whoever is holding the phone; a gate would defend nothing.

**Stats report the decoder, not the catalog.** §7. The web cannot do this.

**Posters are fetched by the device, not shipped with the catalog.** A
published package still carries art for the surfaces that read one. Android
reads the pinned channel index, which carries none, and fetches its own.

## 10. Deliberately out of scope

**Watch state** — resume, watchlist, kids, collections. Still unowned on
Android, still needs a decision about where that state lives given the app has
no server. Its own design.

**Next-episode autoplay.** Needs watch state.

**The television surface.** Blocked for want of a television. Everything here
is phone-shaped; the system screen and the detail screen will both need a
10-foot treatment when that unblocks.

**Re-fetching synopsis text from TMDB.** The uploader decided this text when it
added each title. A device that re-fetched it would be a second writer for one
field, and the two would disagree.

## 11. Unresolved questions

- Which branch this lands on. `android-foundation` is merged into `main`; this
  work should start from `main`, and the `mediagram-android` worktree is still
  checked out on the merged branch.
- Whether the detail screen's technical line should also appear on catalog
  cards. The web's own spec asked for it there and shipped it only in the
  player HUD — the two disagree, and nobody has said which is right.
- `versionCode` is still `1` while `versionName` is now `0.2.0`. Out of scope
  here, but it will matter the first time an APK is handed to someone.
