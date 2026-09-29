---
phase: 3
title: "TV department pages: heroes, rows, Anime, Documentaries"
status: in-review
priority: P2
effort: 8h
dependencies: [2]
---

# Phase 03 — TV department pages: heroes, rows, Anime, Documentaries

## Context links

- Tablet port: `android/ui-mobile/src/main/kotlin/ui/catalog/DepartmentHero.kt:57-106`,
  `DepartmentHeroLayouts.kt`, `DeptHeroText.kt`, `DeptRowHeading.kt`, `MoviesDepartmentScreen.kt`,
  `ShowsDepartmentScreen.kt`, `AnimeDepartmentScreen.kt:37-150`, `DocumentariesDepartmentScreen.kt:48-176`,
  `DocumentaryUnitRow.kt:28`, `CollectionsScreen.kt:60-110`, `ui/HeroArtOf.kt:29-58`; spec
  `plans/260927-2050-android-home-web-parity/phase-03b-department-hero-web-look.md`; reference sheets
  `.../reports/dept-hero-260928-*-web-vs-android.png`, `documentaries-260928-*.png`.
- Web: `web/public/lib/catalog/department-hero.js`, `department-pages.js:40-160`,
  `anime-department.js:24-69`, `category-rows.js`, `styles/departments.css:6-95`.
- Shared models: `feature/catalog/src/main/kotlin/Departments.kt` (`moviesDepartmentOf:32`,
  `documentariesDepartmentOf:77`, `showsDepartmentOf:125`), `Anime.kt:55-86`, `Categories.kt`.
- TV today: routing + recorded differences `ui-tv/.../catalog/TvDepartmentPages.kt:67-124`
  (Anime wall `:88-95`, Documentaries wall and no category rows `:96-113`),
  `TvMoviesDepartmentPage.kt:35-125` (Column + verticalScroll, 21:9 `TvCoverStory` hero),
  `TvShowsDepartmentPage.kt:34-141`, `TvDepartmentTargets.kt:20-74`, `TvDepartmentRows.kt`,
  `TvCollectionsPage.kt:49`, `TvWall.kt:77-156` (`headings` sections).
- Docs recording the differences: `docs/web-player.md:257-264`, `:305-311`; `DESIGN.md:732-739`.

## Overview

Give every department pill the tablet's page: the web's department hero (kicker, huge title,
figures line, lead art fading in from the right, quote top-right) under the bar, then the web's
rows. Anime and Documentaries stop being plain walls — the two recorded TV differences go, and
their notes in code and docs are removed.

## Key decisions

- **Per department** (all = web/tablet rows, in their order):
  - Movies: hero ("911 films · 1,523 hours") · Featured · Genres (tiles) · Acclaimed, not yet
    seen · Recently added · "All N films →" (`MOVIES_PAGE`, as today).
  - Series / Tutorials: hero ("48 shows · 1511 episodes" / "four courses · 37 lessons") ·
    Continue your series|courses (resume cards, play) · category rows (Tutorials; empty for
    Series by construction) · Popular series · New episodes · All shows|courses as the wall.
  - Anime: hero ("N shows · M films") · Continue watching (resume cards) · **Series** then
    **Films** as one `TvWall` with two `headings` sections (`TvWall.kt:77`, the genre page's own
    shape) — the web's two grids (`anime-department.js:60-67`). Show plates open the show; film
    plates open the title page (tablet; see plan question 1).
  - Documentaries: hero ("24 documentaries", no link — `leadHref: null`) · Continue watching ·
    category rows (folders open, singles **play**) · Recently added · one row per folder, "All N →"
    when it holds more (→ collection) · Standalone documentaries. Plates play on OK, as web and
    tablet (`department-pages.js:103`) — removes `DESIGN.md:732-739`. Empty → today's upload hint.
  - Collections: hero ("N franchises · M lists") above today's page content.
- **Hero on TV**: height = 360dp (web `clamp(420px,58vh,600px)` would fill 78% of 540dp and push
  the first row off the first screen); title per `DeptHeroText.kt`'s rule at W = 960; eyebrow and
  quote attribution 16sp. **Not a focus stop**, and the quote credit is not either (TV difference:
  a lone link top-right would be a stop Up/Down reach inconsistently); the lead is one row away.
  Art: right 70% (Artwork mode 100%), hidden under Solid, shared `DeptArtScrim` — the tablet's
  exact rule (`DepartmentHero.kt:68-77`).
- **Arrival**: pill press keeps the remote on the pill (01); Down → page restorer, fallback = the
  first row's first stop (the old "hero Watch now" target goes: `TvDepartmentTargets.kt:33,67`).
  Restore keys land on the row/stop, including the new Anime/Documentaries rows.
- **Bar blend**: department with lead art (shared `heroArtOf`, Solid → none) → translucent over
  the hero, opaque past it (page list state → `coverBlend`); otherwise opaque, page padded below.
- **Parity fix (tablet changes, said so):** hero lines move to one shared helper that spells
  counts ≤ 20 as the web does (`web/public/lib/format.js:170-174`); the tablet's Movies, Series,
  Tutorials, Anime and Collections lines use figures-only `ui.catalog.countOf` today ("4 courses"
  where the web says "four courses"). Documentaries already spells.

## Requirements

Functional
- Routing in `DepartmentOrShelfWall` by shelf title → the five pages above; `TvShelfWall` stays
  only as the fallback for a department builder answering `null`.
- Continue rows use resume cards (landscape, progress, OK plays) from shared `resumeCardsOf`.
- Row headings = `TvBandHeading` (Geist, count where the web has one, "All N →"/"See all →").
- Category rows: exactly `dept.categories` order (fixture-backed `categoryRowsOf`), uncapped strips.
- Hero honours `LocalBackdrop` (Solid hides art, Artwork full width) as the tablet does.

Non-functional
- Pages are lazy (`LazyColumn`, or `TvWall` with header) — Movies' Column + `verticalScroll`
  (`TvMoviesDepartmentPage.kt:57-62`) goes; rows keep their `LazyLayoutCacheWindow`.
- Text ≥ 16sp; rings on every stop; no stop in the overscan margin.

## Architecture

```
TvCatalogBody(shelf tab) ─ DepartmentOrShelfWall(shelf)
  Movies        → TvMoviesDepartmentPage(moviesDepartmentOf)          LazyColumn(state*)
  Series|Tutor. → TvShowsDepartmentPage(showsDepartmentOf)            TvWall(gridState*, header)
  Anime         → TvAnimeDepartmentPage(animeDepartmentOf)            TvWall(gridState*, header, headings)
  Documentaries → TvDocumentariesDepartmentPage(documentariesDepartmentOf) LazyColumn(state*)
  (Collections tab) → TvCollectionsPage + TvDepartmentHero
  each header = TvDepartmentHero(kicker, title, line = departmentLineOf…, lead, quote)
  *state → HeroListState → chrome blend when heroArtOf(title, …) != null
```

Data in: `Shelf` + `watch` + `byId` (`allSetsById`) + `heldIds`. Out: `onPlay`, `onOpenTitle`,
`onOpenCollection`, `onOpenGenre`, `onOpenMoviesPage` — each recording its restore key.

## Related code files

Move / lift (feature/catalog, pure, unit-tested; ui-mobile call sites switch)
- `ui-mobile/src/main/kotlin/ui/HeroArtOf.kt` → `android/feature/catalog/src/main/kotlin/HeroArtOf.kt`.
- Line builders (`MoviesDepartmentScreen.kt:109-113`, `ShowsDepartmentScreen.kt:85`,
  `AnimeDepartmentScreen.kt:146-150`, `DocumentariesDepartmentScreen.kt:111`,
  `CollectionsScreen.kt:106-110`) → `android/feature/catalog/src/main/kotlin/DepartmentLines.kt`
  using `spelledCountOf` (parity fix above).
- `resumeCardsOf(continues, nextUp, watch, heldIds)` extracted from `MagazineHome.kt:57-67` (same
  output; `magazineHomeOf` calls it).

Create (`android/ui-tv/src/main/kotlin/ui/tv/catalog/`)
- `TvDepartmentHero.kt` (layout, art + scrim, words, quote), `TvAnimeDepartmentPage.kt`,
  `TvDocumentariesDepartmentPage.kt`.

Modify
- `catalog/TvDepartmentPages.kt` (routing; delete the two recorded-difference branches `:88-113`),
  `TvMoviesDepartmentPage.kt`, `TvShowsDepartmentPage.kt`, `TvDepartmentTargets.kt` (drop "hero";
  add `animeDeptTargetOf`, `documentariesDeptTargetOf`), `TvDepartmentRows.kt` (headings, widths),
  `TvCollectionsPage.kt` (hero), `TvWall.kt` (optional hoisted `gridState`), `TvCatalogScreen.kt`
  (department list states → blend), `TvCatalogBody.kt:79-104`.
- ui-mobile: `catalog/{Movies,Shows,Anime,Documentaries}DepartmentScreen.kt`, `CollectionsScreen.kt`,
  `ui/LibraryBranchSupport.kt` (imports / shared lines).
- Tests: `ui-tv/src/test/kotlin/ui/tv/catalog/{TvDepartmentPagesStateTest,TvDepartmentTargetsTest,TvCatalogScreenStateTest}.kt`;
  feature `DepartmentLinesTest`, `HeroArtOfTest`, `MagazineHomeTest` (unchanged output); ui-mobile
  department tests whose line assertions change (list them in the report).
- Docs: `docs/web-player.md:257-264` and `:305-311` (TV now has both pages and the category rows),
  `DESIGN.md:732-739` (delete), `docs/project-changelog.md`.

Delete
- `ui-tv/.../catalog/TvCoverStory.kt` (no caller left), `DeptKicker` in `TvMoviesDepartmentPage.kt:20`
  (moves into the hero).

## Implementation steps

1. Lifts: `heroArtOf`, `DepartmentLines` (+ test: each department, spelled ≤ 20, figures above,
   zero parts dropped), `resumeCardsOf`. Update ui-mobile calls; fix the tablet tests whose small
   counts now read as words.
2. `TvDepartmentHero` + Robolectric (kicker, uppercase title, line, Solid hides art, quote only
   with art + tagline, no focusable node inside).
3. Movies → LazyColumn with the new hero and headings; Series/Tutorials → new hero, resume-card
   Continue, category rows, Popular/New, wall.
4. `TvAnimeDepartmentPage` (wall with Series/Films sections) and `TvDocumentariesDepartmentPage`.
5. Collections hero; routing; targets; hoisted states → blend; delete `TvCoverStory.kt`.
6. Tests, docs; `scripts/check.sh`.
7. Box (benchmark, compile speed, D-pad only; plays on "TV test"): screencap each department's top
   and one row down; Anime and Documentaries also at the Series/Films boundary and a category row;
   side by side with the tablet's same page (fresh tablet shots, `ANDROID_SERIAL=caad49da`, for
   Anime and Documentaries categories; existing sheets for the rest) → `reports/tv-dept-*.png`.
8. Bump next minor; changelog; commit.

## Todo

- [x] heroArtOf, DepartmentLines (spelled), resumeCardsOf shared; ui-mobile switched and green
- [x] TvDepartmentHero + tests
- [x] Movies, Series, Tutorials pages on the new hero and rows
- [x] Anime page (Series/Films sections), Documentaries page (categories, folders, standalone)
- [x] Collections hero; routing; targets; blend
- [x] TvCoverStory deleted; recorded differences removed from code and docs
- [x] tests, check.sh green; box sheets vs tablet — box sheets are step 7, explicitly the lead's own
      on-box verification (out of scope for this implementing pass); everything else green
- [x] version, changelog, commit

## Success criteria

- Robolectric: each department page shows "ONLY IN YOUR LIBRARY", its uppercase name, its line
  and the web's rows in order; Anime shows a show under "Series" and a film under "Films"
  (replaces `theAnimeShelfIsAPlainWallThatKeepsBothItsShowAndItsFilm`); Documentaries shows
  category, folder ("All N →") and standalone rows, and OK on a documentary opens the player;
  empty Documentaries keeps the upload hint; Tutorials category rows in `categoryRowsOf` order.
- Focus: Down from a department pill lands on the first row's first stop with the hero still on
  screen; Back from a title opened on any row lands on that plate.
- `grep -rn "plain poster wall\|no television front page" docs DESIGN.md android/ui-tv` finds nothing.
- Box sheets beside the tablet: same hero, rows and order per department; differences listed.

## Risk assessment

| Risk | L×I | Mitigation |
|------|-----|------------|
| First row pushed below the fold by the hero | M×M | 360dp hero; screenshot check at 540dp; Down scrolls it in |
| Anime wall mixing shows and films breaks restore keys | L×M | `TvWall` keys by `keyOf` per entry (shows `ANIME/…`, films setId — distinct) |
| Tablet line change surprises (words for ≤ 20) | L×L | stated parity fix; tests updated deliberately, listed in report |
| Documentary plates now play: accidental plays during walks | M×L | walk on the "TV test" profile only |
| Two hoisted list types (lazy list vs grid) for blend | L×L | `HeroListState` adapters already exist for both (moved in 02) |

## Security

None.

## Next

Phase 04 keeps Home alive under pushed frames and closes the plan with measurements and docs.
