# Project changelog

Dated entries summarizing what shipped, grouped by day. Commit hashes refer
to `main`. Full phase-by-phase detail lives in
`plans/260914-1954-telegram-linux-uploader-mlib-spec-v2/plan.md`'s
"Implementation log" sections.

## 0.77.1 — Android: the core knows which titles are anime, not yet reachable from the UI

**Added**

- `mediagram_core::shows::is_anime`, a line-for-line port of the web
  player's own rule, held to the same fixture cases the web's own tests run
  (`shared_anime_fixtures.rs`). `SetSummary.anime` carries the verdict across
  the UniFFI boundary — computed in `store::editorial::enrich` from the
  index's genres and `original_language`, this device's own fetched sidecar
  as a fallback, and the index's `anime_overrides` — and lands on
  `MediaSet.anime`. An index older than schema v11 lists everything
  `anime = false`, same as it always has. Android-only: the web player
  already shipped this in 0.77.0.

## 0.77.0 — an Anime department for Japanese animation

**Added**

- The web player: Japanese animation (TMDB genre "Animation" plus original
  language `ja`, or a `mediagram edit --anime` override) leaves Movies and
  Series for its own Anime department — a hero, Continue watching, every
  anime series and every anime film, the same shape Documentaries already
  has. The nav tab hides at zero (no upload command points at an empty one);
  `#/anime` itself still renders the empty state. Genre pages, search, person
  pages, franchises, Similar, Continue and Next up all still find an anime
  title where it actually is; the home page's editorial picks stay
  Movies-only, the same rule Documentaries already followed. An index older
  than schema v11 shows no anime at all — Movies and Series read exactly as
  they did before this release.

## 0.76.0 — titles carry their original language, and anime can be set by hand

**Added**

- Schema v11: `shows.original_language`, TMDB's code for a title (`ja`, `en`,
  …), backfilled by `mediagram metadata` from the cached TMDB payload — no key,
  no network needed for a library `add` already resolved. A new
  `anime_overrides` table holds a hand-set decision that a title is, or is
  not, anime, kept apart from `shows` because that table's writer replaces a
  row whole on every `metadata` run. `mediagram edit <set-id> --anime
  yes|no|auto` sets or clears an override, keyed to the TMDB title so it
  covers every episode of a series, including ones uploaded later;
  index-only, `--dry-run` supported, refuses a set with no TMDB id or a
  course. `pull-index`/`push-index` merge both: `original_language` rides the
  existing NULL-fill, and overrides take whichever machine's `set_at` is
  newer. Readers on v10 or older simply do not see the column or table yet;
  no reader behaviour changes in this release.

## 0.75.5 — the television's episode rows play too

**Fixed**

- On the television, an episode or lesson row plays, on a show, a season and
  a course alike, as it does on the phone since 0.75.3 and on the web
  (`lessonRow`); it used to open a title page first. Back from the player
  lands on the row that was played. The television's own Continue rows still
  open a title page — `TvHome` documents that difference, and its Series and
  Tutorials pages keep to it.

## 0.75.4 — the app opens in the theme you chose

**Fixed**

- The window Android draws before the app's first frame follows the theme
  chosen in Settings › Appearance, not the device's own mode. 0.75.3 made it
  follow the device, so Dark chosen on a light-mode device opened on a light
  flash; the choice is now handed to the system as the app's own night mode
  (`UiModeManager.setApplicationNightMode`, API 31+), at startup and on every
  change. Checked on the tablet (light system, Dark chosen): the cold start
  opens dark. Below API 31 the window still follows the device.

## 0.75.3 — the phone after review, ported onto the redesign

The phone review's fixes from 2026-09-26, never committed until now, carried
onto the pages the home and department redesign rebuilt since. Fixes the
redesign had already made (Collections scrolling as one list, the department
hero's height, the Similar tab's empty sentence, no department highlighted for
Continue or My List) were left as they are.

**Fixed**

- The app opens in the right colours. The cold-start window is light on a
  light system and dark on a dark one (`window_ground`, per night mode), and
  always dark on the television, which is dark whatever the system says. The
  status and navigation bar icons follow the theme actually chosen in
  Settings › Appearance, live, not a guess made before Compose started.
- An actor's portrait no longer falls back to initials when its card scrolls
  away and back: a found portrait is remembered for the session.
- Every episode and lesson row plays, on a series page, a course page and a
  season page alike, as the web's `lessonRow` does; none opens a title page
  first.
- A Continue card on the Series or Tutorials page plays, like every other
  resume card; it used to open the title page.
- Popular and New rows appear on Series only, never on Tutorials, however
  many courses there are: the web's own `series && shows.length > ROW` gate,
  now in the shared model, so the television follows it too.
- A title opened from another title's Similar row opens on Overview, and
  going back finds the first one still on the tab and scroll it was left on.
  Each screen keeps its own saved state, dropped once it is popped.
- A series page opens on the season the resume point is in even when watch
  state arrives after the first frame, which it usually does.
- Film and documentary rows on the department pages mark what has been
  watched; they drew every card as unwatched.
- A title page's tagline is drawn over its art in the web's `--on-image`
  with a text shadow, readable in Light, and only on a wide window, as the web
  hides `.spread-quote` below 900px. The art is capped at 40% of the screen's
  height, so a landscape phone keeps the title and tabs above the fold.

**Tests**

- Accent colours are checked at 4.5:1 against the containers they sit on
  (`Sunk`, `LightSunk`, `LightPage`), not only the page.

## 0.75.2 — the cache server's set status, in files of its own

**Internal**

- `GET /v1/sets/{id}` and the store query behind it move into
  `http/set_status.rs` and `store/set_status.rs`. The 0.73.0 change that
  added them left `http.rs` at 224 lines and `store.rs` at 223, over the
  200-line limit `code_standards` holds every source file to, which failed
  the pre-push check. Nothing the route answers changed.

## 0.75.1 — `PreloadService` teardown, made robust

**Fixed**

- `PreloadService.onDestroy` now mirrors `onTimeout`'s own
  `pauseForTimeLimit()` call whenever the service is torn down while a
  preload is still genuinely outstanding (`FilmPreloading.hasWork` true at
  that moment) — previously only `onTimeout` (Android's own dataSync
  ceiling firing) did this; any *other* reason the service stops mid-work
  left the affected film reverting to a bare "Preload · N% held" pill
  instead of the already-designed "Paused — background limit" + Resume
  treatment. Guarded on `hasWork` itself so the service's own ordinary
  self-stop (once the queue empties) stays a no-op, not a blind call.

**Investigated, not a bug**

- A tablet device-verification pass had flagged `PreloadService` being
  torn down roughly 55–75s after every start, logged by
  `ActivityManager` as "Stop FGS timeout" preceded by a MIUI-only
  "does not have any types" warning — read at the time as a possible
  platform/targetSdk issue. Root-caused this pass: `dumpsys activity
  services` shows the service's own foreground type correctly held as
  `dataSync` (`types=0x00000001`) for its entire life; AOSP's
  `ActiveServices.maybeStopFgsTimeoutLocked` (the function behind that log
  line) is called from the *ordinary* `stopService`/`stopForeground`
  paths, not from the abuse-prevention timer itself (`onFgsTimeout`) — it
  is bookkeeping cleanup for a stop that already happened, not evidence
  one was forced. A clean, isolated repro on the same tablet confirmed the
  timing lines up exactly with the film's own completion (`FilmPreload:
  ... held` logged 119ms before "Stop FGS timeout"), i.e. this service's
  own `hasWork.filter { !it }.collect { stopSelf() }` stopping itself
  normally once the queue emptied. The MIUI "no types" warning does not
  reflect the real, correctly-typed service record either. No manifest or
  targetSdk change made. `PreloadService.onDestroy`'s new guard above
  stands regardless, as defense against whatever *does* someday stop this
  service outside the two paths it already expects.

## 0.75.0 — Android: the preload queue, made visible

**Added**

- `FilmPreloader` exposes its own queue as one ordered list — the running
  film first (its live held bytes and pause reason, if paused), then every
  waiting film in FIFO order — without touching any of the engine's own
  rules. A queued film's own label on the film page now names what it is
  actually waiting on instead of the bare "Queued": "Queued · after Der
  Pate, 36%" when nothing but the running film precedes it, "Queued · 2
  ahead" otherwise. Phone and TV alike; a tap still cancels.
- A Preloads page (phone/tablet and TV), reachable from the overflow menu
  (phone) or the TV menu once anything is running or queued — "Preloads ·
  n", present only then. Three sections, empty ones hidden: Preloading
  (title, bar, "x of y · n%", its own pause reason, Cancel), Queued (in
  FIFO order, Cancel), On this device (films fully held — the catalogue's
  own held set, Remove). Each row opens its film. An idle, empty page says
  nothing is preloading rather than showing three empty headings.
  Android-only, the web has no film preload.
- `NeedsSpace` now names the live cache budget beside what the film needs
  — "Needs 30 GB · budget is 8.0 GB · Try again" — the same figure the
  engine's own `fits` rule reads, so the reason a 30 GB film cannot start
  is visible without opening Settings first. A test walk against a real
  8 GB TV box budget is what asked for this.
- A film Android's own background time limit paused stays listed on the
  Preloads page — under Preloading if it was the one actually writing,
  under Queued otherwise — named "Paused — background limit" with one
  Resume action, rather than disappearing along with the rest of the
  queue the time limit clears. The engine reports these apart from the
  queue itself (`FilmPreloader.timeLimitPaused`), since pausing for the
  time limit empties the queue by design; the menu's own count includes
  them.
- A kids profile's Preloads page, menu count, and a queued film's own
  "Queued · after …" label all read only what that profile's own catalogue
  can resolve — a grown-up's own preload never surfaces there, the same
  reason it never appears on a kids profile's shelves.

## 0.74.0 — Android: Preload reaches the film page

**Added**

- A Preload control beside Play on a film's own page, phone/tablet and TV
  alike — the first thing on either screen that drives `FilmPreloader`
  (0.70.1). Films only (`Kind.MOVIE`, with a real size on record; a show's
  episodes already preload two at a time on their own): "Preload · 5.8 GB"
  idle, "Preload · 36% held" once playback alone has already put some of it
  in the cache, "Queued" waiting behind another film, "Preloading" with a
  thin full-width bar underneath on both surfaces ("2.1 of 5.8 GB · 36%",
  tap or OK to cancel), "Paused while playing" / "Waiting for Wi-Fi" / a
  background-limit pause (tap or OK resumes it) with the bar left standing
  through all three, "Preloaded ✓" with "Remove preload" in the phone's ⋯
  menu or a second TV plate, and "Needs 5.8 GB · Try again" — retryable
  the moment the cache budget is raised, not a dead end — beside a "Raise
  the cache budget" link straight into Settings › Storage. Reachable for a
  kids profile the same as Play is — it is not a household mark the way the
  editor's-choice pin beside it is. The phone's own pill row wraps
  (`FlowRow`) rather than crowding My List and ⋯ off a narrow phone once a
  Preload pill joins them.
- A quiet "Home server: x of y GB" line under the control once a paired LAN
  cache server actually holds some of the film (`GET /v1/sets/{id}`,
  0.70.0) — polled every 5s while the page is open and the film is
  actually writing, once on open otherwise, and not at all with the LAN
  cache off, no server paired, or an older server that has never heard of
  the route.
- `MenuScreen.Storage`: Settings opened straight to its Storage section,
  the same direct-section shape `System` already had — what "Raise the
  cache budget" actually opens.
- Android-only, by the same decision `FilmPreloader` itself shipped under:
  the web player has no film preload and gets none.

## 0.73.1 — Android: a film preload engine, not yet reachable from the UI

**Added**

- `FilmPreloader` (`:core:playback`): takes one film at a time into the
  device cache through the same strict `CacheWriter` writer `SeriesPreloader`
  already uses, now sharing one writer instance behind a `DownloadLane` so
  the two never write at once. `CacheWriter.cache()` blocks its own
  dedicated thread (`CacheDataSourceWriter`'s own dispatcher, not the
  preloader's worker), and is interrupted by cancelling the coroutine that
  called it — cancel, remove, and Android's own foreground-service time
  limit each cancel that one film's own `Job`, which reaches a running
  write, a metered-network wait, and a retry backoff alike. Per-film state
  (`Idle`/`Queued`/`Running`/`Paused`/`Done`/`NeedsSpace`/`Failed`) reads
  its held bytes from the cache on every observation, not once and
  remembered — a film already part-held from playback starts at its real
  percentage, a cancelled or removed one settles back to one just as
  honestly, and a `Done` film that eviction later took back stops claiming
  to be held. Pauses while a title is open in the player — playing,
  buffering, or paused by the viewer, any film, not only this one — and
  resumes once the player closes; pauses on a metered network instead of
  failing, checked both before a write starts and while one is running. A
  film's own whole size, not what else the cache happens to hold, decides
  whether it fits the budget (the LRU cache evicts older content to make
  room; only the title open in the player, if a different film, is
  reserved); a write failure backs off and retries a bounded number of
  times rather than giving up outright, since the core has no way to tell
  Kotlin a Telegram `FLOOD_WAIT` apart from any other transient fault.
- `PreloadService`: a `dataSync` foreground service that keeps a queued
  preload running while the app is backgrounded, started the moment
  anything is enqueued and stopping itself once the queue empties (staying
  foreground through a pause, since a `dataSync` service cannot be
  restarted from the background). Android's own 6h/24h ceiling for the
  type surfaces as a resumable pause rather than a crash. No
  `POST_NOTIFICATIONS` — the service runs regardless; its own notification
  is simply never shown, and a film page's progress bar is where a viewer
  actually watches it.
- `ActivePlayback`: feeds the engine what the player has open from a
  main-thread listener only — a film's own worker never touches the real
  `ExoPlayer` directly, which media3 does not allow off its own thread —
  and starts listening lazily, the first time a film is actually
  preloaded, so the catalogue merely being open never forces the app's
  player to build.
- Nothing in the app calls any of this yet: no button, no bar, no route
  into `FilmPreloader.enqueue`. The engine exists on its own; the film page
  that drives it is a later change.

## 0.73.0 — Cache server: a per-film status route

**Added**

- `GET /v1/sets/{id}` on `mediagram_cache`: `{"total","chunks_held","bytes_held"}`
  for one set, `total` null when the server holds no recorded total for it. A
  question, not a read — it touches no chunk's mtime and moves nothing in the
  LRU order, so polling it cannot itself change what eviction picks next.
  Answered straight from the in-memory index (already keyed per chunk) rather
  than a directory scan, since the index already makes a per-set tally cheap.
  Android's `LanChunkProtocol.setStatus` reads it the same lenient way
  `status()` already reads `/v1/status`: `null` for a 404 (a malformed id,
  or an older server without the route at all), a network error, or a
  malformed body — an id the server simply holds nothing of is a 200 with
  zeros instead.

## 0.72.1 — the department hero, in the web player's own layout

**Changed**

- Movies, Series, Tutorials, Documentaries, Collections and a franchise's
  own page open the way the web player's own department pages do: a lead
  title's art fading into the page from the left under the departments bar,
  an uppercase Fraunces name set as large as the cover's own, a Geist
  eyebrow, a Newsreader facts line, and that lead's own tagline as a
  pull-quote — top-right on a wide window, matching the web's own 900px
  breakpoint exactly (hidden below it, not kept visible, on a phone). The
  hero itself is never a tap target, on any of the six; only the quote's
  own credit opens the lead it names, the same as the web. Replaces the
  smaller, hard-edged hero these pages drew before, and the separate
  pull-quote block Movies, Series and Tutorials each drew under it — the
  quote is part of the hero now, so `PullQuote` is gone. A department's own
  name — one line always, "Documentaries" included — shrinks to fit its
  column rather than wrapping or truncating; a franchise's own name, never
  chosen to fit the way a department's is, wraps across up to three lines
  instead.
- A department tab bleeds its own hero under the departments bar the same
  way Home's cover does — the bar starts translucent and settles solid as
  the tab's own list scrolls the hero's bottom edge past the bar's, driven
  by that tab's own scroll position rather than Home's. A department with
  no lead art (or Solid artwork mode) keeps its bar solid from the top, the
  same as before.
- Every screen's own page ground moves from `colorScheme.surface`
  (`#151517` dark, `#FBF8F2` light) to `colorScheme.background` (`#0D0D0E`
  dark, `#F4F0E8` light) — the web's own `--paper`, which `surface` sat one
  step above. A department hero's own art already faded toward
  `background`; the page around it had not caught up to drawing on that
  colour until now, which is what the hard edge at the art's own bottom
  was.
- Collections gained the same kind of hero the four departments already
  have, crediting a franchise's own lead film, and its own franchise row
  and lists now scroll together as one page rather than a fixed column
  that could clip "＋ New list" off the bottom of a phone screen.

## 0.72.0 — Documentaries get their own place on Android

**Added**

- `Kind.DOCUMENTARY` for the index's own `docu` kind, which the Android app
  had been filing under Movies since documentaries themselves shipped
  (0.63.0) — a library's film count and film wall previously folded
  documentaries in with films for exactly that reason. A documentary plays
  and resumes like a film; a folder of them groups by show the way a
  course groups by folder, and anything uploaded on its own stays a plain,
  standalone title, the same split the web's own `groupDocumentaries` makes.
- A Documentaries tab, between Series and Tutorials, the same place the web
  player's own bar has carried it since 0.63.0 — the one department pill
  that is never hidden, reading "0" rather than dropping out of the bar for
  a library that holds none yet.
- The Documentaries department page: a hero ("Only in your library" ·
  "Documentaries" · a spelled count, not itself a link), Continue watching,
  Recently added, one row per folder ("All N" on the row's own heading opens
  it, the same course-style page a folder of lessons already opens), and a
  Standalone documentaries row for the rest — a Compose port of the web's
  own `renderDocumentariesDept`. Every plate here plays on tap rather than
  opening a title page, matching the web: nothing about a documentary comes
  from a provider, so there is no synopsis or cast worth a stop before
  playing it. An empty Documentaries library — every library, until someone
  uploads one — shows the same upload hint the web's own empty state does,
  on the phone and on television alike.
- Documentaries in search, between Series and Tutorials in the filter pills,
  the same order the web's own pills carry them in. A search hit had quietly
  stopped finding any documentary once `docu` moved off `Kind.MOVIE` here;
  it has its own group again now, playing on tap the same as an episode or
  a lesson row does.

**Changed**

- A film's Similar row and franchise link (`filmsOf`) now read the Movies
  shelf alone rather than every shelf's own films — what keeps a
  documentary, shelved on its own now, from turning up as "similar" to an
  unrelated film.
- A documentary folder gets its own collection key, distinct from a course
  or a show that happens to share its name — the two no longer resolve to
  whichever shelf is searched first.
- `runFor` (what a title not opened from a list or a search result plays
  into) now finds the collection that actually holds the set, not just the
  first one with a matching name — a documentary in a folder plays on
  through the rest of the folder, the same as a lesson or an episode does,
  and two collections that happen to share a name (a course and a
  documentary folder, or two shows) no longer hand a title the wrong one's
  run.
- The chosen tab is kept by its name rather than its plain position for the
  rest of a session — a profile switch, or a rail tap made while the
  library is still loading, now lands on the same tab regardless of
  whether a department shifted everything after it by one in between.

## 0.71.1 — the Android home tab, in the web player's own layout

**Changed**

- The phone/tablet home tab is rebuilt to the web player's own magazine
  front section, section for section: a cover story rotating through the
  day's featured films under the chrome (uppercase Fraunces headline at the
  web's own weight and optical size, eyebrow, deck, meta line, and three
  pills — Watch now plays the film, + My List toggles and reads back "✓ My
  List", Details opens its page), then the three editorial features (a
  wide lead plus two beside it on a tablet, one column on a phone; the
  second card alone keeps its own case rather than running uppercase),
  Continue Watching beside a pull-quote in one band, Recently Added
  (posters alone, no caption) beside This month's own numbered column,
  Latest series with its own captions, and Latest courses as a plain list
  — an index rather than posters, since a course carries no artwork of its
  own. Replaces the plain poster grid the tab drew before.
- A collection's own caption ("21 episodes · three seasons") now spells
  counts under twenty-one as words the way the web's own captions do,
  rather than printing every count as a figure.
- The tab's own scroll position is what the departments bar reads to
  decide how solid to draw itself over the cover, in place of a
  viewport-percentage heuristic an earlier build used, which stopped
  tracking the cover's real height once the cover stopped being a fixed
  aspect ratio.
- Fraunces across the whole app — not just the phone/tablet catalogue — now
  draws through static, pre-instanced font files rather than the variable
  font's own axis settings, which a device was found to ignore outright
  away from the font's own heaviest default instance. The shared design
  system carries this, so the television's own shelf headings and title
  names change weight the same way the phone's do.

## 0.71.0 — Android library chrome, in the web player's own layout

**Changed**

- The phone/tablet library's Material top app bar and serif tab row are
  gone. A tablet held wide gets the web's own left rail — wordmark, My List
  and Continue watching with their counts, Latest, Genres, Settings, System,
  then the library's own tally — beside a Geist pill bar (Home, Movies,
  Series, Tutorials, Collections, each with a count) laid over the Home
  tab's content; the bar starts translucent over the cover and settles
  toward solid as Home scrolls, and sits flush and solid above every other
  department. A phone or a narrower tablet gets the web's own ≤900px shape
  instead: the wordmark and icon-only rail row, a scrolling department pill
  row, then a search field and the avatar — the whole header hiding on
  scroll down and returning on scroll up, since a fixed three-row header
  would eat a phone screen the way the web's own static one never has to.
- The overflow ⋮ beside the departments bar now holds only the three
  actions the web has no counterpart for at all — Update library, TMDB
  key…, Start over — since every other destination it used to carry (My
  List, Continue watching, Latest, Genres, Settings, System) now has its
  own control right there in the rail or the header. A pushed page (a
  title, a genre, Latest opened this way) keeps its own back bar; on a
  tablet held wide the rail joins beside it and its own ⋮ trims to the
  same three actions, since the rail beside it now carries the rest — a
  phone or narrower tablet keeps the full menu there, with no rail to
  carry them instead. Settings and System still render without either, on
  every width.
- The rail's icons are the web's own line-drawn marks, traced into vector
  drawables at the same 1.4 stroke rather than redrawn from a different
  icon set.

## 0.70.2 — the one watch-state fake follows the core's rules

**Internal**

- `FakeCore`'s progress, watched marks, watchlist, Kids, editor's choice and
  collection stubs (`core:testing`) are no longer no-ops: a new
  `FakeWatchState` (its own file, so `FakeCore.kt` keeps its line budget)
  keeps them per profile, against a test-controlled clock that defaults to a
  plain monotonic count. `CoreContract` gains one case per rule — progress
  upsert and its non-negative clamp, newest-first order, finishing always
  re-stamping and clearing the position even on a repeat mark, taking a mark
  back leaving the position alone, the watchlist and Kids both idempotent
  and tombstoned, Kids shared across every profile, the editor's choice
  keeping one live pick, a list's rename/delete/membership refused off its
  owning profile — run against the fake now (`core:testing`'s unit test) and
  compiled for the real core's device run (`core:rust`'s `androidTest`) next
  phase. `WatchStateRepositoryTest` (`core:data`) drops its own hand-written
  `StateCore` for this one fake; two of its nine cases now check the actual
  written state on a second profile instead of a call-log string, which is a
  stronger proof of the same claim they always made.
- No behaviour change: this is test infrastructure only.

## 0.70.1 — a watched test that failed on a fast run

**Fixed**

- The web's "a finished show says when" test allowed the re-marked stamp one
  millisecond past the clock, but the test before it marks and un-marks the
  same title, and each of those clamps a millisecond forward — so when all
  three land in one millisecond the stamp is two ahead. The bound is now two,
  with the reason beside it; nothing in the state rules changed. It had
  started failing the pre-push run intermittently.

## 0.70.0 — more than one upload at a time

**Added**

- `upload_slots` (config, default 1): how many uploads may run at once
  across processes. Telegram limits upload speed per connection, not per
  account: measured with one film uploading beside the normal queue, two
  uploads together moved 26–31 MB/s against 12–13 MB/s for one, with no
  flood waits. Each slot is its own lock file; slot 0 is the historic
  `upload.lock`, so an older binary still running shares it and the total
  never exceeds the setting (`upload/lock.rs`).

**Fixed**

- Opening the Telegram session no longer fails when another process has the
  session file locked for a moment ("database is locked"); it waits and
  tries again, for up to about a minute (`telegram/client.rs`).
- `mediagram status` showed one upload with two running: both wrote their
  progress to one shared file, overwriting each other, and the first to
  finish deleted it. Each upload now keeps `upload-progress-<set>.json`,
  `status` reads them all (and the old shared file an older process may
  still write), and counts uploads as running while any slot is held.
- A part upload that Telegram rate-limits at the transport level (`bad
  status (negative length -429)`) is now waited out — 30 s, doubling to ten
  minutes, on a budget of its own — instead of being retried within three
  seconds and ending the whole run. Two seasons had stopped on it the hour
  two upload slots first ran (`upload/transport.rs`, `telegram/retry.rs`).

## 0.69.4 — closing the Settings/System redesign

**Fixed**

- Switching Settings sections on phone and tablet (Telegram → Appearance →
  Storage, and so on) could open the new section already scrolled to
  wherever the previous one had been left, instead of at its own top — the
  same bug the television's own Settings pane had already been fixed for.
  Each section now keeps its own scroll position again.

**Changed**

- `DESIGN.md` and `docs/system-architecture.md` now record the shipped
  Settings/System redesign: the Artwork picker's four modes, Settings'
  two-pane index and its lack of a top app bar, and where television's own
  Appearance answers fewer of the four questions than phone and tablet do.
  No other behaviour changed in this release.

## 0.69.3 — the television's Settings, in the same look

**Changed**

- The television gets the same Settings/System index the phone and tablet
  already have: a focusable column on the left — Telegram, Storage,
  Appearance, System, each with its own one-line status — beside the open
  section on the right, at ten-foot type and overscan-safe spacing. Moving
  the remote along the column only shows a section; Right or OK actually
  steps into it, onto its own first control. Left inside a row of controls —
  the accent swatches, the artwork cards — moves along that row first, only
  reaching the index once nothing is left of it; Up or Down at a section's
  own top or bottom edge stays put rather than falling through to the index
  above or below. Back from anywhere inside still comes straight back to the
  row that opened it, and each section opens scrolled to its own top. The
  overflow menu's System shortcut still opens straight to that row.
- Appearance gained the same Artwork question (Default/Blurred/Artwork/Solid)
  the phone and tablet ask, beside the accent swatches already there; a
  television still never asks Theme, since it stays dark regardless. The
  chosen accent and the chosen artwork now wear a light ring even when the
  remote is elsewhere — before, only a screen reader could tell which one
  was current.
- System re-reads every two seconds while its section stays the one shown,
  matching the web player's own poll and the phone/tablet's own release.
- The old single-scroll Settings and System screens, and their plain
  label/value rows, are gone — replaced by the same ledger look (a quiet
  label, its value, a soft rule beneath) the phone and tablet already draw.
- On the tablet, Settings' columns get room to breathe: the index is 280dp
  rather than the mockups' 320dp, the page margins are sized to the
  ≈1160dp a tablet actually has, and a section's columns sit side by side
  only while each keeps at least 320dp — the rest move to a second row
  instead of every column squeezing narrower.
- Storage's home cache server block reads the server's own `GET /v1/status`:
  what it holds against its budget ("1.0 MB of 10 MB (10%)") and how many
  chunks, on every surface that shows the block — the television's own copy
  included, which before this only ever read the connection line.

## 0.69.2 — Android Settings and System, in the web player's look

**Changed**

- Settings and System are one screen now, not two: on a phone, an index of
  four rows — Telegram, Storage, Appearance, System, each with its own
  one-line status — opens the one asked for, with Back returning to the
  index; on a tablet held wide, the index sits beside the open page the
  whole time, Telegram selected by default. The overflow menu's System
  shortcut still opens System directly, and Back from it leaves straight
  back to what was on screen, since it was asked for directly rather than
  found through the index.
- Every row and page now draws in the web player's own settings look: a
  huge uppercase title over a small tracked-caps line, quiet ledgers for
  facts, outlined pills for actions, and one soft-cornered "ledger" table in
  place of the plain label/value rows Settings and System used before.
- Appearance gained the web's Artwork picker (Default/Blurred/Artwork/Solid)
  — modelled and rendered since 0.69.1, now with a way to choose it — beside
  round accent swatches and theme cards drawn the same way.
- System re-reads every two seconds while its page stays open, the same
  interval the web player's own status panel polls at, rather than only
  once per visit.
- Television's own Settings/System follows in a later release.

## 0.69.1 — Android pages follow the web's Artwork setting

**Added**

- The web's fourth Appearance question — Artwork: Default, Blurred, Artwork,
  Solid — now has an Android answer, held per device beside theme and accent
  in the same preferences file. Blurred softens a hero's picture to colour
  and light (a real blur from API 31; a tiny decode upscaled by the crop
  below it, everywhere older); Solid drops the picture and its tagline quote
  from the title spread and a department's own hero, leaving the words on a
  plain page; the magazine home's cover story keeps its picture in every
  mode but Blurred, matching the web's own `.cover-stage`, which Solid never
  touches either. Artwork reads the same as Default on Android by design:
  the web's Artwork rule only changes its wide two-column spread, which
  Android does not have yet — the phone's spread is already the web's own
  narrow layout at every width, so there is nothing for the rule to move.
- Not yet: a way to choose it. The model, persistence and every hero's
  rendering are in place; the Settings picker that writes the choice is
  separate, later work.

## 0.69.0 — the Android app takes the web player's colours and type

**Changed**

- Every screen's dark palette is now the web player's own dark theme
  (`styles/theme.css`'s `:root`), not a warmer near-black tuned separately
  for the phone: pages read darker and cooler than before, most visibly on
  the catalog wall's ground and app bar. Two web tones without an Android
  equivalent, sidebar and rule-soft, are now carried too, for a settings rail
  still to come.
- Counts, labels, captions and other interface text — everywhere that isn't a
  title or a whole sentence — is now set in Geist, the web's own interface
  face, in place of Newsreader. Sentence-length text (loading, empty and
  failure messages) stays in Newsreader.
- A control — a button, an input, a dialog — now takes a 6dp corner, the
  web's own radius, through Material's `Shapes`. A plate is deliberately not
  a control and keeps its square corner.
- Not changed yet: the Settings and System screens' own layout, the ported
  Artwork setting, and plate corners, which stay square while the rest of
  the system moves to a soft 6dp — each is separate, later work.

## 0.68.15 — the web player loads into the page it is loading

**Fixed**

- Opening the web player showed "Loading your library…" over a row of grey
  poster boxes — the shelf layout the home page no longer has — and then the
  magazine home replaced it: one layout, then another. The loading state is
  now shaped like the cover story the first page opens with (the same dark,
  full-bleed block, the words where its title will be), and the page starts
  with the cover's dark-glass masthead, so the home fades in over the block it
  was waiting in. The first draw clears the cover look for any other page.
  The masthead also gets the cover's opening colours when its scroll timeline
  is idle, which on the one-screen loading state left black links on the dark
  cover.

## 0.68.14 — the Android catalog asks its core once per question

**Changed**

- The catalog listing (`Core::list_sets`) now carries every set's poster,
  backdrop and (for an episode) season poster already resolved to a path on
  disk — matching what the web player's `/api/sets` has always carried for a
  poster and a season poster. A backdrop is a known, pre-existing exception:
  it resolves disk-only on Android, where the web also counts one the
  `artwork` table alone carries (daBOB/mediagram#1 tracks closing that gap).
  `CatalogRepository` used to make one further `posterPath` crossing per set
  per artwork field to get there (poster, backdrop, and per season plate on
  the TV surface); it now reads the resolved fields straight off the record.
  A title's credits, a person page and search-by-name results carry their
  portraits the same way, so a cast list or a page of people costs the one
  crossing that fetched it, not one more per name.
- A new `Core::media_set(setId)` answers one set by id directly
  (`catalog::playable_set`'s own indexed lookup), replacing
  `CatalogRepository.mediaSet`'s former "list everything, then find it" —
  the player's five `mediaSet` calls opening one episode measured
  107–251 ms each against a ~1,160-set catalog before this; the id is now
  looked up without listing, enriching or crossing the boundary for every
  other set in the library.
- On a listing, the index's `artwork` table (custom poster/backdrop bytes,
  see §10.1) is read once into the set of keys it actually holds, rather
  than queried per set with a miss — a key the table does not hold is never
  queried about at all, not just once a snapshot with no table at all is
  ruled out.

**Internal**

- `Core::poster_path`, the per-key FFI method, is gone — nothing on the
  Kotlin side still resolves a key to a path itself, now that a listing and
  a one-set lookup both carry resolved artwork and credits carry resolved
  portraits. The internal resolver it wrapped survives as `store::poster_path`,
  still used for a single portrait fetch download; a title's credits, a
  person page and search-by-name results resolve their portraits through
  the batched resolver instead (`store::resolve_with`, reusing the
  connection and the artwork-key set that call already read), rather than
  reopening the index per name the way `poster_path` would on a miss.
- `SetSummary.backdrop_key`/credit records' `portrait_key` are gone,
  replaced by `backdrop_path`/`portrait_path` carrying the resolved value
  directly — nothing needed the raw key once nothing resolves it separately.
- TV season plates and the mobile `CollectionScreen` no longer take a
  `posterPath` lookup of their own; `SeasonPlate.posterPath` reads straight
  off the episodes the core already resolved it for.
- Artwork materialised from the `artwork` table is now written beside its
  final name and renamed into place, not written to the name directly — a
  listing and a `mediaSet` call can now resolve the same key at the same
  time, each against its own connection, and a reader must never see a
  half-written file at the name it is about to open.

## 0.68.13 — the Android app speaks to its core through the generated interface

**Internal**

- `CoreClient` and `DefaultCoreClient` (android/core/data) are gone. Every
  ViewModel and repository above `core:data` now reaches the generated core
  through its own `CoreInterface` directly; the 7 call sites where it uses
  unsigned integers (`refreshLibrary`, `refreshCatalog`, `totalSize`, `read`,
  `fetchMissing`, `person`, `fetchPortrait`) convert at the call site instead
  of through a wrapper. The close fence — retire local state, then release
  the native handle, once, with a failed retirement left open for retry —
  moved into `CoreProvider`, which already owned every production close;
  `StoredCoreProvider` is now generic over a type that is both `CoreInterface`
  and `AutoCloseable`, since the generated interface itself has no `close()`.
- A new module, `core:testing`, holds the one fake of `CoreInterface` every
  other module's tests now build on (`FakeCore`, plus `FakeCoreProvider` and
  two same-shaped aliases, `ResolvedCoreProvider`/`CatalogCoreProvider`),
  replacing six separate per-module fakes and five `mockk<CoreClient>`
  doubles. `FakeCore` keeps the real contract where tests touch it — `NotFound`
  reading past a set's end or an unknown set, the same for `totalSize`,
  `revokeSession("0")` refused — checked by a shared contract suite run once
  against the fake (`core:testing`'s own unit test) and once against the real
  generated `Core` on a device (`core:rust`'s `androidTest`, next to
  `CoreLoadsTest`).
- No behaviour change: this is the seam the app talks to the core through,
  not what either side of it does.

## 0.68.12 — Android preloads what the web player preloads

**Fixed**

- The Android player preloaded episodes the web player never would. Played
  from a hand-built list or the Kids wall, it walked the list and took the
  next two episodes it found; and it walked past anything that was not an
  episode to find two. The web player — the reference — preloads only while
  an episode plays through its own show, only the next two positions, and
  only those that are episodes, because the preload fetches in the
  background from a flood-limited account. Android now keeps the same rule
  (`PlayerViewModelPreload.kt`, after `playsNext`).
- Behind it: an up-next switch stored a show's own run on the player frame
  as if the viewer had picked it by hand, so "played from a list" could not
  be told apart after the first episode. `LibraryPositions.replacePlayer` now
  keeps a run only when the frame already had one.

## 0.68.11 — what plays next and what is preloaded are one answer

**Internal**

- The web player's choice of what plays after a title, and of what the
  server is asked to preload while it plays, moved out of `app.js` into
  `web/public/lib/playback/plays-next.js` as one pure function, `playsNext`.
  The rule it keeps is unchanged: the next two series episodes, and nothing
  for a lesson, a documentary or a hand-built list, because the preload
  fetches in the background from a flood-limited account. The browser half of
  that rule had no test before; `web/test/plays-next.test.ts` now pins it,
  beside the "next" it must always agree with. `app.js` is 30 lines shorter,
  and its line ceiling came down with it.

## 0.68.10 — the web player's addresses have one home

**Internal**

- The hash address format — `parse`, `href`, `go` — moved into one module,
  `web/public/lib/address.js` (+ `address.d.ts`). `drawRoute` (`app.js`) now
  dispatches on a typed address instead of splitting `location.hash` by hand,
  and every card, crumb, pager and `location.hash =` that used to build a
  `#/…` string itself (about 47 sites across 16 files) now calls `href` or
  `go`. The three separate show-openers in `app.js` are one function,
  `openShow`, now. The format itself is unchanged — every address the app
  accepted before still parses to the same page, an already-bookmarked link
  still opens where it always did — this only gives the format one owner.
  `docs/web-player.md`'s address table was also wrong in three places (search
  took a query string rather than a path segment; a list's own route was
  listed twice, once wrongly; a season's route was described as an episode's)
  and missing four real pages (`#/documentaries`, `#/continue`, `#/watchlist`,
  `#/system`); corrected, and `web/test/address.test.ts` now parses every row
  in it, so the table cannot drift from the code again. Its two Movies rows
  were wrong too: `#/movies` is the department's front page, and the paged
  shelf is always numbered from `#/movies/page/1`.
- One address opens something different: a section named after a built-in
  object property (`#/constructor`, `#/toString`) used to pass for a real
  section and then fail to draw, leaving a blank page. It now opens Movies,
  as every other unknown section does.
- `pager.js` lost `parsePage`, which `address.js` now does alone, and the
  `section` argument its links never used.

## 0.68.9 — a conversion no longer pulls the whole film at once

**Fixed**

- A title converted in copy mode ran ffmpeg at 7.4x real time, reading its
  input through the player's own stream route as fast as the Telegram link
  allowed: about 700 `upload.getFile` requests in 73s on one measured title,
  enough to trip 9 flood waits. Playback itself was unaffected — the decoder
  stayed far ahead — but a seek meanwhile queued behind those flood-slept
  requests, and the conversion fetched the whole film for a viewer who might
  stop after ten minutes. Conversions now read at up to twice real time after
  a 30-second burst (so start-up and a seek's restart are as quick as
  before), on ffmpeg 6.1 or newer; an older ffmpeg is detected once at
  startup and left unpaced rather than have every conversion fail on an
  option it does not recognise.

## 0.68.8 — the System page counts real reconnects only

**Fixed**

- The System page's Reconnects counter climbed by about one every 9 seconds
  while the player sat idle, with nothing actually reconnecting — the main
  connection's socket never closed or redialed the whole time. Cause:
  `teleproto`'s keepalive loop treats any gap since the last pong of 5s or
  more as "just woke from sleep" and announces a fresh connection on success,
  but the loop itself only ever pings every 9s, so that gap was always past
  the threshold and the "woke from sleep" branch fired on every ordinary
  ping. Patched the vendored dependency (`web/patches/teleproto@1.229.0.patch`)
  to raise that threshold above the ping interval, so it only fires on an
  actual gap — a suspended laptop, a backgrounded tab. No connection was ever
  dropped; only the count was wrong.

## 0.68.7 — an uploaded title arrives with its cast

**Fixed**

- Uploading a film or a show recorded its description but not its cast or
  a film's franchise; those came only with the next `mediagram metadata` or
  `sync-index`, so titles uploaded since the last run showed no Cast tab
  (nine series added one evening, for instance). The upload now records
  cast and franchise with the description, once per title, the way
  `metadata` does; a provider that will not answer costs the tab, never the
  upload. Titles already uploaded without cast get it from the next
  `mediagram metadata`.

## 0.68.6 — finishing a title is one write, on the web and on Android

Architecture review candidate E.

**Fixed**

- Finishing a title (the credits rolling, or "mark watched") was two writes:
  the position deleted, then the completion recorded. A second write that
  never landed — a dropped request on the web, the Android app killed in
  between — left the position gone with no completion, and the completion is
  the only thing that beats another device's older copy of that position, so
  the next sync brought the finished title back onto Continue. Marking a
  title watched now clears its position in the same transaction, on the web
  player's server as in the Android core (which gains an explicit
  transaction), and both players send the one write. Taking the mark back
  still leaves a position alone.

## 0.68.5 — the web player's library session has one home

Architecture review candidate D.

**Internal**

- The catalog a profile sees, and keeping it current, moved out of
  `web/public/app.js` into `web/public/lib/library-session.js`: the fetch and
  diff of `/api/sets`, the kids filter, grouping into shelves, the coalesced
  `/api/events` stream (open only while the tab is visible), and holding a
  redraw back while the player or a list picker is open. `app.js` keeps
  rendering, DOM counts, the colophon and routing, reached through the new
  module's `onData`/`onRedraw` notifications and a `hold()`/`release()` pair
  the player and list editing each take one of. The ordering bugs of the last
  week (coalescing, the wait-until-close redraw, one event stream per visible
  tab) all lived in `app.js`'s shared state between unrelated concerns, which
  is what this module now contains on its own. A few behaviours did change
  along the way: becoming visible again now also refreshes the editor's
  choice, not just positions; a title finishing while it plays updates its
  shelf's count at once instead of waiting for the shelf itself; and startup
  no longer asks `/api/player` twice. A `drawn()` on the module settles a
  redraw the player or list-editing hold owed the moment `app.js` draws it
  anyway, so a hold releasing afterwards does not draw the page a second time.
  Behind a small browser port (`library-session-port.js`), so the module's own
  tests (`web/test/library-session.test.ts`) run without a DOM.

## 0.68.4 — course lessons named with an outline number

**Fixed**

- `add-course` read `6.10 – Deep stage` as number 6, so every lesson in a
  chapter named `6.1`, `6.2`, … `6.10` collided, was renumbered in text order
  (`6.1, 6.10, 6.2`) and kept "10 –" in its title. A dotted prefix now counts
  by its last segment (lesson 10, titled "Deep stage"), and a dash or colon
  after the number is dropped from the title (`media/file_names.rs`).
  Courses already uploaded under such names (Wall Street Story) have their
  lessons stored under the old numbers: re-running `add-course` on them
  would compute different identities, so re-running is not safe for them.
- `add-show` filed a double episode (`S09E19E20`) as E19 alone, so `status`
  reported E20 missing. It passes each episode's number explicitly, and an
  explicit number dropped the range the file name gave. A number equal to
  the name's own start episode now keeps the range
  (`metadata/episode_value.rs`).
- `prepare --mp4` failed on every file carrying a Blu-ray (PGS) or DVD
  subtitle: an mp4 holds subtitles only as text, and ffmpeg refused the
  whole file ("Error opening output files: Invalid argument"). Picture-based
  subtitle tracks are now left out of an mp4; text ones are still converted
  (`media/prepare/plan.rs`).
- `prepare` rejected a correct result as `MissingLanguage("eng")` when the
  video track was tagged `eng` beside German-only audio: the languages the
  result must keep were read from every kept track, picture included. They
  now come from the kept audio alone (`PreparePlan::expected_audio_languages`).
- `prepare --mp4` also failed on a Matroska attachment — an embedded font,
  or a release's `.nfo` — or a data stream: an mp4 cannot carry them and
  ffmpeg refused the whole file. They are now left out of an mp4 too;
  chapters are not streams and still travel (`media/prepare/plan.rs`).

## 0.68.3 — a set completes in one transaction

Architecture review candidate C. Review:
`plans/reports/code-reviewer-260927-0345-set-lifecycle-review-report.md`.

**Fixed**

- Completing a set was three writes with three failure policies: the set
  marked complete, then its remux forgotten (a failure ignored), then its
  source forgotten (a failure fatal, after the set was already complete).
  It is now one transaction that also records the publish the set is owed,
  so a set is either pending with a source to resume from, or complete with
  nothing left behind and a publish owed; a failed completion leaves it
  pending and resumable.
- `rescan` completing a set it finds whole in the channel now forgets the
  source an interrupted upload recorded, so no stale path stays in the
  index (and in every snapshot pushed from it).
- A set removed while it was uploading is no longer reported "added" when
  its last part lands, and its source file is not deleted as if it were.
- A remux that cannot be deleted is named in the warning, to delete by hand:
  it sits beside the original when no temp directory is configured.

**Internal**

- `index::lifecycle` is the one module that spells a set's `source:` and
  `tmp:` meta keys (spellings unchanged, and pinned by a test, since
  existing indexes resume through them); planning, uploading, rescan, the
  upload session and `remove` go through it. `run_set` reports whether the
  set completed, so the upload session no longer re-reads the status, and
  the `finish_from` pass-through is gone.

## 0.68.2 — the series preload stops tripping Telegram's flood limit

What the viewer saw: `[INFO] Sleeping for Ns on flood wait (Caused by
upload.GetFile)` every few seconds while a show or tutorial episode played,
with the background preload of the next two episodes as the likely cause of
occasional stalls (`plans/reports/debugger-260927-0310-web-getfile-flood-wait-report.md`).
The preload's whole-episode download shared the same 4-slot `DownloadGate`
as playback but held a slot continuously and issued requests back-to-back,
fast enough on its own to trip the limit even with no other reader active.

What changed: the gate now has a `background` lane (`telegram/download-gate.ts`)
that never starts fresh, and never holds a freed slot, while any foreground
read (playback, readahead, the audio-track probe, transcode) is running or
queued — the series preload (`cache/series-preload.ts`) runs in that lane
(`telegram/part-fetch.ts`'s `backgroundFetcher`). The preload also paces
itself to one 512 KiB request per second, regardless of the gate, so it
cannot flood Telegram by itself either.

## 0.68.1 — a completed set is owed a publish the moment it lands

Review follow-up to 0.68.0
(`plans/reports/code-reviewer-260927-0325-upload-session-review-report.md`);
the entries below under 0.68.0 describe the result.

## 0.68.0 — one upload session behind every uploading command

Plan: `plans/260927-0302-upload-session-module/` (architecture review
candidate A). Terms: `CONTEXT.md` (Upload session).

**Changed**

- `add-show`, `add-course`, `add-docu`, `finish-set` (behind `add`) and
  `resume` run through one upload session. Each item takes the upload lock
  on its own and re-reads what the index holds once it has it, so a file
  added meanwhile goes between two episodes, and a background `add` whose
  set `resume` finished first finds it complete (and deletes its file when
  asked) instead of failing.
- A failure while sending, after the transport's own retries, stops the
  session: the rest of the walk is not tried (each would fail the same way),
  what completed is published, and the command says how many items were not
  reached. A missing or changed source still blocks only its own set.
- An item counts as uploaded only when its set completed; `add-docu` on a
  single file no longer publishes when its set did not complete.
- A session publishes once at its end, over the connection it uploaded with.
  Every set it completes is owed a publish the moment it lands, recorded in
  the local index, so a walk interrupted after that (Ctrl-C mid-show) still
  leaves the publish owed, and the next session pays it even with nothing of
  its own to upload. It skips the publish while another upload is running
  (one pin instead of two); the debt stays until a publish settles it.
- Output: `resume` says "set X added" for each set it completes, as every
  other upload does, and "already finished by another upload" for one it
  finds done; `finish-set` says "set X added" only once the set completed;
  `add-docu FILE` names the file before planning it rather than the resolved
  title after. `resume` also fails when a set could not be resumed at all.

**Internal**

- `upload/session/` replaces `Uploader`, `finish_with`, `finish_one` and
  `upload::resume::pending`; `course::upload` is the one walk `add-course`
  and `add-docu` share. Tests: `tests/upload_session.rs`, and the source
  checks in `tests/upload_finish.rs`, through the session with a fake link.

## 0.67.1 — re-running `add-docu` on a folder no longer uploads it again

**Fixed**

- `add-docu` on a folder records its episodes as `docu` but looked them up as
  course lessons (`tut`), so a re-run never saw what it had uploaded and sent
  the whole collection again as duplicate sets. The lookup now takes the kind
  the walk records (`set_lookup::lesson_status`).

## 0.67.0 — one module pulls and publishes the channel index

Plan: `plans/260927-0146-channel-index-module/` (architecture review
candidate B). Terms: `CONTEXT.md`.

**Changed (breaking)**

- `push-index` always pulls the channel index first, as every publish after
  an upload already did. `--merge` is gone (it is the default now), and so is
  `--check`: `pull-index --dry-run` reports what the channel holds that this
  index lacks, naming the first few sets. `--force` still replaces the channel index without pulling.

**Fixed**

- The uploader chose "the channel index" differently from the players: it
  read only the pins, so a snapshot an interrupted publish left unpinned was
  invisible to it though every player showed it, and it took a member's
  pinned post and a snapshot dated in the future at face value. It now reads
  the pins and the marker search like the players, and chooses with the rule
  that now lives in `mlib-spec` (`index_caption::newest`), which core
  delegates to and the web player's fixtures check.
- A publish landing from another machine while this one was under way was
  refused; it is now pulled in and the publish goes ahead. One that keeps
  landing (three times) fails the publish, sending nothing.
- Two background uploads on one machine could publish at the same moment.
  Publishing now takes a `publish.lock` beside `upload.lock`.
- A publish made two Telegram connections and downloaded the channel index
  twice. It now makes one, and downloads nothing when the channel index is
  the one this machine last pulled or published. A pull that fails, or that
  leaves a conflict unresolved, does not count, so the next publish pulls
  again.

**Internal**

- `crates/mediagram/src/channel_index/` replaces `commands/pull_index`'s
  helpers and `telegram/{index_publish,index_guard,download_index,unpin}`.
  The channel sits behind a `ChannelRemote` port with a Telegram adapter and
  an in-memory one; `tests/channel_index.rs` covers the round trip, including
  the refused and the silently ignored unpin.

## 0.66.4 — transcoding fits pictures larger than UHD

**Fixed**

- A title larger than UHD (screen recordings at sizes like 4784x2464) could
  not be converted for the browser: VAAPI refuses anything over 4096 wide,
  and a frame that size is past every H.264 level. The transcode now scales
  such a picture down to fit inside 3840x2160, keeping its shape, before
  encoding (and before the VAAPI upload); anything smaller passes through
  untouched (`web/src/transcode/args.ts`).

## 0.66.1 — the web player turns its pages cleanly

**Fixed**

- Going to another page from partway down one could blank the screen for a
  moment before the new page snapped in. The page turn was a view transition,
  and the scroll offset moving under it (clamped to a shorter page) drew its
  snapshots out of place. It also lifted the page above the masthead and rail
  while it ran. The new page now rises in on a plain CSS animation of `main`
  (`web/public/lib/page-turn.js`).
- A page gone to opened at the offset of the page left, clamped to its
  length. It now opens at its top, and Back or Forward returns to where the
  page was left.
- A vertical stripe crossed the backdrop of a film page and a department
  front page while its art faded in: the art's entrance zoom reached past its
  box, outside the paper gradient over it. The art box now clips it.

## 0.66.0 — the editorial departments on Android, phone and television

Pays what 0.62.0 left "Owed to Android". Plan:
`plans/260926-1330-android-editorial-departments-parity/`; TV review and device walk:
`plans/reports/code-reviewer-260926-1835-tv-editorial-parity-review-report.md`.

**Added**

- Core read API for credits, people, franchises and lazily fetched portraits (a person's
  portrait is fetched the first time a Cast row or person page shows them, then cached).
- Phone and television: department pages (Movies, Series, Tutorials) with a hero, film pages
  with Overview/Cast/Similar/Details tabs and "Part of <franchise>", series pages with
  Episodes/About/Cast/Similar, person and franchise pages, Collections with franchises and
  lists, grouped search (Films, Series, Tutorials, People, Collections, with filters), Latest
  and Genres. The television's Home takes the web's magazine layout.
- Settings › Appearance: theme and the seven accents on the phone; the television stays dark
  and takes the accent only (user decision).

**Fixed (television, found in review and on the real box)**

- Every build crashed at launch: a view model declared in `ui-tv`, which runs no Hilt
  processor. The television now uses `feature:catalog`'s own.
- Back returns to what was opened — a search result below the fold, a show, person or
  collection from search, a cast member or similar title (with its tab) — and department
  pages no longer take the remote from Search or the Menu.
- Rows show every title instead of six; the cast row scrolls; Home, Movies and Series arrive
  on their hero inside the overscan margin; tab labels stay readable when the row has focus;
  Collections insets its lists once; long rows compose lazily for the box's CPU.

## 0.65.1 — sync-index no longer stops before its push

**Fixed**

- `sync-index` failed at step 4 with "…library.before-channel-merge-….db
  already exists; not overwriting a backup". It pulls twice in one process,
  usually within one minute, and both pulls chose the same backup name. A
  second backup now takes the next free `-2`, `-3`… name, and the first is
  still never overwritten (`pull_index/backup_path.rs`).

## 0.65.0 — sync-index, merge-first publishing, progress lines

**Changed**

- Every publish after an upload (`add`, `add-show`, `add-course`, `add-docu`,
  `resume`) now pulls the channel's index in first, then pushes
  (`pull_index::merge_and_publish`). Before, a push from the other machine
  in the meantime got the publish refused ("the channel's index holds N set(s)
  this index does not"), which is why uploads ran on one machine at a time. Two
  machines on 0.65+ can now upload at once. A push landing between the pull and
  the publish is still refused by the guard, never dropped. Keep the two
  machines on different folders, since nothing detects the same file uploaded
  on both.
- The refusal message now suggests `push-index --merge` first, then `--force`.
  The hint after a failed publish says `sync-index`.

**Added**

- `mediagram sync-index` pulls the channel's index, runs `metadata` and
  `posters`, then pushes, so one command does what took four. Takes `metadata`'s
  `--refresh-older-than`. A failed artwork fetch is reported and does not stop
  the push, since that art never leaves this machine.
- `metadata` and `posters` keep a progress line on the terminal: count,
  percentage and time left (`describing 451/908 (50%) · eta 2m10s`,
  `fetching 5200/8718 (60%) · eta 2s`). They draw nothing when piped. The line
  is `term::count_line`, shared by both.

## Unreleased — 0.64.0

The television surface, from `feat/android-tv-ui`.

**Added**

- The Android TV surface: `:ui-tv` renders the same `feature:*` ViewModels as the
  phone with `androidx.tv:tv-material` — setup and sign-in, "Who's watching?", the
  masthead and Home shelves, walls, title/series/season pages, the full-screen player
  on remote keys, search and genres, and the Menu's System, Settings, TMDB key and
  Start over. Back always lands on the row or card that opened a screen. See
  `docs/system-architecture.md`, "The television surface".
- TV Settings: the cache location picker ("Where"). A USB drive shows up only when
  set up as *removable* storage; Android never lists adopted (internal-format)
  storage in `getExternalCacheDirs`. Checked on the real box with a 512 GB stick.
- TV Settings: the home cache server block — status, on/off, and the address and
  pairing token each asked as a text question. `lanCacheStatusLine` moved into
  `feature:system` so the phone and the television share one sentence.
- TV Settings: active sessions, as the phone has them — this device marked, a
  second press on another session's row to revoke it.
- TV TMDB key: a blank answer is ignored rather than clearing the stored key
  (the keyboard's action key also just closes the keyboard on a remote);
  "Clear stored key" is its own row behind a confirmation.
- A `benchmark` build type: minified and not debuggable like a release, but
  debug-signed so it installs over a signed-in debug install. A debug build felt
  slow on the TV box.

## 0.63.0 — documentaries and custom artwork

Schema v10, the `artwork` table, `mediagram artwork`, `add-docu`, and the new
`Kind::Docu`. **Web:** a Documentaries department in the top bar (between
Series and Tutorials), artwork-table images served before TMDB files, tutorial
cards with their own art, and a Documentaries group in search. Unnumbered lesson
and part names sort naturally (`Teil 2` before `Teil 10`).

**Schema**

- Schema version 10 (`mlib-spec`): new `artwork` table `(key, mime, bytes)`,
  additive over v9. `READABLE_SCHEMAS=[6,7,8,9,10]`; `OLDEST_READABLE_SCHEMA`
  stays 6. Readers tolerate v9 and earlier (the table is simply absent). A
  channel merge carries missing keys the same way it already carries missing
  credits and franchises (`index::merge_artwork`).

**Added**

- **`Kind::Docu`**: a documentary recorded from a TV station, never looked up
  at any provider (a TMDB film with the documentary genre stays `Movie`). A
  standalone file is titled from its name, exactly like a movie; a file inside
  a collection folder is grouped and numbered exactly like a course lesson
  (`Kind::Tut`), sharing `add-course`'s walk, identity and dry-run machinery.
  Gains its own display code (`C02E03`), `edit --kind` target, and label.
- **`mediagram add-docu <file|dir>`**: a file uploads one documentary; a
  folder uploads a collection (e.g. "Terra X"), grouped by the folder name the
  way `add-course` groups by course title. Summaries and subtitles via the
  same sidecars a course lesson reads. `poster.*`/`backdrop.*` at a
  collection's root become its artwork. Flags: `--title`, `--cid`,
  `--dry-run`, `--no-push`, `--no-remux`, `--variant`.
- **`mediagram artwork <set-id|title> --poster <file> --backdrop <file>
  [--clear]`**: custom poster/backdrop bytes, stored in the index and
  overriding TMDB's own art when both exist. Resolves to the existing
  `tmdb-…` key when the target has a provider id, else `title-{slug}` (the
  same slug `add-course` derives a default collection id from). 1 MB cap per
  image; the uploader asks for a resize rather than storing a larger one.
  `add-show` and `add-course` pick up `poster.*`/`backdrop.*` from a folder's
  root the same way.
- `mlib_spec::package::title_art_key`: the one place a title-with-no-provider-id
  art key is derived, called by the uploader and by `mediagram-core`'s
  `poster_key_for`. `poster_key_is_valid` accepts `title-{slug}[-bg]` beside
  the existing `tmdb-…` shapes.
- Android/core `poster_path` checks the `artwork` table before falling back to
  a TMDB fetch, writing a hit into the artwork directory once.

## Unreleased — 0.62.1

- **Navigation**: every link appears once. The departments (Home, Movies, Series,
  Tutorials, Collections) live only in the top bar, which now carries the Collections
  count; the side rail keeps the viewer's shelves and utilities (My List, Continue
  watching, Latest, Genres, Settings, System).

## Unreleased — 0.62.0

Pending merge on feat/editorial-departments branch: web player department pages,
feature pages with tabs, Cast via schema v9 credits, franchises, search paging
and grouping, Settings shell, dark-first theme system.

**Schema**

- Schema version 9 (`mlib-spec`): `shows` gains `collection_id`, `collection_name`,
  `series_type`; new `credits` table (908 titles, 11,706 cast/crew/creator rows,
  portraits at 185px); new `franchises` table (202 franchises, 363 shows grouped).
  `READABLE_SCHEMAS=[6,7,8,9]`; `OLDEST_READABLE_SCHEMA` stays 6. Readers tolerate
  v8 (tables and columns optional). Both uploaders and all Android installs must
  run ≥0.62.0 before any v9 push/export; installed Android builds refuse v9 packages.

**Added**

- **Department pages** (`#/movies`, `#/series`, `#/tutorials`): headline, tagline,
  genre pills, and shelves grouped by popularity/newness. Movies shelf paged
  (`#/movies/page/N`; page 1 is plain `#/movies`), showing 48 films. Series and
  Tutorials list every held show/course at the foot. No Popular/New rows when
  count ≤ 12.
- **Feature pages** (film, series): full-bleed TMDB backdrop, tagline over
  artwork, title, runtime/network, rating, description. Tabbed interface:
  Overview (description), Cast (credits), Similar (recommendations), Details
  (metadata). Tabs keep selection and focus on every redraw. Cast tab only when
  credits exist (schema v9 required).
- **Cast** from v9 credits: cast tab lists top 12 by billing order with character
  names, circular 185px TMDB portraits (`tmdb-person-<id>.jpg`). People search
  returns cast and crew, circular cards, filtered to titles the profile can see,
  link only when the person has a detail page.
- **Person pages** (`#/person/<id>`): portrait, name, filmography split into
  Films and Shows, both filtered to profile visibility.
- **Franchises** (via `belongs_to_collection`): Collections page lists TMDB
  franchises (≥2 films held) and user-created lists. Franchise detail shows
  large cards per film with watch state. Link renders only when franchise has ≥1
  viewable film.
- **Search** grouped by type (Movies, Series, Episodes, Lessons, People,
  Collections), with type filter pills in memory (not in hash).
- **Settings page** (`#/settings`, in the rail for everyone): Appearance (theme
  Dark/Light/Auto, 7 accents, artwork mode) and Profile (who is watching, Switch
  profile); plus **Library & Telegram**, the 0.61.0 admin-gated settings page as a
  tab, shown only when `/api/settings` answers (own network). Its separate rail link
  is gone. Appearance is stored per browser, not per profile.
- **Theme system**: dark-first with light variant via `data-theme` (Dark/Light/Auto)
  set by `lib/appearance-boot.js` before first paint. Accent system: 7 contrast-tested
  swatches (coral, blue, violet, teal, green, amber, rose), each pair 4.5:1 in both
  themes. Artwork-backed blocks (cover, features) read the same in both via fixed
  on-image colours.
- **Pager**: Movies shelf and department pages use sequential page links
  (`1 · 2 · 3`); back, reload and shared links land on the page.

**Changed**

- The home page now reads as a magazine front: rotating cover story on a TMDB
  backdrop, three single-title features (Editor's choice, Trending, Staff pick),
  Continue beside a pull-quote, Recently added beside "This month". Every label
  and line comes from catalog; none invented. Layout is fixed height, never
  horizontal overflow at 375/768/1024/1440px.
- Rail changed: utilities (My List, Continue, Latest, Genres, Settings) stay
  full-height; departments (Home, Movies, Series, Tutorials, Collections) moved
  to a two-row header on phone (no bottom tab bar).
- Series seasons: `<select>` replaces season poster wall; season URLs
  (`#/series/<show>/<division.title>`) unchanged.
- Film button keeps "Resume from 2:20"; series button reads Resume / Continue /
  Play SxEy.
- Settings (Appearance/Profile/admin tabs) merged with legacy settings-menu
  (Telegram/cache tabs admin-only; conflict resolved as single page).
- Versions bumped: `Cargo.toml` (workspace root), `web/package.json`, `android/app/build.gradle.kts`.

**Fixed**

- High: kids profile no longer sees credits for adult-film leads in people
  search; people search returns only titles visible to the profile, counted
  server-side; person page empty state shown instead of name/portrait when no
  visible films.
- High: franchise link renders only when ≥1 film of that franchise is in the
  viewable library (not dead-end on single-film franchises or kids-filtered
  franchises).
- High: title-page tabs now keep selection and focus across redraws triggered by
  watch-state changes (My List toggle, pin, SSE position update).
- Medium: `credits::upsert` now transactional (DELETE + INSERTs in one
  `unchecked_transaction`, fixing potential partial-credits on interrupted
  metadata run).
- Medium: async pages (person, franchise detail) cache by id and render
  synchronously when cached, eliminating blank-page and scroll-reset on redraw.

**Owed to Android**

Department pages, feature pages with Cast, person pages, franchise pages, and
search grouping. Plan: `plans/260926-1330-android-editorial-departments-parity/`.
Android must accept v9 before any push. Existing Android builds refuse v9
packages until updated to ≥0.62.0.

**Older versions 0.61–0.58:** merged into `main` on 2026-09-26. See
[`project-changelog-2026-09-24-to-26.md`](project-changelog-2026-09-24-to-26.md).

## 0.57.0

**Added**

- The Android app gets the web player's magazine home page (Surface Parity):
  - a cover story on TMDB backdrops, with a pause control;
  - Editor's choice, Trending on TMDB and Staff pick features;
  - a Continue Watching strip of landscape cards with progress bars
    (Continue and Next up merged, as on the web);
  - a pull-quote tagline and a Recently Added row.

  Title pages open on a backdrop band, with "Make editor's choice" (hidden on
  kids profiles). The picks are a Kotlin port of `editorial-picks.js`, pinned to
  the web by shared JSON fixtures with exact seeded outputs
  (`web/test/fixtures/editorial-picks/`). Verified on a tablet: 855 backdrops
  fetched on the device.
- The phone fetches backdrops too: w780 on phones, w1280 on tablets
  (`resolve_backdrops` now takes a width). `SetSummary` carries `backdrop_key`,
  `tagline`, `rating` and `popularity`.
- The editor's choice syncs through the phone's watch state as well
  (`editorsChoice` in the core's sync record, same one-pick rule as the web).
  Shared watch-state fixtures cover it.

**Known difference:** a pinned episode's feature card opens its title page on
Android, where the web opens the show. This is written down in the code.

## 0.56.0

**Added**

- `push-index` and every command that publishes (`add`, `add-show`, `add-course`,
  `resume`, `finish-set`) first read the channel's newest index. They refuse
  when it holds sets this index lacks, since pushing would remove them from
  every player and phone. `push-index --force` replaces it anyway;
  `push-index --check` runs only the check and sends nothing. On 2026-09-25 the
  check reported 265 sets this machine lacks.
- `mediagram pull-index [--dry-run]` merges the channel's newest index into
  this machine's, so either uploading machine can publish the whole library.
  The rules:
  - It adds channel-only complete sets, but only those whose part messages
    still exist in the channel (a removed set is not brought back).
  - It re-reads sets that differ between the machines from their Telegram
    captions, in part order.
  - It adds missing shows and fills empty show fields; text is filled only
    from a row in the same language.
  - It never touches machine-local `meta`.
  - It is one transaction, preceded by a backup that is never overwritten:
    `library.before-channel-merge-<time>-<pid>.db`.

  `push-index --merge` pulls first, then pushes. Its guard then allows the
  sets the merge proved removed. The guard now counts only complete sets, as
  another machine's unfinished uploads are no titles.
- `mediagram metadata --refresh-older-than <DAYS>` asks TMDB again for cached
  answers older than that, so popularity (Trending), ratings and taglines stop
  being frozen at first lookup. A refresh that cannot reach TMDB keeps the old
  answer. It needs a TMDB key; without the flag the cache keeps everything, as
  before.
- `bun run preview` in `web/` (`scripts/preview.ts`): the real player pages over
  copies of this machine's channel snapshot and watch state, with real
  artwork and no Telegram, for UI work without touching the running player.
- A line-limit check for `web/` (`test/code-standards.test.ts`): new files stay
  under 200 lines, and the 28 files already over it are capped at their
  current size, so they can shrink but not grow.
- `bun run typecheck` (`tsc --noEmit`) in `web/`; TypeScript is a dev
  dependency. The one standing error (a JSON fixture typed as plain strings) is
  fixed.

**Changed**

- Package readers accept any index schema at or above the oldest they read,
  not only those listed. Schema changes only add optional columns; a breaking
  change moves `format`, which stays exact (`docs/mlib-package-v1.md` §7).
  Installed players and phones no longer stop updating at each bump.
- The home pull-quote prefers taglines of 90 characters or fewer.

**Fixed**

- Redrawing the page already showing no longer flickers. That happens when
  another device's watch state arrives, a pin changes, or the catalog
  refreshes. `lib/redraw.js` hands each loaded image to the new node showing
  the same picture, and marks the page `settled` so entrance animations don't
  replay; a turn of the cover still fades. Measured on the preview: 21 of 21
  images kept, no new poster requests.
- The phone's description store no longer records a lower schema when an
  older app opens a file a newer one wrote. After a downgrade and upgrade, the
  newer app used to replay a migration and fail on every open.

**Changed** (docs)

- The web player's architecture moved to `docs/web-player.md`;
  `system-architecture.md` §7 points there and is 528 lines. Changelog entries
  from 2026-09-14 to 2026-09-23 moved to two archive files. Every doc is now
  within the 800-line budget.

**Removed**

- A React Doctor CI workflow and `doctor` script under `web/` (not a React app;
  `web/.github` is never read by GitHub). `web/.claude/` and `skills-lock.json`
  are ignored.

## 0.55.6

**Changed**

- The artwork path `/api/posters/<key>.jpg` is built in one place,
  `artworkUrl` in `lib/catalog/plate.js`. Seven hand-written copies across the
  plate, film page, series header, title band, Featured reel and home modules
  now call it. Module-internal constants and helpers in `editorial-picks.js` and
  `home-features.js` are no longer exported, and the dead `.page-title` selector
  is gone. No visible change.

## 0.55.5

**Fixed**

- Images flickered just after opening the player at its bare address. Startup
  assigned `location.hash = "#/home"`, which fired `hashchange`, so the page was
  built twice about 100 ms apart. The second build replaced every image inside
  a view-transition cross-fade. The address is now set with
  `history.replaceState`, so the page is drawn once and the bare address leaves
  no history entry.

## 0.55.4

**Changed**

- Cleanup of the home page code, with no visible change:
  - The poster rows are a `strip` option of the shelf grids, and Recently Added
    passes `captions: false` instead of hiding captions with CSS.
  - The override block at the end of `home.css` is merged into the rules it
    overrode.
  - The feature facts line that was built and then hidden is gone.
  - The shared `.sr-only`, `initialOf`, and `--progress`, `--tint-active` and
    `--icon-search` tokens replace inline copies.
  - The poster rows hold 8 cards; wide cards and lists keep 6.

## 0.55.3

**Changed**

- The home cover story grows again: 650px at a 1024px-tall window
  (`--cover-height` clamp(620px, 63.5vh, 705px)).

## 0.55.2

**Changed**

- The home page's cover story is 20% taller (`--cover-height` clamp(528px, 54vh,
  600px), 84vh on a phone); the rail beside it follows.

## 0.55.1

**Changed**

- The web home page follows the magazine reference more closely. The cover is
  one band (about 45% of the viewport) beside a rail that runs only as deep as
  the cover; everything below takes the full width. The rail carries line
  icons and Home. Search is a magnifier that opens into a field, and the
  profile is an initial in a circle. Continue Watching cards carry their title,
  episode and progress bar over the picture. Recently Added is one row of eight
  posters. Section headings are sans; feature and cover standfirsts are upright
  serif. The cover's eyebrow reads "Featured today", which is true: its films
  are chosen by the day.

## 0.55.0

**Added**

- The web player redesigned as a digital entertainment magazine, dark-first
  with a light "paper" variant. Home is a front section: a rotating cover
  story on the film's TMDB backdrop (its title, tagline, year, rating), three
  single-title features (Editor's choice, Trending on TMDB, Staff pick),
  Continue watching beside a pull-quote of a real tagline, then Recently added
  beside a numbered "This month". A library rail holds the reader's own
  shelves; a sticky department bar holds Home, Movies, Series and Tutorials,
  dark glass over the cover that settles as it scrolls away. Film and series
  pages open on a backdrop band with a serif title, italic tagline and a
  drop-capped overview. Every route, element id and label the page code
  reads is unchanged. Newsreader Italic is newly self-hosted.
- "Make editor's choice" on film and series pages: one household pick that
  leads the home features, synced between devices like the Kids mark
  (`/api/editors-choice`, watch-state schema v8). Kids profiles are not
  offered it: the pick is the household's. Unpinning means no pick, even after
  a merge brought another device's. Android does not have it yet; see the
  plan's parity note.
- Backdrops: `mediagram posters` now also fetches each film's and series'
  wide TMDB artwork at w1280, stored beside its poster as `<key>-bg.jpg`
  (`tmdb-movie-550-bg`), from the details payload already cached, so it makes
  no new metadata requests. Catalog rows carry `backdrop`. The export package
  and the phone's on-device fetch leave backdrops out, by using the
  posters-only `resolve_posters`: the package has a 64 MB cap, and the phone
  has no hero to show one in yet. They serve the web player's magazine home
  (`plans/260925-2014-web-player-magazine-redesign`).
- Index schema **v8**: `shows.popularity`, TMDB's popularity as of the cached
  payload, which ranks the "Trending on TMDB" pick. It is optional to
  every reader, as `certification` is. `mediagram metadata` backfills it from
  the cache. Web catalog rows also carry `tagline`, `rating` and `popularity`.

**Fixed**

- The home page's empty-library check read `length` off the row totals, an
  object, so a library with nothing in it never showed its empty state.
- Package readers accepted only the oldest and the newest index schema, not
  those between. `SUPPORTED_SCHEMA` (Rust) and the web's `supportedSchema`
  were `[oldest, current]` checked by membership, so the v8 bump would have
  refused every v7 package. Both now use the full range,
  `mlib_spec::schema::READABLE_SCHEMAS` and `READABLE_SCHEMAS` in
  `web/src/catalog.ts`, and a test holds each to it.
- The player's startup poster count no longer counts backdrops, which sit in
  the same directory.

## 0.54.0

**Added**

- The Android web-parity work, merged: search and genre pages, audio and
  subtitle choice, the player's settings sheet, up next and queues,
  fullscreen gestures, picture-in-picture and a media session, series
  preload with offline badges, notes, profile removal, List/Grid shelves and
  "Mark finished" on the phone. Detail is in the dated entries for
  2026-09-24 and 2026-09-25 below. A state file an earlier pre-release build
  left at version 3 without the `kids` column gains it on open.
- "Mark finished" on the web player's Continue shelf. Each title there has a
  quiet word beside it that does what reaching the credits does: the resume
  position goes and the title counts as watched, synced like any other watch
  state. For a film finished on another device, or one given up on, that
  would otherwise sit on the shelf until played to the end. The player's own
  end-of-title path now calls the same function. The phone's Continue wall
  has it too, under each title (see the Android web-parity entries below).

**Changed**

- Redesigned the web library with a desktop sidebar, a separate search/profile
  toolbar, compact Continue and Next up cards, wider poster shelves, and
  responsive phone layouts. Interface text uses self-hosted Geist; the
  Mediagram wordmark is preserved. Light/dark appearance follows the device.
  Search now has a persistent label and keyboard users can skip to the library.
  Film actions appear before long descriptions. Routes, library data, saved
  List/Grid choices, and playback behavior remain the same.

**Removed**

- The Kids shelf, on the web and the phone. A kids profile shows the same
  titles, so the shelf only repeated it. The player's "Kids" mark stays on
  grown-up profiles, for letting an unrated title through.

**Fixed**

- A film or show page with no poster no longer shows a navy gradient block on
  the paper; it takes the flat sunk paper every other missing poster uses. The
  title page's Play button and the profile picker's Create button now turn
  paper-coloured on hover instead of pure white. All three were colours from
  outside the player's palette.

## 0.43.0

**Added**

- Kids profiles. Tick "Kids profile" when creating a profile, on the web or
  the phone, and that profile sees only titles rated FSK 12 or under plus
  unrated titles marked for Kids by hand — on every shelf, in search, in
  Featured and in Play next. The flag syncs between devices and cannot be
  switched off by a sync. It is a filter, not a lock. The web header's
  profile name now opens "Who's watching?" to switch profile without a reload.
  On a kids profile, the player (web and phone) doesn't offer the "Kids"
  mark, so a child can't approve titles for themselves.

- A Featured reel on the web player's Movies shelf. The Featured button opens
  a dark, full-window run of up to twelve films this profile has not watched,
  shuffled: each poster drifts slowly over a blurred copy of itself, fades into
  the next after seven seconds, and carries its title, year, genres, score and
  tagline. Play and Details act on the film shown; arrows, the dots, Space
  (pause), Esc and the back button steer it. Reduced motion gets still posters.
  Android gained its own reel once the phone fetched posters.

- The web player's Movies shelf is paged, 48 films at a time, with a row of
  page links under the grid. The page is in the address (`#/movies/page/3`),
  so back, reload and shared links return to it; `#/movies` is still page one.
  The Android Movies shelf pages the same way.

**Fixed**

- Catalog updates validate downloaded libraries before publication and reject
  stale concurrent completions. Failed state uploads retain successfully imported
  changes and leave retries possible.
- Upload resumption continues past unavailable source files, and repeated series
  imports direct pending episodes to `resume` instead of creating duplicate work.
- Browser playback and library navigation discard obsolete asynchronous replies,
  release cancelled playback resources, and follow the displayed episode order.
- Web storage and filesystem failures retain their causes; HLS session deletion
  follows the same browser-origin checks as other writes. Login diagnostics no
  longer redirect unrelated output during authentication.
- Android sync, refresh, login and playback operations now respect their owning
  lifetimes and report persistence failures without discarding retry state.
- Browser profile and list controls report failed saves, and shelf choices remain
  usable when browser storage is blocked. Switching audio no longer treats a
  partial conversion's duration as a completed title.
- Delayed transcode cleanup preserves replacement sessions. Audio probes are
  cancelled and reaped before the web server finishes shutting down.
- Upload surveys report files whose compatibility could not be checked; cancelled
  or failed CLI conversions stop their child processes.
- Web setup hides password input on Bun terminals while restoring normal echo
  for subsequent prompts. Failed profile discovery offers a retry, and the
  subtitle shortcut restores the selected language after toggling it off.
- Failed browser profile-state reads now offer retry before showing shelves and
  preserve the previously loaded profile. Disk measurements retain the last
  successful total when a scan fails, and sync counts newly imported profiles.
- Android account resets close local state before deleting it, and queued work
  from the old core cannot recreate the database. Profile choices reject stale
  completions; System diagnostics keep previous readings and offer retry.
- Malformed MP4 box sizes produce an error without overflowing the parser.
  Sign-out holds the session lock through stored-key removal, and private core
  diagnostics retain their nested causes while public errors stay sanitized.
- Web shutdown drains speculative cache reads before disconnecting Telegram.
  Failed metadata reads preserve stored identity and migration state, and a
  rejected browser Play request offers retry without disturbing newer playback.
- Android application changes restore watch-state ownership before reporting
  success, with a separate retry when reconciliation fails. Cache settings show
  recoverable failures even before the first reading; login, catalog and playback
  failures use controlled text while retaining their diagnostic causes.
  Initial provisioning refuses to overwrite an identity that is already installed.
- Session revocation and update listeners are bound to the connection that
  created them, so cleanup of a replaced login no longer disturbs its successor.
  Abandoned downloads stop polling Telegram once their reader closes.
- The channel index rejects provider identifiers outside SQLite bounds and keeps
  metadata unchanged on rejected writes; schema read and migration failures are
  reported instead of hidden. Upload plans with oversized part counts are refused
  before allocation, and mixed-case IMDb prefixes are normalized.
- CLI commands settle their work before disconnecting Telegram, and course
  imports report unreadable metadata sidecars instead of treating them as absent.
- Web thumbnail requests read only from disk and never fall back to Telegram.
  Cache inventory errors other than a missing file are surfaced, and idle
  transcode cleanup rechecks each session before stopping it, so a reused one
  survives. Concurrent audio probes for one title are coalesced, and cancelled
  Telegram reads finish before their stream closes.
- Library update hints keep arriving after a callback throws a value that cannot
  be printed.
## 2026-09-25

**Fixed**

- Android player: an up-next switch (autoplay or "Play now") now moves
  `LibraryPositions` before it reopens anything, through a new
  `LibraryPositions.replacePlayer(id, run)` and `UpNextController`'s own
  `pendingSwitch`, rather than calling back into the ViewModel directly.
  Previously a rotation or process restore right after the switch reopened
  the episode that had just finished, since the saved frame still named it.
  The autoplay gate also now treats a stopped loader with anything at all
  buffered as ready (`PlayerHandle.isLoading()`), on top of the ported 60s
  threshold: media3's default load control caps how far it will ever buffer
  ahead well under that figure, so a high-bitrate file previously waited
  out the full 45s patience ceiling every time. The gate is cancelled the
  moment playback starts by hand, matching the web's own
  `stopWaitingToStart`, so a poll landing after a viewer paused again can no
  longer call `play()` over it; the screen also now stays on through the
  countdown and the gate wait, both of which used to read as "not playing".
  Kids "Marked by hand" tiles play into that marked-by-hand run itself, the
  way `app.js`'s own `play(set, byHand)` does, in place of opening the
  tile's own title or show — its "Play all" button is removed to match, a
  deliberate difference recorded in phase 07's own notes since the web has
  none there either. The run a title opened with is now recomputed once the
  catalog finishes loading after it (a cold resume or process death could
  otherwise leave a title with no next at all), a seek while paused updates
  the up-next card the same way the web's `timeupdate` does, and a save
  landing against a title switched to less than a second in is now dropped
  rather than putting an unwatched episode onto Continue at 0s. The up-next
  card is measured clear of the transport bar and the system's own bottom
  inset the same way `SubtitleLayer` clears the picture, rather than a fixed
  padding that clipped under three-button navigation.
- Android player: after the picture-in-picture window's own ✕ pauses and
  drops the media session (`pauseForPipDismissal`, a user decision — the
  title stays open), pressing play again on that same still-open title
  never runs through `open()`, so nothing had restarted the session,
  foreground state or notification — a viewer could keep listening with
  the screen off and no lock-screen controls, unprotected from the process
  being killed for having no foreground service. `PlayerViewModel.onPlayingChanged`
  now restarts `PlaybackServiceController` on every transition into
  playing, matching a real player's own `onIsPlayingChanged(true)` —
  idempotent, since starting an already-running service is a no-op, so
  the ordinary case (opening a title) is unaffected.

**Added**

- Android profile removal, List/Grid shelves and "Mark finished", closing
  the web-parity plan. "Who's watching?" gains "Remove a profile…": tap a
  name, then the web's own confirmation; the profile and everything it
  watched go, and one another device still names comes back with the next
  sync, as on the web. Films and Series gain the web's List/Grid toggle,
  remembered per device (posters stay the phone's default); a shelf of
  courses is always a list, as on the web. The film shelf now carries the
  "offline" badge too. Continue gains "Mark finished" under each title,
  through the same `markFinished` the player's end-of-title path now calls.
  The deliberate differences left after parity are listed in the Android
  system-menu spec, §9.
- Android notes panel, matching the web player's: a title with a summary
  gets a "Notes" button in the player's top bar, and a lesson's notes open by
  themselves as the web's do. The markdown is parsed by a Kotlin port of
  `markdown.js` in `:core:model` (`model.markdown`), held to the web's by a
  shared fixture, `web/test/fixtures/markdown/cases.json`, that both
  `bun test` and `MarkdownFixtureTest` run; the fixture pins today's quirks
  too, such as emphasis not nesting inside emphasis. Links keep the web's
  scheme allow-list; of the allowed ones only `http`, `https` and `mailto`
  open (in another app), since `#` and `/` point into the web player's own
  page. The column sits beside the picture on a landscape window and below
  it in portrait; on a phone held sideways it is a sheet over the right of
  the picture instead, a deliberate difference since the web never runs in
  a window that short. A panel belongs to its title and resets on an
  up-next switch. `scripts/check.sh` now runs `:core:model:test`, which
  `testDebugUnitTest` never reached.
- Player framing — Fit, Fill, 16:9 and 4:3 — on both surfaces, matching
  what `framing.js`'s own comments always intended: 16:9/4:3 crop the
  picture into a centred window of that shape, never stretch it to fill
  one. The web's first cut didn't: `video { width: 100%; height: 100% }`
  in `style.css` makes CSS ignore `aspect-ratio` entirely, so "16:9"/"4:3"
  rendered identically to "fill" (a bug, not a decision) until
  `framing.js`'s new `framingBox` sized the video element itself to a
  `fitWithin`-computed window and `transport.js` applied it, kept in sync
  across a resize. Android's `core:playback` `Framing.kt` (`frame`, ported
  from the same `framing.js`) does the equivalent: a named ratio's window
  fits within the screen and the picture covers *that*, never the screen
  directly — `Video()`'s overlay slot (subtitles, and the up-next card
  while the transport bar is hidden) sizes to the window rather than to
  the picture's own box, which may be bigger. Fit and Fill are unchanged
  on both surfaces. On Android, a settings-sheet section and per-show
  memory alongside speed and subtitle style; a pinch over the picture sets
  Fill or Fit directly, the Android idiom standing in for the web's `z`
  key, which this app has no keyboard for.
- The player is now immersive: the system bars hide while it is on screen
  (`ImmersiveEffect`, `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`) and re-hide on
  resume and on this window regaining focus — covering both a return from
  the background and the settings sheet (its own dialog window) closing —
  restored only when the player itself is left. There is no fullscreen
  button, unlike the web — a phone's player is already fullscreen the
  moment it opens.
- A double tap over the picture seeks — left third back, right third
  forward, by whatever the transport buttons' own increment already is —
  and the middle toggles play/pause through the same buffering-aware
  `Util.handlePlayPauseButtonAction` the transport button uses, so it works
  mid-rebuffer too. A brief "-10 s"/"+10 s" label accumulates across
  repeated double-taps on the same side. A single tap still toggles the
  transport bar, delayed by the double-tap timeout; the gestures live in a
  new `PlayerGestureLayer` over the whole screen rather than carved to the
  video box, since the transport bar and sheet already consume their own
  taps first. A pinch consumes its own pointers so it never also reads as
  a tap, a double-tap, or a seek, and never fires from a finger already
  claimed by the scrubber. `Video()` measures its own size with
  `BoxWithConstraints` rather than after the fact, so neither the first
  frame nor the first frame after a rotation ever draws full-bleed before
  framing applies. The up-next card, previously clamped only to the
  transport bar, now clamps to the visible picture itself while the bar is
  hidden — otherwise, under a letterboxing framing, its scrim and buttons
  hung in the black band below the picture.
- Picture-in-picture and lock-screen controls. A button beside the back
  arrow and, on API 31+, `setAutoEnterEnabled` shrink the player into a
  floating window on the home gesture, while actually playing or
  buffering with the intent to (`playWhenReady`) — below 31, the same
  gesture is caught by hand in `MainActivity.onUserLeaveHint` through a
  small `PipEntryPoint` slot, since `ui-mobile` cannot import
  `MainActivity` the other way around. Neither runs on a device lacking
  `FEATURE_PICTURE_IN_PICTURE` (Android Go, some OEM builds), which
  `packageManager.hasSystemFeature` now gates before any of it. The
  window opens at the video's own aspect, clamped to what
  `PictureInPictureParams` accepts (1:2.39..2.39:1, built as exact
  fractions rather than a decimal that rounded past the true minimum),
  always `Fit` regardless of the show's own remembered framing (a
  remembered 4:3/16:9 crop otherwise letterboxed a second time inside a
  window already shaped to the video), and carries three `RemoteAction`s
  — skip back, play/pause, skip forward — answered by a
  `BroadcastReceiver` acting on the same `Player` the transport bar's own
  buttons use; leaving the player screen disarms auto-enter on the
  activity again, so the catalog itself never shrinks into a leftover
  window. Every other overlay (transport bar, marks, settings sheet,
  up-next card) is hidden while in the window; the up-next countdown keeps
  running regardless, since it lives in the ViewModel, not in the hidden
  composable. **Closing the window pauses and saves, keeping the title
  open** (a user decision) — reopening the app finds it exactly where it
  was, not back at the catalog — told apart from expanding back to full
  screen by whether the activity's own lifecycle has already dropped to
  `CREATED` by the time the system reports leaving picture-in-picture.
  `MainActivity` now declares `android:configChanges` for screen size and
  orientation, so neither a rotation nor a picture-in-picture resize
  recreates the activity any more — the existing stop-on-dispose path is
  now also guarded by `isInPictureInPictureMode`, in case an OEM still
  tears the activity down mid-window. A new `:feature:player`
  `MediaSessionService` (`PlaybackService`) wraps the app's singleton
  player in a `MediaSession`, added to the service (not merely built —
  the session notification, foreground state and lock-screen controls all
  depend on that) with a session activity so tapping the notification
  reopens the app; started and stopped alongside `PlayerViewModel.open`/
  `stop`. No notification-permission prompt: media-session notifications
  are exempt from `POST_NOTIFICATIONS` on API 33+ by Android's own
  documented behavior, so there was nothing to ask for. Playback still
  pauses only on actually leaving the player screen, never on the screen
  locking or on entering picture-in-picture, per the user decision the
  plan records. `MediaMetadata` (title line, poster) rides on the current
  `MediaItem`, updated through `Player.replaceMediaItem` and remembered
  so a cold start or a `retry()` reload reapplies it rather than losing it
  silently, so the lock screen and notification never show a stale or
  blank title. The button, its auto-enter and the lack of a web-side
  equivalent (the web has only the `p` key) are recorded as deliberate,
  phone-only touch equivalents, not owed to the web.
- Android series preload and offline badges, matching the web's own
  `series-preload.ts`/`held.ts`: opening an episode asks a new
  `:core:playback` `SeriesPreloader` for the next two episodes of the
  run `UpNextController` already resolves — the same `nextInQueue`, so
  nothing computes "next" a second way. One worker, one download at a
  time, over a media3 `CacheWriter` on the same `cacheDataSourceFactory`
  playback itself reads through (`CacheDataSourceWriter`), on its own
  dedicated thread and its own `PlaybackCounters` so a quiet preload never
  shows up as the playing title's own reads. A candidate is skipped when
  Wi-Fi is not the active network (`ConnectivityManager.isActiveNetworkMetered`)
  or when holding it would push held-plus-current-plus-candidate past 75%
  of the cache budget (`fitsInPreloadBudget`) — phone-only limits the web
  never needed, recorded as deliberate differences. `HeldSets` answers
  whether a set is fully on disk from the cache alone
  (`Cache.isCached(key, 0, totalBytes)`, no Telegram asked); `CatalogViewModel`
  scans it once when the shelves are built and folds in every
  `SeriesPreloader.heldEvents` id after, without a full rescan. An
  `offline` badge (`OfflineBadge.kt`, the web's own wording) now shows on
  Continue, Next up, Watchlist and Kids cards, on search rows, on
  Collections-tab list rows, and on the episode and lesson rows of a season
  or course (`course-view.js`'s `lessonRow`) — the place a viewer looks for
  what the preload took; the player's stats overlay says `cached`
  in place of an ahead-seconds reading once the open title is held,
  matching `preload-readout.js`.

## Earlier

- 0.58.0 to 0.61.0: [`project-changelog-2026-09-24-to-26.md`](project-changelog-2026-09-24-to-26.md)
- 2026-09-24: [`project-changelog-2026-09-24.md`](project-changelog-2026-09-24.md)
- 2026-09-19 to 2026-09-23: [`project-changelog-2026-09-19-to-23.md`](project-changelog-2026-09-19-to-23.md)
- 2026-09-14 to 2026-09-18: [`project-changelog-2026-09-14-to-18.md`](project-changelog-2026-09-14-to-18.md)
