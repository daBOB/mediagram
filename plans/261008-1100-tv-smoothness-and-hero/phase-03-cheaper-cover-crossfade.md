# Phase 03 — cheaper cover crossfade

**Status:** rejected; the cover is unchanged.

## Why

Home at rest on the box: the UI thread is idle, but the render thread takes ~15 ms to issue draw commands and the GPU ~22 ms per frame, for all 2.8 s of crossfade per 20 s. `TvHomeCover` crossfades the whole slide (picture, scrim, words). The alpha on a slide that overlaps itself draws both slides into offscreen layers.

## Prototype

Fade only the picture, with no offscreen layer. Draw the scrim once over both pictures. Fade the words on their own small layer.

## Keep only if

On the box, compiled, Home idle for 20 s: GPU per frame and frame p50/p90 drop clearly below the baseline (p50 32 ms, GPU ~22 ms), and the look is unchanged.

## Result (box, compiled, 20 s of Home at rest, focus on the bar so the cover rotates)

| Build | frames | frame p50 | RT issue p50 | GPU p50 |
|---|---|---|---|---|
| baseline (whole-slide `Crossfade`), 3 runs | 164 | 34 ms | 14.7–14.8 ms | 23.3–23.5 ms |
| A: pictures with `ModulateAlpha`, scrim once, words faded alone | 134 | 53 ms | 13.3 ms | 38.9 ms |
| B: pictures with `Crossfade`, scrim once, words faded alone, 2 runs | 134 | 48 ms | 14.2 ms | 37.7–38.0 ms |

Both variants cost this GPU (Mali, 1080p UI) about 15 ms more per frame than the original, so it is not the alpha strategy. Drawing the three full-screen scrim gradients outside the faded layer costs more here than drawing them inside it. Kept copies of the prototype were in the session scratchpad only. Other levers (a shorter fade, a smaller decode for the cover picture) change how it looks, so they are the user's call.
