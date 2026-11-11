# Phase 4 review: the media app lets go

Scope: uncommitted work on `feat/spieltisch-app` on top of f256d92c (41 files, +131 / -1548), plus the new
`VersionCode.kt`, `SpieltischLauncher.kt` and `TvGamesRailTest.kt`. Spec: `phase-04-media-app-lets-go.md`.

**Verdict: not ready to commit yet.** H1 needs about two lines. M1 and M2 are small and belong in the same commit.

## High

### H1. Old avatars in existing checkouts go into the media app's release APK, and git stops ignoring them
- `.gitignore:22` (line removed) and `scripts/release-android.sh:21` (the avatar step was removed and nothing replaces it).
- Checked on disk: `/home/andre/Workspace/mediagram/android/app/src/main/assets/game-avatars` holds 22 `.glb` files, 41 MB.
  It is the `main` checkout, and its releases ran `release-android.sh`. `mediagram-funstation` and `mediagram-games`
  hold the same files. Today `.gitignore:22` ignores them (`git check-ignore` confirms it).
- What goes wrong after the merge to main:
  1. `release-android.sh` runs `:app:assembleRelease`. AGP packages all of `src/main/assets`, and `isShrinkResources`
     does not touch assets. The media app release grows by about 40 MB instead of shrinking, and now compressed too,
     because `noCompress glb` is gone. The changelog's 34.8 MB holds only for a checkout that never had avatars.
     Nothing reads the files any more, so nobody would notice.
  2. `git status` shows 22 untracked `.glb` files. One `git add -A` puts 41 MB of generated binaries into history.
- Fix: keep the ignore line, and in `release-android.sh` before the build:
  ```sh
  # Releases from before the games moved to Spieltisch generated these here; the media app must not ship them.
  rm -rf android/app/src/main/assets/game-avatars
  ```
  You could make the script refuse to build instead of deleting. Either way, delete the folder by hand in the three
  checkouts.

## Medium

### M1. Dead game art is still in `ui-tv`
- `android/ui-tv/src/main/res/drawable-nodpi/ui_tv_game_{poster,backdrop}_{quiz,poster,mau_mau,schwimmen,durak}.webp`:
  10 tracked files, 1.8 MB.
- The only user was the deleted `TvGamesShelf.kt`. Spieltisch has its own copies
  (`android/spieltisch/src/main/res/drawable-nodpi/game_*`).
- Lint already reports 10 `UnusedResources` warnings for them (`android/app/build/reports/lint-results-debug.xml`,
  from the check run).
- R8 strips them from the release APK, but the debug APK and the repo still carry them.
- Fix: `git rm` the 10 files.

### M2. Games pressed over a page or from the avatar menu leaves the viewer's page first
- Where: `TvLibrary.kt:221-225` (avatar menu) and `:255-259` (`TvFrameChrome.onRail`). Both send `RailItem.GAMES`
  through `runRail` (`:170-175`), which runs `restore.forget(0)`, sets `pendingRail = GAMES` and calls `at.toCatalog()`.
- Example: a viewer is on a title page and presses Games on the rail.
  - The stack is popped to Home, and then Spieltisch launches.
  - Back from Spieltisch lands on Home, not on the title.
  - When Spieltisch is missing, the viewer loses the page just to read "Spieltisch ist nicht installiert".
  - The spec says it "does nothing else".
- A second problem: `pendingRail` waits for a Ready catalogue that has content (`TvCatalogScreen.kt:178-185`). In a
  style without a rail, the avatar menu is the only way to reach Games. Pressed there while the catalogue is not
  Ready, it does nothing: no launch and no notice.
- HEAD routed Games the same way. That was right when the games page sat on the catalogue's stack. It is wrong for a
  separate app.
- Fix: handle Games before `runRail` in both `when`s:
  ```kotlin
  RailItem.GAMES -> { menuOpen = false; openGames() }   // menu
  RailItem.GAMES -> openGames()                          // page chrome
  ```
  Then add a `TvGamesRailTest` case that starts over a title page.

## Low

- **L1. `SpieltischLauncher.kt:24-28` catches only `ActivityNotFoundException`.** A `SecurityException` from
  `startActivity` would crash the media app from its rail. That could happen if a later Spieltisch makes its launcher
  activity non-exported, or on boxes with OEM app restrictions. Use `catch (_: RuntimeException)`, or add
  `SecurityException` to the catch.
- **L2. Stale comments in code:**
  - `game_posters.rs:21-22`: "shared by a night hosted here".
  - `game_posters.rs:56-57`: "the prefetch and the image route" were the in-app host. Today's concurrent callers are
    the provider's `/textless` calls.
  - `api/game_night_export.rs:19`: "as for a night hosted here".
  - `LibraryPositions.kt:20`: the KDoc still lists `[GAMES]`, which now links to nothing.
- **L3. Stale docs:**
  - `system-architecture.md:1004-1005` says a missing profile is "treated as locked at 12". That was the deleted view
    model; the provider now answers `ExportedNight.Locked` with no titles.
  - `system-architecture.md:1306-1307`: "the media app's own night passes none and reads its files directly".
  - `project-changelog.md`, 0.175.0 intro: "Nothing changes for a viewer yet." is now false.
  - `project-changelog.md`, the "Changed" entry: "nights hosted in the media app use the same builder".
  - `project-changelog.md`, Spieltisch section: "The media app's own night passes none and is unchanged."
  - `project-changelog.md`: "The media app can see Spieltisch's package, to open it later" was not true until now.
    HEAD had no `<queries>` (`git log -S` finds no earlier entry). The new Removed entry repeats it.
  - The module table (`system-architecture.md:376`) leaves out `feature:games`. This gap predates the phase.
- **L4. `TvLibrary.kt` grew from 311 to 325 lines**; it was already over 200. If wanted, the notice state and its
  effect could move next to `openSpieltisch` as a small `remember…` helper.
- **L5. Focus after process death.** The `rail:games` restore key is gone. If a low-memory box kills the media app
  while Spieltisch runs (WebGL plus avatars), Back recreates the library on the catalogue's default stop, not on
  Games. Acceptable; noted only.
- **L6. `check.sh` never compares the media core's committed bindings with the core.** This predates the phase, and
  `generate-android-bindings.sh` mentions a "CI" that does not exist. I checked the bindings by hand (below).
- **L7. Nit:** `game_titles_tests.rs` now starts with an empty line, left from the removed import.
- **Note only, needs the user's sign-off:** CLAUDE.md § Versioning still says `android/app/build.gradle.kts` derives
  the versionCode.

## What I checked and found correct

- **Provider down to the core:**
  - `/night` → `GameNightExport` → `nightInputs`, which calls `chosenProfile`, `profiles`, `snapshot` and
    `catalogFacts`, then `listSets` and `gameNightTitles(NightTitlesConfig)`.
  - `/image` and `/textless` → `textlessPoster(key, tmdb)`.
  - `/release` → `latestAppRelease(handle, SPIELTISCH)`.
  - `/release.apk` → `downloadAppRelease`.
  - Each one exists in Rust (`api/game_night_export.rs`, `api/mod.rs:155`, `api/channel/app_release.rs:72,84`) and in
    `mediagram_core.kt`, with its checksum.
  - `gameNightConfig` now returns `NightTitlesConfig` directly, and the export passes it straight through.
- **Bindings:** I regenerated them from the committed arm64 `jniLibs` `.so` into the scratchpad. After the script's
  whitespace strip they match the committed file exactly. None of the four ABI libraries contains `start_game_night`
  or `mediagram_game`. The bindings diff only removes code.
- **cargo tree:** `cargo tree -p mediagram-core --offline` shows no `mediagram-game`.
  `cargo tree -i mediagram-game` lists only `spieltisch-core`.
  The only feature on `mediagram-core` is `cli`; the game night used no feature or module of its own beyond the
  deleted `api/game_night.rs`.
- **DI:**
  - `app` → `:feature:games` (`app/build.gradle.kts:104`). `ui-tv` no longer depends on it.
  - `GameNightModule` is now an `object` that provides `NightSnapshotStore`. The `@EntryPoint` is unchanged.
  - The Hilt graph compiled in the check run, and `letgo-emulator.log` shows the new media app's provider answering.
- **Leftovers:** a grep of the media app's code, scripts, Gradle files, proguard rules, manifests and tests finds no
  `start/stop_game_night`, `game_night_back`, `GameNightUrls`, Rust `GameNightConfig`, `FrameKind.GAMES`,
  `openGames()`, `ui.tv.games`, announcer, `glb` or `noCompress`. The only leftovers are the drawables in M1 and the
  comments in L2.
- **Restoring an old stack:** the `FrameKind` decoder works by name and drops kinds it does not know
  (`LibraryPositions.kt:61-70`), so a stack saved by an older build with `GAMES` in it cannot crash.
  `rail:games` now maps to `null`.
- **Games row on Home:**
  - It tries the leanback launch intent first, then the normal one, and starts the activity with `NEW_TASK`.
  - When Spieltisch is missing, the notice shows for 3 s with no focus, and the viewer stays on the shelves.
  - The manifest declares `<queries>` for the package.
  - Back from Spieltisch: its `leave = ::finish` ends its task, so Android returns to the media app's task. The kept
    composition still has focus on the Games row.
- **`versionCodeOf`:**
  - It is the media app's old function word for word. That is stricter than the old controller and Spieltisch copies:
    it also checks for three parts and gives a clear error for a non-number.
  - All three apps import `convention.versionCodeOf`.
  - The app's merged manifest says `175000`, built at 05:35, after `VersionCode.kt` (05:29). Spieltisch and the
    controller feed it the same `0.175.0`, so they get 175000 too.
- **Scripts:**
  - `release-android.sh` still builds the core for all four ABIs, then `assembleRelease`, then checks the certificate
    and the dex metadata, then publishes.
  - `build-android-core.sh` no longer needs bun.
  - `check.sh` is unchanged and still covers Spieltisch: clippy and `cargo test --all` (including `spieltisch-core`
    and `mediagram-game`), the protocol types, Spieltisch's bindings, and the game client's typecheck and tests.
- **Permissions:** all of them are still used.
  - `ACCESS_LOCAL_NETWORK` and `CHANGE_WIFI_MULTICAST_STATE`: `LanServerLocator`, `LanCacheViewModel`.
  - Cleartext: the LAN cache (`LanCacheInput.kt:18` adds `http://`).
  - The network config's comment no longer mentions the game page.
- **Media app self-update:** nothing under `android/core/update` changed, so its update path is untouched.
- **Code standards:** no plan references in the new code; the new comments explain why.
- **Tests I ran:**
  - `cargo test -p mediagram-core --lib game_`: 19 passed.
  - Gradle: `:ui-tv` `TvGamesRailTest` 3 of 3, `:feature:games` 38 of 38.

## Unresolved questions

- Does the release machine's checkout (`/home/andre/Workspace/mediagram`) keep its old avatars until this merges?
  If so, H1 has to be fixed before the next media app release, not just before this commit.
