# Code review: fix/playback-before-preload (570d1031..4f34336b)

## Scope
- 69 files, +1298/-80. Android core/playback (UpstreamTraffic, factories, writer), feature/player (warm-up, download facts), ui-common/mobile/tv (overlay rows, warm-up stage, buffered band), web server (`/api/sets/:id/download`, rate meter, held chunks) and browser (stats poll, stage warm-up).
- Verified by running: `bun test test/download-facts.test.ts` (7 pass), plus a real-thread probe of the real `UpstreamTraffic.kt`, compiled outside the project (recipe at the end).

## Overall
No deadlock path. The player never waits on a background fetch. The two factories have separate `ChunkMemo`s. The player opens the cache non-blocking. A waiting writer thread holds no SimpleCache monitor and no LAN queue slot. The web route is tightly validated. But the gate's central claim, that a preload never runs while the buffer is filling, does not hold for a player that fetches one chunk at a time (the probe below). The warm-up has one real regression (titles already on disk).

## High

**H1. The gate opens in the sub-millisecond gap between two sequential player fetches**
- Where: `android/core/playback/src/main/kotlin/UpstreamTraffic.kt:57-68`. The doc claim is at :24.
- How: `background` waits for `playing == 0`. A width-1 player (every set up to 8 Mbit/s, see `readAheadWidth`) drops to 0 between every two chunks, while ExoPlayer parses the one it just got. The writer thread's StateFlow wake-up beats the loader's next `playing + 1` almost every time.
- Probe (real threads, real class, 100 ms fake fetches):
  - Sequential player, 30 chunks: the preload started 25 fetches (0 ms parse), 31 (1 ms) and 31 (3 ms). That is lockstep: one preload fetch beside every player fetch, which is the pre-fix behaviour.
  - A width-2 window, the shape of the Jim episode (15 Mbit/s gives width 2): 15 of 30 with equal fetch times, 5 of 30 with 0–60 ms jitter.
- Failure: during a warm-up, after a seek, or with any title on a link near its bitrate, the series preload still takes one fetch's share of a latency-bound link (about 16 Mbit/s per fetch on a 17–20 Mbit/s box). ⓘ meanwhile mostly reads "waiting for playback", so the box walk will look fixed while it is not.
- Why the tests miss it: `UpstreamTrafficTest` runs on virtual time with a foreground that holds steady, so it cannot show this.
- Fix: add hysteresis.
  - Record `lastForegroundEndMs` in `foreground`'s `finally`.
  - In `background`, loop: wait for `playing == 0`, then `delay(QUIET_MS - idle)`, and start only once `playing.value == 0 && clock() - lastEnd >= QUIET_MS`. 500–1000 ms is above any parse gap and below a full-buffer pause.
  - Pin it with a real-thread test: a sequential foreground with 1 ms gaps must let 0 background fetches start.
- Open question: the web gate (`download-gate.ts` `finally`) hands the slot to a background waiter the instant the last foreground task ends. That is the same race. It matters there only if the web's foreground is ever sequential.

## Medium

**M1. A preload of the episode that just opened holds the cache lock, so the player streams it uncached and the preload downloads it again**
- Where: `SeriesPreloader.kt:107-110` (`want` filters `currentId` and never cancels the running item) and `:140-147`.
- How it happens:
  - E1 ends and E2 opens while E2 is still preloading (likely now, because the gate slows the E2 preload during E1).
  - The preload's `CacheWriter` session holds a range lock on E2's hole `[P, end)`. Media3 1.10.1 `CachedContent.lockRange` is confirmed in the jar.
  - The player's `CacheDataSource` gets `null` from `startReadWriteNonBlocking` past P, so it bypasses the cache. Nothing it streams from P onward is written to disk.
  - The background write then re-fetches the same chunks in gaps (separate memo). It is twice the traffic for E2, and E3's preload cannot start until E2's ends.
  - ⓘ `next` (`DownloadFacts.kt:40,45`) names the open title itself as "next".
- Fix:
  - When a title opens, cancel the series write if it is that set. For example, pass the open id to `want()` and cancel the running item's job when `currentId == openId`. The cancel releases the lock at the next gap.
  - Filter `next` out in `downloadFacts` when `next.setId == open.setId`.

**M2. The warm-up holds titles already on this device (or served fast over the LAN)**
- Where: `PlayerViewModelOpen.kt:67` with `UpNextAsync.kt:57-74,82`.
- How: the gate resolves only on 60 s ahead (never reached on Android), on the loader stopping (50 s, or the ~131 MB DefaultLoadControl video byte target), or on 45 s of patience. Its first poll, at t=0, always fails, and it then steps in 500 ms.
- Failure: a fully held episode on the TV box's USB-stick cache must read 94–131 MB before the first frame, several seconds. Before this branch it started at media3's 2.5 s threshold. On the phone it adds at least 500 ms.
- The commit's own reason is "a heavy file on a slow link"; a held title has no link. Not changed by me: confirm the intent.
- Suggested fix: resolve the gate at once when `viewModel.held.value` is true (`PlayerViewModel.kt:186` already has it), or skip `warmUp` for held sets.

## Low

**L1. A cancel cannot reach a gate wait**
- Where: `CacheDataSourceWriter.kt:118-122`, `UpstreamTraffic.kt:62`.
- How: `CacheWriter.cancel()` only sets a flag. The wait runs inside `MlibDataSource.fetch`'s `runBlocking`, which belongs to no job.
- Failure:
  - A film or series write that is cancelled while waiting keeps the writer thread and the `DownloadLane` until the next foreground gap. Under saturating 4K playback (width 3–4, link below bitrate) that is until playback stops.
  - It then makes one more full fetch before throwing.
  - `FilmPreloader.remove()` and `pauseForTimeLimit()` `join()` on it.
  - Rarely reached, because the film preloader pauses while a title is open.
- Fix: make the wait observe the job. For example, `runInterruptible(writeDispatcher) { writer.cache() }` so cancellation interrupts the `runBlocking`, or a per-write cancelled flag polled by `background`. Add a real-thread test.

**L2. Web: a failed unattended start keeps the leader stuck on screen**
- Where: `web/public/lib/playback/player.js:588-592` and `stage-warmup.js:24-32`.
- How:
  - A direct-play `error` during a "buffered" wait: `film-leader.js` hides the leader, but `look()` keeps calling `showWarmUp` every 500 ms. That re-shows the leader and "Warming up" over the failure for up to 45 s.
  - Then `hideWarmUp` leaves the leader up because `readyState < 2`, and no `loadeddata` follows.
- Fix: `if (video.error) return stopWaitingToStart();` at the top of `look()`, and in `hideWarmUp` hide the leader when `video.error` too.

**L3. Web: "on disk" can read 100% for a title that is not fully held**
- Where: `web/src/cache/held.ts:125-127`.
- How: every chunk counts as a full `CACHE_CHUNK`, including each part's short last one. With two or more parts the overcount reaches a whole chunk, so a set missing one chunk is capped to `total` and reads 100%. This contradicts `download-rows.js:27` ("never read 100%"). The tests use only one-part sets.
- Fix: count per part with the part's real last-chunk length, or report `total - 1` unless `isHeld`.

**L4. Web: the `next` row is the whole server's**
- Where: `download-facts.ts:38-46`.
- How: the one shared `SeriesPreload` serves every viewer, so a kid profile or another household sees whichever episode someone else's show is preloading (hidden sections included; those are filters, not locks), and the open title itself as in M1.
- Fix: report `next` only when it is not the asked set and is in the same show, or document it beside the rate shortcut.

**L5. Docs that are now wrong**
- `Autoplay.kt:3-9`: an asked-for start now waits.
- `UpNextSwitcher.kt:33`: `playWhenReadyFor` is false for every warming title.
- `UpstreamTraffic.kt:24`: see H1.

**L6. Phone buffered band ignores the slider's own drawing**
- Where: `PlayerScrubber.kt:76-92`.
- How: the band is a plain rectangle from the thumb's centre. It fills the M3 thumb gap and the rounded track end. Visual only.

## Edge cases checked
- Deadlock: none. Foreground never waits. The memos are per factory. The player opens the cache without blocking. The wait holds no monitor or LAN slot. A woken waiter reads `currentCore()` fresh.
- Warm-up paths:
  - A gated up-next switch keeps its runtime gate: `warmUp` returns early on the same id.
  - A Next, episode or run pick warms up with no runtime; a short lesson resolves through `loaderStopped` at end of file.
  - The watch-together and cast paths skip it (`together != null`).
  - Rotation is `sameTitle`. The handle republishes and does not change `playWhenReady`; the gate job survives in the view model's scope.
  - Retry is `sameTitle` and plays at once. A gate still running through a failure calls `play()` on an idle player, which is harmless.
- Polling:
  - Android: `produceState` lives only while the overlay is composed, and `heldBytes` runs on IO.
  - Web: the interval runs only while the panel is open, and after close it does nothing, because `!video.currentSrc` returns early.
- Zero or unknown totals give a hidden or 0% row on both surfaces. Android's bytes come from the exact SimpleCache counts.
- Route security:
  - The path is anchored to `[A-Za-z0-9]{1,64}`, an unknown set gets 404, and the response is `no-store`.
  - Non-GET requests get 405 earlier in the router. HEAD works.
  - Nothing reaches a file path except alphanumeric ids, and `next.setId` comes from the server's own preload item.
- Versions are in step at 0.138.0 in all three manifests.

## Recommended order
1. H1: add hysteresis and a real-thread test, before the box walk. Otherwise the walk measures a partial fix.
2. M1: cancel a series write once its own set opens, and filter `next`.
3. M2: confirm the intent; skip the warm-up for held titles.
4. L1–L4.

## Plan follow-ups
- Phases 1–3 look implemented.
- Phase 1's "background waits for foreground" holds only for overlapping foreground fetches (H1).
- Phase 4 (box sideload, ⓘ check) is still open. Watch the `next` rate there rather than the "waiting" word.

## Probe recipe
Copy `UpstreamTraffic.kt` and a `SetChunkSource` stub to the scratchpad. Compile with `kotlin-compiler-embeddable-2.3.20` from `~/.gradle/caches`. The compiler's own classpath also needs kotlinx-coroutines-core-jvm 1.11.0. Then run a loader thread doing `runBlocking { fg.chunk() }` in sequence against a writer thread looping `runBlocking { bg.chunk() }`.

**Status:** DONE_WITH_CONCERNS
**Summary:** No deadlock or shutdown hang. But the background gate lets the preload fetch beside every sequential player fetch (shown by the probe), a still-running preload of the episode now playing makes the player stream it uncached and the preload fetch it twice, and the warm-up now holds titles already on disk.
**Concerns/Blockers:** H1 undercuts the branch's root-cause fix. M2 needs the user's intent.
