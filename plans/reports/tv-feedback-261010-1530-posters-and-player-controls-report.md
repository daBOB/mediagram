# TV feedback, 2026-10-10 — posters and player controls: report

Brief: `tv-feedback-261010-1530-posters-and-player-controls-brief.md`. Branch `feat/tv-parity`.

## 1. Posters

One rule, `stripPlateWidth(rowWidth)` in `ui/tv/catalog/TvPageSpacing.kt`: `.grid.plates.strip`'s
`minmax(8.5rem, calc((100% - 7 * 14px) / 8))` (`web/public/styles/catalog.css:35`), px through `tvDp`:
`max(102dp, (rowWidth − 7 × 10.5dp) / 8)`. Each row measures its own width (`BoxWithConstraints`);
a row with no bound takes the floor.

| Row | Before | After (on the box) |
|---|---|---|
| Department rows (`DeptRow`, `DeptEntryRow`: Series › Popular, Movies, Documentaries) | 160dp | 102dp |
| Home › Latest series (`TvPosterStrip`) | 160dp | 102dp |
| Home › Recently added (`TvRecentBand`, the 2.6fr column) | 160dp | 102dp |
| Similar, film and show (`TvSimilarRow`) | 140dp, `Spacing.medium` gap | 102dp, the strip's 10.5dp gap |
| `TvFilmOverview`'s title-page poster | 160dp | unchanged, as briefed |

Every row on the box lands on the floor: a department row is 960 − 60.75 (rail) − 2 × 30.72
(gutter) ≈ 838dp wide, an eighth of that less the gaps is ≈ 95dp. That is the web at 1280px too
(`main` ≈ 1117px, eighth ≈ 127px < 136px), so ≈ 7.5 plates across where there were ≈ 4.3. The web's
similar rows (`film-page.js:95`, `series-page.js:176`) and Recently added (`home-view.js:94`) are
`.strip`s, so they take the same rule.

Checked:
- Plate text: `TvPlate` already sets the name at `.grid.plates .name`'s 17px and the line at 12px,
  two lines and one, ellipsized; both fit 102dp. Progress rule and tick are full-width/corner overlays.
- Focus: `TvFocus.Scale` 1.08 grows a 102 × 153dp plate by ≈ 6dp each way, inside the lazy row's
  cross-axis clip allowance; smaller than the 160dp plates' overflow was.
- `DeptCacheAhead`/`Behind` (320dp) are distances past the row's edge, not plate counts; at 102dp
  they keep about three plates composed either side instead of two. Left as they are.
- `TvSimilarFilms`/`TvSimilarShows` asked for focus from a `LaunchedEffect` in the page's own
  composition. Inside `BoxWithConstraints` the plates compose at layout, after that effect is
  dispatched, so the effect moved inside the constraints' content, beside the plates.
  Department and Home rows already wait for layout (`scrollThenFocus`, `TvHome`'s `visibleItemsInfo`).

Not changed, worth a look: `TvPosterStrip`'s name is 15px, after `.strip .name` (`catalog.css:41`).
On the web that rule loses to `.grid.plates .name` (`catalog.css:54`, three classes against two), so
the web draws strip names at 17px, as `TvPlate` does.

## 2. Player card and top bar

Faces and characters are the tablet's (`ui-mobile/.../PlayerCardTransport.kt`, `PlayerCardTools.kt`,
`PlayerControlParts.kt`, `PlayerTopBar.kt`); sizes and spacing are the web's CSS through `tvDp`/`tvSp`.
New roles in `TvPlayerType`; `title`/`body` stay for the sidebar, notes, up next, menus and failure.

| Piece | Before | After | Web |
|---|---|---|---|
| ↺ −15 +15 ⓘ ☰ | Fraunces 500, 34sp | Geist 500, 11.25sp (15px as a character, stands as tall as the 20px icon) | `.tsp svg` 1.25rem, `playback.css:199` |
| ⏮ ⏭ icons | 45dp | 15dp | `.tsp svg` 20px |
| play/pause icon | 45dp | 21dp, in a 34.8dp target | `.tsp.big` 2.9rem / svg 1.75rem, `playback.css:204-205` |
| control targets | text + `Spacing.small`/`medium` padding | ≥ 33dp square, label centred | `.tsp` 2.75rem `playback.css:185-186`; `.tool` 44px `player-card.css:68-69` |
| skip padding | 8dp | 6dp | `#skip-back` 0 .5rem, `playback.css:211` |
| transport gap | 8dp | 7.2dp | `.transport` .6rem, `playback.css:177` |
| CC | "CC ●"/"CC ○", Fraunces 34sp | "CC", faint when off (the tablet's), Geist 10.5sp | `.tool` .875rem, `player-card.css:66-80` |
| ▾, 1×, Audio, 4:3 | ▾ Fraunces 34sp; labels Geist 400 18sp | Geist 500 10.5sp, 9dp padding (▾ 6dp beside CC, no gap) | `.tool`, `.cc .tool + .tool` `player-card.css:70,86` |
| tools gap | 8dp | 9dp | `.card-row` .75rem, `player-card.css:37` |
| seek-row times | Geist 400 18sp | Geist 500 9.84sp, tabular, 0.04em | `.control-card .at` .82rem, `player-card.css:57-63` |
| card | 760dp max, 16dp padding, 8dp rows | 660dp max, 12 × 9dp padding, 6dp rows | `.card-dock` 880px, `.control-card` `player-card.css:6-31` |
| bar title | Fraunces 34sp | Fraunces 500 13.8sp | `.hud .what` 1.15rem, `playback.css:72-83` |
| My List, Kids, Add to list, Notes | Geist 400 18sp, 16dp padding | Geist 500 9.75sp, 10.2dp padding, ≥ 33dp | `.ghost` .8125rem, `playback.css:85-98,595` |
| bar gaps | 16dp / 8dp between marks | 14.4dp | `.hud-top` 1.2rem, `playback.css:52` |
| file line under the title | Geist 18sp | Geist 500 9sp, tabular | `.ends` .75rem, `playback.css:164-170` |

Kept, as briefed: D-pad order and named hops, `TvFocus` treatment and flat controls, keys, the
landing on play/pause, the seek bar's stepping and its focused thickening, `TvCardInset` (overscan).

Deviations, each written in the code:
- ⓘ and ☰ follow ⏭ in the one centred row, as on the tablet; the web stands them at the row's
  end (`.card-end`, `player-card.css:49-55`). `TvTransport.kt`.
- Bar buttons are transparent until focused, as the tablet's; the web rims each `.ghost` in a
  hairline over a 35% black fill. `TvPlayerTopBar.kt`.
- The file line under the title is the television's own; the web's and tablet's bars show the
  title alone. Kept, sized as the web's quiet figures. `TvPlayerTopBar.kt`.
- `TvGlyphButton` is shared with the episode list's ‹ › ✕, which are `.tsp`s on the web too
  (`episode-sidebar.js:26-31`), so they take the new glyph size.

Not changed: the top bar's solid scrim band (the tablet and web fade a gradient), the seek bar's
track thickness, and every overlay surface outside the card and the bar.

## Tests

`./gradlew :ui-tv:testDebugUnitTest :ui-tv:lintDebug`, rerun after the last commit: 623 tests, 623
passed, 0 failed, 0 skipped (two new); lint passed. `:core:designsystem` untouched.

- New: `TvPageSpacingTest.aStripPlateIsAnEighthOfItsRowAndNeverUnderTheFloor` (800dp row → 102dp,
  1000dp → (1000 − 73.5)/8, no bound → floor) and a width check in
  `aDepartmentRowStandsUnderItsHeadAsTheWebsDoes` (screen-wide row → (960 − 73.5)/8).
- New: `TvPlayerScreenTest.theSkipsAreSetInTheInterfaceFaceAtTheWebsSize` (−15/+15 in
  `TvType.labelLarge`'s family, not `headlineSmall`'s, at 15px; CC at 14px).
- Changed: the card's left edge 100dp → 150dp and its width checked against 880px;
  `"CC ●"/"CC ○"` text checks now read CC's state description (`ccStands`, the harness).
- Lint still warns `ModifierParameter` on `TvCardTools`' `each` parameter; it predates this work.

## Commits

- `e4c73870` fix(tv): poster rows take the web's strip width, eight across
- `791c78cf` fix(tv): the player card and its top bar read like the tablet's at the web's size
- `0d57f61a` chore: 0.120.4 — TV posters and player controls at the web's size

Nothing built or installed; no device touched.
