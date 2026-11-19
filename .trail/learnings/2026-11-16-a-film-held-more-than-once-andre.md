---
title: "A film held more than once is one entry, and the viewer picks the copy every time"
author: Andre
date: 2026-11-16
branch: feat/film-versions
id: l-b396e9e7
scope: repo
status: active
src: agent
touches:
  - android/core/data/src/main/kotlin/SetSummaryToMediaSet.kt
  - android/core/model/src/main/kotlin/MediaSet.kt
  - android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt
  - android/core/testing/src/main/kotlin/testing/SetSummaries.kt
  - android/feature/catalog/src/main/kotlin/Anime.kt
  - android/feature/catalog/src/main/kotlin/CatalogUiState.kt
  - android/feature/catalog/src/main/kotlin/CatalogViewModel.kt
  - android/feature/catalog/src/main/kotlin/FactSheetRows.kt
  - android/feature/catalog/src/main/kotlin/HomeShelves.kt
  - android/feature/catalog/src/main/kotlin/KeptShelves.kt
---

## Decided
- 2026-11-17, user: build the 2026-09-25 film-versions spec as written ("seems correct"). One card per film, a Versions list on its page, a chooser every time a card plays a film held more than once. Copies are grouped on read; the index, watch state and sync record stay unchanged.
- Every `variant` label counts as a cut, including ones that really describe quality ("Encore", "4K DV HDR"). Positions therefore do not carry between those copies. The user accepted this; don't re-label the cuts without asking.
- Only a TMDB poster key (`tmdb-movie-<id>`) groups copies. The title fallback key ignores the year, so two untagged films with the same title would merge.
- Continue shows a copy only while its row is still its cut's position, so Continue, the card and the chooser agree.
- The chooser is never shown when the copy is already known: Join, a scene, the film page's own row, or the player's handover to the next title (that plays the first copy, because a dialog behind a fullscreen player is never seen).
- The uploader's duplicate refusal compares quality, HDR, cut AND audio languages: a re-upload that adds a language is wanted, not a duplicate.

## Watch out
- `web/public/app.js` and `player.js` sit at their line ceilings in `web/test/code-standards.test.ts`; film logic lives in `film-session.js` so those files don't grow.
- Android ports the same rules and must pass the shared fixtures.

In the code: web/public/lib/film-versions.js (rules), web/public/lib/film-session.js (where they apply), web/public/lib/catalog/film-versions-view.js (rows + chooser), web/test/fixtures/film-versions/{grouping,watch-state}.json, crates/mediagram/src/upload/film_version.rs, spec plans/260925-1400-film-versions/spec.md
