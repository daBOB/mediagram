# Phase 02 — HUD reassurance

**Priority:** second · **Status:** complete

## Overview

Say why a title is converting, how fast the buffer is actually filling, and
whether frames are being dropped.

## Key insights

- `buffer-health.js` already measures fill rate against the wall clock and
  the readout throws it away. Depth alone cannot tell a satisfied player
  from a starving one — the property is documented in that file.
- `playbackFor()` already decides *why* a title cannot play directly; the
  note only ever repeated *that* it could not.
- A dropped-frame count of zero is noise. Show it only when it is not.

## Requirements

- `preloadReadout(readyState, aheadSeconds, fillRate?)` — third argument
  optional, so every existing call keeps working.
- A reason clause on the conversion note, from the existing decision.
- Dropped frames from `getVideoPlaybackQuality()`, when non-zero.

## Related code files

- Modify: `web/public/lib/preload-readout.js`, `web/public/lib/player.js`,
  `web/public/lib/link.js` (expose the reason if it is not already returned)
- Modify: `web/test/preload-readout.test.ts`

## Implementation steps

1. Extend `preloadReadout` with an optional rate; format as `filling 1.4×`.
   Omit when unknown, when not playing, or when within a hair of 1.
2. Read the reason out of `playbackFor(set)` and append it to the note.
3. Sample `getVideoPlaybackQuality()` on the existing playback tick.
4. Extend the tests.

## Todo

- [ ] fill rate in the readout
- [ ] conversion reason in the note
- [ ] dropped frames
- [ ] tests

## Success criteria

`preloadReadout(2, 12, 1.4)` → `buffering · 0:12 ahead, filling 1.4×`;
`preloadReadout(2, 12)` → `buffering · 0:12 ahead` (unchanged). `bun test`
green.

## Risks

A noisy rate flickering in the HUD — mitigated by rounding to one decimal
and omitting the clause near 1.0.
