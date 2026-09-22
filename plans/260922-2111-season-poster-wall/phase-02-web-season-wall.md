# Phase 2 — Web: season wall + season page

**Depends on:** phase 1. **Status:** pending

## Changes
- `web/src/package/posters.ts` KEY + `web/src/routes.ts` POSTER_PATH: accept `-s<n>`.
- `forBrowser` (routes.ts): add `seasonPoster` = `tmdb-tv-<id>-s<season>` when held
  (`posters.has`), else null. One `existsSync` per set, as `poster` already does.
- `web/public/app.js` `viewCollection`: series → header + wall of season plates
  (reuse `plate.js` / `collectionGrid` card shape); route `#/series/<show>/<season>`
  → crumbs + heading `Season N` + existing `divisionBlock` for that season.
  Single-season show → render episodes directly (today's view).
- Update the course-view / app.js comments that argue against a drill-down for seasons.
- Tests: season-plate model (which poster, caption, single-season rule) in a pure
  module under `web/public/lib/`, tested in `web/test/`.

## Todo
- [ ] key/route regex + forBrowser field + route test
- [ ] season wall + season route
- [ ] tests; stub-harness screenshot
