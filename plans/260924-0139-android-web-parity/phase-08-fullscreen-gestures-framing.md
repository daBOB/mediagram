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
Priority P2 · Status done (device re-check of fixes pending) · Android-native touch layer over web decisions.

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
- [x] Framing + tests
- [x] Video() box + clip
- [x] gestures + ripple + pinch
- [x] immersive
- [x] sheet + persistence
- [x] subtitle clamp recheck
- [x] check.sh, bump, changelog
- [x] device run: immersive hide/restore, Fill crop in portrait, double-tap
      accumulate + seek, rotation after autoplay switch keeps the new title
- [ ] device re-check of this fix round: 16:9/4:3 actually crop rather than
      stretch (both surfaces), pinch no longer also toggles/seeks, centre
      double-tap pauses while buffering, no stretched first frame after
      rotation, bars re-hide after the sheet closes, up-next card stays
      inside the picture with the bar hidden

## Success criteria
- `FramingTest` covers 4 modes × 2 source ratios; Fit renders pixel-identical to before.
- Device checks above pass; no accidental seek on single tap.

## Risks
- Tap latency: single-tap now waits ~300 ms to rule out a double tap. Accepted (standard in video apps).
- Gesture conflict with the slider/sheet: gestures only on the video area, not on control bar.
- Immersive on API 24-29 uses compat path; test on tablet only (min device available).

## Security
None.

## Implementation notes
- Gestures are attached to the whole screen, not carved to a measured video
  rect: Compose already gives the transport bar, the settings sheet and the
  up-next card's own buttons first claim on a tap before it can reach a
  parent's `pointerInput`, so there was nothing left for a video-area-only
  hit box to add. Simpler, and the risk section's actual worry (a control
  underneath losing its tap) never applied.
- Pinch is a custom `awaitEachGesture` loop rather than
  `detectTransformGestures` directly: the latter fires every frame the
  fingers move, which would call `chooseFraming` (and write a preference)
  many times per pinch. The loop accumulates zoom across the whole gesture
  and fires once, the moment it crosses either threshold.
- `PlayerChoicesController.kt` and `PlayerViewModelDelegates.kt` gained the
  wiring a fourth per-show choice needs (a `FramingController`, matching
  `SubtitleStyleController`'s shape, plus one pass-through); neither file
  was named in this phase's own file list, but the pattern already
  established for speed/audio/subtitles has nowhere else for it to go.

### Fix round (code review + device run)
A review (`plans/.../reports/code-reviewer-260925-0120-phase-08-framing-report.md`)
caught a real defect before the device run reached it, and the device run
itself (on 0.48.0) then confirmed everything else: immersive hide/restore,
Fill cropping in portrait, double-tap accumulation and seeking, and a
rotation after an autoplay switch keeping the new title.

- **C1 (blocking): 16:9/4:3 stretched the picture, on both surfaces.**
  `forcedAspect` stood in for the video's own aspect and handed `Video()` a
  box of *that* shape, which `requiredSize` then drew the surface into
  directly — a non-uniform scale, not a crop. The web looked the same but
  for a different reason, and the first implementation note's claim that
  the two matched was wrong on both counts: `video { width: 100%; height:
  100% }` in `style.css` makes both dimensions definite, and CSS ignores
  `aspect-ratio` once they are — so "16:9"/"4:3" rendered as `object-fit:
  cover` alone, i.e. as `fill`, cropping to the *screen's* shape rather
  than the named one. **User decision: crop, never stretch — what
  `framing.js`'s own comments always described.** Fixed on both surfaces:
  - Android: `Framing.kt`'s `videoBox`/`visibleBox` became a single `frame`
    function returning `Framed(box, window)`. A named ratio now first fits
    a `window` of that *ratio* within the container (`fitWithin`, the same
    algorithm `Fit` already used on the video's own shape), then fits the
    video's own shape *over* that window (`fitOver`) — the picture keeps
    its aspect always, cropped into a smaller, fixed-shape, letterboxed
    window rather than stretched to fill one. `Fit`'s window is the
    container itself and `Fill`'s box is fit over the container directly,
    so both are unchanged. `Video()`'s overlay slot (subtitles, and now
    the up-next card while the bar is hidden — see below) sizes to
    `window`, not to the (possibly bigger) `box`. `FramingTest.kt` rewrote
    every ratio case to assert `box.width / box.height == videoAspect` and
    added window-shape assertions.
  - Web: `framing.js` gained `framingBox(name, containerWidth,
    containerHeight)` (`null` for `fit`/`fill`, a centred pixel box for the
    two ratios — the same `fitWithin` Android's own port uses). `transport.js`'s
    `applyFraming()` now sets the video element's `left`/`top`/`width`/
    `height` from it (cleared back to the stylesheet's own `inset: 0`
    for `fit`/`fill`), with a `resize` listener so the window stays
    correct across a browser resize or a fullscreen toggle. `framing.test.ts`
    gained a `framingBox` suite; the existing `framingStyle` tests are
    untouched and still pass — `aspectRatio` is still set (inertly) for
    parity with what a viewer's devtools would show, but no longer does
    the actual work.
- **H1: a pinch also counted as a tap** (and two quick pinches as a
  double-tap), because `detectPinchFraming` never consumed the pointer
  changes it read. Fixed: once two fingers are down, or a pinch has
  already fired this gesture, every change is consumed on every event for
  the rest of it — the tap detector, attached after it on the same node
  and so seeing the `Main` pass first, then finds them already consumed
  and cancels rather than firing when the last finger lifts.
- **H2: a centre double-tap did nothing while the title was buffering** —
  `if (isPlaying) pause() else play()` calls `play()` on a player that is
  already `playWhenReady` but `STATE_BUFFERING` (`isPlaying` is false
  either way), which is a no-op, and this app's byte path rebuffers often
  enough for it to matter. Fixed: `Util.handlePlayPauseButtonAction`, the
  same buffering-aware toggle the transport button's own
  `rememberPlayPauseButtonState` already uses.
- **M1: the first frame, and the first frame after every rotation, drew
  full-bleed** — `onGloballyPositioned` only reports this composable's own
  size a frame after layout, and rotation recreates its `remember`ed state
  along with the activity. Fixed: `Video()` now measures with
  `BoxWithConstraints`, whose `maxWidth`/`maxHeight` are known in the same
  pass a `Box` is measured in, so `frame` runs before anything is ever
  drawn stretched. `onGloballyPositioned`/`containerPx` state is gone
  entirely.
- **M2: the bars came back while the settings sheet was open**, and could
  stay back after returning from the background on the API 24-29 compat
  path — `ImmersiveEffect` only hid them once, on entry, and the sheet is
  its own dialog window (the focused window's own requested visibility
  wins on API 30+). Fixed: re-hides on `ON_RESUME` and on this window
  regaining focus (`ViewTreeObserver.OnWindowFocusChangeListener`) — the
  same listener covers "the sheet closed" for free, since a dialog closing
  is exactly the main window regaining focus.
- **L1 / low finding "pinch on the scrubber":** folded into the H1 fix —
  `detectPinchFraming` now also skips the zoom calculation entirely when
  any change in the event is already consumed elsewhere (the scrubber, a
  control), so a finger down on the Slider can never contribute to a pinch
  no matter what the raw distance to a second finger says.
- **Device finding, up-next card in immersive mode:** with the bar hidden,
  the card anchored to the screen's own bottom rather than the picture's,
  so under a letterboxing framing its scrim and buttons hung in the black
  band below the picture. Fixed the same way subtitles already were:
  `Video()` now reports its window's own bottom edge
  (`onWindowBottomChanged`), threaded through `VideoWithSubtitles` and held
  by `PlayerScreen` as `pictureBottom`; `UpNextCard` clears whichever of
  `barTop` (bar shown) or `pictureBottom` (bar hidden) applies, rather than
  always the bar.
- L3 (racing framing writes, same pattern speed/subtitle style already
  use) and L2 (a tap shortly after a control-bar tap) are unchanged from
  the review — accepted as pre-existing pattern and pre-existing risk
  respectively, not new to this round.
- No new unit tests for H1/H2/M2's own mechanics (pointer consumption
  ordering, `Util.handlePlayPauseButtonAction`'s buffering awareness,
  window-focus timing): none of this module's other gesture or lifecycle
  wiring is Compose-UI-tested either (`PlayerScreenTest`'s own note: "no
  Compose test rule ... proves the decision itself, not the wiring"), and
  none of the three has decision logic of its own left to extract — the
  fixes are entirely calls into already-correct framework/media3 behaviour.
  `FramingTest`/`framing.test.ts` were both rewritten for C1's actual fix.

## Deliberate differences
- No keyboard cycle: the web's `z` key walks all four framings in order.
  This app has no keyboard in the player, so the sheet offers all four as a
  direct pick and a pinch reaches the two used most (Fill, Fit) without
  opening it — recorded here rather than ported as a cycle nothing would
  ever trigger.
- Pinch fires once per gesture at a fixed zoom threshold (±20%), an Android
  touch idiom with no web equivalent to match.
- **User decision (this fix round):** 16:9/4:3 crop, never stretch, on
  both surfaces — the web's own `framing.js` intended this from the start;
  its first shipped behaviour (indistinguishable from `fill`) was a bug
  the CSS engine's own `aspect-ratio`-with-definite-`width`/`height` rule
  produced, not a decision, and is fixed rather than recorded as a
  difference.

## Next steps
09 needs the immersive state to hide controls in PiP.
