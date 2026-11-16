# Phase 05 — Android: one owner view in the view models (tablet and TV share it)

Status: todo · Depends on: Phase 03, Phase 04 (web decisions are the reference)

## Context (all in `android/feature/catalog/src/main/kotlin/profile/` unless noted)

- `ManageProfilesViewModel.kt` (200, full): `Step` Closed/ChoosingActor/Managing, `heldPin`,
  `open()`, `actAs(id)`, `addKid(name, age, dailyLimit)`, `addGrownUp(name)`, `change{}`, `shared()`.
- `ManageUiState.kt` (71): `Managing(actor, grownUps, kids, notice, answers, households)`, `manageable()`.
- `ManageHouseholdParts.kt` (42): `ManagePart(id, name, grownUps, kids, addsGrownUp, addsKid)`,
  `Managing.parts()`; **:40 limits adds to `id == mine`**.
- `HouseholdsOwnerViewModel.kt` (200, full): own `heldPin`, `ownersPin` (:167-174), `open()`, `create`,
  `rename`, `changeAdmin`, `moveHere`, `delete`, `useHere`, `close()`.
- `HouseholdsOwnerUiState.kt` (94): `Open(rows, createChoices, ...)`, `HouseholdRow(...)`, `ownersPart()`.
- `PinAsk.kt` (141). `ui-common/.../profile/ManageActions.kt` (24): `onAddKid(name, age, dailyLimit)`, `onAddGrownUp(name)`.
- `core/model/.../HouseholdWords.kt` (71) + its `HOUSEHOLD_WORDS` map + `household-rules.json` `words`
  (checked by `HouseholdRulesFixtureTest.speaksTheWebsWords`).
- Tests: `feature/catalog/src/test/kotlin/profile/` — `ManageProfilesViewModelTest`,
  `ManageProfilesAcrossHouseholdsTest`, `ManageHouseholdPartsTest`, `HouseholdsOwnerViewModelTest`,
  `HouseholdsOwnerVisitsTest`, `HouseholdsOwnerHarness`. Run `./gradlew :feature:catalog:testDebugUnitTest`.

## Interfaces

- New `OwnerPin.kt`: `@ActivityRetainedScoped class OwnerPin @Inject constructor()` with
  `var held: String?` and `fun drop()` — the one owner PIN both view models read and write.
  Both `close()`s and a `WrongPin` answer call `drop()`.
- `ManageProfilesViewModel.open(chosen: Profile?)`: chosen owner → `Managing(owner)` at once, PIN asked at the first change.
- `ManageProfilesViewModel.asOwner()`: the owner's PIN is in `OwnerPin` → `Managing(owner)`.
- `ManagePart` gains `household: HouseholdRow?` (the owner's actions for it; null for everyone else),
  `kidParents: List<Profile>` (grown-ups a new kid may belong to, admin first); in the owner view
  `addsGrownUp = true` for every household and `addsKid = grownUps.isNotEmpty()`.
- `ManageActions.onAddGrownUp(name: String, household: String?)`,
  `onAddKid(name: String, age: Int, dailyLimit: Int?, parentId: String?)`.
- `HouseholdsOwnerViewModel.open()` after the PIN: calls back so the page shows the owner view
  (a `SharedFlow<Unit>` `ownerView`, like the existing `joined`) instead of a households-only `Open`.
- Both view models are at 200 lines: split before adding (e.g. `ManageAdds.kt` for the add
  actions, `HouseholdsOwnerActs.kt` for the household acts). Splits keep behaviour and tests.

### Task 5.1: Shared owner PIN and the split

- [ ] **Step 1:** Split both view models (no behaviour change); `:feature:catalog:testDebugUnitTest` → PASS.
- [ ] **Step 2: Failing tests:** `ownersPinGivenToHouseholdsIsNotAskedAgainByManage`,
  `ownersPinGivenToManageIsNotAskedAgainByHouseholds`, `closingThePageDropsTheOwnersPin`, `aWrongPinDropsItForBoth`.
- [ ] **Step 3: Implement** `OwnerPin` and wire both view models to it.
- [ ] **Step 4:** run → PASS. **Step 5: Commit** — `refactor(android): one owner PIN for the whole Profiles page`

### Task 5.2: Owner view state and add-anywhere

- [ ] **Step 1: Failing tests** (mirror Phase 04's names in camelCase):
  `watchingAsTheOwnerOpensManagingWithoutWhoAreYou`, `theFirstChangeAsksTheOwnersPinOnce`,
  `addGrownUpInAnotherHouseholdSendsThatHousehold`, `addKidElsewhereBelongsToItsAdmin`,
  `aHouseholdWithTwoGrownUpsListsBothAsKidParents`, `anEmptyHouseholdAddsNoKid`,
  `ownerButtonOpensTheWholeOwnerView`, `partsCarryTheirHouseholdsActionsForTheOwnerOnly`,
  `aHouseholdAddedElsewhereAppearsWithoutAskingThePinAgain`, `choosingTheOwnerInWhoAreYouOpensTheOwnerView`.
- [ ] **Step 2:** run → FAIL. **Step 3: Implement** per Interfaces. Any new word: `HouseholdWords.kt`,
  its map, and `household-rules.json` `words` together.
- [ ] **Step 4:** `./gradlew :feature:catalog:testDebugUnitTest :core:model:testDebugUnitTest` → PASS.
- [ ] **Step 5: Commit** — `feat(android): the owner's Manage holds every household's actions and adds anywhere`
