# Phase 4: The media app lets go

## Context links

- Plan: [plan.md](plan.md)
- Games entry points in the media app:
  - `ui-common/.../RailItem.kt:20-21`, `LibraryPositions.kt:28,292`
  - `ui-tv/.../TvLibrary.kt:47,227,293`, `TvLibraryBranches.kt:139-141`
  - `catalog/TvCatalogScreen.kt:99,164`, `TvCatalogNav.kt:42,108`, `chrome/TvLibraryRail.kt:61`
  - `ui-mobile/.../LibraryFlowBranches.kt:228`, `chrome/RailSelect.kt:28`

## Overview

- Priority: fourth. Only after phases 1–3 work on an emulator.
- Status: done (check.sh result below).
- What it does:
  - The media app's Games row opens Spieltisch.
  - Its game code, the game client, the avatars and the `mediagram-game` dependency leave.
  - Its APK and its core library get smaller.
  - A game change never touches it again.

## Requirements

- Functional:
  - The Games row launches `com.mediagram.spieltisch`. If it isn't installed, the row says
    "Spieltisch ist nicht installiert" and does nothing else.
- Kept in the media app (`feature/games` keeps `GameNightConfig`, `GameNightProvider`, `GameNightExport`, `NightInputs`, `NightSnapshotStore`, `SpieltischReleaseExport` and `GameNightModule`; only its UI, view model and announcer left): the provider (phase 2 and 3), `game_titles.rs`, `game_posters.rs`.
- Removed from the media app:
  - `feature/games`' UI, view model and announcer, and `ui-tv/games`
  - `start_game_night`, `game_night_back`, `stop_game_night`
  - `Core.game_night`, `mediagram-core`'s dependency on `mediagram-game`
  - avatars, `noCompress glb`, the client build in `build-android-core.sh`
  - game fakes in `FakeCore`, `TvAppFixture`'s game parts, `TvGamesRailTest`
- Network permissions: keep each one only if something else in the media app still uses it (LAN cache, NSD). Check each.

## Implementation steps

1. Replace the Games row's frame with a launch intent and the not-installed message, on TV and mobile. Rail tests cover both.
2. Remove the game code and tests. Regenerate the media core's bindings. Rebuild the four ABIs.
3. Measure the media app's APK and `.so` sizes before and after; write them in the changelog.
4. Docs:
   - `docs/system-architecture.md`: the game night section and the crate tree.
   - `docs/mlib-spec.md` if needed.
   - The changelog entries for both version lines.
5. Run `scripts/check.sh`.

## Todo

- [x] Games row → launch intent (TV only; the phone's rail never lists Games, and Spieltisch is leanback-only, so a phone row could only say "not installed") + tests
- [x] game code, client, avatars and dependency removed; bindings regenerated
- [x] sizes before/after
- [x] docs + changelogs
- [x] check.sh passes

## Success criteria

- `cargo tree -p mediagram-core` shows no `mediagram-game`.
- The media app builds without the game client.
- A change under `crates/mediagram-game` or `android/spieltisch` changes nothing in the media app's APK.

## Risk assessment

- Devices that update the media app before Spieltisch is installed lose game night until Spieltisch arrives. Install Spieltisch on the box (phase 5) before publishing this media app version.

## Next steps

Phase 5: both apps on the TV box.

## Result

- Games row (TV): launches `com.mediagram.spieltisch` (leanback intent, else the normal one); not installed → "Spieltisch ist nicht installiert", nothing else. `TvGamesRailTest` covers leanback, fallback and missing. Manifest has `<queries>` for the package.
- Phone: no Games row, deliberately (the rail marks it `tvOnly`; Spieltisch requires leanback, so a phone row could only ever say "not installed"). `phoneRailHasNoGames` stays. Say so if you want a phone row anyway.
- `cargo tree -p mediagram-core`: no `mediagram-game`. `game_posters.rs` has its own `PosterLookup` type; the titles' fixture test compares JSON values.
- Release APK (signed on this machine), before -> after: 64,271,399 -> 34,825,001 bytes. `libmediagram_core.so` on disk (unstripped, per ABI): arm64-v8a 39,153,888 -> 23,785,000; armeabi-v7a 31,802,224 -> 17,356,552; x86 39,250,672 -> 23,606,140; x86_64 38,677,088 -> 23,235,488. In the APK (stripped): arm64 30,345,696 -> 16,185,008; armv7 24,750,660 -> 11,272,188. The avatars were not in this checkout, so the APK drop is the core (the embedded game client) only; a release with the 40 MB of avatars shrinks by that much more.
- versionCodes (app, controller, Spieltisch) 175000 before and after; `versionCodeOf` lives in `android/build-logic/convention/.../VersionCode.kt`.
- Permissions kept: `INTERNET`, `ACCESS_LOCAL_NETWORK`, `CHANGE_WIFI_MULTICAST_STATE` (LAN cache discovery), cleartext config (LAN cache server). None removed.
- Emulator (`reports/letgo-emulator.log`, `letgo-*.png`): the media app on the emulator has no Telegram session, so its rail could not be reached; the Games row is proven by Robolectric only. On the emulator: Spieltisch's leanback launcher resolves while installed and not after `adb uninstall`; the media app carries the `queries` entry; Spieltisch's night reads the new media app's provider (answered, locked, no library chosen).
- `scripts/check.sh` passes in full (`reports/letgo-check.log`). `LibraryUpdateCoordinatorTest` (known flake) failed in two full runs under load, passed alone, then the third full run passed (`reports/letgo-check-run*-flaky-coordinator-test.log`).
