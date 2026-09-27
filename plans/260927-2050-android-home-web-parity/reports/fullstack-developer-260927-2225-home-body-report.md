# Phase Implementation Report

## Addendum — lead review fixes (same session, same worktree)

The lead reviewed the four side-by-side sheets and found six issues; all six
addressed, gate re-run green, sheets regenerated. In order:

1. **Fraunces rendered heavy everywhere except the static page-title cuts.**
   Proven on-device (not just theorized): a temporary A/B — the same string,
   same size, one drawn through `FontVariation.Settings` (`wght 600, opsz
   144`), one through a static instance fontTools' `varLib.instancer` cut at
   the same coordinates — showed the variable one at the font's own default
   instance (confirmed via `fontTools`: `fraunces.ttf`'s `fvar` defaults to
   `wght 900`, `opsz 9`, its heaviest, most decorative cut) regardless of
   what was requested, and the static one correctly weighted. Cut three new
   static instances the same way `fraunces_page_title.ttf` was made —
   `fraunces_display_500.ttf`/`_600.ttf` (`opsz 28`, the two weights
   `Display` actually uses) and `fraunces_cover_600.ttf` (`opsz 144`, the
   cover's own) — and replaced `Type.kt`'s `display()`/`coverFace()`
   `FontVariation.Settings` functions with `Font(resId = ...)` references to
   them. Newsreader and Geist checked the same way (their own `fvar`
   defaults — `wght 400`/`opsz 18` and `wght 400` — are close enough to what
   every call site here actually asks for that nothing showed the failure;
   Newsreader's own 500/600 cuts are additionally dead code today — nothing
   requests `bodyLarge` at a non-default weight), left as variable.
   `:ui-tv:testDebugUnitTest` re-run green (`Display` is shared via
   `TvTypeScale.title`).
2. **Cover buttons drawn in Newsreader, not Geist; Details not underlined.**
   `SolidPill`/`LinePill` (`CoverControls.kt`) and `ResumeCard`'s own name/sub
   text called the bare `Text(text, fontWeight=..., fontSize=...)` overload,
   which falls back to `LocalTextStyle.current` — `bodyLarge`, Newsreader —
   when nothing sets `style`. Fixed by basing each on
   `MaterialTheme.typography.bodyMedium` (Geist) instead. "Details" gained
   `TextDecoration.Underline`.
3. **Departments bar stayed see-through deep in the page.** Two separate bugs:
   - `coverBlend()`'s own 40%–75%-of-viewport heuristic didn't track the
     cover's real height once the cover stopped being a fixed aspect ratio.
     Rewrote it against the cover item's own measured size
     (`LazyListState.layoutInfo.visibleItemsInfo`), opaque once the cover's
     own bottom edge has scrolled past the bar's own bottom edge — matching
     the rule as stated. `CoverBlendTest` rewritten for the new signature
     plus two new cases (`aDeepScrollManyItemsPastTheCoverStaysFullySolid`,
     `restoredDirectlyAtADeepPositionReadsSolidWithNoTransition`).
   - Separately, even at `blend = 1`, the status-bar strip above the pill row
     kept showing a faint trace of the cover's own button row through it.
     `DepartmentsBar`'s background was chained through
     `windowInsetsPadding(statusBars)` rather than wrapping an explicit
     status-bar-height `Spacer`; rebuilt as a `Column` (spacer, then the pill
     row) sharing one `background(bg)`. This took the effect from a bold,
     easily-read "Watch now / + My List / Details" down to a barely-visible
     trace at the exact seam between the spacer and the row — verified
     improved on-device, screenshots before/after in the scratchpad — but a
     faint residual remains in that one-pixel seam on this device; flagged
     below rather than left silent.
4. **Wide feature card 360dp tall, not matching the pair below.** Web's own
   CSS reads `min-height: 360px` at this breakpoint, but the reference shot's
   own wide card sits at the same height as the pair below it — matched the
   shot's own measured proportions (`clamp(220, 16%·W, 270)`, the same
   formula the pair already used), not the declared CSS minimum. While
   fixing this, found and fixed the actual reason the numbers had diverged:
   `heightIn(min = ...)` does not cap a card's height — each card's own
   `fillMaxSize()` backdrop and scrim grow to fill whatever height they are
   handed, so the first card (a bare min, no cap) filled the entire
   available column height and left the row below it nothing to measure
   into, collapsing "the second card" to a zero-size node — the actual cause
   of a `HomeFeaturesTest` regression this surfaced. Every feature card row
   now gets an *exact* height, never just a `heightIn(min = ...)`.
5. **No gap between a feature's eyebrow and its title.** `.feature-eyebrow`'s
   own `margin-bottom: 18px` (`home.css:170`) was never carried over; added.
6. **Cover slide changed by sliding, not the web's own crossfade — and a
   portrait shot caught mid-slide with the title clipped.** Replaced
   `HorizontalPager` with `Crossfade(animationSpec = tween(1400))` — the
   web's own `cover-fade` duration — losing the swipe-to-navigate gesture
   (not "free" to keep alongside a fade; tapping a pager bar still jumps to
   any slide) for a fade that never leaves a title clipped mid-turn.

Gate: `./gradlew :ui-mobile:testDebugUnitTest :feature:catalog:testDebugUnitTest :ui-tv:testDebugUnitTest lint :app:assembleDebug` green. Reinstalled on `caad49da` (test profile), regenerated all four sheets at the same depths (`home-parity-sheet-{1,2,3,portrait}.png`, this addendum's fixes baked in — the originals from the first pass were overwritten, not kept alongside).

### Unresolved from this addendum
- **Status-bar-strip seam (issue 3, second bug):** improved from bold/readable to a barely-visible trace, not eliminated. Spent the available budget confirming it survives a genuinely settled scroll (not a mid-fling capture) and rebuilding the bar's own background structure around it; did not fully root-cause why `windowInsetsPadding` chained onto `background` left that exact seam uncovered even though the modifier order says it shouldn't. Worth a fresh pair of eyes rather than more time against this budget.
- **`CompactLibraryHeader.kt` may share the same `background().windowInsetsPadding(statusBars)` chain** — not checked, since it was not part of what was flagged (its own background is a constant alpha, not blended, so the same defect would read as a permanent faint tint rather than a "should reach opaque but doesn't" bug). Flagging so it is not rediscovered separately.
- Version/changelog were **not** bumped again for this addendum (still `0.70.1` from the base phase) — these are fixes to the same phase's own unshipped work, not a new release; no separate entry added.

---

## Addendum 2 — second lead review round (same session, same worktree)

The lead reviewed the "v2" sheets and found three issues left; all three
addressed, gate re-run green, sheets regenerated. In order:

1. **Bar still see-through deep in the page** (the unresolved item flagged
   above, now actually fixed rather than mitigated). Root cause: the solid
   end of `barBackground()`'s blend was `--paper` at `0.94` alpha, not `1.0`
   — a value carried over from mirroring the web's own `--paper` @ 78%
   without noticing Android has nothing standing in for the `backdrop-filter:
   blur` that makes the web's 78% read as solid. `0.94` alone was enough for
   a faint trace to survive at the bar's own seams (the status-bar strip
   included) against anything bright enough behind it — `home-parity-sheet-3.png`'s
   old "v2" capture showed Latest series' own posters through both. Changed
   `DepartmentsBar.kt`'s solid state to `MaterialTheme.colorScheme.background`
   at its own full opacity, with a comment on why (no blur to lean on here,
   unlike the web). New `DepartmentsBarColorTest.kt`
   (`aDeepScrollReadsFullyOpaque`, `overTheCoverStaysTranslucent`) — plain
   JUnit on `barBackground()` directly, the same reason `CoverBlendTest`
   needs no Robolectric.
2. **Feature cards clipping their own text.** The first round's fix for the
   card-collapse bug (decision 4 above) used an *exact* `height()` on every
   card to stop a `fillMaxSize()` sibling from starving the row below it —
   which then cropped any deck long enough to want the card's own natural
   height back (`home-parity-sheet-2.png`'s old "v2" capture: "Der
   Astronaut…"'s deck missing outright, "Staff pick"'s sliced to a line of
   ellipsis dots). Root cause was never the `heightIn(min=)` modifier itself
   — it was the image/scrim children inside `FeatureCard` asking for
   `fillMaxSize()`, which demands all available height regardless of what
   the min was. Switched those children to `Modifier.matchParentSize()`
   (matches whatever the Box already resolved to, never *asks* for height
   itself) and every card's own modifier back to `heightIn(min = cardHeight)`
   — the card is now `max(min, its own text)` tall, the web's own
   `min-height` semantics exactly. `HomeFeaturesTest`'s existing collapse
   test (`onlyTheSecondCardKeepsItsOwnCase`) stayed green throughout; added
   `aThreeLineDeckStillDisplaysInFullOnTheWideCardAndInThePair`, asserting a
   three-line deck renders in full on both the wide card and the pair.
3. **Depth 1 (scroll 0) appeared to show Continue Watching's card subtitles
   and the quote attribution directly under the cover, in place of the
   features block.** Investigated via semantics bounds rather than
   screenshot-reading alone, per the lead's own suggestion: tagged
   `HomeCover`'s outer `BoxWithConstraints` (`HOME_COVER_TEST_TAG`) and
   `HomeFeatures`' outer container in each of its three width branches
   (`HOME_FEATURES_TEST_TAG`), then added
   `theFeaturesBlockStartsAtOrBelowTheCoversOwnBottom` to
   `HomeScreenTest.kt`, asserting the features node's own top bound is at or
   below the cover node's own bottom bound. It passed against the
   already-shipped layout (`HomeScreen.kt`'s `LazyColumn` items are cover
   then features, in order, each an ordinary item with no overlay layer) —
   there is no code bug here. Re-verified on-device at a freshly relaunched
   app's literal scroll 0 (`home-parity-sheet-1.png`, this addendum): the
   cover is immediately followed by "EDITOR'S CHOICE / CRIME 101", matching
   the web. The prior "v2" depth-1 capture most likely came from a scroll
   position that wasn't actually reset to the top before the screenshot (the
   same session had an unrelated profile mix-up around then, see this
   report's first addendum) — not a reproducible defect. The new semantics
   test stays as a regression guard either way.

Gate: `./gradlew :ui-mobile:testDebugUnitTest :feature:catalog:testDebugUnitTest :ui-tv:testDebugUnitTest lint :app:assembleDebug` green. Reinstalled on `caad49da` (test profile — avatar checked "T" before install, after install, and after the app was accidentally backgrounded by a stray back-key press mid-session). Regenerated all four sheets (`home-parity-sheet-{1,2,3,portrait}.png`, this addendum's fixes baked in — the "v2" captures were overwritten, not kept alongside); depth 1 captured at a literal scroll 0 immediately after relaunch, per the lead's own instruction.

### Files touched this addendum
- `ui-mobile/src/main/kotlin/ui/chrome/DepartmentsBar.kt` — solid alpha 0.94 → 1.0, comment explaining no-blur
- `ui-mobile/src/test/kotlin/ui/chrome/DepartmentsBarColorTest.kt` — new
- `ui-mobile/src/main/kotlin/ui/catalog/home/HomeFeatures.kt` — `heightIn(min=)` restored on every card, image/scrim `fillMaxSize()` → `matchParentSize()`, `HOME_FEATURES_TEST_TAG` added to all three width branches
- `ui-mobile/src/test/kotlin/ui/catalog/home/HomeFeaturesTest.kt` — `set()` gained an optional `tagline`; new `aThreeLineDeckStillDisplaysInFullOnTheWideCardAndInThePair`
- `ui-mobile/src/main/kotlin/ui/catalog/home/HomeCover.kt` — `HOME_COVER_TEST_TAG` added to the outer `BoxWithConstraints`
- `ui-mobile/src/test/kotlin/ui/catalog/HomeScreenTest.kt` — new `theFeaturesBlockStartsAtOrBelowTheCoversOwnBottom`

Version/changelog not bumped this addendum either, same reasoning as addendum 1.

### Unresolved from this addendum
None outstanding from the three issues raised. The status-bar-strip seam flagged unresolved after addendum 1 is superseded by fix 1 above (full opacity, not a blend endpoint that only approached it).

---

## Executed Phase
- Phase: phase-02-home-cover-features-bands-shelves
- Plan: plans/260927-2050-android-home-web-parity
- Status: completed

## Files Modified

New (`ui-mobile/src/main/kotlin/ui/catalog/home/`):
- `HomeType.kt` (58) — `fluid()`, `gutterFor()`, `CompactBreakpoint`/`WideBreakpoint`, `countOf`/`spellCount` (the web's spelled-number captions)
- `HomeCover.kt` (240) — `HomeCover`, `CoverSlide`
- `CoverControls.kt` (160) — `SolidPill`, `LinePill`, `CoverPager`, `CoverScrim` (split out of `HomeCover.kt` to clear the 200-line guideline)
- `HomeFeatures.kt` (182) — the three-card strip, compact/narrow/wide layouts
- `ContinueBand.kt` (112) — the band split, `BandHeading` (shared by every section below it)
- `ResumeCard.kt` (170) — `ResumeRow`, `ResumeCard`, `Quote` (split out of `ContinueBand.kt`)
- `RecentBand.kt` (165) — Recently Added poster strip (no captions) + This month
- `HomeShelfRow.kt` (90) — Latest series poster row with captions
- `CourseList.kt` (86) — Latest courses list

New tests:
- `ui-mobile/src/test/kotlin/ui/catalog/home/HomeTypeTest.kt` (fluid, gutter, countOf spelling — plain JUnit)
- `ui-mobile/src/test/kotlin/ui/catalog/home/HomeCoverTest.kt` (uppercase eyebrow/title, Watch now plays not opens, My List toggles + reads back, SOLID/BLURRED leave the cover's own art showing)
- `ui-mobile/src/test/kotlin/ui/catalog/home/HomeFeaturesTest.kt` (only the second card keeps its own case)
- `ui-mobile/src/test/kotlin/ui/catalog/HomeScreenTest.kt` (section order top-to-bottom, Continue's own "See all" lands on the Continue tab specifically)

Modified:
- `ui-mobile/src/main/kotlin/ui/catalog/HomeScreen.kt` (209→138) — rewritten as a `LazyColumn` of the six sections above; hoisted `LazyListState`; drops the old grid-item plumbing
- `ui-mobile/src/main/kotlin/ui/catalog/CatalogScreen.kt` (233→251) — `HomeScreen` call passes `onPlay`/`onToggleWatchlist`; `homeGridState: LazyGridState` → `homeListState: LazyListState` end to end
- `ui-mobile/src/main/kotlin/ui/LibraryFlowBranches.kt` — `rememberLazyGridState()` → `rememberLazyListState()`; `onToggleWatchlist = catalogViewModel::setWatchlisted` threaded into `CatalogScreen`
- `ui-mobile/src/main/kotlin/ui/chrome/LibraryHome.kt` — `homeScrollState: LazyGridState` → `LazyListState` (the phase's own instruction: "carry the hoisted state and `coverBlend()` over"; `coverBlend()` itself is untouched, it already took primitives)
- `ui-mobile/src/test/kotlin/ui/chrome/WidthClassStateTest.kt` — `rememberLazyGridState()` → `rememberLazyListState()` at its two `LibraryHome(...)` call sites
- `core/designsystem/src/main/kotlin/Type.kt` (145→275 via earlier phases, +30 here) — new public `CoverTitle` `TextStyle`: Fraunces 600 at `opsz 144` (the web's own `.cover-title` cut), no other title on this catalogue reaches for it

Deleted (unused once Home no longer draws them):
- `ui-mobile/src/main/kotlin/ui/catalog/CoverStory.kt`
- `ui-mobile/src/main/kotlin/ui/catalog/FeatureStrip.kt`

Kept (still drawn elsewhere — checked every call site first):
- `ResumeStrip.kt`, `PullQuote.kt` — both still used by `MoviesDepartmentScreen.kt` and `ShowsDepartmentScreen.kt` (their own "Continue your series/courses" bands and lead quote), unrelated to Home now.

Manifests / docs (not committed):
- `Cargo.toml`, `Cargo.lock` (5 workspace members only), `web/package.json`, `android/app/build.gradle.kts` `versionName` → `0.70.1` by regex, not exact-string (`versionCode` untouched — confirmed against git history that patch-only bumps in this project never move it, only the 0.69.4→0.70.0 *minor* bump did)
- `docs/project-changelog.md` — new `0.70.1` entry, this phase's own voice
- `plans/260927-2050-android-home-web-parity/{plan.md,phase-02-*.md}` — phase marked completed, Todo boxes ticked
- Screenshots + side-by-side sheets in `plans/260927-2050-android-home-web-parity/reports/` (`home-parity-sheet-{1,2,3,portrait}.png`)

## Tasks Completed
All phase Todo items ticked; see the phase file.

## Decisions taken where the spec left room

1. **SOLID leaves the cover's own picture showing — a corrected spec detail, not a new deviation.** The phase text said "SOLID → plain paper" for the cover. Read `web/public/styles/appearance.css:19-26` directly: the *blurred* rule lists `.cover-stage` among its targets; the *solid* rule two lines below names only `.spread-art`/`.dept-art` — the cover is not in it. The web's own Solid mode leaves the cover story's picture on screen; only Blurred changes it. `HomeCover` matches that (confirmed with `HomeCoverTest.solidLeavesTheCoversOwnArtworkShowingUnlikeTheTitleSpreadOrADepartmentHero`, and on-device). This wasn't a locked decision in `plan.md`'s own "Decisions" section, just the phase author's own inference from the surrounding comment ("how a hero — cover, title spread, department — shows its picture"), which the actual selector list doesn't bear out. Flagging in case the intent really was to diverge from the web here on purpose.
2. **`countOf`'s spelled numbers ("21 episodes · three seasons") are the literal web output, not loose phrasing.** Read `web/public/lib/format.js:152-175`: the web spells counts 0–20 as words and only uses figures above that (`NUMBER_WORDS`/`spellCount`). Confirmed against `home-web-3.png` itself: "Boardwalk Empire" reads "fourteen episodes · two seasons", "Schitt's Creek" reads "21 episodes · three seasons". Android's existing sitewide `ui.catalog.countOf` (ui-common, used by Movies/Genres/Collections/etc.) is plain-numeral and out of this phase's scope — touching it would ripple into screens this phase doesn't own. Added a second, phase-local `countOf`/`spellCount` in `HomeType.kt` used only by `HomeShelfRow`/`CourseList`, documented as deliberately narrower than the shared one. Verified on-device against the real library (own dataset, same spelling rule, different counts from the reference shots since the two libraries hold different shows).
3. **`HorizontalPager` under `heightIn(min = ...)` inside a `LazyColumn` item collapses — reproduced on-device, not just theorized.** Built the cover with `Modifier.heightIn(min = ...)` first (matching the phase's own "at least 84% of window height, grows with content" wording literally). On the tablet in **portrait** specifically, the pager measured itself small and floated at the top of the (correctly 978dp-tall) outer box, leaving a blank gap down to the pager dots at the true bottom — exactly the "collapses to nothing" trap `CoverStory.kt`'s own old comment already named for a `HorizontalPager` under an unbounded max-height constraint. Fixed by giving the outer box an *exact* `Modifier.height(...)` instead of an open `heightIn(min = ...)` — bounded either way, so `fillMaxSize()` inside it is reliable regardless of width class. Re-verified on-device before and after (screenshots in the scratchpad; the fixed version is what `home-parity-sheet-portrait.png` shows). This is why landscape "looked fine" with the naive version and portrait didn't: landscape's `BoxWithConstraints` happened to still report a workable height to the pager; portrait's taller box exposed the same defect the old file had already worked around once.
4. **Watch now / + My List drawn as their own small `Box`-based pills**, not reused from `TitlePills.kt` (title detail screen's own actions) — that file's pills are plain Material `Button`/`OutlinedButton`, sized and coloured for a page on `paper`, not the artwork-specific fixed on-image colours (`#f6f2ea` text, `rgba(8,8,9,.28)` fill) the cover's own pills need in both themes. Building a shared component for two call sites with different colour systems would be premature; noted as a possible future extraction if a third site needs the same look.
5. **"▶ Watch now" / pause "▶"/"‖" stay as unicode glyphs**, not vector-drawn triangles. Matches the existing convention already in this codebase (`TitlePills.kt`'s own "▶ $playLabel"), and the web's own SVG triangle is a visual, not a semantic, difference a screen reader never hits (each pill/button also carries its own `contentDescription`).
6. **`HomeCover`/`HomeFeatures` read the window's own width via `LocalConfiguration`, threaded down as a `Dp` parameter from `HomeScreen`** rather than each section reading `LocalConfiguration` for itself — one read, passed to every section, so a width-class change can't leave two sections reading two different moments of it. `BoxWithConstraints` is used only where a section's own *column* width (not the window's) is what a formula needs (the cover's text-block max width, `HomeShelfRow`'s poster width formula) — matches the phase's own note that "column" and "W" answer different questions.
7. **`Entry.Collection`-shaped rows (Latest series, Latest courses) are extracted from `homeRowsOf`'s existing `rows` in `HomeScreen.kt` itself**, rather than adding new fields to `MagazineHome`/`HomeRow` — `homeRowsOf` already builds exactly these two rows (Continue/Next up/Latest films already filtered out by `CatalogScreen`), so `HomeScreen` only needed to pick `Entry.Collection` out of `RowContent.Entries` by title. No `feature/catalog` data-layer change was needed for this phase's own visible behaviour.
8. **Left the Series "Latest" home-row limit at the shared `HOME_ROW_LIMIT` (6), not the web's poster-row limit (8).** `homeRowsOf` is shared by Home, `LatestScreen`, and (unbuilt-but-linked) TV's own Latest page; bumping its default for Home alone would need a per-shelf parameter that ripples into callers this phase doesn't own, for a difference of two cards in one scrollable row. Documented as a known, deliberate gap from the web rather than silently left unexplained — see Unresolved Questions.

## Every remaining difference from the web, with its reason

- **Latest series row: 6 cards, not the web's 8.** See decision 8 above — `homeRowsOf`'s shared limit, out of this phase's scope to special-case without touching `LatestScreen`/TV.
- **No right-hand genre column on the cover.** Explicitly named in the phase spec itself as skipped (`hidden ≤1180px`, and the tablet is 1164dp).
- **Compact cover's top padding is `Spacing.large` (24dp), not the web's fixed 96dp.** The web's compact cover sits under its own static masthead and gives itself a fixed top offset; Android's compact chrome is a *separate*, non-overlaying header (plan.md's own deliberate difference #4), so the cover never draws under anything on compact and doesn't need a masthead-sized offset — 24dp is a reasonable top inset for the first line of text, not a masthead clearance.
- **Watch now / pause glyphs are unicode text, not vector paths.** See decision 5.
- **No backdrop crossfade/slow zoom animation between cover slides.** `HorizontalPager`'s own swipe-scroll animates the transition; the web's 1.4s crossfade + 14s slow zoom on the image itself were not ported — a `ponytail:`-flagged trade against the time budget; add if a reviewer wants the exact motion.
- **`Recently Added` posters have no fallback caption drawn under a *broken* image the way the row itself does under a missing one** — this matches the web (`captions: false` draws only the plate), the row's own no-poster fallback shows the title centered on the plate itself, same as the web's own `initialsOf`-style stand-in convention elsewhere on this catalogue.

## Tests Status
- Type check / compile: pass (`:ui-mobile:compileDebugKotlin`, `:core:designsystem:compileDebugKotlin`)
- Unit tests: pass — `:ui-mobile:testDebugUnitTest :feature:catalog:testDebugUnitTest :ui-tv:testDebugUnitTest` all green, including the new `HomeTypeTest`/`HomeCoverTest`/`HomeFeaturesTest`/`HomeScreenTest` and the retargeted `WidthClassStateTest`
- Lint: `./gradlew ... lint` green, no new issues
- `:app:assembleDebug`: pass
- Integration: installed on `caad49da` (pinned throughout; the TV box at `192.168.0.35:5555` was left alone), verified on the real "test" profile only

## Screenshots
`plans/260927-2050-android-home-web-parity/reports/`:
- `home-parity-sheet-1.png` — cover, stacked against `home-web-1.png` (same film, same day-seed)
- `home-parity-sheet-2.png` — features + Continue Watching + quote, against `home-web-2.png`
- `home-parity-sheet-3.png` — Latest series + Latest courses, against `home-web-3.png`
- `home-parity-sheet-portrait.png` — compact/portrait cover, against `home-web-phone-1.png`

On-device checks beyond the sheets (screenshots kept in the scratchpad, not copied in — the sheets above are the record): Watch now opens the player and actually starts it (confirmed, stopped immediately, test profile only); + My List increments the rail's own count and reads back "✓ My List"/"Remove from My List", toggled back off before finishing; "See all" beside Continue Watching lands on the Continue kept wall specifically (not Recently Added's or Latest series's own, which sit right below it); "See all" beside Latest courses lands on Tutorials; portrait/compact renders every section correctly once the `HorizontalPager` fix (decision 3) landed.

## Issues Encountered
- The `HorizontalPager`-collapses-under-`heightIn` defect (decision 3) — the one real bug this phase's on-device pass caught that Robolectric didn't (Robolectric's cover tests use a fixed `width = 1164.dp` parameter directly, never exercising the compact/portrait branch's own height math against a real window). Fixed before screenshots were taken; the sheets already show the corrected version.
- `HomeCover.kt`/`ContinueBand.kt` first landed at 380/260 lines — split into `HomeCover.kt`+`CoverControls.kt` and `ContinueBand.kt`+`ResumeCard.kt` to clear the 200-line guideline; `HomeCover.kt` still lands at 240 (cover + its one slide composable, judged not worth fragmenting further against the phase's own "near/under 200" wording).

## Next Steps
- Phase 03 adds the Documentaries department (data-first, touches `feature/catalog` and TV's `shelvesOf`/`catalogTabsOf`).
- Not committed, per instructions — worktree left with the diff for review.

## Unresolved Questions
- **Latest series' 6-vs-8 card count** (decision 8 / difference list): worth a deliberate call — either accept 6 as a documented Android difference, or take a follow-up to add a per-shelf limit to `homeRowsOf` (touches `LatestScreen` and TV's Latest page too, so probably its own small phase rather than a quick edit here).
- **Cover crossfade/zoom motion** — currently just the pager's own swipe transition. Flag if the web's 1.4s crossfade + slow zoom is wanted pixel-for-pixel rather than "the rotation reads the same, the transition doesn't."
- **appearance.css's Solid rule (decision 1)** — implemented as read (cover picture stays under Solid), but this reverses the phase spec's own literal wording. If the spec's wording was itself a deliberate call the user made elsewhere and not just the planner's inference, this needs an explicit override instruction rather than my own source-reading judgment.

**Status:** DONE
**Summary:** Home tab rebuilt section-for-section to the web's magazine layout (cover, features, Continue+quote, Recently Added+This month, Latest series, Latest courses) with matching type, spelled-number captions, and wired actions (Watch now plays, My List toggles, every See-all lands correctly); full gate green, verified on the tablet in both orientations including a real on-device pager-collapse fix, version bumped to 0.70.1, not committed.
**Concerns/Blockers:** None blocking. Two flagged-but-shipped deviations from the literal web (Latest series' card count, cover motion) and one corrected-spec-vs-source finding (Solid/cover) are called out above for a maintainer to confirm or override.

---

## Addendum 3 — code review round (same session, same worktree)

`code-reviewer-260928-0005-home-body-review-report.md` found a probe-confirmed
crash, a red designsystem gate, four Medium defects and ten Low items. All
addressed; gate re-run green (designsystem added to it, staying there from
now on); reinstalled and verified on `caad49da`.

### High

- **H1, cover crash on a shrinking lineup.** `Crossfade` was keyed on a
  `films` index; the outgoing slide's own lambda re-read `films[shown]`
  against whatever `films` recomposition handed it, so a lineup that shrank
  while a viewer sat on its last slide threw `ArrayIndexOutOfBoundsException`
  — confirmed by the reviewer's own probe. Fixed the way the review verified
  it works: `Crossfade(targetState = films[page])`, a `MediaSet` (a data
  class) rather than an index — the outgoing slide keeps its own value
  regardless of what the list it came from looks like now, no experimental
  API needed. New `HomeCoverTest.theLineupShrinkingPastTheShownPageDoesNotCrash`:
  three films, dot 3 tapped, lineup shrunk to two mid-composition, asserts no
  crash and the page clamps to the new last film.
- **H2, `:core:designsystem:testDebugUnitTest` red.**
  `displayStillCarriesItsOwnVariationAxisRequest` still asserted the
  pre-fix, variable `Display`. Replaced with
  `displayIsAStaticPairAtItsTwoWeights` (asserts the two static resIds, no
  variation settings) and added `coverTitleIsAStaticCutAtTheWidestOpticalSize`.
  `:core:designsystem:testDebugUnitTest` is now part of the gate every run
  after this one uses.

### Medium

- **M1 (the review's own "M3"), taps during the fade and no pause on
  touch/focus.** A `Crossfade` composes both slides at once, the incoming on
  top, so a tap mid-fade landed on the wrong film. A transparent
  tap-swallowing `Box` (no ripple, no callback) now covers the cover while a
  `fading` flag is true — skipped on the composable's very first slide, since
  nothing is actually fading yet then. Rotation now also holds while a
  pointer is down (`pointerInput` with `PointerEventPass.Initial`, so it
  sees a press even under a pill's own click) or while focus sits anywhere
  inside the cover (`focusGroup()` + `onFocusChanged`), and never starts at
  all when `AccessibilityManager.isTouchExplorationEnabled`.
- **M2 (the review's own "M4"), Light-theme scrim contrast.** `CoverScrim`'s
  three `Box` layers were drawn in the web's own CSS declaration order, but
  CSS paints the *first*-declared background on top, while Compose paints
  the *last*-composed child on top — so the web's frontmost layer (the dark
  90deg gradient) was Android's backmost, and the button row sat almost
  directly on `--paper` in Light theme. Reordered to draw back-to-front:
  top scrim, then paper fade, then the horizontal gradient last. Verified
  on-device (`home-parity-sheet-light-depth1.png`): the button row now sits
  on a dark ground in Light theme, not the paper colour. No new automated
  contrast assertion — the codebase's own WCAG contrast helper
  (`ContrastMeasurement.kt`) is `internal` to `core:designsystem`'s test
  source set, and a real one here would mean either exposing it through a
  `testFixtures` artifact or rendering pixels via `captureToImage()`, a
  pattern this codebase has only ever used in `androidTest` (never a JVM
  Robolectric `test`), not the plain unit-test one — a bigger lift than this
  one fix justified against the rest of this round's scope; flagged rather
  than silently skipped.
- **M3 (the review's own "M5"), phone pager/button collision and no wrap.**
  Compact copy's own bottom padding is now 104dp (was a uniform 40dp), and
  the pager's own bottom padding 40dp on compact (was a uniform 18dp) —
  the web's own `padding-bottom: 104px`/`bottom: 40px` split
  (`home.css:351,356`). The action row is now a `FlowRow`
  (`horizontalArrangement`/`verticalArrangement` both `spacedBy(12.dp)`),
  matching the web's own `flex-wrap: wrap` — "Details" no longer squeezes
  mid-word at a narrow width or a larger font scale.

### Lows

- **L1**, `LibraryHome.kt`'s blend now reads `!isHome || !hasCover` (was
  `!isHome` alone) — Home with no cover (new library, kids profile) reads
  fully solid immediately, rather than ramping as if item 0 were the cover.
  `hasCover` added to the `derivedStateOf`'s own `remember` keys.
- **L2**, the feature pair now sits in a `Row(Modifier.height(IntrinsicSize.Max))`
  with `fillMaxHeight()` added to each card's own `heightIn(min = cardHeight)`
  — the taller of the two sets both, rather than each card keeping its own
  independent height. Verified against the `matchParentSize()` image/scrim
  concern the review raised: the existing clipping-regression test stayed
  green.
- **L3**, `ResumeCard`'s film subtitle now reads `factsLine(set.year,
  set.durationSecs)` (reused, not reimplemented) — the web's own "1995 · 2h
  4m", not a bare year with a dead `listOfNotNull(..., null)`. `card.caption`
  ("1h left"/"Next up") is not drawn (this card is deliberately quiet, per
  its own doc comment) but now reaches TalkBack through a zero-size `Spacer`
  carrying it as a `contentDescription`, merged into the card's own
  announcement the same way its visible text already is.
- **L4**, `ThisMonth` now draws one rule down the column's own left edge
  (a 1dp `Box`) and a `HorizontalDivider` under each row, replacing a
  `.border()` on every row that boxed all four sides of each.
- **L5**, `fraunces.ttf` deleted — confirmed first that nothing (main code,
  TV, tests) referenced `R.font.fraunces` any more once H2's fix landed.
- **L6**, pager dots now carry `selected = index == current` (TalkBack's
  nearest match for the web's `aria-pressed`) and both the dots and the
  pause circle grew to 48dp touch targets (were 28dp/32dp), centring the
  same visual size rather than changing it.
- **L7**, `HomeScreen`'s `LazyColumn` is now wrapped in a
  `BoxWithConstraints`; `HomeShelfRow`'s own poster formula reads that
  `BoxWithConstraints`'s own measured `columnWidth` (rail already excluded,
  since `HomeScreen` sits inside `LibraryHome`'s own `Box(Modifier.weight(1f))`)
  rather than the window's raw `screenWidthDp` — the CSS `%` `catalog.css:35`
  poster formula is relative to its own rendered container, unlike every
  other fluid size on this page, which is `vw`-relative and stayed on the
  window width on purpose (`HomeCover`'s own doc comment already explains
  the distinction). Recently Added and Latest series now hold up to 8:
  `magazineHomeOf`/`homeRowsOf` gained `recentLimit`/`posterLimit` params
  (defaulting to the existing shared `limit`, so every other caller —
  `LatestScreen`, both TV call sites — is unaffected), and the phone Home
  call site in `CatalogScreen.kt` passes the web's own `POSTER_ROW_LIMIT`
  (new `HOME_POSTER_ROW_LIMIT = 8` beside `HOME_ROW_LIMIT`). `HOME_ROW_LIMIT`
  itself stays 6 and ungouched — TV's own `TvHomeRow` relies on it for a
  deliberate, documented reason ("six plates fit a television without a
  rail"), unrelated to the web's own Recently Added/Latest series limits;
  raising it would have shrunk TV's own row to fit 8.
- **L8**, the cover's height is a floor now (`heightIn(min = coverMinHeight)`
  on `CoverSlide`'s own root `Box`, the same pattern `HomeFeatures`' cards
  already use), not an exact number — a short window (split screen) can
  grow the text past it instead of clipping. `HomeCover.kt`'s own comment
  now explains why nothing here still needs an exact height (nothing asks a
  child to `fillMaxSize()` under this `LazyColumn` item's own unbounded
  height, which is the actual trap the old comment's `HorizontalPager`
  reasoning was gesturing at after `HorizontalPager` itself was already
  gone).
- **L9**, `HomeFeatures.kt:75`'s "this phase shipped" reworded to drop the
  plan reference. `Type.kt`'s stray KDoc (attached to `read()`, still
  describing Fraunces as variable) rewritten for what's actually true now —
  Newsreader/Geist stay variable, Fraunces went static, see this file's own
  top note. `PageTitle`'s own doc no longer links the deleted `display()`
  function or claims uniquely showing the bug; it now says what actually
  happened — first to surface it, not the only face carrying it.
  `HomeCover.kt` is 203 lines (was 252): `CoverSlide` moved to its own file
  (`CoverSlide.kt`, 171 lines), the same split `CoverControls.kt` already
  used for the pills/pager/scrim. Changelog's scroll-position bullet now
  says "a viewport-percentage heuristic", not "tracked scroll deltas" (0.70.0
  already read the scroll position); a new bullet notes the static Fraunces
  change is app-wide, phone and TV both, not phone-only.

### Gate and device verification

Gate: `./gradlew :ui-mobile:testDebugUnitTest :feature:catalog:testDebugUnitTest :ui-tv:testDebugUnitTest :core:designsystem:testDebugUnitTest lint :app:assembleDebug` green, run twice (once before, once after the H1 regression test was added). Reinstalled on `caad49da`; avatar checked "T" before install, after install, and again after switching to Light theme and back. Fresh `home-parity-sheet-1.png`-equivalent at a literal scroll 0 confirms cover→features ordering still holds; `home-parity-sheet-light-depth1.png` (new) is the Light-theme depth-1 shot the scrim fix needed, taken with the appearance setting returned to its own starting point (Dark · Green) afterward, confirmed by a final screenshot.

### Files touched this addendum

- `ui-mobile/src/main/kotlin/ui/catalog/home/HomeCover.kt` — H1, M1, L8 (rewritten; `CoverSlide` extracted)
- `ui-mobile/src/main/kotlin/ui/catalog/home/CoverSlide.kt` — new (M1's FlowRow/padding split out of `HomeCover.kt`)
- `ui-mobile/src/main/kotlin/ui/catalog/home/CoverControls.kt` — M2, L6
- `ui-mobile/src/main/kotlin/ui/catalog/home/HomeFeatures.kt` — L2, L9
- `ui-mobile/src/main/kotlin/ui/chrome/LibraryHome.kt` — L1
- `ui-mobile/src/main/kotlin/ui/catalog/home/ResumeCard.kt` — L3
- `ui-mobile/src/main/kotlin/ui/catalog/home/RecentBand.kt` — L4
- `ui-mobile/src/main/kotlin/ui/catalog/HomeScreen.kt` — L7 (column width)
- `feature/catalog/src/main/kotlin/HomeShelves.kt` — L7 (`HOME_POSTER_ROW_LIMIT`, `posterLimit`)
- `feature/catalog/src/main/kotlin/MagazineHome.kt` — L7 (`recentLimit`)
- `ui-mobile/src/main/kotlin/ui/catalog/CatalogScreen.kt` — L7 (wired at the mobile Home call site)
- `core/designsystem/src/main/kotlin/Type.kt` — L9 (doc fixes)
- `core/designsystem/src/test/kotlin/PageTitleFontTest.kt` — H2
- `core/designsystem/src/main/res/font/fraunces.ttf` — deleted (L5)
- `docs/project-changelog.md` — L9
- `ui-mobile/src/test/kotlin/ui/catalog/home/HomeCoverTest.kt` — H1 regression test

### Unresolved from this addendum

- **M2's contrast assertion** — verified on-device, not by an automated test; see M2 above for why.
- **The review's own "unresolved questions"** (compact cover's Light-theme contrast on the web itself; whether 6-vs-8 on Recently Added/Latest series should be a documented difference instead; whether TalkBack should stop rotation entirely vs. only pause on focus) were not decided here — they were framed as open questions for the lead, not fixes, and this round left them exactly that.
- Version/changelog (beyond the L9 correction above) not bumped again — same reasoning as addenda 1 and 2: fixes to this phase's own unshipped work, still uncommitted.
