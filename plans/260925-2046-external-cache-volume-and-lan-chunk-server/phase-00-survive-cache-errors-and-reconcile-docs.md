---
phase: 0
title: "Survive cache errors, reconcile docs"
status: done
priority: P1
effort: "3h"
dependencies: []
---

# Phase 0: Survive cache errors, reconcile docs

## Overview
A disk cache that fails, because the disk is full or a card was pulled,
must fall through to the network instead of stopping playback. The stale
spec, plan and roadmap are also rewritten to describe what shipped.

## Key insights
- `cacheDataSourceFactory` (`android/core/playback/src/main/kotlin/PlayerFactory.kt:27-35`)
  sets no flags. A `CacheException` on read or write propagates, and
  playback stops.
- **The player only.** `CacheDataSourceWriter.kt:41` builds series
  preload from the same factory. If preload also ignored cache errors, a
  full disk or missing card would make it download whole episodes from
  Telegram and store nothing, again on every preload trigger. It would
  *not* mark them held: held is computed fresh from `Cache.isCached`
  (`HeldSets.kt:36-40`). So the flag goes on the factory the **player**
  uses, and preload keeps failing loudly. <!-- Updated: Validation Session 1 - corrected reason; the red-team's "marks held" claim was wrong -->
- `FLAG_IGNORE_CACHE_ON_ERROR` covers **read-side** cache errors. After the
  first error the source bypasses the cache for the rest of the request.
  Whether a **sink write** failure (disk full mid-write) is also absorbed
  is not certain. The test below decides it, not documentation.

## Requirements
- Functional: a read through the cache factory returns the full upstream
  bytes when the cache throws on read and when it throws on write.
- Non-functional: no behaviour change when the cache is healthy.

## Related code files
- Modify: `android/core/playback/src/main/kotlin/PlayerFactory.kt`
- Modify: `android/core/playback/src/test/kotlin/PlayerFactoryTest.kt` (or
  create `CacheErrorFallthroughTest.kt` if that file would pass 200)
- Maybe create: `android/core/playback/src/main/kotlin/ForgivingCacheDataSink.kt`
  (only if the write-side test fails with the flag alone)
- Modify docs: `docs/superpowers/specs/2026-09-21-android-external-cache-design.md`
  (§4, §7 budget half, §9, §10 version), `plans/260921-1751-android-external-cache/plan.md`
  (status → superseded, link here), `docs/development-roadmap.md:152`

## Implementation steps (TDD)
1. **Tests before.** Robolectric: build `cacheDataSourceFactory` over a
   `SimpleCache` in a temp dir with an upstream `FakeCore` of known bytes.
   Read a set fully and assert the bytes match (this locks in the healthy
   path). Run it: it passes.
2. **Failing tests.** (a) A `Cache` wrapper whose `startReadWrite` /
   `startReadWriteNonBlocking` throws `CacheException`. (b) A cache whose span
   files cannot be written: a `SimpleCache` over a temp dir that is made
   read-only (or a delegating `Cache` whose `commitFile` throws) after the
   index opens. A real full disk fails at write, close or commit, not at
   `startFile`. (c) Preload through `CacheDataSourceWriter` over the same
   broken cache **still fails** (it throws), so it does not keep downloading into nothing. (a) and (b)
   assert the full upstream bytes come back via `DataSourceUtil.readToEnd`,
   allowing one re-open the way ExoPlayer's loader retries. Run: (a) and (b)
   fail; (c) passes and must keep passing.
3. **Fix.** Add `.setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)` on
   the player's path only: a `forPlayback` parameter or a second factory
   function. `CacheDataSourceWriter` keeps the unflagged one.
   Rerun. If (b) still fails, wrap the sink in `ForgivingCacheDataSink`, a
   `DataSink` that drops the rest of the write after the first
   `IOException` from `CacheDataSink` and logs it once. Set it via
   `setCacheWriteDataSinkFactory`.
4. **Regression gate:** `(cd android && ./gradlew :core:playback:testDebugUnitTest lint)`.
5. **Docs.** Spec §4: replace the preset table with the live size list
   (512 MB–8 GB) from `b91e9e8`, with the cap now following the volume (phase 1). §7:
   the budget applies live and only the location applies at restart. §9:
   the TV surface exists on `feat/android-tv-ui`, and the LAN half is now
   this plan. §10: drop the predicted version. Old plan: `status: superseded`.
   Roadmap row: link to this plan.
6. Commit: `fix(android-cache): fall through to the network when the disk cache fails`
   and `docs: the external cache spec matches the shipped budget`.

## Success criteria
- [x] Both failure tests pass, preload still fails on a broken cache, and the healthy-path test still passes.
- [x] No doc still claims presets or "budget applies at restart".

## Risk assessment
- The flag makes one error bypass the cache for the rest of that request.
  That is acceptable; the next request tries the cache again.
- The media3 `Cache` interface is large, so the fake gets verbose. Delegate
  to a real `SimpleCache` and override only the throwing method.
