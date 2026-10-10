# TV rail margin, then one scale for the whole TV

2026-10-10, worktree `mediagram-tv`, branch `feat/tv-parity`. Not pushed, not released, no device touched.

- `fe85a285` fix(tv): draw the TV a quarter smaller, text and spacing from one scale
- `f55ab9e1` chore: release 0.120.1 — the TV is a quarter smaller (Cargo.toml, web/package.json, versionName, Cargo.lock, changelog)
- `be6f837a` fix(tv): page spacing and the end gutter through the TV scale (folded into 0.120.1; the changelog entry amended)

## How the brief moved

1. **First brief:** "the sidebar still has too much margin". Fix the rail to the web's numbers at the 960px rule (1 px = 1 dp).
2. **Hold:** the font size is also too large. Nothing had been committed yet.
3. **User decision:** one scale for text and spacing. One web CSS px = **0.75 dp/sp**, the web read at a 1280px viewport (960/1280), with `vw`/`clamp()` evaluated at 1280px. This replaces batch 1's 960px rule and its "1920 halved" display rule.

The margin fix and the size fix landed as one change.

## The knob

`designsystem/TvTypeScale.kt`:

- **`const val TvScale = 960f / 1280f`.** One number to retune.
- **`Number.webDp` / `Number.webSp`:** web px through the scale.
- **`TextStyle.onTv()`:** scales the size, the sp line height and the sp tracking.
- **`TvType`:** the ten `CatalogueType` roles plus `Eyebrow`, `SectionHead`, `LedgerLabel` and `LedgerValue`, all through the scale.
- **The phone is unaffected.** `CatalogueType` itself is unchanged.

What changed in `:ui-tv`:

- Every `CatalogueType.` is now `TvType.`: 130 references in 53 files.
- Every literal `N.sp` outside `player/` is now `N.webSp`: 28 files.
- The tv-material slots in `TvTheme` hold the `TvType` roles.
- The rail, the departments bar and the page gutter take their px through `webDp`.

**The floor is dropped.** The web sets no minimum size, so the 11sp floor is gone. The rail count and tally use the web's own 10px, which is now 7.5sp.

## Rail (web `shell.css:17-70`)

The web has no collapsed rail at 1280px; under 900px the rail turns into a header instead. So the collapsed rail is the open rail's own icon column: 18 + 12 + 21 + 12 + 18. Measured is from the Robolectric run at the box's density (xhdpi).

| | Web px @1280 | Old TV | New TV (dp) | Measured |
|---|---|---|---|---|
| Collapsed width | 81 (derived) | 96 | 60.75 | 61 |
| Icon inset from screen edge | 30 (18 + 12) | 56 (overscan 48 + 8) | 22.5 | 22.5 |
| Icon size | 21 | 16 collapsed (squeezed), 22 open | 15.75 both | |
| Open width | 224 (14rem; 184 under 1180) | 224 | 168 | 168 |
| Rail padding L / R / T / B | 18 / 18 / 26 / 28 | 48 / 16 / 27 / 27 | 13.5 / 13.5 / 19.5 / 21 | |
| Row padding | 12 × 10 | 8 × 8 | 9 × 7.5 | |
| Row min height | 44 | 44 | 33 | |
| Icon → label gap | 12 | 16 | 9 | label at 47.25 → 47.5 |
| Row gap / System `.apart` | 2 / 12 | 0 / 16 | 1.5 / 9 | |
| Wordmark inset / below | 10 / 36 | 0 / 24 | 7.5 / 27 | |
| Wordmark | Fraunces 600, 27.2 | 27sp | 20.4sp | |
| Tally rule: top, sides | 28, 10 | 24, 0 | 21, 7.5 | |
| Tally text: top, sides | 24, 16 | 24, 0 | 18, 12 | |
| Label / count / tally | 14 / 10 / 10 | 14 / 11 / 11 (floor) | 10.5 / 7.5 / 7.5sp | |
| First row top | ≈ 92 | 71 collapsed, 85 open | same either way | 71.5 both |

- **Rows no longer jump as the rail opens.** They used to move 14dp: the collapsed spacer was 44dp, but the wordmark was 34dp tall plus its 24dp gap. The wordmark is now laid out in both states and hidden (`alpha 0`, no semantics) while collapsed.
- **`TvIndexRow` gained four defaulted parameters:** padding, icon size, gap and alignment. The rail passes the web's values. The Settings index keeps its own metrics.

## Departments bar and gutter

| | Web px @1280 | Old TV | New TV (dp) |
|---|---|---|---|
| Bar height (overscan 27 above) | 76 (`theme.css:114`) | 76 (103 total) | 57 (84 total) |
| Pill padding | 10 × 16 (`shell.css:90`) | 10 × 12 | 7.5 × 12 |
| Pill min height / gap | 44 / 4 | 44 / 4 | 33 / 3 |
| Toolbar gap | 16 (`shell.css:91`) | 4 | 12 |
| Search and avatar / search icon / initial | 44 / 21 / 17 | 44 / 21 / 17sp | 33 / 15.75 / 12.75sp |
| Rail edge → first pill | gutter, 40.96 | 16 | 30.72 (one edge with the page) |
| Rail edge → page (`--gutter`, 3.2vw) | 40.96 | 32 | 30.72 |
| Page start from screen edge | — | 128 | 91.5 |

**The right end now matches the left.** The bar's end and the page's end are one `--gutter` (30.72dp) in from the screen edge. The web pads `.masthead` and `main` by the same gutter on both sides (`shell.css:81,158`). Before this, the right side alone kept the 48dp overscan margin. `TvDepartmentsBarTest.theAvatarAndThePageEndOneGutterInFromTheEdge` pins it.

| | Web px @1280 | Old TV | New TV (dp) |
|---|---|---|---|
| Avatar's right edge from the screen edge | 40.96 | 48 | 30.72 |
| Page's end padding | 40.96 | 48 | 30.72 (equal to the start) |

## Text

The roles are the web's px × 0.75. The old values were the web's px at 1:1.

| Role | Web px | Old sp | New sp |
|---|---|---|---|
| labelLarge (pills, rail, names) | 14 | 14 | 10.5 |
| bodyMedium (pills, tabs, rows) | 15 | 15 | 11.25 |
| bodySmall / labelMedium | 13 | 13 | 9.75 |
| bodyLarge (Newsreader) | 17 | 17 | 12.75 |
| titleSmall / titleMedium / titleLarge / headlineSmall | 16 / 18 / 23 / 26 | same | 12 / 13.5 / 17.25 / 19.5 |
| labelSmall, Eyebrow | 11 | 11 | 8.25 |
| SectionHead / LedgerLabel / LedgerValue | 16 / 14 / 15 | same | 12 / 10.5 / 11.25 |
| Literal call-site sizes (row head 20.8, shelf sub 24, resume 14/12, plate 17/12…) | as listed | same | × 0.75 |

### Re-evaluated at 1280px

These are `vw`-based values that sat at their 960px floor before.

| Role | CSS | Old sp | New sp |
|---|---|---|---|
| Cover deck | `clamp(1.1rem, 1.4vw, 1.3rem)` → 17.92 | 17.6 | 13.44 |
| Pull quote | `clamp(1.6rem, 2.3vw, 2.35rem)` → 29.44 | 25.6 | 22.08 |
| Department quote | `clamp(1.3rem, 1.9vw, 1.8rem)` → 24.32 | 20.8 | 18.24 |
| Destination name | `clamp(1.4rem, 2vw, 1.8rem)` → 25.6 | 22.4 | 19.2 |
| Who's watching | `clamp(1.75rem, 4vw, 2.5rem)` → 40 | 38.4 | 30 |
| Spread quote | `clamp(1.4rem, 2.1vw, 2rem)` → 26.88 | 22.4 | 20.16 |
| Stats total | `clamp(1.6rem, 3vw, 2.4rem)` → 38.4 | 28.8 | 28.8 (unchanged) |

### Display titles: a deliberate departure from "vw at 1280"

- **Read literally, the rule grows these titles.** At 1280px the display clamps run near their ceilings: department 81.6, cover 61.4, spread 59.5, shelf head 42.2, franchise 52.8sp. That is a third larger, while the user asked for smaller.
- **So they keep the web's 1920px size halved, through the same scale.** That is the user's 2026-10-09 desktop-proportions decision, now a quarter smaller:

| Title | Old sp | New sp |
|---|---|---|
| Department | 60 | 45 |
| Franchise | 40 | 30 |
| Cover | 56 | 42 |
| Cover, long | 36.8 | 27.6 |
| Spread | 46 | 34.5 |
| Spread, long | 34 | 25.5 |
| Shelf head | 28.8 | 21.6 |
| Feature titles | 24 / 26 | 18 / 19.5 |

- **Undoing it is a one-object change** in `TvTypeScale`.
- **Docs:** `DESIGN.md`'s Television Rule and `docs/system-architecture.md` say the same.

## Not scaled (out of this change's scope)

- **The 48 / 27dp overscan:** the right and bottom page edges, the bar's top and end.
- **Page spacing:** plates and cards, hero heights, title-page pills, wall gaps and the `Spacing` tokens inside pages.
- **The Settings index rows' metrics.** Their text did scale.
- **Focus mechanics:** the ring room and the fade width.
- **The player overlay** (`TvPlayerType` 18/34, subtitles), which is still left for batch 5.

## Tests

Changed:
- **`TvTypeScaleTest` (designsystem), rewritten:**
  - the scale is 0.75;
  - a 10px count is 7.5;
  - the roles are web px × `TvScale`;
  - an `em` line height follows its size;
  - display = 1920 halved × `TvScale`.
- **`TvLibraryRailSizesTest`, rewritten.**
  - It runs at the box's density (`xhdpi`), allowing one pixel (0.5dp) because layout rounds to whole pixels.
  - Open rail: width, font sizes, wordmark inset, label start, row pitch, `.apart`, and no label ellipsized.
  - Collapsed rail: the width and the row's inset and width. Opening moves no row, and the first row sits at wordmark + 36.
  - Every expected number is web px × `TvScale`.
  - It failed first on the old code: 96 ≠ 81 collapsed, 224 open, and rows jumping 71 → 85.
- **`TvDepartmentsBarTest`:**
  - the pill is 14 × scale;
  - new: `theFirstPillStandsOneGutterOffTheCollapsedRail`, which was 96 before.
- **`TvResumeCardTest`, `TvThemeTest`:** they now expect the scaled `TvType` sizes.
- **`TvLibraryChromeUpTest`, `TvCollectionStateTest`:** the fixtures are longer (department names, 8 seasons) so the rows still overflow at the smaller size.
- **`TvListStateTest` is unchanged.** It caught a real regression: Up from the first title landed on Rename, because a narrower Play all lost the plate's column. Up into the list's actions now lands on Play all (`onEnter`).

Added with the spacing:
- **`TvPageSpacingTest` (new, xhdpi, one-pixel tolerance):**
  - every page spacing is web px × `TvScale`;
  - a wall of plates measures 22 across and 36 between lines; a wall of tiles, 14;
  - a department row's head stands 44 down, the row 20 under it, plates 14 apart.
- **`TvDepartmentsBarTest.theAvatarAndThePageEndOneGutterInFromTheEdge`:** replaces the 48dp end check. The avatar's end and the page's end padding equal the gutter at both sides.
- **`TvAnimeDepartmentPageStateTest`:** two tests run in a taller window. At the wall's 36px line gap, the Films line fell under a 1080dp window.

Runs, all green:
- `:core:designsystem:testDebugUnitTest`: 23 tests, 0 failed.
- `:ui-tv:testDebugUnitTest`: 597 tests, 0 failed (forced rerun), then 601 tests, 0 failed with the spacing.
- `:ui-tv:compileDebugAndroidTestKotlin` and `:ui-mobile:compileDebugKotlin` pass.
- `:ui-tv:lintDebug` passes with no errors. The warning count was not read, because the hook blocks reading the build directory.

## Page spacing

Each value is the web's px at 1280 × 0.75. All of them live in `catalog/TvPageSpacing.kt`, except where a single call site carries its own.

| Spacing | Web px @1280 (source) | Old TV | New TV (dp) |
|---|---|---|---|
| Home: cover → features | 12 (`.features`, home.css:135) | 32 | 9 |
| Home: Continue band, Recently added band tops | 28 (`.home-band`, home.css:189) | 32 | 21 |
| Home: Latest series, Latest courses tops | 36 (`.home-section`, home.css:192) | 32 | 27 |
| Home: band's column gap (Continue + quote, Recent + This month) | `clamp(32px, 4vw, 72px)` = 51.2 (home.css:188) | 32 | 38.4 |
| Home: quote column minimum | 16rem = 256 (home.css:186) | 240 | 192 |
| Row head → row (Home, departments, Collections) | 20 (`.row-head`, home.css:194) | 16 Home, 8 department, 0 series and courses | 15 |
| Continue cards' gap | 16 (`.resume-strip`, home.css:218) | 16 | 12 |
| Poster strip's gap (Recent, Latest series, department rows) | 14 (`.grid.plates.strip`, catalog.css:36) | 16 | 10.5 |
| Art tile gap (Genres row, Genres wall) | 14 (`.genre-tiles`, catalog.css:139) | 16 | 10.5 |
| Department row and Collections section top | 44 (`.dept-row`, departments.css:53) | 24 | 33 |
| "All N films" pill top | 56 (`.dept-all`, departments.css:73) | 56 | 42 |
| Collections' card gap / "New list" top | 16 / 20 (departments.css:97,106) | 16 / 20 | 12 / 15 |
| Wall of plates: between lines × across | 36 × 22 (`.grid.plates`, catalog.css:30) | 16 × 16 | 27 × 16.5 |
| Shelf head: title → rule; title → sub | 20 / 24 (`.shelf-head`, catalog.css:11) | 20 (counted head 8) / 24 | 15 / 18 |
| Pull quote: text → rule → credit; rule width | 24 + 4 / 18; 32 (home.css:296,302) | 16 / 8; 32 | 21 / 13.5; 24 |
| This month: inset, row padding, gap, number column, title → list | 28, 10, 8, 2.2rem, 8 (home.css:307-321) | 16, 8, 8, 35, 8 | 21, 7.5, 6, 26.4, 6 |
| Feature copy: padding, width, eyebrow → title | 36 × `clamp(24px, 2.6vw, 40px)` = 33.28, 26rem, 18 (home.css:169-170) | 16 × 24, 480, 8 | 27 × 24.96, 312, 13.5 |
| Feature cards' gap | 12 (`.features`, home.css:134) | 16 | 9 |
| Cover: eyebrow → title, title → deck, deck → meta | 22, 28, 18 (home.css:47,59,64) | 16, 24, 8 | 16.5, 21, 13.5 |
| Department hero: title → line, → overview, quote → credit | 14, 18, 14 (departments.css:31,109,48) | 8, 16, 8 | 10.5, 13.5, 10.5 |

### Left as they are, and why

- **TV-specific and not taken from the web:**
  - the 16dp under a shelf head's rule, which leaves room for a focused first plate's 1.08 grow (the web's 36 margin is the wall's own line gap below);
  - the pill ring room (8dp) and the bar's edge-fade width (24dp);
  - the focus scale and the bring-into-view clearance;
  - the quote mark's 8dp, since the TV hangs the mark above the text where the web hangs it in the margin.
- **Overscan 27dp top and bottom.** The bar's top inset and the page's bottom are vertical, and the brief was about the horizontal one.
- **Pushed full-screen pages** (title, Search, Latest, Settings, profiles, setup, menus) keep 48dp on both sides. That margin is already symmetric. Those pages are a documented TV difference (full screen, no rail), and the web has no full-screen equivalent to take a number from.
- **Search's result lines.** The TV puts several web grids (movies, people, destinations) into one list with one spacing, so no single web value maps onto it. The filter chips there belong to the focus batch.
- **Title page:** the tabs' 32 gap and fact sheet 160/14 are already scheduled for the gutters and focus batches. The title pills' 48dp and the spread's layout are sizes, not spacing.
- **Sizes of things, which the brief was about spacing, not:** card, plate and tile widths (160 / 240dp), hero heights (360 / 380dp), feature card heights (300 / 220), and the poster aspect.
- **The player overlay**, still left for batch 5.
- **Pages with three left edges** (the wall's padding added again by department pages). That is batch 3's "one left edge", a layout fix rather than a number.
- **The feature card's rule.** The TV draws none, so the deck keeps its 8dp under the title.

## Concerns

1. **Display titles depart from "vw at 1280"** (above). A literal reading makes them a third larger. *Ruling: keep.*
2. **The rail's icons now sit 22.5dp from the screen edge.** *Ruling: keep; the box is an HDMI dongle, so no overscan is expected.* Both sides now share the gutter.
3. **Counts and the tally are 7.5sp** (15px on the 1080 panel), because the floor was dropped. *Ruling: keep for now; the user judges on the box.*
4. **The stats total stays 28.8sp:** its clamp hits the 2.4rem ceiling at 1280px.
5. **Not seen on the box.** All the numbers above come from code and Robolectric.
6. **The tablet** rail lacks the web's 2px row gap and the tally's extra 6px inset (`LibraryRail.kt`). That is out of scope here.
7. **Files over 200 lines** that were touched only by imports or call-site renames were not split. The rail file is held at 200.
