# Phase 3: Speed, and how the picture is fitted

**Status:** Not started

**Deliverable:** A playback speed control and a fitting control in the player.
Both are per-playback here; phase 6 makes them stick.

## Context

- `web/public/lib/transport.js:43,163-167` — the speed steps the web offers:
  0.75× to 2×
- `web/public/lib/framing.js:23-70` — **the reference** for fitting: Fit, Fill,
  16:9, 4:3, cycled
- `android/ui-mobile/src/main/kotlin/PlayerScreenParts.kt:48-64` — `Video()`
  letterboxes to the decoded aspect ratio and that is all it does
- `android/ui-mobile/src/main/kotlin/ControlsVisibility.kt` — the rule for when
  the bar may show and when it fades

## Key insight

These two are together because they are the last of the controls that need
nothing but the `Player` and a box to draw in — after this, every remaining gap
waits on phase 4. They are also the smallest useful thing that can ship while
phase 4's core work is under way.

Fitting matters more on a phone than on a laptop. A 2.39:1 film on a 20:9
handset in portrait is a strip of picture between two thick bars, and Fill is
the control that answers it. The letterboxing already landed is the correct
default and stays the default; this adds the viewer's ability to override it.

Speed is one line on the `Player` — `setPlaybackSpeed` — and a menu.

## What gets built

**Speed**, in the menu phases 1 and 2 share. The web's steps verbatim, so that
a viewer moving between surfaces finds the same list.

**`Fitting.kt`, pure, in `:core:playback`.** Takes a fitting choice and the
video's decoded aspect ratio and returns what `Video()` should apply — a
content scale and an aspect ratio. Pure so that the television surface, when it
exists, gets the same answer without reading it out of a composable.

**A fitting control**, cycling as the web does rather than opening a menu: four
states, one button, the current one named on it.

## Success criteria

- A 2.39:1 film on the phone fills the screen on Fill and letterboxes on Fit,
  with Fit still the state it opens in.
- Speed changes take effect without interrupting playback, and the bar says
  which speed is running when it is not 1×.
- `Fitting` is tested across the four states against a wide and a 4:3 source.
- `./scripts/check.sh` passes.

## Risks

- **Fill crops.** That is what it is for, but it must crop symmetrically and
  must not move the subtitle box off screen — phase 2 shipped before this and
  its cue box has to be checked against Fill, not only against Fit.

## Todo

- [ ] speed, in the shared menu, with the web's steps
- [ ] `Fitting.kt` with its tests
- [ ] the cycling control, and `Video()` reading it
- [ ] check phase 2's cues against Fill
- [ ] a real device run on a 2.39:1 film
- [ ] `./scripts/check.sh`
