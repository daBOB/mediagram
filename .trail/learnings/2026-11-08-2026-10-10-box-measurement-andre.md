---
title: 2026-10-10 box measurement
author: Andre
date: 2026-11-08
id: l-02720e67
scope: repo
status: active
touches:
  - plans/reports/frosted-masthead-261009-report.md
---

## Decided
2026-10-10, box 192.168.0.46, 0.120.3 vs 0.120.3 + frost (branch `measure/tv-frost`; its worktree was removed 2026-10-11, check it out again to rebuild), both release-signed with .dm: p90 medians off→on — Home at rest 42→46 ms, Movies rows 36→42, Series grid 34→46; janky 2.3–6.1% → 6.9–7.4%. Per the user's rule ("Measure, then frost": ship only if smooth) the TV bar stays unfrosted; the tablet frost ships (tablet scroll p90 14 vs main 12 ms, 0% janky). Full tables: `plans/reports/frosted-masthead-261009-report.md` on `feat/frosted-masthead`.

**Why:** the user asked "why is the top bar not frosted glass" and chose measure-first for the TV.

## Watch out
don't re-propose TV frost without new data. The untested lever is a smaller blur (18 dp unscaled; TV scale would be 13.5 dp). Separately, the box is already far over 16.7 ms p90 with no frost — a TV jank investigation is its own task (tv-self-updates-install-uncompiled, tv-box-4k-facts).

**System (cross-window) blur is not available on the box (verified 2026-10-16, 192.168.0.56).** `ro.surface_flinger.supports_background_blur` is unset (default off) and `dumpsys window` shows `mBlurEnabled=false`, with battery saver off and `disable_window_blurs` null. It is a read-only vendor property, so a
…

_From Claude Code's memory of this repo, last updated Oct 11._
