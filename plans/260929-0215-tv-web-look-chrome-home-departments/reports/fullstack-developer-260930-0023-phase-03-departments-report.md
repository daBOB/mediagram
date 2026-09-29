# Phase 03 — TV department pages: heroes, rows, Anime, Documentaries

Branch: `worktree-agent-a4e8c1b7d67ee7a8d` (worktree off `main` at `a21b7df0`).
Status: in-review (box walk, plan step 7, left to the lead per instructions).

## Files changed

**feature/catalog (new, pure, tested)**
- `HeroArtOf.kt` — `heroArtOf` moved from `ui-mobile`'s `ui.HeroArtOf`, now public so both surfaces share it.
- `DepartmentLines.kt` — `moviesLineOf`/`showsLineOf`/`animeLineOf`/`documentariesLineOf`/`collectionsLineOf`, all spelling through `spelledCountOf` (already existed, now actually used by Movies/Series/Tutorials/Anime/Collections too — Documentaries already did).
- `NextUp.kt` — added `resumeCardsOf(continues, nextUp, watch, heldIds)`, lifted out of `MagazineHome.kt`'s own inline build.
- `MagazineHome.kt` — calls `resumeCardsOf` instead of its own copy.
- Tests: `DepartmentLinesTest.kt`, `HeroArtOfTest.kt` (new).

**ui-mobile (switched to the lifted functions)**
- Deleted `ui/HeroArtOf.kt`; `LibraryBranchSupport.kt` now imports `catalog.heroArtOf`.
- `Movies/Shows/Anime/Documentaries/CollectionsDepartmentScreen.kt` (or `.../CollectionsScreen.kt`): call the lifted line functions and `resumeCardsOf`; removed their own private duplicates.
- `AnimeDepartmentScreenTest.kt`: **the one tablet test expectation changed** — `"1 show"`/`"2 films"` → `"one show"`/`"two films"`, the spelling parity fix (small counts now spell the same way Documentaries' line already did).

**ui-tv (new)**
- `TvDepartmentHero.kt` — kicker/huge title/line, 360dp fixed, art right 70%/100% (Artwork), hidden under Solid, quote top-right, never a focus stop. Test: `TvDepartmentHeroStateTest.kt`.
- `TvAnimeDepartmentPage.kt` — hero, Continue watching, one `TvWall` with "Series"/"Films" headings. Test: `TvAnimeDepartmentPageStateTest.kt`.
- `TvDocumentariesDepartmentPage.kt` — hero, Continue watching, category rows (folder opens, single plays), Recently added, one row per folder ("All N →" when it holds more), Standalone. Test: `TvDocumentariesDepartmentPageStateTest.kt`.
- `TvDepartmentResumeRow.kt` — shared lazy resume-card row (Continue/Continue watching everywhere), reused by Shows/Anime/Documentaries.
- `TvDepartmentScrollStates.kt` — hoisted list/grid states per department, mirrors the tablet's `DepartmentScrollStates`.
- `TvCatalogBlend.kt` — extracted the department-hero bar-blend calculation out of `TvCatalogScreen.kt` (kept that file from growing past its pre-phase size).

**ui-tv (modified)**
- `TvMoviesDepartmentPage.kt` — rewritten: `LazyColumn` (not `Column`+`verticalScroll`), new hero, own `focusRestorer`, `lastSection` tracking.
- `TvShowsDepartmentPage.kt` — new hero, `resumeCardsOf`-based Continue row (was plain entry plates that opened a title page instead of playing — fixed to match the Requirements' own "OK plays"), `lastSection` tracking.
- `TvDepartmentTargets.kt` — shared `DeptSection`/`restoreTargetOf` (generalises `TvHomeTargets`' own rule over named rows instead of one enum); `moviesDeptTargetOf`/`showsDeptTargetOf` dropped the removed hero-focus branch; added `animeDeptTargetOf`/`documentariesDeptTargetOf`.
- `TvDepartmentRows.kt` — `DeptRow`/`DeptEntryRow`/`GenreTileRow` headings swapped `TvSectionHeading` → `TvBandHeading`; added `onSectionFocused` (lastSection) and `trailing` (Documentaries' "All N →").
- `TvDepartmentPages.kt` — routing: Anime and Documentaries route to their own pages now; `DepartmentOrShelfWall` takes `deptScroll`.
- `TvCatalogBody.kt`, `TvCatalogScreen.kt` — thread `TvDepartmentScrollStates` through; blend generalised to the active department tab via `TvCatalogBlend.kt`.
- `TvCollectionsPage.kt` — added the hero above the franchise row.
- `TvWall.kt` — optional hoisted `gridState` param (used by Shows/Anime for the blend).
- `TvLists.kt` — added a `testTag` (`TvListsTestTag`) so a test can scroll it — see "Collections hero squeezes the list" below.
- Deleted `TvCoverStory.kt` (no caller left); fixed the one doc reference to it in `home/TvCoverSlide.kt`.
- Docs: `docs/web-player.md` (both recorded differences rewritten — TV now has the Anime and Documentaries pages), `DESIGN.md` (the "no television front page" bullet removed), `docs/project-changelog.md` (0.84.0 entry).

## Tests

- `check.sh` green (clippy, cargo test, gradle `testDebugUnitTest`/`lint`/`compileDebugAndroidTestKotlin`); bun step skipped (no `web/node_modules` in this worktree, the script's own documented skip).
- `ui-tv`, `ui-mobile`, `feature:catalog` unit tests all green (392/392, 175 cargo, etc.) after every fix below.
- Every card row is keyed: `DeptRow`/`DeptEntryRow`/`DeptResumeRow`/`GenreTileRow` (`itemsIndexed(key=...)`) and `TvWall` (`items(key=...)`) all pass explicit keys — verified by inspection, all pre-existing on `TvWall`/`DeptRow`/`DeptEntryRow`/`GenreTileRow`, added on the new `DeptResumeRow`.
- Restore-key-wins-over-restorer, honestly scoped: **Movies and Documentaries** (each its own dedicated `LazyColumn`) carry a page-level `focusRestorer(fallback = <the shared row FocusRequester>)`, proven by `aRestoreKeyNamingARowPastTheHeroWinsOverThePagesOwnRestorer` (Movies) and `aRestoreKeyNamingAStandaloneDocumentaryWinsOverThePagesOwnRestorer` (Documentaries). **Series/Tutorials and Anime do not** — see "TvWall restorer: tried and reverted" below; their own tests instead prove the explicit key still wins over `TvWall`'s plain plate-0 default.

### TvWall restorer: tried and reverted

Added `Modifier.focusRestorer(fallback = focusRequester)` to `TvWall`'s own `LazyVerticalGrid` first, to give Series/Tutorials/Anime the same page-level restorer Movies/Documentaries have. `TvSearchAndGenreTest.aShowsGenreLinkIsWhereBackFromItsWallLands` broke: `TvCollection`'s own `TvGenreLinks` (a `TvWall` `header`) requests focus on a genre link with a plain, unconditional `LaunchedEffect(focused) { back.requestFocus() }` — adding the restorer made that request lose, landing on the wall's own remembered/fallback plate instead of the genre link. `TvWall` is shared by `TvGenre`, `TvFranchisePage`, `TvPersonPage`, `TvList`, `TvLatestPage`, `TvGenresIndex`, `TvKeptWall`, `TvCollection`, `TvShelfWall` — several with their own header-level explicit-focus mechanisms outside `TvWall`'s own restore-key search. Reverted rather than risk the same class of regression across all of them without box time to check each. Series/Tutorials/Anime's own arrival logic (unaffected — it never depended on the restorer) still works; only "Right from rail returns to the *exact* nested plate" is unproven for these two pages specifically. Documented in `TvWall.kt`'s own history via this report; no dangling reference left in code.

### Open item from phase 02 — Home's Rail-Right, investigated not fixed

Read `TvHome.kt`: its own `.focusRestorer(fallback = ...)` sits on the same one `LazyColumn`, and its arrival effect is gated to fire once per mount (`arrived`), so a later Rail-Right has nothing explicit to re-request — only the restorer's own "remembered child" can put focus back on the exact Continue card. The `TvGenreLinks` regression above shows `focusRestorer` losing to (or, for Home, plausibly failing to record) a focus target nested two lazy levels down (`LazyColumn` item → `Row` → `LazyRow` → card) — the same class of limitation, opposite direction. Home's own bug and my `TvWall` regression both point at "a restorer's own remembered-child tracking is unreliable once the real focus target sits inside a second, nested lazy list", not two unrelated causes. I did not change `TvHome.kt`: the phase 02 report's own finding ("Not reproducible in Robolectric... verified on the box only") means I cannot prove a fix here without the box, and `TvHome.kt` is outside this phase's file list. Recommend the lead's own box pass (phase 04, or a follow-up) tries: does Rail-Right from a Continue card, after a genuinely nested focus target, land on the card or the cover — and if it's still the cover, this same-class hypothesis is where to start.

### Collections hero squeezes "Your lists"

`TvCollectionsPage`'s new hero is the fixed 360dp every department hero is, drawn unconditionally even with zero franchises (the common case). On the real 540dp screen that leaves "Your lists" (`TvLists`, `Modifier.weight(1f)`) a small remainder — `TvKeptWallStateTest`'s own two-list fixture needed an explicit `performScrollToIndex` to reach its second row, where before this phase it fit without scrolling. Real remote behaviour (Down scrolls it in) is unaffected, but a library with several lists is worth a look on the box walk — flagged rather than silently special-cased down to a shorter hero for this one department without asking first.

## Box walk checklist (plan step 7, not run here)

- Each department pill: hero (kicker/title/line, art right 70%, quote top-right when there's a tagline), then the web's own rows, in order.
- Anime: Series then Films headings in one wall; a show opens the show, a film opens its title page (not play).
- Documentaries: category rows (folder opens, single plays), a folder's "All N →" opens the collection, Standalone plays on OK; empty library still shows the upload hint.
- Down from a pill lands on the first row's own first stop, hero still on screen (360dp leaves ~180dp of a 540dp screen above the fold).
- Back from a title opened on any row lands back on that plate.
- Rail Right from a plate on Movies/Documentaries: exact plate vs. the page's own default (untested here; the restorer coexists with the explicit-target effect but has not been proven against a genuinely nested target on real hardware).
- Rail Right on Series/Tutorials/Anime: expect the page's own default stop, not necessarily the exact plate (no restorer there — see above).
- Collections: hero, then "Your lists" — check whether several lists feel cramped under it.
- Bar blend: translucent-to-opaque over Movies/Series/Tutorials/Anime/Documentaries heroes with real backdrop art, opaque throughout on Collections and on a department with no lead art or under Solid.

## Version

`0.84.0` — `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` (`versionName`; `versionCode` untouched), `Cargo.lock` (`mediagram`, `mediagram-cache`, `mediagram-core`, `mediagram-tmdb`, `mlib-spec`), verified with `cargo metadata --locked --offline`.

## Unresolved / for the lead

1. Home's Rail-Right-from-Continue-card cause (above) — same-class hypothesis, box-only verifiable, not fixed.
2. `TvWall`'s own restorer — deliberately not added; Series/Tutorials/Anime "Right from rail" returns to the page's default stop, not the exact plate.
3. Collections' hero vs. "Your lists" room on a real screen — worth a look, not changed.
