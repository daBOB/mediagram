# Phase 04 player base — review fixes

Findings tally as relayed by the coordinator: 0 Critical, 1 High, 2 Medium, 5
Low. Device check on the tablet passed before this pass: title line, ends-at
at 1x and 1.5x, the gear sheet, `key:tmdb-tv-48891/speed/1.5` remembered in
the same format as the web, the next episode of the same show opening at
1.5x, a film opening at 1x.

## H1 — rotation and Retry reset a playing film's speed, choices and title

`PlayerScreen.kt`'s `LaunchedEffect(setId)` re-runs `open()` on rotation with
the *same* id, and `open()` unconditionally reset `openScope`, `_openSet`,
`_choices` to their defaults and called `handle.setPlaybackSpeed(1f)` —
producing an audible drop to 1x mid-film, losing a speed chosen without a
profile or before the scope resolved, and a title/speed flicker. Retry did
the same.

**Fix.** `open()` now tells a rotation reopening the same title apart from a
genuinely new one (`sameTitle = session.openSetId == setId`) and only resets
the choices controller and relaunches its resolution for a new one — never
for the same one. The redundant `setPlaybackSpeed(1f)` is gone entirely:
`DefaultPlayerHandle.openOn` already floors a real reload to 1x, and asking
the handle to open a set that is already loaded and playing republishes
rather than reloading, so nothing here needs to repeat that floor. `retry()`
still resets nothing (same title), but the failed player *was* left in
`STATE_IDLE`, so its reopen is a genuine reload and does re-floor the rate —
`retry()` now reapplies `choices.value.speed` right after, since nothing else
would.

Split the speed/title/scope bookkeeping out of `PlayerViewModel` into a new
`PlayerChoicesController` (`reset()`, `resolve()`, `setSpeed()`) while doing
this, to keep both files under the line guideline.

One real regression surfaced fixing this: the first pass also skipped
resetting `_state` to `Preparing` for `sameTitle`, which broke
`PlayerReopenTest`'s buffering case (a set still buffering must stay
`Preparing` until the player's own ready event ends the wait) and left an
un-stopped ticker running past its test — draining an infinite `while(true)`
loop at `runTest`'s end is exactly what produced the `OutOfMemoryError` on an
unrelated adjacent test in the same JVM. `_state` is reset unconditionally
again, same as before; the handle's own synchronous republish corrects it
straight back for an actually-playing title before anything ever observes
the momentary `Preparing`, so there is no real flicker from this one.

**Tests.** `PlayerChoicesResetTest`: `reopeningTheSameTitleWithNoProfileChosenKeepsAHandPickedSpeed`
(no `setPlaybackSpeed` call at all across the reopen), `retryKeepsTheChosenSpeedAcrossTheReload`.

## M1 — the title line could repeat the show name or show a raw set id

`toMediaSet` set `title = summary.title ?: summary.show ?: summary.setId`,
and `titleLine` read `title` — an episode or film the index never named
would print its own show's name back, or a raw set id, instead of dropping
the segment the way the web's `player.js:601` does over a nullable title.

**Fix at the root.** `MediaSet` gained `rawTitle: String?` — the index's own
title, unfilled-in — kept beside `title` rather than replacing it, since
every other reader of `title` (shelf cards, search rows) still needs *some*
string to show and stays on the fallback chain unchanged. `titleLine` reads
`rawTitle`. `fakeMediaSet` mirrors `rawTitle` to `title` by default so
existing fixtures naming a real title need not repeat it; the two new test
cases override `rawTitle = null` explicitly rather than relying on any
default derived from `setId`.

**Tests.** `PlayerTitleLineTest`: `anUntitledEpisodeDropsTheTitleSegmentRatherThanRepeatingTheShow`,
`aTitleLessFilmDropsTheTitleSegmentRatherThanPrintingTheRawSetId`.

## M2 — every open walked the whole catalog on the main thread

`applyChoices` (now `PlayerChoicesController.resolve`) called
`catalogRepository.mediaSet` → the interface's default `sets().find { }` →
a full `listSets()` plus a synchronous `posterPath` disk check per set with a
poster key, all on `viewModelScope` (main) — delaying the remembered speed
until it finished, and, at catalog scale, plausibly the root of finding #6
below.

**Fix.** `DefaultCatalogRepository` overrides `mediaSet(setId)` to find the
one row on the raw `SetSummary` list *before* mapping any of them —
`toMediaSet`'s poster lookup now runs once, for the matched set, not once per
set in the library — off a new `dispatcher: CoroutineDispatcher = Dispatchers.IO`
parameter (wired from the app's existing IO dispatcher in `DataModule`).
`sets()` moved onto the same dispatcher for the same reason. Timed at debug
level (`Log.d("catalog", "mediaSet($setId): ${…}ms")`) so a slow lookup shows
up in logcat.

**Tests.** `CatalogRepositoryTest`: `mediaSetLooksUpOnlyTheMatchedSetsPoster`
(asserts `FakeCore.posterPathCalls` holds exactly the matched key, not one
per set), `mediaSetAnswersNothingForAnUnknownId`.

## L1 — `applyChoices` could swallow cancellation, and two calls were unguarded

`runCatching { catalogRepository.mediaSet(setId) }` caught
`CancellationException` along with everything else, and
`preferences.load`/`preferences.remember` had no guard at all — a core round
trip failing could throw uncaught out of `viewModelScope.launch`.

**Fix.** A `safely(default) { }` helper on `PlayerChoicesController` rethrows
`CancellationException` and answers a default for anything else, used around
`mediaSet`, `preferences.load`, and `preferences.remember`.

## L2 — a speed picked while the scope was still resolving could be overwritten

`applyChoices` ran a core round trip before finding out the scope, then
unconditionally wrote whatever it found back into `_choices` and the
handle — a speed picked by hand in that window would be silently reverted the
moment the resolution landed.

**Fix.** `PlayerChoicesController.setSpeed` sets `userChoseSpeed = true`;
`resolve()` checks it both before and after its own suspension points and, if
set, remembers the hand-picked speed under the newly-resolved scope instead
of overwriting it with whatever preferences held.

**Test.** `PlayerChoicesResetTest.aSpeedPickedWhileTheScopeIsStillResolvingWinsOverTheLateResult`,
using a new `CompletableDeferred` gate on `FakeCatalogRepository.mediaSet` to
open a real window where the resolution is genuinely still in flight.

## L3 — ends-at read media3's duration and the scrub thumb

`endsAtLabel` used `progress.durationMs` (blank until media3 has buffered
enough) and the scrub-drag position (moving while dragging, which
`player.js:327-332`'s `runtimeSeconds()` does not).

**Fix.** `PlayerControls` now resolves the runtime through
`ResumePoint.trustedRuntime(catalogued = catalogedDurationSecs, observed = …, direct = true)`
— the catalogue's runtime first, known the instant the title resolves,
falling back to media3's own once there is one — and counts from
`progress.currentPositionMs` rather than the scrub thumb. `PlayerScreen`
passes `openSet?.durationSecs` through as `catalogedDurationSecs`. Placement
(beside the transport bar's own clock, not beside Watchlist/Kids/Add-to-list
the way the web's rail groups it) stays the documented deliberate difference
already recorded in `PlayerTopBar.kt`.

## L4 — accepted as the phase's own risk

No change; the phase file already records this.

## L5 — a speed queued before the player is built

Reachable without a device: `DefaultPlayerHandleTest` gained
`aSpeedQueuedBeforeThePlayerIsReadyIsAppliedAfterTheQueuedOpensOwnFloor`,
asserting `player.prepare()` → `setPlaybackSpeed(1f)` (the queued open's own
floor) → `setPlaybackSpeed(1.5f)` (the queued speed, applied last) in that
order once the deferred player completes.

## Finding #6 — first tap on the search icon after the Home catalog appears did nothing

Not reproduced on a device this pass; the tablet check above happened before
this fix, so the coordinator's verification below is the confirmation.

**Likely cause.** `CatalogViewModel.state`'s flow builds the Ready shelves
by calling `CatalogRepository.sets()`, which (before this pass) mapped every
`SetSummary` through `toMediaSet` — including a synchronous `posterPath`
disk check per set with a poster key — entirely on `viewModelScope`, i.e.
main, with no `flowOn`/`withContext` of its own. For a library of any real
size this is a measurable main-thread stall landing at the exact moment the
catalog first turns Ready — the same moment a viewer's first tap on the
newly-drawn search icon is most likely to arrive. A touch event whose
gesture recognition has to complete while the main thread is busy doing
disk I/O is a well-known way to lose a tap; the second tap, after the stall
has passed, succeeds normally.

**Fix.** The same fix as M2: `DefaultCatalogRepository.sets()` now runs its
`listSets().map { toMediaSet(…) }` — poster lookups included — inside
`withContext(dispatcher)` (`Dispatchers.IO`), off main. This does not touch
`CatalogViewModel` itself; the stall it was exposed to no longer exists at
its source.

**Unresolved:** this is the most plausible mechanism found by reading the
code, not a reproduced-and-confirmed root cause — the coordinator asked to
verify on the tablet, which this report defers to.

## Not changed

- `docs/project-changelog.md`, `plan.md`, `phase-04-…md` — no new
  functional decision to record beyond what phase 04's own entry already
  says; the fixes above are corrections to that same entry's work, not
  new user-visible behaviour.
- Version stays `0.44.0` per the coordinator's instruction for this pass.
