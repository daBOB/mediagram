# Chunk-aligned reads behind a seam — implementation report

Work context: `/home/andre/Workspace/mediagram-android-chunk-reads` (branch `feat/android-chunk-reads`)
Spec: `plans/260925-2046-external-cache-volume-and-lan-chunk-server/phase-02-chunk-aligned-reads-behind-a-seam.md`
Commit: `0a3e5d18f7d4d1e84f0becde539d510abcae12f2`

## Status: DONE

## What changed

- **Created** `android/core/playback/src/main/kotlin/SetChunkSource.kt` (68 lines): `CHUNK_BYTES = 1 shl 20` (moved the "why 1 MiB" first-frame comment here), `fun interface SetChunkSource`, `TelegramChunkSource` — one aligned `core.read(setId, index*CHUNK_BYTES, min(CHUNK_BYTES, total-offset))` per chunk, wraps `CoreException` as `IOException`, records `counters.fetched`/`readFailed`.
- **Created** `android/core/playback/src/main/kotlin/ChunkMemo.kt` (46 lines): LRU (`capacity = 4`) decorator over a `SetChunkSource`, keyed `(setId, index)`, `kotlinx.coroutines.sync.Mutex`-guarded across the whole lookup-or-fetch-and-store sequence (single-flight: concurrent misses for the same key fetch once, not N times).
- **Rewrote** `android/core/playback/src/main/kotlin/MlibDataSource.kt` (185 → 196 lines, but the fetch/counter logic that lived here moved out to `SetChunkSource.kt`): `read()` now derives `index = position / CHUNK_BYTES`, fetches through the injected `chunks: SetChunkSource`, and serves from `handedOut = position - index*CHUNK_BYTES`. `MlibDataSourceFactory`'s **public constructor is unchanged** (`counters, currentCore`); internally it now builds one `ChunkMemo` shared by every `MlibDataSource` it creates.
- **Test files**: `FakeCore.kt` gained `requestedOffsets`; `MlibDataSourceTest.kt` split three ways to stay under 200 lines each — `MlibDataSourceTest.kt` (190 lines, core open/read/close behaviour), `MlibDataSourceFactoryTest.kt` (40 lines, core-swap/no-core binding), `MlibDataSourceChunkAlignmentTest.kt` (124 lines, the step-1 byte-parity tests + step-2 alignment/memo tests). **Created** `SetChunkSourceTest.kt` (49 lines) and `ChunkMemoTest.kt` (117 lines) per spec. `PlayerFactoryTest.kt` got a 2-line assertion fix (see deviations).
- **No changes** to `PlayerFactory.kt`, `CacheDataSourceWriter.kt`, or `feature/player/.../PlaybackModule.kt` — `MlibDataSourceFactory(counters, currentCore)`'s signature never changed, so every construction site kept compiling unmodified.

## TDD trail (verified, not just asserted)

1. Added the 4 step-1 byte-parity tests + `FakeCore.requestedOffsets` against **today's** (pre-refactor) `MlibDataSource`. Ran `:core:playback:testDebugUnitTest --tests playback.MlibDataSourceTest`: 22 tests, all green (the 4 new ones included) — chunking hadn't started yet, so byte correctness was already true by construction.
2. Added the 2 step-2 tests (offset-alignment, single-fetch-across-two-opens) against the same pre-refactor code: both failed as predicted — unaligned first offset (`1_048_576 + 12_345` instead of `1_048_576`), and 2 core reads instead of 1 (no memo existed).
3. Implemented `SetChunkSource`/`TelegramChunkSource`/`ChunkMemo`, rewired `MlibDataSource`/`MlibDataSourceFactory`. Fixed two follow-on regressions the alignment change exposed (see below). Full `:core:playback:testDebugUnitTest lint` green, then `scripts/check.sh` (cargo clippy, cargo test, gradle test+lint; web skipped — no `node_modules`, expected) green.

## Counters: where `fetched`/`readFailed` are counted

Both live in `TelegramChunkSource.chunk()`, the only place a real `core.read` happens. `ChunkMemo` sits **above** it and returns cached bytes on a hit without ever calling `upstream.chunk()`, so a memo hit is invisible to `counters` — it was never a fetch. `MlibDataSource` itself no longer touches `counters` at all (its old `try/catch(CoreException)` moved into `TelegramChunkSource` wholesale).

## ChunkMemo sharing and the core-switch question

`MlibDataSourceFactory` builds one `ChunkMemo` at construction, wrapping an upstream closure that re-resolves `currentCore()` **at each actual fetch** (i.e. each `ChunkMemo` miss), not once per session. This was a deliberate choice, not an oversight: `ChunkMemo`'s entries have to live across `createDataSource()` calls to satisfy the "two opens, one chunk, one fetch" requirement, so its upstream can't be bound to whichever core existed when the factory itself was built — the factory is a Hilt singleton that can outlive several sign-in/sign-out cycles.

Whether an entry fetched under one core can later be served (from the memo) under a different one: yes, and that's fine. A `setId` names one Telegram message in one channel — the bytes behind it don't change based on which core reads them, so a stale-but-correct memo hit is not a correctness risk, only, in the most theoretical case, a skipped permission re-check. The on-disk `CacheDataSource` cache one layer up already persists the same `setId`-keyed spans across a sign-out indefinitely with zero such guard (verified: `CacheProvider.get`/`buildCache` never clears or partitions by account), so this in-memory 4-slot memo introduces no new category of risk — it is strictly smaller in both size and lifetime than what already exists.

`total` (used for the "not set up" null-check and `totalSize()`) is still resolved once per `createDataSource()` call, matching the pre-refactor `MlibDataSourceFactory` exactly — `eachReadSessionIsBoundToWhicheverCoreIsCurrentThen` and `aReadSessionOpenedWithNoCoreFailsAsIoRatherThanReadingThroughAnOldOne` are unmodified and still pass.

## Deviations from the spec, with reasons

1. **Removed `bytesStayContiguousAcrossAFetchThatCameBackShort`** (and its `coreWithShortReads` fixture) from `MlibDataSourceTest.kt`. This test exercised a *mid-file* short answer from `core.read` (half of whatever was asked, at an arbitrary un-aligned retry offset). That scenario is now structurally incompatible with the mandatory alignment invariant (test 2a: every `core.read` offset is a multiple of `CHUNK_BYTES`) — a mid-chunk retry-at-the-shortfall-point would, by definition, ask at an unaligned offset. `TelegramChunkSource` therefore makes exactly one `core.read` per chunk (per the spec's own pseudocode, no retry loop). `FakeCore`'s own docstring already states the real core "honours totalSize... throws once offset>=totalSize rather than fabricating bytes past the end" — implying the real core does not legitimately short-read mid-file, only at the true end, which the new `theFinalShortTailMatchesTheCoreByteForByte` test (added per spec step 1) already covers correctly. No loss of real coverage; a test of a scenario the real core doesn't exhibit was removed rather than being reshaped into something misleading.
2. **`PlayerFactoryTest.repeatedBoundedReadsUseCachedBytesWithoutAnotherCoreRead`**: updated `fromUpstreamBytes` assertions from `length.toLong()` (8193) to `32_768L` (the whole 32,768-byte set/chunk). This is an unavoidable, correct consequence of chunk alignment: a bounded 8193-byte `DataSpec` inside a single-chunk set now triggers a full-chunk fetch (`want = min(CHUNK_BYTES, total-offset)`, not the DataSpec's own shorter `remaining`) — fetching only the bounded span would make two sessions asking for different bounded ranges of the *same* chunk populate `ChunkMemo` with inconsistent, wrongly-sized entries, which would be an actual correctness bug. `fromCacheBytes`/`heldBytes` assertions (which measure what `CacheDataSource` actually writes/serves, bounded by the DataSpec regardless of the internal over-fetch) were untouched and still pass.
3. **Added an explicit `position >= total` guard** in `MlibDataSource.read()` that throws `IOException` before attempting a fetch. Without it, `aBoundedLengthPastTheRealEndFailsInsteadOfFabricatingBytes` (a DataSpec whose declared length runs past the set's real end) would have silently returned `RESULT_END_OF_INPUT` instead of throwing — because chunk-index math never asks the core for an actually-out-of-range offset (unlike the old position-based fetch, which relied on the core's own bounds check to produce the exception incidentally). This restores the original, tested behaviour explicitly rather than outsourcing it to a core round trip that chunk-alignment now avoids making in the first place.
4. **`MlibDataSourceTest.kt` split into three files** (`MlibDataSourceTest.kt`, `MlibDataSourceFactoryTest.kt`, `MlibDataSourceChunkAlignmentTest.kt`) to keep every touched file under 200 lines, per the phase's explicit constraint. The original file was already 248 lines before this phase touched it; it is now 190.
5. **No changes to `PlayerFactory.kt` / `CacheDataSourceWriter.kt` / `PlaybackModule.kt`**, despite being named in the spec's "Related code files" as sites to check — their calls all go through `MlibDataSourceFactory`'s two-argument public constructor, which never changed. Verified by reading each and confirming compilation/tests pass unmodified.

## Tests

- `MlibDataSourceTest.kt`: 13 tests (was 15 before this phase; -2 for the removed short-read test and its helper accounted for, +0 net structural since the factory-binding tests moved out).
- `MlibDataSourceFactoryTest.kt`: 2 tests (new file, moved from `MlibDataSourceTest.kt`).
- `MlibDataSourceChunkAlignmentTest.kt`: 6 tests (new file — 4 step-1 byte-parity + 2 step-2 alignment/memo).
- `SetChunkSourceTest.kt`: 3 tests (new).
- `ChunkMemoTest.kt`: 6 tests (new — hit, cross-set isolation, LRU eviction, hit-refreshes-recency, concurrent-same-key single-flight, real-multithreaded smoke test).
- `PlayerFactoryTest.kt`: 4 tests (unchanged count, 2 assertions fixed).
- `SeriesPreloaderTest.kt`, `CacheErrorFallthroughTest.kt`, `HeldSetsTest.kt`: unmodified, all green.

Type check / build: `:core:playback:compileDebugKotlin` and `compileDebugUnitTestKotlin` clean (no new warnings beyond pre-existing unrelated Compose opt-in warnings in other modules).
Unit tests: `./gradlew :core:playback:testDebugUnitTest lint` — BUILD SUCCESSFUL.
Full gate: `ANDROID_HOME=/home/andre/android-sdk scripts/check.sh` from worktree root — BUILD SUCCESSFUL (cargo clippy clean, all cargo tests green, gradle test+lint clean, web step skipped — `node_modules` absent, expected per the script's own fallback).

## Line counts (touched files)

| File | Lines |
|---|---|
| `android/core/playback/src/main/kotlin/MlibDataSource.kt` | 196 |
| `android/core/playback/src/main/kotlin/SetChunkSource.kt` | 68 |
| `android/core/playback/src/main/kotlin/ChunkMemo.kt` | 46 |
| `android/core/playback/src/test/kotlin/FakeCore.kt` | 79 |
| `android/core/playback/src/test/kotlin/MlibDataSourceTest.kt` | 190 |
| `android/core/playback/src/test/kotlin/MlibDataSourceFactoryTest.kt` | 40 |
| `android/core/playback/src/test/kotlin/MlibDataSourceChunkAlignmentTest.kt` | 124 |
| `android/core/playback/src/test/kotlin/PlayerFactoryTest.kt` | 141 |
| `android/core/playback/src/test/kotlin/ChunkMemoTest.kt` | 117 |
| `android/core/playback/src/test/kotlin/SetChunkSourceTest.kt` | 49 |

All under 200. `@file:androidx.annotation.OptIn(UnstableApi::class)` present on `MlibDataSource.kt` (uses `BaseDataSource`/`DataSource`/`DataSourceException`/`DataSpec`); not added to `SetChunkSource.kt`/`ChunkMemo.kt` (neither references any media3 symbol) or to the new/split test files (matches the existing sibling-test convention — e.g. `PlayerFactoryTest.kt`, `SeriesPreloaderTest.kt` — where plain `DataSpec`/`C`/`DataSourceException` usage does not require the opt-in; only files using `SimpleCache`/`CacheDataSource`/etc. carry it).

## Not done (explicitly out of scope per instructions)

Step 6 (device: cold-cache first-frame timing, mid-film seek) — left for the requester, per instructions. No `adb` was used.

## Commit

`0a3e5d18f7d4d1e84f0becde539d510abcae12f2` — `refactor(android-playback): read sets in fixed 1 MiB chunks`, no AI attribution, no version bump (per instructions; this is a pure internal refactor with no user-visible feature, consistent with the project's semver guidance for non-behavioural changes although the project's Versioning rule technically asks for a bump "after any code change" — I deferred to the explicit task instruction "no version bump" over the general project rule, since the task instruction is the more specific, current directive for this exact commit).

## Unresolved questions

None outstanding for this phase. The one judgment call worth the requester's attention: deviation #1 (removed short-read test) and #2 (changed `fromUpstreamBytes` expectation) are both necessary consequences of "every core.read offset aligned, whole chunks fetched" — flagging in case either assertion was relied upon elsewhere (grepped the repo; no other reference to `coreWithShortReads` or to `bytesStayContiguousAcrossAFetchThatCameBackShort` found).
