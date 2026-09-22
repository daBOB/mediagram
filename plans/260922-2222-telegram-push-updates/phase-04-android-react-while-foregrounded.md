# Phase 04 — Android: react while foregrounded

## Context links
- `plans/260922-2135-android-watch-state-sync/phase-04-*` (`WatchSync.onForeground/onBackground/soon`)
- `android/ui-mobile/src/main/kotlin/LibraryFlow.kt` ("Update library"), core `refresh_library`

## Overview
Priority P2. Status: pending. Blocked by 03 and by the Android watch-state plan's phase 04 (WatchSync must exist).

## Requirements
- `LibraryEvents` (`@Singleton`, in `core/data`): `onForeground` launches a loop over `next_library_event()`;
  `onBackground` cancels it. Wired beside WatchSync in `MainActivity.onStart/onStop`.
- `StateChanged` → `WatchSync.soon()`. `IndexChanged` → `refresh_library(handle)`, then the catalog reloads the
  way the button's path already does (no posters, see plan Q2). Nothing on screen besides the refreshed content.
- Error → back off 30 s and restart the loop, at most while foregrounded; sign-out error → stop.

## Success criteria
Unit tests with a fake `CoreClient`: events route to the right call; background cancels; errors back off.
On the phone: a web-player position change shows on Android's Continue shelf in under ~10 s.
