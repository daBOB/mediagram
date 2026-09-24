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
- [ ] UpNext, Autoplay, RunFor + tests
- [ ] queue plumbing + Play all
- [ ] VM + handle flag
- [ ] card + button
- [ ] check.sh, bump, changelog, device run

## Success criteria
- Ported web tests green; same next title as web for a season boundary and a course folder boundary.
- Autoplay on throttled link waits (logcat shows gate reason), never starts into a stall < 45 s.

## Risks
- Rotation during countdown: countdown state lives in VM (survives); ticker restarts.
- `STATE_ENDED` on singleton player then reopening: ensure `open` of a new id always reloads (`DefaultPlayerHandle.kt:126-129` only skips same id when not IDLE).

## Security
None.

## Next steps
10 takes the next two from the same run to preload.
