# Phase 11 — TV: Who's managing? and the calm view

Status: todo · Depends on: Phase 09, Phase 07 · Spec: `docs/superpowers/specs/2026-11-17-who-is-managing-and-calm-owner-view-design.md`

## Context (`android/ui-tv/src/main/kotlin/ui/tv/`)

- The TV picker's tiles; `settings/TvProfilesPage.kt`, `settings/TvProfilesHousehold.kt`, `profile/TvManageProfiles.kt`,
  `profile/TvManagePart.kt`, `profile/TvAddProfileFlow.kt`, `profile/TvHouseholdsPanel.kt`, `profile/TvHouseholdSteps.kt`.
- TV focus notes: `focusRestorer` hijacks; wrap pages in `focusGroup`; Robolectric lacks the TV scroll rule (emulator).

### Task 11.1

- [ ] **Step 1: Failing tests:** `managingAsksWhoWithThePickersTiles`, `ownerTileOpensTheOwnerPanel`,
  `personRowOpensTheirControlsPanel`, `readOnlyPersonRowDoesNotOpen`, `addSomeoneAsksGrownUpOrKidFirst`,
  `addSomeoneUnderAHouseholdSendsThatHousehold` (read back from the fake core), `backFromAPersonReturnsToTheirRow`.
- [ ] **Step 2:** run → FAIL. **Step 3: Implement.** **Step 4:** `./gradlew :ui-tv:testDebugUnitTest :app:assembleDebug` → PASS.
- [ ] **Step 5:** Google TV emulator only (`ANDROID_SERIAL=emulator-5554`), navigate only; never the real box.
- [ ] **Step 6: Commit** — `feat(tv): who's managing and calm rows on the TV`
