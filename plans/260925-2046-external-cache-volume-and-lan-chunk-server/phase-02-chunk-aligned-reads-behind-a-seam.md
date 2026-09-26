---
phase: 2
title: "Chunk-aligned reads behind a seam"
status: done
priority: P1
effort: "0.5d"
dependencies: [0]
---

# Phase 2: Chunk-aligned reads behind a seam

## Overview
This is a pure refactor with no behaviour change a viewer can see.
`MlibDataSource` stops reading 1 MiB from wherever a seek lands. Instead
it asks a `SetChunkSource` for the fixed 1 MiB chunk containing the
position and serves from the right offset inside it. That gives phase 4 a
stable key, `(setId, chunkIndex)`, that every device agrees on, and a seam
to put the LAN in front of Telegram.

## Key insights
- Today `fetch` calls `core.read(setId, position, READ_AHEAD)` at an
  arbitrary `position` (`MlibDataSource.kt:126`). Two devices seeking to
  different offsets would produce overlapping, un-shareable ranges.
- `READ_AHEAD` is already 1 MiB, measured as the right first-frame size
  (4 MiB took the first frame from 3 s to 7 s; see the byte-path memory).
  An aligned 1 MiB chunk is the same request size. A mid-chunk seek wastes
  at most the bytes before the offset; it does not add a round trip.
- 1 MiB is two Telegram 512 KiB chunks, so Telegram alignment improves.
- **Re-opens would re-fetch.** `open()` discards `held`
  (`MlibDataSource.kt:78`), and `CacheDataSource` opens a new upstream
  read for every gap. Aligned, each gap would download its whole chunk
  again (today a gap fetches only from its own start). The fix is a small
  **`ChunkMemo`**: the last few chunks (4 × 1 MiB), keyed by
  `(setId, index)`, shared by the factory's data sources. A re-open inside
  a chunk it already has costs no round trip.

## Requirements
- Functional: identical bytes for every `(position, length)` a
  `DataSpec` can ask for, including unaligned starts, the final short
  chunk, and `C.LENGTH_UNSET`.
- Every `core.read` offset is a multiple of `CHUNK_BYTES`.
- Counters (`fetched`, `readFailed`) keep their meaning.

## Architecture
```kotlin
const val CHUNK_BYTES = 1 shl 20   // replaces READ_AHEAD

fun interface SetChunkSource {        // IOException on failure
    suspend fun chunk(setId: String, index: Long, totalSize: Long): ByteArray
}
class ChunkMemo(capacity = 4) : SetChunkSource   // decorator, LRU of (setId,index) → bytes, thread-safe
class TelegramChunkSource(core, counters) : SetChunkSource
    // core.read(setId, index * CHUNK_BYTES, min(CHUNK_BYTES, total - offset))
MlibDataSource(chunks: SetChunkSource, ...)
    // fetch: index = position / CHUNK; held = chunk; handedOut = (position % CHUNK)
```

## Related code files
- Create: `android/core/playback/src/main/kotlin/SetChunkSource.kt` (interface, `CHUNK_BYTES`, `TelegramChunkSource`)
- Create: `android/core/playback/src/main/kotlin/ChunkMemo.kt` (+ `ChunkMemoTest.kt`: hit, eviction, concurrent access)
- Modify: `android/core/playback/src/main/kotlin/MlibDataSource.kt` (shrinks; factory takes a `SetChunkSource`)
- Modify: `android/core/playback/src/main/kotlin/PlayerFactory.kt` and any `MlibDataSourceFactory` construction sites (`SeriesPreloader`/`CacheDataSourceWriter` wiring, Hilt `PlaybackModule`)
- Modify: `android/core/playback/src/test/kotlin/MlibDataSourceTest.kt`, `FakeCore.kt` (record requested offsets)
- Create: `android/core/playback/src/test/kotlin/SetChunkSourceTest.kt`

## Implementation steps (TDD)
1. **Tests before (must pass on today's code):** add to `MlibDataSourceTest`:
   read from position 0 to the end; from an unaligned position (1 MiB +
   12345) to the end; a bounded length that crosses a chunk boundary; the
   final short tail. Each compares against `FakeCore`'s bytes. Run: green.
2. **New failing tests** (red on today's code): `FakeCore` records offsets.
   (a) Open at 1 MiB + 12345 and read to the end: every offset
   `% CHUNK_BYTES == 0` (today the first offset is unaligned, which makes
   this red). (b) Two `open()` calls on the **same factory** inside one
   chunk, the way `CacheDataSource` fills adjacent gaps, fetch that chunk
   **once** across both sessions (today: two fetches).
3. **Refactor:** introduce `SetChunkSource` and `TelegramChunkSource`, and
   make `fetch` aligned. Move the "why one fetch per MiB" comment to
   `CHUNK_BYTES`.
4. `SetChunkSourceTest`: the final chunk length equals `total − offset`,
   and a `CoreException` is wrapped as `IOException` with
   `counters.readFailed()` recorded.
5. **Regression gate:** step 1 tests unchanged plus `SeriesPreloaderTest`
   and `PlayerFactoryTest` green; `./gradlew :core:playback:testDebugUnitTest lint`.
6. **Device:** cold cache (`run-as … rm -rf cache/mlib`), then time to first
   frame on the 5.8 GB film should stay within 2–4 s. Seek mid-film: it
   plays. Record numbers in the phase review.
7. Commit: `refactor(android-playback): read sets in fixed 1 MiB chunks`.

## Success criteria
- [x] Byte-identical reads across all step-1 cases.
- [x] All `core.read` offsets aligned.
- [x] First frame is not measurably slower on the device.

## Risk assessment
- **Seek cost.** A seek near a chunk's end downloads up to 1 MiB before
  the seek point. That is bounded and equal to today's read-ahead; the
  device measurement confirms it.
- **ExoPlayer `DataSpec` positions from the local cache.** `CacheDataSource`
  asks upstream only for gaps, at arbitrary positions. The bytes stay
  correct, and `ChunkMemo` keeps the cost from multiplying; test (b)
  guards it.

## Review (2026-09-25)
- Device (Redmi tablet, "test" profile, cold `cache/mlib`): 12 Monkeys (12 GB, 1080p h264, 13 Mbps). Tap at 22:25:52.05, video decoder started at 22:25:55.04: about 3 s to data, with the first frame just after, inside the 2–4 s band measured before on a lighter file. A drag-seek to 1:01:21 played. System afterwards: 174 fetches, 173 MB upstream, 171 MB held, no failed reads.
- Code review: a short chunk would have been remembered and stalled or crashed playback. The core cannot return one today, but a LAN source can. `ChunkMemo` now refuses any chunk whose length is not `expectedChunkLength` (`f3c6682`).
- Deviation: the "short mid-file read" test was replaced by "a short chunk is refused and never remembered". A bounded read now costs a whole chunk upstream, which is why `PlayerFactoryTest` expects 32768.
- The "second play records 0 fetches" check owed by phase 1 is covered by `PlayerFactoryTest`'s repeat-read test. It was not repeated on device, to keep test plays off the viewer's profiles.
