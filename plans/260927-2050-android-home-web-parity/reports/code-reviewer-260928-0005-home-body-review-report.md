# Code review: Android home body (cover, features, bands, shelves), 0.70.1, uncommitted on f85bbee0

## Scope
- Worktree `/home/andre/Workspace/mediagram-home`, branch `feat/android-home-web-parity`, reviewing the uncommitted diff:
  - new `ui/catalog/home/*` (9 files) and `HomeScreen`
  - `CatalogScreen`, `LibraryFlowBranches`, `LibraryHome`, `DepartmentsBar`, `CoverBlend`
  - `designsystem/Type.kt`, plus 3 static Fraunces cuts
  - the tests
- Spec: `phase-02-home-cover-features-bands-shelves.md`. Web reference: `home-*.js`, `editorial-picks.js`, `home.css`, `appearance.css`.
- Probes: 3 throwaway Robolectric tests in a scratchpad copy of `android/`. The worktree was not edited.

## Overall
- Layout, type, actions and the See-all targets match the web, and the play path is the same one the title page uses.
- **The gate is not green.** `:core:designsystem:testDebugUnitTest` fails. The implementer's gate never ran that module.
- One probe-confirmed crash, in the cover.
- Four Medium defects: taps landing on the wrong film during the fade, Light-theme contrast, pager/button collisions on compact, and no pause on touch.

## Gate (e)
`./gradlew :ui-mobile:testDebugUnitTest :feature:catalog:testDebugUnitTest :ui-tv:testDebugUnitTest :core:designsystem:testDebugUnitTest lint :app:assembleDebug --continue`
- **FAIL:** `PageTitleFontTest > displayStillCarriesItsOwnVariationAxisRequest` (`core/designsystem/src/test/kotlin/PageTitleFontTest.kt:52`, 18 tests, 1 failed).
- Everything else passes: the ui-mobile, feature:catalog and ui-tv tests, lint, and `:app:assembleDebug`.

## High

### H1: The cover crashes when its lineup shrinks under the slide on screen
- **Where:** `home/HomeCover.kt:116,124-125`. The `Crossfade` is keyed on an index, and each slide reads `films[shown]`.
- **Why it crashes:** When `films` shrinks, `page` is clamped, but `Crossfade` still composes the outgoing state, which is the old index, against the new list.
- **Probe:** 3 films, tap "Cover story 3 of 3", then shrink the lineup to 2 films. Result: `ArrayIndexOutOfBoundsException: Index 2 out of bounds for length 2 at HomeCover.kt:125`, thrown during composition, so the app crashes.
- **When it happens in real use:** The cover is `pickFeatured(open { backdropPath })` (`EditorialPicks.kt:88`). That pool is unwatched films with a backdrop that no feature has already taken. It shrinks when:
  - another device marks a cover film watched and the change syncs in;
  - someone pins an Editor's choice;
  - a library refresh removes films.

  Whenever that pool holds fewer than 5 + 3 films (a kids profile, a small library, or one whose TMDB artwork is still arriving), the cover loses a slide. It crashes if the viewer is on a later slide.
- **Fix (verified in the probe copy: no crash; `HomeCoverTest` and `HomeScreenTest` stay green):**
  ```kotlin
  val transition = updateTransition(targetState = films[page], label = "cover")
  transition.Crossfade(animationSpec = tween(CROSSFADE_MS), modifier = Modifier.fillMaxSize(), contentKey = { it.setId }) { set -> CoverSlide(set = set, …) }
  ```
  This needs `@OptIn(ExperimentalAnimationApi::class)`. The plain `Crossfade(targetState = films[page])` also works without the experimental API. Either way, the outgoing slide keeps its own `MediaSet` instead of an index into a list that has changed.

### H2: The designsystem test module is red
- **What fails:** `PageTitleFontTest.kt:45-55` still asserts that `Display` uses `R.font.fraunces` with variation settings. The change deliberately reversed that.
- **Fix (verified: the designsystem suite goes green in the probe copy):**
  - Replace that test with `displayIsAStaticPairAtItsTwoWeights`: resIds `fraunces_display_500`/`_600`, weights Medium/SemiBold, empty `variationSettings`.
  - Add `coverTitleIsAStaticCutAtTheWidestOpticalSize`: `fraunces_cover_600`, SemiBold, static.
  - Add `:core:designsystem:testDebugUnitTest` to the gate the implementer runs.

## Medium

### M1: A tap during the 1.4 s fade acts on the film that is not the one on screen; the cover never pauses for touch or focus
- **Where:** `HomeCover.kt:96-102,124`. During a `Crossfade` both slides are composed, and the incoming slide sits on top, so it receives every hit even while it is almost transparent.
- **Probe:**
  - Setup: two films; tap at the position of Film a's "Watch now" 150 ms after the auto-advance.
  - Result: `played = b`.
  - What the viewer sees at that moment is still roughly 90% Film a. The web avoids this because `replaceChildren` removes the old slide at once.
- **Spec gap:** the spec says "pause while touched/dragged or focused". The web holds the rotation on `pointerenter`/`focusin` (`home-cover.js:77-80`). Android only has the pause button.
  - Scenario: a viewer reading the deck with a finger resting on the cover sees the slide change under the finger.
  - Scenario: a TalkBack user sitting on "Watch now" loses accessibility focus every 9 s when the node is disposed.
- **Fix:**
  - Swallow pointer input on the cover while `transition.isRunning`. Verified: the tap is ignored (`played = null`) and the viewer taps again after the fade.
  - Hold rotation while a pointer is down: `pointerInput` with `awaitFirstDown(requireUnconsumed = false, pass = Initial)` / `waitForUpOrCancellation`.
  - Hold rotation while focus is inside the cover (`onFocusChanged { it.hasFocus }`).
  - Do not auto-rotate when `AccessibilityManager.isTouchExplorationEnabled`.
  - Add all three flags to the `LaunchedEffect` keys.

### M2: In Light theme on EXPANDED, the cover text sits on light paper (contrast about 2:1)
- **Cause:** `CoverControls.kt:145-161` draws the three scrim layers in the order CSS lists them. But with CSS multiple backgrounds, the first layer listed is painted on top: the web's dark left-to-right gradient covers the paper fade (`home.css:33-36`). Android paints the paper fade over the dark gradient.
- **Dark theme:** no visible difference, because the paper colour (#0d0d0e) is effectively black.
- **Light theme, computed at the button row (64dp above the bottom, x = 10–30%):**

  | | Background | OnImage2 contrast |
  |---|---|---|
  | Android | ≈ rgb(170,167,162) | 1.8–2.0 : 1 |
  | Web | ≈ rgb(32–65) | 6.8–10 : 1 |

  At the meta line, Android reaches 3.7 : 1. "Details", the meta line and "+ My List" are hard to read.
- **Fix:** draw in the reverse order — the top black gradient first, then the paper fade, then the horizontal gradient last.

### M3: On compact, the pager covers the lower edge of the buttons, and the button row does not wrap
- **Pager overlap** (`HomeCover.kt:145,189`):
  - Heights: the text block has 40dp bottom padding, so the pills span 40–88dp from the bottom. The pager sits 18dp from the bottom and is 32dp tall, so it spans 18–50dp. That is a 10dp overlap.
  - Widths on a 412dp phone with 5 films: the pager spans x 194–396dp (layout-only, confirmed by the probe). "+ My List" spans ≈176–288dp and "Details" ≈300–362dp (from Geist advance widths).
  - Result: the pause circle overlaps "+ My List" visibly. Because the pager is drawn later, it takes taps on the pills' lower edge.
  - The web's compact layout uses `padding-bottom: 104px` on the copy and `bottom: 40px` on the pager (`home.css:351,356`).
- **No wrap:** the web uses `.cover-actions { flex-wrap: wrap }` (`home.css:70`). The three actions need about 346dp at font scale 1.0.
  - A 360dp phone has 328dp for them.
  - A 412dp phone at font scale 1.3 needs about 410dp and has 380dp.
  - In both cases "Details" is squeezed until it breaks mid-word.
- **Fix:**
  - On compact, use 104dp bottom padding for the text and 40dp for the pager.
  - Use `FlowRow(horizontalArrangement = spacedBy(12.dp), verticalArrangement = spacedBy(12.dp))` for the actions.
- **Not probe-checked:** Robolectric's text widths are unrealistically narrow (about 1dp per character), so the widths above were computed from the font, not measured.

## Low
- **L1: The bar stays see-through on Home when there is no cover.** `LibraryHome.kt:124-133` never reads `hasCover`. This is the remaining half of the previous review's M2.
  - Now the blend is ramped over the height of item 0, which is the features block.
  - In Light theme the pills are cream on grey-over-paper at scroll 0. This happens with a new library before any TMDB fetch, or on a kids profile.
  - Fix: `if (!isHome || !hasCover) 1f`.
- **L2: The feature cards in the pair do not match in height.** `HomeFeatures.kt:103-107,113-120` uses a `Row` without equal heights.
  - Example: a 3-line title plus a 3-line deck makes one card about 312dp tall beside a 220dp card. The web's grid stretches both to the same height.
  - Suggested fix: `Row(Modifier.height(IntrinsicSize.Max))` with `fillMaxHeight()` on each card. Verify it, since the image uses `matchParentSize`.
- **L3: ResumeCard gaps.**
  - `ResumeCard.kt:276` builds the film subtitle with `listOfNotNull(year, null)`. The `null` is dead code, and the duration the web shows ("1995 · 2h 4m") is missing.
  - `SetCard.caption` ("1h left" / "Next up") is never exposed to TalkBack; the web reads it through `sr-only`.
  - Cards are a fixed 208dp. The web uses 78% width on compact and snaps.
- **L4: "This month" draws a border around every row** (`RecentBand.kt:135`). The web draws a left rule on the column and a bottom rule under each row (`home.css:308,317`).
- **L5: `fraunces.ttf` (114 KB) is no longer referenced by any main code.** The only reference left is the stale test. It still ships because `shrinkResources` is off.
  - The three new cuts add about 108 KB.
  - Deleting the orphan makes the change 6 KB smaller than before instead of 108 KB larger. It can be kept outside `res/` if it is wanted as the source for future cuts.
- **L6: Pager accessibility.**
  - The dots report no selected state; the web uses `aria-pressed`.
  - Touch targets are small: dots 28dp and pause 32dp, against the 48dp guideline. "Details" and "See all →" are about 42–44dp.
- **L7: Poster width and row limits.**
  - `HomeScreen.kt:115` feeds the window width, not the column width, into the poster formula. Posters are too wide on windows over 1180dp (at 1400dp: 151dp instead of 136dp).
  - Recently Added is capped at 6 (`HOME_ROW_LIMIT`), not the web's 8, the same gap as Latest series. The implementer's report only lists Latest series.
- **L8: The compact cover has an exact height of 0.84 × window height.** In a short window (split screen) the text block runs out past the top of the item and gets clipped.
  - The comment justifying it (`HomeCover.kt:106-112`) is about `HorizontalPager`, which is gone.
  - The real reason is that `fillMaxSize` does not fill inside an item with unbounded height. The comment should say that, or `propagateMinConstraints` could be used.
- **L9: Comments and docs.**
  - `HomeFeatures.kt:75` says "this phase shipped", which is a plan reference in a code comment.
  - `Type.kt:38-54`: the KDoc (now attached to `read()`) still says Fraunces is variable and that this is "not a reason to ship a static cut".
  - The `PageTitle` KDoc links to the deleted `[display]` and claims nothing else showed the bug.
  - "Replaces CoverStory/FeatureStrip/ResumeStrip" comments describe history.
  - Pre-existing: "H1" labels in `LibraryHome.kt:157` and in `WidthClassStateTest`.
  - Files over 200 lines: `HomeCover.kt` (252) and `HomeFeatures.kt` (206).
  - The changelog is inaccurate: 0.70.0 already read the scroll position, not "tracked scroll deltas". It also leaves out the app-wide static Fraunces change, which is visible on phone and TV.
  - The implementer's list of remaining differences still describes the cover turning with a pager swipe.
- **L10: TV fonts.**
  - There is no weight-mapping regression: `Display` had only 500 and 600 entries before too, and TV only asks for Medium (`TvTypeScale.title`).
  - TV looks different only if the TV box also ignored the variation axes. In that case titles go from the font's default (900, opsz 9) to 500 at opsz 28, which is the intended look.
  - Not checked on TV hardware.

## Checked and fine
- **Watch now uses the title page's play path.**
  - Watch now → `onPlayRun(id, emptyList())` → `openPlayer`. `playerPayload` treats an empty run as no run (`LibraryPositions.kt:175`), so `at.run == null`, `handPicked = false`, the run comes from `runFor`, and `fsk` is passed to the player.
  - That is the same path as the title page's `at.openPlayer(title.setId)` (`LibraryTitleBranches.kt:38`).
  - The resume position comes from the player's own progress lookup (`PlayerViewModelOpen.kt:45-48`). Continue cards use the same path.
- **My List:** `setWatchlisted(setId, !listed)` passes an explicit boolean, so a double tap does no harm. The probe confirmed "✓ My List" reads back in the same composition.
- **See all:** "Continue" (`KeptKind.CONTINUE`), "Movies", "Series" and "Tutorials" all resolve to real tabs.
- **Case and type:** eyebrow, cover title, features (except the second card), the quote credit and "This month" are uppercase. Section heads are in Geist.
- **SOLID backdrop:** the implementer is right. `appearance.css:22-24` hides only `.spread-art`/`.dept-art` under Solid, so the web leaves the cover's picture showing. The spec text was wrong; this is a correction, not a divergence.
- **Bar blend:**
  - It is a pure function of the scroll position and the measured cover height.
  - After back-navigation it still reads correctly: the list state is hoisted in `LibraryBranches` and keeps the last layout info.
  - Rotation to MEDIUM hides the bar anyway.
  - The earlier H1 fix (one `content()` call site) is intact and still guarded by `WidthClassStateTest`.
  - The solid end is now exactly opaque.
- **Nested scrolling:** the `LazyRow`s sit inside the `LazyColumn` normally, and the header connection consumes only vertical deltas.
- **Rotation cost:** the 9 s turn recomposes only `HomeCover`, and the progress bars are static.
- **Crossfade memory:** both slides stay alive only for the 1.4 s fade, then the old one is disposed. Image decoding costs the same as the old pager.
- **Static font cuts:** checked with fontTools. They have no `fvar` table, and their glyph advances match an instancer cut at the intended settings (weight 500/600 at opsz 28, and 600 at opsz 144).
- **Shared contracts:** `feature/catalog` is untouched. The designsystem change is additive (`CoverTitle`) plus the internal `Display`. The ui-tv tests pass.

## Recommended order
1. H1: key the crossfade on the film.
2. H2: fix the test and add designsystem to the gate.
3. M2: reverse the scrim order.
4. M3: compact padding and `FlowRow`.
5. M1: swallow taps during the fade, pause on press/focus, no auto-rotation under TalkBack.
6. L1 and L5 (one-liners), then the rest as time allows.

## Unresolved questions
- The compact cover also has low contrast in Light theme on the web itself: a single gradient ends in paper. Should that be a separate web ticket?
- Should Recently Added and Latest series stay at 6 cards against the web's 8, documented as a known difference?
- Under TalkBack, should rotation stop entirely (simplest), or only pause while accessibility focus is inside the cover?

**Status:** DONE_WITH_CONCERNS
**Summary:** The spec criteria and the play, watchlist and See-all wiring hold, and the blend fixes survive the move to `LazyListState`. But the gate is red (the designsystem test), the cover crashes when its lineup shrinks (confirmed by probe), and there are four Medium defects: taps landing on the wrong film during the fade, Light-theme scrim contrast, pager/button collisions on compact phones, and no pause on touch or focus.
