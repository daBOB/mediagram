# B3 — security review fixes (profile roles, 0.110.0)

Branch `worktree-agent-aecc271f05e5665ba`, from `main` 3f75e503. No version bump, nothing pushed.

**Status: partial.** #1, #2, #4 are done. #3 is done in the core; its Android half is
blocked because the user paused the NDK / bindings run (coordinator, mid-task) before
the Kotlin bindings were regenerated.

## Commits

| Hash | What |
|---|---|
| `e22bd863` | fix(core): wrong-PIN wait persisted; first profile held until a sync round (#2, #3 core) + contract amendments |
| `e0f54f35` | fix(android): Manage closes and drops its PIN when the library shows again or on ON_STOP; picker prompt given up (#1) |
| `dd876244` | refactor(android): one shared `ManageActions` in ui-common (#4) |

## RED first (recorded failing output)

- #2 `manage_tests::wrong_pins::a_restart_keeps_the_wait_and_it_still_ends_on_time`: `left: Done, right: Wait { seconds: 30 }`;
  `a_restart_keeps_the_wrong_pins_short_of_a_wait`: `left: Done, right: Wait { seconds: 60 }`.
- #3 `the_first_profile::waits_until_this_player_has_taken_in_a_sync_round`: `left: Done, right: NotSynced`;
  `sync::tests::the_first_profile_waits_for_a_round::{a_channel_that_could_not_be_listed_is_no_round, an_import_that_rolled_back_is_no_round}`: `left: Done, right: NotSynced`.
- #1 phone `ProfileGateFlowTest`: 2 of 10 failed: `leavingTheAppClosesManageAndForgetsTheParentsPin` at :113
  ("Who's watching?" absent, so Manage was shown, which reproduces the reviewer's scenario) and
  `leavingTheAppDropsAHalfTypedNewPin` at :130 (the half-typed prompt survived).
  TV `TvProfilesFlowTest`: 2 of 12 failed, same two cases, at :189 and :206.

## What changed

- **#2:** `PinWait` keeps its pure logic, so `pin-wait.json` passes unchanged. It is now loaded
  from and saved to `state_meta['pin_wait']` (JSON) around every comparison in `prove()`.
  The in-memory mutex is gone. A save that fails turns the call into `Invalid`, so a guess is
  never answered without being counted. A stored wait more than 60 s away (the clock went
  back) is moved to now + 60 s. The web keeps its in-memory count (contract §4 amended).
- **#3 core:** `import_merged` sets `state_meta['first_round_imported']` inside its own
  transaction. It is not counted in `pulled`. `create_first` answers the new
  `ProfileOutcome::NotSynced`, checked last after all other checks pass, until the marker exists.
  New export: `Core::has_synced_once()`. Contract §3/§9 amended. The web is unchanged because
  its server syncs before answering. Integration seed: `state_seed::household(player, dir, …)`
  first imports an empty merge on a second connection.
- **#1:** new `ui-common` `ForgetManageWhenAway(showsLibrary, picker, manage)`:
  `LaunchedEffect(showsLibrary)` → `manage.close()`; `LifecycleEventEffect(ON_STOP)` →
  `manage.close()` + `picker.cancelPin()`. Called by both gates. The app declares orientation
  config changes itself, so rotating does not close Manage.
- **#4:** `ui.profile.ManageActions` + `ManageProfilesViewModel.manageActions()` in ui-common.
  The phone's `ManageActions` and the TV's `TvManageActions` / `tvActions()` are deleted.

## Tests

- `cargo test -p mediagram-core`: green. `cargo test` (workspace, incl. code_standards):
  **1792 passed, 0 failed, 4 ignored**. `cargo clippy --all-targets --all-features -D warnings`: clean.
- `./gradlew testDebugUnitTest :core:rust:compileDebugAndroidTestKotlin :ui-tv:compileDebugAndroidTestKotlin lint`:
  green, **2329 unit tests passed, 0 failed**. This ran against the *old* committed bindings.

## Left for #3 (needs the regenerated bindings, which should be committed together)

The paused `generate-android-bindings.sh` (stopped at `cargo run … uniffi-bindgen`; the four
ABI `.so` are already built from `e22bd863`) will rewrite `mediagram_core.kt` when it resumes.
Kotlin then fails to compile until the following land, so the regenerated bindings and these
changes must go in one commit:

1. `CoreProfileCalls`: `CoreOutcome.NotSynced` → `model.ProfileOutcome.NotSynced`. Its sentence
   is "Waiting for this household’s profiles…".
2. `WatchStateRepository.syncedOnce: StateFlow<Boolean>`, read in `reload()` via
   `core.hasSyncedOnce()`. `DefaultWatchSync.attempt` also reloads while it is still false
   (an empty first round pulls 0 rows).
3. `FakeCore.syncedOnce` (default `true`, documented as "a device that has synced once") +
   `hasSyncedOnce()`. `FakeProfiles.createFirst` answers `NotSynced` last.
4. `ProfileUiState.Picking.synced`: `needsFirstProfile = synced && no grown-up`. New
   `awaitingHousehold`. `needsAdmin` requires a grown-up (otherwise it fires with none).
5. Phone and TV pickers show the waiting line + Try again. The TV `focusSpot` falls back to TryAgain.
6. View-model tests: no round → not offered / refused; empty round → offered; round with
   profiles → tiles.
7. `CoreContract.core(synced = true)` + a "fresh core refuses a first profile" case.
   `RealCoreContractTest` seeds `state_meta(first_round_imported)` through Android SQLite
   before `Core` opens the directory. **Until this lands, every profile case of
   `RealCoreContractTest` fails on a device with the new `.so`.**

## Concerns

- **Do not install from this worktree yet.** The gitignored `.so` files are newer than the
  committed bindings (UniFFI checksum mismatch at load).
- `error::tests::io_diagnostics_keep_nested_context_and_the_underlying_cause` failed once in a
  parallel run and passed on every rerun. It is a tracing-subscriber test, flaky, and not
  touched by this branch.
- A device upgraded from 0.110.0 counts as not synced until its next round. That only matters
  for `create_first`, and an upgraded device already has grown-ups.
- No device walk was done (no adb, as instructed).
