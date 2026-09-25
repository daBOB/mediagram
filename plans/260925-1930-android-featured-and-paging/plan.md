---
title: "Android: Featured reel and paged Movies shelf"
status: done
branch: feat/android-featured-and-paging
created: 2026-09-25
---

# Android: Featured reel and paged Movies shelf

Two web features the phone never got (0.43.0 changelog: "Android has no
equivalent: its catalog carries no posters" / "does not page yet"). The first
reason stopped being true once the phone fetched its own posters. Web is the
reference (CLAUDE.md § Surface Parity).

## Ported
- [x] `featured-picks.js` → `catalog.pickFeatured`, `stepFrom` (`FeaturedPicks.kt`), web cases in `FeaturedPicksTest`.
- [x] `pager.js` → `catalog.pageOf`, `pageLinks`, `FILMS_PER_PAGE = 48` (`FilmPages.kt`), web cases in `FilmPagesTest`.
- [x] `featured-reel.js` → `ui.catalog.FeaturedReel` / `FeaturedSlide`: 7 s hold, 1.2 s crossfade, drift, blurred backdrop (API 31+, dimmer below), count, title, year · genres · score, tagline, Play, Details, ‹ dots ›, ✕.
- [x] Movies shelf: `ShelfBar` ("page n of m", Featured, List · Grid), `ShelfPager` under grid and list, new page scrolls to top.
- [x] Shelves keep tab/page/scroll under a title (`SaveableStateHolder` in `LibraryBranches`) — the web's back button returns to `#/movies/page/n`; before, the phone returned to Home.
- [x] Docs, changelog, 0.54.0.

## Deliberate differences
- Space pauses on the web; a tap on the poster pauses on the phone.
- The page lives in the address on the web, in the shelves' saved state on the phone.
- Reduced motion: the web's `prefers-reduced-motion`; the phone's animator
  duration scale of 0.

## Device run (2026-09-25, tablet, `andre` profile, nothing played)
- Movies: "page 1 of 18", Featured, List · Grid; pager "‹ Prev 1 2 … 18 Next ›"; Next → page 2 at its top.
- Featured: 12 films, ~7 s per slide measured over 50 s, tap on poster held slide 9 for 10 s, Details closed the reel and opened the film; back returned to Movies page 2.
- Fixed during the run: the dot row overlapped the poster (foot clearance added); the title used a sans style (now `headlineSmall`, the display face).
