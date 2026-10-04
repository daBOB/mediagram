# B1f — editorial parity, last gaps

2026-10-04, worktree branch `worktree-agent-a75e4e2a9e5233759` (reset to `main` at `e7b9d80e`,
0.105.1 — the branch had been cut at 0.99.9). No version bump (lead bumps on merge). Untouched:
the shared test fixtures, profile/PIN code, `docs/project-changelog.md`.

## Commits

| Commit | What |
|---|---|
| `5d9fa44d` | fix: portrait tablet (Medium) department hero overlaps its art (gap 3) |
| `1ba9300d` | feat: phone list page opens with the shelf head (gap 4) |
| `142d6e55` | feat: TV search people as round person cards (gap 5) |
| `8fae2d2d` | feat: TV Cast crew names link to the person page (gap 1) |
| `f0cacb6c` | feat: TV course page = shelf head over its lessons, no tabs (gap 2) |
| `3faa8e9a` | docs(design): shelf head on list/course pages, person cards in TV search |
| `0ddfa083` | docs: course page comment wording |

## Done, by gap

1. **TV crew links** (`TvCastRow.kt`). The crew line is a `FlowRow`: "Directed by"/"Created by",
   then each name a `TvTextRow` (`key(personId)`, comma riding with the name before it) opening
   `onOpenPerson`. Restore: a crew-only person's id lands on their name (a director who also acts
   lands on their plate); `TitleCredits.onCastTab(key)` (cast or crew) now picks the Cast tab in
   `TvTitlePage` and `TvSeriesPage`. Routing: Up from a plate → crew line → tab row, whose existing
   `onEnter` lands on the Cast tab; Down from the tab → names → plates. No new `onExit`, no
   `focusRestorer`.
2. **TV course page** (`TvCollection.kt` → private `TvCoursePage`). Web `course-view.js`: the
   page's `TvShelfHead(name, courseExtentOf(divisions))` — "two lessons · one document",
   `extentOf`'s words, documentaries for a documentary collection (new shared
   `courseExtentOf`, feature/catalog) — over the existing indented lesson rows. Tab row
   (Episodes/About/Cast/Similar), art/overview/genre header removed: the web has none and a
   course has no provider entry (About stood empty, Cast never appeared).
   - **Kept, written down** (`docs/system-architecture.md` § Television differs): the resume line
     ("▶ Continue <lesson>") under the head — a remote walks a 162-lesson course a row at a time.
   - Dead code removed: `CollectionHeader`, `TvTitleHeader`, `TvTabBody`, `TvSeriesAbout`.
     Files renamed to what remains: `TvSeriesResume.kt` (resume row + `resumeLabel`),
     `TvReadableParagraph.kt` (still used by franchise and genre pages).
   - Rows unchanged ("1. ✓ Title" + marks): the phone draws the same rows, and the series page
     shares them.
3. **Medium hero** (`DepartmentHeroLayouts.kt`, `DepartmentHero.kt`): `overlap` parameter gone;
   every compact layout (phone and portrait tablet) sets its words 48vw down over the 64vw strip,
   the web's ≤900px rule. Expanded untouched.
4. **Phone list heading** (`ListScreen.kt`): `ShelfHead(list.name, spelledCountOf(list.items.size,
   "title"))` (counts what the list names, as web/TV). Head, controls and titles are now one
   `LazyColumn`, so the head scrolls away with a long list instead of holding the phone's screen.
5. **TV search people** (`TvSearchGroups.kt`, `TvSearchRows.kt`, `TvSearchCell.kt`): new
   `SearchLayout.PEOPLE` (six to a line, the posters' `Columns`); `TvPersonCard` replaces
   `TvPersonSearchRow` — round portrait (cell-wide), name, spelled count, centred; one merged
   clickable node. Focus = accent ring + 1.08 scale drawn on the circle by hand (a tv-material
   `Card` clips to its shape, and the name sits under the circle); both modifiers always present,
   only values follow focus.

Docs: DESIGN.md Shelf head (phone list page, TV course page, TV person cards);
`docs/system-architecture.md` § Television differs (course resume line).

## Tests

`./gradlew -q --continue :ui-tv:testDebugUnitTest :ui-mobile:testDebugUnitTest :ui-common:testDebugUnitTest :feature:catalog:testDebugUnitTest :ui-tv:compileDebugAndroidTestKotlin lint`
— exit 0: **ui-tv 481, ui-mobile 279, ui-common 73, feature:catalog 342, 0 failures**; androidTest
compiles; lint: no finding in a touched file beyond the pre-existing `DepartmentHero`
screenWidthDp / `TvDepartmentHero` ModifierParameter; app baseline's "2 entries not found"
pre-existing.

Merge check: trial no-commit merge of `main` at `73010cff` (0.105.2, watch-state test migration)
is clean (`TvSearchAndGenreTest` separate hunks); ui-tv 481 / ui-mobile 279 / feature:catalog 341
green on it; aborted.

New Robolectric (new files, no shared fixture edited):
- `TvCastCrewLinksStateTest`: each director opens their page; a show's "Created by"; D-pad
  (touch mode off, native text) plate ↑ name → Right next name ↑ Cast tab ↓ name ↓ plate; back
  from a crew-only director lands on Cast with their name focused; director in the cast → plate.
- `TvCoursePageStateTest`: shelf head heading + "TWO LESSONS · ONE DOCUMENT" above the lessons;
  no Episodes/About/Cast/Similar even when handed credits/similar; back from a lesson lands on
  it; D-pad first lesson ↑ resume line ↓ lesson, resume plays its lesson.
- `TvSearchPeopleCardsStateTest`: People in `PEOPLE` layout, 7 → lines of 6 + 1; two cards side by
  side, < 200dp, name and count under the portrait; D-pad poster ↓ person → Right → OK opens → ↑
  poster.
- `CourseExtentTest` (feature/catalog): depth, documents apart and only when present, "zero
  lessons · two documents", "22 documentaries".
- `ListScreenTest` (phone): heading + "TWO TITLES", ≥34dp title, head above controls above titles,
  empty list keeps its head.
- `DepartmentHeroTest`: Medium (777dp) title at 48vw, art bottom at 64vw (replaces the "stays
  under" case). `TvSearchAndGenreTest`: wording only (row → card).

## Box only — not verifiable here

1. **Person card focus** on real fonts/art: ring + scale on a ~130dp circle, the name under it not
   clipped by the scaled ring; Down from a full-width row above (Lessons) lands on the nearest card
   by centre — geometry, as for rows → Collections cards.
2. **Crew line** walk on the box: film and show Cast tabs, Up plate → name → Cast tab; Back from a
   director's page lands on their name.
3. **Course page**: head (42sp) comes back into view on Up to the first lesson under the leanback
   pivot (Robolectric's default spec scrolls minimally); a real multi-level course.
4. Phone/tablet: Medium hero overlap on the Redmi Pad in portrait; phone list page head.

## Concerns / seen, not touched

- **Phone course page** still opens with a headline name + `TitleHeader` (art/overview/genres) and
  no extent — the web (and now TV) use the shelf head. Phone is now the surface behind on courses.
- **TV Cast plates** are 2:3 posters; web and phone draw round person cards in Cast. Not in brief.
- TV crew names are plain at rest (the `TvTextRow`/genre-link convention); the web underlines.
- `TvCollectionFrame` still asks for credits/similar on a course; now unused there (harmless).

## Unresolved questions

- Phone course page to the web's shelf head (drop `TitleHeader`)? Same reasoning as TV.
- TV Cast as round person cards (reusing `TvPersonCard`)?
