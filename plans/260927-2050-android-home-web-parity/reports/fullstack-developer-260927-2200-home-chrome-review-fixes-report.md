# Review fixes — library chrome: rail + departments bar

Fixes for `code-reviewer-260927-2145-home-chrome-review-report.md`, same worktree, same uncommitted diff.

## High

- **H1 (state lost on width-class switch).** `LibraryScaffold` (`AppChrome.kt`) and `LibraryHome.kt` each now have exactly one call site for `content()`, with only the rail (`if (expanded) LibraryRail(...)`) conditional beside it. Caught a second instance of the same defect I had introduced myself while fixing H2 (the compact/expanded content padding was still two separate `if/else` calls to `content()`) — the new `rootKeepsScrollAcrossWidthClass` test failed on the first run and pointed straight at it; fixed by computing the padding/layout `Modifier` conditionally and calling `content()` once. Verified on-device: scrolled deep into Home landscape, rotated to portrait — same row (`Boardwalk Empire`/`Latest courses`) still on screen (`tablet-portrait-after-rotate-h1-fix.png`).
- **H2 (hidden compact header leaves a blank band).** Content now measured and placed via `Modifier.layout {}` at `(headerHeightPx + headerOffsetPx)`, in the layout phase, instead of a plain top padding fixed at the header's full height. Verified on-device: swiped up in portrait, header fully off-screen, status bar icons now sit directly over the top content row (`tablet-portrait-header-hidden-h2-fix.png`).

## Medium

- **M1 (blend from scroll deltas, not position).** Extracted the formula into a pure `coverBlend(firstVisibleItemIndex, firstVisibleItemScrollOffset, viewportPx): Float` (`ui/chrome/CoverBlend.kt`), unit-tested (`CoverBlendTest`, 5 cases). `LibraryHome` reads it via `derivedStateOf` off a `LazyGridState` hoisted from `LibraryFlowBranches` through `CatalogScreen`/`HomeScreen` (new `homeGridState`/`now` params on both, `gridState` on `HomeScreen`, threaded into `LazyVerticalGrid`). `now` is shared between the `hasCover` check in `LibraryFlowBranches` and `Shelves`' own `magazineHomeOf` call so the two can never pick different editorial sets.
- **M2 (Home draws under the bar with nothing to bleed over).** `LibraryHome` now bleeds only when `expanded && isHome && hasCover`; `hasCover` comes from the same `magazineHomeOf(...).editorial.cover.isNotEmpty()` check. `CatalogScreen`'s progress indicator and notice row pad by `LocalTopChrome.current` (0 except while bleeding).
- **M3 (lost system-bar insets).** Content pads `navigationBars` (bottom) in `LibraryHome`. `DepartmentsBar` and `CompactLibraryHeader` add `safeDrawing` end/horizontal insets alongside `statusBars`. `LibraryRail` narrowed its own `safeDrawing` padding to start+top+bottom only. Verified on-device: scrolled Home to its last row in landscape — clear of the 3-button nav bar with room to spare (`tablet-landscape-navbar-inset.png`).
- **M4 (no selected semantics).** `Pill`, `RailRow` and the compact header's `CircleIconButton` (new optional `selected` param) all use `Modifier.selectable(selected, onClick, role = Role.Tab)` in place of `clickable`, the same pattern `SettingsIndex` already uses.

## Low

- L1: `LocalRailData` → `compositionLocalOf`.
- L2: `LocalTopChrome` is 0 wherever content is already padded clear of the chrome; only nonzero while actually bleeding. KDoc rewritten to say so.
- L3: `selectedPill = visible.indexOf(chosenTab)` (no `.coerceAtLeast(0)`) — `-1` on a kept wall means no pill reads as selected.
- L4 decision (applied as instructed): on EXPANDED, a pushed frame's own bar now shows the avatar + trimmed `AndroidOnlyMenu`, matching the root — the rail beside it carries the rest. Compact/medium pushed frames keep the full `OverflowMenu` unchanged, since nothing else reaches those six destinations there. `Pill` now has a 44dp `heightIn(min=)`. Tally uses a `HorizontalDivider(color = tones.ruleSoft)` plus the web's own 10sp/0.34em/2× line-height style. "Continue watching" no longer wraps in the 184dp rail (`maxLines=1`, `TextOverflow.Ellipsis` — matches the web's `white-space:nowrap` intent; visually reads "Continue …" at that width, screenshot `tablet-landscape-navbar-inset.png`).
- L5: narrow-phone icon row gets a 24dp trailing fade (`fadeTrailingEdge`) as a scroll-affordance hint, drawn unconditionally rather than only when actually overflowing.
- L6: removed both plan references (`LibraryHome.kt`'s old `ponytail:` comment, `DepartmentsBar.kt`'s "see the plan's own note"); fixed the `?.let` comment's wrong compile claim; fixed `AppChrome.kt`'s stale "five items"/"every non-player screen" doc and the "joins Settings/System's own index" claim (Settings/System never render with the rail — confirmed in the review's own "Checked and fine"); fixed `CatalogScreen`'s `Shelves` KDoc; fixed the changelog entry.
- L7: `AndroidOnlyItems` shared between `OverflowMenu` and `AndroidOnlyMenu` (`OverflowMenu.kt`).
- L8: `LibraryFlowBranches.kt` 278, `CatalogScreen.kt` 247 — grew further from the M1/M2 plumbing; not split further given the remaining time budget (both already over 200 before this phase). `LibraryHome.kt` brought back under 200 (196) by extracting `CoverBlend.kt`.
- L9: added `CoverBlendTest` (5 cases), `WidthClassStateTest` (adapted probe — `pushedFrameKeepsScrollAcrossWidthClass`, `rootKeepsScrollAcrossWidthClass`, `compactHeaderHidingLeavesContentWhereItWas`), and to `LibraryRailTest`: `theAvatarReopensTheProfileChooser`, `theBarsSearchIconOpensSearchFromTheRoot`.

## Gate

`./gradlew :ui-mobile:testDebugUnitTest :feature:catalog:testDebugUnitTest :ui-tv:testDebugUnitTest lint :app:assembleDebug` green. Installed on `caad49da` (test profile, no install prompt), rotated landscape↔portrait on a scrolled Home page (H1), scrolled to hide the compact header (H2), scrolled to the end of a list in landscape (M3) — all confirmed on-device, screenshots in this directory. Not committed, per instructions.

## Files touched beyond the original diff

`ui/chrome/CoverBlend.kt` (new), `ui/catalog/HomeScreen.kt` (new `gridState` param — outside the phase's original file list; done per the coordinator's explicit M1 instruction to hoist the grid state), `ui/chrome/CoverBlendTest.kt`, `ui/chrome/WidthClassStateTest.kt` (new tests). Everything else already listed in the original report's Scope.
