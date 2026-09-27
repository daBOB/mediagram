# Code review: film preload engine (uncommitted diff on feat/android-film-preload @ 2b0afa08)

## Scope
- Files: core/playback `FilmPreloader`, `FilmPreloadState/Queue/Budget/Retry`, `ProgressThrottle`, `DownloadLane`, `CacheDataSourceWriter`, `HeldSets`, `SeriesPreloader`; feature/player `PreloadService`, `di/ActivePlayback`, `di/PlaybackModule`, manifests; feature/catalog `CatalogViewModel`; tests, versions, changelog.
- Build: `./gradlew testDebugUnitTest lint :app:assembleDebug` passes (exit 0).
- Probes: scratchpad copy `…/scratchpad/probe/android`, files `core/playback/src/test/kotlin/FilmPreloaderProbeTest.kt`, `FilmPreloaderCancelProbeTest.kt`, `feature/player/src/test/kotlin/ActivePlaybackProbeTest.kt`. All results below are reproduced there, not inferred.

## Overall
The pieces are well shaped (a small queue, a throttle, the fits rule and a fake-driven engine), but the engine's concurrency only holds under the unit tests' suspending fake writer. The real `CacheWriter.cache()` blocks its thread, so the pause-on-play, time-limit and remove paths are starved. The first enqueue also crashes the app on a wrong-thread ExoPlayer read. Nothing calls `enqueue` yet, so none of this is user-visible today. Phase 03 must not ship on top of it.

## Critical

**C1. The watchdog, `pauseForTimeLimit` and `remove` never run during a write, because a blocking `CacheWriter` holds the worker's only thread.**
`PlaybackModule.kt:449` gives the engine `newSingleThreadExecutor()`. `CacheDataSourceWriter.kt:135-145` runs `writer.cache()` (blocking) on the caller's dispatcher. `FilmPreloader.kt:245` launches the watchdog on that same dispatcher. So once `cache()` starts, the watchdog cannot run until the whole film has been written. `pauseForTimeLimit` (`:116`) and `remove`'s cache clear and unheld event (`:108`) are queued behind the write in the same way.
- Probe `probe_watchdogCannotFireWhileWriteBlocksTheOnlyThread`: `cancelActive` was called 0 times within 1 s of `isPlaying = true`, and the write ran to its natural end.
- What this breaks:
  - Playing anything does not pause the preload. Both download at once.
  - Playing the same film makes the player bypass the locked hole (see "Verified OK") and stream the whole film uncached, so the film is downloaded twice.
  - The series next-episode preload waits on the lane behind the entire film.
  - On Android 15 the service stops at the time limit while the write carries on without a foreground service.
  - Removing film B while film A writes leaves B's bytes and badge until A finishes.
- Why the tests missed it: `FakeFilmWriter.write` suspends (`gate.await()`) instead of blocking.
- Fix: run `cache()` on its own writer dispatcher (a dedicated single thread keeps the "don't starve IO" rationale), and tie coroutine cancellation to `CacheWriter.cancel()`:
  ```kotlin
  withContext(writeDispatcher) {
      val h = coroutineContext.job.invokeOnCompletion(onCancelling = true) { w.cancel() }
      try { w.cache() } finally { h.dispose() }
  }
  ```
  Add one test with a thread-blocking fake on a real single-thread dispatcher.

**C2. The first film preload crashes the app: `fits` reads the ExoPlayer from the worker thread.**
`PlaybackModule.kt:463-466` calls `ActivePlayback.currentTotalBytes()` (`ActivePlayback.kt:57`, `player?.currentMediaItem`) on the film worker thread.
- In media3 1.10.1, `ExoPlayerImpl` sets `throwsWhenUsingWrongThread = true` in its constructor (javap: `iconst_1; putfield` at offset 1013). `getCurrentTimeline()` and `getCurrentMediaItemIndex()` both call `verifyApplicationThread()`.
- Probe `ActivePlaybackProbeTest`: `IllegalStateException: Player is accessed on the wrong thread.`
- The exception is thrown outside `runWrite`'s try block, so it kills the worker.
- Probe `probe_fitsThrowingKillsTheWorker`: the exception is uncaught, the next film stays `Queued` forever and `hasWork` stays `true`. The production scope has no `CoroutineExceptionHandler`, so this is a process crash.
- It triggers whenever the player is built, which since M2 is from catalogue start, so in practice every first enqueue.
- Fix: never touch the player off main. Publish the open set's id and size from a main-thread listener (`onMediaItemTransition` / `onPlaybackStateChanged`) into a field, and read that. Also catch per item in `runWorker` (log, then `Failed`), so no single throw can kill the worker or crash the app.

## High

**H1. Cancel, remove and the time limit are ignored unless the item is inside an active `CacheWriter`.**
`runItem`'s loop checks `queue.activeId` only after an `Interrupted` outcome (`FilmPreloader.kt:188`). The paused-for-playing wait (`:160`), the metered delay (`:165`) and the retry backoff (`:206`) all fall through to `continue`, and then to `setRunning` and a new write. The class doc's claim (`:147-151`) that a cancel while paused ends the loop is untrue.
- Probe `probe_cancelWhilePausedForPlayingIsIgnored`: Paused → cancel → Idle → playback stops → **Done**.
- Probe `probe_removeWhilePausedReDownloads`: the cache was cleared and the unheld event emitted, then the film was re-downloaded and a held event emitted, so the badge comes back.
- Fix (preferred): give each item its own `Job`, and make cancel/remove/time-limit cancel it. `first()`, `delay()` and `Mutex.withLock` are all cancellable, so every wait ends for free.
- Minimal fix: re-check `queue.activeId == item.setId` at the top of the loop and after acquiring the lane.

**H2. `cancelActive()` on the shared writer cancels whichever write holds the lane, including the series preloader's.**
`CacheDataSourceWriter.kt:112-150` keeps one `activeWrite` for both preloaders; see also `FilmPreloader.kt:93,103`.
- Scenario: an episode has just ended and the series preloader is writing E2. Film A shows `Running` while it is actually waiting for the lane (`setRunning` runs before `lane.withLane`). The viewer taps Cancel on A.
- Probe `probe_cancelWhileWaitingForLaneHitsSeriesWriteAndFilmStillRuns`: `interrupted=[ep2]`, `completed=[f1]`. The episode preload dies and the cancelled film downloads in full anyway.
- Fix: per-call cancellation, which the C1 fix gives for free. Also call `setRunning` only after the lane is acquired.

**H3. A cancel during an active write leaves the state stuck at `Running` and `active` stale.**
media3's loop is: read → `onNewBytesCached` → progress callback → `throwIfCanceled`. The in-flight read's callback therefore lands after `cancel()` has already set the override to `null` (`:96`). The return path for an interrupted item that is no longer active (`:188`) resets neither.
- Probe `probe_cancelDuringActiveWriteLeavesStaleRunning`: 1 s after cancel the state is `Running(30, …)` and `active` is non-null.
- Fix: guard `setRunning` with "still this item's attempt" (job active or `queue.activeId`), and settle the state after the write unwinds.

## Medium

**M1. The fits rule treats the whole cache as untouchable (needs the user's call).**
`FilmPreloadBudget.kt:46` with `PlaybackModule.kt:464` checks `cacheSpace + playing + remaining ≤ budget`.
- An LRU cache sits at its budget in steady state, so after normal viewing every preload gets `NeedsSpace`. "Raise the cache budget" only helps until playback fills it again.
- Decision 4 reads "must fit the budget (minus what is playing)". The phase text adds "held-by-others that must stay", and with no pinning nothing has to stay.
- Question for the user: judge by `total ≤ budget − playing` (older content, including earlier preloads, gets evicted) or keep the current rule?

**M1b. The playing reserve is stale and counted twice.**
`ExoPlayer.stop()` keeps the media item, so after leaving the player the last title stays reserved in full. Its held share is also already inside `cacheSpace`.
- Scenario: 20 GB budget, a 12 GB film just watched and fully cached, a 6 GB film to preload. The code computes 12 + 12 + 6 = 30 → `NeedsSpace`. Without the stale reserve it is 18, which fits.
- Fix: reserve only while `playbackState != IDLE`, and reserve only the playing title's unheld remainder.

**M2. The ExoPlayer is now built at catalogue start.**
`CatalogViewModel` injects `FilmPreloading` → `ActivePlayback` → `Deferred<ExoPlayer>`. Before this change only the player screen or `PlaybackService` built it. That means renderers (FFmpeg included) and a playback thread at launch on the TV box, while the changelog says nothing calls this yet.
- Fix (also fixes C2): make `ActivePlayback` a passive holder that `DefaultPlayerHandle`'s existing bridge listener feeds, or give the catalogue its own held/unheld event singleton.

**M3. `isPlaying` is false while buffering, so the preload competes exactly when the player needs bandwidth.**
This happens at every title open (BUFFERING before READY) and every rebuffer; each rebuffer re-grabs the lane and starts a new attempt (`ActivePlayback.kt:46`, `FilmPreloader.kt:160,245`). A user pause also resumes the preload.
- Fix: observe "a title is open", i.e. `playbackState` BUFFERING/READY, or not IDLE. `stop()` already moves the player to IDLE when it closes.

**M4. `Done` is sticky and never re-verified.**
`Completed` goes straight to `finish` with no held check (`:182`). A budget lowered mid-write, or a later LRU eviction (there is no pinning), leaves the film showing "Preloaded ✓" when it is no longer held.
- Fix: re-read held bytes after `Completed`. Derive `Done` from `held == total` instead of an override that is never cleared.

**M5. Metered is only checked between attempts, and one attempt is the whole film.**
If Wi-Fi drops mid-preload, grammers treats the IO error as a 1 s flood, reconnects on the new default network and keeps downloading over cellular. The `PauseReason.Metered` doc ("turned metered mid-download") overstates this (`FilmPreloader.kt:163`, `FilmPreloadState.kt:182`).
- Fix: have the watchdog also poll `network.isUnmetered()` every ~5 s.

**M6. After `remove()` the page can keep showing the pre-remove percentage.**
The override is set to `null` before the cache is cleared. `stateOf` reads held bytes once on that emission, and nothing re-emits after the removal (`:100-112`, `:80-81`).
- Fix: clear first, then settle the state (or emit `Idle(0, total)` explicitly).

**M7. The pause state shows 0 bytes held.**
`cachedBytesSoFar` reads the previous override, so a first pause (coming from `Queued`) or a Playing→Metered pause shows 0 held. A fully held film enqueued on a metered network shows `Paused(0, total, Metered)` instead of `Done` (probe `probe_fullyHeldOnMeteredShowsPausedNotDone`).
- Fix: check `held ≥ total` before the playing and network gates, and read held bytes for the pause state.

## Low
- **L1.** A total of 0 or less goes straight to `Done` plus a held event (probe). That contradicts `HeldSets.isHeld`, which says a set of unknown size is never held, and would light the catalogue badge. Reject it in `enqueue`.
- **L2.** Race on `hasWork`: the worker writes `_hasWork = !queue.isEmpty` (`:137`) and can overwrite the `true` that `enqueue` set on main. The item then runs with the service stopped. The window is narrow. Fix: publish `hasWork` from inside the queue's synchronized methods.
- **L3.** `ContextCompat.startForegroundService` (`PlaybackModule.kt:472`) and `removeFromCache` have no guard; any exception there crashes the app. Use try/catch (`ForegroundServiceStartNotAllowedException`).
- **L4.** FLOOD_WAIT: grammers 0.10 `AutoSleep` sleeps once inside `core.read` for floods of 60 s or less, so a cancel or pause can take about 60 s plus one chunk. Longer floods reach Kotlin as the generic `Network` error. The backoff (1 s up to 30 s; gives up after 9 failures with no progress, about 2.5 min) never hot-loops, but it ends in `Failed` during a longer flood. The implementer's trace of how the core surfaces it is right.
- **L5.** `lint { disable += "NotificationPermission" }` turns the check off for the whole app module, not just this one case as the comment claims. Use a `lint.xml` path ignore or a baseline instead, and fix the comment.
- **L6.** The service stays in the foreground while a preload is paused for playback, which spends Android 15's 6h/24h `dataSync` budget during a binge. This is acceptable given the rules on starting a foreground service from the background; worth a comment.
- **L7.** Version 0.70.1 is a patch bump for an "Added" feature; the plan says minor per feature phase (0.71.0). The changelog's "Pauses while anything plays" is false until C1 is fixed.
- **L8.** File sizes: `FilmPreloader` is 276 lines and `PlaybackModule` 249. Moving the four preload providers and the service-start collector into a `di/PreloadModule.kt` object is a natural split. `FilmPreloader` shrinks once cancellation is per-item `Job` (H1). Comment voice is fine; there are no plan references in code, comments or test names.
- **L9.** No test covers the catalogue's `heldEventRemoved` path.

## Verified OK
- `bytesCached` is cumulative: `CacheWriter.cache()` sets `bytesCached = cache.getCachedBytes(key, pos, len)` before the first `onProgress` (javap). There is no double count across attempts.
- Lane: `Mutex.withLock` releases on `InterruptedIOException`, there is no nested lane use, and the mutex is FIFO. No deadlock; starvation is bounded by one write (but see C1).
- Locked span vs player: the player's factory sets only `FLAG_IGNORE_CACHE_ON_ERROR` (`PlayerFactory.kt:55`), so it opens with `startReadWriteNonBlocking`. A locked hole means it bypasses to upstream uncached and re-checks every `MIN_READ_BEFORE_CHECKING_CACHE = 102400` bytes. It never blocks, and it resumes caching within 100 KiB of the preload unwinding.
- The observed player is the app's singleton ExoPlayer, which `PlaybackService`'s `MediaSession` also wraps, so play from a headset or the lock screen counts. Leaving the player (`stop()` → IDLE) and dismissing PiP (`pause()`) both resume the preload.
- Service: `startForeground` is called synchronously in `onCreate`; `START_NOT_STICKY`; stops when `hasWork` turns false; both FGS permissions are declared; POST_NOTIFICATIONS is absent; `onTimeout` calls `stopSelf` synchronously.
- `SeriesPreloader`: the CONFLATED `want()` is unchanged; the only change is the lane wrap. The cache volume cannot switch mid-process (`CacheProvider`: a move needs a restart), so the writer's memoized factory stays valid.

## Needs a device
- Does the FGS keep a backgrounded preload alive on the tablet and the TV box (cached-app freezer)?
- The TV box's API level: `getprop ro.build.version.sdk`. Below 33 its notification is shown.
- `dataSync` behaviour under targetSdk 37 / Android 17.
- Cellular handover mid-preload (M5).
- Real cancel and pause latency.
- The startup cost of M2 on the TV box.

## Recommended order
1. C1 + H1 + H2 + H3 together: per-item `Job` plus a writer dispatcher whose cancellation calls `CacheWriter.cancel()`, and a blocking-fake test.
2. C2 + M2 + M1b: a passive `ActivePlayback` fed on main; a per-item catch-all in the worker.
3. M3–M7, L1.
4. Ask the user about M1 before phase 03 renders `NeedsSpace`.

## Unresolved questions
- M1: which space rule does the user want once the cache is full?
- M3: should a user-paused (not buffering) title keep the preload paused?

---

# Re-review (2026-09-27, after the implementer's fixes)

## Build
`./gradlew testDebugUnitTest lint :app:assembleDebug` passes (exit 0).

## Probes
The old probes were ported to the new API. A new Robolectric probe runs the **real** `CacheDataSourceWriter` → media3 `CacheWriter` → `SimpleCache` over a slow `FakeCore` (150 ms per 1 MiB read). They live in the scratchpad `probe/android/core/playback/src/test/kotlin/`:
- `RealWriterPreloadProbeTest.kt`
- `PortedPreloadProbeTest.kt`
- `ReserveProbeTest.kt`

## Status of the earlier findings

| # | Finding | Status | Evidence |
|---|---|---|---|
| C1 | Watchdog, time limit and remove starved by the blocking write | Fixed | Real-writer probe: opening a title interrupts the real `CacheWriter` and the item goes to `Paused`. `remove` mid-write leaves 0 bytes cached and `Idle(0)`. |
| C2 | Player read off the main thread | Fixed | `FilmPreloader` never touches `Player`. `ActivePlayback` reads it only inside the listener or a Main-scope launch. |
| H1 | Cancel/remove ignored while paused | Fixed | Probes: paused cancel ends `Idle` with no write; paused remove ends with no held event and no write. |
| H2 | Film cancel hits the series write | Fixed | Real-writer probe: the film waits as `Queued` (not `Running`), the series write finishes "E2 held", and the film has 0 bytes cached. |
| H3 | Stale `Running` after cancel | Fixed | Structured concurrency: `supervisorScope` waits for the write child, so `finally`/`settle` runs after the last progress callback. |
| M1 (decision 6) | Fits rule | Applied | But see R2: a transient reserve now produces a terminal state. |
| M2 | Player built at catalogue start | Fixed | `dagger.Lazy`; constructing the class never resolves the player (`ActivePlaybackTest`). |
| M3 (decision 8) | Buffering counts as not playing | Applied | Open now means `playbackState != IDLE`. |
| M4 | Sticky `Done` | Fixed | Re-verified whenever the state is observed. |
| M5 | Metered only checked between attempts | Fixed | A 5 s poll now runs during the write too, with the R1 caveat. |
| M6 | Stale percentage after remove | Fixed | Real probe: `Idle(0)` once the remove settles. |
| M7 | Pause shows 0 bytes held | Fixed | A fully held film on a metered network is `Done`, and pauses show real held bytes. |
| L1 | Size 0 goes straight to Done | Fixed | Rejected in `enqueue`. |
| L2 | `hasWork` race | Fixed | Published inside the queue's synchronized methods. |
| L3 | Unguarded service start and cache removal | Fixed | Both guarded. |
| L5 | App-wide lint disable | Partly | See R6. |
| L6 | Foreground held while paused | Fixed | Documented. |
| L8 | File size | Partly | `PlaybackModule` is now 111 lines and `PreloadModule` 182; `FilmPreloader` is 354 (see R8). |
| L9 | No test for the un-held badge | Fixed | `CatalogViewModelTest.aFilmThePreloaderRemovesDropsItsHeldBadge`. |

## The regression questions asked
- **Does `supervisorScope` swallow cancellation?** No. An outer cancel makes `await()` throw `CancellationException` in the cancelled caller. `runCatchingWrite` rethrows it because the scope is no longer active. The scope still joins the write child, so the lane and `finally` wait for `CacheWriter` to unwind (the real-writer remove probe leaves 0 bytes cached).
- **Does the writer dispatcher leak threads?** No. There is one non-daemon thread per `CacheDataSourceWriter`, and production has a single instance shared by both preloaders, serialized by the lane.
- **Does `invokeOnCompletion` race a normal completion?** It is harmless. The handle is disposed in `finally` before `withContext` returns. A late cancel only sets the flag on an already finished `CacheWriter`. A cancel that arrives before registration fires immediately, so `cache()` throws at its first check. The one caveat is that this is `@InternalCoroutinesApi` (R7).
- **Can `OpenTitleSource` go stale when the player is released?** The app never releases its ExoPlayer: `PlaybackService` releases only the session, and `handle.release()` only detaches listeners. `stop()` moves the player to IDLE, which clears the open title. If a future change released the player, no IDLE callback would arrive and preloads would stay paused forever; that is worth a one-line comment in `ActivePlayback`.
- **Does `Lazy<Deferred<ExoPlayer>>` ever fail to resolve?** No. `Lazy.get()` runs the provider, which starts `scope.async { awaitCore(); buildPlayer() }`. So the first preload builds the player if nothing else has, and it resolves as soon as a core exists, which a preload needs anyway. Opening the player later reuses the same singleton, whose listener is already attached, so pause-while-playing works.

## Remaining findings

**R1 (Medium). Every watchdog pause is handled as a write failure, and repeated pauses end in `Failed`.**
`FilmPreloader.kt:313` together with `CacheDataSourceWriter.kt:106-117`.
- `write.cancel()` makes `CacheWriter.cache()` throw `InterruptedIOException` inside a cancelling job. kotlinx.coroutines makes the first non-cancellation exception the final cause, so `write.await()` throws `InterruptedIOException`, not `CancellationException`, and lands in `catch (e: Exception)` as `Failed`.
- Probe `probe_whatAwaitThrowsWhenTheWriteIsCancelledFromOutside` shows `java.io.InterruptedIOException isCancellation=false`.
- Probe `probe_openTitleInterruptionIsLoggedAsAWriteFailure` logs `write failed: null`.
- Probe `probe_repeatedOpenCloseWithoutProgressEndsFailed` counts every interruption as a failure.
- Consequences:
  - The `Interrupted -> continue` branch is dead code, and its comment is wrong.
  - Each pause costs a backoff of at least 1 s. Pauses with no progress in between (viewer sampling titles, a flapping metered network) escalate the resume delay to 30 s and, after 9, end in terminal `Failed("Could not preload this film")`.
- Why the tests missed it: the implementer's `BlockingWriter` has the same shape but its tests only assert that `cancelCount > 0`.
- Fix: set a local `interrupted` flag in both watchdogs before `write.cancel()`, and have `runCatchingWrite` return `Interrupted` when the flag is set, whatever exception `await` throws. Assert the outcome (no "write failed" log) in the blocking test.

**R2 (Medium). Opening a big title mid-preload turns into a terminal `NeedsSpace`.**
`FilmPreloader.kt:199-204` checks fits with the open title reserved before the open-title pause gate, and `NeedsSpace` ends the item.
- Probe `ReserveProbeTest`: budget 12, film B (8) running. Opening C (5) gives `NeedsSpace(8)`, and it stays `NeedsSpace` with `hasWork=false` after C closes, although 8 ≤ 12.
- The page tells the viewer to raise the budget when simply closing the player would have been enough.
- Fix: `NeedsSpace` only when `total > budget`. When `total > budget − reserved`, treat it as `Paused(Playing)` and re-check once the title closes.

**R3 (Low). Cancel followed at once by Preload is lost.**
While the cancelled write unwinds (up to one chunk), the queue still names the film active, so `enqueue` returns false.
- Probe `probe_cancelThenImmediateReEnqueueDuringUnwind`: ends `Idle`, `hasWork=false`.
- Fix: have `cancel` clear the queue slot synchronously (`queue.cancel`) before `job.cancel()`, and make `finally`'s `clearActive` clear only if the active item is still this one.

**R4 (Low). `cancel`/`remove` can miss a film that is starting right now.**
`cancel()` and `remove()` read `itemJobs[setId]` on the caller's thread. If the worker has already called `nextToRun()` but has not yet put the job into `itemJobs`, they fall back to `queue.cancel` and the job runs to `Done` anyway.
- This is most reachable for `remove()`: its job lookup happens before the launch, while its queue cancel runs inside it.
- Fix: do the lookup inside the `scope.launch(dispatcher)` block, which is atomic against the single-thread worker, or check `queue.activeId == item.setId` at the top of `runItem`'s loop.

**R5 (Low). Two new crash paths through uncaught exceptions.**
- An exception in the metered watchdog (`network.isUnmetered()` inside a `supervisorScope` child) goes to the uncaught handler. Probe: the `SecurityException` escaped and the write kept running.
- `ActivePlayback.refresh`'s `catalogRepository.mediaSet()` (marked `@Throws(CoreException)`) runs in a handler-less Main scope on every playback-state change.
- Fix: wrap both with the module's existing `safely` helper, the way `PlayerChoicesController.kt:114` already does.

**R6 (Low). The lint baseline swallowed 39 issues, and its comment says it captures one.**
`app/lint-baseline.xml` holds 15 issue ids, including `InsecureBaseConfiguration`, `UsableSpace`, `PictureInPictureIssue` and `DataExtractionRules`. All are now silenced for good.
- Fix: trim the baseline to the one `NotificationPermission` entry, or fix the comment.

**R7 (Low). `invokeOnCompletion(onCancelling = true)` is `@InternalCoroutinesApi`.**
A public alternative with the same behaviour:
```kotlin
coroutineScope {
    val j = launch(writeDispatcher) { w.cache() }
    try { j.join() } catch (e: CancellationException) { w.cancel(); throw e }
}
```

**R8 (Low). `FilmPreloader` is 354 lines.**
The clean seam is the write attempt: `runWrite`, `runCatchingWrite` and both watchdogs (about 75 lines) touch only `lane`, `writer`, `openTitleSource`, `network` and a progress callback. They could become an internal `FilmWriteAttempt(...).run(item): WriteOutcome`. Trimming the multi-paragraph comments would bring the file under about 260 lines. Fixing R1 inside that class keeps it small.

**R9 (Low). On the very first preload in a process, the open-title check reads null.**
`ensureListening()` attaches the listener asynchronously, so if a title is already open, one chunk is written before the watchdog pauses the preload (and, with R1, a backoff follows).

**R10 (Low, carried). The time limit pauses only the active film.**
After `onTimeout`, queued films keep running without a foreground service.

## Needs a device (unchanged plus new)
- Everything from the first list.
- Dismissing PiP pauses the title but keeps it open, so under decision 8 preloads stay paused until the viewer comes back and closes the player. Confirm that is acceptable.
- Real resume latency after closing the player, once R1 is fixed.

## Status
Every Critical and High finding is fixed and verified against the real media3 writer. Two new Medium logic bugs remain: R1, interruption treated as failure, and R2, a transient reserve producing a terminal `NeedsSpace`. Both are small fixes that should land before phase 03 renders these states.
