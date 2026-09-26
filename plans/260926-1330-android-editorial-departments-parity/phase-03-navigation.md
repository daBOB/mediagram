# Phase 3 — Navigation

**Status:** pending. Web reference: `web/public/index.html` (0.62.1: each link once).

- Masthead tabs (`CatalogTabs.kt`): Home · Movies · Series · Tutorials · Collections;
  utilities (My List, Continue, Latest, Genres, Settings) where the phone/TV already keep
  secondary destinations — each destination reachable once, as on web.
- `LibraryPositions` frames: add `PERSON`, `FRANCHISE`, `LATEST`, `GENRES`; `Destination`
  titles; Movies opens the department page, the paged shelf is one step in (web
  `#/movies/page/N`). Saved-state encoding stays backward compatible (old stacks restore).
- Tests: `ui-common/src/test` stack round-trips including the new frames.
