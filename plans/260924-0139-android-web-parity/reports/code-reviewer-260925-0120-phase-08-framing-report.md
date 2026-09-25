# Phase 08 review: fullscreen, double-tap, framing

Scope: uncommitted diff on `feat/android-web-parity` (base 71d82fb). New: Framing.kt, FramingController.kt, PlayerGestures.kt, SeekRipple.kt, ImmersiveEffect.kt, FramingSection.kt and their tests. Modified: PlayerScreen/Parts/Lifecycle, SubtitleLayer, settings sheet, choices controller.
Targeted unit tests (FramingTest, FramingControllerTest, PlayerGesturesTest, SeekRippleTest) pass.

## Critical

### C1. The 16:9 and 4:3 framings stretch the picture. The web never does.
`android/core/playback/src/main/kotlin/Framing.kt:57-61` puts `forcedAspect` in place of the video's own aspect and hands back a box of *that* shape. `PlayerScreenParts.kt:92-97` then draws `PlayerSurface` into it with `requiredSize`, and that surface "applies no ratio of its own" (the file's own KDoc says so). The result is that the video gets scaled non-uniformly into the forced ratio.
- Failure: a 2.39:1 film under "16:9" on a 2000x900 screen gets a 2000x1125 box (FramingTest.kt:59-62 asserts exactly this). The film is stretched vertically by 34% and everyone in it looks tall and thin, which is the defect `Video()`'s KDoc says it exists to prevent. Likewise a 16:9 episode under "4:3" is stretched by 33%, and `ratio16by9IgnoresTheSourceEntirely` (FramingTest.kt:65-70) locks the distortion in.
- Web: `object-fit: cover` always keeps the video's own aspect and only crops. Also, `video` in style.css:1170-1177 is `position:absolute; inset:0; width:100%; height:100%`. Both dimensions are definite, so CSS ignores `aspect-ratio`. On the web today, "16:9" and "4:3" therefore render the same as "fill". Android matches neither.
- The implementation note's claim ("mirroring what the web's aspect-ratio CSS property does to object-fit: cover") is false. Cover never changes the shape of the content.
- Fix: never draw with a non-native aspect. There are two options:
  - (a) Match the web as it actually renders: forced ratios behave like FILL.
  - (b) Implement what framing.js intends (crop the video to a centred R-shaped window, then fit that window into the screen): `window = fitWithin(R, container)`, `box = fitOver(videoAspect, window)`, clip to `window`, `visible = window`.

  Either way, rewrite the ratio tests to assert that `box.width / box.height == videoAspect` for every mode. Option (b) also means the web needs to be fixed for parity. Decide which, and record it.

## High

### H1. A pinch also counts as a tap, so it toggles the controls. Two quick pinches count as a double-tap and seek or pause.
`PlayerGestures.kt:269-287` (`detectPinchFraming`) never consumes pointer changes.
- `detectTapGestures` (`:210`, with no `onLongPress`, so there is no timeout) only cancels a tap on a consumed or out-of-bounds change. Movement alone does not cancel it.
- When the last finger of a pinch lifts, that event holds only that pointer, all of it `changedToUp`, so it counts as a tap. Every pinch-to-Fill therefore also flips `controlsShown` about 300 ms later.
- A pinch that follows another pinch within the double-tap timeout triggers `onDoubleTap`, which seeks ±10 s or toggles play/pause, depending on where the last finger was.
- Fix: in the pinch loop, once a second pointer is down (or `zoomChange != 1f`), `consume()` the changes. The inner `pointerInput` sees the Main pass first, so the outer tap detector then cancels.

### H2. Double-tapping the centre does not pause while the player is buffering.
`PlayerGestures.kt:252` uses `if (player.isPlaying) pause() else play()`. `isPlaying` is false during STATE_BUFFERING even when `playWhenReady` is true.
- Failure: while the title is rebuffering, a double-tap in the centre calls `play()`, which does nothing. The viewer cannot pause. Rebuffering is common in this app because of the slow Telegram byte path on large files. At STATE_ENDED, `play()` also does nothing.
- The on-screen button (`PlayerControls.kt:82`, `rememberPlayPauseButtonState`) uses media3's `Util.shouldShowPlayButton`/`handlePlayPauseButtonAction`, so the gesture and the button disagree.
- Fix: `Util.handlePlayPauseButtonAction(player)`.

## Medium

### M1. The first frame, and the first frame after every rotation, is drawn stretched to the full screen.
`PlayerScreenParts.kt:77-85`: `containerPx` starts at `IntSize.Zero` and is only filled in by `onGloballyPositioned` after layout. Until then `box == null`, so the surface is `fillMaxSize()`.
- Rotation recreates the activity, so `remember` resets.
- Multi-window and PiP resizes lag one frame behind the old box.
- The old `aspectRatio` modifier was correct on the very first layout, so Fit is not strictly identical to the previous layout.
- If `coverSurface` is false at that moment (for example when reopening the same title after a rotation while the player keeps its frame), a stretched frame shows.
- Fix: measure in the same pass with `BoxWithConstraints`, or a custom `Layout`.

### M2. Bars come back while the settings sheet is open (needs a device check).
`ImmersiveEffect.kt` hides the bars on the activity window only. The Material 3 `ModalBottomSheet` is its own dialog window, and on API 30+ the focused window's requested visibility wins.
- While the sheet is open, the status and navigation bars show for real. The transport bar (`PlayerControls.kt:128-129`, `safeDrawing`) and `barTop` / subtitles jump by the nav-bar height, then drop back when the sheet closes.
- On API 24-29 (the compat path uses legacy flags), the flags can stay cleared after a dialog or a return from the background, because nothing re-applies them on focus or resume.
- Fix: re-hide the bars on `ON_RESUME` and on window-focus regain. Consider hiding the bars from the sheet's own window too.

## Low

- **L1. Pinch can fire while scrubbing.** `PlayerGestures.kt:275`: `calculateZoom()` ignores consumption. If one finger is on the Slider and another touches the picture, the framing can change mid-scrub. Guard with `event.changes.none { it.isConsumed }`.
- **L2. A tap shortly after a video tap also toggles the controls.** Single-tap the video, then tap a control-bar button within about 300 ms: the button acts, and then `onTap` (after the double-tap timeout) toggles the controls off. `awaitSecondDown` ignores the consumed down and times out. The bar can vanish right after the viewer uses it.
- **L3. Racing framing writes.** `FramingController.remember` (`:57`) fires `launch` without ordering. A quick pinch followed by a sheet pick can persist the older value if the two writes finish out of order. This is the same pattern speed and subtitle style already use, so it is not new.

## Checked, no issue
- **Fit math:** `fitWithin` equals the old `fillMaxSize().aspectRatio(r)` (width first, then height), apart from 1 px of Dp rounding.
- **Fill** equals `object-fit: cover`.
- **Subtitles:** the overlay box is sized to `visibleBox` (the screen under the cover modes), and `SubtitleLayer`'s `matchParentSize` bottom is measured against it. The clamp is correct.
- **Up-next card:** lifted relative to `screenBottom` (root), so framing does not affect it.
- **SurfaceView:** it is not clipped by `clipToBounds`, but the edge-to-edge window clips it at the screen edge. Worth one device check under 4:3.
- **Child taps:** control-bar buttons, the Slider, PlayerMarks toggles and up-next buttons consume their down events, so the double-tap zones do not steal their taps. The settings sheet is a separate window.
- **Immersive show/hide:** hidden on enter and shown again on dispose (back, switch, rotation). Process restore re-composes and hides again. PlayerMarks uses `systemBarsIgnoringVisibility`, so it stays put. `PlayerTopBar` has no insets, as before.
- **Framing preference:**
  - One `"framing"` key, per show scope, same strings as the web.
  - `reset()` runs for a different title; a same-title reopen keeps the choice.
  - A hand pick made before the preferences load wins, and is written once the scope is known.
  - This matches the `SubtitleStyleController` pattern, and the race test uses a real suspending gate.
- **File sizes:** every new or modified file is under 200 lines (PlayerScreen.kt is 199; PosterCard.kt at 230 is untouched and older).

## Unresolved
- For the forced ratios, should the phone match what the web renders today (same as Fill), or what framing.js intends (crop to that ratio)? The web needs a fix under the second option.

**Status:** DONE_WITH_CONCERNS. C1 is blocking because it distorts the picture under 16:9 and 4:3. H1 and H2 should be fixed before the device run.
