# Phase 1: Spieltisch app on its own

## Context links

- Plan: [plan.md](plan.md)
- Template app: `android/controller` (`com.mediagram.controller`), built with the same convention plugins
- Code today: `crates/mediagram-core/src/api/game_night.rs`, `android/feature/games/`, `android/ui-tv/src/main/kotlin/ui/tv/games/`

## Overview

- Priority: first; everything else builds on it.
- Status: done 2026-11-10. The game UI was copied, not moved: the media app stays unchanged until phase 4.
- What it does: a second TV app, Spieltisch, runs a game night with the general-knowledge
  games on its own. It shows no films yet and is always kids-locked until phase 2. The
  media app is untouched and keeps its own games.

## Key insights

- The game host only needs `start_game_night(titles, kids_locked, port, textless, log)`.
  With no titles, the lobby already blocks the games that need films.
- The shelf is a hand copy of `crates/mediagram-game/src/catalogue.rs` (`TvGamesShelf.kt:237-250`).
  Spieltisch takes the list from the host instead, through a uniffi `game_catalogue()`.
  The art stays keyed by game kind.
- The game client is embedded in whichever `.so` links `mediagram-game` (rust-embed, `client/dist`).
  In Spieltisch's library it is in the right place.

## Requirements

- Functional:
  - The launcher shows a Spieltisch tile.
  - The app opens on the shelf (every game in the catalogue, art where it exists, the name where not).
  - A poster starts that game's page.
  - Back behaves as today: the page claims it during a game; in the lobby, Back returns to the shelf; on the shelf, Back leaves and stops the host.
  - The night is announced over NSD (the controller app finds it), and the avatars are served from its assets.
- Non-functional: the media app's behaviour and tests stay unchanged in this phase.

## Architecture

- `crates/spieltisch-core` (new; cdylib, uniffi):
  - `start_night(SpieltischConfig) -> NightUrls`, `night_back() -> bool`, `stop_night()`, `game_catalogue() -> Vec<GameEntry>`.
  - Depends on `mediagram-game` only, not `mediagram-core`. Its own version.
- `android/spieltisch` (new application module, `com.mediagram.spieltisch`, label "Spieltisch"):
  - Its own `core-rust` source set or module for the `.so` and the generated bindings.
  - It moves (`git mv`) `feature/games` (view model, announcer, config) and `ui-tv/games`
    (shelf, frame, WebView, Back, `AvatarAssetClient`), plus the game art drawables.
  - The media app keeps working copies until phase 4. Code it keeps is copied there and
    marked by its module, not moved.
  - Helpers the shelf uses from ui-tv (`PosterAspectRatio`, `StripGap`, `stripPlateWidth`,
    `TvFocus`, `ArtTileScrim`): take them from shared modules where they already are, and
    otherwise move them to `core/designsystem` rather than copy them.
- Scripts:
  - `scripts/build-spieltisch-core.sh`: client build, then cargo-ndk for the four ABIs.
  - `scripts/generate-spieltisch-bindings.sh`.
  - Avatars go into `android/spieltisch/src/main/assets/game-avatars/`.
- Version line:
  - Spieltisch starts at 0.175.0, above the 174000 the controller is installed at, so no device sees a downgrade.
    It is carried in step by `crates/mediagram-game`, `crates/spieltisch-core`,
    `crates/mediagram-game/client/package.json` and `android/spieltisch` `versionName`.
  - `android/controller` reads its version from `crates/spieltisch-core/Cargo.toml`.
  - CLAUDE.md § Versioning is already updated (approved 2026-11-10).
  - The media line moves to 0.174.1 only if this phase changes media-app code (e.g. helpers moved to `core/designsystem`).

## Related code files

- Create:
  - `crates/spieltisch-core/{Cargo.toml,src/lib.rs,src/night.rs,…}`
  - `android/spieltisch/**`
  - `scripts/build-spieltisch-core.sh`, `scripts/generate-spieltisch-bindings.sh`
- Modify:
  - Workspace `Cargo.toml` (member; `mediagram-game` leaves the workspace version)
  - `crates/mediagram-game/Cargo.toml`
  - `android/settings.gradle.kts`
  - `scripts/check.sh` (Spieltisch's tests and lint)
  - `CLAUDE.md` § Versioning (after sign-off)
  - `docs/system-architecture.md`, `docs/project-changelog.md`
- Delete: nothing yet (phase 4).

## Implementation steps

1. Write the crate: uniffi scaffolding like `mediagram-core`'s. Move the night state that `Core` holds today into a `Night` holder.
2. Add `game_catalogue()`. Test it: it returns every `CATALOGUE` entry in lobby order.
3. Write the build and bindings scripts, and build for the four ABIs.
4. Create the Android module from the controller's template, with the TV manifest (leanback launcher, banner).
5. Move the games UI and view model. Swap `CoreProvider` and `WatchStateRepository` for a `SpieltischCore` facade. Leave `kidsLocked = true` and the titles empty until phase 2.
6. Build the shelf from `game_catalogue()`, with art by kind. Port `TvGamesRailTest` as a Spieltisch test (focus then press; Back returns to the focused poster).
7. Version line: Spieltisch's manifests to 0.175.0, and the controller reads `crates/spieltisch-core/Cargo.toml`. CLAUDE.md is already updated.
8. Run `scripts/check.sh`. Install the debug build on an emulator and play one quiz with two browser phones; take screenshots.

## Todo

- [x] spieltisch-core crate + `game_catalogue` test
- [x] build and bindings scripts
- [x] `android/spieltisch` module, TV manifest, launcher tile
- [x] games UI and view model copied (media app untouched until phase 4); facade in place
- [x] shelf from the catalogue; rail test ported
- [x] CLAUDE.md § Versioning (approved and written 2026-11-10)
- [x] Spieltisch manifests at 0.175.0; controller reads spieltisch-core
- [x] check.sh passes; emulator night with screenshots in `reports/`

## Success criteria

- Spieltisch installs beside the media app and runs a quiz night on its own.
- Adding a game to `catalogue.rs` shows it on Spieltisch's shelf with no Kotlin change.
- `scripts/check.sh` passes; the media app's tests are unchanged.

## Risk assessment

- Two `.so` files each carry tokio and axum: Spieltisch's size goes up. Measure the APK; it is still far smaller than the media app.
- Port 8080 clash if both apps host at once: the media app's games stop being reachable from its UI in phase 4. Until then, only one of the two may run a night (both pick a free port; `preferred_port` falls back already).

## Security considerations

- Spieltisch alone is always kids-locked in this phase, so no film content leaks before phase 2's lock arrives.
- The cleartext network config allows only loopback, as in the media app.

## Next steps

Phase 2 gives Spieltisch the library's titles and the profile's kids lock.

## Result (2026-11-10)

- Spieltisch release APK: 75 MB (avatars 42 MB, two `.so` 29 MB). `libspieltisch_core.so`: about 17 MB per ABI, 12 MB of it the embedded game client.
- The shelf follows the catalogue's lobby order, so Mau-Mau, Schwimmen and Durak now come after Flüchtig.
- After review, also built:
  - one `NightSession` per process (announcer, stops, start/stop order)
  - `ACCESS_LOCAL_NETWORK` asked for on API 37
  - the activity handles configuration changes itself
  - the page refuses non-loopback navigation
- Reports: `reports/code-reviewer-261110-2320-spieltisch-app-phase-1-review.md` and the `spieltisch-*.png` emulator screenshots.
- Still open: nothing fills Spieltisch's avatar folder automatically (phase 3's release script). The multicast permission is left out and should be confirmed on the box (phase 5).
