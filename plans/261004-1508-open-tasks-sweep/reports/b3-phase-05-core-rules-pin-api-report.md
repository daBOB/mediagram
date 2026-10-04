# B3 phase 05: core rules, PIN, wait, operations, uniffi API

2026-10-04, fullstack-developer subagent, worktree branch `worktree-agent-a8dee5dd090a40eca`, reset to main `12216185` (0.108.0) first.
Plan `260928-0047-profile-roles-pins-kids-age-limits`, phase 05. Built to the web on main (the spec), the 2026-10-04 contract amendments, the pre-flight rulings and the phase-04 hand-over.

**Status:** done (pending merge). Not pushed. Versions not bumped, changelog not touched.

**Bindings changed and the native `.so` was rebuilt** here, all four ABIs, with `ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/generate-android-bindings.sh`. The `.so` is gitignored, so **the lead must rebuild it on main after merging**. A stale `.so` against these bindings fails at launch.

## Commits
| Hash | What |
|---|---|
| `ce7591a4` | 05a. Rules, PIN, per-profile wait and the eight operations under `state/profiles*`; the four fixture runners. No uniffi change. |
| `82b90e81` | 05b. The uniffi surface, the regenerated Kotlin bindings, and the Android side made to compile and pass. |

## Test-first record
- **RED 05a, compile.** Fixture runners written first: `cargo test --test shared_watch_state_fixtures` failed with 3× E0432 (`profiles::{pin, pin_wait, rules}` unresolved).
- **RED 05a, runtime.**
  - `a_kid_made_without_a_chosen_limit_stores_twelve_dated_zero` failed: `left: (None, 0) right: (Some(12), 0)`.
  - With `manage.rs` stubbed (`unimplemented!()`) under the 52 manage tests: **14 pass / 51 fail** in `state::profiles`.
- **RED 05b, compile.** Integration tests written first: `cargo test --tests --no-run` gave about 70 errors. They included: no `create_first_admin` ×7, `create_kid` ×8 and `unlock_profile` ×7; no field `admin`/`has_pin`/`kids_age` on `Profile`; no `kids_from_six`; and `set_kids` taking `bool`.
- **GREEN.**
  - `cargo test -p mediagram-core`: **725 pass / 0 fail**. Baseline was 660.
  - `cargo test` across the workspace, `code_standards` included: **1781 pass / 0 fail**, 4 ignored.
  - `cargo clippy --all-targets --all-features -- -D warnings`: clean.
  - All four new fixture runners run real cases, with no "skipping":
    - `pin-hash.json`: 2
    - `profile-rules.json`: 42
    - `profile-names.json`: 9
    - `pin-wait.json`: 8
- **Android.** `./gradlew -q --continue testDebugUnitTest :core:rust:compileDebugAndroidTestKotlin lint`: exit 0.
  - **2159 unit tests pass / 0 fail.** `core:testing` has 45, including the 6 new contract cases and 4 `FakeProfilesTest`.
  - `RealCoreContractTest` compiles. Lint is clean.
  - The first run, before the test cleanup in decision 1, failed 4 `ProfileViewModelTest` and 3 `TvHousekeepingTest` cases. Those are the PIN-less add/remove paths.

## What was built (Rust)
`state/profiles/`, every file at most 200 lines:
- `pin.rs` 47
- `pin_wait.rs` 65
- `rules.rs` 87
- `outcome.rs` 26
- `role_rows.rs` 187
- `manage.rs` 168
- `manage/checks.rs` 118
- `profiles.rs` 190
- `state/mod.rs` 164
- `api/state.rs` 181
- `api/state/profile_roles.rs` 113

What each part does:
- **PIN.** Exactly 4 ASCII digits. The hash is lowercase hex SHA-256 of `salt + pin`, with a 16-byte salt. Comparison folds every byte and has no early exit, and a stored hash of the wrong length never matches.
- **Wait.** Kept per profile, as `pin-wait.json` requires: `seconds_left(id, now)`, `failed(id, now)`, `succeeded(id)`, with the clock passed in. It is a `Mutex<PinWait>` on `StateDb`, taken inside the connection lock as `ticks` is.
- **Rules.** `allowed` is a port of `profiles-rules.ts`: admin counts only on a grown-up. `name_taken` uses `normal_name(clean_name(·))` on both sides.
- **Operations.** `ProfileManager`, reached via `StateDb::manage(|m| …)` (`manage_at(now, …)` in tests), is a one-to-one port of `ProfileManager` in `profiles-manage.ts`. The order is the web's:
  1. invalid
  2. name-taken (create-first, create-grown-up and create-kid only)
  3. not-found
  4. structural not-allowed
  5. no-pin
  6. wait, only right before a comparison
  7. wrong-pin; a malformed current PIN counts
  8. the rule's not-allowed

  The exceptions:
  - A self set-pin on a PIN-less grown-up is unproven.
  - Unlock: a kid opens freely.
  - Claim-admin: `invalid` only for a PIN-less grown-up target, and "an admin exists" counts only a grown-up's claim.
  - Create-first: refused once any grown-up exists; kids do not count.
- **Writes.**
  - PIN and limit stamps are `MAX(now, MIN(stored, 2^53−2)+1)`. Every limit write re-stamps, the same age included.
  - The admin claim is plain `now`.
  - Removing a grown-up deletes `kids = 1 AND parent_id = target` in one transaction; removing a kid deletes only that kid.
  - A kid made here stores its chosen limit dated at creation, with the parent set to the actor. A grown-up's first PIN is dated at creation.
  - `profiles::create`, the sync path, now stores a kid at **12 dated 0**, as the web's `insertProfile` does. That closes the phase-04 gap.
- **Profile.** It gains `kids_age` (`Some(6|12)` on a kid, with an unstored limit reading 12), `parent_id`, `admin` and `has_pin`. A kid never reports `admin` or `has_pin`, whatever its columns hold (contract §8).
- **uniffi surface.**
  - `ProfileOutcome { Done, Invalid, NameTaken, NotFound, Wait { seconds: u32 }, NoPin, WrongPin, NotAllowed }`.
  - Eight async calls on `Core`, exactly as contract §9.
  - `set_kids(set_id, age: Option<u8>)`.
  - `StateSnapshot.kids_from_six` is the last field, defaulted.
  - `create_profile` and `delete_profile(id)` are gone.
- **Kotlin bindings.** Regenerated and never hand-edited. The only removals are `createProfile`, `deleteProfile(id)` and `setKids(…, marked)`. The new signatures match contract §9: `createKid(…, kidsAge: UByte)` and `Wait(seconds: UInt)`.
- **Integration tests.** `tests/state_seed/mod.rs` builds a household through the public surface: the first grown-up is the admin, every later one is added by it, kids belong to the admin.
  - Callers updated: `api_surface`, `state_retirement`, `viewing_stats_api` and `achievements_surface`.
  - New surface tests:
    - one outcome per reason across the boundary
    - the per-profile wait
    - a kid never admin or PIN-holding
    - `kids_from_six`
    - storage failure answering `Invalid` for all eight calls

## What was built (Android, compile-and-pass only, no UI)
- `core/testing/FakeProfiles.kt` (new) holds the fake's household rules, in the same order as the core. It keeps PINs in `roles.pins` and its clock in `roles.nowMs`, which are the names phase 06's `FakeProfilesTest` sketch uses. `shown()` applies the kid rule.
- `FakeCore` is **583 lines**, under `LargeClass` 600. Its eight new overrides delegate to `roles`, and `setKids(setId, age)` goes to `FakeWatchState`. `FakeWatchState` now tracks the age per mark and adds `kidsFromSix`.
- `CoreContract` (547 lines) makes `freshProfile` go through `createFirstAdmin`/`createGrownUp`. It gains six cases that the fake and the tablet's real core must both pass:
  - first-admin
  - a kid's owner and its open unlock
  - the refusal order
  - the per-profile wait
  - removing a grown-up takes its kids
  - from-6 marks
- `FakeProfilesTest` (new, 4 tests) covers what the contract cannot share: claim-admin, a first own PIN, the wait ending on the fake's clock, and a kid shown neither admin nor PIN.
- `WatchStateRepository` gets the minimal call-site change (see decision 1). `setKids(setId, marked)` sends `12u`/`null`, which is today's behaviour exactly.

## Decisions for the lead
1. **The repository's PIN-less `createProfile`/`deleteProfile` now refuse (null/false)**, as interface defaults, and the implementations are gone.
   - The core has no PIN-less create or remove any more, and the repository and `ProfileViewModel` have no PIN to give. Any honest mapping is a refusal, so in the 05b-only app the picker's Add and Remove say "Could not …".
   - Phase 06 Task 1 replaces this pair with `manage(request)`. The 05b commit should land with 06, as the plan says.
   - Tests changed to match:
     - `WatchStateRepositoryTest`: the two create tests become one test, "refused without a PIN".
     - `WatchStateChosenProfileTest`: create and delete go through the core, then a reload.
     - `WatchStateOwnershipTest`: its creation branch is gone. It could never reach the core now.
     - `ProfileViewModelTest`: **4 tests deleted** (`addingAProfileListsItWithoutChoosingIt`, `addingAKidsProfilePassesTheFlagThrough`, `removingAnotherProfileKeepsTheWayToStay`, `removingTheChosenProfileTakesAwayStayAsIAm`). These are the same four that phase 06 Step 8 deletes or replaces; the behaviour they pinned no longer exists.
     - `ui-tv` `TvHousekeepingTest`: **3 tests deleted**, the end-to-end PIN-less removal through the picker (`removingAProfileAsksWhichThenWhetherWithCancelFirst`, `removingTheProfileBeingWatchedStopsOfferingToStayAsIt`, `removingTheLastProfileLandsOnNewProfile`). Phase 06 Step 9 deletes them too, and phase 07 replaces them with Manage-flow tests. `cancellingTheQuestionRemovesNobody` still holds and stays.
     - No UI source changed.
2. **A new kid's FSK 6 default is not in the core.** The web's `createKid` requires `kidsAge`, and contract §8 says a kid needs `kidsAge`. So `create_kid(…, kids_age: u8)` takes the age explicitly, and FSK 6 is the UI's initial choice (phase 03 on the web, phases 06/07 on Android). Only a kid made by sync starts at 12 dated 0.
3. **The order in the module docs follows the web code, not contract §3's numbered list.** The contract lists no-pin after the wait, but the web checks `no-pin` before `wait`, and the brief's order agrees. Under a per-profile wait the two can only differ for a profile that has no PIN, and such a profile cannot have a count anyway.
4. **The current PIN is not validated as `invalid`.** It is compared, it fails, and it counts as `wrong-pin`. This is the web's rule.

## Concerns
- `profiles.rs` is 190/200 and `role_rows.rs` is 187/200. The next addition to either should split first.
- The Kotlin doc comments in the bindings come from the Rust docs, so they say `None` where Kotlin says `null`. This is cosmetic.
- `android/.kotlin/` (the Kotlin daemon's cache) shows up untracked in the worktree. It was not committed; it may want a `.gitignore` line.
- **Not verified on a device** (no adb, by instruction). `RealCoreContractTest` gains six cases, compiled but not run. Phase 06's device round should run it against the rebuilt `.so`.
