# Phase 08: Immersive fullscreen, double-tap seek, framing

## Context links
- `web/public/lib/framing.js:19-27` (modes `fit` (contain, default) / `fill` (cover) / `16:9` / `4:3` (cover + ratio), labels Fit/Fill/16:9/4:3)
- `web/public/lib/transport.js:157-160,222-226` (cycled with `z`, remembered per show as `framing`)
- `web/public/lib/transport.js:46`, `player-keys.js:68-71` (skip = 10 s)
- `web/test/framing.test.ts` (cases to port)
- `android/ui-mobile/src/main/kotlin/PlayerScreen.kt:122` (tap toggles controls via `detectTapGestures`), `:156` (`systemBarsIgnoringVisibility` padding)
- `android/ui-mobile/src/main/kotlin/PlayerScreenParts.kt:48-64` (`Video()` letterboxes to decoded aspect)
- `android/app/src/main/kotlin/com/mediagram/android/MainActivity.kt` (`enableEdgeToEdge`)
- `android/core/playback/src/main/kotlin/PlayerFactory.kt:64-73` (seek increments 10 s)

## Overview
Priority P2 · Status pending · Android-native touch layer over web decisions.

## Key insights
- Web has no touch gestures; its skip is 10 s. Double-tap left/right = the
  same 10 s the buttons already use — phone-native input for a web decision, not a new decision.
- Web has four framing modes and no on-screen control (keyboard `z`). The
  phone gets the same four, in the settings sheet, plus pinch: pinch-out →
  `fill`, pinch-in → `fit` (the Android idiom). Both write `framing`.
- Fullscreen on web is a button; on a phone the player is always fullscreen:
  hide system bars while the player is shown (swipe reveals them transiently),
  restore on leave. No button needed.
- Fill/16:9/4:3 enlarge the video box past the screen: phase 06's subtitle layer
  must be clamped to the **visible** rect, not the video box.

## Requirements
- `WindowInsetsControllerCompat.hide(systemBars())`,
  `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` while player visible; show on dispose.
- Single tap toggles controls (delayed by double-tap timeout); double-tap left
  third −10 s, right third +10 s, centre = play/pause; brief "−10 s"/"+10 s" ripple label; repeated double-taps accumulate.
- Framing: `Framing.kt` pure → content scale + optional forced ratio; sheet
  section; pinch; remembered; reset-on-open (phase 04 rule).

## Architecture
`Framing.kt` (`:core:playback`, pure): `(mode, videoAspect, containerSize) → VideoBox(width, height)` centred, may exceed container.
`Video()` reads `VideoBox`; parent clips. `PlayerGestures.kt` wraps `pointerInput`
with `detectTapGestures(onTap, onDoubleTap)` + `detectTransformGestures` (zoom only).
`ImmersiveEffect.kt`: `DisposableEffect` over the activity window.

## Related code files
Create:
- `android/core/playback/src/main/kotlin/Framing.kt` + `FramingTest.kt` (port framing.test.ts; wide and 4:3 sources)
- `android/ui-mobile/src/main/kotlin/PlayerGestures.kt`, `SeekRipple.kt`, `ImmersiveEffect.kt`, `FramingSection.kt`
Modify:
- `PlayerScreen.kt` (replace tap handler, add immersive effect), `PlayerScreenParts.kt` (`Video()`), `SubtitleLayer.kt` (visible-rect clamp), `PlayerChoices.kt`, `PlayerViewModel.kt`, `PlayerSettingsSheet.kt`

## Implementation steps
1. `Framing` + tests.
2. `Video()` uses `VideoBox`; clip; default `fit` identical to today (screenshot compare).
3. Gestures: tap/double-tap zones, ripple, pinch → framing; controls visibility unchanged otherwise.
4. Immersive effect; verify back gesture and status-bar-dependent toggles (`1444ab6` moved list toggles below the status bar — recheck).
5. Sheet framing section; persistence; reset-on-open.
6. Subtitle clamp; re-run phase 06 device check under Fill.
7. Device: 2.39:1 film Fit vs Fill; 4:3 lesson under 16:9; double-tap seeks; bars hidden and return on swipe.

## Todo
- [ ] Framing + tests
- [ ] Video() box + clip
- [ ] gestures + ripple + pinch
- [ ] immersive
- [ ] sheet + persistence
- [ ] subtitle clamp recheck
- [ ] check.sh, bump, changelog, device run

## Success criteria
- `FramingTest` covers 4 modes × 2 source ratios; Fit renders pixel-identical to before.
- Device checks above pass; no accidental seek on single tap.

## Risks
- Tap latency: single-tap now waits ~300 ms to rule out a double tap. Accepted (standard in video apps).
- Gesture conflict with the slider/sheet: gestures only on the video area, not on control bar.
- Immersive on API 24-29 uses compat path; test on tablet only (min device available).

## Security
None.

## Next steps
09 needs the immersive state to hide controls in PiP.
