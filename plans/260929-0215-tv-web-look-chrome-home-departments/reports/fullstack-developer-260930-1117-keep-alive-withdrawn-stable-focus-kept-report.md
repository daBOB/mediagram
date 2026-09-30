# Phase 04 — keep-alive withdrawn; stable per-card focus kept

## Context

User decision after five box rounds (0.84.1–0.84.5): drop the kept-alive
Home layer, keep the useful fixes. Box result on `6d440458` (0.84.5): the
cold-start first title trip still lands on the Home pill — the recovery
rule doesn't apply there (the Back press closing the title page is itself a
key event, and content didn't hold focus just before), and the
`PinnableContainer` pin didn't prevent the reset either. Coordinator asked
for keep-alive's removal back to `11a67b25` (phase 03), `9affd36e`'s stable
per-card focus requesters kept, docs updated, three tests un-ignored,
0.84.6.

## Method

`11a67b25` (phase 03 close, before keep-alive) and `9affd36e` (the stable-
focus-requester fix, landed mid-keep-alive) are both in this branch's own
history. For every file `9affd36e` touched, checked whether a keep-alive
commit (`b682b204`, `4fa10823`, `13cc3130`, `6d440458`) also touched it:

- **Untouched by keep-alive** (`TvTextRow.kt`, `TvCastRow.kt`,
  `TvCollectionRows.kt`, `TvCollectionsPage.kt`, `TvDepartmentRows.kt`,
  `TvSearchRows.kt`, `TvSimilarRow.kt`, `TvCourseList.kt`,
  `TvHomeFeatures.kt`, `TvPosterStrip.kt`, `TvRecentBand.kt`,
  `TvResumeCard.kt`, `TvDepartmentsBar.kt`, `TvSettingsChoices.kt`,
  `TvProfileTiles.kt`, `TvAppearanceBlock.kt`, `TvHomeStateTest.kt`) — reset
  straight to `9affd36e`'s own version; nothing keep-alive-related to
  remove from them.
- **Touched by both** (`TvWall.kt`) — checked out to `11a67b25`, then
  `git diff 13cc3130 9affd36e -- TvWall.kt` isolated the stable-requester
  hunk on its own (adding `ownRequester`, always attaching a
  `focusRequester`) from any keep-alive-era surrounding changes, applied by
  hand.
- **`TvHome.kt`** — `9affd36e`'s only change to it was removing 0.84.3's
  three-frame safety net; nothing to keep once keep-alive itself is gone.
  Reset straight to `11a67b25`.
- **Everything else keep-alive touched and `9affd36e` never did**
  (`TvHomeLayer.kt`, `TvHomeKeptAliveTest.kt` — deleted, new files with no
  `11a67b25` equivalent; `TvLibrary.kt`, `TvLibraryBranches.kt`,
  `TvCatalogNav.kt`, `TvCatalogScreen.kt`, `TvKeptWall.kt`, `TvLists.kt`,
  `TvHomeCover.kt`, `TvLibraryChrome.kt`, `TvArrivalFocus.kt`, `DESIGN.md`,
  `docs/system-architecture.md`, and the five 0.84.5-only test files) —
  reset straight to `11a67b25`.

Verified after: `git diff 11a67b25 -- <path>` empty for every straight
reset; the `TvWall.kt` diff against `11a67b25` shows only the isolated
stable-requester hunk; a repo-wide grep for
`LocalLibraryCovered|TvHomeLayer|coveredLayer|heldWhile|rememberArrivalReady|HomeDepth|barRequested|requestBarFocus|PinnableContainer`
under `android/ui-tv/src` returns nothing.

## HomeDepth

Removed with `TvLibrary.kt`'s revert. It existed only to fix restore keys
reading the wrong depth once Home stayed composed under a pushed frame
(`4fa10823`) — with Home unmounting again, the position stack's own top is
always the catalogue's own depth again, the bug it fixed cannot recur, and
nothing else referenced it.

## Un-ignored tests

`TvSearchAndGenreTest.searchOpensOnItsFieldAndBackReturnsToTheMastheadEntry`,
`TvSearchAndGenreTest.downFromTheMastheadsSearchLandsOnHomesFirstStop`,
`TvMenuTest.theMenuIsTheAndroidOnlyRowsInThePhonesOrderAndBackReturnsToTheBarsMenuButton`
— all three files reset to `11a67b25` (no `@Ignore`, `back()` back to
calling `onBackPressedDispatcher.onBackPressed()` directly), so they run
unmodified and pass with `TvLibraryChrome.kt`'s own generic recovery rule
gone; nothing left racing them.

## Docs

- `DESIGN.md`, `docs/system-architecture.md` — reset to `11a67b25`, which
  predates any kept-alive description; nothing to strip.
- `docs/system-architecture.md` § Television differs — new bullet: what was
  tried, the five Compose mechanisms chased in order (frame removal,
  startup-refresh reset, modifier-presence toggle, lazy deactivation, the
  cold-start case neither the pin nor the recovery rule covered), why
  withdrawn, and the benchmark's 0 `Davey!`/`Choreographer … Skipped` on
  return (the coordinator's own box number). Every other TV difference the
  phase listed is unchanged.
- `plans/260929-0215-tv-web-look-chrome-home-departments/phase-04-tv-home-kept-alive-measure-docs.md`
  — `status: withdrawn`, a "Withdrawn (2026-09-30)" section with the full
  account, and the Todo list annotated rather than left with misleading
  open boxes.
- `plans/260929-0215-tv-web-look-chrome-home-departments/plan.md` — the
  "Home survives a round trip" locked decision amended to say restore keys
  do the work again, keep-alive tried and withdrawn; phase 04's row in the
  phase table marked withdrawn.
- `docs/project-changelog.md` — new 0.84.6 entry: what ships (stable card
  focus across reorders, kept) and what was withdrawn (keep-alive, with the
  five-mechanism summary). 0.84.1–0.84.5's own entries are untouched —
  changelog history, not rewritten.

## Files Modified

- Deleted: `android/ui-tv/src/main/kotlin/ui/tv/TvHomeLayer.kt`,
  `android/ui-tv/src/test/kotlin/ui/tv/TvHomeKeptAliveTest.kt`.
- Reset to `11a67b25`: `DESIGN.md`, `docs/system-architecture.md` (then
  amended), `android/ui-tv/src/main/kotlin/ui/tv/{TvLibrary,TvLibraryBranches}.kt`,
  `android/ui-tv/src/main/kotlin/ui/tv/catalog/{TvArrivalFocus,TvCatalogNav,TvCatalogScreen,TvHome,TvKeptWall,TvLists}.kt`,
  `android/ui-tv/src/main/kotlin/ui/tv/catalog/home/TvHomeCover.kt`,
  `android/ui-tv/src/main/kotlin/ui/tv/chrome/TvLibraryChrome.kt`,
  `android/ui-tv/src/test/kotlin/ui/tv/{TvLibraryTest,TvMenuTest,TvSearchAndGenreTest}.kt`,
  `android/ui-tv/src/test/kotlin/ui/tv/catalog/{TvCatalogScreenStateTest,TvKeptWallStateTest,TvScreenStateTest}.kt`,
  `android/ui-tv/src/test/kotlin/ui/tv/chrome/TvLibraryChromeUpTest.kt`.
- Reset to `9affd36e`: the 16 card/row files plus `TvHomeStateTest.kt`
  listed under Method above.
- Hand-merged: `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvWall.kt`
  (`11a67b25` base + the isolated stable-requester hunk).
- New content: `docs/project-changelog.md` (0.84.6 entry),
  `plans/260929-0215-tv-web-look-chrome-home-departments/{plan.md,phase-04-tv-home-kept-alive-measure-docs.md}`.
- Version: `Cargo.toml`, `Cargo.lock` (5 workspace crates), `web/package.json`,
  `android/app/build.gradle.kts` — 0.84.5 → 0.84.6.

## Tests Status

- `./gradlew :ui-tv:compileDebugKotlin` / `compileDebugUnitTestKotlin` —
  clean.
- `./gradlew :ui-tv:testDebugUnitTest` — BUILD SUCCESSFUL, no `@Ignore`
  anywhere under `android/ui-tv/src/test`.
- `scripts/check.sh` — all checks passed.
- `cargo metadata --locked --offline` — OK (Cargo.lock consistent).

## Unresolved Questions

None — the coordinator's own five numbered instructions are all addressed;
box verification (cold start, second-card play, cover play, Search/⋮
returns) is explicitly the coordinator's own next step, not mine.
