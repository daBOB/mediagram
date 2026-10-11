# Apple TV style's Home frame cost — investigation (2026-10-11)

Question: why Apple TV style's Home measured +5–6 ms p90 over mediagram on the box (box report, Set B: W1 48 vs 42, W1b 53 vs 48) after its shadow was dropped.

Method: Perfetto traces on the box (0.122.0 fixed build, profile andre, 22 s each), Home at rest and Home scroll (8 down / 8 up from the Home pill), mediagram → Apple → Google, then back to mediagram. Categories gfx/view/input/res + sched + SurfaceFlinger frame timeline; analysed with trace_processor. Traces kept in the session scratchpad (`themes-frames-261011/{mg,ap,gg}-home{,scroll}.pftrace`).

## Findings
- **The whole pipeline is GPU/display-bound on this box, in every style.** The UI thread records in ~0 ms (`Record View#draw()` p50 0.0 at rest, 0.2 on scroll). It spends its frame waiting to sync with RenderThread (`draw` p50 15.7 ms), which in turn waits ~10 ms per frame in `dequeueBuffer` for a free buffer. Compose is not the cost.
- **Home at rest: no real Apple cost.** Per-frame stages match mediagram's: RenderThread `DrawFrames` p50/p90 16.7/18.6 vs 16.7/18.8, GPU completion p50 8.5 vs 8.0, the same two cover backdrop decodes. Frame-timeline p90 is 50.0 vs 48.1. Set A measured the two equal (42/42). In Set B, GPU p90 swings between ~14 and ~23 ms run to run *in both styles*, depending on which cover crossfades fall inside the 20 s window. The W1 gap is that noise.
- **Home scroll: the gap is real.** Frame-timeline p90 is 59.5 (Apple) vs 53.0 (mediagram) vs 45.5 (Google).
  - **Where:** the slow frames are the seconds when the full-bleed cover is on screen and moving under the bar (going down 5–6 s, coming back up 11–13 s). There, GPU completion p90 is 16–25 ms in mediagram and Apple. With rows only on screen (8–10 s) it is 3.5–8 ms, and every style holds ~33 ms frames.
  - **What Apple adds:** about +1–1.5 ms of GPU per frame (24.8 vs 23.6, 19.3 vs 18.1, 17.4 vs 16.6 in the cover seconds) and slightly more RenderThread time. Its rounded clips and 16:9 stills show up as Skia clip-mask preparation (`OpsTask::onPrepare` 4,816 calls vs 17) and 55% more `TextureOp`s (11,156 vs 7,188). Google pays the same.
  - **Why ~1 ms becomes +6.5 ms at p90:** frames land on whole display intervals (16.68 ms at 59.94 Hz). Frames needing ≥ 4 intervals are 12.5% for Apple, 7.1% for mediagram and 2.9% for Google; p90 sits on that 3-to-4-interval step. A small extra GPU cost, on top of a cover that already needs more than one interval of GPU, pushes ~5% of frames over it.
  - **Why Google is fastest despite the same cards:** its inset cover card is ~57% of the screen and drops the full-bleed scrim, so its cover seconds run 14–17 ms of GPU p90 and frames rarely reach four intervals.
- **The cover is the dominant GPU cost, for mediagram too.** `CoverScrim` (`ui-common/.../HeroScrims.kt`) draws three full-size gradient layers (top scrim, paper fade, horizontal gradient) over a full-screen backdrop. That makes four full-screen blended layers per slide, and eight while two slides crossfade, plus the bar's band. That is what puts mediagram's cover frames over one interval in the first place.
- **Secondary, shared by Apple and Google:** 16:9 cards decode the 1280×720 backdrop files to 329×185. Each decode takes 77–93 ms against 36 ms for a 342×513 poster, and the upload 17.6–19.7 ms against 6.8 ms. That is +380 ms of background CPU in 22 s. Google's worst second (7 s, p90 75 ms) is the one in which 15 of these decodes landed.

## Options (not done; for the user)
1. **Accept.** Apple's extra is ~1–1.5 ms of GPU for rounded corners and 16:9 stills, the same as Google's. It only shows while the full-bleed cover scrolls.
2. **Cut the cover's overdraw, for every style.** Merge `CoverScrim`'s two vertical gradients into one brush, leaving three full-screen layers instead of four. This is the largest lever, and it would likely bring Apple at or under today's mediagram. It changes mediagram's pixels by gradient rounding (the merged ramp approximates the composite of the two), and the phone shares `CoverScrim`, so it needs a side-by-side check and a box re-measure.
3. **Smaller stills for 16:9 cards** (a w300 backdrop or a cached downscale) for Apple and Google. This cuts the decode CPU and the uploads behind the rows-appear spikes, but not the cover cost.

Recommendation: 2, measured on the box against today's numbers, then 3 if the rows-appear spikes still show.

## Unresolved questions
- Exact per-layer GPU split of the cover: the box exposes no GPU counters. Option 2's effect needs a build and a box run to confirm.
- Whether SurfaceFlinger scaling the 1080p UI onto the 4K output shares the GPU with the app's frames. The `dequeueBuffer` waits suggest the display side is part of the limit.
