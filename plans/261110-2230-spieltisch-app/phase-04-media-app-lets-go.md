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
- Status: pending.
- What it does:
  - The media app's Games row opens Spieltisch.
  - Its game code, the game client, the avatars and the `mediagram-game` dependency leave.
  - Its APK and its core library get smaller.
  - A game change never touches it again.

## Requirements

- Functional:
  - The Games row launches `com.mediagram.spieltisch`. If it isn't installed, the row says
    "Spieltisch ist nicht installiert" and does nothing else.
- Kept in the media app: the provider (phase 2 and 3), `game_titles.rs`, `game_posters.rs`.
- Removed from the media app:
  - `feature/games`, `ui-tv/games`
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

- [ ] Games row → launch intent (TV + mobile) + tests
- [ ] game code, client, avatars and dependency removed; bindings regenerated
- [ ] sizes before/after
- [ ] docs + changelogs
- [ ] check.sh passes

## Success criteria

- `cargo tree -p mediagram-core` shows no `mediagram-game`.
- The media app builds without the game client.
- A change under `crates/mediagram-game` or `android/spieltisch` changes nothing in the media app's APK.

## Risk assessment

- Devices that update the media app before Spieltisch is installed lose game night until Spieltisch arrives. Install Spieltisch on the box (phase 5) before publishing this media app version.

## Next steps

Phase 5: both apps on the TV box.
