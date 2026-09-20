# Phase 01 — One timeline, whichever way it plays

**Priority:** first — everything else attaches to it
**Status:** not started

## Context

- [plan.md](plan.md)
- `web/public/lib/player.js` — `showJump`, `filmTime`, `convert`, the
  `timeupdate` and `jumpTo` listeners at the tail
- `web/public/style.css` — `.jump`, `.converting video::-webkit-media-controls-*`
- `8979475 fix(web): one scrub bar, not two that disagree` — the half-measure
  this finishes

## Overview

`#jump` already covers the whole runtime and restarts the conversion where it
lands, but only exists while converting; the rest of the time the native
timeline does the job. Make one slider that serves both, and hide the native
timeline unconditionally rather than only under `.converting`.

The native bar stays in this phase, minus its timeline and clocks. That is
exactly what `scratchpad/one-bar.png` shows today for a converting title, so
it is a state the player is already known to survive — and no capability is
lost, which is the rule for every phase here.

## Key insight

The two cases differ in one place only: **what a committed drag does.**

| | while converting | playing direct |
|---|---|---|
| `input` (dragging) | move the readout | move the readout, and seek |
| `change` (let go) | restart the encode there | nothing more to do |

Seeking live during a drag is free on a direct file and ruinous on a
conversion, where every pixel would start an encode and finish none. So the
strategy is one predicate, not two code paths.

## Requirements

- One `<input type="range" id="seek">` replaces `#jump-to`, always present
  once a runtime is known, hidden only when nothing knows the runtime.
- Range is the **film's** runtime from the catalog, and the value is
  `filmTime()` — never `video.currentTime`, which counts from wherever an
  encode began.
- Elapsed and total beside it, from `clockTime`, matching the phone.
- The track paints what is played and what is buffered, replacing the one
  thing the native timeline showed that the readouts do not.
- Follows playback except while the viewer holds it.

## Architecture

New `web/public/lib/seek-model.js`, pure, no DOM:

```js
seekModel({ runtime, at, buffered, converting })
  -> { max, value, label, played, ahead, seeksWhileDragging }
```

Pure because `web/test` can prove it and a `LaunchedEffect`-shaped decision
cannot — the same reason `ControlsVisibility.kt` is pure on the phone, and the
reason `up-next.js` and `preload-readout.js` are already shaped this way here.

`player.js` keeps the wiring and loses the arithmetic.

## Related code files

**Create**
- `web/public/lib/seek-model.js`
- `web/test/seek-model.test.ts`

**Modify**
- `web/public/index.html` — `#jump` becomes the seek row; the comment about
  sitting above the video's own controls no longer describes it
- `web/public/lib/player.js` — `showJump` → `showSeek`; listeners follow
- `web/public/style.css` — `.jump` → `.seek`; the
  `::-webkit-media-controls-timeline` rules lose their `.converting` scope

## Implementation steps

1. Write `seek-model.js` and its tests first — the arithmetic is the part that
   has been wrong before (`Number(null) === 0` bit this file three times).
2. Rename `#jump*` to `#seek*` in markup, script and stylesheet together.
3. Make `showSeek` run for every title with a known runtime, not only
   converting ones; keep a module-level `converting` flag for the predicate.
4. Split the `input` listener on `seeksWhileDragging`.
5. Keep `change` restarting the encode only while converting.
6. Paint played and buffered onto the track from `video.buffered` on
   `progress`, `timeupdate` and `seeked`.
7. Widen the `::-webkit-media-controls-timeline` rules to all playback.

## Todo

- [ ] `seek-model.js` with `seekModel`
- [ ] `seek-model.test.ts`: unknown runtime, zero runtime, position past
      runtime, empty `buffered`, converting vs. direct dragging
- [ ] markup and stylesheet renamed
- [ ] `showSeek` covers direct playback
- [ ] drag seeks directly, restarts encodes while converting
- [ ] track paints played and buffered
- [ ] native timeline and clocks hidden in both modes
- [ ] `./scripts/check.sh`

## Success criteria

- A direct title shows one slider that scrubs it, and the native bar shows
  none.
- A converting title behaves exactly as it does today.
- Dragging a converting title starts one encode, not one per pixel.
- `ends` stays truthful across a seek in both modes.

## Risks

| Risk | Mitigation |
|---|---|
| `::-webkit-media-controls-*` is Chromium-only; Firefox keeps its timeline | Accepted and recorded — the bar is removed entirely in phase 03, which fixes Firefox too |
| A direct seek past the buffer stalls where the native bar looked smoother | `buffer-health.js` already drives the `preload` readout; it reports the stall |
| Renaming `jump` touches a file that is 848 lines | Rename in one commit, behaviour in the next |

## Next

Phase 02 builds the transport around this slider.
