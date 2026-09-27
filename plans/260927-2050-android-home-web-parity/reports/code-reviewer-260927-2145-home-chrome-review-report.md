# Code review — library chrome: rail + departments bar (0.70.0, uncommitted on 225b3bd6)

## Scope
- Worktree `/home/andre/Workspace/mediagram-home`, branch `feat/android-home-web-parity`, diff vs `225b3bd6`
- Files: `ui/AppChrome.kt`, `ui/LibraryFlowBranches.kt`, `ui/LibraryMenuBranch.kt`, `ui/LibraryTitleBranches.kt`, `ui/OverflowMenu.kt`, `ui/catalog/CatalogScreen.kt`, `ui/settings/SettingsIndex.kt`, `ui/chrome/*` (8 files), 6 drawables, 3 tests changed, 2 tests added. ShelfTabs deleted.
- Gate: `./gradlew :ui-mobile:testDebugUnitTest :feature:catalog:testDebugUnitTest :ui-tv:testDebugUnitTest lint :app:assembleDebug` succeeded. The three test tasks were UP-TO-DATE, meaning the same inputs had already passed. Lint reports no errors; it adds 4 `ConfigurationScreenWidthHeight` warnings in the new chrome files.
- Probes: `ProbeWidthSwitchStateTest` ran in a scratchpad copy of the worktree, not in the worktree. It fakes `LocalWindowInfo` to switch the width class in the middle of a test.

## Overall
Everything the spec asks for is present: rail items and counts, pill bar, avatar with "Who's watching: $name", the trimmed ⋮, kept-tab indices derived from `firstKept`, the rail beside pushed frames, and no rail on the player or Settings/System. The TV contracts are unchanged: feature/catalog, ui-common and ui-tv have no diff, and designsystem only gains new drawables. The branch moves are faithful. The defects are in layout and state. Two High findings are confirmed by probes, and two Medium findings are confirmed by the implementer's own screenshots.

## High

### H1 — Moving between EXPANDED and MEDIUM throws away all page state
`AppChrome.kt:138-144` (`if (expanded) Row { Rail; Box { bar() } } else bar()`) and `chrome/LibraryHome.kt:80-96` (`ExpandedLibraryHome` vs `CompactLibraryHome` each call `content()`).
The manifest handles `orientation|screenSize`, so rotating does not recreate the activity. The content simply moves to a different slot in the composition, and every `remember`/`rememberSaveable` under it is lost.
- Scenario: rotate the Redmi Pad (landscape 1164dp is EXPANDED, portrait 777dp is MEDIUM) while on a title page scrolled down, or on the Movies/Latest grid, or on Home. The page jumps back to the top. Title info is fetched again. Anything typed in a field held with rememberSaveable is lost.
- Probe: scrolled to item 50, then switched the width class. The new code ends at 0 for pushed frames and 0 for the root, even though the root sits under the `SaveableStateProvider`. Against the base `LibraryScaffold` the pushed frame stays at 50.
- Fix (the probe confirms it keeps 50): keep `content()` at one call site and make only the rail conditional:
  ```kotlin
  Row(Modifier.fillMaxSize()) {
      if (expanded) LibraryRail(...)
      Box(Modifier.weight(1f).fillMaxHeight()) { bar() }
  }
  ```
  Do the same in `LibraryHome`: one `Row { if (expanded) Rail; Box { Box(pad) { content() }; if (expanded) DepartmentsBar(...) else CompactLibraryHeader(...) } }`, and choose the nested-scroll connection by width.

### H2 — Hiding the compact header frees no space; it leaves an empty band
`chrome/LibraryHome.kt:184-186`. The content is always padded by the full `headerHeightPx`, and only the header is moved off screen.
- Scenario: phone portrait, or tablet portrait, scrolling any department. The three-row header slides away and leaves blank paper of the same height (about a third of a 777dp window). Content never moves into it. In a short list the drag still hides the header and nothing fills the gap.
- Probe at 400dp: the list's top is 256dp before and after the swipe, while the wordmark moves from 78dp to -178dp.
- Fix: place the content at the header's visible height, and do it in the layout phase so the header offset is not read during composition:
  ```kotlin
  Box(Modifier.layout { m, c ->
      val top = (headerHeightPx + headerOffsetPx.roundToInt()).coerceAtLeast(0)
      val p = m.measure(c.copy(minHeight = 0, maxHeight = (c.maxHeight - top).coerceAtLeast(0)))
      layout(c.maxWidth, c.maxHeight) { p.place(0, top) }
  }) { content() }
  ```
  The alternative is to draw the content under the header and have the lists add `LocalTopChrome` to their top `contentPadding`.

## Medium

### M1 — The bar's see-through state is driven by scroll deltas, not by where the page actually is
`chrome/LibraryHome.kt:126-139`. `blend` walks up and down with each nested-scroll delta; it never reads the actual scroll position. It is also held with `remember(isHome)`, so it goes back to 0 whenever `LibraryHome` is created again. The spec said to hoist the Home list's `LazyGridState` for this; that was not done. This is a correctness problem, not the precision limit the `ponytail:` comment describes.
- Scenario A: scroll Home several screens down, open a title, press back. The grid comes back at the deep position (the shelves holder restores it), but the bar is see-through: pale pill text over the shelves.
- Scenario B: from anywhere deep in the page, scroll up about 35% of the viewport. The bar turns see-through far below the cover.
- Scenario C: rotation, which also runs into H1.
- Fix: hoist the Home `LazyGridState` (pass it into `HomeScreen`). Then compute `blend = if (firstVisibleItemIndex > 0) 1f else ((firstVisibleItemScrollOffset - start) / (end - start)).coerceIn(0f, 1f)` with `derivedStateOf`. Read it only when drawing (`drawBehind`/a color lambda) so the bar does not recompose on every scroll frame.

### M2 — On EXPANDED, Home puts things under the bar that nothing lets through
`chrome/LibraryHome.kt:143` passes `padding(top = 0)` whenever `isHome`, but nothing on Home reads `LocalTopChrome`.
- The `LinearProgressIndicator` and the `state.notice` line in `CatalogScreen.kt:140-153` end up under the status bar and the pill bar. Scenario: tablet landscape, Home tab (the default), ⋮ → Update library. The progress line is invisible. If the update fails, "Could not read the library. Try again." is drawn under the pills.
- `HomeScreen.kt:68` draws the cover only when there are films with backdrops. With no backdrops (a new library before any TMDB fetch, or a kids profile) the first section — features, "Continue watching" or "Recently added" — starts under the bar. The bar is in its see-through state there too: pale text over paper.
- Fix: draw under the bar only when a cover is actually drawn. Otherwise pad Home like every other department, and give the progress/notice rows `padding(top = LocalTopChrome.current)` on Home.

### M3 — The root library lost its system-bar insets
Before this change, Material `Scaffold` padded the content by `WindowInsets.systemBars` (the bottom navigation bar and side bars). `LibraryHome` pads only for the status bar.
- The tablet screenshots (`tablet-portrait-home.png`, `tablet-landscape-home-lower.png`) show the 3-button nav bar drawn over the content. At the end of the list, `HomeScreen`'s 16dp bottom padding is less than the ~48dp nav bar, so the bottom of the last row can never be scrolled clear. The walls, the Movies page and Collections have the same problem.
- Phone in landscape at 840dp or wider (EXPANDED) with the nav bar on the right: `DepartmentsBar.kt:72` pads only for the status bar, so the avatar and ⋮ sit under the nav bar (the gutter is about 29dp, the bar about 48dp). Meanwhile `LibraryRail.kt:93` applies all of `safeDrawing`, so the rail on the left gets a 48dp right inset and its labels wrap.
- Fix: add bottom `navigationBars` padding to the content Box, or have the lists add it to their `contentPadding`. Use `statusBars` plus the end-side `safeDrawing` for the bar and header. Use `safeDrawing.only(Start + Vertical)` for the rail.

### M4 — Accessibility: the department and rail controls no longer report which one is selected
The deleted `ShelfTabs` used Material `Tab`, which provides `selected` and `Role.Tab`. `Pill` (`ChromeControls.kt:100-104`), `RailRow` (`LibraryRail.kt:149-164`) and the icon-row `CircleIconButton` (`CompactLibraryHeader.kt:84`) provide neither. Only the colour changes. TalkBack reads "Movies 935" with no "selected", and the web does set `aria-current` (`app.js:593`).
- Fix: `SettingsIndex.kt:135` already does this. Use `Modifier.selectable(selected = active, onClick = …, role = Role.Tab)` in place of `clickable` on the pills, the rail rows and the icon row.

## Low
- **L1 — `LocalRailData` is a `staticCompositionLocalOf` and gets a new value on every `watch` change** (`LibraryRail.kt:61`, `LibraryFlowBranches.kt:84-89`). The player frame sits under the provider, so every progress write recomposes the whole `PlayerScreen` subtree without skipping. Fix: switch to `compositionLocalOf`.
- **L2 — It is unclear what `LocalTopChrome` means.** It carries the full chrome height even where the layout already pads the content (EXPANDED non-Home, all of compact). Its KDoc tells consumers to "pad below it", which would pad twice once phase 02 reads it. Fix: provide 0 wherever the layout has already padded, and describe it as "how much of this content the chrome overlaps".
- **L3 — On the My List or Continue wall, the Home pill still shows as selected** (`LibraryHome.kt:71`: `indexOf` returns -1, which becomes 0). The Home pill and the rail row then look selected together. Fix: `selectedPill = visible.indexOf(chosenTab)`, where -1 means no pill is selected.
- **L4 — Differences from the spec.** These were Claude's choices, not the user's.
  - Pushed frames on EXPANDED keep the full nine-item ⋮, which repeats the rail. The spec says those items leave the menu on EXPANDED.
  - Pushed frames still show the name `TextButton` where the root shows the avatar.
  - `Pill` measures 40dp tall; the spec says a 44dp minimum.
  - The tally has no ruleSoft line and uses `labelSmall` instead of 10sp with 0.34em tracking.
  - "Continue watching" wraps to two lines in the 184dp rail (landscape screenshot).
- **L5 — On a phone narrower than about 440dp, the compact icon row cuts off Settings/System** without any sign that the row scrolls. This is an estimate: 412 − 32 − ~120 (wordmark) − 8 = ~252dp available, and 6×44 + 5×4 = 284dp needed. Reaching them takes a sideways swipe; tests reach them with `performScrollTo`.
- **L6 — Comments.**
  - Plan references in code: `LibraryHome.kt:122` ("phase two's real cover") and `DepartmentsBar.kt:31` ("see the plan's own note on why"). The plan asks for the reason to live in the code, e.g. "Compose cannot blur what is behind a node without a new dependency".
  - `LibraryFlowBranches.kt:93-97` is wrong. A bare `return` in a non-inline lambda does not compile, and the old `?: return` did leave the whole function, so behaviour is unchanged.
  - `AppChrome.kt:135-136` and the changelog say the rail "joins Settings/System's own index". It does not; both render without it.
  - Stale KDoc: `AppChrome.kt:46-50` ("five items", "every non-player screen renders through this") and the `Shelves` KDoc in `CatalogScreen.kt` ("reached from the overflow menu").
- **L7 — Duplicated code.** `AndroidOnlyMenu` repeats the three items from `OverflowMenu`. Extract a shared `AndroidOnlyItems` and have `OverflowMenu` use it.
- **L8 — File size.** `LibraryFlowBranches.kt` is 255 lines and `CatalogScreen.kt` is 230, both over the 200-line limit. The implementer noted this.
- **L9 — Missing tests:** avatar → profile chooser, root search, compact header hiding, the see-through blend, and switching width class. The Robolectric suites always run at one fixed width.

## Checked and fine
- Back handling and the `LibraryPositions` stack are unchanged. `?.let` behaves exactly like the old `?: return`.
- Root back still leaves the app, as before.
- Search reaches `at::openSearch` from the bar, from the field look-alike, and from pushed frames.
- The avatar calls `ProfileBarState.onChoose` (the `reopen` handler).
- Settings/System render without the rail.
- TMDB key, Update library (disabled reason and note) and Start over keep their handlers; `LibraryFlowTest` covers TMDB key and Update.
- No destination was gated for kids profiles before, and none is now.
- The Movies/Series/Tutorials/Collections content dispatch is unchanged.
- `chosenTab` is still held with rememberSaveable.
- Counts match `app.js:116-135`.
- The icons match `index.html:52-56` exactly.
- Icon-only buttons all have content descriptions. The 44dp targets get Compose's automatic hit expansion to 48dp.

## Recommended order
1. H1 and H2 together: restructure `LibraryHome`/`LibraryScaffold` so content has one fixed call site, and place it at the header's visible height.
2. M1 with M2: hoist the Home grid state, draw under the bar only when there is a cover, and pad the progress/notice rows.
3. M3 insets, then M4 `selectable(role = Tab)`.
4. The Low items, as time allows. Add a test that switches width class, using the `LocalWindowInfo` override.

## Unresolved questions
- L4 (full ⋮ on pushed frames on EXPANDED): keep it as the implementer chose, or trim it as the spec says? The lead decides; the user never ruled on it.
- Should the phone header snap to fully shown or fully hidden when a fling ends? Material's enterAlways can; the implementation leaves it half-hidden.

**Status:** DONE_WITH_CONCERNS
**Summary:** The spec criteria and TV contracts hold and the gate passes, but two probe-confirmed High defects (state lost on tablet rotation; hiding the compact header frees no space) and four Medium ones (see-through bar out of step with the scroll, content hidden under the bar on Home, lost nav-bar insets, lost selected semantics) should be fixed before commit.
