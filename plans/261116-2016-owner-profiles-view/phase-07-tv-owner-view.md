# Phase 07 — TV: the owner view with the remote

Status: todo · Depends on: Phase 05 · Parallel with: Phase 06

## Context (`android/ui-tv/src/main/kotlin/ui/tv/`)

- `settings/TvProfilesPage.kt` (198, full): You → *Everyone here* → *Manage profiles* row →
  *This device* row → owner note / *Households*·*Owner* row; each row opens a full-screen panel;
  one `FocusRequester` per row, `returningFrom` lands back on the opening row.
- `profile/TvManageProfiles.kt` (182): `TvWhoAreYou` / `TvManaging`; `TvManageStep` List/Adding/…;
  `TvManagePart.kt` (49) draws a part; `TvAddProfileFlow.kt` (130): name → FSK → daily limit.
- `profile/TvHouseholdsPanel.kt` (111) + `TvHouseholdSteps.kt` (141): household step screens
  (`Actions`, `Rename`, `ChangeAdmin`, `Move`, `CreateName`, `CreateAdmin`, `CreateGrownUp`), `back()`/`done()`.
  Also opened from `TvSettingsPanel.kt:57` and `TvProfilesOverPicker.kt:49`.
- Tests: `ui-tv/src/test/kotlin/ui/tv/` (`TvProfilesPageTest`, `TvHouseholdsFlowTest`, `TvOwnersPartOverPickerTest`)
  and `.../profile/` (`TvManageProfilesStateTest`, `TvManageHouseholdsStateTest`, `TvHouseholdsPanelStateTest`,
  `TvHouseholdsPanelAnswersTest`). Run `./gradlew :ui-tv:testDebugUnitTest`.

## Behaviour — the web's structure, the TV's panels

- For the owner, the page has one *Manage profiles* row (no *Everyone here*, no *Households* row);
  its panel is the owner view: per household a header row (name · admin line) whose OK opens the
  existing household `Actions` step screen, then its people, then *Add a grown-up* / *Add a kid*
  rows for that household, then *Create a household* at the end (existing `CreateName` steps).
- *Add a kid* in a household with more than one grown-up adds a *Belongs to* step before the name
  (a `TvChoicesScreen`), default focus on the admin.
- Non-owner pages keep the *Owner* row; after the PIN it opens the same owner view panel.
- Focus: Back steps out one level as today; after an add or a household change, focus returns to
  that household's header row. Follow the TV focus notes (`focusRestorer` hijacks; wrap pages in `focusGroup`).
- `TvProfilesPage.kt` is full: move `Household(...)` into `settings/TvProfilesHousehold.kt` first.

### Task 7.1

- [ ] **Step 1:** Move `Household(...)` out of `TvProfilesPage.kt`; `:ui-tv:testDebugUnitTest` → PASS.
- [ ] **Step 2: Failing tests:** `ownerPageHasOneManageRowAndNoHouseholdsRow`,
  `ownerViewListsEveryHouseholdWithHeaderPeopleAndAdds`, `addGrownUpUnderAHouseholdSendsThatHousehold`,
  `addKidWithTwoGrownUpsAsksBelongsToFirst`, `backFromAHouseholdsActionsReturnsToItsHeader`,
  `ownerRowOnAnotherHouseholdsDeviceOpensTheOwnerView`.
- [ ] **Step 3: Implement.** **Step 4:** `./gradlew :ui-tv:testDebugUnitTest` → PASS.
- [ ] **Step 5:** Check focus on the Google TV emulator (Robolectric lacks the TV scroll rule), then the
  real TV box (find it with nmap on port 5555 if its address moved). Navigate only — never press OK on a
  setting or a create. Pin installs to a serial.
- [ ] **Step 6: Commit** — `feat(tv): the owner manages every household from one panel`
