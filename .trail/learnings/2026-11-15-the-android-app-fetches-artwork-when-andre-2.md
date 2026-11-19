---
title: The Android app fetches artwork when it opens only if the library list changed
author: Andre
date: 2026-11-15
branch: main
cost: { minutes: 35, tokens: 227673 }
id: l-7beb37e3
scope: repo
status: active
replaces: l-bfdf000e
src: agent
touches:
  - android/core/data/src/main/kotlin/CatalogRepository.kt
---

## Decided
2026-11-15: new videos showed no poster on Android devices that were off or closed when the new library list was published. The app's live "new list" event triggers a quiet artwork fetch, but opening the app (a Read) refreshed the list without fetching, so the new titles stayed bare until someone pressed Update or another list came. Now a Read fetches quietly when its refresh installed a newer list.

## Tried and ruled out
Fetching on every app start, as the web player does. A fetch plans over every title (about 1,800) and checks the TMDB key over the network. That cost lands on the TV box at each start, and a test already guarded "a read without an update does not spend artwork".

## Watch out
If the app is killed mid-fetch after a Read, the next start finds the list current and does not retry; the next list or a manual Update fills the gap.

In the code: LibraryUpdate.update (android/core/data/src/main/kotlin/LibraryUpdate.kt) decides from the RefreshOutcome that CatalogRepository.refresh() returns: a Read fetches only on RefreshOutcome.Updated, a Published unless Refused, a Manual always. RefreshLog is only the System screen's record now. The rule's tests are LibraryUpdateTest (android/feature/catalog/src/test/kotlin/), driven by FakeCatalogRepository.refreshOutcome, which answers AlreadyCurrent unless a test sets Updated.
