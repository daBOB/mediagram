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

---

## Re-review follow-up (2026-10-05): M1, L1, M2

Review: `b3-security-fixes-rereview-report.md`. M2 was taken after the user chose its rule.

### Commits

| Hash | What |
|---|---|
| `3d5256c2` | M1 + L1: the web holds a first profile too; the mark is scoped to the library followed now (core + web). Bindings regenerated (docs, `refresh_library` checksum) |
| `653e0498` | M2: a first PIN never replaces a PIN set earlier on another device (core + web + shared fixtures + contract) |

### RED first

- **L1 core:** `sync_tests::…::a_round_of_the_last_library_is_no_round_of_the_one_followed_now`
  failed with `left: Done, right: NotSynced`.
- **M1/L1 web:**
  - `state-first-profile-waits.test.ts` had 7 failures: no `not-synced`, no `heard`, nothing marked.
  - `profile-picker.test.ts` had 2 failures: no waiting line.
- **M2 shared fixtures:**
  - Web: 4 failures (the parse of `proven`, and 3 merge cases).
  - Core: the merge fixture failed on the kid-tablet case.
- **M2 core:**
  - The import test failed with `left: 1, right: 0`: a newer first PIN was taken.
  - The `proven` assertions failed on writes.
  - The kid-tablet test failed with `left: Done, right: WrongPin`: after the merge, `0000` opened
    André. That is the reported scenario, reproduced.
- **M2 web:** `state-pin-proof.test.ts` had 3 failures (writes, import order, kid-tablet).

### M1: the web holds a first profile

- `ProfileManager.createFirst` answers `not-synced` (HTTP 409) last, after every other check
  says yes. It does so until `Household.heard()` (`household-heard.ts`).
- `GET /api/profiles` now carries `heard`.
- The picker shows "Waiting for this household’s profiles…" with Try again
  (`household-waiting.js`). A refusal reads "The household’s profiles have not arrived yet."
  Android uses the same two strings.
- A player that syncs with nobody never waits.
- Signed out, the channel names no library, so a list that reads as empty never counts.
- `state_meta` access moved to `state-meta.ts`. That keeps `store.ts` at its line ceiling.
- The false comments are corrected: `outcome.rs`, `manage.rs`, `ProfileWords.kt`, and the
  contract §3 (rewritten for both surfaces).

### L1: the mark is per library

- **Core:**
  - The mark's value is the handle whose round was imported. It is written after the
    import commits (`Memo` carries the handle).
  - `refresh_library` records `followed_library` *before* it installs. Every first choice and
    every switch goes through it, so an install that fails leaves the device waiting.
  - The two must match. A device with no `followed_library` yet trusts any round.
  - The UniFFI surface is unchanged apart from docs.
- **Web:** `TelegramStateChannel.library()` is the connection's current channel. It is read
  before and after the round, and a round whose channel changed under it marks nothing.
  `createFirst` compares the mark against the channel followed now.

### M2: the user's rule

A first PIN never replaces a PIN set earlier on another device. Only someone who knows the
current PIN, or the admin's Reset PIN, can replace it.

- **Wire:** `pin.proven?: true`, where only the literal `true` parses. Without it a PIN is a
  first PIN, and so is every PIN from before this change.
- **Which writes are proven:**
  - First: `create-first`, `create-grown-up`, a claim that takes a first PIN, and a self
    `set-pin` with nothing to prove.
  - Proven: a change that proved the old PIN, and an admin reset.
- **Merge:** a proven PIN beats any first PIN. The newest proven PIN wins. Between first PINs
  the oldest wins. Ties go by device id. This is web `keepPin`/`pinBeats` and core `keep_pin`.
- **Import:** takes a merged PIN in the same order, or over no PIN at all.
- **Schema:** `pin_proven INTEGER NOT NULL DEFAULT 0`, as web v13 and core v9.
- **Fixtures:**
  - `profile-roles-merge.json` gains 5 cases, the kid-tablet scenario with the admin among
    them. The 2 existing PIN cases are now proven PINs.
  - `profile-roles-record-parse.json` gains a case for parsing `proven`.
- Two unit tests encoded "newest first PIN wins". One is in `state-roles-merge.test.ts`; the
  other was the import test, in `roles_tests.rs` and `state-roles-exchange.test.ts`. They now
  state the rule for proven PINs, and the first-PIN order is tested separately.

### Verification at `653e0498`

- **Web:** `bun run lint` clean, `bun run typecheck` clean, `bun test` **3025 pass, 0 fail**.
- **Rust:** `cargo test` (workspace) **1797 passed, 0 failed, 4 ignored**; clippy `-D warnings` clean.
- **Android:** `--rerun-tasks testDebugUnitTest :core:rust:compileDebugAndroidTestKotlin
  :ui-tv:compileDebugAndroidTestKotlin lint` green, **2337 passed, 0 failed**.
- **Bindings:** regenerated after `653e0498`. The `.so` is rebuilt, the bindings are
  byte-identical, and `git status` is clean.

### Concerns

- **`/tmp` is full.** It is a 31 GB tmpfs at 97%; 19 GB of it is the shared session scratchpad
  (other agents' build dirs). Robolectric runs failed at random with `SQLiteException` and
  cache errors. Keeping the test JVMs' `java.io.tmpdir` on `/home` (an init script) made the
  runs clean. The failures were environmental, not from this branch.
- **PINs changed at 0.110.0 that have not reached every device.** Every PIN stored before is a
  first PIN, so such a change could lose to the older value once, when the two meet. PINs are
  one day old, and any change made from now on is proven.
- **The web's Try again only re-reads the list.** The next round comes from the server's own
  timer. If the server's start round failed, the picker waits up to one sync period.
- **The real-core contract seed is unchanged.** It writes the mark with no `followed_library`,
  which still counts. It has not been run on a device (no adb).
