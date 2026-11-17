# Phase 10 — Tablet/phone: Who's managing? and the calm view

Status: todo · Depends on: Phase 09 (the web decides; match it) · Spec: `docs/superpowers/specs/2026-11-17-who-is-managing-and-calm-owner-view-design.md`

## Context (`android/ui-mobile/src/main/kotlin/ui/`)

- Picker tiles: the tablet's *Who's watching?* (`ui/profile/` picker composables); reuse them.
- Profiles page `settings/ProfilesPage.kt`, gate `settings/HouseholdsOwnerBlock.kt`, owner view
  `profile/ManageOwnerPart.kt`, `profile/OwnerAdds.kt`, `settings/OwnerHouseholds.kt`, `profile/ProfileForms.kt`.
- State (`feature/catalog/.../profile/`): `ManagePart` (`people`, `readOnly`, `kidParents`, `kidParentDefault`,
  `household`), `Managing.ownersView`, `ManageActions`. Change state only if the web's decisions need it; keep `:ui-tv` green.

### Task 10.1

- [ ] **Step 1: Failing tests** (Robolectric, new `ui-mobile/src/test/kotlin/ui/settings/CalmOwnerViewTest.kt` and
  `WhoIsManagingTest.kt`), the web's names in camelCase: `managingAsksWhoWithThePickersTiles`,
  `ownersTileSaysOwnerAndOpensTheOwnerView`, `anotherHouseholdsDeviceEndsWithAnOwnerTile`,
  `aPersonIsOneRowUntilTapped`, `tappingAPersonOpensTheirControlsAndClosesTheOther`,
  `householdMenuHoldsTheAllowedActions`, `addSomeoneAsksGrownUpOrKid`, `aRefusedAddKeepsTheOpenForm`,
  `addSomeoneUnderMadaniSendsMadani` (household id read back from the fake core).
- [ ] **Step 2:** run → FAIL. **Step 3: Implement**; update older tests pinned to the dense layout (keep their cases).
- [ ] **Step 4:** `cd android && ./gradlew :ui-mobile:testDebugUnitTest :feature:catalog:testDebugUnitTest :ui-tv:testDebugUnitTest :app:assembleDebug` → PASS.
- [ ] **Step 5:** Tablet walk only if `adb devices` lists caad49da (navigate only, check the avatar first); otherwise say it is owed.
- [ ] **Step 6: Commit** — `feat(android): who's managing and calm rows on the tablet`
