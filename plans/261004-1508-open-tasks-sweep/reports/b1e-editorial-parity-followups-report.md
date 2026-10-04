# B1e — editorial parity follow-ups (five gaps)

2026-10-04, worktree branch `worktree-agent-a085839b7416f121b` (fast-forwarded to `main` at
`53f07216`, 0.103.0 — the branch had been cut at 0.99.9). No version bump (lead bumps on merge).
Untouched: the TV title/series files another worker owns, profile/PIN code,
`docs/project-changelog.md`.

## Commits

| Commit | What |
|---|---|
| `c669d86a` | refactor: remove the unreachable TV list-of-lists screen (gap 4) |
| `16df7280` | feat: TV search posters and cards; Movies' "All N films →" pill; a list's shelf head (gaps 1–3) |
| `a8a6dc11` | fix: phone department hero overlaps its art; hero grids keep the side gutter (gap 5) |

## Done, by gap

1. **TV search** (`TvSearchGroups.kt`, `TvSearchResults.kt`, new `TvSearchCell.kt`,
   `TvSearchRows.kt`, `TvSearch.kt`, `TvLibraryFrames.kt`). Each section carries a layout
   (`SearchLayout`): films and matched shows (plain and anime) as `TvPlate` posters six to a line
   (the walls' `Columns`); Collections as `TvArtTile` 4:3 destination cards three to a line
   (`DestinationColumns`, now internal and shared with Collections; 864dp still fits three 16rem
   tracks); episodes, documentaries, lessons, people stay rows, as on the web. The list is one
   `LazyColumn` of lines (`searchLinesOf`), each card `key(keyOf(entry))`-ed with one always-present
   requester. No D-pad reason forbade it, so no difference is written down.
   - A film's poster now **opens the film's page** (web `openFilm`, phone `onOpenTitle`) instead
     of playing; Back lands on that poster. Episode/lesson rows still play.
   - **Down from the field or the chips enters at the first entry** (`onEnter` on the results'
     focus group, Down only, so `RowAsk` restores are never redirected). Geometry alone landed on
     whichever poster sat under the middle of the full-width field.
   - `TvShowSearchRow`/`TvDestinationSearchRow` deleted (unused).
2. **Movies "All N films →"** (`TvMoviesDepartmentPage.kt`): `TvPagePill` 56dp under the last row
   (web `.dept-all` `margin-top:56px`, phone `PagePill`), always-present requester.
3. **A list's TV page** (`TvList.kt`): `TvShelfHead(name, spelledCountOf(list.items.size, "title"))`
   — counts what the list names, the number its Collections card and the web (`list.items.length`)
   show, not only the titles this library still resolves.
4. **Old list-of-lists screen — confirmed unreachable, deleted.** Trace:
   `TvCatalogScreen.kt:119` `collectionsIndex = tabs.firstKept + 2` (= COLLECTIONS' kept slot);
   `TvCatalogBody` matches `selected == collectionsIndex → TvCollectionsPage` *before* its `else →
   TvKeptTab`; `selected` is clamped to `tabs.titles.lastIndex` (= that same index). `TvKeptTab`
   had one caller (`TvCatalogBody`), `TvLists` one caller (`TvKeptTab`'s COLLECTIONS branch); no
   `FrameKind` opens a list of lists (`LIST` is one list), no androidTest referenced it. Deleted
   `TvLists.kt` and `TvListsStateTest.kt`; `TvKeptTab` drops `onOpenList`/`onCreateList` and its
   COLLECTIONS branch is an `error()` naming the invariant.
5. **Phone** (`ui-mobile`):
   - (a) `CompactDeptHero` is a `Box`: on **Compact** width the words start 48vw down over the 64vw
     strip (web ≤900px). The old `Column` stacked them *under* the strip plus 48vw. **Medium keeps
     its old layout exactly** (`artHeight + 48vw`), per the brief. Expanded untouched.
   - (b) Series, Anime and Franchise grids: new `GutteredCells` (outer two cells 16dp wider) +
     `Modifier.gutteredCell(index, columns, gutter)` (pads them back in), so plates sit in the
     page's 16dp gutter — where `DeptRowHeading` and the strips already sit — every plate one width,
     hero still edge to edge. `contentPadding` would have inset the hero too.

Docs: DESIGN.md — Shelf head (TV also heads a list), Art tile (TV: Search's Collections part),
Page pill (TV draws both pills as `TvPagePill`). No `§ Television differs` entry: nothing here is a
deliberate TV difference.

## Tests

`./gradlew -q --continue :ui-tv:testDebugUnitTest :ui-mobile:testDebugUnitTest :ui-common:testDebugUnitTest :feature:catalog:testDebugUnitTest :ui-tv:compileDebugAndroidTestKotlin lint`
— exit 0: **ui-tv 455, ui-mobile 276, ui-common 73, feature:catalog 340, 0 failures**; androidTest
compiles; lint 0 new errors, no warning in a touched line (remaining: pre-existing
`TvDepartmentHero` ModifierParameter, `DepartmentHero` screenWidthDp; app baseline's "2 entries
not found" also pre-existing — nothing in it names a touched file).

Merge check: a trial no-commit merge of `main` at `2f962d25` (0.104.0 — the TV title pages
landed meanwhile) merges cleanly (one shared test file, separate hunks) and the four touched TV
test classes pass on it (42 tests); aborted afterwards.

New/changed Robolectric:
- `TvSearchResultsStateTest`: lines chunk by layout with flat indices; ask lands on a poster on
  line 2 and on a row after it; each kind opens what it stands for (poster → title, show →
  collection, row → play, card → destination); D-pad Right along posters, Down by column onto a
  short line, Down into cards, Right, Up back to the short line's last poster; layouts per
  section; cards in capitals with spelled counts.
- `TvSearchAndGenreTest` (app, 960×540): Down from field → first poster, Right, Up → field;
  poster opens the film page, Back lands on it; episode row under its show's poster plays, Back
  lands on the row; chips: field → chip → first poster → person row (below the fold, walked to).
- `TvListStateTest`: shelf head (heading node + "TWO TITLES" for a two-item list holding one
  resolvable title; old "Sunday · 2" gone); D-pad first title → Remove → title → Play all → title.
- `TvDepartmentPagesStateTest`: back from the films wall lands on the pill, Up → last row, Down →
  pill, press opens.
- `DepartmentHeroTest`: phone title 48vw down, above the strip's 64vw bottom; Medium (700dp)
  title under the strip.
- `FranchiseScreenTest`, `ShowsDepartmentScreenTest`: first plate at 16dp, last ends 16dp short of
  the edge, one plate width, hero 0–400dp.

## Concerns

- **Medium (portrait tablet) hero still stacks its words under the strip**, as instructed — but the
  web overlaps at every width ≤900px, which includes the tablet in portrait (~800dp). It is now an
  explicit Android-internal split (comment in `CompactDeptHero`), not a deliberate web difference;
  owed a user call.
- **Robolectric touch mode:** `TvScreenStateTest`'s plain `ComponentActivity` starts in touch mode,
  where a `clickable` `TvTextRow` takes no focus by search *or* `requestFocus` — so Up from a plate
  to "▶ Play all" never moved there. The new `TvListStateTest` D-pad case leaves touch mode
  (`setInTouchMode(false)`, the androidTest `LeavesTouchModeRule`'s own call) and uses native text
  measurement. Any future state test walking onto a text row needs the same.
- **Search geometry is arithmetic on the box.** Up from a card or row into a poster line lands by
  nearest centre (a tie is possible between two posters equidistant from a card's centre; tests
  avoid that case). Needs the box walk: a query with films + shows + episodes + a collection,
  Down/Up through every section, Back from a film page and from a played episode.
- **Seen, not touched:** TV search's People are still rows (round portrait beside the name) where
  the web draws `personCard`s in a wrapping row — not in this brief and not written down as
  deliberate. The phone's `ListScreen` has no shelf head on the page (its name is only in the top
  bar), on this branch and on `main` at `2f962d25` alike — the brief's "and now the phone" does not
  hold, so the phone is the surface now behind the web there.

## Unresolved questions

- Medium hero: should the portrait tablet also overlap, as the web does ≤900px?
- TV search People: person cards like the web, or a written-down difference?
- Phone list page: give it the shelf head the web's `renderList` draws?
