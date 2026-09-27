# Phase 02 — Home body: cover, features, bands, shelves

## Context links

- Web reference: `web/public/lib/catalog/home-view.js:78-114`, `home-cover.js`,
  `home-features.js`, `home-resume.js`, `home-break.js`, `home-shelves.js`,
  `editorial-picks.js:20-138`, `shelf-view.js:227`, `sections.js:11`;
  styles `web/public/styles/home.css:6-360`, `catalog.css:32-105`,
  `title-page.css:6-20`, `theme.css:76-203`.
- Android today: `ui/catalog/HomeScreen.kt:48-199`, `CoverStory.kt:57-185`,
  `FeatureStrip.kt:53-139`, `ResumeStrip.kt:46-135`, `PullQuote.kt:21-46`,
  `PosterRow.kt:33-56`, `PosterCard.kt`, `DepartmentHero.kt`;
  data `feature/catalog/{MagazineHome,EditorialPicks,HomeShelves}.kt` (unchanged
  unless a field is missing — the picks already mirror the web's).
- designsystem: `Type.kt` (Display = Fraunces variable, `Eyebrow`, `PageTitle`),
  `Backdrop.kt`/`LocalBackdrop`, ui-common `HeroArtwork.kt`.
- Phase 01's seam: the top-chrome height the home draws under.

## Overview

Priority P2 · pending. Rebuild the home tab's sections to the web's layout and type.
Data selection already matches the web (cover ≤5 seeded daily, lead/trending/staff,
quote, this month, latest series/courses); this phase is layout, type and actions.

## Web spec (px → dp; `W` = window width in dp; gutter = clamp(16, 3.2%·W, 56))

**Cover**
- Full bleed across the content column (right of the rail), drawn under the status bar
  and the departments bar. Height clamp(620, 63.5% of window height, 705) on
  EXPANDED; compact: at least 84% of window height, grows with content.
- Image: backdrop, `ContentScale.Crop`, focus ≈ 68% x / 28% y. Honour `LocalBackdrop`
  (SOLID → plain `paper`; BLURRED via `HeroArtwork`) — `CoverStory` ignores SOLID today.
  Slides crossfade ~1.4 s; optional slow 1.06→1 zoom.
- Scrim, EXPANDED: left→right paper@0.94 → 0.78 at 26% → 0.24 at 56% → 0 at 76%;
  bottom: paper → transparent by 26% height; top: black@0.55 → 0 by 22%.
  Compact: one vertical gradient (transparent top → paper bottom).
- Text block bottom-left at the gutter, 40dp bottom padding, max width
  min(736dp, 60% of column) (72% when W ≤ 1180; 100% compact):
  - Eyebrow "FEATURED TODAY · {first genre}" (`Eyebrow`: Geist 500 11sp, 0.32em,
    uppercase), 22dp below. (Android says "Cover story · genre" today.)
  - Title: Fraunces **600**, opsz 144, **uppercase**, tracking −0.035em, line height
    0.86; size clamp(48sp, 6.4%·W, 112sp); titles over 18 chars step down to
    clamp(38.4sp, 4.4%·W, 73.6sp) with line height 0.9. Wraps (no auto-size), so a
    plain `Text` with an explicit `FontVariation.Settings(weight 600, opsz 144)`
    font works — `TextAutoSize` is what lost the axes before. If a static cut is
    needed instead, cut it like `fraunces_page_title.ttf` and note it in Type docs.
  - Deck (tagline, if any): Newsreader 400, clamp(17.6, 1.4%·W, 20.8)sp / 1.4,
    ≤34ch, 28dp above.
  - Meta: `year · 1h 46m · FSK 12 · ★ 5.6`, Geist 13sp, tracking 0.06em.
  - Buttons, 34dp below meta, 12dp gap; all pills: min height 48dp, padding 0×24,
    fully round, Geist 500 15sp:
    **Watch now** = filled `on-image` (#f6f2ea) with #0a0a0b text and a 10×12 play
    triangle — **plays** the film (today it opens the title; the web plays);
    **+ My List** = outline on-image@40%, fill rgba(8,8,9,.28), toggles the
    watchlist, reads "✓ My List" when on; **Details** = text link 14sp underlined,
    16dp left margin → title page.
- Pager (more than one film): bottom 18dp, right at gutter; 32dp round pause
  button ("‖"/"▶", only while rotating) then one 28dp tap target per film drawing a
  20×4dp bar, radius 2, on-image@32% / on-image when current. Rotate every 9 s;
  pause while touched/dragged or focused.
- The web's right-hand genre column is hidden ≤1180px — skip it (tablet is 1164dp).

**Features** (12dp below the cover)
- EXPANDED (web ≤1180 = the tablet): 2 columns, 12dp gap, first card full width min
  height 360dp, other two min height clamp(220, 16%·W, 270). Wider than 1180dp: three
  equal columns. Compact: one column, min height 280dp.
- Card: radius 14dp, fill #141416, image focus 60%/30% (poster fallback 50%/18%);
  scrim left→right black@0.88 → 0.52 at 48% → 0.08, plus bottom 0.7 → 0 by 55%.
  Text bottom-aligned, max width 416dp, padding 36dp vertical × clamp(24, 2.6%·W, 40).
- Eyebrow label ("Editor's choice" / "Trending on TMDB" / "Staff pick" /
  "New in the library" — from `FeatureStrip.kt:32-38`), then title Fraunces 600
  clamp(28.8, 2.8%·W, 46.4)sp / 0.98, tracking −0.025em, **uppercase — except the
  second card**, which keeps its own case at clamp(32, 3%·W, 49.6)sp.
  Then a 32×1dp rule (20dp above, 16dp below), then the deck: Newsreader 16sp/1.4,
  ≤416dp — tagline, else first three genres joined ", ".
- Today's under-image captions (year · rating lines) go.

**Continue band** (28dp above)
- EXPANDED: `Row` = Continue (weight 2.6) | quote (weight 1, min 256dp), gap
  clamp(32, 4%·W, 72). One missing → the other spans. Compact: stacked, quote below.
- Heading row: "Continue Watching" (Geist 600 20.8sp/1.2, −0.01em), no count;
  "See all →" (13sp, ink-2, 44dp tall) → the Continue kept tab.
- Cards: horizontal row, 4 visible in the band width (min 208dp), 16dp gap, snap;
  compact: 78% of width each. Aspect 16:8.4, radius 3dp; backdrop → poster →
  initial. Over the image, bottom gradient black@0.9 → 0.6 at 60% → 0: title 14sp
  Geist 500, subtitle 12sp ("S1E4 · episode" / "1995 · 2h 4m") at ≤42% width, and a
  progress bar right 12dp / bottom 16dp, 52% wide, 3dp, track on-image@22%, fill
  #6fb7e8. Tap → **play** (web: `home-view.js:78-81`). Up to 12 (6 half-watched +
  6 next-up) — check `MagazineHome.resumeCards` gives the same.
- Quote: "“" Fraunces 600 64sp hung left; text indented 54dp, Newsreader italic
  clamp(25.6, 2.3%·W, 37.6)sp / 1.2; 32×1dp rule; attribution "TITLE, YEAR"
  Geist 500 11sp, 0.28em, uppercase, quiet, title part ink-2 → opens the film.

**Second band** (28dp above): Recently Added | This month, same 2.6fr | 1fr split.
- Recently Added: heading with count = films (13sp quiet, 10dp after), "See all →" →
  Movies tab; one horizontal poster row, **no captions**, up to 8.
- This month: up to 5 films added in the last 30 days not on the row: "01"… in
  Fraunces 20.8sp, name Fraunces 500 15sp, meta "year · genre" 12sp; left 1dp
  ruleSoft line, 28dp left padding. (`EditorialPicks.thisMonth` exists.)

**Latest series** (36dp above): heading + count (= shows), "See all →" → Series tab.
Posters 2:3, radius 3dp, width max(136dp, (column − 98dp)/8), 14dp gap, one
scrolling row, up to 8; caption name Fraunces 500 17sp/1.25 (14sp compact), meta
"21 episodes · three seasons" 12sp quiet (reuse the phrasing helper the series wall
uses).

**Latest courses** (36dp above): heading + count, "See all →" → Tutorials; a list
(no artwork): initials tile, name, "eight lessons · one chapter" — read the web's
course-row markup/CSS in `home-shelves.js`/`catalog.css` for exact sizes.

**Page**: gutter both sides; bottom padding 96dp (64 compact). Section heads Geist,
not Fraunces (today's "Recently added · 935" serif heading + hairline goes).

## Architecture

- `HomeScreen` becomes a `LazyColumn` of full-width sections (the poster grid-in-grid
  goes; rows are horizontal `LazyRow`s). Its scroll state is hoisted to phase 01's
  layout for the bar blend.
- New `ui/catalog/home/` files, one per section: `HomeCover.kt` (replaces
  `CoverStory.kt`), `HomeFeatures.kt` (replaces `FeatureStrip.kt`),
  `ContinueBand.kt` (resume cards + quote; replaces `ResumeStrip`/`PullQuote` on
  home — keep `PullQuote` if `HeroBackdropModesTest`/other screens still use it),
  `RecentBand.kt`, `HomeShelfRow.kt`, `CourseList.kt`, `HomeType.kt` (the cover and
  feature title styles, fluid sizes from `W`). Delete what nothing uses afterwards.
- Fluid sizes: one tiny helper `fluid(min, fraction, max, width)`; unit-tested.

## Related code files

- Modify: `ui/catalog/HomeScreen.kt`, `ui/catalog/CatalogScreen.kt` (home inputs),
  `designsystem` only if a font/style must live there.
- Create: `ui/catalog/home/*` above.
- Delete: `CoverStory.kt`, `FeatureStrip.kt`, `ResumeStrip.kt` once unused.
- Tests: new Robolectric tests at `w1164dp-h777dp` and default compact: section order,
  cover eyebrow/title uppercase, Watch now plays, My List toggles, 2nd feature not
  uppercased, Continue "See all" lands on the Continue tab, SOLID backdrop honoured
  on the cover. Update `HeroBackdropModesTest` if it referenced moved composables.

## Implementation steps

1. `fluid()` + test; `HomeType` styles (verify the 600/opsz144 renders on device —
   Fraunces default instance is 900/opsz 9, the classic trap).
2. `HomeCover` (under the chrome, pager, buttons, actions wired to play/watchlist).
3. `HomeFeatures`, `ContinueBand`, `RecentBand`, `HomeShelfRow`, `CourseList`.
4. `HomeScreen` as a `LazyColumn`; hoist scroll state.
5. Tests; `:ui-mobile:testDebugUnitTest :feature:catalog:testDebugUnitTest` green.
6. Tablet (`ANDROID_SERIAL=caad49da`, test profile — plays land in Continue, use the
   test profile only) screenshots at the web's three scroll depths; put them side by
   side with `home-web-*.png` in `reports/`.
7. Patch bump (0.70.1), changelog, commit.

## Todo

- [ ] fluid() + HomeType, weight/opsz verified on device
- [ ] HomeCover (scrim, eyebrow, uppercase title, meta, three buttons, pager)
- [ ] HomeFeatures (wide + two on tablet, uppercase rule)
- [ ] ContinueBand (cards over image, quote beside)
- [ ] RecentBand (posters no captions + This month)
- [ ] Latest series row with captions, Latest courses list
- [ ] HomeScreen LazyColumn, scroll hoisted
- [ ] tests green, old composables deleted
- [ ] tablet side-by-side shots
- [ ] 0.70.1, changelog, commit

## Success criteria

- Side-by-side with `home-web-{1,2,3}.png` at the same depths: same sections, order,
  type (uppercase Fraunces cover title, Geist heads, Newsreader decks), buttons and
  proportions; differences listed in the report with a reason.
- Watch now and Continue cards play; + My List toggles; every "See all" lands.

## Risks

- Fraunces variable axes in Compose: confirm on device, not only in Robolectric.
- LazyRow-in-LazyColumn nested scrolling on the tablet: horizontal rows must not steal
  vertical flings.
- `resumeCards` count/order may differ from the web's 6+6 — fix in `MagazineHome`
  (shared with TV: run ui-tv tests) only if it actually differs.

## Security

None.

## Next

Phase 03 adds the Documentaries department the bar already has room for.
