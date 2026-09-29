# Phase 03 — four fixes from the on-box review of 0.84.0

Follow-up to `fullstack-developer-260930-0023-phase-03-departments-report.md`, after the
coordinator's on-box review (192.168.0.35:5555, benchmark build of `4eff62cd` plus the
coordinator's own `1d64d9dd`, profile "TV test"). Branch `worktree-agent-a4e8c1b7d67ee7a8d`,
now at `568b95d2`. Still `0.84.0` — unreleased, no version bump, following the same precedent
as phase 02's own on-box fixes (`0.83.0` stayed put across three post-review fix commits).

`1d64d9dd` ("Up out of a page reaches the selected pill") was already on the branch and
untouched by this round — no second `focusProperties { onExit }` was reintroduced on
`TvLibraryChrome`'s content Box.

## What was wrong, and the actual root cause of each

1. **Plates behind the bar on `TvWall`-based department pages (Anime, and by construction
   Series/Tutorials).** `TvWall`'s `LazyVerticalGrid` had no bar clearance of its own — Home's
   `LazyColumn` got a custom `BringIntoViewSpec` in phase 02's own on-box round (report above,
   point 3), but the grid never did, so moving a plate up or down left its own top wherever the
   platform default settled it, sometimes behind the bar. Generalised Home's own private
   `TvHomeBringIntoViewSpec` (and the `TvHomeBarClearance` constant that lived beside
   `TvCoverSlide`) into `TvBarClearanceBringIntoView.kt`, shared by both `TvHome.kt` and
   `TvWall.kt` now rather than a second copy. Each heading and plate cell in the grid reads it;
   `header`'s own cell (a department's Continue row, drawn sideways) resets back to the ambient
   default the same way Home's own sideways sections already do, for the same reason: a
   horizontal `Row` reading the vertical clearance as a horizontal offset reserves blank space
   on its own left the bar never touches. Pinned by `TvWallStateTest.theGridReadsTheBarClearanceSpecButTheHeaderResetsToTheAmbientDefault`,
   which asserts the two ambient values actually differ — this failed on the first pass (see
   "what went wrong while fixing this" below) before the wiring was corrected.

   The repeated Up-from-Series-row creep (a few px per press, focus lost entirely by the sixth)
   was not independently reproduced or diagnosed — no device access here. It is the same class
   of bug (bar clearance not applied, or applied inconsistently across repeated scroll passes)
   and should be re-checked on the box now that the grid reads the shared spec; if it persists
   after this fix it is a second, separate bug.

2. **Anime's "Series"/"Films" headings drawn smaller than the web's own `deptRow` heading.**
   `TvWall`'s `headings` map rendered each section label with `TvSectionHeading` — the small
   label genre pages and search results use — rather than `TvBandHeading`, the band Anime's own
   Continue watching row already draws and the same one the web gives every `deptRow` including
   Continue (`anime-department.js:57-67`). Changed the one call site in `TvWall.kt`.

   **What went wrong while fixing this the first time:** wrapping the heading in
   `Box(Modifier.padding(top = Spacing.large)) { TvBandHeading(...) }` — matching how
   `DeptRow`/`GenreTileRow` pad their own headings in a plain `Column` — silently dropped the
   *next* lazy item (the plate right after the heading) from composition inside
   `TvAnimeDepartmentPageStateTest`, with no exception, only a missing node. Isolated by
   reverting `TvWall.kt` wholesale (passed), then re-adding only the heading change with the
   `Box` wrapper removed (passed) — `TvBandHeading(...)` is now called directly, no wrapper, no
   explicit top padding, since `LazyVerticalGrid`'s own `Arrangement.spacedBy(Spacing.medium)`
   already spaces every row including this one; the exact Compose/Robolectric interaction behind
   the original silent drop was not further chased once a working, minimal shape was found.

3. **Collections: Down from the pill didn't enter the page.** The page used to be a plain
   `Column` (hero + franchise row) sitting above a second, separately-scrollable `Column` around
   `TvLists`. With enough franchises the hero pushed that franchise row toward the bottom of the
   screen and past it, and nothing could scroll the *outer* Column to bring it into view —
   Compose refuses two nested vertical scrollables, so the outer one was never lazy or
   scrollable at all. Rewritten as one `LazyColumn` end to end — hero, franchise row, "Your
   lists" heading, each list row, "＋ New list" — mirroring the tablet's own `CollectionsScreen`
   restructuring for the identical reason. `TvListRow` was extracted out of `TvLists.kt` so both
   pages draw the same list row rather than one copying the other's `Text`/`onClick` pair.
   `TvCollectionsPageTestTag` added (matching `TvMoviesDepartmentPageTestTag`'s own precedent) so
   a test can scroll this list directly.

4. **Collections hero: the quote overlapping the title.** `HeroCopyMaxWidth` (640dp) was the
   tablet's own `DEPT_COPY_MAX_WIDTH` cap, which assumes a "wide" layout starting past 900dp and
   commonly running well beyond it — room enough that a 320dp quote column never reaches into a
   640dp copy column. Television's width is always exactly 960dp, where the two, each measured
   from its own gutter, overlap by up to 80dp — which a long title plus a wrapping tagline
   (Collections') actually hit. Narrowed to 480dp, which leaves a guaranteed 80dp gap between the
   two at every department, not only the one that first showed it — the doc on `HeroCopyMaxWidth`
   spells out the arithmetic so the next department with a long title and a tagline doesn't
   rediscover it.

   Pinned with an arithmetic test rather than a rendered-bounds one:
   `TvDepartmentHeroStateTest.theCopyAndQuoteColumnsNeverOverlapAtTheFixedTvWidth` computes both
   edges directly from the constants. A `boundsInRoot`-based version was tried first and passed
   even at the old, overlapping 640dp — Robolectric's font fallback renders "COLLECTIONS"
   narrower than Fraunces does on a real device (the same gap `TvCoverSlideStateTest`'s own doc
   already names), so a bounds check here would have given false confidence for exactly the bug
   being fixed.

## Files

- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvBarClearanceBringIntoView.kt` — new;
  `TvBarClearance` (moved from `TvCoverSlide.kt`, unchanged value) and
  `rememberTvBarClearanceBringIntoView()`, generalised out of `TvHome.kt`'s own private class.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvHome.kt` — private `TvHomeBringIntoViewSpec`
  removed; calls the shared `rememberTvBarClearanceBringIntoView()` instead.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/home/TvCoverSlide.kt` — `TvHomeBarClearance`
  removed; reads the shared `TvBarClearance`.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvWall.kt` — every heading/plate cell wrapped in
  `CompositionLocalProvider(LocalBringIntoViewSpec provides barClearance)`; `header`'s cell reset
  to the ambient default; heading now `TvBandHeading` (point 2).
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvCollectionsPage.kt` — rewritten as one
  `LazyColumn` (point 3); `TvCollectionsPageTestTag` added.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvLists.kt` — `TvListRow` extracted (shared with
  `TvCollectionsPage`); `countLabel` made `internal` for it to reuse.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvDepartmentHero.kt` — `HeroCopyMaxWidth`
  640dp → 480dp, made `internal`; `HeroQuoteMaxWidth` made `internal`; quote column carries its
  own test tag (point 4).
- Tests: `TvWallStateTest.kt` (new, point 1); `TvDepartmentHeroStateTest.kt` (new, point 4);
  `TvCoverSlideStateTest.kt` (reference to the moved constant updated, no behavioural change);
  `TvCatalogScreenStateTest.kt` (`collectionsShowsFranchisesAndLists` now scrolls to "Your lists"
  rather than assuming it's already composed, and runs at the real `w960dp-h540dp` TV size);
  `TvKeptWallStateTest.kt` (`collectionsListsTheViewersListsWhileKeepingTheRemoteOnThePill` now
  scrolls `TvCollectionsPageTestTag` to index 3, the "Later" list row in the rewritten single
  list, rather than the now-gone `TvListsTestTag`).

## Tests

- **Point 1**: `TvWallStateTest.theGridReadsTheBarClearanceSpecButTheHeaderResetsToTheAmbientDefault` —
  asserts the header's own `LocalBringIntoViewSpec.current` and a plate's differ. No rendered
  scroll distance is asserted (Robolectric has no real window manager to animate one) — this
  pins which spec each part of the grid reads, the same split `TvHomeStateTest`'s own doc uses
  for the same reason.
- **Point 2**: covered indirectly — `TvAnimeDepartmentPageStateTest`'s existing tests (a show
  under Series, a film under Films) exercise the same cells the heading fix touches and were the
  ones that caught the `Box`-wrapper regression during the fix; no new test asserts the heading
  is specifically `TvBandHeading` vs `TvSectionHeading` by size, since both draw the same text
  and only their type differs.
- **Point 3**: `TvCatalogScreenStateTest.collectionsShowsFranchisesAndLists` and
  `TvKeptWallStateTest.collectionsListsTheViewersListsWhileKeepingTheRemoteOnThePill`, both at
  `w960dp-h540dp`, both scrolling the new `TvCollectionsPageTestTag` list to reach content below
  the fold — the coordinator's own request for a Robolectric test at 960×540. Neither drives an
  actual D-pad Down from the pill (no window manager here); both confirm the list itself is one
  scrollable able to reach every section, which is what made Down unable to reach the franchises
  in the first place.
- **Point 4**: `TvDepartmentHeroStateTest.theCopyAndQuoteColumnsNeverOverlapAtTheFixedTvWidth` —
  arithmetic, not bounds; confirmed failing at 640dp and passing at 480dp before landing.
- `:ui-tv:testDebugUnitTest` (full suite, 396 tests), `:ui-mobile:testDebugUnitTest`,
  `:feature:catalog:testDebugUnitTest` and `scripts/check.sh` (clippy, cargo test, gradle
  test+lint+compileDebugAndroidTestKotlin) — all green.

## Left for the lead (on-box, no device access here)

1. All four fixes, visually: a focused plate's top clear of the bar moving Up/Down in Anime's
   (and Series'/Tutorials') wall; Anime's "Series"/"Films" headings now the same band as
   Continue watching; Down from the Collections pill landing on the first franchise with the
   page scrolling it in; the hero's quote no longer over the title on Collections.
2. The repeated-Up creep/focus-loss from Anime's Series row toward Continue watching — not
   reproduced here. If it persists after this fix despite the grid now reading the shared bar
   clearance, it is a second bug, not this one.
3. Whether Down out of the Collections pill actually reaches the first franchise on a real
   remote now that the page is one `LazyColumn` with a `focusRestorer` — the test suite confirms
   the list can scroll there and that the franchise row keeps a `focusRequester`, not the actual
   D-pad traversal.

## Unresolved questions

- The exact Compose/Robolectric mechanism behind the `Box(Modifier.padding(...))`-drops-the-
  next-item bug (point 2) was not identified, only isolated and avoided. If a future change
  wants to pad a `WallCell.Heading` again, budget time to re-investigate rather than assuming
  the wrapper is safe.
- `TvWall.kt` (218 lines) and `TvCollectionsPage.kt` (210 lines) both sit slightly over the
  200-line guideline after these changes. Splitting either further looked like it would fragment
  one cohesive composable (the grid's own cell-rendering `when`, the page's own single
  `LazyColumn`) for a marginal line-count win — left as-is; flagging rather than silently
  exceeding it.

**Status:** DONE
**Summary:** All 4 coordinator-reported issues fixed and unit-tested; full ui-tv/ui-mobile/feature:catalog suites and scripts/check.sh green; committed as `568b95d2` on `worktree-agent-a4e8c1b7d67ee7a8d`, on top of the coordinator's own `1d64d9dd`.
**Concerns/Blockers:** Item 1's repeated-Up creep/focus-loss sub-issue and the Down-from-pill real-remote traversal (point 3) are unverified here — no device access; both need the coordinator's own box walk to close out, per their original request.
