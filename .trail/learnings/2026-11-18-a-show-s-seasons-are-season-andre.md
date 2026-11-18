---
title: "A show's seasons are season posters on every surface, never a drop-down"
author: Andre
date: 2026-11-18
branch: main
cost: { minutes: 14, tokens: 189160 }
id: l-a8f6503d
scope: repo
status: active
src: agent
touches:
  - Cargo.lock
  - Cargo.toml
  - android/app/build.gradle.kts
  - android/ui-mobile/src/main/kotlin/ui/LibraryBrowseBranches.kt
  - android/ui-mobile/src/main/kotlin/ui/catalog/CatalogShelves.kt
  - android/ui-mobile/src/main/kotlin/ui/catalog/KeptWall.kt
  - android/ui-mobile/src/main/kotlin/ui/catalog/ShelfList.kt
  - android/ui-mobile/src/main/kotlin/ui/catalog/ShelfWall.kt
  - android/ui-mobile/src/test/kotlin/ui/catalog/KeptWallTest.kt
  - docs/project-changelog.md
---

## Decided
2026-11-18, the user: "make sure we use everywhere is season posters in the season overview". The TV already had a row of season cards (the user's 2026-10-22 pick); the web had a select and the tablet a drop-down, both with no pictures. All three now draw the same row: each season's own cover where the library holds one, else the show's poster with the season's number over it, the watched share as the poster's rule, the season showing marked with a rule under it.

## Tried and ruled out
Keeping the web's select as the reference and only adding posters to the TV: the user asked for posters everywhere, and the TV's cards were already their choice, so here the TV is the reference and the web follows it.

## Watch out
Season covers come from TMDB through the shared mediagram-tmdb crate, so the web and Android both have them; on 2026-11-18, 22,660 of 22,931 episodes carried one. A show with none (Futurama seasons 12–13, Spider-Man 2–5) shows the number over the show's poster; that is the fallback, not a bug.

In the code: web/public/lib/catalog/season-cards.js (web), android/ui-mobile/src/main/kotlin/ui/catalog/SeasonCards.kt (tablet/phone), android/ui-tv/src/main/kotlin/ui/tv/catalog/TvSeasonPicker.kt (TV). The episode's field is `seasonPoster` on the web and `seasonPosterPath` on Android.
