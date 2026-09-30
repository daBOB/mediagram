# Phase 03 — second on-box review round (three items left)

Follow-up to `fullstack-developer-260930-0130-phase-03-onbox-fixes-report.md`, after the
coordinator's re-walk of `568b95d2`. Branch `worktree-agent-a4e8c1b7d67ee7a8d`, now at `298a7104`.
Still `0.84.0` — same precedent as before, no bump for an unreleased phase's own on-box fixes.

Verified this round, no code touched: Down in the Anime grid clearing the bar; Anime's band
headings; Collections' Down-from-pill landing on the first franchise; Anime film → title page,
Back returning to the plate clear of the bar; Home unchanged.

## What was wrong, and the actual root cause of each

1. **Up in `TvWall`'s own grid still put a plate behind the bar; Down looked fine.** The previous
   round's own fix wrapped `CompositionLocalProvider(LocalBringIntoViewSpec provides barClearance)`
   around each lazy item, inside the `items { }` block — that sits *below* the grid's own point in
   composition, and the grid's own scroll-into-view machinery (`ContentInViewNode`, attached to
   the `LazyVerticalGrid`'s own modifier) reads whichever spec is ambient *there*, not at an
   item's. The override never reached it at all; Down only ever looked clear by accident — a
   downward reveal settles with the target's own bottom flush against the viewport's own bottom,
   nowhere near the bar, so it never needed the override to look right. Moved the provider to wrap
   the whole grid instead — `TvHome`'s own `LazyColumn` already does exactly this, and its own
   doc on the header's own reset already described the right shape; the code just was not built to
   it. Confirmed by reverting the wrap (grid-level → item-level) with the rest of this round's
   fixes untouched: `TvWallStateTest.pressingUpKeepsTheFocusedPlateClearOfTheBar` failed against
   it and passes against the fix.

2. **Up or Down across a heading jumped to the far row's own last column.** `TvWall` relied on
   Compose's own default two-dimensional focus search across the full-width heading between two
   sections, and in this exact shape — a short row followed by a longer one, or the other way —
   that search does not keep the column; it lands on whichever plate composition happened to place
   right before or after the heading, the row's own last one. `sectionCrossingsOf` (new,
   `TvWallCells.kt`) computes the up/down targets for the one row on each side of every heading —
   the same column across it, or that row's own last item when it is shorter — and `TvWall` wires
   them by hand with `focusProperties`, the same pattern `TvDepartmentsBar`'s own pills already
   use for their own `down` target.

   Robolectric's own focus search happens to already keep the column correctly for this exact,
   evenly-spaced synthetic grid, wired or not — confirmed by neutralising the wiring and rerunning
   `TvWallStateTest`'s own end-to-end key-press test, which kept passing regardless. That test
   stays as the rule's own documented, end-to-end shape, but `TvWallCellsTest`'s own pure-function
   tests are what actually pin the rule — confirmed failing against a version of
   `sectionCrossingsOf` that reproduces the box's own bug outright (always the far row's own last
   item), passing against the fix.

3. **Collections hero: the quote still overlapped the title vertically.** The width fix from the
   previous round (480dp/320dp) only ever addressed the *horizontal* case; the copy column is
   bottom-anchored and grows upward as the title, kicker and line need more room, while the quote
   sat at a fixed top-anchored offset — nothing coordinated the two vertically at all, so a tall
   enough title (or the fixed 81.6sp ceiling this hero always evaluates at) could still push its
   own top up into the quote's own band regardless of width. The tablet's own hero avoids this
   without ever deciding it explicitly: its own floor height (420–600dp, `heroMinHeight`) is
   generous enough, and its own quote sits a fixed 12% down from the top of that floor, that the
   two never actually reach each other in practice — nothing to port directly, since television's
   own hero is fixed at 360dp specifically to keep the first row on the first screen.
   `TvHeroWordsAboveQuote` (new) measures the words column first, then places the quote at its
   usual top-right floor only if that leaves it clear; otherwise it retreats to sit flush above the
   words, or is left out entirely if even the very top has no room. Verified with fixed-size fakes
   rather than real text (the actual overlap depends on font metrics Robolectric cannot be held to
   — this file's own existing arithmetic test says so already): the floor-still-clear, retreat and
   drop-entirely cases are three separate tests, each confirmed failing against the previous,
   unconditional-floor placement and passing against the fix.

4. **Collections: "Franchises · N" still landed half behind the bar after arrival.** Not the same
   mechanism as item 1: this page's own arrival `LaunchedEffect` calls `listState.scrollToItem(...)`
   directly, which places the target flush against the list's own top — `scrollToItem` never reads
   `LocalBringIntoViewSpec` at all, that mechanism being specific to a *focus-triggered* scroll.
   Wrapping the list in `barClearance` (this round's own first pass at 3b, from the prior report)
   only ever helps a later D-pad move that goes through the focus system; it does nothing for this
   arrival call. Added a plain `listState.scrollBy(-clearancePx)` right after `scrollToItem`, backing
   the list off by the bar's own clearance — every arrival target on this page is past "hero" (the
   one item this page still lets bleed under the bar at scroll 0, untouched by either fix).
   Confirmed by removing the `scrollBy` alone: `TvCollectionsPageStateTest`'s own new test failed
   against that, passes with it restored.

   `TvMoviesDepartmentPage` and `TvDocumentariesDepartmentPage` call the identical
   `listState.scrollToItem(...)` pattern for their own arrival, and the coordinator's own walk did
   not flag either — left untouched rather than assumed broken on no evidence, but the same class
   of gap likely exists there too if a future walk reaches a heading landing exactly at the top of
   either page.

## Files

- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvWall.kt` — the bar-clearance provider now wraps
  the whole grid; `sectionCrossingsOf`'s own up/down targets wired per plate via `focusProperties`.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvWallCells.kt` — new; `WallCell`/`cellsOf` moved
  here from `TvWall.kt` (unchanged) alongside the new `SectionCrossings`/`sectionCrossingsOf`, to
  keep `TvWall.kt` itself near the file-size guideline.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvHeroWordsAboveQuote.kt` — new; the
  `SubcomposeLayout` that keeps the hero's own words and quote clear of each other vertically,
  moved out of `TvDepartmentHero.kt` for the same reason.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvDepartmentHero.kt` — calls the new layout instead
  of two independently `Box`-aligned children.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvCollectionsPage.kt` — `scrollBy` added right after
  the arrival `scrollToItem`.
- Tests: `TvWallStateTest.kt` (two new end-to-end tests, `TestPlate`'s own size now
  parameterised — the previous round's own 120dp plate left over three rows visible on a 540dp
  screen at once, too many for a single Up press to ever need to scroll at all); `TvWallCellsTest.kt`
  (new, pure); `TvDepartmentHeroStateTest.kt` (three new tests against `TvHeroWordsAboveQuote`
  directly); `TvCollectionsPageStateTest.kt` (new).

## Tests

- **Item 1**: `TvWallStateTest.pressingUpKeepsTheFocusedPlateClearOfTheBar` — 260dp plates (barely
  more than one row fits on 540dp at once, forcing a real scroll on every single-row Up press,
  unlike the original 120dp version); Down three times to the last row, Up once back, asserts the
  newly focused plate's own top against `TvBarClearance`. Confirmed failing against the previous
  round's per-item wrap, passing against this round's grid-level one.
- **Item 2**: `TvWallCellsTest` (new, plain JUnit, no Robolectric) — the box's own repro numbers
  (a ten-item section with a four-item last row, then six more); confirmed failing against a
  version of `sectionCrossingsOf` that always returns the far row's own last item.
  `TvWallStateTest.upAndDownAcrossAHeadingKeepTheColumnRatherThanJumpingToTheFarRowsLastItem` pins
  the same shape end to end with real key presses — its own doc is explicit that Robolectric's
  own focus search already agrees with the fix for this particular evenly-spaced grid, wired or
  not, so it cannot by itself tell the fix apart from having none; `TvWallCellsTest` is what
  actually does that.
- **Item 3**: `TvDepartmentHeroStateTest` — `quoteSitsAtItsOwnFloorWhenTheWordsLeaveRoom`,
  `quoteRetreatsAboveTheWordsRatherThanOverlapThem`, `quoteIsLeftOutWhenEvenTheVeryTopHasNoRoomForIt`,
  driving `TvHeroWordsAboveQuote` directly with fixed-size fakes. All three confirmed failing
  against the previous, unconditional-floor placement.
- **Item 4**: `TvCollectionsPageStateTest.theFranchisesHeadingClearsTheBarOnceItsOwnRowIsScrolledToTheTop` —
  confirmed failing with the `scrollBy` removed.
- `:ui-tv:testDebugUnitTest` (full suite) and `scripts/check.sh` — both green.

## Left for the lead (on-box, no device access here)

1. All four fixes, visually: a focused plate's top clear of the bar moving Up in Anime's grid;
   Up and Down across the Series/Films heading keeping the column; the Collections hero's quote no
   longer over the title; "Franchises · N" clear of the bar after Down from the pill.
2. Whether the same `scrollToItem`-bypasses-clearance gap (item 4) actually shows up on Movies' or
   Documentaries' own arrival — not walked, not touched, flagged above only as a live possibility.

## Unresolved questions

- None outstanding from this round; the previous round's "repeated Up creep" sub-issue was not
  re-raised in this walk, so it is assumed resolved by item 1's own fix here (the same class of
  bug, the ambient spec never actually reaching the grid) — worth a specific re-check on the box
  regardless, since it was never independently reproduced here either time.

**Status:** DONE
**Summary:** All 4 items from this round's on-box review fixed and unit-tested (2 with a bisection-verified regression test, 2 with pure-function tests where Robolectric's own focus search coincidentally already agreed); full ui-tv suite and scripts/check.sh green; committed as `298a7104` on `worktree-agent-a4e8c1b7d67ee7a8d`.
**Concerns/Blockers:** None blocking. All four fixes need the coordinator's own box walk to close out, as before — no device access here.
