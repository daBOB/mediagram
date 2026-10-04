# B1a — phone/tablet browse parity (group A)

2026-10-04, worktree branch `worktree-agent-a8f2b46990222d9cd` (off `main` at `bbdced06`).
No version bump (lead bumps on merge).

## Done, by gap-report number

| # | What | Where |
|---|---|---|
| 2 | Latest: parts headed "Movies / Series / Tutorials" (web `SECTIONS.*.label`); courses as the home's `CourseList`, not plates | `LatestScreen.kt` |
| 3 | One `ArtTile` (art, bottom scrim, Fraunces name over the art, meta, aspect; `destination` = `.destination` caps/size) + `TileFlow` (CSS `auto-fill minmax()` inside a lazy list) | new `ArtTile.kt` |
| 3 | Genres index: 16:9 tiles, `GridCells.Adaptive(208dp)`, 14dp gap | `GenresIndexScreen.kt` |
| 3 | Movies genre row: 16:8 tiles, 192dp (12rem floor) | `MoviesDepartmentScreen.kt` |
| 3 | Collections: franchises and lists as 4:3 wrapping cards (min 256dp); a list pictured by its first pictured title (`listArtOf`); "＋ New list" a round pill | `CollectionsScreen.kt`, `ListsScreen.kt`, `Franchises.kt` |
| 3 | Search Collections: same cards; a franchise says "N films", a list "N titles" (`SearchDestination.franchiseId`) | `SearchGroupsView.kt`, `SearchGroups.kt` |
| 4 | Movies: "All N films →" pill at the foot (`.dept-all`); the Featured row's own link now reads "All N films" (no arrow), as the web's row head | `MoviesDepartmentScreen.kt`, new `PagePill.kt` |
| 4 | Tutorials "All courses" as a list (`CourseList`); Series stays plates | `ShowsDepartmentScreen.kt` |
| 4 | Franchise: overview inside the hero (`.franchise-overview`, wide and compact); "In release order" via `DeptRowHeading` | `FranchiseScreen.kt`, `DepartmentHero*.kt`, `DeptHeroText.kt` |
| 5 | Spelled counts via the existing `catalog.spelledCountOf` on every screen above, and Search's people captions | — |
| 5 | Person: "N in your library" (figures, as `cast.js`'s template string) | `PersonScreen.kt` |
| 5 | Page head: new `ShelfHead` (`.shelf-head`: Fraunces SemiBold 35–58sp, caps sub at 0.24em flush right, 1dp rule) + `shelfSub` (`.shelf-sub` 24sp) on Latest, Genres, Genre, Person | new `ShelfHead.kt` |
| 9 | Robolectric: Person, Franchise, Genres index, Latest, Collections, Movies dept, Search collections, Tutorials all-courses | `ui-mobile/src/test/.../catalog/*Test.kt`, shared `BrowsePageTest` |

`PageHead` (designsystem) was not reused for (5): it is the `.dept-title` opener (huge uppercase,
autosized from 112sp); the web gives these pages the plainer `.shelf-head`. The home's feature
card was not reused for (3): it carries an eyebrow, rule and deck a tile has none of.

DESIGN.md: Shapes names art tiles under `{rounded.card}`; Components gains Shelf head, Art tile,
Page pill.

## Deliberate differences kept (each written beside its code)

- `MoviesDepartmentScreen.GenreRow`: tiles keep the 12rem floor; the web's `1fr` stretches a
  short row on a wide window. Shows only on a tablet with fewer genres than fit.
- `ListsScreen.listsSection`: "No lists yet." under Your lists when there are none; the web
  leaves an empty grid above its button, which on a phone reads as a failed load (existing
  behaviour, now written down).
- `ArtTile`: no hover zoom (no touch counterpart).
- `LatestScreen` / `ShowsDepartmentScreen`: courses drawn as one `CourseList` item, not one grid
  item per course, so the grid gap does not open between list rows.

## Tests

`./gradlew -q :ui-mobile:testDebugUnitTest :ui-common:testDebugUnitTest :feature:catalog:testDebugUnitTest lint`
— green: ui-mobile 260, ui-common 73, feature:catalog 334 tests, 0 failures; lint clean
(baseline only). `:ui-tv:testDebugUnitTest` also run (feature:catalog changed): 436, 0 failures.

New: 23 Robolectric cases (22 in seven new classes, one added to `ShowsDepartmentScreenTest`)
+ 2 feature:catalog cases (`listArtOf`,
`SearchDestination.franchiseId`). Updated expectations: `LibraryRailTest`,
`OverflowUtilitiesTest` (sub now reads "NEWEST ARRIVALS FIRST", the web's caps set in the
string) and `LibraryFlowTest` (list card name "FAVOURITES", the destination's caps).

## Concerns

- Search's people caption ("one title") changed to spelled but has no screen test: that
  section calls `hiltViewModel()`, which the plain-activity harness cannot host. The wording
  comes from `spelledCountOf`, itself tested.
- The name dialog behind "＋ New list" never lets Compose idle under Robolectric (its text
  field), so the pill is tested on `listsSection` alone; the dialog wiring is unchanged.
- Seen, not touched (outside this group): `CompactDeptHero` lays its words *below* a 64vw art
  strip plus 48vw padding, where the web overlaps the art (`padding-top:48vw` over an absolutely
  placed 64vw strip). Franchise/department grids have no side padding, so plates touch the
  screen edge.
