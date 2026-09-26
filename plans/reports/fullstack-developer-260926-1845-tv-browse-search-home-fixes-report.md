# TV browse/search/home review fixes — implementation report

Plan/review: `plans/reports/code-reviewer-260926-1835-tv-editorial-parity-review-report.md` (findings),
`plans/reports/fullstack-developer-260926-1722-android-parity-tv-surface-report.md` (what phase 6 built).
Work context: `/home/andre/Workspace/mediagram-tv-review` (worktree `review/tv-editorial-parity`).
File ownership respected: only `android/ui-tv/src/main/kotlin/ui/tv/catalog/{TvSearch*,TvHome*,TvCoverStory,
TvFeatureStrip,TvDepartmentPages*,TvHomeRow,TvCatalogScreen*}.kt` (+ splits) and their tests. No edits to
`TvLibrary.kt`, `TvCollection*.kt`, `TvTitlePage.kt`, `TvPersonPage.kt`, or any other file outside that list —
another agent was working in the same worktree on those in parallel; see "Concerns" for what that caused.

## What changed, per finding

**C1 (row focus counts compositions, not positions).** `TvSearchResults.kt`'s `LazyColumn` used
`var index = 0; val at = index++` inside a lazy item's own content — that content re-runs every time the
item recomposes (scrolled into view, or an unrelated input changes), in whatever order that happens, never
guaranteed to match list order. Replaced with `sectionStartsOf(sections)` — a pure, deterministic
per-section start offset — and `at = start + localIndex`, `localIndex` being `itemsIndexed`'s own stable
index. No mutable counter left anywhere in the item content.

**M1 (department pages steal focus from Search/Menu).** `TvPage`'s own default is
`takesArrivalFocus = true`, which re-provides `LocalTakesArrivalFocus` regardless of the ambient value —
`TvMoviesDepartmentPage`/`TvShowsDepartmentPage` called `TvPage {}` without ever reading or forwarding the
ambient, so a department page always grabbed the remote even when the catalogue said not to
(`TvCatalogScreen`'s own `LocalTakesArrivalFocus provides !backToMasthead`). Both now capture
`val takesFocus = LocalTakesArrivalFocus.current` *before* entering `TvPage`, pass it through as
`TvPage(takesArrivalFocus = takesFocus)`, and gate every `requestFocus()` call on it — including inside the
new `DeptRow`/`DeptEntryRow`/`GenreTileRow` (which take `takesFocus` as an explicit parameter rather than
reading the ambient themselves, since `TvPage` already re-provides it below them).

**M2, department part (rows cut at 6).** `ShowsDepartmentPage`'s Continue/Popular/New episodes rows went
through `TvHomeRow`, whose `PlateRow` always lays out exactly `HOME_ROW_LIMIT` (6) slots — Home's own,
deliberate rule, wrong for a department row that can hold up to a dozen. Replaced those three calls with a
new `DeptEntryRow` (`TvDepartmentRows.kt`), a lazy, horizontally-scrolling row over `TvEntryPlate` that
shows every stop it is given. Home's own rows are untouched — still `TvHomeRow`/`PlateRow`, still six
slots, matching the web's own home-shelves rule.

**M3 (Series/Tutorials arrival focus scrolls past the hero and header rows).** `TvWall`'s own arrival-focus
default (plate 0) fired unconditionally underneath the header, with no focus wiring on the hero or the three
`TvHomeRow`s above it at all (`focusAt = null` unconditionally in the phase-6 code). New `showsDeptTargetOf`
(`TvDepartmentTargets.kt`, pure) resolves, in order: a `restoreKey` matching Continue/Popular/New episodes'
own entries, then — with no `restoreKey`, or one matching none of them — the hero if it is focusable
(`dept.lead` has a backdrop), then the first non-empty header row, `null` only when there is nothing at all
in the header, at which point `TvWall`'s own plate-0 default is left to run. That "leave it alone" case is
wired via a nested `CompositionLocalProvider(LocalTakesArrivalFocus provides (takesFocus && target == null))`
around the `TvWall` call, so the two mechanisms never fight over the remote.

**M6 (magazine home too tall, arrival scrolls the cover off).** Two separate bugs conflated in the finding:
- Feature cards reused `TvPlate`, whose art box is a fixed 2:3 poster crop — three of them under a 21:9
  cover pushed the row below off a 540dp screen and cropped most of the backdrop each card was picked for.
  `TvFeatureStrip.kt` now draws its own `TvFeatureCard` (same `Card`/`TvFocus` treatment as `TvPlate`, own
  16:9 art box) rather than stretching a poster shape into a landscape one.
- `TvHome`'s arrival focus always targeted the first row's first stop; the magazine's cover and features sit
  *above* every row, so arrival scrolled straight past them. `TvHome` now resolves arrival to the cover's own
  "Watch now" whenever no `restoreKey` names a stop still on a row (`landOnCover`), via a second
  `FocusRequester` (`coverFocus`) `TvCoverStory` always attaches to its first slide's Watch now, and used as
  `.focusRestorer`'s own fallback when a cover is present. Coming back to a specific row/plate still lands
  there, unchanged.

**N1 (Watch now opens Details; department hero has the wrong kicker/no web rotation).**
- `TvCoverStory`'s `onPlay` was wired to `onOpenTitle` at both call sites (`TvHome`, the Movies department
  hero) — the same callback `Details` used, so "Watch now" never actually played anything
  (`home-cover.js:137`: `play(set)`). Added a genuine `onPlay: (setId) -> Unit = onOpenTitle` parameter on
  `TvCatalogScreen`/`TvCatalogBody`/`DepartmentOrShelfWall`/`TvMoviesDepartmentPage`, threaded through to the
  cover's Watch now, distinct from `onOpenTitle` (Details). **This still needs one more wire-up outside my
  ownership — see Concerns.**
- Department hero: added a `kicker` parameter to `TvCoverStory`/`TvCoverSlide` (default `"Cover story"` for
  Home's own magazine header), and both department pages now pass `"Only in your library"` —
  `department-hero.js`'s own words, not the magazine cover's. Rotation was already a non-issue for a
  department hero (`films.size` is always 1, so `rotates = films.size > 1` was already `false`); the wrong
  kicker copy was the real, fixable gap.
- Series/Tutorials hero keeps both buttons opening the show's own page (there is no single episode a show's
  hero could sensibly "play"), unchanged from phase 6 — the web's department hero has no such buttons at
  all, a small, deliberate TV addition kept for "every screen has a sensible arrival focus" (Details/Watch
  now here is what the hero being focusable, per M3, is for).

**N7 (Movies front page re-requests focus on republish; genre/all-films restoreKey unmatched).**
- `LaunchedEffect(dept, restoreKey)` re-fires every time `dept` gets a fresh instance (any library
  republish rebuilds `MoviesDepartment`, same content, new object) — now keyed on the *resolved*
  `moviesDeptTargetOf(dept, restoreKey)` result (a small, structurally-equal `Pair<String, Int>`), which
  only changes when the actual target does.
- `moviesDeptTargetOf` (`TvDepartmentTargets.kt`) now also matches a genre name against `dept.genres` and
  `TvMoviesPageEntryKey` (already defined and set by `TvLibrary.kt` when opening the full wall — it existed,
  it was simply never read on the way back) against `"all"`, both previously falling through to the
  page's default first-row target.

**N9 (Movies front page composes ~60 plates at once).** `DeptRow`/`GenreTileRow` were a plain
`Row + horizontalScroll` — every plate composed regardless of visibility. Both rewritten to `LazyRow` with a
`LazyLayoutCacheWindow` (320dp ahead/behind, `TvWall`'s own numbers, at a single row's scale), plus a shared
`scrollThenFocus` helper that scrolls a targeted stop into range before requesting its focus — the same
two-step `TvWall` already uses, needed because a lazy row does not compose what is not near the viewport.

**Item 9 (local `byIdOf` → public `catalog.allSetsById`).** `TvDepartmentPages.kt`'s `byIdOf` (scoped to one
shelf's own shows) removed; `TvCatalogScreen.kt` now computes `allSetsById(shelves)` once (every shelf, the
same pool the phone's own department pages read, per that function's own doc) and passes it down as
`DepartmentOrShelfWall(byId = ...)`.

**Item 11 (plan references in comments).** Fixed at `TvCatalogScreen.kt:114` ("phase 3"),
`TvHome.kt:46-48` ("recorded in the plan… before this phase"), `TvDepartmentPages.kt:133`/now
`TvMoviesDepartmentPage.kt` ("the phase-3 `MOVIES_PAGE` frame") — reworded to explain the decision itself,
not where it was written down. Also fixed one pre-existing instance in
`TvCatalogScreenStateTest.kt:60` ("phase 3") while already touching that file for M1's test.

**Item 12 (200-line limit).** Split `TvSearchResults.kt` (row composables → new `TvSearchRows.kt`),
`TvSearch.kt` (`RowAsk` → `TvSearchGroups.kt`, which already held the sibling `SearchEntry`/`SearchSection`
types), `TvCatalogScreen.kt` (masthead/restore-key bookkeeping → new `TvCatalogNav.kt`; the tab-content
dispatch → new `TvCatalogBody.kt`), and `TvDepartmentPages.kt` (`TvMoviesDepartmentPage` → its own file;
`TvShowsDepartmentPage` + `leadCover` → its own file; the row-drawing composables → `TvDepartmentRows.kt`;
the pure target-resolution functions → `TvDepartmentTargets.kt`; `TvMoviesPage`/`DepartmentOrShelfWall` kept
in `TvDepartmentPages.kt`). Every owned file is now 64–199 lines.

## Item 10 — "Recently added" running Z→A: investigated, not a code defect found

Traced the whole path with evidence, no edit made (per instruction, `feature/catalog` is off limits):

- `MagazineHome.kt:41`: `recentlyAdded = movies.sortedByDescending(MediaSet::addedAt).take(limit)` —
  Kotlin's `sortedByDescending` is a stable sort; two sets with an *identical* `addedAt` keep their
  relative order from `movies`.
- `Shelves.kt:36`: the Movies shelf's own pre-sort order (what `movies` is built from) is **alphabetical,
  A→Z**, by title. A stable descending-by-`addedAt` sort over same-timestamp ties would therefore render
  those ties **A→Z**, not Z→A — a same-timestamp tie does not explain the observed order on its own.
- `addedAt` (`SetRow.created_at`, `crates/mediagram-core/src/dto/summary.rs:111`) is populated from
  `msg.sent_at` (`crates/mediagram/src/index/rescan.rs:86`) — the Telegram **message's own send time**, not
  a local scan/import clock.

Put together: the sort itself is correct and does exactly what "Recently added" should — newest `sent_at`
first. A run of Z-titled films at the top, in reverse-alphabetical order, is most consistent with those
specific titles genuinely having been (re-)sent to the channel most-recently, in that relative order — e.g.
a recent reprocessing pass that happened to touch the household's Z-titled films — rather than a shared
sorting rule or a same-timestamp tie artifact (ties would sort A→Z here, the opposite of what was seen).
**Recommend:** the lead check the real `addedAt`/`sent_at` values for those three titles (`sqlite3`/`psql`
on the local index, or `mediagram verify`) before treating this as a bug at all.

## Files

### Modified/created (main)
`TvSearch.kt`, `TvSearchResults.kt`, `TvSearchRows.kt` (new), `TvSearchGroups.kt`, `TvHome.kt`,
`TvCoverStory.kt`, `TvFeatureStrip.kt`, `TvCatalogScreen.kt`, `TvCatalogNav.kt` (new), `TvCatalogBody.kt`
(new), `TvDepartmentPages.kt`, `TvMoviesDepartmentPage.kt` (new), `TvShowsDepartmentPage.kt` (new),
`TvDepartmentRows.kt` (new), `TvDepartmentTargets.kt` (new). `TvHomeRow.kt` unchanged (Home's own six-slot
row is correct as-is; only department rows needed a different shape).

### Modified/created (tests)
`TvCatalogScreenStateTest.kt` (+1 test: M1), `TvSearchResultsStateTest.kt` (new, 2 tests: C1's own pure
arithmetic and the composable wiring), `TvDepartmentTargetsTest.kt` (new, 8 tests: N7's genre/all-films
match plus M3's hero/row/defer priority, all pure — no Compose needed), `TvDepartmentPagesStateTest.kt`
(new, 3 tests: M2's row-past-six, M3's hero-takes-focus, and the `LazyRow` rewrite still opens a genre row).
14 new tests, 0 removed. `TvHousekeepingTest.kt`/`TvLibraryTest.kt`/`TvSearchAndGenreTest.kt` untouched and
still green.

## Commands run

- `./gradlew :ui-tv:compileDebugKotlin` — clean.
- `./gradlew :ui-tv:testDebugUnitTest :ui-tv:compileDebugAndroidTestKotlin :ui-tv:lint` (the lead's own
  command) — **BUILD SUCCESSFUL**. Ran this several times over the session; twice mid-session it reported 3
  failures, all three in `TvCollectionTabsStateTest.kt`/`TvPersonPageStateTest.kt` — neither owned by me,
  both modified by the parallel agent at that moment (confirmed via `git status`); by the final run those
  had resolved on their own and the whole suite (theirs and mine together) passed. Also hit repeated
  transient Gradle failures (`NoSuchFileException`/`EOFException` on `ui-tv/build/test-results/...`) from
  two agents running Gradle against the same worktree's build directory at once — infrastructure
  contention, not a code issue; resolved by retrying.
- `./gradlew :ui-tv:lint` alone — clean, no warnings printed.

## Concerns

- **N1's "Watch now plays" is architecturally correct but not yet wired to a real player.** `onPlay` is
  now a real, distinct parameter throughout `ui-tv/catalog`, defaulting to `onOpenTitle` so nothing
  regresses — but the one caller that could supply a genuine "play this set" callback is `TvLibrary.kt`
  (`at.openPlayer(setId)`, used by the title page's own Play), which sits outside my ownership along with
  `TvCatalogRoot`'s param list in `TvLibraryBranches.kt`. Until `TvCatalogRoot`/`TvCatalogScreen` are given
  a real `onPlay = { at.openPlayer(it) }` at that call site, "Watch now" still opens the title page — safe,
  but not yet what N1 asks for on a real device. Small change, two files, both outside this task's
  boundary.
- Two other agents' files broke and un-broke mid-session in this shared worktree (see "Commands run"); the
  final full-suite run was clean, but if the lead sees `TvCollectionTabsStateTest`/`TvPersonPageStateTest`
  fail again, that is not this report's changes.

## Unresolved questions

- None from my own scope. Item 10's real-data verification (the recommendation above) is the one open
  item, and needs the lead's access to actual `addedAt`/`sent_at` values, not further code investigation.
