# Phase 02 — Preload engine: queue, progress, pause, service

## Context links

All under `android/core/playback/src/main/kotlin/` unless noted:
`SeriesPreloader.kt:60-133` (one worker, CONFLATED `want()`, `heldEvents`),
`CacheDataSourceWriter.kt:56-59` (media3 `CacheWriter`, ProgressListener `null`),
`PreloadBudget.kt:10-24` (0.75 × budget, unmetered), `PreloadNetwork.kt`,
`HeldSets.kt:36-68` (yes/no, key `mlib://set/<id>`), `MlibDataSource.kt:24-30` (key),
`CacheProvider.kt:66-179` (SimpleCache, budget), `AdjustableLruEvictor.kt`,
`android/feature/player/src/main/kotlin/di/PlaybackModule.kt:125-157` (preloader wiring,
own thread), `feature/player/.../PlayerViewModelPreload.kt:31-45`,
`feature/player/.../PlaybackService.kt:35` + its manifest, `android/app/src/main/
AndroidManifest.xml:29-34` (POST_NOTIFICATIONS deliberately absent).

## Overview

P2 · done. A `FilmPreloader` (name to taste) the film pages drive: enqueue, cancel,
remove, and observe per-film progress. Downloads one film at a time into the player's
cache through the same strict writer the series preloader uses (so the LAN path reads
what the home server holds and uploads what it lacks).

## Requirements

- **Queue:** FIFO of set ids, one active; enqueue is idempotent; cancel removes from
  the queue or cancels the active write (`CacheWriter.cancel()`); remove = cancel +
  `cache.removeResource(key)` + a held event so badges update.
- **Progress:** `StateFlow<Map<String, PreloadState>>` or `stateOf(setId): Flow<...>`,
  states: `Idle(heldBytes, total)`, `Queued`, `Running(heldBytes, total)`,
  `Paused(heldBytes, total)` (something is playing), `Done`, `NeedsSpace(needed)`,
  `Failed(reason)`. Initial held bytes from `cache.getCachedBytes(key, 0, total)` so a
  half-watched film starts at its real percentage; live from `CacheWriter`'s
  `ProgressListener` (throttle to ~4 updates/s).
- **Space:** fits when `total − held ≤ budget − (held-by-others that must stay) −
  playing reserve` — i.e. the budget, not 0.75 × budget; not fitting →
  `NeedsSpace(total)`. No pinning. Unmetered-network rule: keep the series preloader's
  `PreloadNetwork` check — a metered network pauses (state `Paused`) rather than fails.
- **One lane:** the film and series preloaders never write at the same time (shared
  lane/mutex); the film preload yields while the player plays — observe the player's
  playing state (the service/session already knows it) and cancel the active write,
  re-queue it at the front; resume when playback stops. Cached ranges survive a cancel,
  so a resume continues where it stopped.
- **Service:** start a `dataSync` foreground service while the queue is non-empty; stop
  when it empties. Declare `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`; do NOT
  declare POST_NOTIFICATIONS. Minimal notification ("Preloading {title} · 36%") on a
  low-importance channel — shown only if the user ever grants notifications.
  Survives activity death; not across process death (queue kept in memory — say so in a
  comment; persist later only if needed).
- **Pacing:** none beyond one sequential worker (same as the series preloader); a
  `FLOOD_WAIT` from the core must surface as a wait, not a failure — check how
  `core.read` reports it and back off accordingly.
- Completion emits on the existing `heldEvents` so the catalogue's held badges update.

- The home server's per-film status is on `LanChunkProtocol.setStatus` (default null
  = older server); the engine does not need it — the film page's ViewModel does.

## Architecture

Pure pieces unit-tested without Android: queue state machine, fits rule, progress
throttling. The writer/cache/service edges behind small interfaces the tests fake (the
module already tests `SeriesPreloader` this way — follow it).

## Related code files

- Create: `core/playback/.../FilmPreloader.kt` (+ `FilmPreloadState.kt`, `DownloadLane.kt`
  if it earns its keep), `feature/player/.../PreloadService.kt`, tests.
- Modify: `CacheDataSourceWriter.kt` (progress listener + cancel handle),
  `SeriesPreloader.kt` (take the shared lane), `PlaybackModule.kt` (wiring),
  `feature/player` manifest (service), `HeldSets.kt` (a `heldBytes(setId, total)`).

## Steps

1. Writer: expose progress + cancel. 2. Lane shared with the series preloader.
3. FilmPreloader state machine + tests. 4. Pause-while-playing hook. 5. Service +
manifest. 6. `./gradlew :core:playback:testDebugUnitTest :feature:player:testDebugUnitTest
:ui-tv:testDebugUnitTest :ui-mobile:testDebugUnitTest` green; `:app:assembleDebug`.
7. Minor bump, changelog. No commit.

## Todo

- [x] writer progress + cancel
- [x] shared lane with SeriesPreloader
- [x] FilmPreloader + states + tests
- [x] pause while playing, resume after
- [x] dataSync service, manifest, no POST_NOTIFICATIONS
- [x] held events on completion/remove
- [x] tests green, version, changelog

## Success criteria

Enqueue a film: progress climbs from its real held share; playing anything pauses it
and stopping resumes it; cancel stops within a chunk; remove frees the bytes; the app in
the background keeps downloading.

## Risks

- Double download when playing the preloading film: avoided by pausing on play.
- `CacheWriter` holds a span lock; cancel must release it before the player opens.
- Android 15 caps `dataSync` services (6 h / 24 h): a very long preload stops — surface
  as `Paused`/`Failed("time limit")`, resumable from the page.

## Security

No new permissions beyond the foreground-service pair; nothing leaves the device except
the existing authenticated PUTs to the paired home server.
