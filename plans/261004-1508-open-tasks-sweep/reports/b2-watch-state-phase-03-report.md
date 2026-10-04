# B2 — watch-state fake, phase 03: feature/TV/phone tests on the real repository

2026-10-04, worktree branch `worktree-agent-a79c12293882448cd`, off `main` `2f962d25` (0.104.0).
Plan: `plans/260928-0130-android-one-watch-state-fake/` — phase file
`phase-03-feature-tests-on-the-real-repository.md` (new). Status: done (pending merge).
No version bump (lead bumps at merge). `docs/project-changelog.md` untouched.

## What changed

- **`testing.WatchStateFixture`** (`core/testing`, new, ~55 lines): the app's
  `DefaultWatchStateRepository` over a stateful `FakeCore`, on `Dispatchers.Unconfined`.
  `WatchStateFixture(profiles = [p1 "Viewer"], chosen = first id, core = FakeCore(), seed = {})`
  seeds profiles + choice into the core, reloads, then runs `seed` against the repository
  (state the core itself stamps — list ids are the core's, `list-1`…). Exposes `core`,
  `provider` (`beforeCore` for failures) and `repository`. `core:testing` gains
  `implementation(:core:model)`; `feature:player` tests gain `core:testing`.
- **Deleted**: `feature/player/.../FakeWatchStateRepository.kt`, `ui-tv/.../FakeWatchState.kt`
  (its `NoopWatchSync` moved, private, into `TvAppFixture.kt`), `CatalogViewModelTest`'s
  `FakeCatalogWatchState`, `ProfileViewModelTest`'s fake. Also gone: the relaxed mocks in
  `TvPlayerFixture` / `PlayerLifecycleFixture` (and `LibraryFlowFixture`'s stubs on it), the
  strict mock in `FailureDiagnosticsTest`, the delegating wrappers in `PlayerActionFailureTest`
  / `PlayerActionNoticeTest`. `git grep` finds no `WatchStateRepository` double but
  `WatchSyncTest`'s `RecordingRepository`.
- **Failures** go through `provider.beforeCore`: throw (catalogue list writes, profile
  reads/writes, TV "cannot confirm"), throw on the Nth call (`createMembership`, retry tests),
  hold-then-fail (`aLateFailure…`). **Refusals** are the core's own: whitespace list/profile
  names, an unknown list or profile id, nobody chosen (catalogue `refusedCollectionWrites…`,
  `PlayerNoticesTest`).
- **Nobody chosen → somebody chosen**: every fixture defaults to `p1` chosen; the phone
  `PlayerLifecycleFixture` and `TvPlayerFixture` used to have nobody, `FakeCatalogWatchState`
  accepted list writes with nobody. Kids tests choose a kids profile through the repository
  (`chooseProfile`), not by poking a flow.
- **`ProgressRecorderTest`** (guarded 40a43589 with a call log): now asserts that after a finish
  the position is gone and the watched stamp is newer than the position it replaces, and that
  a repeat finish moves the stamp (cc8bf95d). Mutation check: making `ProgressRecorder` skip
  `markFinished` for an already-watched title fails `aTitleAlreadyWatchedIsReStamped…` (1 of 4).
- **TvAppFixture** builds the viewer's fixture over the same `FakeCore` its setup plumbing
  uses, and hands that one fixture to the picker, catalogue and player (the app shares one
  repository). Its `watch` parameter is now a seed lambda (`suspend WatchStateRepository.() -> Unit`).
- Main code: one KDoc line in `ProgressRecorder.kt` that named the deleted fake.

## Behaviour the old fakes hid (assertions updated, not weakened)

- A title played in a walk is now saved (the player stands at 0:42 in both fixtures):
  `LibraryFlowTest`, `TvLibraryTest`, `TvSearchAndGenreTest` now expect "▶ Resume from 0:42"
  after returning from the player; `TvLibraryTest` also tells the new Continue card from the
  plate that opened the title (focus is on the plate). The TV fake had swallowed every write
  but "Mark finished" on purpose; the real repository keeps them.
- `coVerify` on writes became state checks on the repository's snapshot
  (`TvPlayerMarksTest` ×2, `TvPlayerScreenTest`). `PlayerNoticesTest` lost its
  `coVerify(exactly = 0) { setInList }` — "no membership after a refused creation" is pinned
  at view-model level by `PlayerActionNoticeTest.refusedCreationDoesNotAttemptMembership…`
  (one provider call, not two).
- `PlayerActionNoticeTest.refusedMembership…`: the list is now removed in the core between its
  creation and the membership write (a real refusal) instead of a stub answering `false`.

## Tests removed or renamed

| Test | Change | Why |
|---|---|---|
| `ProfileViewModelTest.aChoiceWhoseSnapshotFailedCannotBeAcceptedThroughStay` | **removed** | Staged a choice that commits and then throws. On the real repository the only failure points of `chooseProfile` come before the choice is published (provider, core refusal); the snapshot read after it cannot throw — the core answers a failed read with an empty snapshot (`crates/mediagram-core/src/api/state.rs:99-113`, `unwrap_or_default`). Keeping it would need a core double that throws where the real core cannot. `ProfileViewModel.failed()`'s `previousId == chosenProfileId` guard is now untested (left in place; harmless). |
| `PlayerActionNoticeTest.aCommittedWriteWhoseReloadFailsDoesNotInventASnapshotOrRetryAutomatically` | **renamed** `aFailedWriteIsTriedOnceAndInventsNoSnapshot` | Same reason: a write cannot commit and then fail on the real repository. The remaining intent — one attempt, no invented snapshot, the "could not confirm" wording — is kept, through a provider failure. |

## Tests

`cd android && ./gradlew -q testDebugUnitTest :core:model:test lint :ui-tv:compileDebugAndroidTestKotlin :core:ffmpeg:compileDebugAndroidTestKotlin`
plus `:core:rust:compileDebugAndroidTestKotlin` (core:testing feeds the device contract runner): exit 0, 0 failures.

| Module | Before | After |
|---|---|---|
| core/data | 146 | 146 |
| core/testing | 36 | 36 |
| feature/catalog | 338 | 337 (−1, removed above) |
| feature/player | 252 | 252 (1 renamed) |
| feature/setup | 91 | 91 |
| ui-mobile | 272 | 272 |
| ui-tv | 468 | 468 |
| others (app 5, designsystem 21, model 15, playback 273, update 22, stats 44, system 84, ui-common 73) | 537 | 537 |
| **total** | **2,140** | **2,139** |

Test-name diff against the baseline run shows exactly the two rows above.

## For whoever builds on this (profile roles / PINs)

- `WatchStateFixture(profiles, chosen)` takes `model.Profile` and maps it to the core's in one
  line of `init`; when `Profile` gains `kidsAge`/`parentId`/`admin`, extend that line.
- Kids-age rules belong in `CoreContract` (plan "Coordination"); feature tests then get them
  through `FakeCore` with no fixture change.

## Concerns

- `beforeCore` counters ("fail the 2nd call") count every provider access from the moment they
  are installed; a view model that starts reading through the provider on open would shift
  them. Each is installed right before the action it targets.
- `ProfileViewModel.failed()` keeps a guard for a path the real stack no longer has (above).
- CoreContract unchanged, so phase 04's device contract run is not invalidated by this phase.
