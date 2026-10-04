# B2 — watch-state fake, phase 02: `chosenProfile`, no `clearProgress`, provider failure hook

2026-10-04, worktree `worktree-agent-aa1ecd96463812912`, off `main` `f2489d66` (0.100.1).
Plan: `plans/260928-0130-android-one-watch-state-fake/` — phase file
`phase-02-chosen-profile-and-no-clear-progress.md` (new; plan.md said 02-04 get files when
their turn comes). Status: done (pending merge). No version bump (lead bumps at merge).

## Readiness facts — verified
- `clearProgress` at `WatchStateRepository.kt:71` (interface) and `:299` (impl); no
  production callers; 5 test overrides (WatchSyncTest, CatalogViewModelTest,
  ProfileViewModelTest, player `FakeWatchStateRepository`, TV `FakeWatchState`). Confirmed.
- Only `chosenProfileId` existed; three hand lookups: `CatalogViewModel.kt:62-79`,
  `PlayerMarksController.kt:41`, `ProfileSettingsViewModel.kt:42`. Confirmed.
- `FakeCoreProvider.kt` 51 lines, no failure hook. Confirmed.

## What changed
- `WatchStateRepository.chosenProfile: StateFlow<Profile?>`; `DefaultWatchStateRepository`
  republishes it under `publicationLock` in `invalidate`, `reload`, `chooseProfile`,
  `createProfile`. `null` with nobody chosen or while the chosen id is not listed yet (the
  same answer the three lookups gave). `chosenProfileId` stays (stats, achievements,
  player choices, picker, subtitle default need only the id).
- `clearProgress` removed from the interface + impl (UniFFI/Rust, FakeCore and
  `CoreContract` keep theirs, per decision 5). `markFinished` KDoc now says why there is
  no position-only clear. The two-call shape behind 40a43589 no longer compiles against
  the repository.
- View models: `CatalogViewModel` (kids filter + `kidsProfile`), `PlayerMarksController`
  (`canMarkKids`), `ProfileSettingsViewModel.profile` (now the repository's flow itself).
  **CatalogViewModel edit is minimal**: one import, `kidsFilter`/`currentKids`/`kidsProfile`
  bodies only (lines ~62-78).
- `FakeCoreProvider.beforeCore: suspend () -> Unit`, run first in `awaitCore`/`coreOrNull`:
  throw to fail a call, suspend to hold one. Suspend hook, not a `failure` field, because
  phase 03's failure tests need throw, throw-on-second-call (`createMembership`) and
  hold-then-fail-late (`aLateFailure…`).

## Forced test edits (not the phase-03 migration)
- `clearProgress` overrides deleted from the 5 fakes; each fake gains `chosenProfile`.
- Catalogue fake: `chosenProfile` is a plain `MutableStateFlow` the tests set — 9 setup
  hunks in `CatalogViewModelTest.kt` (kids tests ~767-939) changed from
  `profiles` + `chosenProfileId` to `chosenProfile`. **Shared with B1**, small and local.
- Player fake: `chosenProfile` = `Profile("p1","Viewer")` when chosen; `PlayerMarksTest`
  kids test sets it. TV fake keeps it in step in choose/delete/invalidate.
  `ProfileViewModelTest` fake: fixed `null` (ProfileViewModel reads only the id; commented).
- Mock stubs: `TvPlayerFixture`, `PlayerLifecycleFixture`, `LibraryFlowFixture` add a
  `chosenProfile` stub; `FailureDiagnosticsTest` swaps its `profiles`/`chosenProfileId`
  stubs for it (strict mock, CatalogViewModel is its only reader).
- Not touched: ui-mobile screens, `SeriesSummary`, `TitleInfo` code, `MobileAppFixture.kt`,
  `TvAppFixture.kt`.

## RED → GREEN
New tests (core:data): `WatchStateChosenProfileTest` (5), `WatchStateProviderFailureTest` (2).
RED against an unpublished `chosenProfile` and an unwired hook — 5 of 7 failed on assertions:

```
choosingAnotherProfileMovesTheChosenProfileWithIt   expected:<Profile(id=a, name=Ana, kids=false)> but was:<null>
reloadPublishesTheChosenProfileWithItsKidsFlag      expected:<Profile(id=k, name=Mia, kids=true)> but was:<null>
aProfileCreatedHereIsTheChosenProfileOnceChosen     expected:<Profile(id=p1, name=Bea, kids=true)> but was:<null>
aWriteHeldByTheProviderLandsOnlyOnceReleased        expected:<[]> but was:<[s1]>
aWriteTheProviderCannotServeThrowsAndWritesNothing  Expected an exception of class java.lang.IllegalStateException to be thrown, but was completed successfully.
```
(`deletingTheChosenProfileLeavesNobodyChosen`, `anAccountResetForgetsWhoWasWatching` pass
trivially against a never-set flow; they pin the clearing paths once publishing exists.)

GREEN: `./gradlew -q --continue testDebugUnitTest lint` exit 0; plus check.sh's
`:core:model:test :ui-tv:compileDebugAndroidTestKotlin :core:ffmpeg:compileDebugAndroidTestKotlin`
and `:core:rust:compileDebugAndroidTestKotlin` exit 0.

| Module | Baseline | After |
|---|---|---|
| core/data | 139 | 146 |
| core/testing | 36 | 36 |
| feature/catalog | 332 | 332 |
| feature/player | 252 | 252 |
| feature/setup | 91 | 91 |
| ui-mobile | 237 | 237 |
| ui-tv | 436 | 436 |
| others (app 5, designsystem 21, playback 273, update 22, stats 44, system 84, ui-common 73) | 522 | 522 |
| **total** | **1,954** | **1,961**, 0 failures |

## Gotcha for whoever merges — stale/poisoned compile output
First full run: 5 `PlayerActionNoticeTest` cases failed with
`AbstractMethodError … does not define … getChosenProfile()`. Cause: Kotlin incremental
compilation did not recompile the anonymous `object : WatchStateRepository by acknowledged`
delegates (source unchanged, interface gained a member), and with `org.gradle.caching=true`
that stale output went into the shared build cache — `:feature:player:clean` alone restored
it again. Fixed here with `./gradlew :feature:player:compileDebugUnitTestKotlin --rerun`
(fresh compile, re-stored); clean + full rerun then green. If the same error shows up in
the main checkout after merging, run that same `--rerun` (or `--no-build-cache`).

## Concerns / open
- Decision 6 ("tests that wrote with no chosen profile move onto one") and the four fakes'
  deletion are phase 03; `ProgressRecorderTest`'s call log is still the runtime guard until
  then (the compile-time guard now exists).
- No Rust/UniFFI change. `CoreContract` unchanged, so no device re-run needed for 02.
