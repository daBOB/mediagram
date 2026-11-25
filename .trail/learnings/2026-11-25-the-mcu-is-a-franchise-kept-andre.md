---
title: "The MCU is a franchise kept in story order by TMDB id, and the web suite fails 20 profile tests on a checkout under ~/Workspace"
author: Andre
date: 2026-11-25
branch: feat/mcu-story-order
id: l-4a5b12a2
scope: repo
status: active
src: agent
touches:
  - web/public/lib/catalog/collections-page.js
  - android/feature/catalog/src/main/kotlin/Franchises.kt
---

## Decided
2026-11-25, the user asked for a playlist in Collections with every MCU film the library holds, in story order (Marvel's Disney+ timeline, not release order), and to prefer Endgame's "Encore" cut. TMDB has no MCU collection, only one per hero, so the MCU is one more franchise inside `franchisesIn` with id -1 (TMDB collection ids are all positive), its order a list of TMDB ids. That reaches the web, the phone and the TV with no new page. A film with a preferred cut is represented by that copy, still carrying every version, so play-through takes it; only Endgame → Encore so far.

## Tried and ruled out
- A hand-built list (`create_collection`): per profile, frozen at the films held that day, and the TV has no Play all on lists either.
- Playing through on Android: a title frame carries no run (`openTitle` has no run parameter), so the TV page is an ordered page, not a play-through. The web franchise page got Play all from the list page's own queue.

## Watch out
- `web` `bun test` fails 20 profile/household tests (household-join, profile selection, player library marks, clip mode, stats) on clean main when the checkout sits under ~/Workspace, and passes in /tmp. Bun runs test files in directory-listing order, which differs between the two filesystems, and some test leaks a chosen profile into the next file. Existing bug, not a regression; scripts/check.sh stops on it there.
- The local `~/.local/share/mediagram/library.db` is not what the player shows; the preview reads `~/.cache/mediagram-channel-index/current/library.db` (38 MCU films there, 37 locally). Titles are German, so match by TMDB id, not by title.
- New MCU films (Avengers: Doomsday, December 2026) need their TMDB id added to the list; the Kotlin test reads mcu.js and fails if the two lists differ.

In the code: web/public/lib/catalog/mcu.js (MCU_STORY_ORDER, PREFERRED_CUT, mcuIn), android/feature/catalog/src/main/kotlin/MarvelStoryOrder.kt and its test, Franchise.order on both sides.
