# Frame times on the tablet, with and without the web's motion

Tablet caad49da (2136x3200 panel, shown landscape), `benchmark` build type, all 0.120.0.
Animator scale 1.0 before and after. Backdrop as found: Default (restored at the end).
Walks: idle = Home with the cover showing, 20 s settle, `gfxinfo reset`, 30 s window;
scroll = reset, three swipes down and three up on Home (`input swipe ... 120`, 1 s apart).
"Janky" is gfxinfo's `Janky frames` (deadline-based); the legacy figure (frames over 16 ms) is
given too, since the brief's grep picks up both lines. Frame counts differ between builds
because the display only refreshes when something draws: the motion build's cover drift
keeps drawing, main's idle cover does not.

| Build | Backdrop | Walk | Frames | Janky | Legacy janky | p50 | p90 | p99 |
|---|---|---|---|---|---|---|---|---|
| main | Default | idle | 960 | 0.21% | 72.5% | 17 | 18 | 31 |
| main | Default | scroll | 880 | 0.11% | 2.95% | 9 | 12 | 25 |
| main | Blurred | idle | 2100 | 0.10% | 14.5% | 12 | 15 | 30 |
| main | Blurred | scroll | 1006 | 0.10% | 5.57% | 8 | 11 | 22 |
| motion | Default | idle | 7206 | 0.00% | 28.7% | 14 | 16 | 18 |
| motion | Default | scroll | 1056 | 0.00% | 2.46% | 7 | 12 | 23 |
| motion | Blurred | idle | 7208 | 0.00% | 32.6% | 14 | 16 | 19 |
| motion | Blurred | scroll | 1220 | 0.00% | 30.5% | 14 | 18 | 22 |
| frosted-masthead | Default | idle | 1212 | 0.33% | 17.7% | 14 | 16 | 38 |
| frosted-masthead | Default | scroll | 948 | 0.00% | 5.27% | 11 | 14 | 27 |

(Times in ms. GPU p90: main Blurred idle 12, motion Blurred idle 11.)

## Ruling on Blurred

The rule: the drift janks under Blurred if, on the idle walk, motion's p90 exceeds 16 ms where
main's does not, or its janky share rises by more than 10 points.

- p90: motion 16 ms, main 15 ms. 16 does not exceed 16.
- Janky share (gfxinfo `Janky frames`): motion 0.00%, main 0.10%. It fell.
- GPU p90 is 11 ms for motion against 12 ms for main.

Verdict: the drift does not jank under Blurred. No fallback applied; `CoverSlide.kt` is untouched.

Caveats, for the walk (nothing here fires the rule):
- The legacy figure on the Blurred idle walk is 32.6% against main's 14.5% (+18 points). The
  legacy count compares against a fixed 16 ms, and motion's frames sit at p50 14 ms / p90 16 ms
  because the screen now draws continuously; the deadline-based figure is 0%. If the rule meant
  the legacy line, it would fire, so the walk should look at the cover under Blurred by eye.
- Blurred scroll on motion is slower than main's (p50 14 vs 8 ms, p90 18 vs 11 ms, legacy 30.5% vs
  5.6%) while deadline-janky stays at 0%. The walk starts with the cover on screen, so the drift
  and the blur run together there. Under Default the same walk matches main (p50 7 vs 9 ms).
  Worth watching on the walk; not covered by the rule, which names the idle walk only.
- The idle windows include the cover reel's own auto-advance on both builds.

## Frosted masthead (record only)

Scroll walk on `feat/frosted-masthead` at 49588421: 948 frames, 0.00% janky, p50 11, p90 14,
p99 27 ms. Against main's Default scroll row (p50 9, p90 12, p99 25) it is a little slower,
inside the same band. Its merge is decided separately.

## State left on the tablet

`feat/motion` benchmark build, 0.120.0, installed over the others with `-r`; Default backdrop;
animator scale 1.0; profile A (no playback was started).
