# Phase 02 — Repository: `chosenProfile`, drop `clearProgress`, provider failure injection

## Context links

- Plan decisions 4, 5, 7 (`plan.md`); readiness check
  `plans/261004-1508-open-tasks-sweep/reports/b2-subtitles-and-test-cleanup-readiness-report.md`
  ("Watch-state fake").
- `android/core/data/src/main/kotlin/WatchStateRepository.kt` (interface + `DefaultWatchStateRepository`).
- The three hand-rolled "who is chosen" lookups: `feature/catalog/.../CatalogViewModel.kt`
  (`kidsFilter`/`currentKids`/`kidsProfile`), `feature/player/.../PlayerMarksController.kt`
  (`onKidsProfile`), `feature/setup/.../ProfileSettingsViewModel.kt` (`profile`).
- `android/core/testing/src/main/kotlin/testing/FakeCoreProvider.kt`.
- Report: `plans/261004-1508-open-tasks-sweep/reports/b2-watch-state-phase-02-report.md`.

## Overview

P2 · done (pending merge) — code `8d080a18`, off main @ `f2489d66` (0.100.1); 1,961 unit tests + lint green. Re-checked against main before
building: `clearProgress` still on the repository (no production callers, five test
overrides), only `chosenProfileId` existed, three view models rebuilt the chosen profile
from `profiles` + `chosenProfileId` by hand, and `FakeCoreProvider` had no failure hook.

## Requirements (from the plan's decisions)

1. `WatchStateRepository.chosenProfile: StateFlow<Profile?>` — the one answer to "who is
   watching"; view models read `kids` (later its age limit) from it.
2. `clearProgress` leaves the Kotlin repository (the UniFFI/Rust function and FakeCore's
   copy stay — `CoreContract` still pins the core's plain delete). With it gone, the only
   way to drop a position through the repository is `setWatched(true)`/`markFinished`,
   which re-stamps — so the two-call shape behind 40a43589 no longer compiles, which is
   what lets phase 03 delete `ProgressRecorderTest`'s call-log guard.
3. Failure injection on `FakeCoreProvider` (provider failures, not core ones): the core's
   state writes never throw, so a provider that cannot hand its core out is how a
   repository write fails.

## Design

- `chosenProfile` is derived, not stored separately: `DefaultWatchStateRepository`
  republishes it (under `publicationLock`) wherever `_profiles` or `_chosenProfileId`
  moves — `invalidate`, `reload`, `chooseProfile`, `createProfile`. `null` with nobody
  chosen and while the chosen id is not in `profiles` yet (same answer the three
  hand-rolled lookups gave; the picker only offers listed profiles).
- `chosenProfileId` stays: stats, achievements, player choices, the profile picker and
  subtitle defaults need only the id.
- `FakeCoreProvider.beforeCore: suspend () -> Unit` runs first in `awaitCore` and
  `coreOrNull`: throw from it to fail a call, suspend in it to hold one open. A suspend
  hook rather than a `failure` field because phase 03's failure tests need all three of
  "throw", "throw on the second call" and "hold, then fail late".

## Steps

1. RED: declare `chosenProfile` (unpublished) and `beforeCore` (unwired); add
   `WatchStateChosenProfileTest` (5) and `WatchStateProviderFailureTest` (2) in core:data.
2. GREEN: publish `chosenProfile`; wire `beforeCore`.
3. Remove `clearProgress` from the interface and the default implementation; delete the
   five test overrides.
4. Move `CatalogViewModel`, `PlayerMarksController`, `ProfileSettingsViewModel` onto
   `chosenProfile`.
5. Fakes/mocks the interface change forces (no migration — that is phase 03): each
   hand-written fake gains `chosenProfile`; the mockk fixtures stub it; tests that made a
   kids profile by poking `profiles` + `chosenProfileId` now set `chosenProfile`.
6. `./gradlew -q testDebugUnitTest lint` whole project.

## Todo

- [x] RED tests for `chosenProfile` and the provider hook (failing output in the report)
- [x] `chosenProfile` published by `DefaultWatchStateRepository`
- [x] `FakeCoreProvider.beforeCore`
- [x] `clearProgress` gone from the repository and its five test overrides
- [x] three view models read `chosenProfile`
- [x] forced fake/mock updates
- [x] whole-project unit tests + lint green

## Success criteria

No view model looks the chosen id up in `profiles`; `WatchStateRepository` has no call that
clears a position without stamping completion; a test can fail or stall any repository
call through `FakeCoreProvider` while running the real repository over `FakeCore`.

## Risks

- `CatalogViewModel.kt` and `CatalogViewModelTest.kt` are shared with the editorial-parity
  work (B1): the edits are small and local (the kids filter and nine test setup lines).
- Phase 03 still has to migrate the four fakes; until then they carry a `chosenProfile`
  each (the catalogue fake's is set directly by its tests).
