# Phase 03 — Rust core: rule, store, device household, sync, management, bindings

**Spec:** controller calls 1–8, 11–15. **Status:** pending. **Depends on:** phase 01. **Runs beside** phase 02.
**Pattern to copy:** the core half of hidden sections on `feat/profiles-page` (its Task 3.3 commit): `schema/roles.rs`, `role_rows.rs`, `record/roles_record.rs`, `merge/roles.rs`, `exchange/roles.rs`, `profiles/manage/hidden_sections.rs`, `api/state/profile_roles.rs`, `tests/api_surface.rs`, `scripts/generate-android-bindings.sh`.
**Line limit:** every non-test `.rs` ≤200 (`crates/mediagram/tests/code_standards.rs`). `profiles.rs` and `role_rows.rs` sit at it: split before adding.
**Words** (`household-rules.json` `words`/`lines`) are not ported here; the core speaks in outcomes.

### Task 3.1: The household rule, ported

**Files:**
- Create: `crates/mediagram-core/src/state/households.rs`: the module root, with `pub const HOME: &str = "home";` and `pub mod rules;`.
- Create: `crates/mediagram-core/src/state/households/rules.rs`:
  - `MemberView {id, kids, owner, parent_id, household: Option<String>}`;
  - `HouseholdRecord {id, admin_id: Option<String>, removed}`;
  - `ResolvedView {household, admin}`;
  - `view(profiles, households) -> (HashMap<String, ResolvedView>, BTreeMap<String, Option<String>>)`;
  - `HouseholdAction`, kebab-case, the seven actions;
  - `allowed(profiles: &[RoleView], live: &[String], actor_id, action, household_id, target_id) -> bool`, over the `RoleView` of `profiles/rules.rs`, which gains `owner: bool` (`#[serde(default)]`);
  - `device_household(stored, chosen, live, heard) -> Option<String>`.
  
  Each is a line-for-line port of `web/public/lib/household-rules.js`.
- Modify: `crates/mediagram-core/src/state/mod.rs`: `pub mod households;`.
- Modify: `crates/mediagram-core/tests/shared_watch_state_fixtures.rs`: tests `household_view_fixtures`, `household_allowed_fixtures`, `device_household_fixtures` over `household-rules.json`.

- [ ] **Step 1:** Write the three runners. **Run** `cargo test -p mediagram-core --test shared_watch_state_fixtures household` → FAIL (doesn't compile).
- [ ] **Step 2: Implement. Run** → PASS; then `cargo clippy -p mediagram-core --all-targets -- -D warnings`.
- [ ] **Step 3: Commit** `feat(core): the household rule, held to the web's fixture`.

### Task 3.2: Store, resolved profiles, and this device's household

**Files:**
- Modify: `src/state/schema/roles.rs`: `HOUSEHOLDS`, the web's `HOUSEHOLDS_GROUP` statement for statement (Task 2.1). Then `schema.rs` lists it in `GROUPS` (v12 at planning time).
- Modify: `src/state/households.rs`:
  - `#[derive(uniffi::Record)] Household {id, name, admin_id: Option<String>}`;
  - `load_rows(conn)`;
  - `live(conn, profiles: &[Profile])`: live households, admin resolved, sorted by `normal_name(name)` in code-point order;
  - `resolve(rows: Vec<Stored>, households) -> Vec<Profile>`.
- Create: `src/state/households/device.rs`. `state_meta` key `joined_household` is local, never exported. Functions:
  - `joined(conn)`: applies `device_household(stored, chosen profile's resolved household, live ids, sync::round_imported)`, writes the answer when it is `Some` and differs from what is stored, and returns it;
  - `profiles_here(conn)`: the joined household's profiles, empty while `joined` is `None`;
  - `chosen_here(conn)`: `profiles::chosen`, but only when that profile is in the joined household.
- Modify: `src/state/profiles.rs`: `Profile` gains `#[uniffi(default = false)] owner: bool` and `household: String`, defaulted to `"home"` (declared last; use whatever literal default uniffi 0.32 accepts for a `String`, else a non-defaulted field and named-argument callers). `admin` now means "runs its household". `list(conn)` resolves through `households::resolve`. First move `Profile` and its `From<Stored>` into `src/state/profiles/profile.rs` (`pub use`), so `profiles.rs` stays ≤200.
- Modify: `src/state/profiles/role_rows.rs`: `Stored` gains `household_id` and `household_updated_at`, and `load`/`insert` carry them. `NewProfile.household: Option<String>`, dated `now` when given. `view()` gives `household` and `owner` (the claim). If it would pass 200 lines, split the inserts into `role_rows/insert.rs`.
- Modify: `src/api/state.rs`: `profiles()` → `households::device::profiles_here`, and `chosen_profile()` → `households::device::chosen_here`. Line count unchanged.

- [ ] **Step 1: Failing tests.**
  - In `src/state/upgrade_tests.rs`: `a_store_from_before_households_puts_everyone_in_home_run_by_the_owner` (the web's v16 test, mirrored).
  - Create `src/state/households/device_tests.rs`:
    - `a_device_that_chose_before_joins_its_profiles_household`;
    - `a_new_device_joins_the_only_household_once_heard`;
    - `a_new_device_with_two_households_joins_none`;
    - `profiles_lists_only_the_joined_household`;
    - `the_chosen_profile_outside_the_joined_household_is_nobody`.
  - Create `src/state/households_tests.rs`:
    - `a_kid_is_listed_in_its_parents_household`;
    - `a_profile_whose_household_is_gone_is_listed_in_home`;
    - `households_are_listed_live_by_name_with_who_runs_them`.
- [ ] **Step 2: Run** `cargo test -p mediagram-core households upgrade` → FAIL. **Step 3: Implement.** **Step 4: Run** `cargo test --all`, then clippy → PASS.
- [ ] **Step 5: Commit** `feat(core): households in the store, and the household this device joined`.

### Task 3.3: Sync — households and each profile's household

**Files:**
- Create: `src/state/record/households_record.rs`:
  - `HouseholdEntry {id, name: Option<HouseholdName{text, updated_at}>, admin: Option<HouseholdAdmin{name, updated_at}>, removed: bool}`, serde camelCase, `skip_serializing_if` on each optional;
  - `parse_households(&Value) -> Vec<HouseholdEntry>`, hostile like `record/parse.rs`.
- Modify: `src/state/record.rs`: `SyncRecord.households: Vec<HouseholdEntry>`, `#[serde(default, skip_serializing_if = "Vec::is_empty")]`.
- Modify: `src/state/record/parse.rs`: read `households`.
- Modify: `src/state/record/roles_record.rs`: `ProfileRoles.household: Option<ProfileHousehold{id, updated_at}>`.
- Create: `src/state/merge/households.rs`: `merge_households`, the web's `mergeHouseholds`.
- Modify: `src/state/merge.rs`, `merge/merged.rs`: `MergedState.households`.
- Modify: `src/state/merge/roles.rs`: `household` by `keep`.
- Create: `src/state/exchange/households.rs`: `export`, `import`, the web's rules (Task 2.2).
- Modify: `src/state/exchange.rs`: export the households, and import them after the roles. `src/state/exchange/roles.rs`: the profile's household.
- Modify: `tests/shared_watch_state_fixtures.rs`: `households_sync_parse_fixtures` and `households_sync_merge_fixtures`, the merge in both orders.

- [ ] **Step 1: Failing tests:**
  - the two fixture runners;
  - `src/state/exchange/households_tests.rs`, the web's nine exchange tests in snake_case:
    - `a_household_nobody_changed_is_not_said`;
    - `a_renamed_home_is_said_with_its_name_alone`;
    - `an_admin_whose_profile_is_not_here_is_not_said`;
    - `a_profiles_household_is_said_once_it_is_dated`;
    - `a_household_heard_from_another_device_is_made_here_its_admin_found_by_name`;
    - `a_removal_is_taken_and_never_undone`;
    - `a_newer_local_rename_survives_an_older_merge`;
    - `a_profiles_newer_household_is_taken`;
    - `a_document_from_an_older_build_changes_no_household`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** `cargo test --all`, then clippy → PASS.
- [ ] **Step 5: Commit** `feat(core): households and each profile's household travel in the state sync`.

### Task 3.4: Management, the owner's actions, joining, the API and the bindings

**Files:**
- Modify: `src/state/profiles/manage/checks.rs`: `check()` builds its views from `households::resolve`, so the cross-household guard bites. `prove` becomes `pub(crate)`.
- Modify: `src/state/profiles/manage.rs`:
  - `create_grown_up` and `create_kid` make the profile in the actor's household;
  - `claim_admin` goes per household, as the web's (Task 2.3): no owner → today's claim; else `claim-household` writes the household's `admin_id`, dated.
- Create: `src/state/households/manage.rs`, with submodules under `households/manage/` as size needs: `create`, `rename`, `set_admin`, `move_to`, `delete`, `join`. Each is the web's Task 2.4, in the same refusal order, with the same stamps and the same join wait key `household:<id>`. `join` also stores `joined_household`.
- Create: `src/api/state/households.rs` (`#[uniffi::export(async_runtime = "tokio")] impl Core`):
  - `households() -> HouseholdsView {joined: Option<String>, households: Vec<Household>, everyone: Vec<Profile>}` (a `uniffi::Record`);
  - `join_household(id, pin) -> ProfileOutcome`;
  - `create_household(actor_id, pin, name, admin: NewHouseholdAdmin) -> ProfileOutcome`, with `enum NewHouseholdAdmin { Existing { id: String, new_pin: Option<String> }, New { name: String, new_pin: String } }` (`uniffi::Enum`);
  - `rename_household(actor_id, pin, id, name)`;
  - `set_household_admin(actor_id, pin, id, admin_id, new_pin: Option<String>)`;
  - `move_to_household(actor_id, pin, id, profile_id)`;
  - `delete_household(actor_id, pin, id)`.
  
  All `-> ProfileOutcome`, and none throws, as `profile_roles.rs`.
- Modify: `crates/mediagram-core/tests/api_surface.rs`: the new calls.
- Regenerate: `android/core/rust/.../mediagram_core.kt` (committed) and the per-ABI `libmediagram_core.so` (gitignored, so rebuilt but not committed), both through `ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/generate-android-bindings.sh`.
- Create: `android/core/testing/src/main/kotlin/testing/FakeHouseholdCalls.kt`. It keeps Android compiling: an abstract base `FakeCore` now extends, implementing the new interface calls as a core holding Home alone, every owner action `NotAllowed`. `FakeCore.kt` (714 lines) must not grow: its supertype line changes, nothing else. Phase 05 makes the fake real.

- [ ] **Step 1: Failing tests.**
  - `src/state/households/manage_tests.rs`, the web's Task 2.3 and 2.4 manage tests in snake_case:
    - `a_households_admin_adds_a_grown_up_into_its_own_household`;
    - `a_kid_is_made_in_its_parents_household`;
    - `the_admin_of_one_household_cannot_remove_a_grown_up_of_another`;
    - `with_no_owner_yet_the_claim_makes_the_owner_who_runs_home`;
    - `a_grown_up_of_a_household_nobody_runs_may_claim_it`;
    - `nobody_claims_a_household_somebody_runs`;
    - `the_owner_creates_a_household_with_a_new_grown_up_who_runs_it`;
    - `the_owner_creates_one_with_an_existing_grown_up_who_moves_in_with_their_kids`;
    - `a_households_name_is_unique_among_the_live_ones`;
    - `a_new_grown_ups_name_is_unique_on_the_whole_install`;
    - `an_admin_without_a_pin_is_given_one_and_refused_without`;
    - `the_owner_moves_a_grown_up_and_their_kids_each_dated`;
    - `a_households_last_grown_up_moves_out_and_the_emptied_household_can_be_deleted`;
    - `the_owner_deletes_an_empty_household_never_home`;
    - `only_the_owner_acts_on_households`;
    - `joining_takes_the_household_admins_pin_or_the_owners_and_remembers_it`;
    - `wrong_join_pins_wait_per_household_not_per_profile`.
  - `src/api/state/households_tests.rs`: `households_view_says_what_this_device_joined_and_everyone`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** `cargo test --all`, then `cargo clippy --all-targets --all-features -- -D warnings` → PASS.
- [ ] **Step 5: Bindings.** Run the generator above. Then run `cd android && ./gradlew :core:data:compileDebugKotlin :core:testing:compileDebugKotlin :app:compileDebugKotlin`, which must build: Kotlin's `uniffi Profile` callers use named arguments (`CoreProfileCalls.kt` `toModel` keeps compiling through the defaults).
- [ ] **Step 6: Commit** `feat(core): the owner's household actions and joining, across the boundary` (the generated Kotlin and the fake base go with it).
