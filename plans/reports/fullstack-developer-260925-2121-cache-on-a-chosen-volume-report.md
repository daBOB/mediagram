# Phase 1: Cache on a chosen volume — implementation report

Plan: `plans/260925-2046-external-cache-volume-and-lan-chunk-server/phase-01-cache-on-a-chosen-volume.md`
Worktree: `/home/andre/Workspace/mediagram-android-cache-volume`, branch `feat/android-cache-volume`

## Status: DONE

## media3 1.10.1 finding (verified before writing the fallback)

Decompiled `SimpleCache.class` from `media3-datasource-1.10.1.aar` with `javap -c -p`.

- The public constructor locks the folder (`lockFolder`, throws `IllegalStateException`
  synchronously if another instance already holds it), then starts a background thread
  (`SimpleCache$1`, named `ExoPlayer:SimpleCacheInit`) that calls the private `initialize()`
  method, and blocks the caller on an `android.os.ConditionVariable` until that thread
  signals completion — regardless of whether `initialize()` succeeded.
- `initialize()` catches every failure path (directory creation, `listFiles()` returning
  null, UID creation, index replay) and stores the exception in the private field
  `initializationException`; it never rethrows, and the constructor never reads that field.
- `checkInitialization()` is `public synchronized`, reads `initializationException`, and
  `athrow`s it if non-null — otherwise returns silently.

Confirms the spec exactly: **the constructor never throws on a failed init; only
`checkInitialization()` does.** `CacheOpen.kt`'s `checkedInitializationFailure` calls
`cache.checkInitialization()` right after construction and treats a thrown
`Cache.CacheException` as "this volume failed to open," triggering the fallback to internal
storage.

## Files created

| File | Lines |
|---|---|
| `android/core/playback/src/main/kotlin/CacheVolumes.kt` | 104 |
| `android/core/playback/src/main/kotlin/CacheLocation.kt` | 59 |
| `android/core/playback/src/main/kotlin/CacheVolumeSettings.kt` | 52 |
| `android/core/playback/src/main/kotlin/CacheOpen.kt` | 106 |
| `android/core/playback/src/test/kotlin/CacheVolumesTest.kt` | 60 |
| `android/core/playback/src/test/kotlin/CacheLocationTest.kt` | 68 |
| `android/core/playback/src/test/kotlin/CacheVolumeSettingsTest.kt` | 60 |
| `android/core/playback/src/test/kotlin/CacheProviderVolumeTest.kt` | 148 |
| `android/core/playback/src/test/kotlin/CacheVolumeTestFixtures.kt` | 49 |
| `android/ui-mobile/src/main/kotlin/ui/settings/CacheVolumeBlock.kt` | 62 |
| `android/ui-mobile/src/main/kotlin/ui/settings/CacheSection.kt` | 21 |
| `android/ui-mobile/src/main/kotlin/ui/settings/SignOutConfirmation.kt` | 38 |
| `android/ui-mobile/src/test/kotlin/ui/settings/CacheVolumeBlockTest.kt` | 112 |

## Files modified

| File | Lines (after) |
|---|---|
| `android/core/playback/src/main/kotlin/CacheProvider.kt` | 188 |
| `android/core/playback/src/test/kotlin/CacheProviderTest.kt` | 179 |
| `android/feature/system/src/main/kotlin/CacheBudgetViewModel.kt` | 107 |
| `android/feature/system/src/test/kotlin/CacheBudgetChoicesTest.kt` | 14 |
| `android/feature/system/src/main/kotlin/SystemUiState.kt` | 40 |
| `android/feature/system/src/main/kotlin/SystemViewModel.kt` | 121 |
| `android/ui-mobile/src/main/kotlin/ui/settings/CacheBudgetBlock.kt` | 69 |
| `android/ui-mobile/src/test/kotlin/ui/settings/CacheBudgetBlockTest.kt` | 166 |
| `android/ui-mobile/src/main/kotlin/ui/settings/SettingsScreen.kt` | 167 (was 200) |
| `android/ui-mobile/src/main/kotlin/ui/LibraryFlowBranches.kt` | 203 (1-line change; pre-existing size, not grown by this phase) |
| `android/ui-mobile/src/main/kotlin/ui/system/SystemScreen.kt` | 148 |
| `android/ui-mobile/src/main/kotlin/ui/system/SystemRows.kt` | 145 |
| `android/ui-mobile/src/test/kotlin/ui/system/SystemRowsTest.kt` | 144 |
| `android/ui-mobile/src/test/kotlin/ui/system/SystemScreenTest.kt` | 84 |
| `android/ui-mobile/src/test/kotlin/ui/LibraryFlowFixture.kt` | 147 |
| `android/ui-mobile/src/test/kotlin/ui/system/SystemViewModelTest.kt` | 168 |

All created/grown files are strictly under 200 lines. `LibraryFlowBranches.kt` was already
203 lines before this phase (untouched by it apart from one renamed import and one renamed
call); it was not grown here, so it is flagged rather than split — splitting it is outside
this phase's file ownership.

Every file touching media3 types (`SimpleCache`, `Cache`, `CacheEvictor`, `CacheSpan`)
carries `@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)`.

## Tasks completed

- [x] Step 1 tests-before: ran `CacheProviderTest`, `CacheBudgetSettingsTest`,
      `AdjustableLruEvictorTest`, `CacheBudgetBlockTest` green (2+4+5+4 = 15), then added
      the locking test (`cacheDir/mlib`, survives `resetForTest()` + reopen).
- [x] `CacheVolumes.kt` — copied verbatim from the old plan's Task 1 (lines 44–262),
      `CACHE_DIR_NAME` moved here from `CacheProvider.kt`.
- [x] `CacheLocation.kt` — `resolveCacheLocation`, `budgetCap`, `budgetLadder`, red then green.
- [x] `CacheVolumeSettings.kt` — interface + `InMemory` + `Plain`, key `cache_volume_id`,
      same `playback_settings` prefs file as the budget, verified it doesn't clobber it.
- [x] `CacheProvider.kt` + new `CacheOpen.kt` — `get()` resolves the location via the
      `volumesFor` test seam, opens the chosen volume, falls back to internal on a missing
      or failed-init volume, computes `capBytes` from what's actually held post-open, clamps
      a too-large stored budget directly on the evictor (never through `setBudget`), and
      schedules the stale-volume sweep as a fire-and-forget coroutine after `get()` returns.
      `occupancy()` gained `volumeLabel`, `fellBack`, `capBytes`.
- [x] `CacheBudgetViewModel.kt` — `cacheBudgetChoices(cap)` now delegates to
      `budgetLadder(cap)`; added `volumes` and `chosenVolumeId` state and `chooseVolume(id)`.
- [x] UI — `CacheVolumeBlock.kt` (the "Where" row + restart sentence), `CacheSection.kt`
      (hosts `CacheBudgetBlock` + `CacheVolumeBlock`), `CacheBudgetBlock.kt` gained the
      fallback sentence, `SettingsScreen.kt` shrunk by extracting `SignOutConfirmation.kt`
      (it was exactly 200 lines, over the "strictly under 200" bar, before this phase touched
      it), `LibraryFlowBranches.kt` now wires `cache = { CacheSection() }`.
- [x] System screen — `SystemUiState` gained `volumeLabel`/`fellBack`; `SystemRows.kt` gained
      `cacheWhereLine` and a "Where" row in `cacheRows`.
- [x] Regression gate — `scripts/check.sh` (cargo clippy + test, gradle test + lint across
      every Android module) passed twice, the second time after the test-file split.
- [x] Step-1 tests re-run unchanged and green: `CacheProviderTest` (now split, see below),
      `CacheBudgetSettingsTest`, `AdjustableLruEvictorTest`, `CacheBudgetBlockTest`.

## Tests status

Type check / compile: pass (`compileDebugKotlin`/`compileDebugUnitTestKotlin` clean across
`core:playback`, `feature:system`, `ui-mobile`).

Unit tests, before → after (all green):

| Suite | Before | After |
|---|---|---|
| `CacheProviderTest` (`core:playback`) | 2 | 3 (kept only the no-choice-recorded lineage) |
| `CacheProviderVolumeTest` (`core:playback`, new) | — | 5 |
| `CacheBudgetSettingsTest` (`core:playback`) | 4 | 4 (unchanged) |
| `AdjustableLruEvictorTest` (`core:playback`) | 5 | 5 (unchanged) |
| `CacheVolumesTest` (`core:playback`, new) | — | 6 |
| `CacheLocationTest` (`core:playback`, new) | — | 8 |
| `CacheVolumeSettingsTest` (`core:playback`, new) | — | 4 |
| `CacheBudgetChoicesTest` (`feature:system`) | 1 | 1 (new signature) |
| `CacheBudgetBlockTest` (`ui-mobile`) | 4 | 5 |
| `CacheVolumeBlockTest` (`ui-mobile`, new) | — | 2 |
| `SystemRowsTest` (`ui-mobile`) | 11 | 13 |

`core:playback:testDebugUnitTest` module total: 145 → 150. Full `scripts/check.sh` (cargo +
gradle test + lint, all modules): **BUILD SUCCESSFUL**, twice.

Integration/regression: `scripts/check.sh` covers this — no separate integration suite for
this phase.

## Deviations from the literal spec, with reasons

1. **`CacheProviderTest.kt` split into `CacheProviderTest.kt` +
   `CacheProviderVolumeTest.kt` + `CacheVolumeTestFixtures.kt`.** Adding the six volume/
   fallback/cleanup/clamp tests in place pushed the file to 312 lines, over the "every
   created/grown file strictly under 200 lines" instruction. Split along the same seam the
   spec itself draws (`CacheProviderTest` = the no-choice-recorded locking behaviour;
   `CacheProviderVolumeTest` = volume resolution, spec step 5) rather than trimming
   coverage. Shared fixtures (`internalVolume`, `tempVolume`, `commitSpan`, `assertUnder`,
   `FAKE_FREE_BYTES`) moved to `CacheVolumeTestFixtures.kt`. All 8 tests still pass; test
   counts unchanged by the split.
2. **`SettingsScreen.kt` modified only to extract `SignOutConfirmation.kt`.** The spec's
   architecture note says this file "shrinks" as a consequence of the cache blocks moving
   into `CacheSection.kt`, but those blocks were never inlined in `SettingsScreen.kt` — they
   already arrived through a `cache: @Composable () -> Unit` lambda supplied by
   `LibraryFlowBranches.kt`, which is the only wiring this phase needed to change. The file
   was already exactly 200 lines (over the "strictly under 200" bar) before this phase
   touched it, so touching it at all requires bringing it under the limit; extracting the
   sign-out dialog (used nowhere else, no behaviour change) does that in the smallest way.
3. **No dedicated `CacheSection.kt` test.** It is a pure composition of two already-tested
   composables with no logic of its own (`Column { CacheBudgetBlock(); CacheVolumeBlock() }`).
   The spec's "(+ test)" markers are attached to `CacheVolumesTest` and `CacheVolumeBlockTest`
   only, not to `CacheSection`.
4. **`CacheProvider.volumesFor` is a mutable `internal var`, not a `get()` parameter.**
   Kotlin has no per-parameter visibility, so a public parameter would have widened `get()`'s
   public signature for every real caller. A swappable internal property mirrors the existing
   `resetForTest()` pattern and keeps the seam test-only, closer to the spirit of "internal
   seam" than a public default parameter would be.
5. **`CacheProvider.setBudget`'s cap-clamp reasoning is stated once, in `CacheOpen.kt`'s doc
   comment on `openCache`, not duplicated in `CacheProvider.kt`.** `setBudget` itself is
   otherwise unchanged: the UI only ever offers ladder entries already bounded by the current
   cap, so no runtime re-clamp was added there — the clamp exists only at open time, per spec.

## Unresolved questions

None. Device steps 8 and 9 were explicitly left to the user, per the task's instructions —
not attempted here, and `adb` was not touched.
