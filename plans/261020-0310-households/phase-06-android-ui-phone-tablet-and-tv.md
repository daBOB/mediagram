# Phase 06 — Android UI: Join a household, Switch household, the owner's part (phone/tablet and TV)

**Spec:** controller calls 6, 7, 13. **Status:** pending. **Depends on:** phase 05; phase 04's screenshots are the look to match (the web is the reference).
**Pattern to copy:**
- phone: `ui-mobile/.../profile/ProfileGate.kt`, `ProfilePickerScreen.kt`, `PinDialog.kt`, `ProfileForms.kt`, `ui-mobile/.../settings/ProfilesSettingsSection.kt` (the one Profiles page);
- TV: `ui-tv/.../profile/TvProfileGate.kt`, `TvProfilePicker.kt`, `TvPinPrompt.kt`, `TvManageProfiles.kt` + `TvManageDialogs.kt`, `ui-tv/.../settings/TvProfilesSection.kt` (its full-screen panel from a row, ruling R9 on `feat/profiles-page`).

**Words** only from `model/HouseholdWords.kt`.
**Kotlin line rule:** files over 200 lines must not grow: `TvSettingsScreen.kt` (201), `TvProfilesFlowTest.kt` (501). New tests go in new files.
**TV focus:** read memory `tv-compose-focus-gotchas` first: focusRestorer hijacks, unkeyed rows, a conditional focusRequester resets the target.

**After a switch that leaves the chosen profile behind:** call `ProfileViewModel.reopen()`, or the picker never shows. `Mode.Chosen` with an id no longer listed reads as Loading (`ProfileViewModel.kt` state, "the chosen row has not reached [profiles] yet").

### Task 6.1: Phone and tablet — Join a household, and Switch household

**Files:**
- Create: `android/ui-mobile/src/main/kotlin/ui/profile/JoinHouseholdScreen.kt`: the web's `household-join.js`. Title `JOIN_A_HOUSEHOLD`, then `JOIN_NOTE`, then one button per household; `PinDialog` for the PIN. It serves the gate and the switch dialog alike.
- Modify: `android/ui-mobile/src/main/kotlin/ui/profile/ProfileGate.kt`: before every other branch, `households.needsJoin(synced) → JoinHouseholdRoute`. Back there leaves the app, as on a first run.
- Modify: `android/ui-mobile/src/main/kotlin/ui/settings/ProfilesSettingsSection.kt`: below Manage, for anyone but a kid, and only with more than one household:
  - a `THIS_DEVICE` row showing the household's name;
  - a `SWITCH_HOUSEHOLD` button opening `JoinHouseholdScreen` in a dialog (`switching(true)`);
  - when `joined` emits `false`, `ProfileViewModel.reopen()`.
- Tests:
  - create `android/ui-mobile/src/test/kotlin/ui/profile/JoinHouseholdFlowTest.kt`:
    - `aDeviceWithTwoHouseholdsAndNoneJoinedAsksWhichToJoin`;
    - `theAdminsPinShowsThatHouseholdsPicker`;
    - `aWrongPinStaysOnThePrompt`;
  - create `android/ui-mobile/src/test/kotlin/ui/settings/HouseholdSwitchTest.kt`:
    - `theProfilesPageSaysThisDevicesHouseholdOnlyWithTwo`;
    - `switchingAwayFromTheChosenProfileShowsThePicker`;
    - `aKidSeesNoHouseholdRow`.

- [ ] **Step 1:** Write the tests. **Run** `cd android && ./gradlew :ui-mobile:testDebugUnitTest --tests '*Household*'` → FAIL.
- [ ] **Step 2: Implement. Run** → PASS; then `detekt`, `lint`.
- [ ] **Step 3: Commit** `feat(android): a phone or tablet joins one household and can switch`.

### Task 6.2: Phone and tablet — the owner's Households part

**Files:**
- Create: `android/ui-mobile/src/main/kotlin/ui/settings/HouseholdsOwnerBlock.kt`: the web's `settings-households.js` on `HouseholdsOwnerViewModel`.
  - `HOUSEHOLDS` heading. A non-owner sees `OWNER_NOTE` and an `OWNER` button (only when an owner exists); with the owner chosen, the part shows directly.
  - One row per household: its name, its `line`, and `RENAME`, `CHANGE_ADMIN`, `MOVE_HERE`, `USE_HERE`, `DELETE`, each as the row allows.
  - `CREATE_A_HOUSEHOLD` at the end; the notice above.
- Create: `android/ui-mobile/src/main/kotlin/ui/settings/HouseholdDialogs.kt`:
  - rename: a text field, prefilled;
  - change admin: a list, then `PinDialog` with a new PIN when needed;
  - move: a list of grown-ups;
  - create: name, then `A_NEW_GROWN_UP` or a grown-up, then a name and PIN or a PIN.
- Modify: `ProfilesSettingsSection.kt`: mount the block under the "This device" row, never for a kid.
- Tests: create `android/ui-mobile/src/test/kotlin/ui/settings/HouseholdsOwnerBlockTest.kt`:
  - `theOwnerSeesEveryHouseholdWithItsLine`;
  - `anyoneElseSeesTheOwnerButtonWhichAsksThePin`;
  - `createWithANewGrownUpSendsItsNameAndPin`;
  - `renameChangeAdminMoveAndDeleteEachSendTheHeldPin`;
  - `deleteOnlyForAnEmptyHouseholdNeverHome`;
  - `leavingTheSectionDropsTheOwnersPin`.

- [ ] **Step 1:** Write the tests. **Run** → FAIL. **Step 2: Implement. Run** → PASS; `detekt`, `lint`.
- [ ] **Step 3: Screens.** A Robolectric/Compose screenshot is not required. Install on the tablet (`ANDROID_SERIAL=caad49da` or `d54bc94f`, whichever is attached; rebuild the `.so` first) and compare by eye with the web's `reports/` screenshots. Navigate only: no household is created here (phase 07 does that).
- [ ] **Step 4: Commit** `feat(android): the owner sets up households on a phone or tablet`.

### Task 6.3: TV — Join a household, and Switch household

**Files:**
- Create: `android/ui-tv/src/main/kotlin/ui/tv/profile/TvJoinHousehold.kt`: full screen, one keyed focusable row per household. Focus lands on the first row; `TvPinPrompt` for the PIN. One screen at a time, as `TvProfileGate`.
- Modify: `android/ui-tv/src/main/kotlin/ui/tv/profile/TvProfileGate.kt`: the `needsJoin` branch first.
- Modify: `android/ui-tv/src/main/kotlin/ui/tv/settings/TvProfilesSection.kt`: under "Manage profiles", for anyone but a kid, and only with more than one household:
  - a `TvTextRow` "$THIS_DEVICE · <name>" → `TvJoinHousehold` as a panel, the way `TvProfilesPanel` opens, without growing `TvSettingsScreen.kt`;
  - after a switch that left the chosen profile, `ProfileViewModel.reopen()`.
- Tests:
  - create `android/ui-tv/src/test/kotlin/ui/tv/profile/TvJoinHouseholdStateTest.kt`: `firstRowHasFocus`, `downAndOkAskTheAdminsPin`;
  - create `android/ui-tv/src/test/kotlin/ui/tv/TvHouseholdsFlowTest.kt` (real D-pad keys, as `TvProfilesFlowTest` does):
    - `aBoxWithTwoHouseholdsAndNoneJoinedAsksWhichToJoin`;
    - `switchingFromSettingsShowsTheOtherHouseholdsPicker`;
    - `backFromTheJoinPanelReturnsToTheRow`.

- [ ] **Step 1:** Write the tests. **Run** `./gradlew :ui-tv:testDebugUnitTest --tests '*Household*'` → FAIL.
- [ ] **Step 2: Implement. Run** → PASS; then `detekt`, `lint`.
- [ ] **Step 3: Commit** `feat(android): a TV joins one household and can switch`.

### Task 6.4: TV — the owner's Households panel

**Files:**
- Create: `android/ui-tv/src/main/kotlin/ui/tv/profile/TvHouseholdsPanel.kt`:
  - full screen, from an `OWNER` row in `TvProfilesSection` (`HOUSEHOLDS` when the owner is chosen);
  - the owner's PIN pad first, unless the owner is chosen;
  - one keyed row per household with its `line`; OK on a row opens its actions as `TvTextRow`s;
  - `CREATE_A_HOUSEHOLD` last.
- Create: `android/ui-tv/src/main/kotlin/ui/tv/profile/TvHouseholdSteps.kt`: each action as steps, the way `TvManageDialogs.kt` does Daily limit:
  - rename: the TV text field;
  - change admin: a list, then `TvPinPrompt` for a new PIN when needed;
  - move: a list;
  - use here: done at once;
  - delete: done at once;
  - create: name → admin choice → name + PIN, or PIN.
- Modify: `TvProfilesSection.kt`: the row.
- Tests: create `android/ui-tv/src/test/kotlin/ui/tv/profile/TvHouseholdsPanelStateTest.kt`:
  - `theOwnerRowAsksThePinThenListsHouseholds`;
  - `okOnARowOffersOnlyWhatItAllows`;
  - `renameStepsSendTheName`;
  - `createStepsSendNameAdminAndPin`;
  - `backFromAStepReturnsToItsRow`.

  In `TvHouseholdsFlowTest.kt`: `theOwnerMovesAGrownUpWithTheRemote`.

- [ ] **Step 1:** Write the tests. **Run** → FAIL. **Step 2: Implement. Run** → PASS; `detekt`, `lint`.
- [ ] **Step 3: Commit** `feat(android): the owner sets up households on the TV`.
