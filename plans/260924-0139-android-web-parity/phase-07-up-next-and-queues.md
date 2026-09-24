# Phase 07: Up next and queues

## Context links
- `web/public/lib/up-next.js:17-43` (`WARN_SECONDS=30`, `COUNTDOWN_SECONDS=10`; `upNextPhase`: hidden / waiting / counting; counting only once ended; unknown runtime → nothing early)
- `web/public/lib/player.js:68,585-611,966-973,1054-1057` (cancel = in-memory `Set` of set ids, lost on reload; card "Play now" and rail `#play-next` both `playNext("asap")`; cancel never hides the rail button; tooltip = title line)
- `web/public/lib/autoplay.js` + `player.js:710-725` (unattended start: ≥ 60 s buffered, or all remaining buffered, or 45 s patience; poll 500 ms; "asap" = as soon as playable)
- `web/public/app.js:407-435` (queue given → `nextInQueue`; else `nextAfter(collection)` crossing seasons/folders)
- `web/public/app.js:552,578-595` (queues: Kids "Marked by hand" grid, list rows and "Play all")
- `web/public/lib/library.js:229-243`
- `android/feature/catalog/src/main/kotlin/NextUp.kt:115-118` (`nextAfter(order, setId)`, currently only used by `nextInCollection`), `:127` (`playOrder`)
- `web/test/up-next.test.ts`, `web/test/autoplay.test.ts` (cases to port)
- Android list play: `android/ui-mobile/src/main/kotlin/ListScreen.kt:41,62`, `LibraryFlow.kt:182,230`

## Overview
Priority P1 · Status pending.

## Key insights
- "Next" is plain `nextAfter` over the flattened collection — it does **not**
  skip watched episodes (that is `nextInCollection`, the Home "Next up" rule).
  Android has exactly this function unused; call it, do not write another.
- Web: "a show's episodes and a hand-built list are both a flat run by the time
  they get here; only the flattening differs" (`library.js:225-228`). Android
  does the same: `playOrder(divisions)` lives in `:feature:catalog` on its
  `Division` model, and `:feature:player` may not import it (feature ↛ feature),
  so **the UI flattens and the player only ever receives a run** (list of set ids).
- Films outside a queue have no next.
- Cancel is per title for the session only — Android keeps it in the VM/handle
  memory (app-scoped handle survives screen changes; process death forgets, as a reload does on web).
- Buffer gate matters more on a phone (metered link): port it unchanged.

## Requirements
- Card over the video in the last 30 s: "Up next" + title line + "Play now" +
  "Cancel"; after end: "Starting in N…" 10→0, then unattended start.
- Standing "Play next" button in the control bar whenever a next exists (even after cancel).
- Unattended start waits on the autoplay gate; "Play now" starts as soon as ready.
- Queue: `LibraryPositions` keeps `queue: List<String>?` beside `setId`; list
  rows play into the list, a "Play all" button on lists and on the Kids
  marked-by-hand wall starts at item 1.
- Progress of the ending title saved as finished before switching (existing save path).

## Architecture
`UpNext.kt` (pure port: `upNextPhase(hasNext, cancelled, ended, remainingSec)`),
`Autoplay.kt` (pure: `readyToStart(bufferedAheadSec, remainingSec, waitedMs)`),
`RunFor.kt` in `:feature:catalog` (pure: set + catalog state → run ids: the
set's collection via `playOrder`, or none for a film); list/kids screens pass
their own run. `PlayerViewModel.open(setId, run)` → `nextInQueue(run, setId)`.
VM ticks (existing ticker) → phase → `UpNextState` → `UpNextCard`. On switch:
`handle.open(next, playWhenReady = false)` → poll gate every 500 ms → play.
`PlayerHandle.open` gains a `playWhenReady` flag (default true, all callers unchanged).

## Related code files
Create:
- `android/feature/player/src/main/kotlin/UpNext.kt`, `Autoplay.kt`, `UpNextState.kt`
- `android/feature/catalog/src/main/kotlin/RunFor.kt`
- tests: `UpNextTest` (port up-next.test.ts), `AutoplayTest` (port autoplay.test.ts), `RunForTest` (season and folder boundaries, film → empty)
- `android/ui-mobile/src/main/kotlin/UpNextCard.kt`, `PlayAllButton.kt`
Modify:
- `android/feature/player/src/main/kotlin/PlayerViewModel.kt` / split session helper, `PlayerHandle.kt`, `DefaultPlayerHandle.kt`, `FakePlayerHandle.kt`
- `android/ui-mobile/src/main/kotlin/LibraryPositions.kt`, `LibraryFlow*.kt`, `ListScreen.kt`, `KeptWall.kt`, `PlayerScreen.kt`, `PlayerControls.kt`

## Implementation steps
1. Pure ports + `RunFor` + tests (`nextAfter` over `playOrder`, unchanged in `NextUp.kt`).
2. `LibraryPositions` gets `run: List<String>?` (saveable) set with every `setId`
   assignment (`LibraryFlow.kt:145-230` call sites, search rows, home cards);
   list/kids pass their run; "Play all".
3. Keep `nextInQueue` as a 3-line helper in `:feature:player` (same web function name).
4. VM up-next state, cancel set, gate polling, `playWhenReady` flag.
5. Card + standing button; title line from phase 04.
6. Device: seek to 0:40 before an episode's end → card; let it end → countdown → next starts
   after buffering; cancel → no autoplay but button remains; list "Play all" of two films chains.

## Todo
- [x] UpNext, Autoplay, RunFor + tests
- [x] queue plumbing + Play all
- [x] VM + handle flag
- [x] card + button
- [x] check.sh, bump, changelog
- [ ] device run (controller runs this separately; not done from this worktree)

## Implementation notes
- Files touched beyond the phase's own list, and why:
  - `android/ui-mobile/src/main/kotlin/CatalogScreen.kt` — the Kids wall's
    "Marked by hand" Play all needed a run threaded from `LibraryBranches`
    down to `KidsWall`; `CatalogScreen`/`KeptTabContent` sit on that path.
  - `android/feature/player/src/main/kotlin/UpNextAsync.kt`,
    `UpNextState.kt`, `DefaultPlayerHandleOps.kt`,
    `android/ui-mobile/src/main/kotlin/PlayerScrubber.kt`,
    `PlayerViewModelDelegates.kt` — new files, not on the phase's own list,
    split out to keep `UpNextController.kt`, `DefaultPlayerHandle.kt`,
    `PlayerControls.kt` and `PlayerViewModel.kt` under the 200-line
    guideline once this phase's additions landed (the same move as
    `AudioChoiceController`/`SubtitleChoiceController`). `PlayerScreenLifecycle.kt`
    already existed (an earlier phase's own split off `PlayerScreen.kt`);
    this phase grew it with the `DisposableEffect`s `PlayerScreen.kt` used
    to run inline, for the same line-guideline reason.
  - `android/feature/player/src/test/kotlin/MediaSetFixtures.kt` — `fakeMediaSet`
    gained an `fsk` parameter (default `null`) so `UpNextControllerTest` could
    pin that a switch carries the next title's age rating along.
- Caught before it shipped: the phase ticker (`UpNextController.ensureTicking`)
  first ran unconditionally from the moment a title opened, which left it
  running past the end of every existing `:feature:player` test that opens a
  title and never explicitly stops the player — `runTest`'s own implicit
  drain then never finished. Gated on `onPlayingChanged` instead, the same
  trigger `PlayerSession`'s own save ticker already uses (and every one of
  those tests already exercises before finishing); see
  `UpNextControllerTest.thePhaseTickerStopsOncePlaybackPauses`.
- "asap" (Play now / the standing button) does not poll a readiness gate at
  all: it opens with `playWhenReady = true` and lets media3's own
  buffer-then-play handle it, the same as opening any title by hand. Only
  the countdown's own unattended start polls [pollAutoplayGate], gated on
  `autoplayReady`.
- The autoplay gate logs its own reason (`Log.d("UpNext", …)`) each poll, so
  the device check's "logcat shows gate reason" can actually be read.

### Fixes from device run and review
- The switch (autoplay and "Play now") no longer reopens the next title by
  calling into the handle directly from the VM. `UpNextController` asks
  through a new `pendingSwitch: StateFlow<PendingPlayerSwitch?>`;
  `LibraryPositions.replacePlayer(id, run)` moves the top PLAYER frame in
  place (not a push — back still leaves to whatever opened the player), and
  `PlayerScreen`'s own effect calls it, then reopens through the ordinary
  `LaunchedEffect(setId)`/`viewModel.open` path once that recomposes with
  the new id. A rotation or process restore now reads the title actually
  playing, never the one that had just finished. `UpNextSwitcher` (new,
  split out of `UpNextController`) owns the pending-switch/gate bookkeeping;
  `fsk` no longer travels through the switch at all — the UI layer
  recomputes it from the catalog for whatever `setId` is now open, the same
  as it always has for an ordinary navigation.
- The autoplay gate also treats "the loader has stopped, with anything at
  all held" as ready (`PlayerHandle.isLoading()`, new): media3's default
  `DefaultLoadControl` caps how far it will ever buffer ahead, well under
  the ported 60s threshold, so a high-bitrate file could sit at the 45s
  patience ceiling every time. The ported constant and the pure function
  are unchanged; the extra condition lives beside them in `pollAutoplayGate`
  only.
- The gate is cancelled the moment playback starts by hand
  (`UpNextSwitcher.cancelGateOnPlay`, from `onPlayingChanged(true)`), the
  same trigger the web's own `stopWaitingToStart` uses — otherwise a poll
  landing after a viewer paused again would call `play()` straight over it.
- The screen now also stays on for the countdown and the gate wait
  (`UpNextUiState.awaitingStart`, new): `STATE_ENDED` drops `isPlaying`,
  which used to drop `FLAG_KEEP_SCREEN_ON` for the whole ten seconds before
  anything had actually switched.
- Kids "Marked by hand" tiles now play into the marked-by-hand run itself
  (`onPlayRun(item.setId, byHandIds)`), matching `app.js:552`'s
  `setGrid(byHand, (set) => play(set, byHand))` exactly, in place of opening
  the tile's own title or show. The wall's own "Play all" button is
  removed — see Deliberate differences below.
- `UpNextController.updateRun` recomputes what follows the open title
  without touching the ticker, countdown or `ended`: the run supplied at
  `startTitle` could still be empty if the catalog was mid-load when a
  PLAYER frame restored (cold resume, process death), and nothing used to
  ask again once it arrived. `PlayerScreen` gets a second effect,
  `LaunchedEffect(setId, run) { viewModel.updateRun(setId, run) }`, apart
  from the one that opens.
- `PlayerSession.save` gained a floor (`MIN_SAVE_MS`, 1s): the real
  `setMediaItem` fires a synchronous "stopped playing" against whichever
  title `session.open` already named when a switch lands mid-playback
  (`PlayerViewModel.open` calls `session.open` before `handle.open`), which
  otherwise wrote the next title onto Continue at 0s before a frame of it
  had played. `FakePlayerHandle.open` now models the same synchronous
  emission, gated on the fake's own last-reported playing state, so
  `PlayerViewModelSwitchTest`/`PlayerSessionTest` can pin it without
  mocking media3 directly.
- A seek while paused now updates the card (`PlayerHandle.Listener.onSeeked`,
  wired off media3's own `onPositionDiscontinuity` with a seek reason): the
  ticker only runs while playing, and a seek is the one other moment the
  web's own `timeupdate` would have caught it.
- The card is now measured the same way `SubtitleLayer` clears the
  transport bar over the picture — `PlayerScreen` passes `barTop`/its own
  screen-bottom measurement into `UpNextCard`, which lifts itself clear of
  the bar when shown and sits inside the system's own bottom inset either
  way, rather than a fixed padding that clipped under a three-button
  navigation bar and could sit under the bar when both were shown at once.

## Deliberate differences
- Kids "Marked by hand" has no "Play all" — the web has none there either
  (only on a list); the Android wall matches it rather than keeping the
  earlier addition. Lists keep their own "Play all", which the web also has.

## Success criteria
- Ported web tests green; same next title as web for a season boundary and a course folder boundary.
- Autoplay on throttled link waits (logcat shows gate reason), never starts into a stall < 45 s.

## Risks
- Rotation during countdown: both the countdown and the phase ticker live in
  the VM (a rotation recreates the Composition, not the ViewModel or its
  coroutines), so neither resets or restarts.
- `STATE_ENDED` on singleton player then reopening: ensure `open` of a new id always reloads (`DefaultPlayerHandle.kt:126-129` only skips same id when not IDLE).

## Security
None.

## Next steps
10 takes the next two from the same run to preload.
