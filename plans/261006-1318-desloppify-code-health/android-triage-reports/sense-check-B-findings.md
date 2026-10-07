# Sense-check B: findings (read-only verification)

Worktree: /home/andre/Workspace/mediagram-desloppify/android (Android source = 31cb8561; the web player at 0.117.4).
Every cited line in every step was read with `sed -n` / `git grep`. Web `../` references were rechecked against the moved web player.
The corrected step texts are in `sense-check-B-edits.sh`: 17 `update-step` commands, all validated. Every detail is over 80 characters, names existing android-relative files, and every slash+extension token resolves from android/. The script passes `bash -n`.

## Summary

| Cluster | Verdict | Steps | Key corrections |
|---|---|---|---|
| typed-department-and-tab-identity | keep (tighten) | 1 tighten, 2 tighten, 3 tighten, 4 tighten (small→medium), 5 keep | See below. |
| typed-navigation-ids | keep (tighten) | 1 tighten, 2 tighten | See below. |
| tv-focus-targets-and-effects | keep (tighten) | 1 tighten (small→medium), 2 tighten, 3 tighten (small→trivial), 4 tighten (small→medium), 5 tighten (Device: none) | See below. |
| shared-surface-glue-in-ui-common | keep (tighten) | 1 tighten, 2 tighten, 3 tighten | See below. |
| process-globals-to-owned-state | keep (tighten) | 1 tighten, 2 tighten and simplify (stays medium), 3 tighten | See below. |

Key corrections:

**typed-department-and-tab-identity**
- The 8→6 Latest-courses change is confirmed against the web: `home-shelves.js:66` cuts latestCourses to `limit`, and `app.js:139` passes the defaults, 6 and 8. Step 4 now names it as a visible change, puts it in the commit message and adds a check on the tablet and the TV box.
- Step 3 no longer retypes HomeRow.seeAll. Its only production reader is TvLatestPage.kt:56, and step 4 deletes it. So the TvLatestPage bridge edit goes, and **steps 3/4 are no longer order-dependent.**
- Step 4 also deletes setCard (dead; its KDoc is false), HomeRow and RowContent. MagazineHome.recentlyAddedRow becomes a `recentlyAddedTotal: Int`.
- Step 2: the test count is 64, not 53. The phone's ShelfWall `else` branch and the shelfView values go. The tautological sections.js test is dropped.
- Step 1 owns the whole MastheadSplit KDoc, per the lead.

**typed-navigation-ids**
- The 12/16 counts and the web lines are exact.
- Verify is now unconditional, because the gate is already widened by the dependency chain.
- The readers need no edit.
- The tautological keyOf test is dropped, and two fixtures need `franchiseId` instead of five.

**tv-focus-targets-and-effects**
- All lines and counts are exact.
- Step 1: drop the redundant JVM cases; add TvHome.kt:123 and the Home walk.
- Step 2: guard with `in included`. The existing focus tests already pass before the fix, so assert `totalItemsCount` instead.
- Step 3: a local `when` replaces `railRestoreOf` and its test.
- Step 4: all 27 vals are proven safe to convert; the helper is appended at the end of TvFocus.kt.
- Device walks are navigate-only: OK only on plates or rail rows, only Back inside Settings/System.

**shared-surface-glue-in-ui-common**
- The twins are byte-identical non-visual glue.
- Drop step 1's new Robolectric test; FilmPreloadFlowTest covers it.
- The moved KDocs must not link ui-mobile-only symbols.
- Step 3 nets about −40 lines; its cancellation test already exists.
- The Preload device check is look-only.

**process-globals-to-owned-state**
- Step 2 invented in-flight/wait dedupe. The redesign instead:
  - deletes the redundant `asked` set (it always equals attemptedPortraitFetches);
  - turns the reserving query into a plain `needsFetch`;
  - passes the log through the same 17 files, with behaviour unchanged.
- Step 1: imports, the TvFocus.kt:40 note and the test rename are spelled out.
- Step 3: the either/ors are resolved, and the activity 1.13.0 listener APIs are confirmed.

## Global notes for the lead
- **Rust commits:** none of these five clusters touches core/update AppUpdater, subtitle-bundle failures or preferences::set. So 1ab59974 and 41ab9392 need no accounting here.
- **User-decision deferrals:** none is absorbed or reversed (ktlint, security-crypto, LAN pairing, material3-adaptive pin, PlayerViewModel size, UniFFI surface, Start-over).
  - process-globals step 2 passes `PortraitRequestLog` (a core:data class), not a UniFFI record.
  - catalog-records-mirrored-in-core-model step 3 already anticipates that.
- **Ordering facts the dependency graph needs:**
  1. typed-department step 3 no longer needs to precede step 4. Neither carries a bridge edit now; they only touch neighbouring lines in HomeScreen.kt, TvHome.kt, CatalogScreen.kt, TvCatalogScreen.kt and TvCatalogBody.kt.
  2. tv-focus step 4 appends rememberStableRequester at the END of TvFocus.kt, so process-globals step 1's TvFocus.kt line references hold. process-globals step 1 also tells the executor to locate reads with `git grep`.
  3. shared-surface step 3 adds a RememberLookupTest case before process-globals step 2 rewrites that file's portrait tests. process-globals step 2 now cites those tests by name.
  4. Steps that run after typed-department-and-tab-identity in the same files find their sites by expression, not line. This applies to typed-navigation-ids 1, shared-surface 1, tv-focus 1 and tv-focus 3.
  5. comments-to-invariants no longer touches CatalogTabs.kt (lead's handoff). typed-department step 1 owns that KDoc.
- **Device wording:** all walks are navigate-only.
  - OK only on plates, cast cards or rail rows to open a page.
  - Never OK on a Settings/System/player-menu row, a title page's state-changing pills or a profile tile.
  - Only Back inside Settings/System.
  - Never Back at the library root.
  - Plays use the TV test profile and land in Continue (expected).
  - tv-focus step 5 now has Device: none.

## Cluster: typed-department-and-tab-identity

Web parity reference rechecked against the moved web player (worktree, version 0.117.4):
- `../web/public/lib/catalog/sections.js:9-18` SECTIONS: movies (film, no noun), series (show/episode), tutorials (course/lesson), documentaries (documentary/documentary), anime (title/episode). The step 2 enum values match exactly.
- `../web/public/index.html:76-82` nav.departments: home, movies, series, anime, documentaries, tutorials, collections. Rail kept rows are `:55-56` (watchlist, continue). Both still hold.
- `../web/public/lib/address.js:26-31` KNOWN_SECTIONS and `../web/public/lib/nav-current.js:18-26` markCurrent still hold.
- `../web/public/app.js:108-110`: the rail masthead counts movies, series and tutorials only. This still holds.
- `../web/public/app.js:139`: `homeShelves({library, byId, progress, watchedAt})` passes no limits, so the defaults apply: limit = SHELF_LIMIT = 6 (`home-shelves.js:18`) and posterLimit = POSTER_ROW_LIMIT = 8 (`:21`).
- `../web/public/lib/catalog/home-shelves.js:64-66`:
  - latestMovies and latestSeries are `.slice(0, posterLimit)`.
  - **latestCourses is `.slice(0, limit)`**, which is 6 on Home.
  - Totals are at `:73-79`.
- **The 8→6 change is confirmed against the web.**
  - Android Home passes `posterLimit = HOME_POSTER_ROW_LIMIT` (8) to every Latest row:
    - phone: ui-mobile/src/main/kotlin/ui/catalog/CatalogScreen.kt:195
    - TV: ui-tv/src/main/kotlin/ui/tv/catalog/TvCatalogScreen.kt:188
  - So Latest courses holds 8 today.
  - Neither CourseList (ui-mobile/src/main/kotlin/ui/catalog/home/CourseList.kt) nor TvCourseList (ui-tv/src/main/kotlin/ui/tv/catalog/home/TvCourseList.kt) truncates on its own.
  - The change is visible on both surfaces whenever the library holds more than six courses.
- These web line numbers moved:
  - `home-view.js`: series band `:103-106`, courses band `:108-113`. The steps cite `:103-112` and `:104-112`.
  - `utility-pages.js` renderLatest: now `:24-35`, with `:25` passing `posterLimit: 48, limit: 48`. The steps cite `:23-33`.

### Step 1: Delete MastheadSplit.utilities and UtilityDestination; fix the KDocs [trivial]
Verdict: **tighten** (the facts hold; the KDoc instruction changes because of the lead's comments-to-invariants handoff).

Evidence:
- `CatalogTabs.kt` matches the step:
  - `:32-42` is the KDoc. It says "web 0.62.1", lists a department row without Anime, links `[android.ui.OverflowMenu]` (that package does not exist) and ends with the phase sentence.
  - `:43` data class, `:45-52` UtilityDestination, `:54` the "five utilities" KDoc, `:55-59` mastheadSplitOf.
- `CatalogTabsTest.kt:14-17` is the only reader of `utilities` (git grep).
- Production reads only `.departments`: TvCatalogScreen.kt:112 and :149, TvCatalogNav.kt:51, :69 and :92.
- `OverflowMenu.kt:22-28`: the BrowseActions KDoc credits `[ui.catalog.mastheadSplitOf]` (:25). The real function is `catalog.mastheadSplitOf`.

Corrections:
1. Per the lead, this step now owns the whole MastheadSplit KDoc (:32-42). That means:
   - no "web 0.62.1" pin, which is stale: the web is 0.117.4 and its bar has Anime;
   - no phase sentence;
   - the overflow-menu reference as plain text, because feature:catalog cannot link into ui-mobile.
2. single-package-move-pass moves OverflowMenu.kt into ui/chrome. The step now says to edit the file wherever it lives.
3. The rewrite stays two sentences, because step 3 deletes MastheadSplit outright.

Effort: trivial ✓.
Touch list (every step in this cluster also edits `../Cargo.toml`, `../web/package.json` and `app/build.gradle.kts` for the version bump; not repeated below):
- E feature/catalog/src/main/kotlin/CatalogTabs.kt
- E feature/catalog/src/test/kotlin/CatalogTabsTest.kt
- E ui-mobile/src/main/kotlin/ui/OverflowMenu.kt (or its moved path)

### Step 2: Department enum keyed on every shelf [medium]
Verdict: **tighten.**

Evidence. Every cited site was read and holds:
- CatalogUiState.kt:54-57; Shelves.kt:17, :63/:65/:67/:69/:70; Anime.kt:13 and :39
- HeroArtOf.kt:22-56, with the `when` at :29
- LibraryTally.kt:41 and :48-49; DepartmentLines.kt:21-27; MagazineHome.kt:41
- HomeShelves.kt:103 and :124-125; TitlePageCandidates.kt:17-18
- ChromeCounts.kt:19, :23-28 and :47
- CatalogScreen.kt:225-250 and :257; ShowsDepartmentScreen.kt:41, :77, :91, :122 and :124
- LibraryBranchSupport.kt:89-118; LibraryBrowseBranches.kt:121; LibraryFlowBranches.kt:123
- TvCatalogBlend.kt:49, :53 and :66-72; TvDepartmentPages.kt:85, :93, :106, :129, :133 and :134
- TvShowsDepartmentPage.kt:81-82 and :95; TvLibrary.kt:93
- LibraryTallyTest.kt:42, DepartmentLinesTest.kt:40/:45, ShowsDepartmentScreenTest.kt:87, TvDepartmentPagesStateTest.kt:45/:60/:159

Corrections:
1. Counts:
   - Test constructions: `git grep -o '\bShelf(' -- '*/src/test/*.kt'` gives 64 constructions on 54 lines across 18 files, not "about 53".
   - Dispatch sites: 16 files (7 feature:catalog, 5 ui-mobile, 4 ui-tv), not 17.
2. A knock-on the step misses: the exhaustive `when` removes the phone's unreachable `else -> ShelfWall(...)` (CatalogScreen.kt:249).
   - CatalogScreen's `shelfViewModel`, `chosenView` and `shelfView` (:157-159) then have no reader, and must go or lint/compile warns.
   - ShelfWall itself stays: LibraryBrowseBranches.kt:128 uses it for "All N films".
3. The private `ShowsDepartment(kind, label, unit, ...)` (CatalogScreen.kt:257) should take the Department as well, deriving the Kind from it.
4. The planned test "Department labels/extents equal sections.js" would only compare hard-coded strings with the same hard-coded strings, which proves nothing.
   - Drop it.
   - Keep the shelvesOf→Department test (it guards the Shelves.kt mapping) and the libraryTallyLines guard that pins Documentaries/Anime out.

Over-engineering check: passes.
- About 40 string comparisons become exhaustive `when`s.
- EXTENT_NOUNS and the label/unit parameter pairs fold into one table that mirrors SECTIONS.
- TvDepartmentPages' catch-all `else` (any unknown shelf treated as shows) goes.
- Net lines are about neutral; it removes the branching and a silent-misroute class of bug.

Effort: medium ✓ (16 production files plus 18 mechanical test files).

Touch list:
- C Department.kt (feature/catalog/src/main/kotlin)
- E feature/catalog/src/main/kotlin/: CatalogUiState.kt, Shelves.kt, Anime.kt, HeroArtOf.kt, LibraryTally.kt, DepartmentLines.kt, MagazineHome.kt, HomeShelves.kt, TitlePageCandidates.kt, ChromeCounts.kt
- E ui-mobile/src/main/kotlin/ui/: catalog/CatalogScreen.kt, catalog/ShowsDepartmentScreen.kt, LibraryBranchSupport.kt, LibraryBrowseBranches.kt, LibraryFlowBranches.kt
- E ui-tv/src/main/kotlin/ui/tv/: catalog/TvCatalogBlend.kt, catalog/TvDepartmentPages.kt, catalog/TvShowsDepartmentPage.kt, TvLibrary.kt
- Optional E: Destination.kt:82 and the department-naming display literals (MoviesDepartmentScreen.kt:77, TvMoviesDepartmentPage.kt:111), only where they name a department.
- E tests (18 files with `Shelf(`):
  - feature/catalog: CatalogTabsTest, ChromeCountsTest, GenreIndexTest, GenreShelfTest, HeroArtOfTest, LibraryTallyTest, PersonPageTest, RunForTest, SearchGroupsTest, SearchRowsOfTest, ShelfViewTest, TitlePageCandidatesTest, VisiblePeopleTest
  - ui-mobile: AnimeDepartmentScreenTest, DocumentariesDepartmentScreenTest, LatestScreenTest, title/FilmPageTest
  - ui-tv: TvDepartmentPagesStateTest
- E tests also: DepartmentLinesTest, ShowsDepartmentScreenTest
- E feature/catalog/src/test/kotlin/ShelvesTest.kt: the new shelvesOf→Department case goes here, so no new file is needed

### Step 3: Keyed CatalogTab [medium]
Verdict: **tighten.**

Evidence. These cited sites hold:
- feature/catalog: CatalogTabs.kt:14-24; KeptShelves.kt:22-29 (CONTINUE "Continue", WATCHLIST "My List", COLLECTIONS); ChromeCounts.kt:23-28
- ui-mobile: CatalogScreen.kt:53-56, :165-167, :187, :204, :207-208, :210 and :224; LibraryFlowBranches.kt:75-81, :84-93, :104 and :123; LibraryBranchSupport.kt:71 and :109; LibraryHome.kt:68-102
- ui-tv: TvCatalogScreen.kt:107, :112, :117-120, :147-150 and :151-160; TvCatalogNav.kt:51-56, :66-71, :72-77 and :91-95; TvCatalogBody.kt:31-33, :59, :75, :80, :96 and :110; TvCatalogBlend.kt:49; TvHome.kt:89, :310 and :327
- Web references hold, except home-view.js, which is now :103-113.

Corrections:
1. **Drop the HomeRow.seeAll retype, and with it the TvLatestPage.kt:56 bridge edit.**
   - `git grep -n '\.seeAll\b'` over main sources shows no production reader of HomeRow.seeAll except TvLatestPage.kt:56, which reads it as a heading.
   - The "See all" taps never read it: they are hard-coded `onSeeAll("Movies"/"Series"/"Tutorials"/"Continue")` calls at HomeScreen.kt:107/:117/:125/:133 and TvHome.kt:310/:327.
   - Step 4 deletes homeRowsOf, TvLatestPage's use and (corrected below) HomeRow itself.
   - Retyping it here only churns HomeShelvesTest :69/:111/:132, TvHomeStateTest :246/:254 and MagazineHome.kt:62, all of which step 4 rewrites or deletes.
   - With that dropped, **steps 3 and 4 no longer depend on each other's order.** They only touch neighbouring lines in HomeScreen.kt, TvHome.kt, CatalogScreen.kt, TvCatalogScreen.kt and TvCatalogBody.kt. Keep the listed order anyway.
   - **This replaces enrich's note that the order is load-bearing.**
2. These call sites were missing from the step:
   - CatalogScreen.kt:66-79: the outer composable's chosenTab/onTabChange KDoc and parameters.
   - CatalogScreen.kt:119 and :139: the inner pass-through.
   - LibraryHome.kt:181 and :187: `onTabChange(visible[it])`.
   - LibraryFlowBranches.kt:219-233: where `tabs`, `visible`, `chosenTab` and `onTabChange` are passed.
   - Test comments naming mastheadSplitOf: TvHousekeepingTest.kt:117 and TvCatalogScreenStateTest.kt:65.
   - TvLibraryRemoteTest and TvCatalogRootPlayStateTest pass only the parameterless `onTabChanged` and need no change.
3. RestoredTabIndexTest and VisibleTabIndicesTest test functions this step deletes.
   - Delete both files and move their two rules into CatalogTabsTest: a removed department's key restores Home, and the masthead omits Continue and My List.
   - Do not write new keyed-function test files.
4. Simplest key: `Department.name.lowercase()` and `KeptKind.name.lowercase()` already equal the web's data-section ids (movies, series, anime, documentaries, tutorials, continue, watchlist, collections), plus "home". No lookup table is needed.
5. Device wording: Back is pressed after opening a title from a tab, never at the library root.

Over-engineering check: passes. A three-variant sealed type replaces:
- every `firstKept`, `+ 1`, `+ 2`, `- 1` and lastIndex expression;
- three *Index vals;
- the TV pill-position translation;
- two parallel tab vocabularies (CatalogTabs and MastheadSplit).

It also fixes a real TV defect: `chosen` is a bare saved Int at TvCatalogScreen.kt:120, so a refresh that adds or removes a department reopens the wrong tab. The phone already documents this failure at LibraryFlowBranches.kt:75-80.

Effort: medium, at the top of the band (12 production files on both surfaces, including focus-sensitive TvCatalogNav).

Touch list:
- E feature/catalog/src/main/kotlin/: CatalogTabs.kt, ChromeCounts.kt
- E ui-mobile/src/main/kotlin/ui/: catalog/CatalogScreen.kt, catalog/HomeScreen.kt, LibraryFlowBranches.kt, LibraryBranchSupport.kt, chrome/LibraryHome.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/: TvCatalogScreen.kt, TvCatalogNav.kt, TvCatalogBody.kt, TvCatalogBlend.kt, TvHome.kt
- E tests: feature/catalog/src/test/kotlin/CatalogTabsTest.kt, feature/catalog/src/test/kotlin/ChromeCountsTest.kt, ui-mobile/src/test/kotlin/ui/chrome/WidthClassStateTest.kt, ui-mobile/src/test/kotlin/ui/catalog/HomeScreenTest.kt (onSeeAll target only), ui-tv/src/test/kotlin/ui/tv/catalog/TvCatalogScreenStateTest.kt, ui-tv/src/test/kotlin/ui/tv/TvHousekeepingTest.kt, ui-tv/src/test/kotlin/ui/tv/catalog/TvHomeStateTest.kt (onSeeAll lambdas only)
- D ui-mobile/src/test/kotlin/ui/RestoredTabIndexTest.kt
- D ui-mobile/src/test/kotlin/ui/catalog/browse/VisibleTabIndicesTest.kt

### Step 4: Home and Latest read named fields [small → medium]
Verdict: **tighten.**

Evidence. These cited sites hold:
- CatalogScreen.kt:194-197; TvCatalogScreen.kt:181-193 (comment plus filter)
- HomeScreen.kt:69-72 and :142-148 (collectionsOf); TvHome.kt:98-101 and :371-378
- HomeShelves.kt:169-175 (latestTitleFor)
- LatestScreen.kt:29, :49 and :69; TvLatestPage.kt:47 and :56

Corrections:
1. **Visible change, stated explicitly:**
   - Home's "Latest courses" drops from 8 to 6 on the phone/tablet and on TV, because the web's latestCourses uses `limit` (6).
   - It shows only when the library holds more than six courses.
   - The commit message must say it follows the web Home.
   - The user checks it on the tablet and the TV box.
2. `setCard` (HomeShelves.kt:127-137) is dead once homeRowsOf goes.
   - Its KDoc says magazineHomeOf builds the same card, which is false: magazineHomeOf uses resumeCardsOf (NextUp.kt:228).
   - Its only callers are HomeShelves.kt:77 and :93.
   - So the step's "(underwayOf / setCard captions used by magazineHomeOf)" is wrong: delete setCard too.
3. `MagazineHome.recentlyAddedRow: HomeRow` (MagazineHome.kt:22-23 and :59-65) is read only for `.total` (HomeScreen.kt:116, TvHome.kt:324). Its title, `seeAll = "Movies"` and content (a copy of `recentlyAdded`) are never read.
   - Replace it with `recentlyAddedTotal: Int`.
   - HomeRow and RowContent then have no users (RowContent.Sets only ever came from homeRowsOf), so delete both. SetCard stays.
   - This removes the last display-string `seeAll`, which is the cluster's own smell. "HomeRow stays" in the step is reversed.
4. Stale KDocs: NextUp.kt:17-22 (links homeRowsOf and HomeRow) and TvLatestPage.kt:20-25 (links `catalog.HomeRow.seeAll`).
   - Use `git grep -n 'homeRowsOf\|HomeRow'` after the edit.
   - AllSetsIndex.kt:8 also names homeRowsOf, but dead-aliases-and-pass-throughs deletes that file first.
5. Tests:
   - Port the HomeShelvesTest latest cases (filmsAreNewestFirst, aShowIsDatedByItsNewestEpisode, aRowHoldsSix…, everyRowNamesTheShelfBehindIt, anEmptyLibrary…, aSetWithNoArrivalTime…, thereIsNoLatestAnimeRow) to latestOf in the same file, plus one case pinning courses at `limit`, not `posterLimit`.
   - Port the Continue/Next up cases (aStartedFilmLeadsContinue, aFinishedEpisodeOffersNextUp, aFinishedAnimeEpisode…) to `magazineHomeOf(...).resumeCards`, unless MagazineHomeTest already covers them.
   - No new test file.
   - TvCatalogScreenStateTest needs no change: no test pins eight courses.
6. Web line numbers: home-view.js is now :103-113; utility-pages.js is now :24-35.

Over-engineering check: passes, and the step is net negative. It deletes homeRowsOf (about 60 lines, whose Continue/Next up rows are computed and thrown away on every Home), latestTitleFor, both `.filterNot` calls, both collectionsOf copies, LATEST_HEADINGS, setCard, HomeRow and RowContent. In their place it adds one latestOf plus a small `Latest` class.

Effort: medium, not small (11 production files on both surfaces, a visible change and a two-device check).

Touch list:
- E feature/catalog/src/main/kotlin/: HomeShelves.kt, MagazineHome.kt, NextUp.kt (KDoc)
- E ui-mobile/src/main/kotlin/ui/catalog/: CatalogScreen.kt, HomeScreen.kt, LatestScreen.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/: TvCatalogScreen.kt, TvCatalogBody.kt, TvHome.kt, TvLatestPage.kt
- E tests: feature/catalog/src/test/kotlin/HomeShelvesTest.kt, ui-mobile/src/test/kotlin/ui/catalog/HomeScreenTest.kt, ui-mobile/src/test/kotlin/ui/catalog/LatestScreenTest.kt, ui-tv/src/test/kotlin/ui/tv/catalog/TvHomeStateTest.kt
- E only if it does not already cover the resume cases: feature/catalog/src/test/kotlin/MagazineHomeTest.kt

### Step 5: Rename the shadowing composables [trivial]
Verdict: **keep.**

Evidence:
- AnimeDepartmentScreen.kt:16 (import), :31-35 (KDoc, which links [DocumentariesDepartment] at :33) and :37.
- DocumentariesDepartmentScreen.kt:19 and :45.
- CatalogScreen.kt:245-248 and :257.
- Tests: AnimeDepartmentScreenTest.kt:58/:133/:142 and DocumentariesDepartmentScreenTest.kt:69-72/:204.
- The ambiguity is real, not convention-only: tests must fully qualify the data-class constructor.

Effort: trivial ✓.

Touch list:
- E ui-mobile/src/main/kotlin/ui/catalog/: AnimeDepartmentScreen.kt, DocumentariesDepartmentScreen.kt, CatalogScreen.kt
- E ui-mobile/src/test/kotlin/ui/catalog/: AnimeDepartmentScreenTest.kt, DocumentariesDepartmentScreenTest.kt

### Cross-cluster overlaps
- dead-aliases-and-pass-throughs (a dependency) renames indexById to allSetsById inside homeRowsOf (HomeShelves.kt:63) and deletes AllSetsIndex.kt. Step 4 then deletes homeRowsOf. There is no conflict as long as the dependency order holds.
- shared-surface-glue-in-ui-common step 1:
  - It renames the DepartmentScrollStates type at TvCatalogScreen.kt:196, TvCatalogBlend.kt:43, TvDepartmentPages.kt:77 and TvCatalogBody.kt:41. Those are neighbouring lines to steps 2 and 3.
  - It depends on this cluster, so they run in sequence.
  - It already leaves collectionsOf and the heading filter to step 4.
- tv-focus-targets-and-effects step 3 edits TvCatalogNav.kt after step 3 has removed the index parameters. It already says so.
- comments-to-invariants: the lead removed its CatalogTabs.kt bullets, so step 1 owns that KDoc.
- single-package-move-pass may move OverflowMenu.kt before step 1. The step 1 text now names it by file name.

### Cluster value verdict: **keep (tighten)**
It deletes layers and branching:
- the index arithmetic;
- a test-only parallel enum;
- string dispatch with silent `else` fallbacks;
- a home-row builder whose output is mostly computed and then discarded.

It also fixes a real TV tab-restore defect. Step 4 is the one visible change: it follows the web and is called out for the commit message and the device check.

---

## Cluster: typed-navigation-ids
### Step 1: carry person and franchise ids as Long through LibraryPositions. Verdict: TIGHTEN (effort small, correct)

YAGNI check: worth doing.
- It deletes 16 conversions (12 `.toString()`, 4 `.toLongOrNull()`) and moves one parse into the accessor, which is the pattern menuScreen already uses at LibraryPositions.kt:145. Net lines about 0, net branching -4.
- The saved-state format does not change.
- This is no new abstraction.

Evidence (all verified):
- ui-common/src/main/kotlin/ui/LibraryPositions.kt:
  - :147 `val personId: String?`, :149 `val franchiseId: String?`
  - :211-212 `openPerson(id: String)` and `openFranchise(id: String)`
  - :145 menuScreen decodes inside its accessor
- 12 openers, exactly:
  - LibraryTitleBranches.kt:53, :54, :95
  - LibraryFlowBranches.kt:164, :165, :242
  - TvLibraryFrames.kt:61, :65
  - TvLibraryBranches.kt:185
  - TvLibraryCatalogFrames.kt:66, :78, :138
- 4 readers: LibraryBrowseBranches.kt:56, :195; TvLibrary.kt:127, :130. No other main or androidTest caller.
- Tests: LibraryPositionsTest.kt:115-130 (asserts "42" and "7" at :122 and :129) and LibraryPositionsFrameKeyTest.kt:66. LibraryPositionsEncodingTest has a `positions(token)` helper and a precedent restore test at :60-72.
- Web refs, rechecked after the web moved; all still exact:
  - ../web/public/lib/address.js: :97 person, :105 franchise, :63 person parse, :70 franchise parse
  - ../web/public/lib/address.d.ts: :24, :32

Corrections:
1. **The Verify line is conditional ("plus :core:rust… once parity-fixtures-and-test-gates has widened the gate"), and the condition is stale.** This cluster depends on typed-department-and-tab-identity, which depends on parity-fixtures-and-test-gates, so the gate is already widened. Make it unconditional, like every other cluster here.
2. **Ordering note.** typed-department-and-tab-identity steps 2-3 edit LibraryFlowBranches.kt (:70-93, :104, :123), LibraryBrowseBranches.kt:121 and TvLibrary.kt:93 first. The hunks do not overlap, but line numbers shift, so the step should say to find each site by its expression (`at.openPerson(id.toString())` and so on).
3. The encoding test is right-sized: one 3-line case proves the claim that the payload is unchanged. Name the existing helper and precedent so it is concrete.

FILE TOUCH LIST (step 1):
- E ui-common/src/main/kotlin/ui/LibraryPositions.kt
- E ui-mobile/src/main/kotlin/ui/LibraryTitleBranches.kt
- E ui-mobile/src/main/kotlin/ui/LibraryFlowBranches.kt
- E ui-mobile/src/main/kotlin/ui/LibraryBrowseBranches.kt
- E ui-tv/src/main/kotlin/ui/tv/TvLibraryFrames.kt
- E ui-tv/src/main/kotlin/ui/tv/TvLibraryBranches.kt
- E ui-tv/src/main/kotlin/ui/tv/TvLibraryCatalogFrames.kt
- E ui-tv/src/main/kotlin/ui/tv/TvLibrary.kt
- E ui-common/src/test/kotlin/ui/LibraryPositionsTest.kt
- E ui-common/src/test/kotlin/ui/LibraryPositionsFrameKeyTest.kt
- E ui-common/src/test/kotlin/ui/LibraryPositionsEncodingTest.kt
- E ../Cargo.toml, ../web/package.json, app/build.gradle.kts

### Step 2: typed SearchDestination.franchiseId and shared TV restore-key builders. Verdict: TIGHTEN (effort small, correct)

YAGNI check: mostly passes.
- The constructor field replaces a parse, with net lines about 0. The real defect is that ui-tv hard-codes feature:catalog's private "tmdb-" prefix in `"dest:tmdb-$id"` (TvLibraryFrames.kt:64). A mismatch there silently sends Back-focus to the first result.
- The three one-line key builders each have exactly two consumers in two files: keyOf (TvSearchGroups.kt:22-28) and the frame (TvLibraryFrames.kt:56/:60/:64/:68). That is acceptable DRY, not a speculative abstraction.
- The "list id starting with tmdb- misread" case is theoretical. The web has the same prefix rule at its own URL boundary (../web/public/lib/address.js:70), so this is Android's own tightening with no visible change, which the step should say.

Evidence verified:
- SearchGroups.kt: :12-15 (getter :14), :17 FRANCHISE_HREF, :82 franchise href, :85 list href.
- TvSearchGroups.kt:22-28.
- TvLibraryFrames.kt:56/:60/:64/:68, with the pointer comment at :53-54.
- Web: ../web/public/lib/catalog/collections-page.js:78 and ../web/public/lib/catalog/search-view.js:64. The step wrote a bare "search-view.js:64"; give the full path.

Corrections:
1. **The readers do not change.** SearchGroupsView.kt:236-242, TvSearch.kt:155-158 and TvSearchCell.kt:92 read `destination.franchiseId`, which keeps its name and type. Say so, so the executor does not hunt for edits there.
2. **Tests are over-specified.**
   - SearchGroupsTest already has aFranchiseDestinationKnowsItsFranchiseAndAListsDoesNotEvenWhenItsIdIsANumber (feature/catalog/src/test/kotlin/SearchGroupsTest.kt:100-107). Extend it with a second list whose id is "tmdb-7" and expect `listOf(42L, null, null)`; today's getter fails that. No new test.
   - Only two fixtures depend on franchise behaviour:
     - SearchCollectionsTest.kt:33, "tmdb-5": asserts "two films" and onOpenFranchise.
     - TvSearchResultsStateTest.kt:174, "tmdb-9": asserts "three films" at :185.
     These two gain `franchiseId = 5` and `franchiseId = 9`.
   - The `destination(name, href)` helper (TvSearchResultsStateTest.kt:235-238) and its callers at :49/:98/:126 need no change. Those tests record only `it.href` (:107) or focus.
   - Drop "one ui-tv test that keyOf(franchise destination) equals destinationKey(franchiseHref(id))". It is true by construction once keyOf calls destinationKey(href), so it guards nothing.
3. The Verify conditional is stale, as in step 1.

FILE TOUCH LIST (step 2):
- E feature/catalog/src/main/kotlin/SearchGroups.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvSearchGroups.kt
- E ui-tv/src/main/kotlin/ui/tv/TvLibraryFrames.kt
- E feature/catalog/src/test/kotlin/SearchGroupsTest.kt
- E ui-mobile/src/test/kotlin/ui/catalog/SearchCollectionsTest.kt
- E ui-tv/src/test/kotlin/ui/tv/catalog/TvSearchResultsStateTest.kt
- E ../Cargo.toml, ../web/package.json, app/build.gradle.kts

Duplicates and overlaps:
- shared-surface step 2 rewrites PersonFrame right after step 1 changes its :195. The ordering holds because shared-surface depends on typed-navigation-ids.
- process-globals step 2 later renames shouldRequestPortrait in TvLibraryFrames.kt and LibraryBrowseBranches.kt. It touches different lines and runs later.
- catalog-records-mirrored-in-core-model step 2 edits SearchGroups.kt:6/:52 (the SearchHit import). Different lines; it runs later.

**Cluster value verdict: KEEP (tighten).**
- It removes a stringly round-trip at 16 sites.
- It deletes an href-parsing getter.
- It ends a cross-module hard-coded private prefix.
- The changes are small and type-checked, with no new layers. The only cuts are one tautological test and two unneeded fixture edits.

---

## Cluster: tv-focus-targets-and-effects

Every cited line was read at the worktree (Android source = 31cb8561). All line numbers, names and counts in the five steps are correct; the corrections below are about over-building, missing or ineffective tests, and device wording.

### Step 1: Type the TV department focus targets and put homeTargetOf on the one generic restore search. Verdict: TIGHTEN. Effort: small -> medium

Evidence (all match):
- `ui-tv/src/main/kotlin/ui/tv/catalog/TvDepartmentTargets.kt`
  - :15 `DeptSection(val name: String, ...)`.
  - :29-44 `restoreTargetOf` returns `Pair<String, Int>?`.
  - The KDoc at :17-28 says Home and every department page "share the one search". That is false.
  - Pairs are returned at :33/:60/:86/:113/:133, from movies :56-71, shows :81-90, showsSections :92-101, anime :109-117 and docs :129-136.
  - Section literals sit at :61/:64-67/:70/:97-100/:114/:135.
- `ui-tv/src/main/kotlin/ui/tv/catalog/home/TvHomeTargets.kt`
  - :7 `TvHomeTarget`.
  - :29-46 `homeTargetOf`, whose loop at :34-43 is a copy.
- `TvHome.kt`
  - :103-122 `sections` as Pairs. :123 `included` destructures them.
  - :147 is the one caller.
  - `.section`/`.stop` are read at :197/:212/:232/:236.
  - `TvHomeTarget` has no other main reference (`TvHomeCover.kt:54` mentions `homeTargetOf` only in KDoc, which stays valid).
- Pages:
  - Movies: lastSection :70, included :75-79, `target.first` at :87/:95/:120/:135/:149/:163/:180, lastSection writes :124/:138/:153/:167, keys :114/:129/:143/:157/:171.
  - Shows: :59, :98/:112/:126/:139, :101/:115/:129/:142.
  - Docs: :61, :65-69, :79/:87, keys :102/:116/:134/:150/:170, stopAt :108/:126/:140/:156/:176, writes :111/:129/:144/:160/:180.
  - Anime: :50, :89/:92.
- Test counts are right: `TvDepartmentTargetsTest` has 15 @Test, `TvHomeTargetsTest` has 7.

Over-engineering check:
- The generic `SectionStop<S>` / `restoreTargetOf<S>` is what the issue suggests. It deletes the copied loop and `TvHomeTarget`, and it makes the KDoc true. It is a net deletion: keep.
- The `MoviesSection` enum replaces five literals that are each spelled about five times. Worth it.
- The String consts plus `categorySection(i)`/`groupSection(i)` add about 9 lines. They are borderline, but the issue asks for them and they guard silent restore misses (targets file vs page). Keep.
- Cut: "add pure JVM cases for the generic search". Every rule named there is already pinned:
  - lastSection wins: `TvDepartmentTargetsTest.kt:62-63`, `TvHomeTargetsTest.kt:40-42`.
  - null or unknown key: `TvDepartmentTargetsTest.kt:55-56/:87/:127`.
  - Home's first-non-empty fallback: `TvHomeTargetsTest.kt:22/:61`.
  - New cases would duplicate these.

Other corrections:
- `TvHome.kt:123` (`included` destructures the pairs) and `TvHomeTargetsTest`'s pair-built fixtures (e.g. :61 `TvHomeSection.COURSES to emptyList()`) also change. The step did not say so.
- The device walk covered only department pages, but `homeTargetOf` changes too, so Home is added.
- Documentaries plates play on OK (`onPlay`), and a play lands in Continue, so the walk uses the box's TV test profile.
- typed-department-and-tab-identity runs first and edits `TvShowsDepartmentPage.kt` (:82/:95) and `TvHome.kt` (:89-101). Lines must be found by content.
- Effort is medium: 9 files (2 targets, TvHome, 4 pages, 2 tests) of fragile focus code, not small.

FILE TOUCH LIST:
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvDepartmentTargets.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/home/TvHomeTargets.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvHome.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvMoviesDepartmentPage.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvShowsDepartmentPage.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvDocumentariesDepartmentPage.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvAnimeDepartmentPage.kt
- E ui-tv/src/test/kotlin/ui/tv/catalog/TvDepartmentTargetsTest.kt
- E ui-tv/src/test/kotlin/ui/tv/catalog/home/TvHomeTargetsTest.kt
- E ../Cargo.toml, ../web/package.json, app/build.gradle.kts (version bump; the same for every step below)

### Step 2: Emit TV department-page rows under the scroll index's emptiness rule (Movies, Documentaries). Verdict: TIGHTEN. Effort: small (ok)

Evidence:
- `TvMoviesDepartmentPage.kt`
  - :72-81 `included` filters empty rows. :87 scrolls with `+ 1`.
  - :114/:143/:157 are emitted unconditionally. Only genres at :128 is guarded.
- `TvDocumentariesDepartmentPage.kt`
  - :73 `included`. :79 the same `+ 1`.
  - :102/:116/:134/:150/:170 are emitted unconditionally.
- The rows return early when empty: `DeptRow` (`TvDepartmentRows.kt:75`), `DeptEntryRow` (:122), `DeptResumeRow` (`TvDepartmentResumeRow.kt:47`). So the extra items are zero-height, and guarding them changes nothing visible.
- TvHome's precedent is real: `drawn` at :131-141 and `if (TvHomeSection.X in drawn) item(...)` at :262-336.

The bug is real. Two corrections:
1. Spell each guard as `if (<section> in included)`, as TvHome reads `drawn`. Do not re-spell `dept.featured.isNotEmpty()` and the rest: that would be a second copy of the rule the issue complains about. After step 1 these are `MoviesSection.X in included` and `categorySection(i) in included`.
2. The proposed tests already exist and cannot catch the bug.
   - Movies with no Featured: `TvDepartmentPagesStateTest.recentlyAddedTakesArrivalFocusWhenEveryEarlierRowIsEmpty` (:93).
   - Docs with nothing underway restoring to a standalone: `TvDocumentariesDepartmentPageStateTest.aRestoreKeyNamingAStandaloneDocumentaryWinsOverThePagesOwnRestorer` (:113).
   - Both assert focus only, and both pass today because the target row is on screen anyway.
   - Instead, extend those two tests: hoist `listState` (both pages take it) and assert `listState.layoutInfo.totalItemsCount` == 1 + the non-empty sections. Movies is 3 after the fix and 5 before; Docs loses the empty Continue item.
   - This fails before the fix and is deterministic, because it counts items rather than measuring scroll position.

Device:
- OK on a Documentaries plate plays it, and the play lands in Continue. Use the TV test profile.

FILE TOUCH LIST:
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvMoviesDepartmentPage.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvDocumentariesDepartmentPage.kt
- E ui-tv/src/test/kotlin/ui/tv/catalog/TvDepartmentPagesStateTest.kt
- E ui-tv/src/test/kotlin/ui/tv/catalog/TvDocumentariesDepartmentPageStateTest.kt
- E version manifests

### Step 3: Merge TvCatalogNav's five rail restore effects. Verdict: TIGHTEN. Effort: small -> trivial

Evidence (`ui-tv/src/main/kotlin/ui/tv/catalog/TvCatalogNav.kt`):
- :97-103 are the seven flags, with :104 `redirectsFocus`.
- :118 is the `!ready` bar effect.
- :119-124 is the search effect.
- :125-132 is the menu effect, with no ready gate. Its comment requires it to come after :118.
- :133-162 are five rail effects that differ only in the RailItem.
- `menuRestoreKey` is `ui-tv/src/main/kotlin/ui/tv/system/TvMenuPage.kt:108`, `"menu:${screen.name}"`.
- Merging into one `LaunchedEffect(railTarget, ready)` is behaviour-equivalent: same keys and same order after the menu effect.

Over-engineering:
- An `internal fun railRestoreOf` plus a JVM test for a five-entry map literal is a function and a test written only to look up a map.
- Use a local `when (restoreKey)` inside `rememberTvCatalogRestore` instead. Kotlin allows the non-constant `menuRestoreKey(...)` branches.
- No new test is needed. `ui-tv/src/test/kotlin/ui/tv/TvStatsRailTest.kt:72-79` (`theStatsRowOpensThePageAndBackPutsTheRemoteBackOnIt`) already drives Back from a rail page through the real TvLibrary and asserts the rail row is focused, which exercises the merged effect.
- `TvCatalogScreenStateTest` :324 (menu) and :338 (search) cover the two effects kept.

Overlap:
- typed-department-and-tab-identity step 3 rewrites :50-95. This step touches only :97-162, so it applies on top.

Device wording fix:
- "open ... Settings and System from the rail" now says: OK on the rail row only, then Back at once; inside Settings and System press only Back (no OK, Left or Right on a row).

FILE TOUCH LIST:
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvCatalogNav.kt
- E version manifests

### Step 4: rememberStableRequester. Verdict: TIGHTEN (facts right; resolve the open "check" items). Effort: small -> medium

Counts verified:
- 27 own-requester vals. `git grep -nE 'val own[A-Za-z]* = remember \{ FocusRequester\(\) \}' -- ui-tv/src/main` gives 27, exactly the step's list.
- 25 pointer comments in 20 files, correct. They break down as:
  - 23 sit directly over a val.
  - `TvCollectionsPage.kt:197-200` is the `destinationRows` KDoc that covers the :220 val.
  - `ui-tv/src/main/kotlin/ui/tv/chrome/TvDepartmentsBar.kt:102-107` sits over `requesterOf(index)` (:85); its val `pillModifier` is at :108.
- The four vals without a pointer comment:
  - `TvResumeCard.kt:78` (canonical comment at :70-77).
  - `TvCollectionsPage.kt:220` (covered by the KDoc).
  - `TvCardMenu.kt:152` (own comment at :151).
  - `TvPinPrompt.kt:160` (own comment at :158-159).

Safety check, done here so the executor has no open "check":
- Every one of the 27 vals is read exactly once, as the focusRequester fallback. `TvWall.kt:206` is used only at :210; the crossing requesters are separate vals (:207-209). `TvCardMenu.kt:152` is used only at :158. `TvPinPrompt.kt:160` is used only at :163. All 27 convert.
- No val sits inside a branch that the call would newly enter.
  - `TvCardMenu`'s val already lives inside `if (choices.subtitleStyleVisible)` together with its button. That block stays.
  - `TvTitlePills.kt:147` sits in an unkeyed `forEachIndexed` inside `if (open)`, as today.
  - `TvSearchResults.kt:144-149` is a `when` whose `else -> own`. The call goes around the whole `when` (`rememberStableRequester(when (at) { 0 -> first; ask?.index -> focus; else -> null })`), not inside a branch. `first` and `focus` are non-null (:93-94) and `TvSearchCell`'s requester is non-null.
  - `TvCardMenu`'s fallback is inverted: `if (languages) own else current` becomes `rememberStableRequester(current.takeUnless { languages })`.
- The step's KDoc wording matches the canonical explanation at `TvResumeCard.kt:70-77`. That comment's own pointer to "TvHome.kt's own doc" is stale: TvHome has no such text. Do not carry the pointer over.

Corrections:
- Keep `TvMoviesDepartmentPage.kt:172-173`, the web/phone note; only its :174 sentence goes.
- Delete `TvCardMenu.kt:151` and `TvPinPrompt.kt:158-159` as well.
- Reword the `TvCollectionsPage.kt:197-200` KDoc to name [rememberStableRequester].
- Files outside package `ui.tv` need `import ui.tv.rememberStableRequester`; drop the `remember`/`FocusRequester` imports left unused.
- Place the function after `object TvFocus` (end of file). process-globals-to-owned-state step 1 cites `TvFocus.kt:40/:101/:146/:167/:171-180/:177`, and appending keeps those lines valid.
- Size: TvFocus.kt grows from 181 to about 193 lines. single-package-move-pass later moves TvSafeArea (`TvApp.kt:116`) into TvFocus.kt, which will pass 200. That is the lead's call there, not this step's.
- Effort: 24 files (TvFocus.kt, 22 val files, TvDepartmentsBar.kt). Mechanical, but in the focus code the user has been burned by, so medium.

Device wording:
- "profile tiles" plus "Navigate only" could lead to selecting a profile.
- Now says: arrows and Back only on the picker; OK only to open a plate or page; never OK on a row in Settings, System or a player menu.
- The converted Settings/System/player-menu rows (TvSettingsChoices, TvAppearanceBlock, TvCardMenu, TvPinPrompt) are deliberately left out of the walk; the unit suites cover them.

FILE TOUCH LIST:
- E ui-tv/src/main/kotlin/ui/tv/TvFocus.kt
- E ui-tv/src/main/kotlin/ui/tv/TvTextRow.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvCastRow.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvCollectionRow.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvCollectionsPage.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvDepartmentRows.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvFranchisePage.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvMoviesDepartmentPage.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvSearchResults.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvSearchRows.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvSimilarRow.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvTitlePills.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvWall.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/home/TvCourseList.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/home/TvHomeFeatures.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/home/TvPosterStrip.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/home/TvRecentBand.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/home/TvResumeCard.kt
- E ui-tv/src/main/kotlin/ui/tv/chrome/TvDepartmentsBar.kt
- E ui-tv/src/main/kotlin/ui/tv/player/TvCardMenu.kt
- E ui-tv/src/main/kotlin/ui/tv/player/TvSettingsChoices.kt
- E ui-tv/src/main/kotlin/ui/tv/profile/TvPinPrompt.kt
- E ui-tv/src/main/kotlin/ui/tv/profile/TvProfileTiles.kt
- E ui-tv/src/main/kotlin/ui/tv/system/TvAppearanceBlock.kt
- E version manifests
- (no test files)

### Step 5: Name the TV player key flags at the three positional calls. Verdict: TIGHTEN (device only). Effort: trivial (ok)

Evidence (all match):
- `ui-tv/src/main/kotlin/ui/tv/player/TvPlayerKeys.kt:126-135` `tvKeyAction(key, controlsShowing, focusInControls, canControl=, panelOpen=, upNextShown=, notesOpen=, statsShown=)`.
- `TvPlayerRemote.kt`: `onKey` at :78-87; :91 passes six Booleans positionally. `statsShown` is left at its default, which naming makes visible.
- `TvPlayerScreen.kt:161` passes six positional Booleans to `onKey`.
- `TvPlayerKeyHolder.kt:76` passes `barShown, onSeekBar` positionally.
- No new type is the right call.

Correction:
- The device walk is disproportionate. Named arguments in the same order change no value. The diff shows each value landing on the same parameter, and TvPlayerKeysTest and its siblings pin `tvKeyAction` itself.
- The walk also opened a player panel, which invites choosing an option, and a play lands in Continue.
- Device is now none.

FILE TOUCH LIST:
- E ui-tv/src/main/kotlin/ui/tv/player/TvPlayerRemote.kt
- E ui-tv/src/main/kotlin/ui/tv/player/TvPlayerScreen.kt
- E ui-tv/src/main/kotlin/ui/tv/player/TvPlayerKeyHolder.kt
- E version manifests

### Cross-cluster notes for the lead
- typed-department-and-tab-identity (runs first) edits:
  - `TvShowsDepartmentPage.kt` :82/:95 (step 2).
  - `TvCatalogNav.kt` :50-95 (step 3).
  - `TvHome.kt` :89/:310/:327 (step 3) and :98-101 (step 4).
  - Its step 2 may also touch `TvMoviesDepartmentPage.kt:111`, a display literal.
  - None of these overlap these steps' edits, but the lines shift.
- comments-to-invariants (runs after):
  - It rewrites the 9-line comment inside `TvHome.kt`'s `sections` listOf (:106-114). Step 1 here rebuilds that listOf, so locate it by content.
  - Its `TvDepartmentsBar.kt:137` reference shifts by about 5 lines after step 4.
- process-globals-to-owned-state step 1 edits `TvFocus.kt`. Appending `rememberStableRequester` at the end keeps its line refs.
- No step duplicates another cluster's step.

### Cluster value verdict: KEEP, tightened
- Steps 2-5 delete branching or copies: the 3 unguarded items become `in included`, five LaunchedEffects become one, 27 vals and 25 comments become one helper with one KDoc (about 60 lines net removed), and positional flags become named.
- Step 2 also fixes a real one-item-short restore scroll.
- Step 1 is roughly line-neutral but replaces `Pair`/`.first`/`.second` with a typed target, deletes the duplicated loop, and makes a false KDoc true.
- Cuts made: the `railRestoreOf` function and its map test; the redundant new JVM cases in step 1; and the existing-duplicate focus-only tests in step 2, replaced by an item-count assertion that actually fails before the fix.

---

## Cluster: shared-surface-glue-in-ui-common
### Step 1: move film-preload wiring + department scroll states into ui-common. Verdict: TIGHTEN (effort small, correct)

Over-engineering check: passes. The twins really are identical non-visual glue:
- ui-mobile/src/main/kotlin/ui/TitlePreloadWiring.kt:42-69 and ui-tv/src/main/kotlin/ui/tv/TvFilmPreloadWiring.kt:33-60: the same 26 code lines (hiltViewModel, five keyed remember/collectAsStateWithLifecycle blocks, resolvableQueueRows filter, constructor call). Only the function name, return type and KDoc wording differ.
- TitlePreloadUi (ui-mobile/src/main/kotlin/ui/catalog/TitlePreload.kt:37-47, KDoc :29-36) and TvTitlePreloadUi (ui-tv/src/main/kotlin/ui/tv/catalog/TvTitlePreload.kt:133-142): the same 7 fields, same defaults. Plain data plus callbacks, no drawing.
- DepartmentScrollStates.kt:20-43 vs TvDepartmentScrollStates.kt:19-41: the same holder and remember body; only names and comments differ.
- The visual composables (TitlePreload pill, TvPreloadPlate/TvPreloadDetailLines) are left alone. Good, that is the value trap the step avoids.

Line and name checks: every cited line matches. Call sites are complete (git grep, excluding baseline profiles): TV TvLibraryCatalogFrames.kt:89, TvTitlePage.kt:86, TvCatalogScreen.kt:196, TvCatalogBlend.kt:43, TvDepartmentPages.kt:77, TvCatalogBody.kt:41; phone LibraryTitleBranches.kt:62. Phone TitleDetailScreen.kt:69 and CatalogScreen.kt:107/:153 are in package ui.catalog, so they need no edit. LibraryBranchSupport.kt:22 and LibraryFlowBranches.kt:27/:32 import by FQN ui.catalog.*, so they need no edit either.
Deps: "No Gradle change" verified. ui-common/build.gradle.kts:15-39 has feature:catalog, feature:player, core:model, core:playback, lifecycle-runtime-compose and hilt-lifecycle-viewmodel-compose. ui-mobile:13 and ui-tv:22 depend on ui-common, and both already have core:playback (ui-mobile:25, ui-tv:30), which FilmPreloadState needs. No explicitApi mode.

Corrections:
1. **Drop the new Robolectric test.** It does not belong in a pure move:
   - Behaviour does not change.
   - ui-mobile/src/test/kotlin/ui/FilmPreloadFlowTest.kt already drives rememberFilmPreloadUi through LibraryFlowFixture and FakeFilmPreloading.
   - The kids rule is pinned by CatalogUiStateTest.resolvableQueueRowsDropsAGrownUpsFilmAKidsCatalogueCannotResolve (feature/catalog/src/test/kotlin/CatalogUiStateTest.kt:121).
   - A new flow test would grow a cleanup commit for coverage the move does not need.
2. **The moved KDocs name ui-mobile-only symbols**, which do not resolve from ui-common:
   - TitlePreloadUi's KDoc links [TitleDetailScreen] and [ui.LibraryFlowBranches].
   - rememberFilmPreloadUi's KDoc links [ui.catalog.TitleDetailScreen] and [LibraryFlowBranches].
   - DepartmentScrollStates' KDoc says "at the same [ui.LibraryBranches] level".
   - Reword each to name both surfaces in backticks.
3. **Line numbers will drift.** typed-department-and-tab-identity (a dependency) edits TvCatalogScreen, TvCatalogBlend, TvCatalogBody and TvDepartmentPages first, so the cited lines will have moved. The step should say to find the sites by name.
4. **Device wording.** "a film page's Preload control" invites pressing it, which enqueues a download. Make it look-only.
5. Not a step edit: app/src/release/generated/baselineProfiles/{baseline,startup}-prof.txt list ui/tv/TvFilmPreloadWiringKt, TvTitlePreloadUi and TvDepartmentScrollStates. Those entries just go stale, and the next profile generation replaces them. Do not hand-edit them.

FILE TOUCH LIST (step 1):
- C ui-common/src/main/kotlin/ui/catalog/FilmPreloadUi.kt
- M ui-mobile/src/main/kotlin/ui/catalog/DepartmentScrollStates.kt -> ui-common/src/main/kotlin/ui/catalog/DepartmentScrollStates.kt (+E: public, KDoc)
- D ui-mobile/src/main/kotlin/ui/TitlePreloadWiring.kt
- D ui-tv/src/main/kotlin/ui/tv/TvFilmPreloadWiring.kt
- D ui-tv/src/main/kotlin/ui/tv/catalog/TvDepartmentScrollStates.kt
- E ui-mobile/src/main/kotlin/ui/catalog/TitlePreload.kt (TitlePreloadUi + KDoc removed)
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvTitlePreload.kt (TvTitlePreloadUi + KDoc removed)
- E ui-mobile/src/main/kotlin/ui/LibraryTitleBranches.kt (import)
- E ui-tv/src/main/kotlin/ui/tv/TvLibraryCatalogFrames.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvTitlePage.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvCatalogScreen.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvCatalogBlend.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvDepartmentPages.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvCatalogBody.kt
- E ui-tv/src/test/kotlin/ui/tv/catalog/TvTitlePreloadStateTest.kt
- E ui-tv/src/test/kotlin/ui/tv/catalog/TvTitlePageStateTest.kt
- E ui-tv/src/test/kotlin/ui/tv/catalog/TvDepartmentPagesStateTest.kt
- E ../Cargo.toml, ../web/package.json, app/build.gradle.kts (version bump)

### Step 2: phone person page onto rememberPersonLookup; delete rememberPerson. Verdict: TIGHTEN (effort small, correct)

Evidence:
- ui-mobile/src/main/kotlin/ui/LibraryBrowseBranches.kt:
  - `var attempted` at :202
  - rememberPerson with a try/finally at :203-209
  - `!attempted || shelves == null` at :213
  - the import at :37; the KDoc at :178-185 names rememberPerson (:181) and `attempted` (:184)
- RememberLookups.kt:70-89 holds rememberPerson; its only caller is :203. KDocs at :91 and :95.
- TV already does this at ui-tv/src/main/kotlin/ui/tv/TvLibraryExtraFrames.kt:50.

The semantics carry over:
- rememberPersonLookup's `loading` starts true per key and flips false in `finally`. That is the same as `attempted` starting false and set in `finally`.
- A failed lookup leaves person null and loading false, so the page shows "Nobody by that number", as today.

Corrections:
1. Removing `attempted` leaves the imports androidx.compose.runtime.mutableStateOf (:7) and setValue (:9) unused. getValue stays, because :127 and :164 delegate with `by`. Say so.
2. typed-navigation-ids step 1 (a dependency) has already made `at.personId` a Long? by then, so :195 reads `val id = at.personId`. Mention it so the executor does not reintroduce toLongOrNull.
3. Tests: one LibraryFlowTest case is the right size, because no phone test reaches PersonFrame. Trim it to a single case: a gated lookup answering null shows "Loading your library…" and then "Nobody by that number…". PersonScreenTest already covers the found-person page. The TV equivalent is TvSearchAndGenreTest.peopleGroupWithFilterChipsNarrowsToJustThatSection (:286-311). fixture.repository is a strict mockk (LibraryFlowFixture.kt:88), so `person` must be stubbed with coEvery.

FILE TOUCH LIST (step 2):
- E ui-mobile/src/main/kotlin/ui/LibraryBrowseBranches.kt
- E ui-common/src/main/kotlin/ui/catalog/RememberLookups.kt
- E ui-mobile/src/test/kotlin/ui/LibraryFlowTest.kt
- E ../Cargo.toml, ../web/package.json, app/build.gradle.kts

### Step 3: collapse RememberLookups onto one private rememberLookup built on orDefault. Verdict: TIGHTEN (effort small, correct)

Does it shrink the code? Yes.
- After step 2, four public functions hold about 77 code lines of the same remember + LaunchedEffect + catch(CancellationException) rethrow + catch(Exception) Log.w skeleton: rememberTitleInfo :25-43, rememberTitleCredits :50-68, rememberPersonLookup :100-122, rememberFranchiseOverviews :129-144. All four ranges verified.
- One private helper (about 12 lines) plus four 2-3-line bodies comes to about 25 lines, a net of roughly -40 lines.
- The android.util.Log and kotlinx.coroutines.CancellationException imports go too.
- This is a real deletion of a repeated skeleton, not new indirection.

orDefault does not exist yet. It is failure-handling-one-way step 1, `suspend fun <T> orDefault(default: T, what: String? = null, block: suspend () -> T): T` (package data, file OrDefault.kt). That cluster is in shared-surface's depends_on, and its own step lists RememberLookups as "already compliant (leave)", so the two do not collide.

rememberPersonLookup's loading semantics survive with
`LaunchedEffect(key) { value = orDefault(initial, what) { lookup(key) }; loading = false }`:
- On failure, orDefault returns `initial`, which equals today's untouched remember(key) state, and loading goes false.
- On cancellation, loading stays true, but the key changed (remember(key) resets it) or composition left. That is the same visible behaviour as today.

The cited test lines are right: RememberLookupTest.kt reads "CatalogMetadata" at :52, :53 and :66. The :53 "title details" check survives, because orDefault logs "title details lookup failed". RememberPortrait.kt:67 keeps its own Log.w, as stated.

Corrections:
1. **Drop the duplicate test.** "One where leaving composition mid-lookup cancels without logging a failure" already exists as RememberLookupTest.titleLookupCancellationRemainsCancellationWithoutAFailureDiagnostic (:56-67). Keep only the new failure case, aimed at the one function with no test of its own: a throwing rememberPersonLookup gives person null and loading false. Nothing tests rememberPersonLookup directly today (git grep).
2. Give the helper's shape, to remove guesswork (no new public type).
3. Cross-cluster note, no step change needed: ui-common/build.gradle.kts:19-24 says core:data is there only for TitleInfo. After this step orDefault also needs it. catalog-records-mirrored-in-core-model step 2 (which runs after this cluster) already words its removal as conditional ("may still need core:data … once failure-handling-one-way lands") and rewords the comment to the remaining reason.

FILE TOUCH LIST (step 3):
- E ui-common/src/main/kotlin/ui/catalog/RememberLookups.kt
- E ui-common/src/test/kotlin/ui/catalog/RememberLookupTest.kt
- E ../Cargo.toml, ../web/package.json, app/build.gradle.kts

Duplicates and overlaps:
- comments-to-invariants deletes the "split out of … line guideline" clauses, TitlePreloadWiring.kt:20-21 and TvFilmPreloadWiring.kt:20-22 among them. It depends on this cluster and works from a grep, so step 1 deleting those files is consistent, not a duplicate.
- single-package-move-pass step 7 (ui-common gets ui.common.*) runs last and will move FilmPreloadUi.kt and DepartmentScrollStates.kt with the rest. No conflict.

**Cluster value verdict: KEEP (tighten).**
- Steps 1-2 delete one of two byte-identical copies of non-visual glue: about 60 lines plus one duplicate type on each surface. They also remove a hand-rolled loading flag that duplicates a shared helper.
- Step 3 replaces four copies of a 15-line try/catch skeleton with one.
- Nothing visual is merged. The cuts are only the two surplus tests.

---

## Cluster: process-globals-to-owned-state

### Step 1: Read the TV accent from the theme and delete Palette.Imprint [small] — TIGHTEN (facts right; four gaps)

Over-engineering: none. Deletes a process-wide snapshot `var` written from two `SideEffect`s and replaces it with the standard theme read. Net lines about neutral (longer expression, fewer globals/KDoc).

Verified (android/):
- core/designsystem/src/main/kotlin/Palette.kt:63-80 KDoc, :81 `var Imprint: Color by mutableStateOf(Color(0xFFE57A61))`. Imports :3-5 are used only by :81. OK.
- core/designsystem/src/main/kotlin/Theme.kt:163-165 comment, :166 `SideEffect { Palette.Imprint = accentColor }`, :175 the system-bar SideEffect. OK.
- ui-tv/src/main/kotlin/ui/tv/TvTheme.kt:102-103 comment, :104 the write, :5 SideEffect import, :36 `primary = accent`. OK.
- 21 reads in 13 ui-tv main files: every cited line matches. Each read is inside composition: a @Composable body, a Modifier chain built in composition, or a TvFocus @Composable helper. The step's `git grep -n 'Palette.Imprint' -- ui-tv/src/main` prints 23 lines in 14 files, because it also lists TvTheme.kt:102/:104, which the step deletes. Say so.
- The two DrawScope reads are confirmed: TvReadableParagraph.kt:48 inside `drawBehind` and TvTitlePreload.kt:97 inside `drawBehind`. Both enclosing functions are @Composable, so hoisting `val accent` works.
- TvFocus.textStyle (TvFocus.kt:171-180, fun at :175) is the only non-@Composable reader. It has 10 main call sites: TvIndexRow:134, TvTextRow:58, TvAchievementItems:57, TvCollectionRow:113, TvSearchRows:95, TvStatsPage:72/:85/:98, TvContinueBand:122 and TvInfoBlock:66. Every one is an argument to a `Text(...)` call, so all are composable. No test calls it. Marking it @Composable is valid, and the "accent parameter if not composable" fallback is dead text; drop it.
- There is exactly one MaterialTheme on TV. TvApp.kt:51 wraps everything in TvTheme, and nothing in ui-tv or app nests another MaterialTheme(...). ui-tv cannot see compose-material3: ui-tv/build.gradle.kts:5-7, ui-common has no material3, and core/designsystem keeps it `implementation`. So `MaterialTheme.colorScheme.primary` is always androidx.tv.material3's, and it equals the accent Imprint held. This is the same colour, with no visible change.
- The phone's MediagramTheme write (Theme.kt:166) has no reader: no ui-mobile or ui-common file reads Imprint. Deleting it changes nothing on the phone.
- Tests: TvThemeTest.kt:51-55 matches. Its sibling test at :45-49 already asserts `colorScheme.primary == Accent.Default.dark`, so the rewritten test needs a new name. CatalogueColorsTest.kt:32/37/93/105/111 match. TvLibraryRemoteTest.kt:128 matches, and its TvTheme calls (:89/:108/:136/:148) use the default accent. Its `import designsystem.Palette` (:15) is used only at :128, so it becomes unused and must be swapped for `designsystem.Accent`.
- Accent.kt:11 links [Palette.Imprint], which breaks after deletion. Repointing it is right.

Gaps to tighten:
- (a) Imports. `import designsystem.Palette` goes unused in 8 files: TvFocus, TvIndexRow, TvTextField, TvArtTile, TvReadableParagraph, TvSearchRows, TvSectionTabs and TvTitlePills. TvPlateMarks, TvTitlePreload, TvNotesPanel, TvOverlayButtons and TvSeekBar read other Palette values and keep it. `androidx.tv.material3.MaterialTheme` must be added in 5 files: TvReadableParagraph, TvTitlePreload, TvNotesPanel, TvOverlayButtons and TvSeekBar.
- (b) TvFocus.kt:40 ("Imprint red already means...") is listed but the Change never says what to do with it. It names DESIGN.md's Imprint accent role, not the property, so leave it.
- (c) Line drift. tv-focus-targets-and-effects runs first (this cluster depends on it). Its step 4 edits TvSearchRows.kt (around :78) and TvTitlePills.kt:147, and adds rememberStableRequester to TvFocus.kt. TvSearchRows:156 will move, and so may TvFocus's lines if the helper lands above the object. The executor should locate the reads with the git grep, not by line.
- (d) Name the rewritten test without "Imprint", e.g. `choosingAnAccentSetsThePrimaryColour`.

Effort: small is right (mechanical, 16 main files plus 3 tests).
Device: TV-only walk, "NEVER change the accent" is good. No phone check is needed.

FILE TOUCH LIST:
- E core/designsystem/src/main/kotlin/Palette.kt
- E core/designsystem/src/main/kotlin/Theme.kt
- E core/designsystem/src/main/kotlin/Accent.kt
- E ui-tv/src/main/kotlin/ui/tv/TvTheme.kt
- E ui-tv/src/main/kotlin/ui/tv/TvFocus.kt
- E ui-tv/src/main/kotlin/ui/tv/TvIndexRow.kt
- E ui-tv/src/main/kotlin/ui/tv/TvTextField.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvArtTile.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvPlateMarks.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvReadableParagraph.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvSearchRows.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvSectionTabs.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvTitlePills.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvTitlePreload.kt
- E ui-tv/src/main/kotlin/ui/tv/player/TvNotesPanel.kt
- E ui-tv/src/main/kotlin/ui/tv/player/TvOverlayButtons.kt
- E ui-tv/src/main/kotlin/ui/tv/player/TvSeekBar.kt
- E ui-tv/src/test/kotlin/ui/tv/TvThemeTest.kt
- E core/designsystem/src/test/kotlin/CatalogueColorsTest.kt
- E ui-tv/src/androidTest/kotlin/ui/tv/TvLibraryRemoteTest.kt

### Step 2: PortraitRequestLog as the one owner [medium] — TIGHTEN (simplify the design; the step invents behaviour)

Over-engineering: the step adds new machinery that today's code does not have.
- "Track in flight separately from done; while in flight a second card waits and does not fetch again" is new behaviour. "Waits" needs a shared observable (Deferred or snapshot map) so the second card learns the result.
- Today a second card mounted during an in-flight fetch fetches again. At ui-common/src/main/kotlin/ui/catalog/RememberPortrait.kt:53, `retrying = attempted && !done` is true.
- Same person on two mounted cards at once is rare. Nothing asks for dedupe: YAGNI.
- "Choose one and apply it at all 17 sites" leaves the threading design to the executor: vague.

Key fact found by reading: the reservation is redundant.
- PortraitRequestLog.kt:25 `shouldRequest = asked.add(id)` has exactly one caller: rememberPortrait, via BrowseViewModel.kt:35.
- RememberPortrait.kt:55 adds every reserved id to `attemptedPortraitFetches`, which has the same lifetime: process globals versus a @Singleton.
- So `asked` == attempted. The whole rule today reduces to "fetch iff this person's fetch has not finished", plus "seed a remount from the found-path map".
- Cases checked:
  - First mount: fetch.
  - Concurrent mount: fetch again.
  - After cancellation: fetch again.
  - After success or error: never.
  - Remount: seeded.
- Consequences:
  - The `asked` set can be deleted outright.
  - The mutating query becomes a plain query, `needsFetch(id) = id !in finished`.
  - That fixes should_request_portrait_reserves by removing the hidden mutation rather than renaming it to tryReserve.
  - Behaviour is unchanged.

Threading:
- rememberPortrait needs three operations from the log: seed path, needs-fetch and finish. The seed must stay synchronous; RememberPortrait.kt:30-41 documents why remounts must not show initials.
- Threading three lambdas is worse than threading the log itself.
- Threading the log means retyping `shouldRequestPortrait: (Long) -> Boolean` to `portraits: PortraitRequestLog` in the same 17 files the step's rename already touches. The churn is the same, and the result has one owner.
- Both surfaces and ui-common already depend on core:data: ui-mobile/build.gradle.kts:21, ui-tv/build.gradle.kts:34, ui-common/build.gradle.kts:24.
- This is consistent with catalog-records-mirrored-in-core-model step 3, which already anticipates "PortraitRequestLog if process-globals-to-owned-state step 2 threads the log into rememberPortrait" when deciding whether ui-common still needs core:data.
- Defaults:
  - The 6 `= { false }` defaults are CollectionScreen.kt:59, TitleDetailScreen.kt:68, TvCollection.kt:44, TvSearch.kt:53/:112 and TvTitlePage.kt:78. They become `= PortraitRequestLog()`.
  - Every production caller passes both portrait arguments: LibraryTitleBranches.kt:58/:109, TvLibraryCatalogFrames.kt:68/:140 and TvLibraryFrames.kt:71. Only tests use the defaults, and their fetchPortrait default is `{ null }`, so nothing changes.
- Not recommended now: folding fetchPortrait into the log too, which would need a repository in the log and remove the second threaded lambda. It is a larger redesign nobody asked for.

Verified:
- RememberPortrait.kt:73-80 globals, :50-66 uses, :13-42 KDoc and :67 Log.w are all OK.
- PortraitRequestLog.kt:18-26 @Singleton, :25 shouldRequest and :7-17 KDoc are OK.
- BrowseViewModel.kt:35 is OK, and :27 holds `private val portraits`.
- 17 production files are confirmed, with 36 occurrences of `shouldRequestPortrait`: ui-mobile 6, ui-tv 11.
- Test references are confirmed: RememberLookupTest has 12, PortraitRequestLogTest 3, TvSearchPeopleCardsStateTest:131 1 and TvSearchResultsStateTest:215 1.
- The RememberLookupTest workaround spans :69-153 (three portrait tests, `PersonId + 1` / `+ 2` and the PersonId const at :153), not :87-153.

Duplicates and order:
- shared-surface-glue-in-ui-common step 3 runs first and edits RememberLookupTest's CatalogMetadata assertions (:52/:53/:66). It adds two cases, so this step's test lines shift; cite tests by name.
- shared-surface step 3 leaves RememberPortrait.kt:67's Log.w alone.
- comments-to-invariants defers the rename to this step. The rename becomes a deletion and the decision still lives here.

Effort: medium stays (22 files), though each edit is mechanical. The logic change is small and keeps behaviour.

FILE TOUCH LIST:
- E core/data/src/main/kotlin/PortraitRequestLog.kt
- E core/data/src/test/kotlin/PortraitRequestLogTest.kt
- E feature/catalog/src/main/kotlin/BrowseViewModel.kt
- E ui-common/src/main/kotlin/ui/catalog/RememberPortrait.kt
- E ui-common/src/test/kotlin/ui/catalog/RememberLookupTest.kt
- E ui-mobile/src/main/kotlin/ui/LibraryBrowseBranches.kt
- E ui-mobile/src/main/kotlin/ui/LibraryTitleBranches.kt
- E ui-mobile/src/main/kotlin/ui/catalog/CastPanel.kt
- E ui-mobile/src/main/kotlin/ui/catalog/CollectionScreen.kt
- E ui-mobile/src/main/kotlin/ui/catalog/SearchGroupsView.kt
- E ui-mobile/src/main/kotlin/ui/catalog/TitleDetailScreen.kt
- E ui-tv/src/main/kotlin/ui/tv/TvLibraryCatalogFrames.kt
- E ui-tv/src/main/kotlin/ui/tv/TvLibraryExtraFrames.kt
- E ui-tv/src/main/kotlin/ui/tv/TvLibraryFrames.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvCastRow.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvCollection.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvSearch.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvSearchCell.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvSearchResults.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvSearchRows.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvSeriesPage.kt
- E ui-tv/src/main/kotlin/ui/tv/catalog/TvTitlePage.kt
- E ui-tv/src/test/kotlin/ui/tv/catalog/TvSearchPeopleCardsStateTest.kt
- E ui-tv/src/test/kotlin/ui/tv/catalog/TvSearchResultsStateTest.kt

### Step 3: PiP listeners in PipController; delete PipEntryPoint [small] — TIGHTEN (facts right; resolve the two "either/or"s)

Over-engineering: none. Removes a public mutable cross-module bridge (`object PipEntryPoint` with @Volatile slots) and a MainActivity override plus a branch. The replacement is the platform's own listener API. Lines are about neutral.

Verified:
- gradle/libs.versions.toml:3 has `activityCompose = "1.13.0"`. javap on the cached activity-1.13.0.aar shows ComponentActivity has public final `addOnUserLeaveHintListener(Runnable)`/`remove...` and `addOnPictureInPictureModeChangedListener(androidx.core.util.Consumer<PictureInPictureModeChangedInfo>)`/`remove...`. ui-mobile has activity-compose (:26) and androidx.core (:31) on its classpath, plus androidx.lifecycle.Lifecycle (already imported by MobileApp.kt:19).
- PipController.kt:
  - :34-48 object, :86-89 assigns and :91-92 clears: OK.
  - DisposableEffect :76-103: OK.
  - The KDoc naming PipEntryPoint is :53-68 (mentions at :56/:59): OK.
  - `findActivity()` returns `Activity?` (ui-common/src/main/kotlin/ui/player/ActivityContext.kt:15), so a `as? ComponentActivity` cast is needed.
- MainActivity.kt:
  - Imports: Build :5 (used only at :106), Lifecycle :15 (used only at :123/:138), PipEntryPoint :21.
  - onUserLeaveHint :104-107, KDoc :96-103.
  - onPictureInPictureModeChanged :120-126, KDoc :109-119.
  - isInPip :48, seeded :69, provided :75.
  - isPipDismissal :129-139 (KDoc :129-137).
  - All OK.
- PipActions.kt:74-75: the comment names MainActivity.onUserLeaveHint and PipEntryPoint. OK.
- app/src/test holds only PipDismissalTest.kt (package com.mediagram.android). Moving it empties :app's unit-test source set, so `:app:testDebugUnitTest` becomes NO-SOURCE; drop it from the fast loop.

Gaps to tighten:
- (a) Pick "keep a one-line onPictureInPictureModeChanged override that only sets isInPip" rather than a listener in onCreate. It is the smallest diff, and LocalIsInPictureInPicture's KDoc (PipController.kt:15-22, "published by MainActivity.onPictureInPictureModeChanged") stays true. ComponentActivity's 2-arg override dispatches to the registered listeners from `super`, so the override must keep calling super.
- (b) Name the destination: isPipDismissal goes into PipController.kt (123 lines). PipDismissalTest moves to the ui-mobile test tree with its package changed to ui.player.
- (c) Register the user-leave-hint Runnable only when SDK_INT <= 30. The effect already returns early below 26.
- (d) Device note: the plays land in Continue (expected).

Not touched by 1ab59974 (no update or subtitle path).
Effort: small is right.

FILE TOUCH LIST:
- E ui-mobile/src/main/kotlin/ui/player/PipController.kt
- E ui-mobile/src/main/kotlin/ui/player/PipActions.kt
- E app/src/main/kotlin/com/mediagram/android/MainActivity.kt
- M app/src/test/kotlin/com/mediagram/android/PipDismissalTest.kt -> ui-mobile/src/test/kotlin/ui/player/PipDismissalTest.kt (package edit)

### Cluster value verdict: KEEP (tighten all three)

Each step deletes a process global that hides a dependency:
- Imprint's composition-time write
- the portrait file maps
- the PiP @Volatile bridge

Each replaces it with the owner the platform or DI already provides: the theme, the @Singleton log and ComponentActivity listeners. Step 2 is worth doing only in the simplified form, which keeps behaviour, deletes the redundant `asked` set and threads the log. Do not add the invented in-flight/wait machinery.
