# Code review: Documentaries department on Android (uncommitted, over 9f1080ce)

Worktree `/home/andre/Workspace/mediagram-home`, branch `feat/android-home-web-parity`.
Spec: `phase-03-documentaries-department.md`. Web reference: `documentaries.js`,
`department-pages.js:74-124`, `app.js:116-123`, `home-shelves.js`, `search-view.js`,
`plays-next.js`, `shelf-view.js:87-107`.

## Gate

`./gradlew :core:model:test :core:data:testDebugUnitTest :feature:catalog:testDebugUnitTest :ui-common:testDebugUnitTest :ui-mobile:testDebugUnitTest :ui-tv:testDebugUnitTest :core:designsystem:testDebugUnitTest lint :app:assembleDebug --continue` passes (BUILD SUCCESSFUL, EXIT=0).
The TV androidTests were not run because that needs a device (see L9).

Probes ran in a scratch copy (`ProbeDocumentariesTest`, feature/catalog). Their output:

```
PROBE search films=0 episodes=0 lessons=0 filters=[]
PROBE runFor lesson t1 = [d1]  docu d1 = [d1]
PROBE keys course=COURSE/Nature folder=COURSE/Nature opening course resolves to shelf item ids=[d1]
PROBE empty docs shelf entries=0 dept=null tabs=[Home, Movies, Documentaries, Continue, Watchlist, Collections]
```

## Defects

### H1. Search no longer finds any documentary, on the phone or the TV (regression)
- `feature/catalog/src/main/kotlin/SearchGroups.kt:46-48`, plus `SearchFilter` at :9.
- Films are filtered on `MOVIE`, episodes on `EPISODE` and lessons on `TUTORIAL|DOCUMENT`. A `DOCUMENTARY` hit is resolved by `searchRowsOf` and then matches none of the three buckets, so it is dropped.
- Scenario: search "Baraka". The probe returns `filters=[]` and every group empty, so `SearchScreen.kt:154-160` shows "No title, person, folder or summary in the library mentions that." Before this change the same search found it as a film. TV search goes through the same `searchGroupsOf`, so it has the same gap.
- On the web, `search-view.js:81,87,108` has its own Documentaries group. Its rows play on tap.
- Fix:
  - Add `val documentaries: List<SearchRow>` (`kind == DOCUMENTARY`).
  - Add `SearchFilter.DOCUMENTARIES` between SERIES and TUTORIALS, in the web's order, and include it in `counts` and `total`.
  - Draw it as a play-on-tap row section in `SearchGroupsView.kt` and `TvSearchGroups.kt`. `TvSearchGroups`' exhaustive `when` will point at the spot.
  - Add one `SearchGroupsTest` case.
- The implementer's report calls this out of scope. It is a regression caused by moving `docu` off `MOVIE`, so it should land in the same release.

### H2. An empty Documentaries department shows a blank page (phone) or a blank wall (TV)
- Phone: `ui-mobile/.../ui/catalog/CatalogScreen.kt:244-252`. `department?.let { ... }` has no else branch, and `documentariesDepartmentOf` returns null when there are no items (confirmed by the probe: `dept=null`).
- TV: `TvDepartmentPages.kt:93` falls through to `TvShelfWall` with no entries. `TvWall` draws nothing and has no message.
- How often this is hit: the shelf is always present now, so every library without documentaries hits it. So does almost every kids profile. A `docu` set never gets an FSK (`crates/mediagram-tmdb/src/certification.rs:30` bails for `Docu`), so `kidsVerdict()` is UNRATED and the set is hidden unless someone marked it by hand. A kids profile therefore sees a "Documentaries 0" pill that opens an empty screen.
- The spec says: "Empty library → the same empty state other departments use." On the web, `shelf-view.js:100-107` shows "No documentaries yet. Upload one with `mediagram add-docu <file|folder>`.", or "Nothing rated FSK 12 or under yet." for kids.
- Fix: in the phone branch use `department?.let { ... } ?: CenteredMessage("No documentaries yet. Upload one with mediagram add-docu <file|folder>.")`, and draw the same line on TV when `shelf.entries.isEmpty()`. `CatalogUiState.Ready` has no kids flag, so the kids wording would need one passed down. The neutral line is enough to ship.

### M1. A documentary folder and a course with the same name share one collection key
- `feature/catalog/src/main/kotlin/Documentaries.kt:22` builds folders through `collections(..., CollectionKind.COURSE, ...)`, so they get the key `COURSE/<name>` (`Shelves.kt:99`).
- `CatalogUiState.collection(key)` (`CatalogUiState.kt:63-69`) returns the first match across shelves, and Documentaries sits before Tutorials.
- Scenario, confirmed by the probe: a course "Nature" and a documentary folder "Nature". Tapping the course on the Tutorials tab opens the documentary folder. A saved COLLECTION frame restores to the wrong page as well.
- The web avoids this because it addresses the two by section (`app.js:641`).
- Fix, one line in `groupDocumentaries`: `collections(...).map { it.copy(key = "DOCUMENTARY/${it.name}") }`. Nothing parses the key prefix; I grepped and found only one androidTest literal. Then add the collision case to `DocumentariesTest`.

### M2. `runFor` can hand a lesson the run of a same-named documentary folder, so the lesson loses Up next
- `feature/catalog/src/main/kotlin/RunFor.kt:19-26` finds the first `Entry.Collection` on any shelf whose name equals `set.show`. Shelves are now ordered Movies, Series, Documentaries, Tutorials.
- The web's `plays-next.js:29` searches series, then tutorials, then documentaries.
- Scenario, confirmed by the probe: lesson t1 in course "Nature" gets the run `[d1]`. `nextInQueue` (`UpNext.kt:54-57`) then returns null, so lesson 1 no longer autoplays lesson 2.
- Fix the root cause: pick the collection that actually contains the set, e.g. `.find { it.name == show && playOrder(it.divisions).any { s -> s.setId == set.setId } }`. This also fixes the older series/course name collision.

## Parity and design divergences (low)

- **L1. Tapping the hero plays a documentary.** `DocumentariesDepartmentScreen.kt:71-77` passes `onOpenTitle = onPlay`, and `DepartmentHero.kt:59` makes the art clickable, so tapping the hero art plays the newest documentary that has a backdrop. The web passes `leadHref: null`: its hero is not a link. Fix: give the Documentaries hero a no-op tap.
- **L2. "All N" is a trailing card instead of a heading link.** `DocumentariesDepartmentScreen.kt:124-135` draws "All N" as a poster card. The web puts it on the row heading (`deptRow` `more`), and `DeptRowHeading(onSeeAll, seeAllLabel)` already supports that. Fix: `DeptRowHeading(name, onSeeAll = { open(key) }.takeIf { hasMore }, seeAllLabel = "All $count")`, then delete the card.
- **L3. Continue row cap.** The Continue row takes 12 (`DEPARTMENT_ROW`) before filtering to documentaries. The web takes 6 (`SHELF_LIMIT`). This matches the existing Android shows department, so it is informational only.
- **L4. TV Documentaries wall.** On TV, a standalone documentary opens its title page (the web plays it), and folder plates read "N chapters" through `extentOf` (the web reads "N documentaries"). A TV page is out of this phase's scope, so this is acceptable as long as it is written down.
- **L5. Blank show names.** `groupDocumentaries` treats a whitespace-only `show` as a single. The web treats any truthy `show` as a folder. Negligible.

## Style, voice, size (low)

- **L6. Comments.**
  - `TvDepartmentPages.kt:87-92` refers to the plan ("a later plan's to build") and sits inside the Movies branch rather than beside the `else if` it explains.
  - `Shelves.kt:23-26` says "not something this change is asked to undo". That describes this change, not why the code is shaped this way.
  - The KDoc on `ShelvesTest.aDocumentaryNeverJoinsASeries...` retells a debugging session (48 vs 46, stale server).
  - `LibraryFlowBranches.kt` says "(Documentaries did, once already)", which is history.
  - The `DOCUMENTARIES` KDoc (`Shelves.kt:9`) says `catalogTabsOf` reads it; it does not. It also mentions "this screen's own routing" in a file that is not a screen.
- **L7. Dead code and a weak test.** `countDocumentaries` is only used by tests. Production counts through `documentaryCountOf`, so delete one of the two. `DocumentariesTest.theOnlyPlayableWayIntoADocumentaryFolderIsBySetIdNotByAKindCheck` only asserts that three ids are distinct, which is less than its name claims.
- **L8. File size.** `HomeShelves.kt` grew from 195 to 209 lines, crossing 200. `CatalogScreen.kt` grew from 256 to 279. Move the inline DOCUMENTARIES branch into a private `DocumentariesDepartment(shelf, state, ...)` in `DocumentariesDepartmentScreen.kt`, the way `ShowsDepartment` is split out.
- **L9. TV androidTest.** `TvCatalogScreenTest.rightAlongTheMastheadReachesTheViewersName` presses Right a fixed number of times (`repeat(8)`). The always-present tab adds one step. It was not run (no device) and may already be out of date since the masthead split. Run it on the TV emulator before release. Masthead width is fine: 6 labels, 49 characters, against the 7-tab, 53-character budget the tab padding was sized for.
- **L10. Changelog.**
  - It says "Movies, Recently Added and editorial picks now read only the Movies shelf". `magazineHomeOf` already did that; what changed is `filmsOf` (Similar and franchise).
  - It says "a tab chosen before this update is restored by its name". Saved state does not survive an app update. The real gain is within a session: profile switches, and rail taps made while the library is still loading.
  - It does not mention that a documentary in a folder now plays on through the folder (`runFor`).

## Merge note

The preload worktree's `LibraryFlowBranches.kt:195` shows `rememberFilmPreloadUi` only when `title.kind == Kind.MOVIE`. After both branches merge, documentary title pages lose the preload control they had while filed as MOVIE. Decide on purpose whether that is right. Both branches also edit `LibraryFlowBranches.kt`.

## Checked and correct

- **Counts:** Movies excludes docu. Documentaries counts folder counts plus singles, the same as the web's `countDocumentaries`. Series and Tutorials are unchanged. Tally stays films/shows/courses (`LibraryTally.kt:34`).
- **Grouping:** folders group by show through the course grouping, singles are sorted by NATURAL title, and tab order is Home, Movies, Series, Documentaries, Tutorials, Collections.
- **Department page:** rows, their order, the 12-item limit, "All N" when a folder holds more than 12, play on tap, and the hero lead (newest with a backdrop) all match `renderDocumentariesDept`.
- **Home page:** the cover, features, Recently added and This month read only the Movies shelf, the same as the web's `library.movies`. `homeRowsOf` skips Latest documentaries, matching the web's `homeShelves`. Next-up excludes documentary folders (the web's underway loop covers series and tutorials only). Continue includes started documentaries, the same as the web's `continues`.
- **Kids filter:** `forKidsProfile` does not look at kind; documentaries go through the same FSK rule as films.
- **Watch state:** keyed by set id, so moving a set to a new kind orphans nothing.
- **Empty-library checks:** `hasContent()` replaces the old check at all 5 sites, and no `shelves.isEmpty()` checks remain.
- **Tab maths:** `firstKept`, `catalogTabsOf`, `mastheadSplitOf` and TV's `shelfCount` are all generic over the shelf list.
- **Player:** preload is still episodes only, as on the web. `runFor` now gives a documentary in a folder a run, matching `plays-next.js`. Resume cards name a documentary by its folder, matching the web's `resumeCard`.
- **Saved tab:** restoring by title survives rotation and process death. While the library loads the screen shows Home, and the saved title is not overwritten.

**Status:** DONE_WITH_CONCERNS
