# TV frame-rate matching

Files: core/playback DisplayModeMatch.kt (+Test, 7 cases), PlayerFactory.kt (strategy OFF on TV), ui-tv TvDisplayModeMatch.kt (new effect), TvPlayerScreen.kt (+1 line call).
Tests: `./gradlew testDebugUnitTest lint` pass.
Merge item: PlayerFactory.kt `onTelevision(context)` is a local UiModeManager read; replace with shared isTelevision once moved.
Deviation: display read via `window.decorView.display` (Activity.display needs API 30, minSdk 24).
Risks: HDMI re-sync blank 1-2 s on a real switch; no effect on the 59.94-only monitor (picker returns null). Not device-verified (no adb per constraints).
