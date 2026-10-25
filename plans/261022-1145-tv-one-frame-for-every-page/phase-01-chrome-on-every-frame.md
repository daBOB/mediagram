# Phase 1: chrome on every library frame

## Behaviour

- `TvLibrary` wraps every frame but PLAYER, MENU (settings screens) and the avatar's menu page in one `TvLibraryChrome`, outside the `when`, so it stays put while frames change.
- Lit pill, the web's `sectionOf` (`address.js:122`): a show/course → its department; Movies page → Movies; franchise/list → Collections; everything else none. Lit rail row: Latest, Genres, Stats on their own pages.
- A pill or a My List/Continue/Latest/Genres/Stats row on a pushed page returns to the catalogue and runs there (the existing `pendingRail` path, plus a pending tab). Settings/System open over the page, as now. Search opens over the page.
- Back anywhere on a pushed page closes it (the frame's own `BackHandler`); the chrome's root Back chain is off there.
- Pages read `LocalTvPagePadding`; the title spread bleeds under the bar (art from y=0, words below the bar).

## Todo

- [x] `TvLibraryChrome`: param to turn off the root Back chain
- [x] `TvLibrary`: frame chrome + selection rule + pending tab
- [x] `TvCatalogScreen`: run a pending tab
- [x] Title spread, title/series/course pages: page padding instead of `Overscan`
- [x] Robolectric (`TvFrameChromeTest`, 6): a pushed page has rail + bar; Back closes it; Left reaches the rail; Up reaches the bar

## Risk

Focus: Up/Left exits and the rail's Right on pages without a focus restorer. Covered by tests above.

## Notes

- Back from search or the avatar's menu opened over a page lands on the page's own first stop, not the bar's button (`shortcut:` in `TvLibrary.kt`).
