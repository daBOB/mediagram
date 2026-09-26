# Phase 2 — Kotlin data and pure rules

**Status:** done (2026-09-26). Web reference: `similar.js`, `series-resume.js`,
`utility-pages.js#genreIndex`, `collections-page.js#franchisesIn`, `cast.js#visiblePeople`.
Report: `plans/reports/fullstack-developer-260926-1630-android-parity-data-and-navigation-report.md`
(data + view-model half; the pure-rule half landed earlier — see
`plans/reports/fullstack-developer-260926-1612-android-parity-pure-rules-report.md`).

- `MediaSet` gains the four row fields; `CoreClient` (default impls for fakes) →
  `DefaultCoreClient` → `CatalogRepository`: `credits(key)`, `person(id)`, `franchises()`,
  `searchPeople(q)`.
- Pure ports in `feature/catalog`, one file + one test each, same cases as the web tests:
  `Similar.kt` (franchise first, unwatched, shared genres, popularity, max 12),
  `SeriesResume.kt` (Resume / Continue / Play SxEy), `GenreIndex.kt` (by size, distinct
  artwork), `Franchises.kt` (≥2 held films, release order, most popular art),
  `VisiblePeople.kt` (only people on titles this profile can see — kids).
- Where both sides can read one JSON fixture (as `shared_*_fixtures` do), share it so the
  rules cannot drift between web and Android.
