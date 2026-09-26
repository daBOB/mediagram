---
phase: 1
title: "Cache on a chosen volume"
status: done
priority: P1
effort: "1.5d"
dependencies: [0]
---

# Phase 1: Cache on a chosen volume

## Overview
The viewer picks which volume holds the cache: internal storage, or an
SD/USB volume reached through `getExternalCacheDirs()`. The budget
size list (today five fixed sizes from 512 MB to 8 GB) extends up to the chosen volume's cap. A location change applies at
the next start; a budget change stays live.

## Key insights
- `SimpleCache` takes a `java.io.File`. That rules out SAF and
  `MANAGE_EXTERNAL_STORAGE`; `getExternalCacheDirs()[1..]` is the one
  permission-free path (spec §2).
- **Capacity, not free space:** `cap = free + heldHere − 1 GiB`. Free space
  alone shrinks on every restart because the cache's own bytes stop
  counting as free (old plan, divergence 1).
- The player is a process singleton over a live `SimpleCache`, so moving
  the cache needs a restart (spec §7). The budget is already live through
  `AdjustableLruEvictor`.
- The Redmi has no removable volume, so card behaviour is proven by pure
  and Robolectric tests. The real check is on the microSD phone or the TV box.

## Requirements
- Functional
  - The list of volumes: internal plus present external volumes, index 0
    and nulls dropped.
  - The choice persists as a volume id in `playback_settings` (the same
    prefs file as the budget; plain, not a secret).
  - At open, the cache uses the chosen volume. If that volume is absent it
    falls back to internal **and keeps the recorded choice**.
  - An open counts as successful only after `SimpleCache` initialisation
    is confirmed. The constructor does not throw on a failed init;
    `checkInitialization()` does. **Verify this against media3 1.10.1
    sources first.** A failed init on the chosen volume falls back to
    internal, the same as a missing volume.
  - After a successful open, the `mlib` dirs on every other *present*
    volume are removed with `SimpleCache.delete(dir, databaseProvider)`,
    which also drops that cache's index tables from the shared database.
    This runs on a background coroutine **after** `get()` returns, so it
    never delays the first frame.
  - Budget choices are the doubling ladder from 512 MiB up to the chosen
    volume's cap (512M, 1G, 2G, 4G, 8G, 16G, …). A stored budget above the
    cap is clamped at open to **the largest ladder step ≤ cap**, never the
    raw cap. That keeps a list row selected (`CacheBudgetBlock.kt:55`
    matches by equality). The clamp is applied **directly to the evictor**
    at construction and never through `CacheProvider.setBudget`, which
    writes prefs; the stored choice is restored if space returns.
  - Settings: a "Where" row lists volumes with free space. Choosing another
    says "Takes effect the next time the app starts. Titles already held
    will be fetched again."
  - Settings and System show the volume in use, plus a sentence when it
    fell back.
- Non-functional: no new permission, and every file under 200 lines.

## Architecture
```
CacheVolumes.kt      pure list builder + Context adapter   (reuse old plan Task 1 verbatim)
CacheLocation.kt     resolveCacheLocation(volumes, chosenId) → Location(volume, fellBack)
                     budgetCap(volume, heldBytes) ; budgetLadder(cap)
CacheVolumeSettings  interface + InMemory + Plain (key "cache_volume_id", same prefs file)
CacheProvider        get(): resolve location → build SimpleCache(location.dir) → delete stale dirs
                     occupancy() gains volumeLabel, fellBack, capBytes
```

## Related code files
- Create: `android/core/playback/src/main/kotlin/CacheVolumes.kt` (+ `CacheVolumesTest.kt`) — copy
  from `plans/260921-1751-android-external-cache/phase-01-volumes-and-budget.md` Task 1
- Create: `android/core/playback/src/main/kotlin/CacheLocation.kt` (+ `CacheLocationTest.kt`)
- Create: `android/core/playback/src/main/kotlin/CacheVolumeSettings.kt` (+ test)
- Modify: `android/core/playback/src/main/kotlin/CacheProvider.kt` (dir + stale-dir deletion; `CACHE_DIR_NAME` moves to `CacheVolumes.kt`)
- Modify: `android/core/playback/src/test/kotlin/CacheProviderTest.kt`
- Modify: `android/feature/system/src/main/kotlin/CacheBudgetViewModel.kt` (`cacheBudgetChoices(cap)`, volumes, `chooseVolume`)
- Create: `android/ui-mobile/src/main/kotlin/ui/settings/CacheVolumeBlock.kt` (+ test)
- Create: `android/ui-mobile/src/main/kotlin/ui/settings/CacheSection.kt` (hosts budget + volume blocks, so `SettingsScreen.kt` shrinks)
- Modify: `android/ui-mobile/src/main/kotlin/ui/settings/SettingsScreen.kt`, `CacheBudgetBlock.kt`
- Modify: System screen cache rows (`SystemRows`/`SystemUiState`) — volume row plus fallback sentence

## Implementation steps (TDD)
1. **Tests before:** run `CacheProviderTest`, `CacheBudgetSettingsTest`,
   `AdjustableLruEvictorTest` and `CacheBudgetBlockTest` green and note the
   count. Add a `CacheProviderTest` asserting that today's cache dir is
   `cacheDir/mlib` and that data written survives `resetForTest()` plus a
   reopen. That locks in the no-choice-recorded path, which must remain
   the default.
2. `CacheVolumes`: follow old Task 1 steps 1–5 exactly (red, green, commit).
3. `CacheLocation` red tests: chosen present → that volume; chosen absent
   → internal with `fellBack=true`; nothing chosen → internal with
   `fellBack=false`; `budgetCap` = free + held − 1 GiB, floored at 512 MiB;
   `budgetLadder(cap)` doubles from 512 MiB, stops at ≤ cap, and always
   contains at least 512 MiB. Then implement.
4. `CacheVolumeSettings` red then green: default `null`, round trip, and
   the same prefs file as the budget without clobbering it.
5. `CacheProvider` red (Robolectric, fake volume list injected through an
   internal seam `volumesFor: (Context) -> List<CacheVolume>`):
   - Opens on the chosen external dir.
   - Chosen absent → internal, the setting is unchanged, and `occupancy().fellBack`.
   - Stale `mlib` on the other present volume is deleted only **after**
     the open succeeds, via `SimpleCache.delete`, off the `get()` path.
   - A chosen volume whose cache fails to initialise (a read-only dir)
     falls back to internal, and **internal is not deleted**.
   - A stored budget above the cap → the evictor gets the largest ladder
     step ≤ cap, and prefs still hold the original.
   Then implement, moving resolution into `CacheLocation.kt` to keep
   `CacheProvider.kt` under 200.
6. ViewModel and UI red: ladder derived from the cap; the volume row
   wording, including the fallback sentence and the restart sentence.
   Move blocks into `CacheSection.kt`, then implement.
7. **Regression gate:** `scripts/check.sh` with `ANDROID_HOME`, and run the
   step 1 tests unchanged.
8. **Device (Redmi):** Settings shows one volume, "Internal storage"; the
   ladder reaches its cap above 8 GiB; playback still caches (a second play
   records 0 fetches).
9. **Device (microSD phone / TV box, when available):** choose the card,
   restart, and confirm `mlib` is on the card. Pull the card, restart:
   System shows the fallback. Reinsert and restart: back on the card.

## Success criteria
- [x] All red tests are written before their implementation and pass after.
- [x] Redmi behaviour is unchanged apart from a larger ladder.
- [x] No manifest change.

## Risk assessment
- **Android may clear a large internal cache under storage pressure.**
  Apps over `getCacheQuotaBytes()` are cleared first. Red-team raised it;
  the user chose to keep "cap follows the volume" on internal storage too
  and accept the wipes (2026-09-25). Phase 0 keeps playback alive when it
  happens. Do not add a quota bound without asking.
- **Deleting the only good cache.** Guarded by the init check above, with a test.
- **Deleting the wrong dir.** Only `<volume cacheDir>/mlib` is ever
  deleted, and never the directory that was just opened. A test asserts a
  sibling file survives.
- **Label null on some OEMs.** Covered by the adapter's fallback labels.
- **TV-branch conflict** on `CacheBudgetBlock.kt` and `SettingsScreen.kt`:
  the second to land rebases.

## Review (2026-09-25)
- Device (Redmi tablet, 0.54.0 debug, downgrade-installed over the featured/paging build, data kept): Settings shows one volume, "Internal storage — 69 GB free"; ladder 512 MB…64 GB (cap 69 + 2.7 held − 1 ≈ 70.7 GB); stored 8 GB still selected; 2.7 GB held survived; System shows "Where: Internal storage"; cache still at `cache/mlib`.
- The playback half of step 8 (second play records 0 fetches) runs with phase 2's device check, under a test profile.
- Step 9 (card, pull, reinsert) is owed: no removable volume on the Redmi.
- The restart sentence is always shown, not only after choosing another volume. Kept that way, since one visible line costs little.
- Code review H1 (the sweep deleted the chosen card after a fallback) was fixed in `ad33ccc`, with tests.
