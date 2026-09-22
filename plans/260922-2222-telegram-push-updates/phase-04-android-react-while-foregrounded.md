# Phase 04 — Android: react while foregrounded

## Context links
- `plans/260922-2135-android-watch-state-sync/phase-04-*` (`WatchSync.onForeground/onBackground/soon`)
- `android/ui-mobile/src/main/kotlin/LibraryFlow.kt` ("Update library"), core `refresh_library`

## Overview
Priority P2. Status: **index half done 2026-09-23** (uncommitted); state half still blocked by the Android
watch-state plan's phase 04 (`WatchSync` does not exist yet).

## What was built (differs from the original sketch — simpler, same effect)
- `CoreClient.nextLibraryEvent` (default body waits for ever, so the five module fakes need no change; `DefaultCoreClient` delegates).
- `data.LibraryEvents` (`fun interface`, `None` for tests) + `CoreLibraryEvents`: cold flow looping the core call; reads the
  handle before each wait; ends when no library is chosen; 30 s pause after a failed wait. Hilt singleton in `DataModule`.
- No `MainActivity` hooks: `CatalogViewModel.state` now merges `INDEX` events beside the button's reloads. `state` is shared
  `WhileSubscribed(5_000)` and collected with `collectAsStateWithLifecycle`, so listening lasts exactly as long as the catalog
  is on screen — foreground-only for free.
- **No round on (re)start for the index**, deliberately: `refresh_library` downloads the whole index (MBs) every time, even
  unchanged. The catalog still loads once per app start and the button still works; web parity (web logs index events only).
- `ownDevice` is `""` until a watch-state device id exists; `STATE` is ignored by the catalog.
- **Fetch on new media (user decision, 2026-09-23):** "getting a new media should trigger fetch metadata and posters".
  Reverses the first cut's "no posters on push". `CatalogViewModel.published` fires once after a *pushed* read that
  succeeded (not after the button's read, which chains its own fetch; not after a failed read); `LibraryFlow` collects it
  and runs `FetchViewModel.fetch(quiet = true)` — no result dialog, and a result still on screen is left alone. A push
  during a running fetch gets no fetch of its own (fetch refuses to overlap); the next push or the button covers it.
  Tests: `onlyAPushedReadThatSucceededAsksForAFetch`, `aPushedReadThatFailedAsksForNothing`,
  `aQuietFetchFillsInWithoutAReport`, `aQuietFetchLeavesAResultStillOnScreen` (catalog pair mutation-checked).
- Tests: `LibraryEventsTest` (passes events + handle; no library → ends without asking; failure → 30 s virtual pause → resumes),
  `CatalogViewModelTest.aNewIndexFromAnotherDeviceReadsTheChannelAgain` (INDEX refreshes, STATE does not; mutation-checked).
  `:app:assembleDebug` + all `testDebugUnitTest` green.
- **Device-validated 2026-09-23** on the tablet (`caad49da`), debug build installed over the signed-in session. App on the
  start page, catalog `v-1790115283`; nobody touched it. The external uploader pushed index 3164 (`pushed_at` 00:22:35 by
  its own clock); the tablet had installed `v-1790115755` by 00:22:38 (file mtime). Seconds, across two machines' clocks.
  USB stay-awake was changed for the run and restored to its original `15`.

## Remaining (state half)
When `WatchSync` lands: collect the same `LibraryEvents` (share one collection — the core serves one stream), call
`WatchSync.soon()` on `STATE`, and pass the real watch-state device id instead of `""`.

## Requirements
- `LibraryEvents` (`@Singleton`, in `core/data`): `onForeground` launches a loop over `next_library_event()`;
  `onBackground` cancels it. Wired beside WatchSync in `MainActivity.onStart/onStop`.
- `StateChanged` → `WatchSync.soon()`. `IndexChanged` → `refresh_library(handle)`, then the catalog reloads the
  way the button's path already does (no posters, see plan Q2). Nothing on screen besides the refreshed content.
- Error → back off 30 s and restart the loop, at most while foregrounded; sign-out error → stop.

## Success criteria
Unit tests with a fake `CoreClient`: events route to the right call; background cancels; errors back off.
On the phone: a web-player position change shows on Android's Continue shelf in under ~10 s.
