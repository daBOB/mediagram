# Phase 3 — Android: season wall + season screen

**Depends on:** phase 1 (core validates keys via mlib-spec). **Status:** pending

## Changes
- Core DTO / `MediaSet`: carry season poster path (`posterPath(seasonKey)`),
  resolved in `CatalogRepository` the way `posterPath` is.
- `ui-mobile/CollectionScreen.kt`: for a series with >1 season, a grid of
  `PosterCard`s (season poster → show poster → initials); tap opens a season
  screen listing that season's episodes (existing row code). Courses unchanged.
- Navigation in `LibraryFlow.kt` for the season destination; back returns to wall.
- Tests: season-row model in `feature/catalog` (pure), PosterCard fallback.

## Todo
- [ ] core/model field + repository
- [ ] wall + season screen + navigation
- [ ] unit tests; real-phone check (never "start over" on it)
