# Phase 5 — Departments: Movies, Series, Tutorials

**Priority:** P2. **Status:** done (0.62.0, 2026-09-26). It starts after checkpoint approval.

## Context
`app.js` `viewMovies` / `viewCollections`, `lib/catalog/shelf-view.js`, `pager.js`, `genres.js`.

## Requirements
- **Movies (panel 02):** a department hero with the eyebrow "Only in", the serif-caps "MOVIES",
  a line from the real count ("581 films in your library"), and the backdrop of the top
  popular unwatched film. Then a **Featured Movies** shelf with See all, then **Genres** as
  image tiles, each with the backdrop of that genre's most popular title. Then Recently added.
  **The full paged grid moves to `#/movies/all`**, which keeps `#/movies/page/N` working.
- **Series (panel 04):** the same hero, with a line like "N series · M episodes". Then
  **Popular Series** (with years and status under each card), **Continue your series**,
  **New episodes** (recent `created_at`), **Completed series** (status Ended), **Limited
  series** (`series_type = Miniseries`, hidden without v9), **Recently added**. Then the full
  grid at `#/series/all`.
- **Tutorials:** the same hero and shelves (In progress, Recently added, by collection).
- Any shelf with nothing in it is hidden, never shown empty.

## Todo
- [ ] department-hero.js (shared)  - [ ] movies  - [ ] series  - [ ] tutorials  - [ ] route compat test

## Success criteria
The old hashes resolve. Movies and Series share components but differ in shelves
and metadata, so the two pages do not read as one grid.
