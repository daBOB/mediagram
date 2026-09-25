# Android cache budget integration

Status: DONE

## Change

Only `android/core/playback/src/test/kotlin/CacheProviderTest.kt` changed permanently. The existing dispatcher test now releases its real cache during teardown. The new integration test runs through the production `CacheProvider`, `PlainCacheBudgetSettings`, `AdjustableLruEvictor`, database-backed Media3 `SimpleCache`, and Robolectric application context.

The test commits two real 512 MiB span files through `startReadWrite`, `startFile`, and `commitFile`. Sparse allocation avoids writing a gigabyte of fixture data; first and last bytes contain markers. No fake cache, mocked eviction callback, or reduced production budget is involved.

It verifies this sequence:

1. Both spans are held under the default 2 GiB budget.
2. `CacheProvider.setBudget` lowers the budget to 512 MiB, immediately reduces occupancy, and physically deletes one span file.
3. A fresh settings wrapper reads the selected budget.
4. Releasing the cache and resetting the provider creates a distinct database-backed cache on the same directory. The retained span is rediscovered, its boundary bytes survive, and occupancy reports the selected budget.
5. Committing another 512 MiB span after reopening evicts the prior retained file and keeps occupancy within the restored budget. This checks actual enforcement after reopen, not just a displayed number.

Cache hole locks, file handles, live cache instances, and the owned executor are released, including on assertion failure. Existing uncommitted edits were preserved. No production fix was required.

## Regression strength

Two bounded temporary mutations targeted `CacheProvider.kt`, with its original content restored in `finally` after each run:

- Replace the live `evictor.setBudget` call with `Unit`: the integration test fails after shrinking because the real held bytes and evictor budget remain unchanged.
- Seed reopened caches from `CACHE_MAX_BYTES` instead of the settings value: the integration test fails after reopening because the restored budget is wrong.

Both failed the intended `loweringTheBudgetEvictsRealSpansAndTheChoiceSurvivesReopening` test, rather than failing compilation. The original production source was restored before the final successful gate.

Logs:

- `/tmp/mediagram-cache-budget-live-eviction-mutation.log`
- `/tmp/mediagram-cache-budget-restored-budget-mutation.log`

## Validation

From `android/`:

```sh
./gradlew :core:playback:testDebugUnitTest --tests playback.CacheProviderTest
mise exec ktlint@1.8.0 -- ktlint --format core/playback/src/test/kotlin/CacheProviderTest.kt
mise exec ktlint@1.8.0 -- ktlint core/playback/src/test/kotlin/CacheProviderTest.kt
./gradlew :core:playback:testDebugUnitTest \
  --tests playback.CacheProviderTest \
  --tests playback.CacheBudgetSettingsTest \
  --tests playback.AdjustableLruEvictorTest
```

The new/existing provider tests passed first. The final three-class gate passes all 11 selected tests (2 provider, 4 settings, 5 evictor). Scoped ktlint and `git diff --check` pass. Logs: `/tmp/mediagram-cache-budget-first.log` and `/tmp/mediagram-cache-budget-green.log`.

An attempted `:core:playback:spotlessKotlinCheck` command failed because that task is not installed on this module. The existing scoped ktlint CLI was used successfully instead; no build configuration was changed. The failed task-discovery log remains `/tmp/mediagram-cache-budget-verified.log`.

The shared Gradle slot was coordinated with the controller and both Android workers. Every command reused daemon **3092229** with its existing JVM/locale settings. The slot was explicitly released afterward; the controller owns daemon cleanup. No extra daemon, dependency, manifest/version change, project commit, or scanner-state action was introduced.

Concerns/Blockers: None. The controller owns independent review and finding disposition.
