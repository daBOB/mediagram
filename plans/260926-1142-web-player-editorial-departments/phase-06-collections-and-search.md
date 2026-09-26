# Phase 6 — Collections and Search

**Priority:** P2. **Status:** done (0.62.0, 2026-09-26).

## Collections (panel 07)
- A hero headed "Curated collections". Then **Franchises** (from `collection_id`, only those
  with at least 2 held titles) as large backdrop cards (name and "N items"), then **Your
  lists** (existing, editable as today) in the same card language.
- **Detail** (`#/collections/tmdb-{id}` vs the existing list ids): an introduction with
  the eyebrow "The … collection" and a serif-caps name. The line under it is TMDB's
  collection overview **if fetched, otherwise omitted** (no invented copy). Then the
  titles in release order, split into Films and Series when both exist.
- Existing list editing and routes are unchanged.

## Search (panel 08)
- The field sits at the top of the page (the bar icon focuses it). Filter pills: **All,
  Movies, Series, Tutorials, People, Collections**. The results are sections: Top results
  (posters), People (circular portraits, name and role), Collections (cards).
- A person page (`#/person/{id}`) shows a portrait and a poster grid of held titles.
- The filter is in the hash (`#/search/q?type=people`), so the back button works.

## Todo
- [ ] franchise grouping  - [ ] collection detail  - [ ] search filters + sections  - [ ] person page  - [ ] tests
