# Phase 05 — Android data: model, core calls, households repository, view models

**Spec:** controller calls 6, 7, 11, 13, 15. **Status:** pending. **Depends on:** phase 03 (bindings and `.so`); words from phase 01.
**Pattern to copy:** `android/core/model/src/main/kotlin/DailyLimit.kt` + `DailyLimitFixtureTest.kt` (words held to a web fixture), `ProfileRoles.kt` + `ProfileRolesFixtureTest.kt`, `core/data/CoreProfileCalls.kt`, `feature/catalog/profile/ManageProfilesViewModel.kt` + `PinAsk.kt`.
**Kotlin line rule:** a file over 200 lines must not grow. That covers `WatchStateRepository.kt` (470), `FakeCore.kt` (714), `FakeProfiles.kt` (305) and `ProfileViewModel.kt` (234), none of which this phase grows. New files stay ≤200.
**Filtering is the core's** (Task 3.2): `core.profiles()` is already this device's household, so `WatchStateRepository.profiles` and everything on it (picker, Everyone here, Manage, index count) follow with no change here.

### Task 5.1: The model — households, the rule port and the words

**Files:**
- Modify: `android/core/model/src/main/kotlin/WatchSnapshot.kt`: `Profile.owner: Boolean = false`, after `household`.
- Create: `android/core/model/src/main/kotlin/Households.kt`:
  - `data class Household(val id: String, val name: String, val adminId: String?)`;
  - `data class Households(val joined: String? = null, val list: List<Household> = emptyList(), val everyone: List<Profile> = emptyList())`, with:
    - `fun needsJoin(synced: Boolean) = joined == null && synced`;
    - `val here: Household?`;
    - `val owner: Profile?` (`owner && !kids`);
    - `fun members(id: String): Int`;
  - `enum class HouseholdAction(val wire: String)`: `CREATE("create-household")`, `RENAME("rename-household")`, `SET_ADMIN("set-household-admin")`, `MOVE("move-to-household")`, `DELETE("delete-household")`, `JOIN("join-household")`, `CLAIM("claim-household")`;
  - `fun List<Profile>.householdAllowed(live: List<String>, actorId: String, action: HouseholdAction, householdId: String, targetId: String?): Boolean`, the web's `householdAllowed` line for line.
- Create: `android/core/model/src/main/kotlin/HouseholdWords.kt`:
  - one `const val` per `HOUSEHOLD_WORDS` key, named in SCREAMING_SNAKE (`HOUSEHOLDS`, `OWNER`, `OWNER_NOTE`, `OWNERS_PIN`, `THIS_DEVICE`, `SWITCH_HOUSEHOLD`, `JOIN_A_HOUSEHOLD`, `JOIN_NOTE`, `CREATE_A_HOUSEHOLD`, `HOUSEHOLD_NAME`, `ADMIN`, `A_NEW_GROWN_UP`, `RENAME`, `CHANGE_ADMIN`, `MOVE_HERE`, `USE_HERE`, `DELETE`, `HOUSEHOLD_NAME_TAKEN`, `PROFILE_NAME_TAKEN`);
  - `fun householdLine(adminName: String?, count: Int)` and `fun joinPinTitle(adminName: String?)`;
  - `val HOUSEHOLD_WORDS: Map<String, String>`, keyed as the web's object, for the fixture test.
- Create: `android/core/model/src/test/kotlin/HouseholdRulesFixtureTest.kt`, over `household-rules.json`:
  - `agreesWithTheWebOnWhoMayDoWhat`, the `allowed` section;
  - `speaksTheWebsWords`, `words` and `lines`.
  
  The `view` and `device` sections are the core's and are not run here.

- [ ] **Step 1:** Write the test. **Run** `cd android && ./gradlew :core:model:test --tests '*HouseholdRulesFixtureTest*'` → FAIL.
- [ ] **Step 2: Implement. Run** → PASS; then `./gradlew :core:model:test detekt`.
- [ ] **Step 3: Commit** `feat(android): the household rule and its words, held to the web's fixture`.

### Task 5.2: Core calls, the households repository, and the fakes

**Files:**
- Create: `android/core/model/src/main/kotlin/HouseholdRequests.kt`, more `ProfileRequest` subclasses (same module and package as the sealed interface):
  - `JoinHousehold(id, pin)`;
  - `CreateHousehold(actorId, pin, name, adminId: String?, adminName: String?, newPin: String?)`;
  - `RenameHousehold(actorId, pin, id, name)`;
  - `SetHouseholdAdmin(actorId, pin, id, adminId, newPin: String?)`;
  - `MoveToHousehold(actorId, pin, id, profileId)`;
  - `DeleteHousehold(actorId, pin, id)`.
- Modify: `android/core/data/src/main/kotlin/CoreProfileCalls.kt`:
  - `sendTo` answers each with its core call; `CreateHousehold` maps to `NewHouseholdAdmin.Existing` or `.New`, and a missing `newPin` for `.New` is `Invalid`;
  - `toModel` passes `owner` and `household` by name;
  - add `fun HouseholdsView.toModel(): Households`.
- Create: `android/core/data/src/main/kotlin/HouseholdsRepository.kt`:
  - the interface: `val view: StateFlow<Households>`, `suspend fun reload()`;
  - `DefaultHouseholdsRepository(coreProvider, watchState, @AppScope scope, dispatcher)`, which reads `core.households()`;
  - it reloads by itself whenever `watchState.profiles` or `watchState.syncedOnce` emits. Every household action goes through `WatchStateRepository.manage`, which re-reads profiles already, so `WatchStateRepository.kt` needs no change;
  - a failed read keeps the last view.
- Modify: `android/core/data/src/main/kotlin/di/DataModule.kt`: bind it, as the other repositories are bound.
- Create: `android/core/testing/src/main/kotlin/testing/FakeHouseholds.kt`: in-memory households, the joined household, join (admin's or owner's PIN), and the owner's actions. Every refusal comes from `householdAllowed`, in the core's order.
- Modify: `FakeHouseholdCalls.kt` (from Task 3.4): delegate to `FakeHouseholds`.
- Modify: `FakeCore.kt`: `profiles()` and `chosenProfile()` answer for the joined household. Both are edits in place; the file must not grow.
- Modify: `android/core/testing/src/main/kotlin/testing/CoreContract.kt`: cases run against `FakeCore`:
  - `profilesAreTheJoinedHouseholds`;
  - `joiningTakesTheAdminsOrTheOwnersPin`;
  - `ownerActionsAreTheOwners`.

- [ ] **Step 1: Failing tests:**
  - `android/core/data/src/test/kotlin/HouseholdsRepositoryTest.kt`:
    - `readsWhatTheCoreSays`;
    - `rereadsWhenTheProfilesChange`;
    - `rereadsWhenTheFirstRoundLands`;
    - `keepsTheLastViewWhenAReadFails`;
  - `CoreProfileCallsTest` (create if absent): `eachHouseholdRequestIsItsCoreCall`, `aNewAdminWithoutAPinIsInvalid`;
  - the three `CoreContract` cases.
- [ ] **Step 2: Run** `./gradlew :core:data:testDebugUnitTest :core:testing:test` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS; then `detekt`.
- [ ] **Step 5: Commit** `feat(android): households from the core, and their requests`.

### Task 5.3: View models — joining, switching, and the owner's part

**Files:**
- Create: `android/feature/catalog/src/main/kotlin/profile/JoinHouseholdViewModel.kt`:
  - `state: StateFlow<JoinUiState>`, where `JoinUiState(households: List<Household>, adminNames: Map<String, String?>, notice: String?)`. The households offered are every live one, or every one but the device's own when switching (`switching(true)`);
  - `pick(id)` → `PinAsk` titled `joinPinTitle(adminName)` → `repository.manage(JoinHousehold(id, pin))` (not `manageAndShare`: joining changes nothing synced);
  - when done, `households.reload()` and `joined: SharedFlow<Boolean>` emits whether the chosen profile is still here, i.e. `repository.chosenProfileId.value != null` after the reload.
- Create: `android/feature/catalog/src/main/kotlin/profile/HouseholdsOwnerViewModel.kt`:
  - `state`: `Closed`, or `Open(rows, notice)`. Each row: `household`, `line` (`householdLine`), `isHere`, `canRename`, `canChangeAdmin`, `canDelete`, `adminChoices`, `moveChoices`, all from `householdAllowed` over `view.everyone`;
  - `open()`: the owner's PIN through `PinAsk` (`OWNERS_PIN`, `Unlock(owner.id, pin)`), held until `close()`;
  - `openAsOwner()`: the owner chosen, the PIN asked at the first action;
  - `create(name, adminId?, adminName?, newPin?)`, `rename`, `changeAdmin`, `moveHere`, `delete` → `repository.manageAndShare(request, sync)`, then `households.reload()`;
  - `useHere(id)` → `JoinHousehold(id, heldPin)`;
  - a refusal sets `notice`. `NameTaken` reads `HOUSEHOLD_NAME_TAKEN` for a household's name and `PROFILE_NAME_TAKEN` for a new grown-up's.
- Modify: `android/feature/catalog/src/main/kotlin/profile/ProfileWords.kt`:
  - `ProfileOutcome.NameTaken.sentence()` → `PROFILE_NAME_TAKEN`;
  - `everyoneHere` prefixes `"$OWNER · "` for the owner.
- Modify: `ui-common/src/main/kotlin/ui/common/settings/ProfilesVisit.kt`: leaving the visit also calls `HouseholdsOwnerViewModel.close()`, so the owner's PIN goes when Manage's does.

- [ ] **Step 1: Failing tests** (`android/feature/catalog/src/test/kotlin/profile/`):
  - `JoinHouseholdViewModelTest`:
    - `offersEveryHouseholdToADeviceThatHasNone`;
    - `switchingLeavesOutThisDevicesHousehold`;
    - `theAdminsPinJoins`;
    - `theOwnersPinJoins`;
    - `aWrongPinStaysOpenWithTheReason`;
    - `saysWhenTheChosenProfileWasLeftBehind`;
  - `HouseholdsOwnerViewModelTest`:
    - `theOwnersPinOpensThePartOncePerVisit`;
    - `rowsOfferWhatTheRuleAllows`;
    - `changeAdminIsNotOfferedForTheOwnersHousehold`;
    - `eachActionSendsTheHeldPin`;
    - `aTakenHouseholdNameSaysSoInItsOwnWords`;
    - `closeDropsThePin`;
  - `ProfileWordsTest`: `aTakenNameIsUsedOnThisInstall`, `theOwnerIsSaidFirstInEveryoneHere`.
- [ ] **Step 2: Run** `./gradlew :feature:catalog:testDebugUnitTest` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS; then `detekt`, `lint`.
- [ ] **Step 5: Commit** `feat(android): joining a household and the owner's actions, behind view models`.
