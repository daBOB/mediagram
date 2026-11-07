# TV design gap audit: what the TV does that the web design does not

2026-10-09. Read-only audit. The web player is the reference (CLAUDE.md § Surface Parity). The phone and tablet are the Compose reading of that design. Nothing here is a new design: every proposed value is a web value, a shared `designsystem` token, or a ui-mobile value.

**Evidence**
- **The TV photo (primary):** `/home/andre/Downloads/PXL_20261009_132109082.jpg`, Series page, today. A flattened copy is `tv-audit/photo-series-flat.png`.
- **Box screenshots from yesterday:** `scratchpad/tv*-full.png`, 20 shots, all read. `tvb3` is the Google TV launcher, not mediagram.
- **Web captures:** taken from the stub preview at 1920×1080 in the dark theme, and again at 960×540 @2x (`web960-*.png`). They show the web in a window the size of the TV's dp viewport. All are in `tv-audit/`.
- **Code:** `ui-tv`, `ui-mobile`, `core/designsystem`, the root `DESIGN.md` (Android) and `web/DESIGN.md`.
- **Preview and live player:** the preview has been stopped. The live player on :8770 was not touched.

All paths below are relative to `/tmp/claude-1000/-home-andre-Workspace-mediagram/35591186-acb4-4a07-9b7f-c6e887a63688/scratchpad/`.

## How the TV sizes were picked, and the rule to replace them

The box draws its UI at 960×540 dp: 1080p at xhdpi, so 1 dp = 2 px. Nothing in ui-tv overrides density or font scale. The inflation is entirely in the token values:

- `TvTypeScale` (`core/designsystem/.../Type.kt:254-276`) has two text sizes: `body` 18sp and `title` 34sp.
- `TvTheme.kt:80-91` pours every title and headline slot into 34sp and every body slot into 18sp.
- 171 call sites use one of those two. An 18sp label is 36 px on the panel, against the web's 14 px.
- The eyebrow is explicitly "double the phone's 11sp" (`Type.kt:271`). That doubles a value the 2× density had already doubled, so a 16sp eyebrow is 32 px against the web's 11 px.
- **Display type is the exception.** It was derived from the web's own `vw` clamps evaluated at the TV's fixed 960 dp width:
  - cover 61sp (`TvCoverSlide.kt:97`);
  - department title 81.6sp (`TvDepartmentHero.kt:188-189`);
  - spread title 59.5sp (`TvTitleSpread.kt:180`);
  - shelf head 42sp (`TvHeadings.kt:56`, `DESIGN.md:576-577`).

**The rule to apply everywhere** is the one the display type already uses: **the web's CSS evaluated at a 960 px window, with px read as sp/dp.**

- This is the 10-foot adaptation. On a 55" set at 3 m, one dp subtends about what one CSS px subtends on a 24" monitor at 65 cm. Text then reads at the web's desktop size.
- The phone's scale already holds these numbers: Geist 13/15, Newsreader 17, Fraunces 16/18/23/26, Eyebrow 11, SectionHead 16, Ledger 14/15 (`Type.kt:110-238`).
- The only floor is 11sp, tv-material's smallest role. It lifts the web's 10 px counts.
- Proof that the rule is the design: `web960-series.png` is the web at the TV's dp size. Its display type matches the TV, and its labels and headings are visibly smaller than the TV's.

---

## A. Type scale

Columns:
- **web px:** the web at 1920.
- **@960:** the web at a 960 px window, which equals the proposed TV sp.
- **TV:** the current TV value.

| Role | Web (file:line) | web px | @960 | TV now (file:line) | Proposed |
|---|---|---|---|---|---|
| Department tabs | Geist 500 (shell.css:47,90) | 14 | 14 | Geist 18 (TvChromeControls.kt:70 via TvTheme.kt:88) | **14** |
| Tab/rail counts | 40% ink (shell.css:60) | 10 | 10 | 13 (TvChromeControls.kt:74, TvIndexRow.kt:137, Type.kt:275) | **11** |
| Rail row label | Geist 500 (shell.css:47) | 14 | 14 | 18 (TvIndexRow.kt:133) | **14** |
| Rail tally | caps .34em (shell.css:66) | 10 | 10 | 16 (TvLibraryRail.kt:141) | **11** |
| Cover kicker / feature eyebrow | `.eyebrow` (theme.css:200) | 11 | 11 | 16 (TvCoverSlide.kt:90, TvHomeFeatures.kt:132) | **11** |
| Cover headline | Fraunces 600 (home.css:50) | 112 | 61 | 61 (TvCoverSlide.kt:97) | keep |
| Cover deck | **Newsreader** (home.css:60) | 20.8 | 17.6 | **Geist** 18 (TvCoverSlide.kt:105) | **Newsreader 17.6** |
| Cover meta | (home.css:65) | 13 | 13 | 16 (TvCoverSlide.kt:115) | **13** |
| Pills (cover, title page) | Geist 500 (title-page.css:14) | 15 | 15 | 18 (TvCoverActions.kt:128, TvTitlePills.kt:87) | **15** |
| Home/dept row heading ("Continue your series") | **Geist 600** (home.css:195) | 20.8 | 20.8 | **Fraunces** 34 (TvBandHeading.kt:44) | **Geist SemiBold 21** (= ui-mobile ContinueBand.kt:90) |
| Wall section ("Films", "Series") | Fraunces 600 `.shelf-sub` (catalog.css:207) | 24 | 24 | 34 (TvWall.kt:178 → TvBandHeading.kt:44) | **Fraunces 600 24** |
| Shelf head title | (catalog.css:116) | 57.6 | 42.2 | 42 (TvHeadings.kt:56) | keep |
| Shelf head extent | caps .24em (catalog.css:122) | 11 | 11 | 16 (TvHeadings.kt:64) | **11** |
| Dept title "SERIES" | Fraunces 500 (departments.css:32) | 120 | 81.6 | 81.6 (TvDepartmentHero.kt:189) | keep (see Q2) |
| Dept line "171 shows · …" | **Newsreader** ink-2 (departments.css:36) | 20 | 20 | **Geist** 18 (TvDepartmentHero.kt:129) | **Newsreader 20** |
| Dept quote / credit | Newsreader italic / caps (departments.css:47-48) | 28.8 / 10 | 20.8 / 10 | Geist italic 18 / 16 (TvDepartmentHero.kt:161,168) | **Newsreader italic 20.8 / 11** |
| Spread title | (title-page.css:89) | 92 | 59.5 | 59.5 (TvTitleSpread.kt:180) | keep |
| Spread facts | (title-page.css:94) | 14 | 14 | 18 (TvTitleSpread.kt:112) | **14** |
| Spread overview | **Newsreader** /1.6 (title-page.css:101) | 17 | 17 | **Geist** 18 (TvTitleSpread.kt:122) | **Newsreader 17** |
| Spread quote | **Newsreader** italic (title-page.css:113) | 32 | 22.4 | **Geist** italic 22.4 (TvTitleSpread.kt:139-141) | **Newsreader** italic 22.4 |
| Title tabs | (title-page.css:127) | 15 | 15 | 18 (TvSectionTabs.kt:100) | **15** |
| Fact label / value | (title-page.css:140-141) | 11 / 15 | 11 / 15 | 16 / 18 (TvFactSheet.kt:53,58) | **11 / 15** |
| "Directed by" line | **Newsreader** (title-page.css:174) | 17 | 17 | Geist 18 (TvCastRow.kt:146) | **Newsreader 17** |
| Person name / role | (title-page.css:182-183) | 14 / 12 | 14 / 12 | 18 / 18 (TvSearchRows.kt:159) | **14 / 12** |
| Plate name / meta | **Fraunces 500** / Geist (catalog.css:163-164) | 17 / 12 | 17 / 12 | **Geist** 18 / 18 (TvPlate.kt:103,120) | **Fraunces Medium 17 / 12** |
| Strip plate name | Fraunces 500 (catalog.css:150) | 15 | 15 | CoverTitle 600 18 / 16 (TvPosterStrip.kt:109,116) | **Fraunces Medium 15 / 12** |
| Resume card name / sub | (home.css:272-273) | 14 / 12 | 14 / 12 | 18 / 16 (TvResumeCard.kt:101,111) | **14 / 12** |
| "offline" badge | held text, no box (catalog.css:196-198, set-badge.js:65) | 12 | 12 | 18 in a box (TvPlateMarks.kt:98-104) | **12, sage, no box** |
| List row: title / sub / num / meta | (catalog.css:188-193) | 15/13/12/13 | same | one 18 line (TvCollectionRow.kt:86 → TvTextRow.kt:51) | **15/13/12/13** |
| Season/module heading | 600 ink-2; top level ink (catalog.css:180-182) | 15 / 18 | same | 18 SemiBold (TvHeadings.kt:133) | **15 / 18** |
| Search filter | (catalog.css:260) | 14 | 14 | 18 (TvSearchResults.kt:204) | **14** |
| Settings section title | Geist 600 (appearance.css:31, library-controls.css:44) | 16 | 16 | **Fraunces 34** (TvInfoBlock.kt:66, TvAppearanceSection.kt:62,75) | **`SectionHead` 16** (Type.kt:227) |
| Ledger label / value | (library-controls.css:47-48); Android 14/15 | 13 / 14 | 14 / 15 | 18 / 18 (TvInfoBlock.kt:78,81) | **`LedgerLabel`/`LedgerValue`** (Type.kt:235,238) |
| Settings index label / sub | Android: **Fraunces 16** / 12 (DESIGN.md:620-621) | — | 16 / 12 | **Geist** 18 / 16 (TvIndexRow.kt:133, TvSettingsIndex.kt:133) | **Fraunces 16 / Geist 12** |
| Page-head eyebrow | `Eyebrow` (Type.kt:217) | 11 | 11 | 16, wraps to two lines on tvs1 and tvst1 (TvSettingsPanes.kt:137) | **11** |
| Profile heading / initial / name / kids | Geist 600 clamp / Geist 500 32 / 14 / 12 held (library-controls.css:4,7,8,10) | 40/32/14/12 | 38/32/14/12 | Fraunces 34 / Fraunces 34 / 18 / 18 **accent** (TvProfilePicker.kt:136; TvProfileTiles.kt:80,83,136) | **38 / 32 / 14 / 12 sage** |
| Sentences (loading, empty) | Android Newsreader 17 (DESIGN.md:725) | — | 17 | Geist 18 (TvHeadings.kt:151,167) | **Newsreader 17** |

Two things the table shows:
- **Font families.** The TV never sets Newsreader anywhere: `designsystem.Read` is `internal` (Type.kt:93) and `TvTypeScale.body` is Geist. It also sets plate names and row headings in the wrong face.
- **Fix shape.** Expose the phone's roles as plain `TextStyle`s from `designsystem`; ui-tv cannot take M3 `Typography`. Then map each call site to its role. That replaces two sizes with the design's ten.

## B. Spacing

| Element | Web @1920 (file:line) | @960 | TV now (file:line) | Proposed |
|---|---|---|---|---|
| Masthead height | 76px (theme.css:114) | 76 | 27 overscan + 76 = **103dp, 19% of the screen** (TvDepartmentsBar.kt:96, TvLibraryChrome.kt:30) | **83dp** = 27 + 56 *(10-foot adaptation: the overscan inset already supplies the bar's top air)* |
| Tab padding / gap / height | 10×16, 12px inline ≤1180 / 4 / 44 (shell.css:43,89-90,181) | 10×12 / 4 / 44 | 8×16 / 4 / ~43 (TvChromeControls.kt:67, TvDepartmentsBar.kt:110) | **10×12 / 4 / 44** |
| Rail, open | 224px, labelled, beside the page (theme.css:113) | 184 | 96 collapsed (deliberate), **288dp opened over the page** (TvLibraryRail.kt:43-44) | keep 96; open at **224dp**, "the web's own width" (docs/system-architecture.md:884-888) |
| Page gutter | 56 (theme.css:112) | 31 | 32 after rail, 48 right (TvLibraryChrome.kt:33,189) | keep |
| Left edges on a department page | one gutter (departments.css:11-12, shell.css:158) | — | **three:** plates 128dp, rows 160dp, hero title 192dp; right edge 864dp. Cause: TvWall.kt:152 contentPadding, re-padded in TvShowsDepartmentPage.kt:79,92,145 and TvAnimeDepartmentPage.kt:83,86, then again in TvDepartmentHero.kt:109 | **one edge**, 128 left / 912 right |
| Dept hero top | bleeds up under the masthead (departments.css:11) | — | starts below the bar (contentPadding top 103dp); 360dp fixed (deliberate) | bleed under the bar; keep 360 |
| Row head → row | 20 (home.css:194) | 20 | 8dp dept (TvDepartmentRows.kt:85), 16dp home (TvContinueBand.kt:74) | **16** both |
| Strip / resume gaps | 14 / 16 (catalog.css:145, home.css:218) | same | 16 / 16 | keep |
| Wall gaps rows × cols | 36 × 22 (catalog.css:139) | same | 16 × 16 (TvWall.kt:153-154) | **36 × 22**, once the caption leaves the card |
| Plate caption | 12 art→name, 4 name→meta, no side inset (catalog.css:161-162) | same | 8 + 8dp side inset inside a card (TvPlate.kt:106,124) | **12 / 4 / 0** |
| Resume caption | block padding 26/12/10, **sub max 42%** (home.css:267,273) | same | 8dp all round, no width cap (TvResumeCard.kt:100) | **12 sides / 10 bottom; sub 42%** |
| Title tabs | gap 32 + rule-soft under the row (title-page.css:120) | 32 | 16, no rule (TvSectionTabs.kt:101) | **32 + 1dp rule-soft** |
| Fact sheet | label column 160, row padding 14 (title-page.css:139) | same | 220 / 8 (TvFactSheet.kt:83,86) | **160 / 14** |
| Profile picker | 96px tiles, 12 to the name, gap 24 (library-controls.css:5-7) | same | 180×220dp cards, gap 16 (TvProfileTiles.kt:37,63; TvProfilePicker.kt:169) | **96 / 12 / 24**, no card |
| List rows | min 56, padding 16×8, rule-soft divider (catalog.css:185) | same | 8dp gaps, no padding, no rule (TvCollectionRows.kt:80-81) | **per web** |
| Search filters | 40px pills, gap 8 (catalog.css:259-260) | same | text rows, gap 16 (TvSearchResults.kt:199-200) | **per web** |

## C. Corner radii

The root cause is one constant: `TvFocus.Shape = RectangleShape` (TvFocus.kt:46-52, 74), "square, like the web player's plates". The web's plates are 3px (catalog.css:158). Every surface that takes the default draws square, and so does the focus ring cut from it.

| Surface | Web | Android shared / mobile | TV now | Proposed |
|---|---|---|---|---|
| Wall plate poster | 3px (catalog.css:158) | DESIGN.md plate 0dp (DESIGN.md:85,449,812); mobile PosterArt square | square (TvPlate.kt:93) | **Q1** |
| Home/dept strip posters | 3px (catalog.css:149) | mobile 3dp (HomeShelfRow.kt:60, RecentBand.kt:103) | square (TvPosterStrip.kt:87, TvRecentBand.kt:120) | **3dp** |
| Continue (resume) cards | 3px (home.css:242) | mobile 3dp (ResumeCard.kt:87) | square (TvResumeCard.kt:77), visible in the photo | **3dp** |
| Course tiles | — | mobile 3dp (CourseList.kt:58) | square (TvCourseList.kt:67) | **3dp** |
| Overview poster | 6px (title-page.css:137) | — | square (TvFilmOverview.kt:32) | **6dp** |
| Dept hero art | no edge at all, bleeds (departments.css:11,16) | mobile bleeds | **hard-edged boxed rectangle** (TvDepartmentHero.kt:87-101 inside padded wrappers), visible in the photo | **bleed** (see B) |
| Profile initial | 6px (library-controls.css:7) | — | square 180×220 card (TvProfileTiles.kt:105) | **6dp tile, no card** |
| Search field | pill 999px (shell.css:113) | `rounded.full` (DESIGN.md:460-462) | square, 3dp border (TvTextField.kt:73,85) | **pill, 1dp rule**; other text questions 6dp (library-controls.css:12) |
| Dialogs | 6px (library-controls.css:62) | `Radius.control` (DESIGN.md:446-448) | square (TvConfirmDialog.kt:103, player/TvAddToListDialog.kt:121) | **6dp** |
| PIN keys | 6px input (library-controls.css:71) | control | square (TvPinPrompt.kt:162) | **6dp** |
| Accent swatches | circle (appearance.css:57) | mobile circle (AppearanceSection.kt:114-118) | square (TvAppearanceSection.kt:113) | **circle** |
| Artwork swatch cards | 12px (appearance.css:43) | `Radius.card` (SettingsControls.kt:149) | square (TvAppearanceSection.kt:142) | **12dp** |
| Player overlay buttons | 6px / round (player-card.css:72,108; playback.css:189) | — | square (player/TvOverlayButtons.kt:139) | **6dp** |
| "offline" badge | no box (catalog.css:196) | 4dp box (OfflineBadge.kt:29) | 4dp box, 18sp (TvPlateMarks.kt:103) | **no box** |
| Focus ring | follows the element: round on pills, square on a `.card` (catalog.css:151) | — | square unless a caller passes a shape | **follow each surface** |

---

## Gaps, worst first

### 1. One oversize type scale for every role · L
- **Screen:** every screen. In the photo, the tabs, "Continue your series", the card text and "offline" are all oversize. In tvs1 and tvst1 the eyebrow wraps.
- **Reference:**
  - Table A gives the web's sizes and their file:line.
  - The phone carries the same numbers (Type.kt:110-238).
  - Newsreader is used for decks, overview, quotes and the department line; Fraunces for plate names; Geist 600 for row heads.
- **TV now:**
  - 18sp and 34sp for everything (TvTheme.kt:80-91, Type.kt:254-276).
  - Labels are 1.2–1.3× the web, headings 1.6×, eyebrows and extents 1.45×.
  - No Newsreader anywhere (Type.kt:93 `internal`).
- **Fix:** expose the phone roles as plain `TextStyle`s in designsystem, map each call site per table A, and keep the display constants.
  - This also delivers most of gap 6 (the bar) and gap 3 (card text).
- **Files:** Type.kt, TvTheme.kt, and about 45 ui-tv files. The change is mechanical.
- **Composite:** `tv-audit/01-type-scale-series-web-vs-tv.jpg` (web Series page vs today's photo).

### 2. Square corners by default · M
- **Screen:**
  - Home strips, Continue cards, walls (tvpe1, tvse2), the profile picker (tvpr), search (tvse1), dialogs, the PIN pad and Appearance.
  - In the photo, the Continue cards.
- **Reference:** table C. The web rounds posters 3px, controls 6px, picture cards 12px and swatch dots fully. ui-mobile rounds home posters and Continue cards 3dp.
- **TV now:** `TvFocus.Shape = RectangleShape` is the default for `cardShape()`, `surfaceShape()` and `fieldBorder()` (TvFocus.kt:74). Fourteen surfaces take it.
- **Fix:**
  - Make the default `RoundedCornerShape(Radius.control)`.
  - Add a 3dp poster shape: the web's `.thumb` radius, which `designsystem.Radius` lacks.
  - Pass it at TvPosterStrip, TvRecentBand, TvResumeCard and TvCourseList, and at TvPlate after Q1.
  - Accent swatches become `CircleShape`, artwork swatches `Radius.card`, the search field `PillShape`.
  - The ring follows the shape automatically (TvFocus.kt:86-104, 139).
- **Files:** TvFocus.kt, Spacing.kt, and the 11 call sites in table C.
- **Composite:** `tv-audit/03-corners-plates-web-vs-tv.jpg`.

### 3. Continue cards: unreadable caption, oversize badge, progress bar through the text · S
- **Screen:**
  - Series department "Continue your series" (photo) and Home Continue.
  - "S1E2 · Der Fall Charly" has the bar crossing it. **This is a layout bug.**
- **Reference:**
  - 3px corners (home.css:242).
  - Name 14px, sub 12px (home.css:272-273).
  - The scrim sits under the caption block only, from 0.9 to 0 over the block's height (home.css:260-270).
  - The bar is 52% wide at the right on the sub's line, and the sub is capped at **42%** width so the two never meet (home.css:248-258, 273).
- **TV now:**
  - Square (TvResumeCard.kt:77).
  - 18/16sp text (TvResumeCard.kt:101,111). The caption is about 100dp of a 126dp card, so it climbs above the scrim, which only darkens the lower 60% (TvResumeCard.kt:89).
  - The "offline" box is 18sp (TvResumeCard.kt:124 → TvPlateMarks.kt:98).
  - The bar is pinned bottom-end at 52% (TvResumeCard.kt:126-137) with **no width cap on the sub**. Without a badge, the sub line sits under the bar.
- **Fix:**
  - 3dp shape.
  - 14/12sp text.
  - Cap the sub at `fillMaxWidth(0.42f)`.
  - Align the bar to the sub line, inside the caption column, as the web does.
  - The badge becomes 12sp sage text, no box (catalog.css:196-198). On this card, put it after the sub, so the bar never shares a line with it.
- **Files:** TvResumeCard.kt, TvPlateMarks.kt.
- **Composite:** `tv-audit/02-continue-cards-web-vs-tv.jpg`.

### 4. Department hero is a box inside the page, and the page has three left edges · M
- **Screen:** Series (photo, tvb1), Anime (tva1), Movies (tvb2).
- **Reference:**
  - The hero bleeds to the window's right edge and up under the translucent masthead (departments.css:11-12, 16). It fades into paper on the left, bottom and top only (departments.css:18-28).
  - Title, line and rows share the one gutter.
- **TV now:**
  - The art is drawn inside padded wrappers. It ends 96dp short of the right edge, starts below the bar, and shows a hard top and right edge.
  - The title sits 32dp right of the rows, and the rows sit 32dp right of the wall (table B).
  - The bar's blend (TvDepartmentsBar.kt:84-85) has nothing to blend over.
- **Fix:**
  - Draw the hero item edge to edge: no wrapper padding, negative top inset equal to `pagePadding.top`. TvHomeCover already does this.
  - Pad only its copy, once, by `pagePadding.start`.
  - Drop the duplicate start/end padding around header rows on TvWall-based pages.
  - Set the department line in Newsreader 20sp (table A).
- **Files:** TvDepartmentHero.kt, TvShowsDepartmentPage.kt, TvAnimeDepartmentPage.kt, TvMoviesDepartmentPage.kt, TvDocumentariesDepartmentPage.kt.
- **Composite:** `tv-audit/04-dept-hero-web-vs-tv.jpg`.

### 5. Focus reads as a thick outline that grows, or as an underlined link · M
- **Screen:** pills and buttons (tvpill, tv2, tvbar), plates (tvpe1, tvse2, photo), text rows (tv3 course, tvst2, tvs1 index, tvb2 rail).
- **Reference:**
  - **Web keyboard focus:** a 2px accent outline at a 4px offset (theme.css:170).
  - **Web hover:** a colour, border or fill change. Controls "never lift or grow"; only artwork lifts (web/DESIGN.md:230).
  - **Rows:** `.row:hover` gives a surface fill and an accent title, with no underline (catalog.css:186-187). Rail rows tint `rule-soft` (shell.css:54).
  - **TV's own precedent:** the player already drops the grow and uses a sunk fill with accent content (player/TvOverlayButtons.kt:120-155, docs/system-architecture.md:860-863).
- **TV now:**
  - A 3dp ring and a 1.08 grow on every control and card (TvFocus.kt:63-66).
  - The grow needs `PillRingRoom` scroll hacks, and it still clips the ring in the bar (TvDepartmentsBar.kt:101-110, 145-150).
  - Text rows and rail labels go accent-plus-underline (TvFocus.kt:177-182, TvTextRow.kt:51, TvIndexRow.kt:133).
- **Fix:**
  - **Controls** (pills, tabs, icon buttons, rows): the web hover fill plus a **2dp accent ring at 4dp offset**, with no scale. At 1dp = 2px, 2dp is twice the web's on-panel width, enough from 3 m.
    - Pill-line hover: 45% ink border and 10% ink fill (title-page.css:24).
    - Department pills: `rule-soft` fill (shell.css:54).
    - Rows: surface fill and accent title (catalog.css:186-187).
  - **Artwork** (plates, portraits, art tiles, feature cards): keep a grow, because the web lifts artwork. Put the ring around the art, with the name turning accent (catalog.css:157).
  - **No underline anywhere.** The web underlines prose links only.
- **Files:** TvFocus.kt, TvTextRow.kt, TvIndexRow.kt, TvChromeControls.kt, TvCoverActions.kt, TvTitlePills.kt, TvDepartmentsBar.kt (delete the ring-room code).
- **Composite:** `tv-audit/05-focus-web-vs-tv.jpg`.

### 6. Departments bar truncates and collides · S (after gap 1)
- **Screen:** photo ("Tutori"), tv2 and tvm1 ("Tuto"), tvbar ("Colle", ring cut at search), tva1 ("…vies").
- **Reference:** Geist 14px tabs, 10px counts, padding 10×12 below 1180px (shell.css:47,60,90,181). All seven fit beside search and the avatar at 1920.
- **TV now:** 18sp tabs, 13sp counts, 8×16dp padding (TvChromeControls.kt:67-74). The pills need about 970dp; about 660dp is available.
- **Fix:** 14/11sp and 10×12dp padding (tables A, B), which brings the pills to about 740dp. That is still over. The web itself overflows at a 960 window (`web960-series.png`), so the rest is **Q3**.
- **Files:** TvChromeControls.kt, TvDepartmentsBar.kt.
- **Composite:** `tv-audit/06-departments-bar-web-vs-tv.jpg`.

### 7. Plates sit on grey caption cards · S
- **Screen:** walls, person (tvpe1), search (tvse2), strips.
- **Reference:**
  - The name and meta sit straight on the page: no card fill, no side inset (catalog.css:151, 161-163).
  - The web DESIGN says "Don't draw cards, panels with backgrounds" (web/DESIGN.md:300).
  - The phone draws captions on the page too (PosterCard.kt:62-80).
- **TV now:** `containerColor = surface` (#151517) behind the poster and caption (TvPlate.kt:97, TvPosterStrip.kt). There is an 8dp inset (TvPlate.kt:106,124), and names are set in Geist.
- **Fix:**
  - Transparent container, so the art alone carries the shape.
  - Caption 12/4/0dp (table B), Fraunces 17 / 12sp (table A).
- **Files:** TvPlate.kt, TvPosterStrip.kt, TvRecentBand.kt.
- **Composite:** shared with gap 2 (`03-…`).

### 8. Rail opens over a third of the page, at oversize type · S
- **Screen:** tvb2 ("…VIES" cut off). The collapsed icons are deliberate (docs/system-architecture.md:884-888).
- **Reference:** the rail is 224px, with labels 14px, counts 10px, tally 10px caps and wordmark Fraunces 27px (shell.css:24-70).
- **TV now:** opens at 288dp (TvLibraryRail.kt:44) with 18sp labels, 13sp counts and 16sp tally.
- **Fix:**
  - Open at 224dp, the documented "web's own width".
  - Sizes per table A. The wordmark can go to 27sp at that width.
- **Files:** TvLibraryRail.kt, TvIndexRow.kt.

### 9. Lists drawn as bare text lines · M
- **Screen:**
  - Course and season rows (tv3).
  - Storage radio list and cache toggles (tvst1, tvst2).
  - Search filters (tvse2).
  - The "Who runs this household?" chooser (tvpr).
  - The course resume line.
- **Reference:**
  - **Course and season rows:** web `.row` with a number column, title, codec sub, meta at the right, a progress rule and a `rule-soft` divider (course-view.js:48-78, catalog.css:185-193). Module rows are folder rows with a count and chevron (`web-course.png`).
  - **Selects:** pills (title-page.css:143-153). The season picker already became a pill row on TV, deliberately (docs/system-architecture.md:915-921).
  - **Toggles:** DESIGN.md Toggle (DESIGN.md:677-683).
  - **Search filters:** `.search-filter` pills, with pressed = ink fill (catalog.css:259-263).
  - **Household chooser:** `.pill`s (library-controls.css:73-75).
- **TV now:**
  - All are `TvTextRow`s ("1. Greetings", "○ 512 MB", "● All · 42", "Use the home cache server — on").
  - In TvCollectionRow.kt:86, TvCacheBudgetBlock.kt:76-90, TvCacheVolumeBlock.kt:43, TvLanCacheBlock.kt:74-92, TvSearchResults.kt:193-206, TvPickerQuestions.kt:94-95, TvSeriesResume.kt:20.
- **Fix:**
  - Course and season rows become the web row.
  - Choices become a wrapping row of 40dp outline pills, pressed = ink fill.
  - Toggles take the design's Toggle.
  - The course resume line takes the series page's resume pill (TvTitlePills.kt:171).
- **Files:** the files listed above plus TvCollectionRows.kt.
- **Composite:** `tv-audit/07-course-web-vs-tv.jpg`.

### 10. Masthead eats 19% of the height · S
- **Reference:** 76px of 1080 (7%).
- **TV now:** 103dp of 540 (TvDepartmentsBar.kt:96).
- **Fix:** 83dp (table B). Every page under the bar gains 20dp.
- **Files:** TvLibraryChrome.kt, TvDepartmentsBar.kt.

### 11. Profile picker · M
- **Screen:** tvpr against `web-profile.png`.
- **Reference:**
  - 96px initial tiles at 6px radius in paper-sunk, with the name under them at 14px and the kids label in **held** (library-controls.css:5-10).
  - Hover is an accent tint plus accent letter (library-controls.css:9).
  - The household question is a Fraunces 21.6px heading over pills (library-controls.css:73-75).
  - "Manage profiles" and "Stay as I am" are `.quiet` 6px buttons (library-controls.css:18-19).
- **TV now:** 180×220 square cards with an 88dp circle and Fraunces initials (TvProfileTiles.kt:37-136). The kids label is in the accent. The question is a plain text row.
- **Fix:** per web, with focus per gap 5.
- **Files:** TvProfileTiles.kt, TvProfilePicker.kt, TvPickerQuestions.kt.

### 12. Settings · M
- **Screen:** tvs1, tvst1, tvst2.
- **Reference:**
  - `SectionHead` 16sp and `Ledger*` 14/15 (Type.kt:227-238) are tokens the phone already uses.
  - Index rows are Fraunces 16 / Geist 12 (DESIGN.md:618-628).
  - Swatches per table C.
- **TV now:** 34sp section titles (TvInfoBlock.kt:66), an 18/18 ledger, a 16sp eyebrow that wraps, Geist 18 index labels, and square swatches.
- **Fix:** tables A and C. The 260dp index (TvSettingsIndex.kt:32) can stay (the two-pane layout is deliberate).
- **Files:** TvInfoBlock.kt, TvSettingsIndex.kt, TvSettingsPanes.kt, TvAppearanceSection.kt.

### 13. Search page · S
- **Screen:** tvse1, tvse2.
- **Reference:** a pill field with a 1px rule (shell.css:108-128), with the query as the page head (`web-search.png`).
- **TV now:**
  - The field is a square with a 3dp border (TvTextField.kt:73-85).
  - The results list scrolls under the fixed field and filters. Row 1 shows captions with its posters cut off (tvse2), because the LazyColumn has no top inset (TvSearchResults.kt:108-121).
- **Fix:**
  - Pill field with a 1dp rule, turning accent when focused.
  - Scroll the field and filters with the list, as the web's head does, or wrap the list in `TvPage` so whole rows come into view.
- **Files:** TvTextField.kt, TvSearchResults.kt.

### 14. Title page details · S
- **Reference:** tabs 32px apart over a `rule-soft` line (title-page.css:120). The fact sheet has a 160px label column and 14px rows (title-page.css:139).
- **TV now:** tabs 16dp apart with no line, a 220dp fact-label column, 8dp rows (TvSectionTabs.kt:101; TvFactSheet.kt:83-86).
- **Fix:** table B.
- **Files:** TvSectionTabs.kt, TvFactSheet.kt.

### 15. Cover "+ My List" and "Details" · S
- **Reference:**
  - The line pill over art has a 1px border at 40% on-image (home.css:75).
  - "Details" is underlined at rest with a 35% underline (home.css:77-84).
- **TV now:** the line pill has a fill but no border (TvCoverActions.kt:116-120). "Details" is a plain text row (TvCoverActions.kt:90).
- **Fix:** per web.
- **Files:** TvCoverActions.kt.

### 16. Loading state · S
- **Screen:** tv1.
- **Reference:** a dark, cover-shaped block with the sentence at the lower left (shell.css:175-177).
- **TV now:** a centred Geist 18sp sentence on an empty page, with a bar that has no pills.
- **Fix:** the cover-shaped block, with the sentence in Newsreader 17.
- **Files:** TvCatalogBody.kt:53 → TvHeadings.kt:167.

**Count:** 16 gaps: 9 S, 6 M, 1 L.

## Composites (reference left or top, TV right or bottom)

All in `/tmp/claude-1000/-home-andre-Workspace-mediagram/35591186-acb4-4a07-9b7f-c6e887a63688/scratchpad/tv-audit/`:

| # | Gap | File |
|---|---|---|
| 1 | Type scale | `01-type-scale-series-web-vs-tv.jpg` (the photo vs the web's Series page) |
| 2 | Corners and plates | `03-corners-plates-web-vs-tv.jpg` |
| 3 | Continue cards | `02-continue-cards-web-vs-tv.jpg` |
| 4 | Dept hero and edges | `04-dept-hero-web-vs-tv.jpg` |
| 5 | Focus | `05-focus-web-vs-tv.jpg` |
| 6 | Departments bar | `06-departments-bar-web-vs-tv.jpg` |
| bonus | Course rows | `07-course-web-vs-tv.jpg` |

## Fix order: four batches, each one visible change on the box

1. **"Text at the web's size."**
   - Gap 1, the type roles. Gap 3's text, width cap and bar placement, because it is a bug and lives in the same lines. Gaps 6 and 8.
   - On the box: the bar fits or nearly fits, the cards are readable, "Continue your series" drops to 21sp, and the settings eyebrow stops wrapping.
2. **"Rounded and flat."**
   - Gaps 2, 7 and 13 (field shape), and the radius, badge and swatch rows of table C.
   - On the box: posters and cards rounded, captions on the page, no grey blocks.
3. **"One gutter, bleeding heroes."**
   - Gaps 4, 10 and 14, plus the wall gaps in table B.
   - On the box: the heroes run to the edge and under the bar, every page has one left edge, and there is 20dp more room.
4. **"The web's hover is the TV's focus."**
   - Gaps 5, 9, 11, 12, 15 and 16.
   - On the box: a thin offset ring with a fill, no growing pills, and lists that read as rows and pills.

## Already right (don't redo)

- **Display sizes:** cover 61, department 81.6, spread 59.5, shelf head 42 and franchise 52.8sp are the web at the TV's 960dp width.
- **Home cover:** it bleeds under the bar and to the edge, and the bar blends from translucent to opaque (TvHomeCover.kt, TvCoverSlide.kt). Its dots are 20×4 at 2dp, like web `.cover-dot`.
- **Rounded already:** feature cards and art tiles at `Radius.card` (TvFocus.kt:83, TvArtTile.kt:62); rail rows at 6dp (TvIndexRow.kt:95). Pills, the avatar, icon buttons, cast portraits and search people are full circles.
- **Margins and rhythm:**
  - The gutter after the rail (32dp) matches the web at 960 (31px).
  - Overscan is 48/27dp.
  - Strip and resume gaps are 16dp; home bands are 32dp apart, against the web's 28-36px.
- **Title page:** pills 48dp tall with a 12dp gap (TvTitlePills.kt:203-206). Tab underline style. Resume card proportions 16:8.4.
- **Palette and accent:** the TV reads `Palette` and `Accent`, and stays dark, deliberately.
- **Documented TV differences, kept:** every entry in docs/system-architecture.md:845-960 stands:
  - pushed pages full screen;
  - the collapsed rail;
  - fixed 360/380dp heroes;
  - tabs switching on OK;
  - the season pill row;
  - the course resume line;
  - the player not growing on focus.

## Open questions (only where the reference is ambiguous for TV)

1. **Plate corners.** The web rounds every poster 3px (catalog.css:158). The Android DESIGN.md says a plate stays square, "Don't round a plate's corner" (DESIGN.md:85,212,449,812). ui-mobile follows that on walls but rounds home rows 3dp. Under Surface Parity the web wins, but that line looks like a deliberate decision. Should TV walls go to 3dp, with DESIGN.md updated, or stay square?
2. **Display type.** At the 960 rule, "SERIES" is 81.6sp, the web's own size at a 960 window. That is 12% of the screen height, against 8% on the 1920 web. Keep it, or halve the 1920 sizes (60 / 56 / 46 / 29sp: department, cover, spread, shelf head) so the proportions match the desktop?
3. **The bar at 960dp.** Even at the web's sizes, seven pills with counts, search, the avatar and ⋮ need about 740 of about 660dp, and the web itself overflows at a 960 window. Which should give way?
   - Drop the counts on TV.
   - Move ⋮ into the rail or the avatar menu.
   - Keep the scrolling row and fade its clipped edge.

## User decisions (2026-10-09, binding for the batches)

- Posters round like the web (3dp); update the Android DESIGN.md and phone walls to match.
- Display type at desktop proportions: department 60sp, cover 56sp, spread 46sp, shelf heads 29sp.
- Departments bar: drop the counts on TV, move ⋮ into the avatar menu, scroll with a faded clipped edge.
- Batch by batch, each checked on the box.
- **No orange focus ring on buttons** ("this orange focus ring on buttons looks weird"). Overrides gap 5's "2dp accent ring at 4dp offset" for controls: pills, tabs, icon buttons, cover actions and rows take focus as a **fill**, the web's hover fill made strong enough to read from 3 m, following the TV player's own precedent (sunk fill + accent content, `player/TvOverlayButtons.kt:120-155`). No outline, no grow. Artwork (plates, posters, cards) keeps the web's lift; any ring around artwork is neutral ink, not accent — check with the user on the box.
