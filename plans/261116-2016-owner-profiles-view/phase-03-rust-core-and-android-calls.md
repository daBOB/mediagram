# Phase 03 — Rust core and Android calls: the same two optional fields

Status: todo · Depends on: Phase 01 · Parallel with: Phase 02

## Context

- `crates/mediagram-core/src/state/profiles/manage.rs` (167 lines): `create_grown_up` ~L75, `create_kid` ~L91
- `crates/mediagram-core/src/state/profiles/manage/checks.rs`: `check(..., target_id: Option<&str>, ...)`
  refuses `NotFound` when `target_id` names no row; `household_of(actor_id)` (Home → `None`)
- Live households: `crate::state::households::live(conn, &profiles)`; admin write:
  `state/households/manage/writes.rs::admin(conn, household_id, profile_id, now)` (pub(super) —
  widen to pub(crate) if needed); `move_to.rs` shows the admin-less pattern.
- `crates/mediagram-core/src/api/state/profile_roles.rs` (165 lines, uniffi export): `create_grown_up` ~L36, `create_kid` ~L52
- Android: `android/core/model/src/main/kotlin/ProfileRequests.kt` (`CreateGrownUp` :28-33,
  `CreateKid` :35-42), `android/core/data/src/main/kotlin/CoreProfileCalls.kt` :26-28,
  fake `android/core/testing/src/main/kotlin/testing/FakeProfiles.kt` (`add(..., maker)` :221, household :232),
  contract `android/core/testing/src/main/kotlin/testing/HouseholdContract.kt`.
- Bindings: `scripts/generate-android-bindings.sh`; native lib: `scripts/build-android-core.sh`
  (a stale `.so` passes the build and crashes the app at launch).

## Interfaces

- Rust state: `create_grown_up(&mut self, actor_id: &str, pin: &str, name: &str, new_pin: &str, household: Option<&str>) -> Answer`
  and `create_kid(&mut self, actor_id: &str, pin: &str, name: &str, kids_age: u8, daily_limit: Option<u16>, parent_id: Option<&str>) -> Answer`
- uniffi: `create_grown_up(actor_id: String, pin: String, name: String, new_pin: String, household: Option<String>)`
  and `create_kid(actor_id: String, pin: String, name: String, kids_age: u8, daily_limit: Option<u16>, parent_id: Option<String>)`
- Kotlin: `class CreateGrownUp(actorId, pin, name, newPin, val household: String? = null)`,
  `class CreateKid(actorId, pin, name, age, dailyLimit: Int? = null, val parentId: String? = null)`
- Behaviour identical to Phase 02 (same refusals, admin-less household → new grown-up is admin).

### Task 3.1: Core state + API

- [ ] **Step 1: Failing tests** in `crates/mediagram-core/src/state/profiles/manage_tests.rs`, same names as
  Phase 02's seven tests in snake_case (e.g. `the_owner_adds_a_grown_up_straight_into_another_household`).
- [ ] **Step 2:** `cargo test -p mediagram-core profiles::manage` → new tests FAIL.
- [ ] **Step 3: Implement** the signatures above; update every Rust caller (`cargo build` finds them;
  `crates/mediagram-core/tests/api_surface.rs` too).
- [ ] **Step 4:** `cargo test -p mediagram-core && cargo clippy -p mediagram-core --all-targets -- -D warnings` → PASS.
  Format only the files you touched (never workspace-wide `cargo fmt`).
- [ ] **Step 5: Commit** — `feat(core): the owner adds a profile straight into any household`

### Task 3.2: Bindings, Android request types, fake

- [ ] **Step 1:** `scripts/generate-android-bindings.sh`, then `scripts/build-android-core.sh`; the regenerated
  `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt` is tracked and goes in this task's commit.
- [ ] **Step 2: Failing tests** in `HouseholdContract.kt` (runs on the fake via `FakeCoreContractTest`
  and on the real core via `RealCoreContractTest`):
  `ownerAddsAGrownUpIntoAnotherHousehold`, `ownerAddsAKidForAGrownUpElsewhere`, `anAdminCannotAddIntoAnotherHousehold`.
- [ ] **Step 3: Implement** the Kotlin fields, `CoreProfileCalls` passing them through, and the fake
  (`FakeProfiles.add` takes the target household / parent instead of always the maker's). `FakeProfiles.kt`
  is 321 lines: move the create functions into a new `FakeProfileCreates.kt` while there.
- [ ] **Step 4:** `cd android && ./gradlew :core:testing:testDebugUnitTest :core:data:testDebugUnitTest :core:model:testDebugUnitTest`
  → PASS. `:core:rust:connectedAndroidTest` with `ANDROID_SERIAL=caad49da` when the tablet is on.
- [ ] **Step 5: Commit** — `feat(android): requests carry the household or the parent a new profile goes to`

## Success criteria

Real core and fake agree on the three contract cases; existing callers compile unchanged (defaults).
