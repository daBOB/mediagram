# Phase 05 — Core: rules, PIN, wait, uniffi API

## Context links

- Contract (authoritative): [shared-contract.md](shared-contract.md) §2 rule, §3 outcomes + order, §4 wait,
  §5 PIN, §9 uniffi API, §10 fixtures
- Spec: `docs/superpowers/specs/2026-09-28-profile-roles-design.md` §1 (honest ceiling), §2, §6
- Web reference (phase 02 creates it): `web/src/state/profiles*.ts`, fixtures
  `web/test/fixtures/watch-state/{pin-hash,profile-rules}.json`
- Builds on [phase-04](phase-04-core-schema-sync-merge.md) (schema v7, `rows::{kids_from_six,set_kids}`)
- Core today: `crates/mediagram-core/src/state/profiles.rs`, `src/api/state.rs`, `src/api/state/collections.rs`
- Bindings: `scripts/generate-android-bindings.sh`, `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt`
- Bumping: [phase-08 § Bumping](phase-08-verify-docs-version.md)

## Overview

Priority P1 (06 needs its bindings). Status: pending. Effort ~6h.
The rule half of the core: the pure `allowed()`, the PIN format and hash, the wrong-PIN wait, the eight
management operations checked in the contract's order (amended 2026-09-28: structural refusals before
the wait, the wait only before a PIN comparison, and `create_first_admin` for a device that knows no
grown-up), and the uniffi surface exactly as contract §9 — then the regenerated Kotlin bindings and a
rebuilt native library.

**Lands with phase 06** (plan.md § Dependencies): the regenerated bindings remove `createProfile`, change
`deleteProfile`/`setKids`, and add abstract members `FakeCore` does not implement, so the Android build is
red from this phase's last commit until 06's first. Commit locally; push only after 06.

## Key insights (verified)

- **Deps are already there:** workspace `sha2 = "0.11"`, `getrandom = "0.4"`, `hex = "0.4"`
  (`Cargo.toml:18-20`), all in `crates/mediagram-core/Cargo.toml` `[dependencies]`. House patterns:
  `hex::encode(Sha256::digest(…))` (`package/reader.rs:50`), `getrandom::fill(&mut bytes).expect("the OS random source is available")`
  (`state/sync/device.rs:40`, `api/account/auth.rs:40`).
- **Contract §5 vectors verified** with `sha256sum`: `sha256("00112233445566778899aabbccddeeff1234")` =
  `f377124b…d4d80924`, `sha256("ffeeddccbbaa998877665544332211000000")` = `48618b45…5137f8`.
- **Where the wait lives:** contract §4 says one per `Core`. `api/mod.rs` is exactly 200 lines (`Core` at
  `:52-71`) — no room for a field. `StateDb` is created once per `Core` (`api/mod.rs:79`, the only
  non-test `StateDb::new`) and `state/mod.rs` is 194 lines (`StateDb` at `:45-48`, `new` at `:57-62`), so
  the count is a `Mutex<PinWait>` field on `StateDb` (+3 lines → 197), reached only through
  `StateDb::guarded`, an `impl StateDb` block in `profiles/manage.rs` (a descendant of `state` may read
  the private field). Lock order is always wait → connection; nothing else takes the wait lock.
- **Injectable clock = a parameter.** Every function that reads time takes `now: i64` (ms); only
  `StateDb::guarded` supplies `profiles::now_ms()` (`profiles.rs:151-156`). Tests pass any `now`.
- **"Nothing here throws"** (`api/state.rs:6-10`): `StateDb::with` logs and returns `None` on any failure
  (`state/mod.rs:78-102`); `guarded` maps that to `ProfileOutcome::Invalid` (contract §9 last line).
- **`profiles.rs` is 160 lines**; `Profile` at `:21-30` already uses `#[uniffi(default = false)]`, and the
  generated Kotlin gives defaulted fields (`mediagram_core.kt:3950-3962`). New modules go under
  `state/profiles/` so `profiles.rs` stays ~192: `pin.rs`, `pin_wait.rs`, `rules.rs`, `request.rs`,
  `role_rows.rs`, `manage.rs`. `api/state.rs` is 180: the eight calls go to a new
  `api/state/profile_roles.rs`, the pattern `api/state/collections.rs` set.
- **uniffi 0.32.1 locked** (`Cargo.lock:3216-3219`). `#[uniffi(default = [])]` is supported
  (`uniffi_macros-0.32.x/src/default.rs`, `DefaultValue::EmptySeq`). An enum with a field becomes a Kotlin
  `sealed class` with `object` for unit variants and `data class` for `Wait` (`uniffi_bindgen`
  `EnumTemplate.kt`). `u8` is Kotlin `UByte`, `u32` is `UInt`.
- **Removing a grown-up in a transaction:** `profiles::delete` (`profiles.rs:73-75`) gets the kids
  delete and its own transaction; so `role_rows::apply` must not wrap `Remove` in another one —
  `unchecked_transaction` inside a transaction fails with "cannot start a transaction within a transaction".
- **Amended order (contract §3, 2026-09-28):** structural refusals (a kid actor; claim-admin with an
  admin already or on a kid; create-first where a grown-up exists) come *before* the wait, and the wait
  answers only when a PIN is about to be compared. So `check_pin` asks "is there a PIN?" before "is
  there a wait?": a PIN-less grown-up is told `no-pin` even mid-wait, since nothing would be compared —
  the only reading under which §3's items 4 and 5 both hold.
- **Bootstrap:** `create_first_admin(name, new_pin)` (contract §3 `create-first`, §12 "no grown-up at
  all" picker state) replaces the only other way a first profile could appear — sync. It checks
  "no grown-up here" (`kids = 0`), not "no profile": a device holding only kids may still create one.
  It also lets the integration tests build a household through the public surface, so they need no
  SQL seeding.
- **Callers that go away with `create_profile`/old `delete_profile`:** Rust — `api/state.rs:52-63,86-88`;
  `tests/api_surface.rs:144,149,271,277,368,426`; `tests/state_retirement.rs:32,51,91,98`.
  `set_kids(…, bool)`: `tests/api_surface.rs:183,254,417`. No sync path uses either
  (`profiles::create` stays for `profile_named_with_creation`, `profiles.rs:124-141`).
- **Bindings and `.so`:** `scripts/generate-android-bindings.sh` cross-compiles all four ABIs (via
  `scripts/build-android-core.sh`) then regenerates and whitespace-trims the Kotlin. It needs
  `ANDROID_NDK_HOME`, unset in agent shells; the NDK is at `/home/andre/android-sdk/ndk/28.2.13676358`
  (used in `plans/reports/merge-main-second-260926-0145-report.md`). `cargo-ndk 4.1.2` is installed.
  Moving exports between files reorders the generated file (`3765c6d8`), so regenerate, never hand-edit.
- **Kotlin that breaks when the bindings regenerate** (phase 06 fixes; listed so nobody is surprised):
  - `android/core/testing/src/main/kotlin/testing/FakeCore.kt:404` (`createProfile` override — member gone),
    `:432` (`deleteProfile(id)`), `:450` (`setKids(setId, marked: Boolean)`), and it does not implement
    `createFirstAdmin`, `createGrownUp`, `createKid`, `deleteProfile(actorId, pin, id)`, `unlockProfile`, `claimAdmin`,
    `setPin`, `setKidsAge`, `setKids(setId, age)`
  - `android/core/testing/src/main/kotlin/testing/CoreContract.kt:70,79,89,90,97` (and so
    `core/rust/src/androidTest/kotlin/rust/RealCoreContractTest.kt`, `core/testing/src/test/kotlin/testing/FakeCoreContractTest.kt`)
  - `android/core/data/src/main/kotlin/WatchStateRepository.kt:255,271,306`
  - `android/core/data/src/test/kotlin/WatchStateRepositoryTest.kt:37,68`,
    `android/core/data/src/test/kotlin/WatchStateOwnershipTest.kt:50`
  - Still compile: positional `StateSnapshot(…6 args…)` (`FakeCore.kt:440`, `WatchStateRepositoryTest.kt:55,80`)
    because `kids_from_six` is last and defaulted; `Profile(id, name[, kids])` (`FakeCore.kt:406`,
    `WatchStateOwnershipTest.kt:54`, `ProfileOwnershipTest.kt:28`) because every new field is defaulted.
    Repository-level overrides (`WatchSyncTest.kt:63,86`, `ProfileViewModelTest.kt`, `FakeWatchState.kt`, …)
    are 06's own interface change, not a bindings break.
- clippy `-D warnings` (`scripts/check.sh`); `too_many_arguments` fires above 7 — the widest function here
  takes 6. Edition 2024 with `rust-version = 1.87`: no let-chains.

## Outcome order (contract §3, amended 2026-09-28)

`invalid` → `not-found` → structural `not-allowed` → `wait` → `no-pin` → `wrong-pin` → rule `not-allowed`.
The wait answers **only when the call is about to compare a PIN**, so a caller with no PIN to compare —
a kid, a PIN-less grown-up, a first PIN being set — never waits. One pipeline for the five actor calls;
each cell is pinned by a test in `manage_tests.rs`.

| Call | 1 invalid | 2 not-found | 3 structural not-allowed | 4 wait | 5–6 no-pin / wrong-pin | 7 rule |
|---|---|---|---|---|---|---|
| create-grown-up, create-kid, remove, set-pin, set-kids-age | `name` blank, `new_pin` not 4 digits, age ∉ {6,12}. The current `pin` never: it is compared, so a malformed one is `wrong-pin` and counts | actor, then target | actor is a kid | when the actor has a PIN | no PIN → `no-pin` (never waits); mismatch → `wrong-pin` (counts); match resets the count | `allowed()` |
| set-pin on self, grown-up with no PIN yet | `new_pin` | actor / target | – | never | skipped (`pin` ignored, may be `""`) | `allowed()` (true) |
| unlock | – | target | – | grown-up with a PIN only; a kid opens at once | grown-up: `no-pin` / `wrong-pin` | – |
| claim-admin | `pin` (it may become the PIN) | target | an admin exists, or the target is a kid | when the target has a PIN | PIN → must match (`wrong-pin`); none → `pin` becomes it | – |
| create-first (`create_first_admin`) | `name`, `new_pin` | – | a grown-up exists here (kids alone do not count) | never | – | – |

A right PIN resets the count; the 5th wrong one starts 60 s during which every comparison-bound call
answers `wait` first.

## Requirements

Functional
- `pin::valid` (exactly `/^[0-9]{4}$/`), `pin::hash(salt, pin)` = lowercase hex SHA-256 of `salt + pin`,
  `pin::new_salt()` = 16 random bytes hex, `pin::verify` compares full digests without early exit.
- `PinWait` with `MAX_WRONG_PINS = 5`, `WAIT_MS = 60_000`, seconds rounded up, count 0 after a wait.
- `rules::allowed(profiles: &[RoleView], actor_id, action, target_id) -> bool`, pure, per §2.
- `Profile` gains `kids_age`, `parent_id`, `admin`, `has_pin` (never the hash); a kid with no stored limit
  reads 12, and a new kid stores 12 (the web's `createProfile` does, phase 01).
- Removing a grown-up removes the kids whose `parent_id` is it.
- The eight calls of §9 — `create_first_admin` included — plus `set_kids(set_id, age: Option<u8>)` and
  `StateSnapshot.kids_from_six`; `create_profile` and `delete_profile(id)` removed from the API.
- The order of § Outcome order, the wait only before a PIN comparison.
- Runners for `pin-hash.json` and `profile-rules.json`.

Non-functional
- Every file ≤ 200 lines; clippy clean; bindings regenerated from the rebuilt `.so`, all four ABIs.

## Architecture

```
Kotlin ─► Core::{create_grown_up, create_kid, delete_profile, set_pin, set_kids_age}   (api/state/profile_roles.rs)
          Core::{unlock_profile, claim_admin, create_first_admin}
          │  blocking pool
          ▼
StateDb::guarded(call)  — lock pin_wait → with(conn) → call(conn, &mut wait, now_ms()) → None ⇒ Invalid
          ▼
manage::change(conn, wait, now, actor_id, pin, Change)
   request.well_formed()?            → Invalid
   role_rows::load(conn) (RoleView + (hash, salt))
   actor / target exist?             → NotFound
   actor is a kid?                   → NotAllowed (structural, before any wait)
   first-PIN exception? else check_pin: no PIN → NoPin; wait.remaining → Wait;
                                        pin::verify → succeeded() | failed() + WrongPin
   rules::allowed(views, actor, action, target) → NotAllowed
   role_rows::apply(conn, now, actor, &request) → Done
Core::unlock_profile  ─► manage::unlock      Core::claim_admin ─► manage::claim_admin
Core::create_first_admin ─► manage::create_first (no grown-up here? → role_rows::first_admin)
Core::profiles        ─► profiles::list  (has_pin, never the hash)
Core::snapshot        ─► … + rows::kids_from_six
Core::set_kids(age)   ─► rows::set_kids
```

Module and line budget (all ≤ 200):

| File | Now | After |
|---|---|---|
| `state/profiles.rs` | 160 | ~192 |
| `state/profiles/pin.rs` (new) | – | ~45 |
| `state/profiles/pin_wait.rs` (new) | – | ~50 |
| `state/profiles/rules.rs` (new) | – | ~60 |
| `state/profiles/request.rs` (new) | – | ~75 |
| `state/profiles/role_rows.rs` (new) | – | ~115 |
| `state/profiles/manage.rs` (new) | – | ~160 |
| `state/mod.rs` | 194 | 197 |
| `api/state.rs` | 180 | ~168 |
| `api/state/profile_roles.rs` (new) | – | ~125 |

## Interfaces

**Consumes**
- Phase 04: schema v7 columns; `rows::kids_from_six(conn)`, `rows::set_kids(conn, set_id, Option<u8>)`.
- Phase 02: `web/test/fixtures/watch-state/pin-hash.json` (contract §5 values),
  `profile-rules.json` (`[{ name, profiles: RoleView[], actorId, action, targetId, expect }]`).

**Produces — Rust** (`crate::state::profiles`)

```rust
#[derive(Debug, Clone, Default, PartialEq, uniffi::Record)]
pub struct Profile {
    pub id: String,
    pub name: String,
    #[uniffi(default = false)] pub kids: bool,
    #[uniffi(default = None)]  pub kids_age: Option<u8>,      // Some(6|12) on a kid, None on a grown-up
    #[uniffi(default = None)]  pub parent_id: Option<String>,
    #[uniffi(default = false)] pub admin: bool,
    #[uniffi(default = false)] pub has_pin: bool,
}
#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum ProfileOutcome { Done, Invalid, NotFound, Wait { seconds: u32 }, NoPin, WrongPin, NotAllowed }

pub mod pin   { pub fn valid(pin: &str) -> bool; pub fn hash(salt: &str, pin: &str) -> String; }
pub mod rules { pub struct RoleView { id, kids, admin, parent_id: Option<String> }
                pub enum Action { CreateGrownUp, CreateKid, Remove, SetPin, SetKidsAge }
                pub fn allowed(&[RoleView], actor_id: &str, Action, target_id: &str) -> bool; }

// on Core (async, uniffi-exported)
create_first_admin(name: String, new_pin: String) -> ProfileOutcome   // only while no grown-up exists here
create_grown_up(actor_id: String, pin: String, name: String, new_pin: String) -> ProfileOutcome
create_kid(actor_id: String, pin: String, name: String, kids_age: u8) -> ProfileOutcome
delete_profile(actor_id: String, pin: String, id: String) -> ProfileOutcome
unlock_profile(id: String, pin: String) -> ProfileOutcome
claim_admin(id: String, pin: String) -> ProfileOutcome
set_pin(actor_id: String, pin: String, id: String, new_pin: String) -> ProfileOutcome
set_kids_age(actor_id: String, pin: String, id: String, kids_age: u8) -> ProfileOutcome
set_kids(set_id: String, age: Option<u8>)
StateSnapshot { …, #[uniffi(default = [])] pub kids_from_six: Vec<String> }   // last field
```

**Produces — generated Kotlin phase 06 relies on** (package `uniffi.mediagram_core`; confirm spelling in
the regenerated file, Task 7):

```kotlin
data class Profile(
    var id: String, var name: String, var kids: Boolean = false,
    var kidsAge: UByte? = null, var parentId: String? = null,
    var admin: Boolean = false, var hasPin: Boolean = false)

sealed class ProfileOutcome {
    object Done : ProfileOutcome(); object Invalid : ProfileOutcome(); object NotFound : ProfileOutcome()
    data class Wait(val seconds: UInt) : ProfileOutcome()
    object NoPin : ProfileOutcome(); object WrongPin : ProfileOutcome(); object NotAllowed : ProfileOutcome()
}

data class StateSnapshot(/* progress, watched, watchlist, kids, collections, editorsChoice, */
    var kidsFromSix: List<String> = listOf())

interface CoreInterface {  // additions/changes only
    suspend fun createFirstAdmin(name: String, newPin: String): ProfileOutcome
    suspend fun createGrownUp(actorId: String, pin: String, name: String, newPin: String): ProfileOutcome
    suspend fun createKid(actorId: String, pin: String, name: String, kidsAge: UByte): ProfileOutcome
    suspend fun deleteProfile(actorId: String, pin: String, id: String): ProfileOutcome
    suspend fun unlockProfile(id: String, pin: String): ProfileOutcome
    suspend fun claimAdmin(id: String, pin: String): ProfileOutcome
    suspend fun setPin(actorId: String, pin: String, id: String, newPin: String): ProfileOutcome
    suspend fun setKidsAge(actorId: String, pin: String, id: String, kidsAge: UByte): ProfileOutcome
    suspend fun setKids(setId: String, age: UByte?)
    // removed: createProfile(name, kids): Profile?;  deleteProfile(id): Boolean
}
```

## Related code files

Modify
- `crates/mediagram-core/src/state/profiles.rs`, `state/profiles_tests.rs`, `state/mod.rs`
- `crates/mediagram-core/src/api/state.rs`
- `crates/mediagram-core/tests/{api_surface,state_retirement,shared_watch_state_fixtures}.rs`
- `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt` (regenerated, never hand-edited)

Create (under `crates/mediagram-core/`)
- `src/state/profiles/{pin,pin_wait,rules,request,role_rows,manage}.rs`
- `src/state/profiles/{pin,pin_wait,rules,manage}_tests.rs`
- `src/api/state/profile_roles.rs`
- `tests/state_seed/mod.rs`

Delete: none (files). Removed items: `Core::create_profile`, `Core::delete_profile(id) -> bool`.
Kotlin call sites are phase 06's.

## Implementation steps

All commands from `/home/andre/Workspace/mediagram`.

### Task 0: Preflight

- [ ] **Step 1:** `git fetch -q origin && git rebase origin/main`; `git log --oneline -8` shows phase 04's commits.
- [ ] **Step 2:** `ls web/test/fixtures/watch-state/{pin-hash,profile-rules}.json` — both present (phase 02).
  Missing → stop.
- [ ] **Step 3:** If phase 02 has landed, read `web/src/state/profiles*.ts` and its tests for the order of
  refusals. The amended contract §3 is the shared decision: a difference from § Outcome order is a bug on
  whichever side departs from §3 — stop and tell the lead rather than silently following either.
- [ ] **Step 4:** `cargo test -p mediagram-core -q` — green baseline.

### Task 1: PIN format and hash

- [ ] **Step 1: failing tests** — create `src/state/profiles/pin_tests.rs`:

```rust
use super::*;

#[test]
fn only_exactly_four_ascii_digits_are_a_pin() {
    assert!(valid("0000") && valid("1234"));
    for bad in ["", "123", "12345", "12a4", " 1234", "1234\n", "١٢٣٤"] {
        assert!(!valid(bad), "{bad:?}");
    }
}

#[test]
fn the_hash_is_lowercase_hex_sha256_of_the_salt_then_the_pin() {
    assert_eq!(
        hash("00112233445566778899aabbccddeeff", "1234"),
        "f377124b2c2ffeb096001d94cb0e3df86fbe20ad9981335bc624b960d4d80924"
    );
}

#[test]
fn a_new_salt_is_sixteen_random_bytes_in_lowercase_hex() {
    let (a, b) = (new_salt(), new_salt());
    assert_eq!(a.len(), 32);
    assert!(a.bytes().all(|c| matches!(c, b'0'..=b'9' | b'a'..=b'f')));
    assert_ne!(a, b);
}

#[test]
fn a_pin_verifies_only_against_the_hash_made_from_it() {
    let salt = new_salt();
    let stored = hash(&salt, "1234");
    assert!(verify(&salt, "1234", &stored));
    assert!(!verify(&salt, "1235", &stored));
    assert!(!verify(&salt, "1234", &stored[..63]));
}
```

Append to `tests/shared_watch_state_fixtures.rs` (import `use mediagram_core::state::profiles::pin;`):

```rust
#[derive(Deserialize)]
struct PinHashCase {
    salt: String,
    pin: String,
    hash: String,
}

/// The same salt and PIN make the same string on both surfaces — what lets a
/// PIN set on the television open the profile on the laptop.
#[test]
fn pin_hash_fixtures_match_the_web() {
    let Some(cases) = load::<PinHashCase>("pin-hash.json") else {
        return;
    };
    assert!(!cases.is_empty(), "pin-hash.json holds no cases");
    for case in cases {
        assert_eq!(pin::hash(&case.salt, &case.pin), case.hash, "salt {} pin {}", case.salt, case.pin);
    }
}
```

- [ ] **Step 2:** `cargo test -p mediagram-core -q` — expected FAIL to compile (`profiles::pin` missing).
- [ ] **Step 3: implement `src/state/profiles/pin.rs`**

```rust
//! A grown-up's PIN: exactly four digits, kept as a salted SHA-256 — the same
//! string the web computes for the same salt and PIN (`pin-hash.json` pins
//! the two), which is what lets a PIN set on the television open the profile
//! on the laptop.
//!
//! Not a password hash in any strong sense, and not meant to be one: anyone
//! holding the sync document can try all ten thousand PINs. The document
//! lives in the household's own channel, readable only by the account that
//! owns the library; the people this PIN is for cannot read it. It stops a
//! child tapping into a grown-up's profile — not someone with `adb`.

use sha2::{Digest, Sha256};

/// Exactly four ASCII digits; anything else is refused before it is stored.
pub fn valid(pin: &str) -> bool {
    pin.len() == 4 && pin.bytes().all(|b| b.is_ascii_digit())
}

/// Lowercase hex SHA-256 of the UTF-8 bytes of `salt + pin`.
pub fn hash(salt: &str, pin: &str) -> String {
    hex::encode(Sha256::digest(format!("{salt}{pin}").as_bytes()))
}

/// Sixteen random bytes, lowercase hex.
pub(crate) fn new_salt() -> String {
    let mut bytes = [0u8; 16];
    getrandom::fill(&mut bytes).expect("the OS random source is available");
    hex::encode(bytes)
}

/// Whether `pin` opens a profile stored as `stored` under `salt`. Every byte
/// of both digests is compared whatever the first difference, so how long a
/// wrong guess takes says nothing about how close it came.
pub(crate) fn verify(salt: &str, pin: &str, stored: &str) -> bool {
    let given = hash(salt, pin);
    let differ = given.bytes().zip(stored.bytes()).fold(0u8, |acc, (a, b)| acc | (a ^ b));
    given.len() == stored.len() && differ == 0
}

#[cfg(test)]
#[path = "pin_tests.rs"]
mod tests;
```

  In `state/profiles.rs`, after the `use` lines: `pub mod pin;`.
- [ ] **Step 4:** `cargo test -p mediagram-core -q` — green, incl. `pin_hash_fixtures_match_the_web`
  (both §5 cases, not "skipping").
- [ ] **Step 5: commit.** Bump the three manifests by pattern — see phase-08 § Bumping (patch, changelog), then:

```bash
git add crates/mediagram-core/src/state/profiles.rs crates/mediagram-core/src/state/profiles/ \
  crates/mediagram-core/tests/shared_watch_state_fixtures.rs \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(core): a grown-up's PIN, hashed as the web hashes it; release <next>"
```

### Task 2: The wrong-PIN wait

- [ ] **Step 1: failing tests** — create `src/state/profiles/pin_wait_tests.rs`:

```rust
use super::*;

const T: i64 = 1_000_000;

fn after_wrong(times: u32) -> PinWait {
    let mut wait = PinWait::default();
    for _ in 0..times {
        wait.failed(T);
    }
    wait
}

#[test]
fn four_wrong_pins_wait_for_nothing_and_the_fifth_waits_a_minute() {
    assert_eq!(after_wrong(4).remaining(T), None);
    assert_eq!(after_wrong(MAX_WRONG_PINS).remaining(T), Some(60));
}

#[test]
fn the_seconds_left_round_up() {
    let mut wait = after_wrong(MAX_WRONG_PINS);
    assert_eq!(wait.remaining(T + 30_500), Some(30));
    assert_eq!(wait.remaining(T + 59_001), Some(1));
    assert_eq!(wait.remaining(T + 59_999), Some(1));
}

#[test]
fn when_the_wait_ends_the_count_starts_again_from_nothing() {
    let mut wait = after_wrong(MAX_WRONG_PINS);
    assert_eq!(wait.remaining(T + WAIT_MS), None);
    wait.failed(T + WAIT_MS);
    assert_eq!(wait.remaining(T + WAIT_MS), None, "one wrong PIN after a wait is one, not six");
}

#[test]
fn a_right_pin_clears_the_count() {
    let mut wait = after_wrong(4);
    wait.succeeded();
    for _ in 0..4 {
        wait.failed(T);
    }
    assert_eq!(wait.remaining(T), None);
}
```

- [ ] **Step 2:** `cargo test -p mediagram-core --lib state::profiles -q` — FAIL to compile (`pin_wait` missing).
- [ ] **Step 3: implement `src/state/profiles/pin_wait.rs`**

```rust
//! Five wrong PINs in a row — for any profile — and every PIN check waits a
//! minute before it compares another. Four digits are ten thousand guesses;
//! at five a minute that is more than a day of a child pressing buttons.
//!
//! In memory, one count per `StateDb`, which is one per `Core`: a restart
//! forgets it. That is the ceiling this accepts, the same one the web's
//! per-process count has.

/// Wrong PINs in a row that start a wait.
pub const MAX_WRONG_PINS: u32 = 5;
/// How long a wait lasts.
pub const WAIT_MS: i64 = 60_000;

/// The count, and when a running wait ends. The time is always passed in,
/// never read here, so a test can say what time it is.
#[derive(Debug, Default)]
pub struct PinWait {
    wrong: u32,
    until: Option<i64>,
}

impl PinWait {
    /// The seconds left, rounded up, while a wait runs; `None` otherwise.
    /// Once a wait is over the count is back at nothing.
    pub fn remaining(&mut self, now_ms: i64) -> Option<u32> {
        let until = self.until?;
        if now_ms >= until {
            *self = PinWait::default();
            return None;
        }
        Some(((until - now_ms + 999) / 1000) as u32)
    }

    pub fn failed(&mut self, now_ms: i64) {
        self.wrong += 1;
        if self.wrong >= MAX_WRONG_PINS {
            self.until = Some(now_ms + WAIT_MS);
        }
    }

    pub fn succeeded(&mut self) {
        *self = PinWait::default();
    }
}

#[cfg(test)]
#[path = "pin_wait_tests.rs"]
mod tests;
```

  In `profiles.rs`: `pub(crate) mod pin_wait;`.
- [ ] **Step 4:** `cargo test -p mediagram-core --lib state::profiles -q` — green.
- [ ] **Step 5: commit.** Bump (patch), then:

```bash
git add crates/mediagram-core/src/state/profiles.rs crates/mediagram-core/src/state/profiles/ \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(core): five wrong PINs make the player wait a minute; release <next>"
```

### Task 3: The rule

- [ ] **Step 1: failing tests** — create `src/state/profiles/rules_tests.rs`:

```rust
use super::*;

fn view(id: &str, kids: bool, admin: bool, parent: Option<&str>) -> RoleView {
    RoleView { id: id.into(), kids, admin, parent_id: parent.map(Into::into) }
}

/// André is the admin with a kid of his own (Ben); Bea is Mia's parent;
/// Cleo has no kids; Tom predates parents; Zed's parent is gone from here.
fn household() -> Vec<RoleView> {
    vec![
        view("andre", false, true, None),
        view("bea", false, false, None),
        view("cleo", false, false, None),
        view("ben", true, false, Some("andre")),
        view("mia", true, false, Some("bea")),
        view("tom", true, false, None),
        view("zed", true, false, Some("gone")),
    ]
}

#[test]
fn only_the_admin_makes_grown_ups_and_any_grown_up_makes_kids() {
    let h = household();
    assert!(allowed(&h, "andre", Action::CreateGrownUp, ""));
    assert!(!allowed(&h, "bea", Action::CreateGrownUp, ""));
    assert!(allowed(&h, "bea", Action::CreateKid, ""));
    assert!(!allowed(&h, "mia", Action::CreateKid, ""), "a kid manages nothing");
    assert!(!allowed(&h, "nobody", Action::CreateKid, ""));
}

#[test]
fn a_kid_without_a_grown_up_parent_here_belongs_to_the_admin() {
    let h = household();
    for kid in ["tom", "zed", "ben"] {
        assert!(allowed(&h, "andre", Action::SetKidsAge, kid), "{kid}");
        assert!(!allowed(&h, "bea", Action::SetKidsAge, kid), "{kid}");
    }
    assert!(allowed(&h, "bea", Action::SetKidsAge, "mia"));
    assert!(!allowed(&h, "andre", Action::SetKidsAge, "mia"), "not another parent's kid");
}

#[test]
fn removal_follows_the_role_and_never_reaches_the_admin() {
    let h = household();
    assert!(allowed(&h, "andre", Action::Remove, "bea"));
    assert!(!allowed(&h, "andre", Action::Remove, "andre"));
    assert!(!allowed(&h, "bea", Action::Remove, "andre"));
    assert!(!allowed(&h, "bea", Action::Remove, "cleo"));
    assert!(!allowed(&h, "bea", Action::Remove, "bea"));
    assert!(allowed(&h, "bea", Action::Remove, "mia"));
    assert!(!allowed(&h, "bea", Action::Remove, "ben"));
}

#[test]
fn a_pin_is_set_by_its_own_grown_up_or_the_admin_and_never_on_a_kid() {
    let h = household();
    assert!(allowed(&h, "bea", Action::SetPin, "bea"));
    assert!(allowed(&h, "andre", Action::SetPin, "bea"));
    assert!(!allowed(&h, "bea", Action::SetPin, "cleo"));
    assert!(!allowed(&h, "andre", Action::SetPin, "mia"));
}

#[test]
fn with_no_admin_an_orphan_kid_belongs_to_nobody() {
    let h = vec![view("bea", false, false, None), view("tom", true, false, None)];
    assert!(!allowed(&h, "bea", Action::SetKidsAge, "tom"));
    assert!(!allowed(&h, "bea", Action::Remove, "tom"));
}
```

Append to `tests/shared_watch_state_fixtures.rs` (import
`use mediagram_core::state::profiles::rules::{Action, RoleView, allowed};`):

```rust
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct RuleCase {
    name: String,
    profiles: Vec<RoleView>,
    actor_id: String,
    action: Action,
    #[serde(default)]
    target_id: Option<String>,
    expect: bool,
}

/// Who may do what to whom — one rule, on both surfaces.
#[test]
fn profile_rule_fixtures_match_the_web() {
    let Some(cases) = load::<RuleCase>("profile-rules.json") else {
        return;
    };
    assert!(!cases.is_empty(), "profile-rules.json holds no cases");
    for case in cases {
        let target = case.target_id.as_deref().unwrap_or_default();
        assert_eq!(
            allowed(&case.profiles, &case.actor_id, case.action, target),
            case.expect,
            "case: {}",
            case.name
        );
    }
}
```

- [ ] **Step 2:** `cargo test -p mediagram-core -q` — FAIL to compile (`profiles::rules` missing).
- [ ] **Step 3: implement `src/state/profiles/rules.rs`**

```rust
//! Who may do what to whom: the one rule both surfaces enforce, a port of the
//! web's `allowed` (`web/src/state/profiles.ts`), pinned to it by
//! `profile-rules.json`.
//!
//! Pure — every local profile in, yes or no out, never an error. A kid
//! manages nothing. The admin adds and removes grown-ups and resets their
//! PINs, and is never removed. A grown-up manages its own kids and its own
//! PIN. A kid whose parent is not a grown-up here — made before parents
//! existed, or whose parent was removed on this device — belongs to the
//! admin, so no migration has to guess a parent.

use serde::Deserialize;

/// One profile as the rule sees it.
#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RoleView {
    pub id: String,
    pub kids: bool,
    pub admin: bool,
    #[serde(default)]
    pub parent_id: Option<String>,
}

/// What an actor asks to do, spelled as the web spells it.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum Action {
    CreateGrownUp,
    CreateKid,
    Remove,
    SetPin,
    SetKidsAge,
}

/// Whether `actor_id` may do `action` to `target_id` (ignored when creating).
pub fn allowed(profiles: &[RoleView], actor_id: &str, action: Action, target_id: &str) -> bool {
    let find = |id: &str| profiles.iter().find(|p| p.id == id);
    let Some(actor) = find(actor_id).filter(|actor| !actor.kids) else {
        return false;
    };
    let owns = |kid: &RoleView| kid.kids && owner_of(profiles, kid) == Some(actor_id);
    let target = find(target_id);
    match action {
        Action::CreateGrownUp => actor.admin,
        Action::CreateKid => true,
        Action::Remove => target.is_some_and(|t| {
            !t.admin && ((!t.kids && t.id != actor.id && actor.admin) || owns(t))
        }),
        Action::SetPin => target.is_some_and(|t| !t.kids && (t.id == actor.id || actor.admin)),
        Action::SetKidsAge => target.is_some_and(owns),
    }
}

/// The grown-up who manages `kid`: its parent when that names a grown-up
/// here, otherwise the admin, otherwise nobody.
fn owner_of<'a>(profiles: &'a [RoleView], kid: &RoleView) -> Option<&'a str> {
    let parent = kid
        .parent_id
        .as_deref()
        .and_then(|id| profiles.iter().find(|p| p.id == id && !p.kids));
    parent.or_else(|| profiles.iter().find(|p| p.admin)).map(|p| p.id.as_str())
}

#[cfg(test)]
#[path = "rules_tests.rs"]
mod tests;
```

  In `profiles.rs`: `pub mod rules;`.
- [ ] **Step 4:** `cargo test -p mediagram-core -q` — green, incl. `profile_rule_fixtures_match_the_web`
  with real cases. A failing fixture case is a core bug; fix the core, never the fixture.
- [ ] **Step 5: commit.** Bump (patch), then:

```bash
git add crates/mediagram-core/src/state/profiles.rs crates/mediagram-core/src/state/profiles/ \
  crates/mediagram-core/tests/shared_watch_state_fixtures.rs \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(core): the profile rule the web enforces; release <next>"
```

### Task 4: A profile says its role; removing a grown-up takes its kids

- [ ] **Step 1: failing tests** — append to `src/state/profiles_tests.rs`:

```rust
#[test]
fn a_profile_says_its_role_limit_owner_and_whether_it_has_a_pin_but_never_the_pin() {
    let (_dir, db) = db();
    let andre = db.with(|c| create(c, "André", false)).unwrap().unwrap().id;
    let mia = db.with(|c| create(c, "Mia", true)).unwrap().unwrap().id;
    db.with(|c| {
        c.execute_batch(&format!(
            "UPDATE profiles SET admin_claimed_at = 5, pin_hash = 'h', pin_salt = 's' WHERE id = '{andre}';
             UPDATE profiles SET kids_age = 6, parent_id = '{andre}' WHERE id = '{mia}';"
        ))
    })
    .unwrap();
    let listed = db.with(list).unwrap();
    let find = |id: &str| listed.iter().find(|p| p.id == id).unwrap().clone();
    assert_eq!(
        find(&andre),
        Profile { id: andre.clone(), name: "André".into(), admin: true, has_pin: true, ..Default::default() }
    );
    assert_eq!(
        find(&mia),
        Profile {
            id: mia.clone(),
            name: "Mia".into(),
            kids: true,
            kids_age: Some(6),
            parent_id: Some(andre.clone()),
            ..Default::default()
        }
    );
}

#[test]
fn a_new_kid_stores_twelve_and_a_grown_up_no_limit() {
    let (_dir, db) = db();
    db.with(|c| create(c, "Mia", true)).unwrap();
    db.with(|c| create(c, "Bea", false)).unwrap();
    let stored = |name: &str| -> Option<i64> {
        db.with(|c| c.query_row("SELECT kids_age FROM profiles WHERE name = ?1", [name], |r| r.get(0)))
            .unwrap()
    };
    assert_eq!((stored("Mia"), stored("Bea")), (Some(12), None));
}

#[test]
fn a_kid_with_no_limit_stored_reads_as_twelve() {
    let (_dir, db) = db();
    db.with(|c| create(c, "Mia", true)).unwrap();
    db.with(|c| c.execute("UPDATE profiles SET kids_age = NULL", [])).unwrap();
    assert_eq!(db.with(list).unwrap()[0].kids_age, Some(12));
}

#[test]
fn removing_a_grown_up_takes_the_kids_it_owns_and_no_others() {
    let (_dir, db) = db();
    let bea = db.with(|c| create(c, "Bea", false)).unwrap().unwrap().id;
    let mia = db.with(|c| create(c, "Mia", true)).unwrap().unwrap().id;
    db.with(|c| create(c, "Ben", true)).unwrap();
    db.with(|c| c.execute("UPDATE profiles SET parent_id = ?1 WHERE id = ?2", [&bea, &mia]))
        .unwrap();
    assert!(db.with(|c| delete(c, &bea)).unwrap());
    let names: Vec<String> = db.with(list).unwrap().into_iter().map(|p| p.name).collect();
    assert_eq!(names, ["Ben"]);
}
```

- [ ] **Step 2:** `cargo test -p mediagram-core --lib state::profiles -q` — FAIL to compile (no field
  `admin`/`has_pin`/…; `Profile: Default` missing).
- [ ] **Step 3: implement in `state/profiles.rs`**
  - `Profile`: derive `Default` too; `kids` doc becomes `/// A kid: sees only what its limit allows — see \`kids_age\`.`;
    after `kids` add the four fields of § Interfaces, each with a one-line doc:
    `kids_age` "FSK 6 or 12 on a kid; `None` on a grown-up.", `parent_id` "The grown-up who owns this
    kid, if it is one here.", `admin` "The household's admin — there is at most one.", `has_pin`
    "Whether a PIN is set. Never the PIN, its hash or its salt."
  - `list`:

```rust
pub fn list(conn: &Connection) -> rusqlite::Result<Vec<Profile>> {
    let mut stmt = conn.prepare(
        "SELECT id, name, kids, kids_age, parent_id, admin_claimed_at IS NOT NULL, pin_hash IS NOT NULL
           FROM profiles ORDER BY created_at",
    )?;
    let rows = stmt.query_map([], |row| {
        let kids = row.get::<_, i64>(2)? != 0;
        let age: Option<i64> = row.get(3)?;
        Ok(Profile {
            id: row.get(0)?,
            name: row.get(1)?,
            kids,
            // A kid with no limit stored predates limits: FSK 12.
            kids_age: kids.then_some(if age == Some(6) { 6 } else { 12 }),
            parent_id: row.get(4)?,
            admin: row.get(5)?,
            has_pin: row.get(6)?,
        })
    })?;
    rows.collect()
}
```

  - `create`: the literal becomes
    `Profile { id: ulid::Ulid::new().to_string(), name: clean, kids, kids_age: kids.then_some(12), ..Default::default() }`,
    and the insert stores that limit, as the web's `createProfile` does (phase 01):
    `INSERT INTO profiles(id, name, created_at, kids, kids_age) VALUES (?1, ?2, ?3, ?4, ?5)` with
    `params![profile.id, profile.name, now_ms(), i64::from(kids), profile.kids_age]`.
  - `delete` (doc gains: "A grown-up's kids go with it — by `parent_id`, deliberately not a foreign key:
    a kid whose parent is missing here must stay readable, and belongs to the admin meanwhile."):

```rust
pub fn delete(conn: &Connection, id: &str) -> rusqlite::Result<bool> {
    let tx = conn.unchecked_transaction()?;
    tx.execute("DELETE FROM profiles WHERE parent_id = ?1", params![id])?;
    let removed = tx.execute("DELETE FROM profiles WHERE id = ?1", params![id])? > 0;
    tx.commit()?;
    Ok(removed)
}
```

- [ ] **Step 4:** `cargo test -p mediagram-core -q` — green (incl. `migration_tests`, `upgrade_tests`,
  `tests/api_surface.rs` — `profiles()` equality still holds: seeded and created rows read the same way).
- [ ] **Step 5: commit.** Bump (patch), then:

```bash
git add crates/mediagram-core/src/state/profiles.rs crates/mediagram-core/src/state/profiles_tests.rs \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(core): a profile says its role, limit and owner; a grown-up's kids leave with it; release <next>"
```

### Task 5: The management calls

- [ ] **Step 1: failing tests** — create `src/state/profiles/manage_tests.rs`:

```rust
use crate::state::StateDb;
use crate::state::profiles::pin_wait::PinWait;
use crate::state::profiles::request::{Change, ProfileOutcome};
use crate::state::profiles::{Profile, create, list, role_rows};

const T: i64 = 1_000_000;

struct Household {
    _dir: tempfile::TempDir,
    db: StateDb,
    wait: PinWait,
}

impl Household {
    fn new() -> Self {
        let dir = tempfile::tempdir().unwrap();
        let db = StateDb::new(dir.path().to_path_buf());
        Household { _dir: dir, db, wait: PinWait::default() }
    }

    /// A grown-up; `None` for one from before PINs.
    fn grown_up(&self, name: &str, pin: Option<&str>) -> String {
        self.db
            .with(|conn| {
                let id = create(conn, name, false)?.unwrap().id;
                if let Some(pin) = pin {
                    role_rows::write_pin(conn, &id, pin, T)?;
                }
                Ok(id)
            })
            .unwrap()
    }

    fn admin(&self, name: &str, pin: &str) -> String {
        let id = self.grown_up(name, Some(pin));
        self.db
            .with(|conn| conn.execute("UPDATE profiles SET admin_claimed_at = 1 WHERE id = ?1", [&id]))
            .unwrap();
        id
    }

    fn change(&mut self, now: i64, actor: &str, pin: &str, request: Change) -> ProfileOutcome {
        let wait = &mut self.wait;
        self.db.with(|conn| super::change(conn, wait, now, actor, pin, request)).unwrap()
    }

    fn unlock(&mut self, now: i64, id: &str, pin: &str) -> ProfileOutcome {
        let wait = &mut self.wait;
        self.db.with(|conn| super::unlock(conn, wait, now, id, pin)).unwrap()
    }

    fn claim(&mut self, now: i64, id: &str, pin: &str) -> ProfileOutcome {
        let wait = &mut self.wait;
        self.db.with(|conn| super::claim_admin(conn, wait, now, id, pin)).unwrap()
    }

    fn first(&self, now: i64, name: &str, pin: &str) -> ProfileOutcome {
        self.db.with(|conn| super::create_first(conn, now, name, pin)).unwrap()
    }

    fn named(&self, name: &str) -> Profile {
        self.db.with(list).unwrap().into_iter().find(|p| p.name == name).unwrap()
    }
}

fn kid(name: &str, kids_age: u8) -> Change<'_> {
    Change::CreateKid { name, kids_age }
}

#[test]
fn a_malformed_request_is_invalid_before_anyone_is_looked_up() {
    let mut home = Household::new();
    for request in [
        kid("Mia", 7),
        kid("   ", 6),
        Change::CreateGrownUp { name: "Bea", new_pin: "12a4" },
        Change::SetPin { id: "nobody", new_pin: "12345" },
        Change::SetKidsAge { id: "nobody", kids_age: 0 },
    ] {
        assert_eq!(home.change(T, "nobody", "1234", request), ProfileOutcome::Invalid);
    }
}

#[test]
fn an_actor_or_a_target_naming_nobody_is_not_found() {
    let mut home = Household::new();
    let andre = home.admin("André", "1234");
    assert_eq!(home.change(T, "nobody", "1234", kid("Mia", 6)), ProfileOutcome::NotFound);
    assert_eq!(home.change(T, &andre, "1234", Change::Remove { id: "nobody" }), ProfileOutcome::NotFound);
    assert_eq!(home.unlock(T, "nobody", "1234"), ProfileOutcome::NotFound);
}

#[test]
fn a_grown_up_from_before_pins_sets_its_first_one_without_one() {
    let mut home = Household::new();
    let bea = home.grown_up("Bea", None);
    assert_eq!(home.change(T, &bea, "1234", kid("Mia", 6)), ProfileOutcome::NoPin);
    assert_eq!(home.unlock(T, &bea, "1234"), ProfileOutcome::NoPin);
    assert_eq!(home.change(T, &bea, "", Change::SetPin { id: &bea, new_pin: "4321" }), ProfileOutcome::Done);
    assert!(home.named("Bea").has_pin);
    assert_eq!(home.unlock(T, &bea, "4321"), ProfileOutcome::Done);
}

#[test]
fn five_wrong_pins_make_every_check_wait_a_minute_even_for_the_right_one() {
    let mut home = Household::new();
    let andre = home.admin("André", "1234");
    for _ in 0..5 {
        assert_eq!(home.unlock(T, &andre, "0000"), ProfileOutcome::WrongPin);
    }
    assert_eq!(home.unlock(T, &andre, "1234"), ProfileOutcome::Wait { seconds: 60 });
    assert_eq!(home.change(T + 30_000, &andre, "1234", kid("Mia", 6)), ProfileOutcome::Wait { seconds: 30 });
    assert_eq!(home.unlock(T + 60_000, &andre, "1234"), ProfileOutcome::Done);
}

#[test]
fn only_a_call_about_to_compare_a_pin_waits() {
    let mut home = Household::new();
    let andre = home.admin("André", "1234");
    let bea = home.grown_up("Bea", None);
    assert_eq!(home.change(T, &andre, "1234", kid("Mia", 6)), ProfileOutcome::Done);
    let mia = home.named("Mia").id;
    for _ in 0..5 {
        home.unlock(T, &andre, "0000");
    }
    // Nothing to compare: a kid acting, a kid opening, a grown-up with no
    // PIN, a first PIN being set.
    assert_eq!(home.change(T, &mia, "", kid("Ben", 6)), ProfileOutcome::NotAllowed);
    assert_eq!(home.unlock(T, &mia, ""), ProfileOutcome::Done);
    assert_eq!(home.unlock(T, &bea, "1234"), ProfileOutcome::NoPin);
    assert_eq!(home.change(T, &bea, "", Change::SetPin { id: &bea, new_pin: "4321" }), ProfileOutcome::Done);
    // Now Bea has a PIN, and opening her profile would compare it.
    assert_eq!(home.unlock(T, &bea, "4321"), ProfileOutcome::Wait { seconds: 60 });
}

#[test]
fn the_rule_is_asked_only_after_the_pin() {
    let mut home = Household::new();
    let andre = home.admin("André", "1234");
    let bea = home.grown_up("Bea", Some("1111"));
    let cleo = Change::CreateGrownUp { name: "Cleo", new_pin: "2222" };
    assert_eq!(home.change(T, &bea, "1111", cleo), ProfileOutcome::NotAllowed);
    assert_eq!(home.change(T, &bea, "9999", Change::Remove { id: &andre }), ProfileOutcome::WrongPin);
    assert_eq!(home.change(T, &bea, "1111", Change::Remove { id: &andre }), ProfileOutcome::NotAllowed);
}

#[test]
fn a_new_kid_belongs_to_the_grown_up_who_made_it() {
    let mut home = Household::new();
    home.admin("André", "1234");
    let bea = home.grown_up("Bea", Some("1111"));
    assert_eq!(home.change(T, &bea, "1111", kid("Mia", 6)), ProfileOutcome::Done);
    let mia = home.named("Mia");
    assert_eq!((mia.kids, mia.kids_age, mia.parent_id), (true, Some(6), Some(bea)));
}

#[test]
fn removing_a_grown_up_takes_its_kids_and_the_admin_is_never_removed() {
    let mut home = Household::new();
    let andre = home.admin("André", "1234");
    let bea = home.grown_up("Bea", Some("1111"));
    home.change(T, &bea, "1111", kid("Mia", 12));
    home.change(T, &andre, "1234", kid("Ben", 6));
    assert_eq!(home.change(T, &andre, "1234", Change::Remove { id: &andre }), ProfileOutcome::NotAllowed);
    assert_eq!(home.change(T, &andre, "1234", Change::Remove { id: &bea }), ProfileOutcome::Done);
    let mut names: Vec<String> = home.db.with(list).unwrap().into_iter().map(|p| p.name).collect();
    names.sort();
    assert_eq!(names, ["André", "Ben"]);
}

#[test]
fn a_limit_changes_only_for_its_owner_and_lands_after_the_last_change() {
    let mut home = Household::new();
    let andre = home.admin("André", "1234");
    let bea = home.grown_up("Bea", Some("1111"));
    home.change(T, &bea, "1111", kid("Mia", 6));
    let mia = home.named("Mia").id;
    let twelve = Change::SetKidsAge { id: &mia, kids_age: 12 };
    assert_eq!(home.change(T, &andre, "1234", twelve), ProfileOutcome::NotAllowed);
    // A clock behind the kid's last change still lands after it.
    let twelve = Change::SetKidsAge { id: &mia, kids_age: 12 };
    assert_eq!(home.change(T - 5_000, &bea, "1111", twelve), ProfileOutcome::Done);
    assert_eq!(home.named("Mia").kids_age, Some(12));
    let at: i64 = home
        .db
        .with(|c| c.query_row("SELECT kids_age_updated_at FROM profiles WHERE id = ?1", [&mia], |r| r.get(0)))
        .unwrap();
    assert_eq!(at, T + 1);
}

#[test]
fn the_admin_resets_another_pin_and_a_grown_up_only_its_own() {
    let mut home = Household::new();
    let andre = home.admin("André", "1234");
    let bea = home.grown_up("Bea", Some("1111"));
    let cleo = home.grown_up("Cleo", Some("2222"));
    assert_eq!(home.change(T, &bea, "1111", Change::SetPin { id: &cleo, new_pin: "3333" }), ProfileOutcome::NotAllowed);
    assert_eq!(home.change(T, &andre, "1234", Change::SetPin { id: &bea, new_pin: "5555" }), ProfileOutcome::Done);
    assert_eq!(home.unlock(T, &bea, "5555"), ProfileOutcome::Done);
}

#[test]
fn the_first_claim_makes_an_admin_and_gives_a_pinless_grown_up_its_pin() {
    let mut home = Household::new();
    let andre = home.grown_up("André", None);
    let bea = home.grown_up("Bea", Some("1111"));
    assert_eq!(home.claim(T, &andre, "12"), ProfileOutcome::Invalid);
    assert_eq!(home.claim(T, &andre, "1234"), ProfileOutcome::Done);
    let now = home.named("André");
    assert!(now.admin && now.has_pin);
    assert_eq!(home.claim(T, &bea, "1111"), ProfileOutcome::NotAllowed, "there is an admin already");
    assert_eq!(home.unlock(T, &andre, "1234"), ProfileOutcome::Done);
}

#[test]
fn a_claim_needs_the_pin_a_profile_has_and_a_kid_cannot_claim() {
    let mut home = Household::new();
    let bea = home.grown_up("Bea", Some("1111"));
    assert_eq!(home.claim(T, &bea, "2222"), ProfileOutcome::WrongPin);
    let mia = home.db.with(|c| create(c, "Mia", true)).unwrap().unwrap().id;
    assert_eq!(home.claim(T, &mia, "1234"), ProfileOutcome::NotAllowed);
    assert_eq!(home.claim(T, &bea, "1111"), ProfileOutcome::Done);
}

#[test]
fn once_there_is_an_admin_a_claim_is_refused_before_its_pin_is_compared() {
    let mut home = Household::new();
    home.admin("André", "1234");
    let bea = home.grown_up("Bea", Some("1111"));
    for _ in 0..5 {
        assert_eq!(home.claim(T, &bea, "0000"), ProfileOutcome::NotAllowed);
    }
    assert_eq!(home.unlock(T, &bea, "1111"), ProfileOutcome::Done, "no wrong PIN was counted");
}

#[test]
fn a_claim_waits_only_when_it_would_compare_a_pin() {
    let mut home = Household::new();
    let bea = home.grown_up("Bea", Some("1111"));
    let cleo = home.grown_up("Cleo", None);
    for _ in 0..5 {
        home.unlock(T, &bea, "0000");
    }
    assert_eq!(home.claim(T, &bea, "1111"), ProfileOutcome::Wait { seconds: 60 });
    assert_eq!(home.claim(T, &cleo, "2222"), ProfileOutcome::Done);
}

#[test]
fn the_first_profile_is_a_grown_up_admin_with_its_pin() {
    let mut home = Household::new();
    assert_eq!(home.first(T, "   ", "1234"), ProfileOutcome::Invalid);
    assert_eq!(home.first(T, "André", "12"), ProfileOutcome::Invalid);
    assert_eq!(home.first(T, "André", "1234"), ProfileOutcome::Done);
    let andre = home.named("André");
    assert!(!andre.kids && andre.admin && andre.has_pin);
    assert_eq!(home.unlock(T, &andre.id, "1234"), ProfileOutcome::Done);
    assert_eq!(home.first(T, "Bea", "1111"), ProfileOutcome::NotAllowed);
}

#[test]
fn a_first_profile_needs_a_device_with_no_grown_up_but_kids_do_not_count() {
    let home = Household::new();
    home.db.with(|c| create(c, "Mia", true)).unwrap();
    assert_eq!(home.first(T, "André", "1234"), ProfileOutcome::Done);

    let other = Household::new();
    other.grown_up("Bea", None);
    assert_eq!(other.first(T, "André", "1234"), ProfileOutcome::NotAllowed, "claim_admin is for that");
}
```

- [ ] **Step 2:** `cargo test -p mediagram-core --lib state::profiles -q` — FAIL to compile (`manage`,
  `request`, `role_rows` missing).
- [ ] **Step 3: implement `src/state/profiles/request.rs`**

```rust
//! What a management call asks, and how it ended. Split out of `manage.rs`
//! to keep it under the line limit.

use super::rules::Action;
use super::{clean_name, pin};

/// How a management call ended — one variant per reason the web answers.
#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum ProfileOutcome {
    Done,
    /// A blank name, a new PIN that is not four digits, a limit not 6 or 12.
    Invalid,
    /// The actor or the target names nobody here.
    NotFound,
    /// Too many wrong PINs: nothing is compared for `seconds` more.
    Wait { seconds: u32 },
    /// A grown-up from before PINs, who has to set one first.
    NoPin,
    WrongPin,
    /// The rule says no.
    NotAllowed,
}

/// A change a grown-up asks for, before anything about it is checked.
pub(crate) enum Change<'a> {
    CreateGrownUp { name: &'a str, new_pin: &'a str },
    CreateKid { name: &'a str, kids_age: u8 },
    Remove { id: &'a str },
    SetPin { id: &'a str, new_pin: &'a str },
    SetKidsAge { id: &'a str, kids_age: u8 },
}

impl Change<'_> {
    pub(super) fn action(&self) -> Action {
        match self {
            Change::CreateGrownUp { .. } => Action::CreateGrownUp,
            Change::CreateKid { .. } => Action::CreateKid,
            Change::Remove { .. } => Action::Remove,
            Change::SetPin { .. } => Action::SetPin,
            Change::SetKidsAge { .. } => Action::SetKidsAge,
        }
    }

    pub(super) fn target(&self) -> Option<&str> {
        match *self {
            Change::Remove { id } | Change::SetPin { id, .. } | Change::SetKidsAge { id, .. } => Some(id),
            Change::CreateGrownUp { .. } | Change::CreateKid { .. } => None,
        }
    }

    /// The new values only. The actor's current PIN is not checked here: it
    /// is compared, and a malformed one simply does not match.
    pub(super) fn well_formed(&self) -> bool {
        let limit = |age: u8| age == 6 || age == 12;
        match *self {
            Change::CreateGrownUp { name, new_pin } => clean_name(name).is_some() && pin::valid(new_pin),
            Change::CreateKid { name, kids_age } => clean_name(name).is_some() && limit(kids_age),
            Change::Remove { .. } => true,
            Change::SetPin { new_pin, .. } => pin::valid(new_pin),
            Change::SetKidsAge { kids_age, .. } => limit(kids_age),
        }
    }
}
```

- [ ] **Step 4: implement `src/state/profiles/role_rows.rs`**

```rust
//! The rows `manage` reads and writes: every profile as the rule sees it,
//! with the PIN it opens with, and the write each allowed change makes. Split
//! out of `manage.rs` to keep it under the line limit.

use rusqlite::{Connection, params};

use super::request::{Change, ProfileOutcome};
use super::rules::RoleView;
use super::{create, delete, pin};

/// One profile as the rule sees it, and its PIN as `(hash, salt)` — `None`
/// on a kid and on a grown-up from before PINs.
pub(super) struct Stored {
    pub(super) view: RoleView,
    pub(super) pin: Option<(String, String)>,
}

pub(super) fn load(conn: &Connection) -> rusqlite::Result<Vec<Stored>> {
    let mut stmt = conn.prepare(
        "SELECT id, kids, admin_claimed_at IS NOT NULL, parent_id, pin_hash, pin_salt FROM profiles",
    )?;
    let rows = stmt.query_map([], |row| {
        let hash: Option<String> = row.get(4)?;
        let salt: Option<String> = row.get(5)?;
        Ok(Stored {
            view: RoleView {
                id: row.get(0)?,
                kids: row.get::<_, i64>(1)? != 0,
                admin: row.get(2)?,
                parent_id: row.get(3)?,
            },
            pin: hash.zip(salt),
        })
    })?;
    rows.collect()
}

/// Makes a change already checked and allowed.
pub(super) fn apply(conn: &Connection, now: i64, actor_id: &str, request: &Change) -> rusqlite::Result<ProfileOutcome> {
    match *request {
        // `delete` holds its own transaction: the kids and their parent go together.
        Change::Remove { id } => {
            delete(conn, id)?;
        }
        Change::SetPin { id, new_pin } => write_pin(conn, id, new_pin, now)?,
        // One past the last change at least, so a limit set here beats one
        // imported from a device whose clock runs ahead.
        Change::SetKidsAge { id, kids_age } => {
            conn.execute(
                "UPDATE profiles SET kids_age = ?2, kids_age_updated_at = MAX(?3, kids_age_updated_at + 1)
                   WHERE id = ?1",
                params![id, kids_age, now],
            )?;
        }
        Change::CreateGrownUp { name, new_pin } => {
            let tx = conn.unchecked_transaction()?;
            let Some(made) = create(&tx, name, false)? else {
                return Ok(ProfileOutcome::Invalid);
            };
            write_pin(&tx, &made.id, new_pin, now)?;
            tx.commit()?;
        }
        Change::CreateKid { name, kids_age } => {
            let tx = conn.unchecked_transaction()?;
            let Some(made) = create(&tx, name, true)? else {
                return Ok(ProfileOutcome::Invalid);
            };
            tx.execute(
                "UPDATE profiles SET kids_age = ?2, kids_age_updated_at = ?3, parent_id = ?4 WHERE id = ?1",
                params![made.id, kids_age, now, actor_id],
            )?;
            tx.commit()?;
        }
    }
    Ok(ProfileOutcome::Done)
}

/// A fresh salt and the hash of `new_pin` under it, stamped past the last
/// change for the same reason as a limit.
pub(super) fn write_pin(conn: &Connection, id: &str, new_pin: &str, now: i64) -> rusqlite::Result<()> {
    let salt = pin::new_salt();
    conn.execute(
        "UPDATE profiles SET pin_hash = ?2, pin_salt = ?3, pin_updated_at = MAX(?4, pin_updated_at + 1)
           WHERE id = ?1",
        params![id, pin::hash(&salt, new_pin), salt, now],
    )?;
    Ok(())
}

/// Makes `id` the admin, giving it `first_pin` when it has none yet.
pub(super) fn claim(conn: &Connection, id: &str, first_pin: Option<&str>, now: i64) -> rusqlite::Result<()> {
    let tx = conn.unchecked_transaction()?;
    make_admin(&tx, id, first_pin, now)?;
    tx.commit()
}

/// The household's first grown-up: made with `new_pin`, the admin from `now`.
pub(super) fn first_admin(conn: &Connection, name: &str, new_pin: &str, now: i64) -> rusqlite::Result<ProfileOutcome> {
    let tx = conn.unchecked_transaction()?;
    let Some(made) = create(&tx, name, false)? else {
        return Ok(ProfileOutcome::Invalid);
    };
    make_admin(&tx, &made.id, Some(new_pin), now)?;
    tx.commit()?;
    Ok(ProfileOutcome::Done)
}

/// The two writes of becoming admin; the caller's transaction makes them one.
fn make_admin(conn: &Connection, id: &str, first_pin: Option<&str>, now: i64) -> rusqlite::Result<()> {
    if let Some(first_pin) = first_pin {
        write_pin(conn, id, first_pin, now)?;
    }
    conn.execute("UPDATE profiles SET admin_claimed_at = ?2 WHERE id = ?1", params![id, now])?;
    Ok(())
}
```

- [ ] **Step 5: implement `src/state/profiles/manage.rs`**

```rust
//! Changing who is in the household, behind a grown-up's PIN: making a
//! grown-up or a kid, removing one, setting a PIN or a kid's limit, opening a
//! profile from the picker, claiming the admin role, making a household's
//! first profile. A port of the web's profile rules
//! (`web/src/state/profiles.ts`), refusing for the same reasons in the same
//! order (contract §3): malformed input, an id naming nobody, a refusal the
//! caller's role already makes certain, the wrong-PIN wait — only when a PIN
//! is about to be compared — a grown-up with no PIN yet, a wrong PIN, and only
//! then the rule.
//!
//! This stops a child tapping into a grown-up's profile. It is not a login:
//! the catalog filter runs on this device, and anyone with `adb` gets past
//! every check here.

use std::sync::PoisonError;

use rusqlite::Connection;

use super::pin_wait::PinWait;
use super::request::{Change, ProfileOutcome};
use super::role_rows::{self, Stored};
use super::{clean_name, now_ms, pin, rules};
use crate::state::StateDb;

/// Makes `request` for `actor_id`, who proves who it is with `pin`.
pub(crate) fn change(
    conn: &Connection,
    wait: &mut PinWait,
    now: i64,
    actor_id: &str,
    pin: &str,
    request: Change,
) -> rusqlite::Result<ProfileOutcome> {
    if !request.well_formed() {
        return Ok(ProfileOutcome::Invalid);
    }
    let stored = role_rows::load(conn)?;
    let Some(actor) = find(&stored, actor_id) else {
        return Ok(ProfileOutcome::NotFound);
    };
    if request.target().is_some_and(|id| find(&stored, id).is_none()) {
        return Ok(ProfileOutcome::NotFound);
    }
    // A kid manages nothing: refused before any wait, as there is no PIN to
    // compare.
    if actor.view.kids {
        return Ok(ProfileOutcome::NotAllowed);
    }
    // A grown-up from before PINs sets its first one without the PIN it does
    // not have yet — the way it stops being open to anyone. It never waits.
    let first_pin =
        matches!(request, Change::SetPin { id, .. } if id == actor_id) && actor.pin.is_none();
    let refused = if first_pin { None } else { check_pin(wait, now, actor, pin) };
    if let Some(refused) = refused {
        return Ok(refused);
    }
    let views: Vec<_> = stored.iter().map(|s| s.view.clone()).collect();
    let target = request.target().unwrap_or_default();
    if !rules::allowed(&views, actor_id, request.action(), target) {
        return Ok(ProfileOutcome::NotAllowed);
    }
    role_rows::apply(conn, now, actor_id, &request)
}

/// Opening a profile from the picker: a kid opens freely — even while the
/// player waits out wrong PINs — and a grown-up with its PIN.
pub(crate) fn unlock(conn: &Connection, wait: &mut PinWait, now: i64, id: &str, pin: &str) -> rusqlite::Result<ProfileOutcome> {
    let stored = role_rows::load(conn)?;
    Ok(match find(&stored, id) {
        None => ProfileOutcome::NotFound,
        Some(kid) if kid.view.kids => ProfileOutcome::Done,
        Some(grown_up) => check_pin(wait, now, grown_up, pin).unwrap_or(ProfileOutcome::Done),
    })
}

/// Makes `id` the household's admin while nobody here is one. A profile
/// with a PIN proves it; one without takes `pin` as its first.
pub(crate) fn claim_admin(conn: &Connection, wait: &mut PinWait, now: i64, id: &str, pin: &str) -> rusqlite::Result<ProfileOutcome> {
    if !pin::valid(pin) {
        return Ok(ProfileOutcome::Invalid);
    }
    let stored = role_rows::load(conn)?;
    let Some(target) = find(&stored, id) else {
        return Ok(ProfileOutcome::NotFound);
    };
    // Settled by who is here, before any PIN is compared.
    if target.view.kids || stored.iter().any(|s| s.view.admin) {
        return Ok(ProfileOutcome::NotAllowed);
    }
    let refused = if target.pin.is_some() { check_pin(wait, now, target, pin) } else { None };
    if let Some(refused) = refused {
        return Ok(refused);
    }
    role_rows::claim(conn, id, target.pin.is_none().then_some(pin), now)?;
    Ok(ProfileOutcome::Done)
}

/// The household's first grown-up, on a device that knows none: made with
/// `new_pin`, the admin from now. Without it a fresh install could never have
/// anyone to make the rest. Kids alone do not count — a device holding only
/// kids may still make one — but wherever a grown-up exists this is refused,
/// admin or not (`claim_admin` is for that). A device that does this before
/// its first sync and then hears of an older claim loses admin to it by the
/// merge's earliest-claim rule; nothing here special-cases that.
pub(crate) fn create_first(conn: &Connection, now: i64, name: &str, new_pin: &str) -> rusqlite::Result<ProfileOutcome> {
    if clean_name(name).is_none() || !pin::valid(new_pin) {
        return Ok(ProfileOutcome::Invalid);
    }
    if role_rows::load(conn)?.iter().any(|s| !s.view.kids) {
        return Ok(ProfileOutcome::NotAllowed);
    }
    role_rows::first_admin(conn, name, new_pin, now)
}

/// A grown-up's PIN, checked: `None` when it may go on. One with no PIN yet
/// is told so at once — nothing would be compared, so there is nothing to
/// wait for; otherwise a running wait answers before any comparison.
fn check_pin(wait: &mut PinWait, now: i64, who: &Stored, pin: &str) -> Option<ProfileOutcome> {
    let Some((hash, salt)) = &who.pin else {
        return Some(ProfileOutcome::NoPin);
    };
    if let Some(seconds) = wait.remaining(now) {
        return Some(ProfileOutcome::Wait { seconds });
    }
    if pin::verify(salt, pin, hash) {
        wait.succeeded();
        None
    } else {
        wait.failed(now);
        Some(ProfileOutcome::WrongPin)
    }
}

fn find<'a>(stored: &'a [Stored], id: &str) -> Option<&'a Stored> {
    stored.iter().find(|s| s.view.id == id)
}

impl StateDb {
    /// Runs one PIN-checked call against the store and this owner's wrong-PIN
    /// count. A store that cannot be read or written is logged by `with` and
    /// answered `Invalid` — nothing on this surface throws.
    pub(crate) fn guarded(
        &self,
        call: impl FnOnce(&Connection, &mut PinWait, i64) -> rusqlite::Result<ProfileOutcome>,
    ) -> ProfileOutcome {
        let mut wait = self.pin_wait.lock().unwrap_or_else(PoisonError::into_inner);
        self.with(|conn| call(conn, &mut *wait, now_ms())).unwrap_or(ProfileOutcome::Invalid)
    }
}

#[cfg(test)]
#[path = "manage_tests.rs"]
mod tests;
```

- [ ] **Step 6: wire the modules and the count**
  - `state/profiles.rs`, with the other module lines: `pub(crate) mod manage;`, `pub(crate) mod request;`,
    `mod role_rows;` and `pub use request::ProfileOutcome;`.
  - `state/mod.rs`: in `StateDb`, after `conn`:

```rust
    /// Wrong PINs, counted for as long as this owner lives — see `profiles::pin_wait`.
    pin_wait: Mutex<profiles::pin_wait::PinWait>,
```

    and in `StateDb::new`: `pin_wait: Mutex::default(),`.
- [ ] **Step 7:** `cargo test -p mediagram-core -q && cargo clippy -p mediagram-core --all-targets --all-features -- -D warnings && cargo test -p mediagram --test code_standards -q`
  — green and clean; `wc -l crates/mediagram-core/src/state/mod.rs crates/mediagram-core/src/state/profiles.rs crates/mediagram-core/src/state/profiles/*.rs`
  shows nothing over 200.
- [ ] **Step 8: commit.** Bump (patch), then:

```bash
git add crates/mediagram-core/src/state/mod.rs crates/mediagram-core/src/state/profiles.rs \
  crates/mediagram-core/src/state/profiles/ \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(core): manage profiles behind a grown-up's PIN, refusing as the web does; release <next>"
```

### Task 6: The uniffi surface

- [ ] **Step 1: shared seed for integration tests** — create `tests/state_seed/mod.rs`:

```rust
//! Grown-ups made the way the surface makes them: the first is the
//! household's admin, every other is added by it, all with the PIN `1234`.

use std::sync::Arc;

use mediagram_core::api::Core;
use mediagram_core::state::profiles::ProfileOutcome;

/// Answers their ids, in the order given.
pub async fn grown_ups(player: &Arc<Core>, names: &[&str]) -> Vec<String> {
    let mut ids: Vec<String> = Vec::new();
    for name in names {
        let made = match ids.first() {
            None => player.clone().create_first_admin(name.to_string(), "1234".into()).await,
            Some(admin) => {
                let (admin, name) = (admin.clone(), name.to_string());
                player.clone().create_grown_up(admin, "1234".into(), name, "1234".into()).await
            }
        };
        assert_eq!(made, ProfileOutcome::Done, "{name}");
        let listed = player.clone().profiles().await;
        ids.push(listed.into_iter().find(|p| p.name == *name).unwrap().id);
    }
    ids
}
```

- [ ] **Step 2: failing tests** — in `tests/api_surface.rs` add `mod state_seed;` and
  `use mediagram_core::state::profiles::ProfileOutcome;`, then:

```rust
#[tokio::test]
async fn profile_management_answers_one_outcome_per_reason_across_the_boundary() {
    let dir = tempfile::tempdir().unwrap();
    let player = core(dir.path());
    let p = || player.clone();

    assert_eq!(p().create_first_admin("  ".into(), "1234".into()).await, ProfileOutcome::Invalid);
    assert_eq!(p().create_first_admin("André".into(), "1234".into()).await, ProfileOutcome::Done);
    assert_eq!(p().create_first_admin("Bea".into(), "1111".into()).await, ProfileOutcome::NotAllowed);
    let andre = p().profiles().await.into_iter().find(|x| x.name == "André").unwrap();
    assert!(andre.admin && andre.has_pin && !andre.kids);
    let andre = andre.id;

    assert_eq!(p().claim_admin(andre.clone(), "0000".into()).await, ProfileOutcome::NotAllowed);
    assert_eq!(p().create_kid(andre.clone(), "1234".into(), "Mia".into(), 7).await, ProfileOutcome::Invalid);
    assert_eq!(p().create_kid(andre.clone(), "0000".into(), "Mia".into(), 6).await, ProfileOutcome::WrongPin);
    assert_eq!(p().create_kid(andre.clone(), "1234".into(), "Mia".into(), 6).await, ProfileOutcome::Done);
    assert_eq!(p().delete_profile(andre.clone(), "1234".into(), andre.clone()).await, ProfileOutcome::NotAllowed);
    assert_eq!(p().delete_profile(andre.clone(), "1234".into(), "nobody".into()).await, ProfileOutcome::NotFound);

    let mia = p().profiles().await.into_iter().find(|x| x.name == "Mia").unwrap();
    assert_eq!((mia.kids, mia.kids_age, mia.parent_id.as_deref()), (true, Some(6), Some(andre.as_str())));
    assert_eq!(p().create_kid(mia.id.clone(), "".into(), "Ben".into(), 6).await, ProfileOutcome::NotAllowed);

    assert_eq!(p().set_kids_age(andre.clone(), "1234".into(), mia.id.clone(), 12).await, ProfileOutcome::Done);
    assert_eq!(p().unlock_profile(mia.id.clone(), String::new()).await, ProfileOutcome::Done);
    assert_eq!(
        p().create_grown_up(andre.clone(), "1234".into(), "Bea".into(), "1111".into()).await,
        ProfileOutcome::Done
    );
    let bea = p().profiles().await.into_iter().find(|x| x.name == "Bea").unwrap();
    assert_eq!(p().set_pin(andre.clone(), "1234".into(), bea.id.clone(), "2222".into()).await, ProfileOutcome::Done);
    assert_eq!(p().unlock_profile(bea.id, "2222".into()).await, ProfileOutcome::Done);
}

#[tokio::test]
async fn a_kids_mark_says_from_six_or_from_twelve_in_the_snapshot() {
    let dir = tempfile::tempdir().unwrap();
    let player = core(dir.path());
    let viewer = state_seed::grown_ups(&player, &["Viewer"]).await.remove(0);
    player.clone().set_kids("six".into(), Some(6)).await;
    player.clone().set_kids("twelve".into(), Some(12)).await;
    let snapshot = player.clone().snapshot(viewer.clone()).await;
    let mut kids = snapshot.kids.clone();
    kids.sort();
    assert_eq!(kids, ["six", "twelve"]);
    assert_eq!(snapshot.kids_from_six, ["six"]);
    player.clone().set_kids("six".into(), Some(12)).await;
    assert!(player.clone().snapshot(viewer.clone()).await.kids_from_six.is_empty());
    player.clone().set_kids("six".into(), None).await;
    assert_eq!(player.snapshot(viewer).await.kids, ["twelve"]);
}
```

  Rewrite the existing callers of the removed API in the same file:
  - `watch_state_is_profile_scoped_except_kids_and_survives_reopening` (`:137`):
    `let ids = state_seed::grown_ups(&player, &["André", "Bea"]).await;` and
    `let (andre, bea) = (ids[0].clone(), ids[1].clone());`; every `andre.id.clone()`/`bea.id.clone()` →
    `andre.clone()`/`bea.clone()`, `andre.id`/`bea.id` → `andre`/`bea`;
    `set_kids("family".into(), true)` → `Some(12)`, `set_kids("family".into(), false)` → `None`.
  - `a_collection_can_only_be_changed_by_its_owner` (`:266`): `owner`/`other` from
    `state_seed::grown_ups(&player, &["Owner", "Other"])`.
  - `unavailable_state_storage_returns_safe_defaults_and_can_be_retried` (`:360`): the first
    `create_profile(…) == None` becomes
    `assert_eq!(player.clone().create_first_admin("Viewer".into(), "1234".into()).await, ProfileOutcome::Invalid);`
    plus `assert_eq!(player.clone().claim_admin("viewer".into(), "1234".into()).await, ProfileOutcome::Invalid);`;
    `set_kids("set".into(), true)` → `Some(12)`; after `remove_dir`,
    `let viewer = state_seed::grown_ups(&player, &["Viewer"]).await.remove(0);` and the last asserts become
    `assert_eq!(player.clone().profiles().await.into_iter().map(|p| p.id).collect::<Vec<_>>(), [viewer.clone()]);`
    and `assert_eq!(player.snapshot(viewer).await, Default::default());`.

  In `tests/state_retirement.rs` add `mod state_seed;` and `use mediagram_core::state::profiles::ProfileOutcome;`:
  - `queued_create_cannot_reopen_retired_state`: the `if opened` block becomes
    `assert!(old.clone().profiles().await.is_empty());` (a read opens the file just as the create did);
    the queued call becomes `old.clone().create_first_admin("Late old-owner write".into(), "1234".into())`
    and the final check `assert_eq!(late.await, ProfileOutcome::Invalid);` — retired answers `Invalid`,
    where a live store would have made the profile (`Done`).
  - `replacing_a_retired_core_preserves_profiles_without_reviving_its_owner`:

```rust
    let old = core(dir.path());
    let retained = state_seed::grown_ups(&old, &["Retained"]).await.remove(0);
    old.retire_local_state();

    let replacement = core(dir.path());
    let ids = |listed: Vec<mediagram_core::state::profiles::Profile>| {
        listed.into_iter().map(|p| p.id).collect::<Vec<_>>()
    };
    assert_eq!(ids(replacement.clone().profiles().await), [retained.clone()]);
    assert_eq!(old.clone().create_first_admin("Late".into(), "1234".into()).await, ProfileOutcome::Invalid);
    assert_eq!(old.profiles().await, vec![]);
    assert_eq!(ids(replacement.profiles().await), [retained]);
```

- [ ] **Step 3:** `cargo test -p mediagram-core --tests -q` — FAIL to compile (`create_first_admin`, `create_kid`,
  `kids_from_six`, `set_kids(…, Option)` missing on `Core`).
- [ ] **Step 4: implement `src/api/state/profile_roles.rs`**

```rust
//! Managing profiles across the boundary: making the household's first
//! profile, a grown-up or a kid, removing one, opening one from the picker,
//! claiming the admin role, setting a PIN or a kid's limit. Split out of `state.rs` to keep it under
//! the line limit; every rule lives in `crate::state::profiles`.
//!
//! Each call answers a `ProfileOutcome` and never throws: a store that
//! cannot be read or written is logged and answered `Invalid`, the same
//! "nothing here throws" `state.rs` keeps.

use std::sync::Arc;

use crate::state::profiles::ProfileOutcome;
use crate::state::profiles::manage;
use crate::state::profiles::request::Change;

use super::super::Core;

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// The household's first profile, while this device knows no grown-up:
    /// a grown-up with `new_pin`, the admin from now. `NotAllowed` once one
    /// exists here.
    pub async fn create_first_admin(self: Arc<Self>, name: String, new_pin: String) -> ProfileOutcome {
        self.blocking(move |core| {
            core.state_db.guarded(|conn, _, now| manage::create_first(conn, now, &name, &new_pin))
        })
        .await
    }

    /// The admin adds a grown-up, with its first PIN.
    pub async fn create_grown_up(self: Arc<Self>, actor_id: String, pin: String, name: String, new_pin: String) -> ProfileOutcome {
        self.blocking(move |core| {
            let request = Change::CreateGrownUp { name: &name, new_pin: &new_pin };
            core.state_db.guarded(|conn, wait, now| manage::change(conn, wait, now, &actor_id, &pin, request))
        })
        .await
    }

    /// A grown-up adds a kid of its own, from 6 or from 12.
    pub async fn create_kid(self: Arc<Self>, actor_id: String, pin: String, name: String, kids_age: u8) -> ProfileOutcome {
        self.blocking(move |core| {
            let request = Change::CreateKid { name: &name, kids_age };
            core.state_db.guarded(|conn, wait, now| manage::change(conn, wait, now, &actor_id, &pin, request))
        })
        .await
    }

    /// Removes a profile — a grown-up with the kids it owns. The admin is
    /// never removed. `chosen_profile` clears itself when this was the one it
    /// named: see `profiles::chosen`.
    pub async fn delete_profile(self: Arc<Self>, actor_id: String, pin: String, id: String) -> ProfileOutcome {
        self.blocking(move |core| {
            let request = Change::Remove { id: &id };
            core.state_db.guarded(|conn, wait, now| manage::change(conn, wait, now, &actor_id, &pin, request))
        })
        .await
    }

    /// Opening a profile from the picker: a kid opens freely, a grown-up with its PIN.
    pub async fn unlock_profile(self: Arc<Self>, id: String, pin: String) -> ProfileOutcome {
        self.blocking(move |core| {
            core.state_db.guarded(|conn, wait, now| manage::unlock(conn, wait, now, &id, &pin))
        })
        .await
    }

    /// Makes `id` the household's admin while there is none; a profile
    /// without a PIN takes `pin` as its first.
    pub async fn claim_admin(self: Arc<Self>, id: String, pin: String) -> ProfileOutcome {
        self.blocking(move |core| {
            core.state_db.guarded(|conn, wait, now| manage::claim_admin(conn, wait, now, &id, &pin))
        })
        .await
    }

    /// A grown-up's own PIN, or — for the admin — another grown-up's. A
    /// grown-up from before PINs sets its first with any `pin`, `""` included.
    pub async fn set_pin(self: Arc<Self>, actor_id: String, pin: String, id: String, new_pin: String) -> ProfileOutcome {
        self.blocking(move |core| {
            let request = Change::SetPin { id: &id, new_pin: &new_pin };
            core.state_db.guarded(|conn, wait, now| manage::change(conn, wait, now, &actor_id, &pin, request))
        })
        .await
    }

    /// A parent sets its kid's limit: 6 or 12.
    pub async fn set_kids_age(self: Arc<Self>, actor_id: String, pin: String, id: String, kids_age: u8) -> ProfileOutcome {
        self.blocking(move |core| {
            let request = Change::SetKidsAge { id: &id, kids_age };
            core.state_db.guarded(|conn, wait, now| manage::change(conn, wait, now, &actor_id, &pin, request))
        })
        .await
    }
}
```

- [ ] **Step 5: edit `src/api/state.rs`**
  - module doc, "Nothing here throws" paragraph: the list of "nothing happened" values gains
    "or `ProfileOutcome::Invalid`".
  - add `mod profile_roles;` after `mod collections;`.
  - delete `create_profile` (`:52-63`) and `delete_profile` (`:82-88`).
  - `StateSnapshot`, as the **last** field (positional Kotlin constructors keep compiling):

```rust
    /// The live Kids marks that say "from 6" — a subset of `kids`, whose
    /// other marks are "from 12".
    #[uniffi(default = [])]
    pub kids_from_six: Vec<String>,
```

  - `snapshot`: `kids_from_six: rows::kids_from_six(conn)?,` after `editors_choice`.
  - `set_kids`:

```rust
    /// Marks a title as a child's — `Some(6)` "from 6", `Some(12)` "from
    /// 12" — or takes the mark off with `None`. Not scoped to a profile —
    /// see `state::schema` on why.
    pub async fn set_kids(self: Arc<Self>, set_id: String, age: Option<u8>) {
        self.blocking(move |core| core.state_db.with(|conn| rows::set_kids(conn, &set_id, age)))
            .await;
    }
```

- [ ] **Step 6:** `cargo test -p mediagram-core -q` — green (unit, fixtures, `api_surface`,
  `state_retirement`). Then
  `cargo clippy --all-targets --all-features -- -D warnings && cargo test -p mediagram --test code_standards -q` — clean.
  No commit yet: Task 7's bindings belong in the same commit.

### Task 7: Regenerate the bindings, rebuild the native core

- [ ] **Step 1:** `cargo ndk --version && ls /home/andre/android-sdk/ndk/28.2.13676358/source.properties` — both present.
- [ ] **Step 2: build all four ABIs and regenerate**

```bash
CARGO_INCREMENTAL=0 ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/generate-android-bindings.sh
ls -l android/core/rust/src/main/jniLibs/*/libmediagram_core.so
```

Expected: four `.so` files with a fresh timestamp; the script exits 0.
- [ ] **Step 3: check the generated surface** —
  `f=android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt; grep -nE "sealed class ProfileOutcome|suspend fun (createFirstAdmin|createGrownUp|createKid|deleteProfile|unlockProfile|claimAdmin|setPin|setKidsAge|setKids)\(|kidsFromSix|hasPin|kidsAge" $f; grep -c "createProfile" $f`
  — expected: every name above present, `deleteProfile(actorId…, pin…, id…)`, `setKids(…, age: kotlin.UByte?)`,
  and `0` for `createProfile`. Paste the real Kotlin signatures into the phase-06 notes if any differ
  from § Interfaces. `git diff --check -- $f` — clean (the script trims whitespace).
- [ ] **Step 4: Kotlin probe** —
  `cd android && ./gradlew -q :core:rust:compileDebugKotlin` — expected: passes (the bindings compile).
  `./gradlew -q :core:data:compileDebugKotlin` — expected: FAILS at `WatchStateRepository.kt:255,271,306`
  only. That is phase 06's starting list (with the files in § Key insights); do not fix Kotlin here.
- [ ] **Step 5: commit (not pushed alone — lands with phase 06).** Bump (patch), then:

```bash
git add crates/mediagram-core/src/api/state.rs crates/mediagram-core/src/api/state/profile_roles.rs \
  crates/mediagram-core/tests/api_surface.rs crates/mediagram-core/tests/state_retirement.rs \
  crates/mediagram-core/tests/state_seed/mod.rs \
  android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(core): profile roles, PINs and kid limits across the boundary; release <next>" \
  -m "create_profile and delete_profile(id) give way to PIN-checked calls answering a ProfileOutcome; set_kids takes an age and the snapshot lists marks from 6. The Kotlin side follows in the next commit; this one does not build the Android app alone."
```

### Task 8: Verify

- [ ] **Step 1:** `cargo test --workspace -q` — green (incl. `code_standards`, both fixture runners).
- [ ] **Step 2:** `cargo clippy --all-targets --all-features -- -D warnings` — clean.
- [ ] **Step 3:** `rg -n "create_profile" crates/` — no hits.
- [ ] **Step 4:** `rg -n -i "phase[ -]?0[0-9]" crates/mediagram-core/src crates/mediagram-core/tests` — no hits.

## Todo list

- [ ] Task 0 preflight (fixtures present; phase 02's order checked against amended §3)
- [ ] Task 1 pin.rs + pin-hash runner
- [ ] Task 2 pin_wait.rs
- [ ] Task 3 rules.rs + profile-rules runner
- [ ] Task 4 Profile fields + cascading delete
- [ ] Task 5 request.rs / role_rows.rs / manage.rs (amended order, create_first) + StateDb count
- [ ] Task 6 api/state/profile_roles.rs, api/state.rs, integration tests
- [ ] Task 7 bindings + `.so` regenerated; Kotlin probe recorded; commit (unpushed until 06)
- [ ] Task 8 verify

## Success criteria

- `pin_hash_fixtures_match_the_web` and `profile_rule_fixtures_match_the_web` run real cases and pass.
- `manage_tests` pins every row of § Outcome order, including review focus 1 (pre-upgrade grown-up
  sets its first PIN) and 5 (five wrong, then the right one → still `Wait`), structural refusals before
  the wait, no wait without a PIN to compare, and `create_first` on an empty, kids-only and
  grown-up-holding store.
- `api_surface` exercises every new call across the boundary; storage failure answers `Invalid`.
- `Profile` exposes `has_pin` and nothing of the hash (by type: no hash/salt field exists).
- Regenerated `mediagram_core.kt` matches § Interfaces; all four ABI `.so` rebuilt from this commit;
  `:core:rust` compiles, `:core:data` fails only where listed.
- Every file under `crates/*/src` ≤ 200 lines; clippy clean.

## Risk assessment

| Risk | L×I | Mitigation |
|---|---|---|
| Android build red between this phase and 06 | certain × M | Commit body says so; 05+06 pushed together (plan.md); the probe lists the exact breakages |
| Stale `.so` against new bindings → `UnsatisfiedLinkError`/checksum failure at launch | M×H | The generate script rebuilds all four ABIs from the same tree it generates from; never regenerate from an old `.so` |
| `ANDROID_NDK_HOME` unset in the agent shell | H×L | Explicit path in the command |
| Web phase 02 orders refusals differently from amended §3 (no shared fixture pins outcomes) | M×M | Task 0 checks phase 02 against §3 and tells the lead; order is one function (`change`) and one table of tests |
| Deadlock between the wait and the connection lock | L×H | Only `guarded` takes the wait lock, always before `with`'s |
| Nested transaction (`delete` inside `apply`) | M×M (caught) | `Remove` is applied without an outer transaction; `removing_a_grown_up_takes_its_kids…` covers it |
| First profile on a fresh device | resolved | `create_first_admin` (contract §3 `create-first`); allowed only with no grown-up here |
| A device bootstraps an admin before its first sync, then learns of an older claim | M×L | Merge's earliest-claim rule demotes it (phase 04); no special case, per contract §3 |
| A file crosses 200 lines (`profiles.rs` ~192) | M×L | Budget table; `code_standards` at Tasks 5, 6, 8; if over, move `list` into `role_rows` |

Rollback: revert Task 7's commit together with phase 06's (they are one change for Android). Tasks 1–5
are internal and revert independently, newest first.

## Security considerations

- The PIN hash and salt never cross uniffi: `Profile` carries `has_pin` only; `role_rows::Stored` is
  private to `state::profiles`. They leave the device only inside the sync document (phase 04).
- Comparison uses full-length XOR folding (`pin::verify`), no early exit.
- Salt from the OS RNG (`getrandom::fill`), 16 bytes, fresh on every PIN write.
- Wrong-PIN wait bounds guessing at 5/min per `Core`; resets on restart — the stated ceiling
  (spec §2, `pin_wait.rs` header).
- Honest ceiling written where the next reader finds it: `pin.rs` and `manage.rs` headers (spec §1:
  not a login; `adb` gets past it).
- No PIN is logged: `StateDb::with` logs only rusqlite errors, which carry no bound parameters; no
  new logging added.
- Every management action is enforced in the core, not only in the UI (spec §1).

## Next steps

Phase 06: Android model/repository/view models against the regenerated bindings (start from the Kotlin
break list above, plus `createFirstAdmin` for contract §12's "no grown-up" picker state), rebuild
confirmed `.so` on `ANDROID_SERIAL=caad49da`. Phase 08 amends the spec with the contract's 2026-09-28
amendments (create-first, the refusal order).
