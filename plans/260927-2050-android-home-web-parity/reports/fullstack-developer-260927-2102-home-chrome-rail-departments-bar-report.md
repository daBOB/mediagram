# Phase Implementation Report

## Executed Phase
- Phase: phase-01-library-chrome-rail-departments-bar
- Plan: plans/260927-2050-android-home-web-parity
- Status: completed

## Files Modified

New (ui-mobile, `ui/chrome/`):
- `ChromeCounts.kt` (52) — pure `chromeCountsOf`/`profileInitial`, no Compose import
- `Wordmark.kt` (49) — shared wordmark, `fontSize` param for the compact/narrow-rail sizes
- `LibraryRail.kt` (176) — `RailItem`, `RailData`/`LocalRailData`, the rail itself
- `TopChrome.kt` (15) — `LocalTopChrome` seam
- `ChromeControls.kt` (114) — `Pill`, `ChromeAvatar`, `CircleIconButton`
- `DepartmentsBar.kt` (93) — EXPANDED pill bar, over-cover blend
- `CompactLibraryHeader.kt` (131) — ≤900px-equivalent header, three rows
- `LibraryHome.kt` (200) — ties rail/bar/header/seam/scroll-blend together

New (ui-mobile, `ui/`):
- `LibraryMenuBranch.kt` (54) — `MenuBranch`, split out of `LibraryFlowBranches`
- `LibraryTitleBranches.kt` (107) — `TitleFrame`/`CollectionFrame`, same split

New (core/designsystem res):
- `core_designsystem_ic_rail_{my_list,continue,latest,genres,settings}.xml`
- `core_designsystem_ic_search.xml`
(System reuses the existing `core_designsystem_ic_settings_system.xml` — its paths already match the web's rail System icon exactly.)

New tests:
- `ui-mobile/src/test/kotlin/ui/chrome/ChromeCountsTest.kt`
- `ui-mobile/src/test/kotlin/ui/chrome/LibraryRailTest.kt` (Robolectric, `w1164dp-h777dp`)

Modified:
- `ui/AppChrome.kt` — `LibraryScaffold` wraps in `Row(LibraryRail, content)` on EXPANDED; `railItemFor`/`railSelect` helpers
- `ui/OverflowMenu.kt` — added `AndroidOnlyMenu` (Update library/TMDB key/Start over only); existing full `OverflowMenu` untouched
- `ui/LibraryFlowBranches.kt` (325→255 lines) — `firstKept`-derived My List/Continue tab math, `RailData` computed once and provided via `CompositionLocalProvider`, root renders through `LibraryHome` instead of `LibraryScaffold`+`CatalogScreen`, `MENU`/`TITLE`/`COLLECTION` branches delegated to the two new files, `?: return` replaced with `?.let` (see Deviations)
- `ui/catalog/CatalogScreen.kt` (233→230) — `ShelfTabs` call removed; `visibleTabIndices` import/computation dropped from `Shelves` (still exported for `DepartmentsBar`)
- `ui/settings/SettingsIndex.kt` — inline wordmark replaced with `Wordmark()`
- Tests: `ui/MobileAppTest.kt`, `ui/LibraryFlowTest.kt` ("Mediagram" → "mediagram"); `ui/catalog/browse/OverflowUtilitiesTest.kt` rewritten for the trimmed menu + icon-based utilities

Deleted:
- `ui/catalog/ShelfTabs.kt`

Manifests / docs:
- `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` → 0.70.0 (versionCode 18→19)
- `docs/project-changelog.md` — new 0.70.0 entry
- Reference PNGs copied to `plans/260927-2050-android-home-web-parity/reports/reference/`
- Tablet screenshots in `plans/260927-2050-android-home-web-parity/reports/` (`tablet-landscape-home-{top,mid,lower}.png`, `tablet-portrait-home.png`)

## Tasks Completed
All phase Todo items ticked; see phase file.

## Decisions taken where the spec left room

1. **System's rail row is never gated.** The spec said "mirror whatever gates the overflow's System entry today"; grepped the whole module tree — nothing gates it (unlike the web's `offerSystem()` HEAD probe). The rail's System row is unconditional, same as the overflow item it replaces.
2. **Pushed frames keep the OLD, full `OverflowMenu` unchanged.** The plan's own "deliberate differences" already say pushed pages keep their own back bar; I read that as also keeping their own (unchanged, nine-item) overflow — the rail only joins as a *second pane* on EXPANDED. Only the root's new chrome (`DepartmentsBar`/`CompactLibraryHeader`) uses the trimmed `AndroidOnlyMenu`. This means "every destination the old overflow reached is still one tap away" holds trivially for pushed frames (nothing removed there) and holds for the root via the rail/icon-row.
3. **`RailData`/`LocalRailData` composition local**, computed once in `LibraryBranches` (shelves+watch → counts+tally, plus a `toCatalog()+tab=0` "go home" closure), read by both `LibraryHome` (root) and `LibraryScaffold` (pushed frames). Avoided threading counts/tally/onHome through `PersonFrame`/`FranchiseFrame`/`GenresFrame`/`LatestFrame`/`MoviesPageFrame`/`ResolvedBranch` — none of those files needed touching.
4. **`ChromeCounts` is Compose-free on purpose** (spec calls it "pure: unit-tested"); the composition-local wrapper (`RailData`) that also carries the wordmark's "go home" callback lives in `LibraryRail.kt` instead.
5. **`?: return` → `?.let {}`** in the four `LibraryBranches` sub-branches (PLAYER/MENU/SEARCH/GENRE) that used a bare early return. Those branches now sit inside `CompositionLocalProvider`'s content lambda; `let` is `inline`, a bare `return` there is not guaranteed to be. Verified compiled and behaves identically (nothing follows the `when` in the function body either way).
6. **Narrow-rail wordmark: 23sp, not 27sp.** Confirmed on-device: Android's Fraunces metrics run wider than the web's at the same nominal size, and "mediagram" wrapped to two lines inside the 184dp rail at 27sp (screenshot `state-01.png`/`state-02.png` in the scratchpad, not copied into reports/). Reused the compact header's own 23sp tier rather than inventing a third number; `Wordmark` also got `maxLines=1`/`overflow=Clip` as a backstop.
7. **Split `LibraryFlowBranches.kt`** (already 328 lines before this phase) three ways: `MenuBranch` (Settings/System/TmdbKey routing) and `TitleFrame`/`CollectionFrame` (the two biggest inline `ResolvedBranch` bodies) moved out, parallel to the existing `LibraryBrowseBranches.kt` split. Landed at 255 lines — reduced, not under 200; the remaining bulk is the dispatch table itself plus the tabs/counts/browse setup, which I judged not worth fragmenting further against the time budget. `LibraryHome.kt` landed at exactly 200.

## Deviations from the web (already named in plan.md, confirmed as-implemented)

- Android-only ⋮ (Update library, TMDB key…, Start over) beside the avatar.
- No backdrop blur behind the bar — a flatter opening alpha (`0x590A0A0B`, matching the web's own pre-blur value) stands in; the bar's own background/ink interpolate via `lerp` on nested-scroll offset against 40%/75% of the pane's viewport height (a `ponytail:`-flagged approximation — real scroll-position math is phase 02's job once the cover is real).
- Pushed pages keep their own back bar and full menu; the rail only joins beside them on EXPANDED.
- Phone/narrow-tablet header hides on scroll down, returns on scroll up (`CompactLibraryHome`'s own `NestedScrollConnection`, offsetting the header within `[-height, 0]`).

## Tests Status
- Type check / compile: pass (`:ui-mobile:compileDebugKotlin`, `:core:designsystem:compileDebugKotlin`, `:ui-tv` unaffected)
- Unit tests: pass — `:ui-mobile:testDebugUnitTest :feature:catalog:testDebugUnitTest :ui-tv:testDebugUnitTest` all green (ui-tv untouched, confirmed still passing)
- `:app:assembleDebug`: pass
- Integration: installed on tablet (`caad49da`, `ANDROID_SERIAL` pinned throughout, no MIUI install prompt appeared), verified on the real "test" profile

## Screenshots
`plans/260927-2050-android-home-web-parity/reports/`:
- `tablet-landscape-home-top.png` — rail + translucent pill bar over the cover, matches `home-web-1.png`
- `tablet-landscape-home-mid.png` — bar mid-blend, Continue watching row visible
- `tablet-landscape-home-lower.png` — bar fully solid, matches `home-web-3.png`'s Recently added/Latest series area
- `tablet-portrait-home.png` — compact header (wordmark + icon row, department pills, search field look-alike + avatar + ⋮), matches `home-web-phone-*.png`'s shape

## Issues Encountered
- One real bug found and fixed only via the on-device check (not caught by Robolectric, whose default test fonts/metrics didn't reproduce it): the narrow-rail wordmark wrapped to two lines. Fixed as decision #6 above. This is exactly why the phase asked for a real install rather than trusting the emulated tests alone.
- `core_designsystem_ic_search.xml`'s first comment draft used an em-dash-adjacent `--icon-search` inside an XML comment, which AAPT rejects (`"--" not permitted within comments`) — reworded, not a design change.

## Next Steps
- Phase 02 draws the Home body (cover/features/bands/shelves) under `LocalTopChrome`'s seam and can read the real scroll position once `CoverStory` owns its own `LazyGridState`.
- Not committed, per instructions — worktree left with the diff for review.

## Unresolved Questions
- None blocking. Worth a second look in phase 02: the over-cover blend's 40%/75% thresholds are read against the *pane's* viewport height (`BoxWithConstraints.maxHeight`), not the cover's own measured height — reasonable now since Home's cover today is exactly `CoverStory`'s own height-ambiguous grid item, but phase 02 may want to key the blend off the cover's actual bottom edge instead.
