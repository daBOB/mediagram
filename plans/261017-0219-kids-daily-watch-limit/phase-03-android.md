# Phase 3 — Android (phone, tablet, TV)

## Context
- Requests: `android/core/model/src/main/kotlin/ProfileRequests.kt` (`SetKidsAge` template), `CoreProfileCalls.kt`, `WatchSnapshot.kt` (`model.Profile`).
- Manage: `feature/catalog/.../profile/ManageProfilesViewModel.kt`, `ui-common/.../profile/ManageActions.kt`, `ui-mobile/.../profile/ManageProfilesScreen.kt`, `ui-tv/.../profile/TvManageProfiles.kt`.
- Player (shared by all form factors): `PlayerViewModelOpen.kt:30-66` (gate), `PlayerSession.kt:66-74` (10 s tick), up-next/replace `LibraryPositions.kt:195`, `PlayerViewModelTogether.kt:63-66` (precedent per-profile check).

## Overview
Priority high, status pending (after phase 1). Match the web's decisions and words exactly (Surface Parity).

## Requirements
- `SetDailyLimit` request + outcome; `Profile.dailyLimit`.
- Manage profiles, phone/tablet and TV: the same choices as the web, beside the age limit.
- Gate in `PlayerViewModel.open` (covers navigation, Next episode, together switch): `out` → the player does not open the title; the same notice as the web.
- Tick: re-read watched-today from the core (local SQLite, cheap) every 10 s tick; warning once per title on entering `warn`; phone/tablet and TV each show it in their own player chrome.
- `out` during play: the title finishes; the up-next countdown/autoplay is refused with the notice.

## Steps
1. Model + data layer + uniffi bindings (regenerate; rebuild the native core — stale `.so` crashes at launch).
2. ViewModel gate + tick + tests (Robolectric/unit, fake clock and watched seconds).
3. Manage UI on both form factors.
4. Player notice/warning UI on both form factors.

## Todo
- [ ] model/data/FFI
- [ ] gate + tick + tests
- [ ] Manage UI (mobile, TV)
- [ ] player UI (mobile, TV)
- [ ] `./gradlew testDebugUnitTest lint`

## Success criteria
Unit tests cover free/ok/warn/out at open and mid-play; up-next refused at `out`; Manage sets a limit that the web then shows after a sync round.

## Risks
- TV focus: the notice must not steal or strand focus (see TV focus gotchas memory); log focus on the box if anything moves.
- Background sync only every 5 min — accepted lag.
