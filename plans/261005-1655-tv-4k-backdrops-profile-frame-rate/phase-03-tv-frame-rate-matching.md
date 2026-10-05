# Phase 03: TV frame-rate matching

**Priority:** low (no gain on the current monitor). **Status:** pending.

## Context

- **Media3 default:** `ExoPlayer.Builder` sets `VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS` (`androidx/media` `ExoPlayer.java:513`). Javadoc: an app using `CHANGE_FRAME_RATE_ALWAYS` "should set the mode to `VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF`".
- **Android guide** (`developer.android.com/media/optimize/performance/frame-rate`): for a heavy switch "(for example, on an Android TV device), use `preferredDisplayModeId`".
- **Player construction:** `android/core/playback/src/main/kotlin/PlayerFactory.kt` `buildPlayer(context, …)`.
- **TV player:** `android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerScreen.kt`. `ui-common/.../PlayerLifecycle.kt` already reaches the Activity through `findActivity()`.
- **Current display:** the box drives an HP OMEN 27k at 3840×2160 @ 59.94 Hz only (memory `tv-box-4k-facts`), so this phase must change nothing there.

## Design

**Pure picker, `core/playback/src/main/kotlin/DisplayModeMatch.kt`:**
- Signature: `pickDisplayMode(current: Mode, supported: List<Mode>, fps: Float): Int?`, where `Mode(id, width, height, refreshHz)`.
- Candidates: the same `width × height` as `current`, with `refreshHz / fps` within 0.5% of a whole number ≥ 1.
- Choice: the smallest multiple wins, then the smallest error.
- Returns `null` when:
  - `fps` is unknown or ≤ 0;
  - no candidate exists;
  - the best candidate is `current`.

**`buildPlayer`:** on TV only, `setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)`. This leaves one mechanism in charge, not two. TV detection uses the shared `isTelevision` from phase 01.
- If phase 01 has not landed, read `UiModeManager` here and leave a note for phase 04 to merge it.

**`TvPlayerScreen` effect:**
- When the selected video `Format.frameRate` becomes known (`Player.Listener.onTracksChanged` / `videoFormat`), read `display.mode` and `display.supportedModes` and run the picker.
- On a match, set `window.attributes.preferredDisplayModeId`.
- `onDispose` restores the id that was there before, normally 0.
- One `DisposableEffect` keyed on the player.

**Comment at the effect:** why it is TV-only (see the plan's Deliberate differences).

## Files

- **Create:**
  - `android/core/playback/src/main/kotlin/DisplayModeMatch.kt`
  - `android/core/playback/src/test/kotlin/DisplayModeMatchTest.kt`
- **Modify:**
  - `android/core/playback/src/main/kotlin/PlayerFactory.kt`
  - `android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerScreen.kt` (or a small sibling file if the effect pushes it over 200 lines)

## Steps

- [ ] Picker tests first:
  - 23.976 fps with modes {59.94, 23.976} → 23.976;
  - 24 with {60, 24, 48} → 24;
  - 25 with {60, 50} → 50;
  - 29.97 on 59.94 current → null;
  - 23.976 with only {59.94, 60} → null;
  - a mode at another resolution is ignored;
  - fps 0 or unknown → null.
- [ ] Implement the picker; set strategy OFF on TV; add the effect.
- [ ] `./gradlew testDebugUnitTest lint`.
- [ ] Box (phase 04): play one 24p film on the "TV test" profile. Expect no mode switch, no black flash, and normal start and exit.

## Success criteria

The frame-rate acceptance rows in `plan.md`. The real switch cannot be shown on this monitor; the picker tests are what prove the matching.

## Risks

- **HDMI re-sync.** A real switch blanks the screen for 1–2 s mid-start. Accepted for now, with no toggle (YAGNI); revisit once a 24 Hz display exists.
- **Restoring the mode.** It must happen even if the player screen dies with the Activity, which `onDispose` covers.
