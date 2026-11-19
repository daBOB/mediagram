---
title: "On the TV emulator, re-take a screenshot before calling a scroll bug, and reach search with the D-pad"
author: Andre
date: 2026-11-17
branch: feat/film-versions
id: l-ded25ea8
scope: repo
status: active
src: agent
touches:
  - Cargo.lock
  - Cargo.toml
  - android/app/build.gradle.kts
  - docs/project-changelog.md
  - docs/system-architecture.md
  - web/package.json
  - android/ui-tv/src/main/kotlin/ui/tv/catalog/TvPage.kt
---

## Watch out
- A screenshot taken right after a D-pad key catches the TV page mid-scroll (`StaysPutWhenOnScreen` in ui-tv TvPage.kt animates the scroll): the focused row looks cut off at the bottom. Re-take it a moment later before calling it a focus or scroll bug (2026-11-17, the Versions list in TvTitlePage.kt looked broken and wasn't).
- The search key (KEYCODE_SEARCH) on the Google TV emulator opens the system assistant, not the app's search, and text typed after it goes nowhere. Move to the app's search icon with the D-pad instead.
- TV Continue and My List cards (`TvKeptWall`) open the title page and never play straight away, so a "plays from the card" check on TV needs the search results.

In the code: android/ui-tv/src/main/kotlin/ui/tv/catalog/TvPage.kt, TvTitlePage.kt, TvKeptWall.kt; adb -s emulator-5554 shell input keyevent KEYCODE_DPAD_*; read focus from `uiautomator dump` (focused="true").
