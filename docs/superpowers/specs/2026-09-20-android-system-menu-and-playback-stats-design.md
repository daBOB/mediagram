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
  ─ Refresh library
  ─ Fetch details and artwork
  ─ TMDB key…
  ─ Start over
```

The fetch item carries no ellipsis. One was drawn here and shipped, and it was
a promise of a dialog that never came — that tap starts minutes of HTTP there
and then. `AppChrome.kt` says so where the item is built.

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
| Cache | Held of budget, Reads | media3 `SimpleCache` + a `CacheDataSource.EventListener` |
| Upstream | Since starting, Failed reads | counters on `MlibDataSource` |
| This app | Version, Telegram, Uptime | `BuildConfig`, core, process start |

A row whose value is unknown is omitted rather than shown blank, matching
`renderStatus`.

**There is no Conversion block.** The web has one because it transcodes;
Android decodes natively and never will. See §9.

**There is no Evicted row**, which this table asked for in its first
drawing. media3 will not say how much it evicted. See §9.

**There are no cache hit and miss counts, and a listener does not supply
them.** `PlayerFactory.cacheDataSourceFactory` does attach a
`CacheDataSource.EventListener`, but the interface answers in bytes:
`onCachedBytesRead` and `onCacheIgnored` are the whole of it, and neither
counts a read as a hit or a miss. So the Cache block reports the share of
bytes served from disk and the round trips behind the rest, which is what
the app can actually know. The same counters feed §7. See §9.

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
`<data_dir>/catalog/artwork/<key>.jpg`. Existing files are left alone, so a
second run fetches only what the first could not.

**Not `<current>/posters/`, which this section first said.** That path has to
satisfy two things at once, and naming only the first is how the second was
missed.

*Out of the version directory, so a refresh cannot delete it.* `current`
points at a version directory, and a version directory is storage with a
timer on it: `install_staged` removes one wholesale before renaming a fresh
download into place, `remove_other_versions` clears every version but the one
just published, and a refresh runs on every catalog load. Artwork written
inside one was deleted before it was ever shown — counted on a device at 0,
then 236, then 0 again across a restart. A poster is a fact about a title,
not about a snapshot of the index.

*Inside `catalog/`, so forgetting the library forgets its artwork too.*
Start-over deletes `catalog/` whole. Artwork held outside it would survive a
sign-out and leave the next account to set the device up looking at cached
provider payloads naming the previous one's titles — and it would grow
without bound, since nothing else ever removes it. `artwork/` is a sibling of
the version directories, which neither cleanup pass touches:
`remove_other_versions` removes only entries named `v-…` or `incoming`. The
TMDB response cache sits inside it for both reasons.

Both halves are pinned by tests rather than only written down here: one
plants a poster and drives `install_staged` over it — the real rename and
the real sweep, not a stand-in that deletes the old version directory by
hand, which would leave `catalog/incoming/` passing as a home for artwork —
and one states the containment that makes a sign-out sufficient.

`poster_path` reads the version's own `posters/` first and the artwork
directory second, so a published package keeps its publisher's chosen art for
the keys it covers — a fetch only ever ran for a title the package had
nothing for.

The action reports what happened — fetched, already held, skipped for want of
a provider id, failed — because "done" over a library of 540 sets tells a
viewer nothing about the eight that did not work.

**Two failure modes get named rather than swallowed.** A key TMDB rejects is
reported as a key problem, not a network one, or a viewer retries forever
against a wrong key — and that check is asked of TMDB itself rather than
through the response cache, which holds no credential and would otherwise
vouch for a rotated key out of a file the previous one paid for. And a
refresh landing mid-fetch swaps `current` beneath the run; the fetch resolves
that directory once at the start and reads `library.db` only from there,
while the art it writes goes beside the version directories rather than into
one, so a refresh mid-fetch costs nothing at all.

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

**The buffer row carries no byte figure, against §7's own drawing.** §7 draws
`buffer  1:23 ahead · 47 MB`, and the bytes had no honest source. ExoPlayer
reports a buffered *duration* and no count of the bytes behind it; the only
byte total media3 will answer for is `Cache.getCacheSpace()`, which is the
whole `SimpleCache` across every title ever played, capped by the 2 GiB
evictor — so on a well-used device the row would pin at `2.0 GB` and stop
moving, and it is in any case the figure §5's Cache block already prints as
`Held`. A per-title `getCachedBytes` would be a smaller version of the same
category error: a cache fact under a label that says buffer. The row is
`buffer  1:23 ahead` and nothing else. The overlay's byte volumes are the
`reads` and `cache` rows, where they are about reads and the cache.

**Posters are fetched by the device, not shipped with the catalog.** A
published package still carries art for the surfaces that read one. Android
reads the pinned channel index, which carries none, and fetches its own.

**No Evicted row in the Cache block.** §5. The web player owns its cache
outright: `web/src/cache/store.ts` performs the eviction itself and counts
it, and the status panel reports that count. Android's cache is media3's
`SimpleCache` behind a `LeastRecentlyUsedCacheEvictor`, and media3 surfaces
no eviction total anywhere the app can read one. The listener the app does
attach, `CacheDataSource.EventListener`, has exactly two callbacks —
`onCachedBytesRead` and `onCacheIgnored` — and neither is about eviction.
What media3 does offer is `Cache.Listener.onSpanRemoved`, which fires per
cache span rather than per title, sees only the spans removed while this
process happened to be listening, and would reset on every launch while the
cache on disk does not. A number with those three properties under a label
reading "Evicted" is worse than no row.

**Resolved: the phone asks in the library's own language, not the device's.**
This was recorded here as an open question — the device asked for `en-US`, a
constant, while the uploader asked TMDB in its configured `tmdb_language`, so
a library curated in German got the English poster and, where it had no
description at all, would have got an English one. It was the one entry in
this section that turned out to be a defect rather than a difference, and it
has since been ruled on.

The answer comes from the index rather than from a setting beside the TMDB
key. Every row the uploader writes carries the language it was described in,
and `language_of` (`crates/mediagram-core/src/api/fetch.rs`) takes the most
common one across the library and asks in that. A library is described in one
language by the machine that published it; asking a viewer to name it again
would be asking them to repeat something the library already says, and to
keep the two in step by hand.

The device locale, `Locale.getDefault().toLanguageTag()`, is passed down as a
*fallback* only, for the library that says nothing — one written before the
column existed, or one nobody ran `mediagram metadata` over. It is read once
in `FetchViewModel`, because a rotation during a run of minutes must not
change what that run asked for. Confirmed on the device: a phone set to
`en-GB` fetched German descriptions for a `de-DE` library.

### Kept on purpose after the parity plan

The web-parity plan (`plans/260924-0139-android-web-parity/`) closed the gaps
it could; these it left open deliberately.

**No scrub thumbnails.** The web draws them from sprites its server cuts with
ffmpeg. The phone has no server and no ffmpeg; the scrubber shows the time.

**No adaptive bitrate, and no "needs converting" badge.** The web transcodes
what a browser cannot play; the phone decodes the original directly, so there
is one rendition and nothing that could need converting.

**No keyboard-only controls.** A-B loop, frame step, `0`–`9` jumps, `[`/`]`,
`m` and `c` are keys on the web; the phone has no keyboard to press them with.

**No remembered volume.** The phone's hardware buttons own volume.

**Switching audio restarts nothing it does not have to**, where the web
reloads the stream; ExoPlayer switches track in place.

**Genres come from the device's own fetch.** The channel index carries none,
so the phone's genre pages are filled from the TMDB details it fetches itself,
like its posters.

**Preload is bounded.** Two episodes ahead, on an unmetered network only, and
never past 75% of the cache budget — a phone's data plan and storage are the
viewer's, where the web player's host is not.

**Notes are a sheet over the picture on a phone held sideways.** Everywhere
else they sit beside or below it, the web's rule; a window that short has no
room beside the picture for anything worth reading.

**Subtitles come from the index only**, as on the web: tracks embedded in the
file stay off, since the index's are the ones a viewer chose to upload.

**Films and Series open as posters.** The web opens on its list; a thumb finds
a poster faster than a line of text. Courses are a list on both.

**Retry, double-tap seek and a picture-in-picture button are the phone's own**
— touch equivalents of the web's seek-to-retry, arrow keys and `p` key, not
features the web owes back.

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
