# Code review: Spieltisch app, phase 1 (uncommitted, feat/spieltisch-app vs main 2f3e7d81)

## Scope

- Tracked diff: `.gitignore`, `CLAUDE.md`, `Cargo.toml`, `Cargo.lock`, `android/controller/build.gradle.kts`, `android/settings.gradle.kts`, `crates/mediagram-game/{Cargo.toml,client/package.json,src/catalogue.rs,src/lib.rs}`, `docs/*`, `scripts/check.sh`
- New: `crates/spieltisch-core/**`, `android/spieltisch/**`, `scripts/build-spieltisch-core.sh`, `scripts/generate-spieltisch-bindings.sh`
- About 1,100 lines of hand-written code (Rust 230, Kotlin 650 main + 400 test, scripts and Gradle 150), plus 1,366 lines of generated bindings
- Spec: `phase-01-spieltisch-app-on-its-own.md`; the brief overrides it on purpose: the UI is copied rather than moved, and the media app is untouched

## What I ran

| Check | Result |
|---|---|
| `cargo test -p spieltisch-core -p mediagram-game` | 415 + 5 passed |
| `cargo clippy -p spieltisch-core -p mediagram-game --all-targets --all-features -D warnings` | clean |
| `./gradlew :spieltisch:testDebugUnitTest` | 15/15 passed (9 view model, 6 frame) |
| `./gradlew :spieltisch:lint :controller:lint` | clean |
| check.sh's bindings step run on its own (host `.so` → bindgen → strip → diff) | **matches** the committed, arm64-generated `spieltisch_core.kt` |
| `--dry-run` task graphs | `testDebugUnitTest` and `lint` never reach `verifyNativeCore`; `assembleRelease` does |

## Overall

The work is careful and close to the media app. The Rust night is a faithful port of `game_night.rs`. The kids lock fails closed. The bindings check works. The media app has no behaviour change. No critical issues. There are two real Medium bugs, one in the NSD lifecycle and one in API 37 permissions, plus one test that passes without testing its claim. The rest is low or nit.

## Verified facts (the questions in the brief)

- **The media app is unchanged.** Nothing under `android/app`, `android/ui-tv`, `android/feature` or `crates/mediagram-core` is in the diff. The media `.so` still links `mediagram-game`, which is now at version 0.175.0. No code reads that version: grep found no `CARGO_PKG_VERSION` in mediagram-game or the client, and the client never reads `package.json`'s version. `bun.lock` stores no root version, so `--frozen-lockfile` still passes.
- **mediagram-game changes:** `CATALOGUE` went from `pub(crate)` to `pub` (`catalogue.rs:26`), it is re-exported (`lib.rs:83`), and the version is now explicit (`Cargo.toml:4-5`). Nothing else changed.
- **The kids lock is always on and fails closed.** The only `startNight` caller hard-codes `kidsLocked = true` (`GameNightViewModel.kt:54`, pinned by `startIsKidsLockedAndShowsTheTvPage`). Rust enforces it on every `configure`: `kids_only || kids_locked` and `adult && !kids_only` (`mediagram-game/src/room_settings.rs:51-53`). The TV page's `?game=` configure cannot unlock it either. The uniffi record still takes a bool, which is right for phase 2.
- **versionCode:** Spieltisch and the controller both get 0.175.0 → 175000, above the controller's 174000. Release ABIs are arm64-v8a and armeabi-v7a, the same as the media app.
- **Bindings check:** it is deterministic. uniffi metadata does not depend on the architecture, and a host build gave byte-identical output. It catches any change to the Rust surface.
- **Avatar path handling is safe.** `Uri.getPath()` is already decoded, so encoded `..` or `/` is caught by `contains("/")`/`contains("..")` (`TvGamesFrame.kt:136`). `AssetManager` cannot leave the APK's `assets/`. A request can only read files directly in `game-avatars/`.
- **WebView:** it has no `addJavascriptInterface`, so no bridge is exposed. Cleartext is allowed only to `127.0.0.1` (`network_security_config.xml`). targetSdk 37 means `allowFileAccess` defaults to false.
- **Files under 200 lines:** every Kotlin and Rust file passes; the largest are `TvGamesFrameTest.kt` at 189 and `TvGamesFrame.kt` at 175. The generated bindings are the only exception. No plan or phase references in code, comments or test names (grep found none).

## Medium: real bugs

### M1. The NSD announcement can outlive the night, and in Spieltisch nothing ever withdraws it

`android/spieltisch/src/main/kotlin/GameNightViewModel.kt:55,73-86`, `MainActivity.kt:22`

`stop()` calls `announcer.withdraw()` straight away on Main. The in-flight start is only cancelled later, inside `appScope.launch` on `Dispatchers.Default`. The start job resumes on Main, and if it gets there before that cancel it announces anyway:

1. `start()` is suspended in `core.startNight`. Rust finishes, and the resumption is queued on Main.
2. ON_STOP or Back calls `stop()`: `withdraw()` finds nothing registered, and `stopJob` is posted to Default.
3. Main runs the queued resumption: `announcer.announce(joinUrl)` registers NSD and the state becomes `Ready`.
4. `stopJob` runs. `cancelAndJoin` does nothing because the job has already finished, and `stopNight()` kills the host.

**Result:** "Spieltisch" stays registered with a dead URL. In the media app this heals itself, because its announcer is `@Singleton` (`feature/games/.../GameNightAnnouncer.kt`) and the next `announce()` withdraws first. Spieltisch creates a new `NsdGameNightAnnouncer` per view model, and every stop also finishes the activity, so the old registration is orphaned until the process dies. The controller lists a dead table, and the next night registers as "Spieltisch (2)". No host or port leaks: `stop_night` stops whatever is in the slot. The trigger is leaving within the host's start time, so it is rare, but the fix is cheap.

**Fix:**
```kotlin
// GameNightViewModel.stop()
stopJob = appScope.launch {
    starting?.cancelAndJoin()
    announcer.withdraw() // a start that finished after stop() was called may have announced
    core.stopNight()
}
```
Also make the announcer process-wide, as `NightScope` already is: a top-level lazily created `NsdGameNightAnnouncer(applicationContext)`. Then the next night's `announce()` cleans up anything left behind. Add a view-model test: `appScope` on a `StandardTestDispatcher` that has not run yet, `start` held at `startGate`, then `stop()`, open the gate, run Main, then run appScope, and assert the last announcer call was a withdraw.

### M2. `ACCESS_LOCAL_NETWORK` is declared but never requested

`android/spieltisch/src/main/AndroidManifest.xml:14-16`

The manifest's own comment says that on API 37+ the phones "can neither reach the host nor find its NSD announcement" without this permission. The project treats it as a runtime permission on 37: the media app prompts for it in `ui-tv/src/main/kotlin/ui/tv/settings/TvLanCacheBlock.kt:100-104` and `ui-mobile/.../LanCacheBlock.kt:65`. Spieltisch has no prompt, so on an Android 17 TV the night starts and shows its QR code, but no phone can join or discover it. Nothing reports the failure. No later phase plans this. It only affects API 37 devices, not today's armv7 box.

**Fix:** on API 37 and up, request it before or alongside `model.start()`, for example with `rememberLauncherForActivityResult(RequestPermission())` in `TvGamesFrame`, the same pattern as `rememberLocalNetworkRequest`. If it is denied, show a line on the lobby screen.

### M3. `backDuringAGameStaysOnThePage` passes whatever the core answers

`android/spieltisch/src/test/kotlin/TvGamesFrameTest.kt:141-151`

Robolectric 4.16.1's `ShadowWebView.evaluateJavascript` stores the callback and never calls it; I checked the bytecode. So Back always goes through the 400 ms `giveUp` path (`TvGamesFrame.kt:87-91`). `back()` only calls `waitForIdle()`, which does not advance a bare `delay(400)`. That is why the lobby test needs `waitUntil`. `shownUrl()` returns at once because the WebView already exists, so the assertion runs before `stepBack()`. The test passes with `backAnswer = false` too, so the "a running game keeps the page" behaviour is untested. The copy in `ui-tv/src/test/kotlin/ui/tv/TvGamesRailTest.kt:130-139` has the same flaw. The path where the page claims Back is also untested.

**Fix:** count `nightBack` calls in `FakeSpieltischCore`. After `back()`, call `compose.mainClock.advanceTimeBy(500); compose.waitForIdle()`, then assert `core.backs == 1` and that the page is unchanged. Add a test where the page claims Back: `shadowOf(webView).lastEvaluatedJavascriptCallback.onReceiveValue("\"true\"")`, then assert `core.backs == 0` and that the page stays.

## Low

### L1. The start/stop ordering only holds inside one view model, and `stop_night` is not tied to a night

`GameNightViewModel.kt:45-50`, `crates/spieltisch-core/src/night.rs:53-57,83-87`

`start()` waits only for its own view model's `stopJob`. In Spieltisch every stop finishes the activity, so a relaunch gets a new view model that cannot see the old stop. If the old `stopJob` has not reached `stop_night` before the new `start_night` takes the lock, the old stop ends the new night. The new screen then shows `Ready` over a dead host. In practice the relaunch takes hundreds of milliseconds and the old stop gets there first, so this is very unlikely. The process-wide holder from M1 fixes it too: keep `stopJob` beside `NightScope` rather than in the view model.

### L2. A configuration change closes the app in the middle of a night

`AndroidManifest.xml:30`, `TvGamesFrame.kt:57-65`

`configChanges` covers only screen size, layout and orientation. ON_STOP always runs `stop()` and `finish()`. Any other change ends the night and closes the app, for example `keyboard|keyboardHidden|navigation` when a BT keyboard or gamepad connects, `uiMode`, `locale` or `density`. The media app has the same list. **Fix:** add `keyboard|keyboardHidden|navigation|uiMode|density|fontScale|locale` (Compose copes), or skip the stop when `activity.isChangingConfigurations`.

### L3. check.sh's bindings step leaves its temp dir behind on failure

`scripts/check.sh:37-46`

`rm -rf "$bindings"` only runs on success. The stale-bindings `exit 1` and any `set -e` failure in cargo leave a `/tmp/tmp.*` directory behind. **Fix:** `trap 'rm -rf "$bindings"' EXIT` right after `mktemp`. The step also compares against the working tree rather than git, so regenerated bindings that were never committed still pass. The protocol step above it checks `git ls-files --others`; doing the same here is optional.

### L4. Nothing puts Spieltisch's avatars in place, and the doc comment points at a tool that writes elsewhere

`TvGamesFrame.kt:125-130`

The comment says the folder is generated by `make-ceremony-avatars.mjs`. That tool writes to `android/app/src/main/assets/game-avatars/` by default (`crates/mediagram-game/tools/make-ceremony-avatars.mjs:42`), and no script passes `--out=android/spieltisch/...`. A clean checkout's Spieltisch silently gets 404s and flat figures. Phase 3's `release-spieltisch.sh` plans to stage the avatars. Until then, name the `--out=` invocation in the comment, or have `build-spieltisch-core.sh` copy them from `android/app` when they are present.

### L5. The WebView can navigate anywhere

`TvGamesFrame.kt:131-145`

`AvatarAssetClient` does not override `shouldOverrideUrlLoading`, so a link or redirect in the page can load any https origin in the TV's full-screen WebView. There is no bridge and cleartext is loopback-only, so the impact is small. **Hardening:** return `true` (refuse) for any `request.url.host != "127.0.0.1"`.

## Nits

- `versionCodeOf` (`spieltisch/build.gradle.kts:10-14`, `controller/build.gradle.kts:19-23`) lost the media app's format checks (`app/build.gradle.kts:13-19`). "0.176" fails with IndexOutOfBounds, and "0.176.0.1" is silently accepted. There are now three copies, a candidate for one `build-logic` helper.
- `spieltisch/build.gradle.kts:49`: the comment "the tablet (arm64)" was copied from the media app, but Spieltisch is TV-only. `verifyNativeCore` (line 75) requires x86 and x86_64 even for a release that packages only the two ARM ABIs.
- Doc comments are missing on some public APIs: `NightError`, the `NightUrls` and `GameEntry` fields, `SpieltischConfig.kids_locked`, `GameNightUi`, and `SpieltischCore.startNight`/`stopNight`.
- `crates/spieltisch-core/Cargo.toml:26`: the `rt-multi-thread` feature is unused, since the crate builds no runtime.
- `build-spieltisch-core.sh:8,13` resolves a relative `OUT` argument against the repo root, not the caller's directory. That is fine for the current caller.
- `ShelfGames.kt:21-28`: art is keyed by hand-typed kind strings. A Rust rename would silently drop the art, which then falls back to the name. Acceptable by design.
- Two Back presses within 400 ms during a slow page go from the game straight to the shelf. The behaviour is copied from the media app and is not new.

## Observations, not defects

- **3 of 10 posters lead nowhere in this phase.** Poster has no titles, and Kopfnuss and Dreifelder are refused in kids mode (`room_settings.rs:24-26,66-67`). Spieltisch is always kids-locked, so Kopfnuss and Dreifelder can never start. The docs only say "the games that need films" are blocked.
- **Deviation from the plan, deliberate and documented:** the code is copied, not `git mv`'d, and the shelf helpers are copied as values (`SpieltischTheme.kt:43-64`, equal to `ui-tv`'s) rather than moved to `core/designsystem`. `docs/system-architecture.md` explains why: ui-tv pulls in the media core.

## Positive

- The Rust slot keeps the media core's stop-before-start inside the same lock, and its tests cover start twice, port release, Back with no game, and the exported surface.
- `verifyNativeCore` turns a missing `.so` into a build error without dragging Rust into unit tests or lint (checked with dry runs).
- The shelf really is built from `game_catalogue()`, and the Rust test pins catalogue order and the wire names of kinds.
- The scripts mirror the media ones and fix their cwd (`cd "$(dirname "$0")/.."`).

## Recommended actions

1. M1: withdraw again inside `stopJob` after `cancelAndJoin`, make the announcer (and `stopJob`, for L1) process-wide, and add the race test.
2. M3: make the Back-during-game test advance past 400 ms and assert `nightBack` was called; add the page-claims-Back test.
3. M2: request `ACCESS_LOCAL_NETWORK` on API 37+ before the night starts.
4. L3: `trap` cleanup in check.sh.
5. L2, L4, L5 and nits as convenient.

## Plan follow-ups (I edited no plan files)

These look done: the crate with its `game_catalogue` test, both scripts, the module with its TV manifest and banner, the copied UI and facade, the shelf from the catalogue with the ported test, the version line. Not verified by me: the emulator night, though the screenshots exist in `reports/`.

## Unresolved questions

- Should the shelf hide or mark Poster, Kopfnuss and Dreifelder while Spieltisch is always kids-locked, or should they stay as dead ends until phase 2?
- Will any target box run API 37 before phase 4? That decides whether M2 is needed now or can wait.

**Status:** DONE
**Verdict:** Ready to land after M1 and M3, which are small; M2 can be scheduled; nothing critical.
