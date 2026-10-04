# Phase 03 — Feature, TV and phone tests on the real repository; the four fakes deleted

## Context links

- Plan decisions 1, 6, 7 (`plan.md`); phase 02 (`phase-02-chosen-profile-and-no-clear-progress.md`).
- Readiness: `plans/261004-1508-open-tasks-sweep/reports/b2-subtitles-and-test-cleanup-readiness-report.md`.
- Report: `plans/261004-1508-open-tasks-sweep/reports/b2-watch-state-phase-03-report.md`.

## Overview

P2 · done (pending merge) — off main @ `2f962d25` (0.104.0); 2,139 unit tests + lint green.

## Requirements

1. Feature/TV/phone tests run `DefaultWatchStateRepository` over `core/testing`'s stateful `FakeCore`.
2. Deleted: player `FakeWatchStateRepository`, TV `FakeWatchState.kt`, `FakeCatalogWatchState`,
   `ProfileViewModelTest`'s fake; plus the relaxed/strict mocks (`TvPlayerFixture`,
   `PlayerLifecycleFixture`, `LibraryFlowFixture` via it, `FailureDiagnosticsTest`) and the
   delegating failure wrappers (`PlayerActionFailureTest`, `PlayerActionNoticeTest`).
   `WatchSyncTest`'s `RecordingRepository` stays (it tests WatchSync's own calls).
3. Failures through `FakeCoreProvider.beforeCore`; refusals from the core's own rules.
4. Tests that wrote with nobody chosen choose a profile first.
5. `ProgressRecorderTest`'s call log becomes a behavioural assertion: after a finish the
   position is gone and the watched stamp postdates it; a repeat finish moves the stamp.

## Design

`testing.WatchStateFixture(profiles = [p1 Viewer], chosen = first, core = FakeCore(), seed = {})`:
seeds profiles + choice into the core, reloads the real repository (on `Dispatchers.Unconfined`),
then runs `seed` against the repository. Exposes `core`, `provider`, `repository`. One fixture
per test; `TvAppFixture` builds it over the core its setup plumbing already uses and hands the
same one to the picker, catalogue and player.

## Todo

- [x] `WatchStateFixture` in `core:testing` (+ `core:model` dep; `feature:player` tests depend on `core:testing`)
- [x] player tests migrated; fake deleted
- [x] catalogue + profile tests migrated; both fakes deleted
- [x] phone fixtures (`PlayerLifecycleFixture`, `LibraryFlowFixture`, `FailureDiagnosticsTest`, `PlayerNoticesTest`)
- [x] TV fixtures (`TvAppFixture`, `TvPlayerFixture`, run fixture, marks tests); fake deleted
- [x] `testDebugUnitTest :core:model:test lint` + androidTest compiles green; counts per module in the report

## Success criteria

No test double of `WatchStateRepository` except `RecordingRepository`; no test silently dropped
(one removed, one renamed — both in the report, with why).

## Risks

- Walk tests now save what they play (real behaviour): three assertions moved from "▶ Play" to
  "▶ Resume from 0:42", and a TV focus check had to tell the new Continue card from the plate.
- Count-based `beforeCore` hooks ("fail the second call") depend on how many provider calls an
  action makes; each counts from the moment it is installed.
