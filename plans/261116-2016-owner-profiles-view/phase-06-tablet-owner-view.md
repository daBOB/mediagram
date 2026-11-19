# Phase 06 — Tablet/phone: the owner view on the Profiles page

Status: todo · Depends on: Phase 05

## Context (`android/ui-mobile/src/main/kotlin/ui/`)

- `settings/ProfilesPage.kt` (150): You → `Household()`: *Everyone here*, `ManageProfilesScreen`,
  `PinDialog`, `ThisDevice()`, `HouseholdsOwnerBlock()`.
- `settings/HouseholdsOwnerBlock.kt` (173): `Closed` = note + *Owner* pill; `Open` = `OpenPart` →
  `HouseholdEntry` rows + `CreateHouseholdForm`; own `PinDialog`.
- `settings/HouseholdForms.kt` (130): `RenameForm`, `PersonForm`, `CreateHouseholdForm`.
- `profile/ManageProfilesScreen.kt` (106), `profile/ManageProfilesParts.kt` (121): `ManagePartRows`
  (heading, grown-ups, `NameForm(ADD_A_GROWN_UP)`, kids, private `AddKidForm`). No role line, no household actions.
- `profile/ProfileForms.kt` (149): `NameForm`, `LimitChoice`, `DailyLimitChips`, `ProfileRow`.
- Tests: `ui-mobile/src/test/kotlin/ui/profile/` (`ManageProfilesScreenTest`, `ManageProfilesFlowTest`,
  `ManageProfilesHouseholdsTest`, `HouseholdsOverPickerTest`) and `.../ui/settings/`
  (`HouseholdsOwnerBlockTest`, `HouseholdsOwnerWatchingTest`, `HouseholdSwitchTest`). Robolectric sdk 35.
  Run `./gradlew :ui-mobile:testDebugUnitTest`.

## Behaviour — match the web's Phase 04 view

- `HouseholdEntry`'s header (name, line, actions, form slot) moves to a `HouseholdHeader(row: HouseholdRow, ...)`
  composable drawn by `ManagePartRows` when `part.household != null`.
- Each person's `ProfileRow` gets the role line *Everyone here* shows today.
- *Add a grown-up* calls `onAddGrownUp(name, part.id)`; *Add a kid* calls `onAddKid(..., parentId)`
  with a *Belongs to* `PersonForm`/chips when `part.kidParents.size > 1`.
- `CreateHouseholdForm` follows the last part in the owner view.
- While the owner view shows: no *Everyone here*, no `HouseholdsOwnerBlock`. Elsewhere the block is only
  the note + *Owner* button, which opens the owner view (Phase 05 `ownerView`).

### Task 6.1

- [ ] **Step 1: Failing tests** in a new `ui-mobile/src/test/kotlin/ui/settings/OwnerViewTest.kt`:
  `ownerSeesEveryHouseholdWithItsActions`, `addGrownUpUnderMadaniSendsMadani`, `kidFormAsksBelongsToOnlyWithTwoGrownUps`,
  `emptyHouseholdHasNoAddKid`, `everyoneHereAndOwnerButtonHiddenInOwnerView`, `ownerButtonOnAnotherHouseholdsDeviceOpensOwnerView`,
  `takenNameIsSaidUnderThatHouseholdsForm`.
- [ ] **Step 2:** run → FAIL. **Step 3: Implement**; update older tests pinned to the two-halves page
  (keep their non-owner cases). `HouseholdsOwnerBlock.kt` shrinks to the gate.
- [ ] **Step 4:** `./gradlew :ui-mobile:testDebugUnitTest` → PASS.
- [ ] **Step 5:** Install on the tablet only (`ANDROID_SERIAL=caad49da`, never a bare `installDebug`),
  check the avatar before touching anything, walk: Settings → Profiles as andre; picker → Manage profiles.
  Navigate only; do not create or change profiles on the real install.
- [ ] **Step 6: Commit** — `feat(android): the owner manages every household from one view on the tablet`
