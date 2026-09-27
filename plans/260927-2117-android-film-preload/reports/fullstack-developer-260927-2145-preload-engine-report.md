# Phase Implementation Report

### Executed Phase
- Phase: phase-02-preload-engine-queue-progress-service
- Plan: /home/andre/Workspace/mediagram-preload/plans/260927-2117-android-film-preload
- Status: completed

### Files Modified

Created (`android/core/playback/src/main/kotlin/`):
- `FilmPreloadState.kt` (104 lines) — `FilmPreloadState` sealed states, `PauseReason`, `FilmPreloadWriter`, `FilmPreloading` (+ `Noop`), `ActivePreload`.
- `FilmPreloadBudget.kt` (22) — `fitsFilmPreloadBudget`, mirrors `PreloadBudget.kt`.
- `FilmPreloadQueue.kt` (65) — FIFO + active slot, `@Synchronized` (touched by the worker and by cancel/remove from another thread).
- `FilmPreloadRetry.kt` (24) — `WriteOutcome`, `backoffDelayMs`.
- `ProgressThrottle.kt` (28) — caps state emissions to ~4/s.
- `FilmPreloader.kt` (276) — the engine.

Created (`android/core/playback/src/test/kotlin/`):
- `FilmPreloadQueueTest.kt`, `FilmPreloadBudgetTest.kt`, `ProgressThrottleTest.kt` — pure-piece tests.
- `FilmPreloaderTest.kt` — engine tests against fakes (14 cases).

Created (`android/feature/player/src/main/kotlin/`):
- `PreloadService.kt` (103) — the `dataSync` foreground service.
- `di/ActivePlayback.kt` (60) — split out of `PlaybackModule.kt` for line count, same reason `DefaultPlayerHandleOps.kt` exists.

Modified:
- `core/playback/.../CacheDataSourceWriter.kt` — added `write(item, onProgress)` + `cancelActive()` (implements new `FilmPreloadWriter`); kept `PreloadWriter.write(item)` as a thin delegate for `SeriesPreloader`. Updated the class doc: the lazy `factory` build is now safe because the shared `DownloadLane` — not "one dispatcher" — guarantees only one call is ever inside `write()`.
- `core/playback/.../SeriesPreloader.kt` — added a `lane: DownloadLane = DownloadLane()` constructor param (default keeps every existing test unchanged), wraps `writer.write(item)` in `lane.withLane { }`.
- `core/playback/.../HeldSets.kt` — added `heldBytes(setId, totalBytes): Long` to `HeldSetsQuery` (+ `Noop`, + the real `Cache.getCachedBytes` read in `HeldSets`).
- `feature/player/.../di/PlaybackModule.kt` — `provideDownloadLane`, `provideCacheDataSourceWriter` (the one shared writer instance), `provideSeriesPreloader` now takes the shared writer+lane, `provideActivePlayback`, `provideFilmPreloader` (builds the engine, subscribes `hasWork` to start `PreloadService`).
- `feature/player/build.gradle.kts` — added `androidx.core` (already a project dependency elsewhere, not new to the catalog) for `NotificationCompat`/`ServiceCompat`/`ContextCompat`.
- `feature/player/src/main/AndroidManifest.xml` — declares `PreloadService`, `foregroundServiceType="dataSync"`.
- `app/src/main/AndroidManifest.xml` — added `FOREGROUND_SERVICE_DATA_SYNC`; extended the POST_NOTIFICATIONS comment to cover `PreloadService`'s deliberately-unrequested case.
- `app/build.gradle.kts` — `versionName` 0.70.0 → 0.70.1; added a `lint { disable += "NotificationPermission" }` block (manifest-level check that a call-site `@SuppressLint` does not reach — see Issues below).
- `feature/catalog/.../CatalogViewModel.kt` — added `filmPreloader: FilmPreloading = FilmPreloading.Noop` param, collects `heldEvents` (folds into the existing `heldEventApplied`) and a new `unheldEvents` → `heldEventRemoved`, which drops a setId from `heldIds` (the badge consumer only ever added before this).
- `feature/player/src/test/kotlin/FakePreload.kt`, `ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt` — added `heldBytes()` overrides, mechanical fallout of the `HeldSetsQuery` interface change.
- `Cargo.toml`, `Cargo.lock` (via `cargo check -p mediagram-cache`), `web/package.json` — 0.70.0 → 0.70.1.
- `docs/project-changelog.md` — new 0.70.1 entry, states plainly that nothing in the UI calls any of this yet.
- `plans/260927-2117-android-film-preload/{plan.md,phase-02-*.md}` — status → done, Todo boxes ticked.

### Tasks Completed
- [x] Writer progress + cancel (`CacheDataSourceWriter.write(item, onProgress)` + `cancelActive()`)
- [x] Shared lane with `SeriesPreloader` (`DownloadLane`, one `CacheDataSourceWriter` instance for both)
- [x] `FilmPreloader` + states (`Idle/Queued/Running/Paused/Done/NeedsSpace/Failed`) + tests
- [x] Pause while playing (any playback, not just this film), resume from where the cache left off
- [x] `dataSync` service, manifest, no `POST_NOTIFICATIONS`
- [x] Held events on completion (`heldEvents`) and removal (`unheldEvents`, new — catalogue badges extended to consume it)
- [x] Tests green, version bumped, changelog written

### Tests Status
- Type check: pass (`compileDebugKotlin` clean across every touched module)
- Unit tests: pass — `./gradlew testDebugUnitTest` (whole project, all modules) green; 14 new `FilmPreloaderTest` cases + 8 `FilmPreloadQueueTest` + 5 `FilmPreloadBudgetTest` + 4 `ProgressThrottleTest`, plus the existing `SeriesPreloaderTest`/`HeldSetsTest`/`CacheErrorFallthroughTest`/etc. unaffected.
- Lint: pass — `./gradlew lint` (whole project) green after fixing 5 real errors this work introduced (below).
- Integration: `./gradlew :app:assembleDebug` green. No device install (tablet/TV box in use elsewhere, per instruction).

### The interface phase 03 will use

`playback.FilmPreloading` (Hilt-bound in `PlaybackModule`, same pattern as `SeriesPreloading`):
```kotlin
interface FilmPreloading {
    fun stateOf(setId: String, totalBytes: Long): Flow<FilmPreloadState>
    fun enqueue(setId: String, title: String, totalBytes: Long)
    fun cancel(setId: String)
    fun remove(setId: String)
    fun pauseForTimeLimit()          // called by PreloadService.onTimeout; a ViewModel would not normally call this
    val heldEvents: SharedFlow<String>
    val unheldEvents: SharedFlow<String>
    val hasWork: StateFlow<Boolean>  // PreloadService's own start/stop signal
    val active: StateFlow<ActivePreload?>  // PreloadService's notification text
}
```
`FilmPreloadState` is `Idle(heldBytes, totalBytes) | Queued | Running(heldBytes, totalBytes) | Paused(heldBytes, totalBytes, reason: PauseReason) | Done | NeedsSpace(neededBytes) | Failed(reason)`. `stateOf` is the one call a film page's ViewModel needs: it recomputes `Idle` from the real cache (`HeldSetsQuery.heldBytes`) whenever there is no active/queued/paused/done/failed override, so a cancel or remove settles back to the true percentage with no extra bookkeeping on the ViewModel's side.

### How pause-while-playing is detected

`core:playback` has no Android/player dependency, so "is anything playing" is answered by a small `ActivePlayback` class built in `feature/player/di` from the SAME `Deferred<ExoPlayer>` `DefaultPlayerHandle` awaits — a second `Player.Listener` on the one ExoPlayer instance (media3 allows more than one). `FilmPreloader` is handed `isPlaying: StateFlow<Boolean>` and, inside `runWrite`, races the actual write against `isPlaying.filter { it }.first()`; when that fires it calls `writer.cancelActive()`, which is `CacheWriter.cancel()` underneath — media3 checks that flag roughly every internal read, so the write stops within about a chunk and throws `InterruptedIOException`. `runItem`'s loop tells a pause-interruption apart from a cancel/remove/time-limit one purely by asking `FilmPreloadQueue` whether this item is still the active one afterward — `cancel()`/`remove()`/`pauseForTimeLimit()` all clear the active slot themselves before interrupting, so only the isPlaying case leaves it in place, which is what makes the loop retry.

The same `ActivePlayback` also answers `currentTotalBytes()` (the open title's byte size, resolved from the `MediaItem`'s own media id — `setUri(setId).toString()`, since nothing ever overrides it — through `CatalogRepository`), which is the "keep room for what is playing" half of `fitsFilmPreloadBudget`.

### How FLOOD_WAIT surfaces

Traced through `crates/mediagram-core/src/api/read.rs`: any failure during `core.read()` — Telegram flood limit included — is mapped by `failed()` to `CoreError::Network("the download ended before it finished")`, a **fixed** message; the real cause is only `tracing::warn!`-logged in Rust (see `error.rs`'s `CoreError::network`, which deliberately drops the cause from what Kotlin gets). `CoreError` has no `FloodWait` variant at all. So on the Android side a FLOOD_WAIT is indistinguishable from any other transient network failure — it surfaces as a plain `CoreException.Network` wrapped by `MlibDataSource`'s chunk path into a generic `IOException`, exactly the same shape `CacheWriter.cache()` throws for.

Given that, `FilmPreloader.runItem` treats **every** write failure that is not the cancel signal (`InterruptedIOException`) as transient: it backs off (`backoffDelayMs`, 1s→2s→4s→8s→16s→30s capped, up to 8 retries) and tries again, resetting the failure count whenever the held-byte total actually grew since the last failure (so a film that is mostly done and hits one blip does not get judged by the same budget as one that is fully stuck). Only after `MAX_CONSECUTIVE_FAILURES` (8) failures **with no forward progress** does it give up with `Failed(coreSentence() ?: "Could not preload this film")`. This is a deliberate, documented (`FilmPreloadRetry.kt`'s doc comment) design choice given the core's error surface, not a rediscovered Rust variant — no Rust was touched.

### Deviations from the phase file's file list (all within this worktree, no ownership conflict)

- `feature/catalog/.../CatalogViewModel.kt`, `feature/player/src/test/kotlin/FakePreload.kt`, `ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt` were not in the phase's "Related code files" list but were required by acceptance criterion 4 ("badges must also drop a removed film... if it only adds, extend it minimally") and by the `HeldSetsQuery` interface change respectively. No parallel phase in this plan owns any of them (phase 01 = crates/mediagram-cache; phase 03 = ui-mobile/ui-tv screens + "a small ViewModel", not `CatalogViewModel`; phase 04 = tests/docs/manifests, and phases run one at a time in this worktree per plan.md).
- `feature/player/src/main/kotlin/di/ActivePlayback.kt` is a new file not named in "Create:", split out of `PlaybackModule.kt` purely for the line-count guideline (same justification the codebase already uses for `DefaultPlayerHandleOps.kt`).

### Line-count guideline

Every new file is at or under 104 lines except `FilmPreloader.kt` (276) and `PlaybackModule.kt` (249, up from 162). Both were split once already (`FilmPreloadState.kt`/`FilmPreloadRetry.kt`/`FilmPreloadQueue.kt`/`ProgressThrottle.kt`/`DownloadLane.kt` out of the engine; `ActivePlayback.kt` out of the DI module) and I judged further fragmentation would hurt more than help: `FilmPreloader.runItem` is one state machine that reads worse split across call sites needing the same item/held/queue context (says so in its own `@Suppress` comment), and `PlaybackModule` is eight independent, already-minimal `@Provides` functions. Flagging this rather than silently accepting it.

### Risks / Unresolved Questions

- **`bytesCached` semantics** (verified, not guessed): media3's `CacheWriter` seeds `bytesCached` from `Cache.getCachedBytes` before its first callback and only ever grows it, so it is the resource's whole held total, not a per-attempt delta. Confirmed via a web fetch of the androidx/media source (`bytesCached = cache.getCachedBytes(cacheKey, dataSpec.position, dataSpec.length)`) after first getting this wrong in a draft (used `heldAtStart + bytesCached`, which double-counted on a resumed write) — caught before writing tests, not left in.
- **`remove()`'s cache clear is not synchronized with the cancel it triggers**: `cancelActive()` returns immediately; `cache.removeResource()` runs right after, without waiting for the interrupted `CacheWriter.cache()` to actually unwind. Worst case a stray span up to one chunk (1 MiB) survives a remove for a moment — self-heals on the next `getCachedBytes` read, and the LRU evictor would reclaim it regardless. Documented as an accepted ceiling rather than added `Job.join()` machinery for it.
- **Metered-network recheck is a plain 5s poll** (`METERED_RECHECK_MS`), not an observed connectivity flow — `PreloadNetwork.kt`'s `UnmeteredNetworkCheck` only offers a poll, matching `SeriesPreloader`'s own existing pattern; if playback starts during that 5s window the pause is only noticed at the next poll, not instantly. Same order of imprecision the phase's own "within about a chunk" bar already accepts.
- **No device verification** — per instruction (tablet/TV box in use by other work). The engine, service, and DI wiring compile and pass unit tests but have not been run on a real device; `PreloadService`'s foreground-service lifecycle and the notification's actual (non-)appearance are unverified beyond code review. Phase 04 owns device verification per plan.md.
- **`FilmPreloader.kt` / `PlaybackModule.kt` over the 200-line guideline** — see above, flagged rather than force-split.

### Next Steps
- Phase 03 (film page button/bar/server line) can inject `FilmPreloading` directly; `stateOf`/`enqueue`/`cancel`/`remove` are the calls it needs. It will also want the new `GET /v1/sets/{id}` route from phase 01 for the "Home server: x of y GB" line — unrelated to this engine.
- Phase 04 should device-verify `PreloadService` actually survives backgrounding and that its (permission-less) notification behaves as documented — nothing here was checked on hardware.

---

## Addendum: fixes from code-reviewer's concurrency review

Report reviewed: `code-reviewer-260927-2230-preload-engine-review-report.md`, probes at
`/tmp/.../scratchpad/probe/android/{core/playback/src/test/kotlin/FilmPreloaderProbeTest.kt,
FilmPreloaderCancelProbeTest.kt, feature/player/src/test/kotlin/ActivePlaybackProbeTest.kt}`.
The review was right on every Critical/High finding; all fixed, all probes ported into real
tests using a **thread-blocking** fake writer (not a suspending one), plus the existing
suspending-fake tests kept for pure logic.

### Architecture change (C1/H1/H2/H3 together)

`CacheDataSourceWriter.write` now runs `CacheWriter.cache()` on its own dedicated
`writeDispatcher` via `withContext`, and registers `currentCoroutineContext().job
.invokeOnCompletion(onCancelling = true) { writer.cancel() }` — the one way to reach a
call already blocking that thread, since plain coroutine cancellation cannot interrupt it.
`cancelActive()`/the shared `activeWrite` slot are gone entirely; cancellation is now
**per call**, done by cancelling whichever coroutine is running that write.

`FilmPreloader` gives each running film its own `Job` (`itemJobs`), which `cancel`/`remove`/
`pauseForTimeLimit` cancel directly — every wait inside `runItem` (paused for an open
title, a metered recheck, a retry backoff) and the write itself are ordinary suspend
points, so one cancel reaches all of them for free. `runWrite` uses `supervisorScope`
(not `coroutineScope` — a real bug I hit and fixed before it shipped: a plain scope
treats the write's own failure as scope-wide, skipping the retry path entirely on the
first IOException) and shows `Running` only after `lane.withLane` is actually entered.
A cancel settles cleanly because `runItem`'s `finally` (which calls `settle`) cannot run
until the write's own coroutine has fully unwound — no separate "ignore late progress"
flag needed; structured concurrency gives that for free.

### C2/M2/M3: passive `ActivePlayback`

`ActivePlayback` now implements `core:playback`'s new `OpenTitleSource` (`OpenTitle(setId,
totalBytes)?`), fed only from a `Player.Listener` attached on main; `FilmPreloader` never
touches `Player` at all. It takes `dagger.Lazy<Deferred<ExoPlayer>>`, not the plain
`Deferred`, and only calls `.get()` from `ensureListening()`, which `FilmPreloader.runItem`
calls once a film is actually being processed — never at construction — so the catalogue
merely injecting `FilmPreloading` no longer forces the app's `ExoPlayer` to build. "Playing"
is now "a title is open" (`playbackState != IDLE`), not literally `Player.isPlaying`, so
buffering and a viewer's own pause no longer contend with the preload.

**Bug caught by my own new test, fixed before reporting done:** `MediaItem.fromUri(uri)`
leaves `mediaId` at its default (empty), it does **not** default to the URI — my first
draft read `currentMediaItem?.mediaId`, always empty, always null `openTitle`. Fixed to
`currentMediaItem?.localConfiguration?.uri` (the URI `openReal` actually built the item
from). Caught by `ActivePlaybackTest.anOpenTitleIsPublished...`, not by inspection.

### Lead decisions #6/#8 (fits rule, what "playing" means)

`fitsFilmPreloadBudget(totalBytes, reservedBytes, budgetBytes)`: a film's own whole size
against budget minus only the *open* title's size (and only if it is a different film) —
no `heldBytes` term at all, since the LRU evictor makes room by evicting older content
(including earlier preloads) on its own. `NeedsSpace(totalBytes)` — the film's own size,
matching the "Needs 5.8 GB" example in plan.md decision 2 (5.8 GB being the film, not a
shortfall). Checked before the "is anything open" pause gate, so `NeedsSpace` is still
answerable while paused.

### Medium/Low

- M4/#9: `stateOf` re-verifies `Done` against `heldSets.heldBytes` on every observation.
- M6: `remove` clears the cache *before* settling the override, so the one emission a live
  collector sees already reads zero.
- M7: held is checked before the playing/metered gates, so a fully-held film enqueued on a
  metered network is `Done`, not `Paused`.
- L1: `enqueue` rejects `totalBytes <= 0`.
- L2: `hasWork` now lives on `FilmPreloadQueue` itself, published from inside its own
  `@Synchronized` methods — no separate racing write.
- L3: `removeFromCache` and `startForegroundService` are both try/caught and logged
  (`ForegroundServiceStartNotAllowedException` et al.), never crash the app.
- L5: the app module's lint suppression is now `baseline = file("lint-baseline.xml")`
  (`android/app/lint-baseline.xml`, committed), not `disable +=` — scoped to the specific
  already-known findings, not the whole `NotificationPermission` check.
- L6: `PreloadService`'s class doc now says explicitly why it stays foreground through a
  pause (a `dataSync` service cannot be restarted from the background) and names the
  Android 15 budget cost that accepts.
- L8: `di/PreloadModule.kt` now holds the four preload providers (was: crammed into
  `PlaybackModule.kt`, 249 lines). `PlaybackModule.kt` is 111 lines. `FilmPreloader.kt`
  did **not** shrink as L8 expected — seepage from the correctness fixes above
  (`externallySettled`, the outer catch-all, `supervisorScope`'s own reasoning) left it at
  354 lines. Moved `ItemOutcome` out to `FilmPreloadRetry.kt` as the one easy win; a
  further split would mean threading `overrides`/`_active`/`lane`/`writer`/
  `openTitleSource` into a second class for little real separation, so flagging this
  rather than forcing it under time pressure.
- L9: `CatalogViewModelTest.aFilmThePreloaderRemovesDropsItsHeldBadge` covers
  `heldEventRemoved`.
- L4/L7: no change — the review's own read was that the backoff is fine as shipped, and
  the coordinator confirmed keeping `0.70.1` (changelog text corrected instead; see below).

### Files touched by this pass (beyond the original phase-02 file list)

New: `OpenTitleSource.kt`, `FilmPreloaderBlockingWriterTest.kt`,
`feature/player/.../di/PreloadModule.kt`, `feature/player/.../di/ActivePlaybackTest.kt`,
`android/app/lint-baseline.xml`. Rewritten: `FilmPreloader.kt`, `FilmPreloadQueue.kt`,
`FilmPreloadBudget.kt`, `CacheDataSourceWriter.kt`, `di/ActivePlayback.kt`,
`di/PlaybackModule.kt` (trimmed). `docs/project-changelog.md`'s 0.70.1 entry rewritten to
describe the corrected (working) pause/fits/passive-player behaviour truthfully, per the
coordinator's explicit instruction to keep the version at 0.70.1 and fix the text rather
than bump to 0.71.0.

### Tests status (this pass)

`./gradlew testDebugUnitTest lint :app:assembleDebug` (whole project, `--rerun-tasks`)
green, run 4× in a row with no flakiness (the concurrency tests use real threads/real
`Thread.sleep`, worth the repeat check). No device install (unchanged constraint).

### Unresolved / still open

- M1's original ambiguity is resolved by the coordinator's lead decision #6 (LRU evicts,
  reserve only the open title) — no longer open.
- M3's "should a user-paused title still pause the preload" is resolved by decision #8
  ("playing" = open, paused-by-viewer included) — no longer open.
- `FilmPreloader.kt` at 354 lines, `di/PreloadModule.kt` at 182 — flagged above, not
  force-split.

## Addendum 3 — Re-review fixes (R1-R10, PiP, lint baseline)

Re-review confirmed all Critical/High fixed. This pass addresses the "Re-review" section's
R1-R10, the PiP question, and R6.

### R1 — cancelled write miscounted as a failure

`FilmWriteAttempt` (new, extracted from `FilmPreloader` per R8) sets a local `interrupted`
flag from *either* watchdog before it calls `write.cancel()`, and `runCatchingWrite` checks
that flag — not the exception type — in both its `CancellationException` and generic
`Exception` catch arms. `CacheWriter.cancel()`'s real behaviour (an ordinary
`InterruptedIOException`, not `CancellationException`, on the next `throwIfCanceled()`) is
exactly what made the exception-type check wrong before. Verified by
`anOpenTitlePausesARealBlockingWriteRatherThanFailingIt`, rewritten to assert the outcome is
`Paused` with no "write failed" log line, not merely that `cancelCount > 0`.

### R2 — open-title reserve making NeedsSpace permanent

`runItem`'s gate now double-checks: `NeedsSpace` only fires when the film fails to fit even
with nothing reserved (`!fits(total, reserved) && (reserved <= 0 || !fits(total, 0))`); a
film that only fails because of the *current* open title's reserve falls through to the
existing pause-and-rewait branch instead, and is judged fresh (against a live `open`) on
every loop iteration once that title closes. Verified by
`openingATitleThatOnlyTransientlyExceedsTheBudgetPausesRatherThanNeedsSpace` (new).

That test needed two follow-up fixes of its own, not the production code: (1) its `MapHeldSets`
fake was never written to by `BlockingWriter`, so `stateOf`'s own re-verification of `Done`
(`FilmPreloader.kt:90-93`, which re-reads `heldSets` on every observation so a lowered budget
or later eviction can't leave a stale checkmark) always downgraded a genuinely-finished write
back to `Idle` — fixed with a `HeldSetsQuery` fake that mirrors `writer.completed`, i.e. what
actually happened, not a hand-set map. (2) the original 8-byte film with a 50-byte chunk
finished in a single 10ms step, racing `open.set(...)` (called from a different thread
immediately after `waitUntil` returned) with no margin — scaled the film to 800 bytes (same
fits ratios) so the first write is genuinely still in flight when the title opens, matching
how every other test in the file avoids this. Confirmed non-flaky over several `--rerun-tasks`
passes.

### R3 — immediate re-enqueue after cancel

`cancel()` clears the queue slot (`queue.cancel(setId)`) synchronously, before dispatching
the job-cancel to the worker dispatcher — a re-tap lands on an already-empty slot rather than
racing an unwinding write. Verified by `cancelThenImmediateReEnqueueIsNotLost` (new).

### R4 — atomic cancel/remove job lookup

`cancel`, `remove`, and `pauseForTimeLimit` all look up `itemJobs[setId]` from inside
`scope.launch(dispatcher) { ... }` — the same single dispatcher `runWorker` registers a job
from — rather than the caller's own thread, closing the gap between an item going active and
its `Job` actually being stored.

### R5 — `safely`-wrapping

`core/playback` cannot depend on `feature/player`, so `core/playback/Safely.kt` (new) mirrors
`feature/player/Safely.kt`'s shape. `FilmWriteAttempt`'s metered watchdog and
`ActivePlayback.refreshNow`'s catalogue lookup (`player.safely(0L) { catalogRepository.mediaSet(...) }`)
both now use it, so a core exception there can't crash the app off a handler-less scope.

### R6 — lint baseline

Only `InlinedApi` (`PreloadService.kt`, `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC`) was
caused by this phase's own code — fixed at the source (extracted `startForegroundDataSync()`,
`@Suppress("InlinedApi")` with a comment explaining the constant is inlined at compile time,
not resolved at runtime, so passing it below API 29 is exactly how `ServiceCompat` expects to
be called on this app's minSdk 24) and removed from the baseline entirely.

The other ~13 issue types in the baseline (`UseKtx`, `ModifierParameter`, `ComposableNaming`,
`AutoboxingStateCreation`, `VectorRaster`, `PictureInPictureIssue`, `ModifierNodeInspectableProperties`,
`ModifierFactoryExtensionFunction`, `LeanbackUsesWifi`, `InsecureBaseConfiguration`,
`DataExtractionRules`, `UsableSpace`, `AndroidGradlePluginVersion`) live in files this phase
never touches — settings screens, TV catalog rows, the manifest's WiFi/backup config, the
Gradle/AGP version itself. Pre-existing and out of this phase's scope; left in the baseline,
reported rather than fixed. `:app:lintDebug` passes clean (`--rerun-tasks`): "1 error, 34
warnings and 3 hints filtered by baseline", no new issues.

### R7 — public cancellation pattern

`CacheDataSourceWriter.write()` no longer uses `@InternalCoroutinesApi`'s
`invokeOnCompletion(onCancelling = true)`. Now: `coroutineScope { launch(writeDispatcher) {
writer.cache() } }`, `job.join()`, catch `CancellationException` → `writer.cancel(); throw e`.
`BlockingWriter` (the test fake) was rewritten to the same shape, so the blocking-writer tests
stay faithful to the real implementation. Existing `CacheErrorFallthroughTest`/`HeldSetsTest`
pass unchanged.

### R8 — extraction

`FilmWriteAttempt.kt` (new, 103 lines) holds `run`/`runCatchingWrite` and both watchdogs — the
one piece of `FilmPreloader` that touches only `lane`, `writer`, `openTitleSource`, `network`,
never the queue or the state map. `FilmPreloader.kt` is 316 lines, down from 354 but short of
the ~250 target: R2's double-check, R3/R4's dispatcher-confined lookups, and R10's
drain-and-pause-every-queued-item logic all added real, load-bearing lines rather than
boilerplate. Flagging rather than force-splitting further under time pressure, same call as
Addendum 2's L8.

### R9 — open-title signal attached before the first write

`OpenTitleSource.ensureListening()` is now `suspend`, and awaits until the listener is
attached *and* the first `refreshNow` has actually settled `openTitle` — not fire-and-forget.
`ActivePlayback` uses an `AtomicBoolean` (only-once start) plus a `CompletableDeferred`
(what every caller, including the first, awaits). `FilmPreloader.runItem` calls it before
its first loop iteration.

### R10 — Android 15 time limit pauses every queued film

`FilmPreloadQueue.drainPending()` (new) empties and returns the whole pending list.
`pauseForTimeLimit()` cancels the active item's job (if any), then pauses it and every
drained pending item as `Paused(TimeLimit)`, then stops the service — previously only the
active item was paused; anything still queued would have kept running with no foreground
service backing it. Verified by `pauseForTimeLimitPausesEveryQueuedFilmNotJustTheActiveOne`
(new).

### PiP — investigated, no code change

Checked what the existing player does on picture-in-picture dismissal:
`PlayerViewModel.pauseForPipDismissal()` (`PlayerViewModel.kt:165-169`) calls `handle.pause()`,
not `stop()`, and its own doc comment says explicitly "the title stays open ... so reopening
the app finds it exactly where it was, paused." That is decision #8's "paused by the viewer"
case, already covered by `OpenTitleSource`'s "anything but closed counts" rule — working as
designed, not a gap. No code change; noting this as verified rather than silently treating it
as settled without a citation.

### Full verification (this pass)

`./gradlew testDebugUnitTest lint :app:assembleDebug` (whole project, `--rerun-tasks`) green.
`core:playback:testDebugUnitTest` re-run separately, `FilmPreloaderBlockingWriterTest`
specifically re-run 3× in a row (the real-thread tests) — no flakiness. No device install.

### Files touched this pass

New: `FilmWriteAttempt.kt`, `core/playback/Safely.kt`. Modified: `FilmPreloader.kt`,
`FilmPreloadQueue.kt`, `OpenTitleSource.kt`, `CacheDataSourceWriter.kt`,
`di/ActivePlayback.kt`, `PreloadService.kt`, `FilmPreloaderBlockingWriterTest.kt`,
`FilmPreloaderTest.kt`, `android/app/lint-baseline.xml`. Version left at 0.70.1 (no new
user-visible behaviour beyond Addendum 2's corrected changelog text).

### Unresolved / flagged, not fixed

- `FilmPreloader.kt` at 316 lines (target ~250) — see R8 above.
- The ~13 pre-existing, out-of-scope lint issue types — see R6 above.
