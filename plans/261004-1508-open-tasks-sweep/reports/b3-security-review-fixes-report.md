# B3 — security review fixes (profile roles, 0.110.0)

Branch `worktree-agent-aecc271f05e5665ba`, from `main` 3f75e503. No version bump, nothing pushed.

**Status: done.** All four findings are fixed. The NDK / bindings run was paused mid-task
by the user, then resumed. #3's Android half landed in one commit with the
regenerated bindings (`b37b0077`).

## Commits

| Hash | What |
|---|---|
| `e22bd863` | fix(core): wrong-PIN wait persisted; first profile held until a sync round (#2, #3 core) + contract amendments |
| `e0f54f35` | fix(android): Manage closes and drops its PIN when the library shows again or on ON_STOP; picker prompt given up (#1) |
| `dd876244` | refactor(android): one shared `ManageActions` in ui-common (#4) |
| `b37b0077` | fix(android): regenerated bindings + #3's Android half (first profile only after a sync round) |

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

- `cargo test` (workspace, incl. code_standards): **1792 passed, 0 failed, 4 ignored**.
  `cargo clippy --all-targets --all-features -D warnings`: clean.
- `./gradlew --rerun-tasks testDebugUnitTest :core:rust:compileDebugAndroidTestKotlin :ui-tv:compileDebugAndroidTestKotlin lint`:
  green, **2337 unit tests passed, 0 failed**, run against the regenerated bindings. There are
  8 new tests: one WatchSync test, four first-profile view-model tests, one never-synced
  contract case, and one more each in the phone and TV picker screens (the old
  "Try again beside the first-profile form" tests were replaced).
- The bindings are regenerated from the same Rust as `e22bd863`. After `b37b0077`,
  `git status` under `android/core/rust/src/main/kotlin` is clean.
- `RealCoreContractTest` compiles but has not been run (no adb).

## #3 on Android (`b37b0077`)

1. `CoreProfileCalls` maps `NotSynced` to `model.ProfileOutcome.NotSynced`, which reads
   "Waiting for this household’s profiles…" (`WAITING_FOR_HOUSEHOLD`).
2. `WatchStateRepository.syncedOnce` is read in `reload()`. `DefaultWatchSync.attempt` also
   re-reads while it is still false, because a new household's first round pulls 0 rows and
   can outlast the picker's 5 s wait.
3. `FakeCore.syncedOnce` defaults to `true` (a device that has synced). `FakeProfiles.createFirst`
   answers `NotSynced` last, as `manage.rs` does.
4. `ProfileUiState.Picking.synced`:
   - `needsFirstProfile` needs a round to have landed.
   - New `awaitingHousehold` covers no grown-up and no round yet.
   - `needsAdmin` now requires a grown-up to exist.
5. The phone and TV pickers show the waiting line with Try again in place of the form. The TV
   `focusSpot` lands on Try again when there are no tiles. Try again no longer sits beside the
   form once a round has said the household is empty.
6. `FirstProfileWaitsForASyncRoundTest` covers four cases:
   - before any round: not offered, and refused with the waiting sentence;
   - an empty round: offered and made;
   - a round that brought profiles: tiles only;
   - a late round: the form appears when it lands.
7. `CoreContract.core(synced: Boolean = true)` plus a new `aFirstProfileWaitsForASyncRound` case.
   `RealCoreContractTest` builds its core lazily and seeds `state_meta(first_round_imported)`
   through Android SQLite before the core opens the directory.

## Concerns

- **Stale Gradle build cache.** After the bindings change, `core:data`'s unit-test compile
  was restored from cache without the new delegate method. `WatchStateLocalDayTest`'s
  `CoreInterface by seeded` then threw `AbstractMethodError: … hasSyncedOnce`, and `clean`
  did not help because the cache restored it again. `--rerun-tasks` (or a `--rerun` on that
  compile) fixes it. Expect the same on other machines after pulling: rebuild with
  `--rerun-tasks` once.
- `RealCoreContractTest` seeds the marker by its key (`first_round_imported`). If that key is
  renamed in `state/sync/first_round.rs`, the seed must follow.
- `error::tests::io_diagnostics_keep_nested_context_and_the_underlying_cause` failed once in a
  parallel run and passed on every rerun. It is a tracing-subscriber test, flaky, and not
  touched by this branch.
- A device upgraded from 0.110.0 counts as not synced until its next round. That only matters
  for `create_first`, and an upgraded device already has grown-ups.
- No device walk was done (no adb, as instructed).
