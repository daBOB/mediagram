# Phase 02 — Android: Profiles section in Settings

**Plan decisions:** 1, 2, 3, 5. **Status:** pending. The web (phase 01) is the reference: read `settings-page.js`'s Profiles tab before building this.

**Context:**
- Settings sections: `ui-common/.../settings/SettingsSection` (TELEGRAM, STORAGE, APPEARANCE, PROFILE, SYSTEM); phone/tablet `ui-mobile/.../ui/settings/SettingsScreen.kt` (index with a status line per section, `SettingsPage.kt`); TV `ui-tv/.../ui/tv/settings/TvSettingsScreen.kt`.
- Manage profiles: `feature/catalog/.../profile/ManageProfilesViewModel.kt`, `ManageUiState.kt`; phone `ui-mobile/.../ui/profile/ManageProfilesScreen.kt` (opened from `ProfileGate.kt`), TV `ui-tv/.../ui/tv/profile/TvManageProfiles.kt` (from `TvProfileGate.kt`); `ui-common/.../profile/ManageActions.kt`, `ForgetManageWhenAway.kt` (drops the held PIN when the screen goes away).

### Task 2.1: Phone and tablet

**Files:** add `PROFILES` to `SettingsSection` after `PROFILE`; `SettingsScreen.kt` shows it only while the chosen profile is a grown-up, with the index status `"<n> profiles"`; its page draws the web's Everyone list (same words) above the existing Manage profiles content, inline, driven by the same `ManageProfilesViewModel`. Leaving the section drops the held PIN (`ForgetManageWhenAway`). Keep `SettingsScreen.kt` from growing past its size: the section's page goes in a new file.
**Tests (Robolectric Compose):** `aGrownUpSeesProfilesInSettings`; `aKidSeesNoProfilesSection`; `everyoneIsListedWithTheirRole`; `leavingTheSectionForgetsThePin`.
**Commit** `feat(android): a Profiles section in Settings on phone and tablet`.

### Task 2.2: TV

**Files:** the same section in `TvSettingsScreen.kt` (a panel of its own, focus rules per memory `tv-compose-focus-gotchas`), reusing `TvManageProfiles`' parts.
**Tests:** `aGrownUpSeesProfilesInTvSettings`; `theRemoteReachesManageFromTheSection`; `aKidSeesNoProfilesSection`.
**Commit** `feat(android): a Profiles section in TV Settings`.
