# Phase 01 on the box — lead verification

Box `192.168.0.35:5555`, benchmark build **0.82.0** from `feat/tv-web-look-chrome` (6d2dc721),
`compile -m speed`, profile "TV test", 2026-09-29 ~04:00–04:15. The worktree needed the gitignored
native libs copied from the main checkout first (`libmediagram_core.so` and FFmpeg's
`libffmpegJNI.so` — `verifyFfmpeg` refuses the build without the latter).

## Verified

- Chrome renders: icon rail (My List, Continue, Latest, Genres, Settings, System) + pills with
  counts (Home, Movies 896, Series 55, Anime 36, Documentaries 27, Tutorials 4, Collections 0),
  search, avatar initial, ⋮. Screens: scratchpad `w-*.png`.
- Pills scroll with the remote to Tutorials, Collections and search.
- Pill press switches the page (Series hero shown) and keeps the ring on the pill; Down enters
  the page (Series hero's Watch now); Back from content returns to the pills.
- Left at the left edge opens the full rail over the content (wordmark, counts, tally); Right
  closes it back to the pill.
- Title page round trip: poster → OK → title page (▶ Play focused) → Back returns focus to the
  same poster, 7/7 cycles.
- Launch focus: the last opened plate is restored (existing TV behaviour), not always Watch now.
- After leaving the player, focus lands on the played title's plate (now first in Continue).

## Measurements (same method as `baseline-home-return-measurements.md`)

| Repro | Run 1 | Run 2 | Run 3 |
|---|---|---|---|
| R1 poster → Back | Davey 0, skipped 0, janky 2 (8.3 %) | 0, 0, 3 (8.1 %) | (see below) |

No Davey frame / skipped-frame warning, janky % in line with the baseline (8–12 %).

## Open — intermittent self-exit

Twice in roughly twenty transitions the app left to the Google TV launcher without a Back:

1. R1 run 3 (only 2 frames rendered; not diagnosable, log already cleared).
2. Cover **Watch now → OK**, no further key: media session created then destroyed at once
   (`AudioMediaPlayerWrapper: The session was destroyed com.mediagram.android`), and ~5 s later
   WindowManager closed the task with `numActivities=0` (activity finished, no crash buffer entry).

Not reproduced in 8 further title-page cycles and one further 15 s Watch now play. Not seen on
0.81.1 in the valid baseline runs (the earlier 0.81.1 exit was an extra Back at the masthead).
Needs a root cause before this phase is merged: look for every path that finishes the activity or
pops past the root (the new Back chain's "rail → app exits" step, onBackPressedDispatcher calls,
player open failure → pop) and reproduce with `logcat -v time` running continuously.

## Not yet done

- R2/R3 measurements on 0.82.0 (R2 aborted by the self-exit guard).
- `TvCatalogScreenTest.kt` / `TvLibraryRemoteTest.kt` (androidTest, compile-only in check.sh) still
  describe the old masthead focus graph — rewrite before merge.
- Minor: the open rail shows "My List 0" but no count beside Continue watching.

## Follow-up — 0.82.1 (Back-gap fix), 2026-09-29 09:00–09:13

The debugger (`debugger-260929-0420-tv-self-close-report.md`) proved a regression: the chrome's
Back handlers were off on every fresh mount of Home until arrival focus landed, so a Back in that
gap fell through and closed the app. Fixed with a test that fails without it (0.82.1).

On the box, benchmark 0.82.1, "TV test", full `logcat -v time` running throughout:
- 15/15 cycles "bar → Down → Watch now → OK → 12 s playing → Back" plus one extra play: no
  self-close, 0 Davey, 0 skipped-frame lines, no ANR / foreground-service timeout.
- "The session was destroyed com.mediagram.android" appears after every normal Back from the
  player — it is not the symptom's signature.
- The Watch-now self-close (symptom 1) did not recur. Kept as a watch item for phase 04's walk.
- Reliable route to the cover: Up until focus sits in the bar (y < 200), then one Down.
