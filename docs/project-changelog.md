# Project changelog

Dated entries summarizing what shipped, grouped by day. Commit hashes refer
to `main`. Full phase-by-phase detail lives in
`plans/260914-1954-telegram-linux-uploader-mlib-spec-v2/plan.md`'s
"Implementation log" sections.

## Unreleased — 0.57.0

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
  Android has no equivalent: its catalog carries no posters.

- The web player's Movies shelf is paged, 48 films at a time, with a row of
  page links under the grid. The page is in the address (`#/movies/page/3`),
  so back, reload and shared links return to it; `#/movies` is still page one.
  The Android catalog does not page yet.

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

## 2026-09-24

**Added**

- Android player: a settings sheet (gear button in the transport bar) with
  a Speed section — the six steps `web/public/lib/transport.js` offers,
  remembered per show through `state.db`'s `preferences` table and
  reapplied on every open, never inherited from whichever title played
  last on the app's one singleton player. A title line at the top (show ·
  episode · title, the web's `titleLine` order) and an `ends HH:MM` label
  beside the transport bar's own clock, both dividing by the chosen speed.
  A failed set now shows a Retry button that re-opens at the last saved
  position — a phone-only touch equivalent of the web's seek-to-retry,
  recorded as deliberate rather than web debt. `episodeLabel` moved from
  `feature:catalog` to `core:model` so the player can use the same port
  without one feature module reaching into another.
- The catalog core now carries what the web player's `forBrowser` already
  attached to every row: `genres`, `subtitles` (languages, sorted), and
  `hasSummary`, joined into `list_sets` by poster key and by set id. Genres
  read the index's `shows.genres` first, then this device's own fetched
  sidecar for a title the index says nothing about — a deliberate difference
  from the web, which has no such sidecar and reads the index alone.
- `Core::set_text(setId, kind, lang)` reads a stored summary or subtitle
  track straight off the index, the same query the web player's
  `web/src/assets.ts` runs, ready for the subtitle and notes panels.
- `state.db` gains a `preferences` table, ported verbatim from the web's
  schema v5: what a viewer chose per show (audio language, subtitle
  language, playback speed), capped at 200 characters per string, unsynced
  and per profile.
- Profiles can now be deleted (`Core::delete_profile`), the same "remove
  only" the web offers — a device still holding the profile brings it back
  on its next sync round. The chosen-profile pointer clears itself on the
  next read if it named the profile removed.
- Android's `MediaSet` carries the same three new fields (`genres`,
  `subtitleLanguages`, `hasSummary`); no UI reads them yet.
- `Core::search(query)` ports the web player's search — `search/normalize.rs`,
  `search/rank.rs`, `search/excerpt.rs` — so the phone ranks a query against
  the index the same way the server does, summaries included, rather than a
  Kotlin filter over the in-memory set list that has no summary to search.
  A tie within a field is broken by `icu_collator`, ICU4X's pure-Rust German
  collator, so the order agrees with the web's `localeCompare("de")` rather
  than approximating it — bundled at compile time (`compiled_data`), no
  runtime data file. Folding the catalog costs enough (tens of milliseconds
  over the real library) that it is cached in `Core`, keyed by the catalog's
  resolved version directory, and only redone after a refresh actually
  changes it. `web/test/fixtures/search/cases.json` runs against both the
  web's `SearchIndex` and this port, so a case that would only pass one of
  them fails the build. Kotlin gets `CoreClient.search`; no screen reads it
  yet.
- Android search screen: a search icon in the library's top bar opens a
  field, debounced 200 ms like the web's own box, ranking through
  `Core::search` and joining each hit back onto its `MediaSet` for the row's
  facts — a flat list, no shelves, matching `search-view.js`. A film or
  show's genres are now links (`GenreLinks`) rather than a plain provider
  sentence, opening a genre page (films then series, headed only when both
  are there) ported from the web's `genreShelf`. `LibraryFlow.kt`'s
  navigation `when` moved to `LibraryFlowBranches.kt` to make room.
- The settings sheet's Audio section: rows built and labelled the way
  `web/public/lib/audio-chooser.js` builds them (language · title ·
  channels · codec, `Track N` for one the file names nothing), remembered
  per show the same way speed is, under the same `preferences` scope.
  Codec names match ffprobe's own (`ac3`, `eac3`, `dts`, `truehd`, …, the
  same names the web's probe already returns), and language names are
  always English, the same fixed locale the web and this app's stats
  overlay already use — never the device's own, which the phase this
  built from had said, wrongly against that same web reference. The
  language is matched, never the ordinal — a re-rip can reorder a file's
  streams — and a pick with no usable language (blank, or `und`) is
  honoured for the session but never saved over a real choice already on
  record, the same restraint `player.js`'s own change handler has. A track
  this build has no decoder for (DTS, TrueHD — there is no FFmpeg
  extension here) is left off the menu entirely and never pinned, even if
  it is what a viewer once chose; with nothing remembered, the row already
  marked is whichever one ExoPlayer's own selector picked, never a guess
  invented here. Only shown for more than one (decodable) track. The sheet
  itself now scrolls and pads for the navigation bar, so Audio's rows are
  reachable under three-button navigation in either orientation.
  Mechanically different from the web, deliberately: the web can only
  switch by asking the server to re-encode into a new response; ExoPlayer
  switches inside the decoder with a `TrackSelectionOverride`, no re-fetch,
  same decision either way. `AudioChoiceController` attaches its own
  listener straight to the app's singleton player rather than through
  `DefaultPlayerHandle`'s permanent one, and undoes that when its own
  `PlayerViewModel` is cleared — that permanent listener has exactly one
  caller today (playback state), and stays that way. Guards against a
  synthetic empty tracks report `ExoPlayerImpl` fires while reloading for
  a new title, which had been read as "this file has no audio" and
  silently dropped every real track that followed.
- The settings sheet's Subtitles and Subtitle style sections. Off is a
  remembered choice like any other (`transport.js`'s own rule: nothing
  remembered or a language the file has lost defaults to the first track;
  a remembered "off" stays off); size (Small/Normal/Large/Larger), backing
  (Shadow/Box/None) and a `+/-30s` timing offset (`0.1s` steps, a Reset
  button) are ported from `subtitle-panel.js`/`subtitle-style.js`, all
  remembered per show under the same `preferences` scope speed and audio
  already use. ExoPlayer never renders a subtitle: its text renderer is
  disabled outright (`PlayerFactory.buildPlayer`, once, not per open), and
  a title's VTT (already in the index via `Core::set_text`, phase 01) is
  parsed with media3's own `WebvttParser` — confirmed present in this app's
  1.10.1 media3-extractor dependency — into plain cues an app-owned
  `SubtitleLayer` draws inside the video's own rectangle, bottom-centred,
  lifted by exactly as much of the picture as the transport bar covers
  while it is shown (measured, not a fixed clearance: a guessed 96dp left
  cues on top of the bar on the tablet). An offset is applied fresh
  from each cue's parsed times on every read rather than accumulated onto
  a mutable cue (there is nothing here for a second nudge to drift away
  from, unlike the web's own `TextTrackCue` mutation). No position control,
  the same restraint and for the same reason `subtitle-style.js` records.
  `SubtitleChoiceController`'s language pick alone races two sources (a
  title's own languages, known as soon as the set resolves, and this
  profile's remembered choice, known once the preference round trip that
  follows it lands) and chooses nothing until both have answered: a
  default applied from the languages alone fetched a file the remembered
  choice then turned off, and could flash its cues on a show the viewer
  had switched off. A language picked by hand while that round trip is
  still in flight wins over whatever it answers late. Size and backing are
  remembered one value at a time, as the web does, so a size picked early
  never writes the default backing over a remembered one.
- Android player: up next and queues, ported from `up-next.js`/`autoplay.js`/
  `player.js`. In the last 30 seconds a card offers the next title with
  "Play now" and "Cancel"; once the title actually ends (never before — the
  bug the web fixed by separating the two) a ten-second countdown starts it
  unattended, waiting on the same buffer gate the web polls (a minute
  buffered ahead, or everything a short title has, or 45 seconds' patience,
  whichever comes first) so a metered link never starts into a stall. A
  standing "Play next" button in the transport bar stays even after a
  cancel, which is remembered per title for the app's own lifetime — the
  phone's equivalent of the web's in-memory `Set`, lost only on a process
  death the way a reload forgets it there. "Next" is a show's or course's
  own flattened order (`catalog.runFor`, crossing a season or folder
  boundary the same way `catalog.playOrder` already does) unless the title
  opened from a hand-built list or the Kids wall's "Marked by hand", which
  now also gets a "Play all" button next to Lists' own; both pass their run
  explicitly rather than have the player guess it. `PlayerHandle.open`
  gains a `playWhenReady` flag (default true) and a `play()`/
  `bufferedPositionMs()` pair for the gate to drive; `PlayerHandle.Listener`
  gains `onEnded()`.

## Earlier

- 2026-09-19 to 2026-09-23: [`project-changelog-2026-09-19-to-23.md`](project-changelog-2026-09-19-to-23.md)
- 2026-09-14 to 2026-09-18: [`project-changelog-2026-09-14-to-18.md`](project-changelog-2026-09-14-to-18.md)
