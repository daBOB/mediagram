---
title: The Android app fetches artwork when it opens only if the library list changed
author: Andre
date: 2026-11-15
branch: main
cost: { minutes: 9, tokens: 113731 }
id: l-bfdf000e
scope: repo
status: active
src: agent
touches:
  - android/feature/catalog/src/test/kotlin/LibraryUpdateCoordinatorTest.kt
  - android/core/data/src/main/kotlin/LibraryUpdateCoordinator.kt
---

## Decided
2026-11-15: new videos showed no poster on Android devices that were off or closed when the new library list was published. The app's live "new list" event triggers a quiet artwork fetch, but opening the app (a Read) refreshed the list without fetching, so the new titles stayed bare until someone pressed Update or another list came. Now a Read fetches quietly when its refresh installed a newer list.

## Tried and ruled out
Fetching on every app start, as the web player does. A fetch plans over every title (about 1,800) and checks the TMDB key over the network. That cost lands on the TV box at each start, and a test already guarded "a read without an update does not spend artwork".

## Watch out
If the app is killed mid-fetch after a Read, the next start finds the list current and does not retry; the next list or a manual Update fills the gap.

In the code: LibraryUpdateCoordinator reads RefreshLog.last() right after its own refresh (it is the only caller of CatalogRepository.refresh).
