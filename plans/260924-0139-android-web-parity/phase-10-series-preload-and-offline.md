# Phase 10: Series preload and offline badges

## Context links
- `web/src/cache/series-preload.ts:45-76` (whole episodes, every missing chunk; one set and one run at a time; a new `want()` replaces the waiting queue, the current set finishes)
- `web/public/app.js:430,445-456` (on opening an `ep`: next two via the same `nextAfter` as Play next); `web/src/routes.ts:429-444` (max 2, `ep` only)
- `plans/260923-2032-smart-series-preload/plan.md` (whole episodes; on by default; env switch not UI; "Android not owed this until it has a cache")
- `web/src/cache/held.ts:38-53` (held = every chunk present), `web/public/lib/set-badge.js:64-65` (`offline` badge), `web/public/lib/preload-readout.js:97-103` (player readout says `· cached`)
- `android/core/playback/src/main/kotlin/PlayerFactory.kt:21-39` (`cacheDataSourceFactory`, key = URI), `CacheProvider.kt:61-133`, `CacheBudgetSettings.kt:7,14` (2 GiB default, 512 MiB min)
- Phase 07: the run passed to the player

## Overview
Priority P2 · Status pending.

## Key insights
- The preload plan's precondition is now met: Android has a disk cache
  (`SimpleCache`, `cacheDir/mlib`), so the web decision is owed.
- media3 `CacheWriter` over the **same** `cacheDataSourceFactory` fills the
  exact spans playback will read (same key `mlib://set/<id>`); nothing new in the core.
- Phone-only constraints the web never had (record as deliberate differences):
  1. **Budget**: two 2 GB episodes into a 2 GiB LRU would evict the playing one.
     Preload a candidate only while (held + current + candidate) ≤ 75 % of budget; else skip it.
  2. **Metered network**: preload only on unmetered networks (`ConnectivityManager.isActiveNetworkMetered`) — pending open question 4.
  3. No switch in the UI (web: env key, on by default) — on by default.
- Held check is `cache.isCached(key, 0, totalBytes)` — O(spans) in memory, run off-main when shelves are built.

## Requirements
- Opening an `EPISODE` → preload next two from the run (skip non-episodes, skip already held).
- One worker, sequential, cancellable; a new open replaces the queue, the in-flight set finishes.
- Stops when the app is swiped away (process-scoped coroutine; no WorkManager).
- `offline` badge on cards (`PosterCard`/`SetPlate`), search rows and list rows; web wording.
- Player shows "cached" in the stats/readout when the current set is held.

## Architecture
`SeriesPreloader` (`:core:playback`, `@Singleton`): `want(ids)` → `Channel(CONFLATED)`
of the latest list → worker: for each id, budget + network check → `CacheWriter(cacheDataSource, DataSpec(uri), …).cache()` on IO.
`HeldSets` (`:core:playback`): `isHeld(setId, totalBytes)`; `CatalogViewModel` maps
sets → `held` flag when shelves are built and after a preload completes (event flow).
`MediaSet` stays pure; the flag rides the UI card model (`ShelfCard` or equivalent).

## Related code files
Create:
- `android/core/playback/src/main/kotlin/SeriesPreloader.kt`, `PreloadBudget.kt` (pure) , `HeldSets.kt`
- tests: `PreloadBudgetTest`, `SeriesPreloaderTest` (fake writer: replacement, sequencing, skip-held)
- `android/ui-mobile/src/main/kotlin/OfflineBadge.kt`
Modify:
- `android/feature/player/src/main/kotlin/PlayerViewModel.kt` (call `want` on episode open), `di/PlaybackModule.kt`
- `android/feature/catalog/src/main/kotlin/CatalogViewModel.kt` (203 l. — split shelf building out first), `CatalogUiState.kt`
- `android/ui-mobile/src/main/kotlin/PosterCard.kt` (230 l. — split first), `SetPlate.kt`, `SearchRow.kt`, `ListScreen.kt`, `PlaybackStatRows.kt`
- `android/core/playback/src/main/kotlin/MlibDataSource.kt` only if counters must distinguish preload reads (prefer a separate `PlaybackCounters` instance)

## Implementation steps
1. Split `CatalogViewModel.kt` and `PosterCard.kt` below 200 (no behaviour change).
2. `PreloadBudget` + tests (fits / skip / current counted).
3. `SeriesPreloader` with injectable writer; tests.
4. Wire on episode open; network check.
5. `HeldSets`; badge on cards, rows; "cached" in player readout.
6. Device: clear `cache/mlib` only; open S1E1 on wifi; watch logcat until E2, E3 held;
   airplane mode → E2 plays with no network, badge shows on E2/E3.

## Todo
- [ ] splits
- [ ] budget + preloader + tests
- [ ] wiring + network gate
- [ ] held check + badges + readout
- [ ] check.sh, bump, changelog, device run

## Success criteria
- Tests green; device: next two episodes held within minutes on wifi and play offline.
- Playback of the current episode shows no stall while preloading (stats `buffer` stays > 30 s).

## Risks
- Telegram flood limits: preload and playback both call `core.read`; one worker at
  1 MiB reads adds ≤ one concurrent stream. Watch for `FLOOD_WAIT` in logcat; if seen,
  pause preload while playback buffer < 60 s.
- `runBlocking` reads in `MlibDataSource` on the preload thread: dedicated single-thread dispatcher.
- Eviction of the playing title: budget rule above; test with the minimum 512 MiB budget.

## Security
No new data leaves the device; cache is app-private.

## Next steps
12 documents budget/metered differences in the deliberate-differences list.
