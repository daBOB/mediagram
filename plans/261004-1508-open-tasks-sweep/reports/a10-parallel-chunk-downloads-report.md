# A10: parallel chunk downloads for high-bitrate sets

Date: 2026-10-04. Branch: `worktree-agent-a6550a185439df880`, from `main` at f141bdb7 (0.108.0).
Commit `aacac445`: feat(android): fetch several chunks at once for high-bitrate sets.
Android only, in `android/core/playback`. No core, UniFFI or .so change, so the bindings were
not regenerated. Versions not bumped, as asked.

## The byte path today (read before designing)

- `MlibDataSource.read` holds one 1 MiB chunk (`CHUNK_BYTES`, `SetChunkSource.kt`) and fetches
  the next one only when it runs out. That is one blocking `core.read` per chunk.
- `MlibDataSourceFactory` puts `ChunkMemo` (LRU of 4) over `LanFirstChunkSource` over
  `TelegramChunkSource`. `CacheDataSource` (the disk cache) wraps the whole thing.
- **`ChunkMemo` held one mutex across the upstream fetch**, so every fetch through a factory ran
  one after another. Parallel fetching was impossible without changing this.
- Core `read` (`crates/mediagram-core/src/api/read.rs`) plans 512 KiB steps and pumps them one by
  one through `client.iter_download(...).skip_chunks(n)`. A 1 MiB chunk is two sequential
  `upload.getFile` calls.
- grammers 0.10 pipelines concurrent requests. `SenderPoolRunner` keeps one connection per DC,
  and every `invoke_in_dc` just pushes onto that connection's `rpc_tx`
  (`grammers-mtsender-0.10.0/src/sender_pool.rs:237-255`). Its own `download_media` uses 4
  concurrent `GetFile` workers on that same pool
  (`grammers-client-0.10.0/src/client/files.rs:34`, `:245-329`). So concurrent `core.read`
  calls from Kotlin become concurrent GetFile requests on the one connection, and no separate
  DC connections are needed.
- The core exports with `async_runtime = "tokio"`. `PartDocuments` is behind a `Mutex`. The core
  needed no change to accept concurrent reads.
- FLOOD_WAIT: `Client::new` uses `AutoSleep { threshold: 60 s }`. That sleeps once through any
  flood of 60 s or less inside the call (`grammers-client-0.10.0/src/client/client.rs:82-93`,
  `net.rs:124-166`). Anything longer, or a second flood, reaches Kotlin as
  `CoreException.Network("the download ended before it finished")`. That is the same error a
  dropped connection gives.

## Design (smallest change)

1. **`ChunkMemo`: in-flight sharing instead of a mutex across the fetch.**
   - Different chunks are fetched side by side.
   - The same chunk is never fetched twice concurrently: the first caller fetches, later
     callers await its `CompletableDeferred`. This is the Android counterpart of
     `web/src/cache/in-flight-chunks.ts`.
   - A fetcher's failure reaches its waiters.
   - A fetcher's cancellation does not: a waiter that is still wanted fetches the chunk itself.
   - Capacity is 4 + `MAX_READ_AHEAD` = 8 MiB, so chunks fetched ahead survive a session close
     for the next open.
2. **`ChunkWindow` (new, one per read session).**
   - Keeps chunks `index..index+width-1` in flight while the reader waits for `index`.
   - The first `take` of a session fetches its chunk alone. The window opens only after that
     chunk arrives, so the first frame and a seek's first frame wait one round trip, as before.
     The width lookup runs alongside that first fetch, not before it.
   - It stops at the session's last chunk. A `CacheDataSource` gap fill asks only for the gap,
     and the chunks after it are already on disk.
   - `cancel()`, called from `MlibDataSource.close()`, cancels whatever is still in flight.
     ExoPlayer seeks by closing and reopening, so this also covers seeks.
   - All fetches go through the shared memo and then the LAN-first path. A prefetched chunk is
     read from the LAN server when it has it and mirrored to it when it does not.
3. **`ReadAhead` (new, one per factory).**
   - Width is `readAheadWidth(total, duration)`, looked up once per set via
     `core.mediaSet(id).duration`.
   - After any failed fetch, every set's width is 1 until a full 60 s passes with no further
     failure. Each failure restarts that wait. Narrowing to 1 makes the next `take` cancel
     whatever was in flight past the reader.
4. **Wiring.** Only the player's factory reads ahead (`playbackDataSourceFactory`,
   `readAhead = true`). Preloads (`CacheDataSourceWriter`) stay sequential, because no viewer is
   waiting on them.

### Choosing N, with the arithmetic

- **Per-fetch rate:** one `core.read` is 1 MiB per ~0.5 s round trip = 8.39 Mbit / 0.5 s ≈
  16.8 Mbit/s. This matches the box's measured 15–17 Mbit/s sequential rate. The code uses
  16 Mbit/s, rounded down.
- **Width formula:** `N = ceil(2 × avgBitrate / 16 Mbit/s)`, clamped to 1..4. The factor 2 is
  headroom: the buffer fills twice as fast as it drains, which absorbs scenes running at double
  the average and lets the buffer recover after a seek.
- **Width by kind of file:**

  | Kind of file | Width |
  |---|---|
  | Up to 8 Mbit/s (episodes, almost every 1080p film) | 1, sequential as today |
  | 12 Mbit/s remux | 2 |
  | 20 Mbit/s | 3 |
  | The Batman (26.7 Mbit/s) | 4 |
  | Heat (28 Mbit/s) | 4 |
  | Anything ≥ 24 Mbit/s | 4 |
  | Unknown duration | 1 |

- **Cap of 4:** 4 × 16.8 ≈ **67 Mbit/s** in theory. That is above the 40 Mbit/s target and the
  40–65 Mbit/s peaks. It is also the worker count grammers uses itself. Each in-flight
  `core.read` has one GetFile outstanding at a time, so at most 4 GetFile requests are in flight.
- **Fake-clock test with 500 ms per chunk:** 40 chunks take 20 s with N=1 (16.8 Mbit/s) and
  5.5 s with N=4 (**61 Mbit/s**, 3.6×).

## Files

| File | Change |
|---|---|
| `android/core/playback/src/main/kotlin/ChunkWindow.kt` | new, the per-session window |
| `android/core/playback/src/main/kotlin/ReadAhead.kt` | new, the width formula, per-set lookup and failure backoff |
| `android/core/playback/src/main/kotlin/ChunkMemo.kt` | in-flight sharing replaces the mutex across the fetch; capacity 8 |
| `android/core/playback/src/main/kotlin/MlibDataSource.kt` | a window per session, cancelled on close and reopen |
| `android/core/playback/src/main/kotlin/MlibDataSourceFactory.kt` | `readAhead` flag; `ReadAhead` from `core.mediaSet` |
| `android/core/playback/src/main/kotlin/PlayerFactory.kt` | the player passes `readAhead = true`; named `currentCore` |
| `android/core/playback/src/main/kotlin/CacheDataSourceWriter.kt` | named `currentCore` argument (preload stays sequential) |
| `android/core/playback/src/main/kotlin/SetChunkSource.kt`, `LanChunkHttp.kt` | comments that described the old behaviour |
| `android/core/playback/src/test/kotlin/ChunkWindowTest.kt` | new, 8 tests on a virtual clock |
| `android/core/playback/src/test/kotlin/ReadAheadTest.kt` | new, 7 tests |
| `android/core/playback/src/test/kotlin/MlibDataSourceReadAheadTest.kt` | new, 3 Robolectric tests through open/read/close |
| `android/core/playback/src/test/kotlin/ChunkMemoTest.kt` | 4 new tests; the upstream's list is now synchronized |
| `docs/system-architecture.md` | the "Where the bytes come from" paragraph on read-ahead |

## Tests

A fake fetcher with a latency (`delay` on the test scheduler) checks the following. None of the
tests touch the network.

- The first `take` returns after one round trip (500 ms), and only one chunk is in flight until
  then.
- The window fills to 4 in parallel and never goes above 4.
- Every chunk is asked for exactly once and in order, even when the reader catches up with
  fetches still in flight.
- Memo: ten readers of one in-flight chunk cause one fetch. Different chunks run side by side.
  When a fetcher is cancelled, its waiter fetches the chunk itself. A fetcher's failure reaches
  all of its waiters.
- `cancel()` cancels every fetch in flight. Through `MlibDataSource`, close cancels what is in
  flight, a seek cancels the old window and opens a new one where it lands, and a low-bitrate set
  fetches nothing ahead.
- Flood-wait: a fast failure on chunk 2 makes the next `take` cancel 3 and 4. No new chunk is
  asked for after that. The retry session reads one chunk at a time. After 60 s the width is 4
  again. Each new failure restarts the backoff.
- Width: episodes and 8 Mbit/s films get 1, a 12 Mbit/s remux 2, 20 Mbit/s 3, 4K 4, and the cap
  holds at 4. An unknown duration gives 1. The duration is looked up once per set, and a failed
  lookup is asked again next time.
- Throughput: 16.8 Mbit/s sequential against 61 Mbit/s with 4 in flight, which is ≥ 3.5× and
  ≥ 40 Mbit/s.

**Mutation check (the tests bite).** Each of these deliberate bugs, applied one at a time and
then reverted, made named tests fail:

| Deliberate bug | Tests that failed |
|---|---|
| Open the window before the first chunk arrives | `theFirstTakeReturnsAfterOneRoundTrip…` (4 in flight instead of 1) |
| Make `cancel()` a no-op | `cancelStopsEveryFetchStillInFlight` and both close/seek tests in `MlibDataSourceReadAheadTest` |
| Drop the in-flight sharing | `aReaderAskingForAChunkAlreadyOnItsWay…` (10 fetches instead of 1) and `aFetchersFailureReaches…` |
| Serialise the memo again, as before | `differentChunksAreFetchedSideBySide` (2000 ms instead of 500 ms) |

The existing `concurrentRequestsForTheSameKeyStillFetchItOnlyOnce` did *not* catch the missing
sharing. Its upstream answers instantly, so nothing is ever in flight, which is why the
slow-upstream test was added.

**Acceptance:** `cd android && ./gradlew -q :core:playback:testDebugUnitTest
:feature:player:testDebugUnitTest testDebugUnitTest lint --rerun-tasks` is green. Exit 0, and lint
found nothing new: its one error is the `app` module's existing baseline entry.

| Module | Tests |
|---|---|
| core:playback | 297 (18 new; 4 added to `ChunkMemoTest`) |
| feature:player | 252 |
| feature:catalog | 341 |
| feature:system | 84 |
| feature:setup | 91 |
| feature:stats | 44 |
| core:data | 146 |
| core:testing | 36 |
| core:update | 22 |
| core:designsystem | 21 |
| ui-common | 73 |
| ui-mobile | 284 |
| ui-tv | 484 |
| app | 5 |
| **Total** | **2180 passed, 0 failed** |

The first full run had **1 of 284 failures in ui-mobile**. It passed on its own rerun, 284/284,
and again in the full rerun above. Nothing in ui-mobile's tests touches the byte path (grep for
`ChunkMemo`, `MlibDataSource`, `cacheDataSourceFactory`, `buildPlayer` and `ReadAhead` finds
nothing), so it is a flake unrelated to this change. Its name was not captured because `-q`
hides test logging.

## How to measure on the box

Install the build the usual way. The lead does the adb work; this worker did not touch a device.
Use the "TV test" profile and a cold cache: clear it with Settings › App info › "Cache leeren",
and either stop the LAN cache server or pick a title it does not hold. Otherwise you are
measuring the LAN server, not Telegram.

1. **logcat, tag `readahead`:** `adb -s 192.168.0.35:5555 logcat -s readahead:*`
   - When a title opens you should see one line:
     `<setId>: 28 Mbit/s average, 4 fetch(es) in flight`.
   - The Batman and Heat should say 4. An ordinary episode should say 1.
   - `a chunk fetch failed; one at a time for 60 s` means the backoff started. If this happens
     more than rarely, Telegram is pushing back, and that has to be reported.
2. **The playback stats overlay**, which has two rows to watch:
   - **reads** (`N fetches · X GB`): note it, wait about 20 s during a cold start or right after a
     seek into an unfetched region, and note it again. Throughput is ΔGB × 8 / 20 s. Expect about
     **50–65 Mbit/s** while the buffer fills, compared with 15–17 Mbit/s before. Once the buffer
     is full the rate falls to the film's own bitrate, because ExoPlayer stops reading. That
     falling rate is not a regression.
   - **buffer** (`m:ss ahead`) is the real signal. During playback of Heat or The Batman it
     should **grow**, then hold at ExoPlayer's ceiling of roughly 30–50 s. Previously it shrank
     to zero and rebuffered.
3. **First frame and seek:** these should be unchanged at about 3 s cold, because the first chunk
   of a session is still fetched alone. If the first frame gets slower, report it.
4. **What to report back:**
   - Mbit/s while filling, for Heat and for The Batman.
   - Whether either rebuffered, and whether they kept up through the high-bitrate scenes.
   - Any `readahead` backoff lines.
   - The overlay's `failed` count in the reads row.

If the fill rate stays near 16 Mbit/s with width 4, pipelining over the one connection is not
helping. See concern 1.

## Concerns and follow-ups

1. **This rests on Telegram serving pipelined GetFile requests on one connection in parallel.**
   grammers' own 4-worker downloader assumes it does. This has not been measured here. If the box
   shows no gain, the next step is a core change: separate sender connections per download. That
   would mean regenerating the UniFFI bindings and rebuilding the .so.
2. **Possible free latency win, not done here.** grammers' `DownloadIter::next` starts every
   call at the home DC (`grammers-client-0.10.0/src/client/files.rs:104`). If the library's
   files live on another DC, every 512 KiB request first pays a `FILE_MIGRATE` round trip. That
   could be a large part of the ~0.5 s per chunk. It is worth checking in the core's
   `tracing`/grammers logs, or by remembering the file's DC per part.
3. **Flood detection is coarse.** Kotlin cannot tell a flood wait from a dropped connection, so
   both cost 60 s of sequential reading. That is the safe direction. Telling them apart would
   need the core to surface the flood seconds, which is a .so change.
4. **Preloads stay sequential.** This was a choice: a whole film fetched 4-wide is a long stretch
   of parallel requests with no viewer waiting. It can be enabled by passing `readAhead = true`
   in `CacheDataSourceWriter` if the user wants faster 4K preloads.
5. **Memory.** The memo is now up to 8 MiB, up from 4 MiB, plus up to 3 MiB held by the window,
   alongside ExoPlayer's ~130 MB video buffer. This is negligible.
6. Sets with no `duration` in the index stay sequential. Every uploaded set has had one so far,
   but this has not been checked across the whole library.
