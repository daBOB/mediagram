---
title: The Android app fetches artwork for any installed index it has not finished fetching for
author: Andre
date: 2026-11-18
branch: main
cost: { minutes: 8, tokens: 92458 }
id: l-ad29cdf0
scope: repo
status: active
replaces: l-7beb37e3
src: agent
touches:
  - Cargo.lock
  - Cargo.toml
  - android/app/build.gradle.kts
  - android/ui-mobile/src/main/kotlin/ui/catalog/SeasonCards.kt
  - android/ui-mobile/src/main/kotlin/ui/catalog/SeasonWall.kt
  - android/ui-mobile/src/test/kotlin/ui/LibraryFlowTest.kt
  - android/ui-mobile/src/test/kotlin/ui/catalog/title/SeriesPageTest.kt
  - docs/project-changelog.md
  - web/package.json
  - web/public/lib/catalog/season-cards.js
---

## Decided
2026-11-19: funstation showed no posters for everything added on 2026-11-18. The user: "funstation is not updating posters. WHY IS THE HOOK NEW FILMS is not working". The three indexes published that day arrived while the app was not listening (asleep or closed), so the live "new index" fetch never ran; opening the app installed the newest index without fetching. Funstation also ran a hand-installed benchmark 0.180.4, from before the 2026-11-15 fix that fetches when an opening installs a newer index, and benchmark builds never update themselves. Even with that fix, an index already installed bare stayed bare.

Now the app remembers, across starts, the push time of the index whose artwork it last finished fetching. A Read fetches when its refresh installed a newer index, or when it reached the channel and the installed index is not the one remembered. A fetch that returned a report marks the index; Published and Manual still fetch as before.

## Tried and ruled out
Fetching on every start, as the web player does: still too dear on the TV box (a fetch plans over every title and checks the TMDB key over the network). Deciding from "some titles have no poster file": titles TMDB has no picture for would re-fetch on every start.

## Watch out
A Refused refresh (offline) does not catch up, so an offline start does not mark a failed run as done. A fetch that cannot run (no TMDB key and no home cache) returns no report and leaves the index unmarked; that costs only a key read per start. Funstation's app is a benchmark build that never self-updates: every Android fix reaches it only by a sideload, so check its versionName before debugging it.

In the code: LibraryUpdate.update (android/core/data/src/main/kotlin/LibraryUpdate.kt), ArtworkFetchMark (android/core/data/src/main/kotlin/settings/ArtworkFetchMark.kt, SharedPreferences file artwork_fetch_mark), CatalogRepository.publishedAt() from core.catalogFacts().publishedAt. Tests: LibraryUpdateTest (aReadOnAnIndexNoFetchFinishedForFetchesItOnceQuietly and its neighbours).
