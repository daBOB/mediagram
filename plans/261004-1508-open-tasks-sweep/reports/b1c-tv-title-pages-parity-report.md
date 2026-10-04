# B1 group C — TV title and series pages, parity with the web

2026-10-04, worktree subagent, branched from `main` at `f2268a00` (0.102.1). Gap report item 11
(`b1-editorial-parity-gap-report.md`, phase-6 row "TV title + series pages"). No version bump
(lead bumps on merge). No device touched: everything below the line "box only" is unverified on
the TV box.

## What changed (TV, `android/ui-tv`)

| Gap (phase-6 row) | TV now | Web reference |
|---|---|---|
| no spread/backdrop | `TvTitleSpread`: art from 28% across (full width in Artwork, none in Solid), the spread's three gradients, title (59.5sp, 44sp past 22 chars), facts, 4-line overview, pills bottom-left; tagline as pull-quote bottom-right at 22% height. Floor 380dp, grows with its words; no-art spread shrinks to them | `title-spread.js`, `title-page.css .spread` |
| no My List pill | `▶ Play` / `▶ Resume from 12:30` (solid), `+ My List` / `✓ On My List`, `⋯` (line pills, `TvTitlePills.kt`). Series: `▶ Resume S1 E3` (resume pick), My List + ⋯ for the first episode | `film-page.js`, `series-page.js`, `list-toggle.js`, `moreMenu` |
| tabs above a poster header | spread first, tabs under it, panel under the tabs; one scroll for the whole page (film: scrolling Column; show: one LazyColumn) | `tabbed()` |
| Details one text line | Details = the web's fact sheet (Quality, Video, Audio, Audio languages, Subtitles, Container, Size, Bitrate, Parts); Overview = poster + fact sheet (Released, Runtime, Rated, Score, Genres links, Part of link) | `film-page.js#details`, `#overview` |
| About only 3 facts | About = Aired, Held ("1 of 73 episodes · 1 of 8 seasons · …"), From, Genres (links), Picture, Audio, Subtitles — from `TitleInfo`'s air dates/totals | `series-page.js#fillAbout`, `series-summary.js` |
| Episodes a wall of season posters | season picker (row of pills, "Season 2 · eight episodes") over that season's episode rows; no picker for one season; season kept in `LibraryPositions.collectionSeason` like the phone | `series-page.js#episodes` |
| Similar hidden when empty | Similar always a tab; empty says "Nothing else in the library shares its genres." | `similarShelf`/`similarShows` |
| Up lands on nearest tab | Up from any panel (and Down from the pills) enters the *selected* tab: `onEnter` on the tab row's focus group redirects directional arrivals | — |

Editor's choice moved from the Details tab into the ⋯ (web `moreMenu`, phone ⋯). The
series page also gained it (was film-only on TV). Back is unchanged: the page registers no
Back handler except while the ⋯ is open (then Back closes it onto ⋯); tested.

## Shared builders (both surfaces)

- `feature/catalog/FactSheetRows.kt`: `FactValue` (Words / Genres / PartOf), `filmOverviewFacts`,
  `filmDetailFacts`, `seriesAboutFacts`. Phone `TitleDetailScreen`/`CollectionScreen` map them
  through a new `factRows()` in `FactSheet.kt`; phone output identical (its tests unchanged, green).
- `hdrLabel`/`bitrateLabel` moved from `feature/player/TechnicalLine.kt` to
  `core/model/FileLabels.kt`: the Details builder needs them and `feature/catalog` does not (and
  should not) depend on `feature/player`. `TechnicalLine.kt` and its test import them.
- `feature/player/ListLabel.kt`: `listLabel(watchlisted: Boolean)`; the marks overload delegates.
- Removed `feature/catalog/SeasonWall.kt` (+ test): the TV wall was its last user.

## Deliberate TV differences — written under `docs/system-architecture.md` § Television differs

Spread floor 380dp (not the web clamp); tabs switch on OK and the tab row rises to the top of
the screen when reached (fact panels have no stop to scroll by); season picker as pills not a
drop-down; ⋯ choices inline in the pill row. TV Episodes stays the web's picker + list (ruling 2).

## Tests

`./gradlew -q :ui-tv:testDebugUnitTest :ui-mobile:testDebugUnitTest :feature:catalog:testDebugUnitTest
:feature:player:testDebugUnitTest :core:model:test :ui-tv:compileDebugAndroidTestKotlin lint` — green:
ui-tv 450, ui-mobile 272, feature:catalog 338, feature:player 252, all passing; androidTest
compiles; lint passes (only note on touched files: `ModifierNodeInspectableProperties` on the new
`RevealsPageBelow`, the same info-level note the existing `RevealsFromTop` beside it carries).

- New: `FactSheetRowsTest` (overview/details/about rows, order, omissions).
- `TvTitlePageStateTest` (rewritten): facts line with three genres, spread overview + quote,
  no quote without art, Down from Play enters the *selected* tab, Up from a panel enters its
  own tab not the nearest, reaching the tabs lifts them to the safe line, "Resume from 12:30".
- `TvTitlePageTabsStateTest`: Similar always + empty sentence, Overview fact sheet with genre and
  franchise links, Details fact sheet, My List label/toggle, ⋯ editor's choice (focus in and back
  out), Back closes ⋯ and otherwise the page takes no Back, kids profile has no ⋯, restore to
  genre link / Cast person (incl. late credits) / Similar film.
- `TvCollectionStateTest`/`TvCollectionTabsStateTest`: picker words and selection (pressed pill
  keeps focus, no repeated season heading), no picker for one season, D-pad walk pills → tab →
  picker → episode and back up to the tab, restore to an episode incl. one in an unpicked season,
  About fact sheet with air dates/"1 of 73 episodes", genre restore into About, Up from Cast
  enters Cast, series My List and ⋯, resume pill takes the remote.
- `TvLibraryTest`/`TvSearchAndGenreTest` walks updated for picker + About; Back paths unchanged
  (episode → show → home plate; genre wall → link → plate).
- androidTest `TvPageScrollTest` rewritten for the spread/tabs walk (compiles; not run).

## Box only — not verifiable here

1. **Tab-row rise under the leanback pivot spec.** `revealsPageBelow` asks for a rect one screen
   minus the bottom inset tall; worked out to land the row on the safe line under both the default
   and the pivot `BringIntoViewSpec`, and pinned in Robolectric (default spec only). The
   androidTest walk (`TvPageScrollTest`) was rewritten but not run.
2. **Details fits under the tabs.** Rows are 8dp-padded; at the box's real Geist metrics nine
   rows should take ~360 of the ~420dp under the tabs. Robolectric sets text ~2x tall, so it can
   only pin where the row lands.
3. **Spread at real font sizes**: a two-line title plus a 4-line overview may push the tab row
   just below the first screen on long titles; look at the gradients/quote vs pills on real art.
4. **Up/Down routing** (`onEnter` on the tab row's group) rests on Compose's focus search; the
   group sits *after* `wrapContentWidth` because a full-width group's centre loses the weighted
   distance to a pill above (found in Robolectric). Walk film + show: pills ↓ tabs ↓ panel ↑.

## For the lead

- My List reads "+ My List" / "✓ On My List" (the shared `listLabel`, as asked) with the web's
  `+`/`✓` mark. The web's title-page pill and the phone's say "✓ My List" when listed — the
  shared label is the players' wording. Pick one if they should match.
- Not done (not in this group): TV crew names in Cast are still plain text (web links them);
  a course on TV keeps its old header + tabs layout (the web's course view is a different page).
