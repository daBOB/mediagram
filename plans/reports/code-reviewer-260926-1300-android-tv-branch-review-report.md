# Code review: feat/android-tv-ui vs main (android/, crates/, web/)

Date 2026-09-26. Scope: `git diff main...HEAD -- android/ crates/ web/` (11 commits, 45 files, +1854/-207).
Only android/ changed. Tests run: `:ui-tv:testDebugUnitTest` (TvMenuTest, TvTitlePageStateTest,
TvListStateTest, TvCatalogScreenStateTest) and `:feature:system:testDebugUnitTest`. All pass.

## Overall

Solid. Focus plumbing is careful: the `LaunchedEffect` after the early return in
`TvLanCacheBlock` is right (it runs on the first composition that has rows), the
menu/Back layering (TvMenuPage -> MENU frame -> Settings panel BackHandlers) resolves
innermost-first as intended, and phone call sites still compile after the
`setManualAddress`/`saveToken` Boolean change (callable-reference Unit coercion at
`LanCacheBlock.kt:59`). Phone refactors (`catalogueRows`, `thisAppRows`,
`lanCacheStatusLine` moved to feature/system) are behaviour-neutral; the moved tests
moved with them. No critical findings.

## Major

### M1. The benchmark build is not "a release build in everything that decides how fast it runs"
`android/app/build.gradle.kts:18` has release `isMinifyEnabled = false`; benchmark (`:33-40`)
sets `isMinifyEnabled = true` + `isShrinkResources = true`.
- Failure: frame timings measured on the box with `installBenchmark` come from an R8-optimised
  build. The release variant ships unminified, so it is slower than what was measured (R8 matters a
  lot for Compose). A perf fix "verified on benchmark" may not show up in release. The comment at
  `:24-32` states the opposite.
- Also: the new JNA/UniFFI keep rules (`proguard-rules.pro:271-285`) have only been exercised by
  the benchmark walk. Release never runs R8 at all.
- Fix: pick one. (a) Set release `isMinifyEnabled = true` / `isShrinkResources = true` now that the
  keep rules exist and a device walk passed on the benchmark build. Or (b) reword the comment to say
  benchmark is minified and release is not, and treat its numbers as an upper bound.
- R8 risk check, done: no `@Serializable` classes, no `getIdentifier`/`ServiceLoader`, Hilt and Room
  have rules, JNA `Structure`/`Callback` subclasses live in `uniffi.mediagram_core` (kept). No
  reflection target found that the rules miss.

### M2. On TV, the keyboard's action key on an empty TMDB field silently deletes the stored key
`ui-tv/.../system/TvTmdbKeyScreen.kt:30-33`. `onSubmit` calls `onSave(key)` with `key == ""`, and
`FetchViewModel.saveKey("")` writes a blank key (the "blank clears" rule).
- Failure: a viewer opens "TMDB key…" only to check whether a key is stored. The field takes focus
  and the IME comes up. They press Done/Enter to dismiss it, which is the IME action. The key is
  wiped with no confirmation. The next Update library quietly skips artwork ("need a TMDB key"). On
  the phone the same rule needs a deliberate tap on a separate Save button. On TV the only answer
  key is also the usual way to close the keyboard. This is a parity divergence in *risk*, not in
  wording.
- Fix: in `TvTmdbKeyScreen`, ignore a blank submit (`if (key.isNotBlank()) onSave(key)`). Offer
  clearing as its own row ("Clear stored key") or behind `TvConfirmDialog`. Write the deliberate
  difference down in the doc comment.

## Minor

### m1. Stale LAN-cache validation error shown when a panel is reopened
`feature/system/.../LanCacheViewModel.kt:76-110`, `ui-tv/.../system/TvLanCachePanel.kt:38,57`.
`_addressError`/`_tokenError` are only cleared on a *successful* save. `open()` does not reset
them. On TV the VM is activity-scoped (no NavHost), so they outlive the panel and Settings itself.
- Failure: type `a b`, submit ("Could not understand that address."), press Back. Reopen Server
  address: the field is reset to the stored value, yet the old error still sits under it. Leaving
  Settings and coming back later still shows it. The same applies to the token.
- Fix: add `fun clearErrors()` in the VM (set both flows to null). Call it from
  `TvSettingsScreen`'s `open` for Lan panels, next to `viewModel.clearNotice()`, or from
  `LanCacheViewModel.open()`.

### m2. A focused row that disappears drops the remote to nowhere
- `TvLanCacheBlock.kt:66-68`: the "Grant local network access" row disappears once permission is
  granted (connection leaves NEEDS_PERMISSION). The remote was on it, since it just launched the
  prompt.
- `TvCacheBudgetBlock.kt:43-46`: "Try again" disappears once the retry succeeds.
- Failure: nothing is focused. The next D-pad press lands on the first focusable (top of Settings),
  away from the viewer's place. API 37+ only for the first. Any failed cache read for the second.
- Fix: before the row goes, move focus to a stable neighbour (the "Use the home cache server" row,
  or the first budget row) with a `FocusRequester` in a `LaunchedEffect` keyed on the condition.

### m3. Test gaps against their names/claims
- `TvMenuTest.updateLibraryGoesToTheShelves` (`TvMenuTest.kt:156-163`) checks only that the menu
  page closed. It never checks that an update was started, which is the item's whole point. Deleting
  `catalogViewModel.update()` from `TvMenuBranches.kt:244` would still pass. Add a verify on the
  fixture's repository refresh (or assert the "Updating…" line appears).
- `TvMenuTest.aRefusedTokenKeepsItsQuestionOpen` uses a relaxed-mock VM, so it proves the panel
  stays open but not that the refusal reason is shown. That is fine for the name, but no test
  covers the `error = state?.tokenError` wiring.

### m4. Five files crossed the 200-line rule on this branch
`TvLibrary.kt` 205, `catalog/TvCatalogScreen.kt` 217, `catalog/TvMasthead.kt` 225,
`catalog/TvPlate.kt` 237, `profile/TvProfilePicker.kt` 209 (all under 200 on main's base).
Cheap splits: `TvPlateInitials` + constants into `TvPlateInitials.kt`; the masthead entry keys and
`apartOnMasthead`/`leadingRule` into a small file; the `null if menuOpen` branch body in TvLibrary is
already delegated, so moving `CatalogStateKey` + the fetch-dialog block out would do.

### m5. Version will be behind at merge
Branch manifests are 0.59.0. `main` is at 0.60.0 (`6aa40b3e chore: release 0.60.0`). This branch
adds features (TV System/Settings/menu), so per CLAUDE.md § Versioning bump all three
(`Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` versionName) to 0.61.0 when
merging. A pattern-based bump, not exact-string (main already moved).

## Checked, no issue

- `TvLanCacheBlock` focus restore after panel close. Same VM instance while the panel collects it
  (WhileSubscribed), so state is non-null on return. Covered by
  `settingsShowsTheHomeCacheServerAndAnAcceptedAddressReturnsToItsRow`.
- `TvCatalogScreen` `LaunchedEffect(backFromMenu)` after the `ready == null` masthead effect. Order
  is correct. Forgetting the key does not re-trigger the wall (TvWall's effect keys on
  `focusIndex, restoreKey`, not `takesFocus`).
- `TvList` `takesArrivalFocus = !backFromPlayAll || afterRemoval != null`. `afterRemoval` is set
  before `onRemove`, and the test drives the real path.
- `TvSystemContent` effect keys `(failure != null, current != null)`: retry success/failure both
  re-key and land correctly.
- `TvPlateInitials` `drawWithCache`: a new lambda per recomposition invalidates the cache on
  theme/title change. Semantics text preserved.
- `TvWall` `contentType = { it::class }` and cache window: fine. `null if menuOpen` guard is stable
  in Kotlin 2.3.21.
- Plan-reference comments: none found in added lines.
- Surface parity: TV Settings = phone Settings (Telegram block, 3 actions, budget, where, LAN
  block). System = phone's four blocks from the same row builders. Menu = `MenuActions` order/words.
  Web-only stats on main are recorded as owed in main's docs.

**Status:** DONE_WITH_CONCERNS
**Summary:** No critical defects. Two majors: the benchmark build is minified while release is not,
so perf numbers and R8 coverage don't transfer to release. And on TV an empty IME submit silently
clears the stored TMDB key. Five minors: stale LAN errors, focus loss on vanishing rows, a test that
doesn't check the update ran, 200-line overruns, version bump at merge.
**Concerns/Blockers:** M1 needs a user decision (minify release vs. reword). M2 changes a
documented "phone's rule", so confirm before diverging.
