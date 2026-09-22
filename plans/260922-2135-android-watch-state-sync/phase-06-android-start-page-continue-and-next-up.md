# Phase 06 — Start page: Continue, Next up, card marks

## Context links

- `web/public/lib/home-shelves.js:31-130` (model; `nextInCollection` 96-130; totals 66-72)
- `web/public/lib/home-view.js:46-107` (row order, titles, See-all targets: Continue → `#/continue` :57, Next up → `#/series` :69, caption :76)
- `web/public/lib/shelf-view.js:137-170` (set plate: meta `show · episode`, caption `resumeLine`, progress rule, watched tick), `web/public/lib/format.js:124-133` (`resumeLine`)
- `web/public/lib/library.js:182-243` (`levelEntries`, `flattenCollection`, `nextAfter`)
- `android/feature/catalog/src/main/kotlin/HomeShelves.kt:16-73`, `feature/catalog/src/test/kotlin/HomeShelvesTest.kt`
- `android/ui-mobile/src/main/kotlin/HomeScreen.kt:39-121`, `CatalogScreen.kt:77-117`, `PosterCard.kt:45-57`
- `android/feature/catalog/src/main/kotlin/Shelves.kt:113-134`, `ui-mobile/src/main/kotlin/CollectionScreen.kt:126-127` (Android renders a level's items, then its folders)
- Phase 01 `next-up.json`

## Overview

- Priority: P1. Status: pending. Blocked by 05.
- The start page gains the web's first two rows, Continue and Next up, above the three Latest rows; every set card gains the progress rule and the watched tick.

## Key insights

- Port `homeShelves` rather than re-deriving it (Surface Parity). Row names verbatim: "Continue", "Next up". Rows absent when empty, as now (`HomeShelves.kt:22-26`, `home-view.js:56,60`).
- Next up is one card per show **or course** underway; See all goes to **Series** (the web's choice, `home-view.js:69`), even for a course card. Match it; do not "fix" it here.
- Continue excludes sets already on Next up, but its **total counts every started title** (`home-shelves.js:63-67`) so the figure equals what See all opens.
- **Play order** must be the order the page renders (`library.js:198-207`). Android renders items then folders (`CollectionScreen.kt:126-127`); the web interleaves by leading number (`library.js:182-196`). For shows (seasons only) both agree; for courses mixing lessons and folders they can differ. `playOrder()` follows Android's own rendering; the course divergence is recorded as an existing parity gap (open question), not widened.
- Documents never play and are skipped (`library.js:213-215`).
- `HomeRow` holds `Entry`; Continue/Next up hold sets with captions. Add `Entry`-independent row content rather than bending `Entry.Film` (an episode is not a film).

## Requirements

- Functional:
  - `homeRowsOf(shelves, snapshot)` → Continue (≤6), Next up (≤6), then the existing Latest rows; totals as the web.
  - Next up caption: `resumeLine(progress)` when the card is a resume, else "Next up". Continue caption: `resumeLine`.
  - Set plate meta under a poster: `show · S1E4` (`episodeLabel`); poster from the set, or the show's.
  - Progress rule = `watchedFraction`; watched tick when watched; on every set card and collection row. Season-level tick when every item watched (`shelf-view.js:103`).
  - See all: Continue → Continue tab (07 adds it; until then hidden), Next up → Series tab.
- Non-functional: rows computed once per (shelves, snapshot) change via `remember`/VM, not per recomposition. Files < 200 lines.

## Architecture

```
CatalogViewModel: combine(catalog shelves, repository.snapshot) → Ready(shelves, watch)
HomeShelves.kt  : homeRowsOf(shelves, watch) → [Continue?, NextUp?, Latest…]
NextUp.kt       : playOrder(collection), nextInCollection(order, positions, watchedAt)  (port)
ResumeLine.kt   : resumeLine(progress), episodeLabel(set)  (format.js port)
HomeScreen      : RowHeading unchanged; SetPlate for set rows; PosterCard for entries
PosterCard      : + progress: Float?, watched: Boolean
```

## Related code files

- Create: `feature/catalog/src/main/kotlin/NextUp.kt`, `feature/catalog/src/main/kotlin/ResumeLine.kt`, `feature/catalog/src/test/kotlin/NextUpFixtureTest.kt` (reads `next-up.json`), `feature/catalog/src/test/kotlin/PlayOrderTest.kt`, `ui-mobile/src/main/kotlin/SetPlate.kt` (set card: meta + caption + marks, over `PosterCard`).
- Modify: `feature/catalog/src/main/kotlin/HomeShelves.kt` (new rows, doc comment no longer says "no watch state"), `HomeShelvesTest.kt`, `CatalogUiState.kt` (`Ready.watch`), `CatalogViewModel.kt` (combine with snapshot), `ui-mobile/src/main/kotlin/HomeScreen.kt` (set rows, KDoc), `CatalogScreen.kt` (See all mapping), `PosterCard.kt` (rule + tick), `CollectionScreen.kt` (tick on rows), `SeasonWall.kt` (season tick), `feature/catalog/build.gradle.kts` (test JSON dep).
- Delete: none.

## Implementation steps

1. `playOrder` + tests (show: seasons in order; course: rendered order; documents skipped).
2. `NextUp.kt` port of `nextInCollection`; fixture test over `next-up.json` passes unmodified.
3. `homeRowsOf` extension + tests: exclusion, totals, limits, empty rows absent, ordering by `touchedAt`.
4. `CatalogViewModel` combine; `Ready` carries the snapshot (no second source for screens).
5. UI: `SetPlate`, marks on `PosterCard`, `CollectionScreen` and `SeasonWall` ticks, See all wiring.
6. `./gradlew testDebugUnitTest lint`; `:app:installDebug`.

## Todo

- [ ] playOrder + tests
- [ ] NextUp port + fixture test
- [ ] homeRowsOf rows + totals + tests
- [ ] VM combine
- [ ] SetPlate, rule, tick, See all
- [ ] tablet walk-through below

## Success criteria (tablet, with a web player on the same channel)

- An episode half-watched on the web appears under Continue on the tablet after the next round; the show's next episode appears under Next up once an episode is finished; a finished show leaves Next up.
- The same title never appears twice on the start page.
- Row totals equal what See all opens.

## Risks

| Risk | L×I | Mitigation |
|---|---|---|
| Course order differs from web | M×M | Documented gap; fixture uses flat order so the rule itself is pinned |
| Recomposition cost of combining hundreds of sets | L×M | Computed in the VM on change only |
| Snapshot and catalog out of step (ids no longer in library) | M×L | Drop unknown ids, as `setsFor` does (`app.js:70`) |

## Security

Display only.

## Next steps

07 adds the kept shelves and the controls that fill them.
