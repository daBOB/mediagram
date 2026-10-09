# Frosted masthead (phone/tablet) - 2026-10-09

Status: DONE_WITH_CONCERNS (jank, below)

## Haze
`dev.chrisbanes.haze:haze-blur:2.0.0` (published 2026-09-22). 2.0.1 (2026-09-29) is under two weeks old. Haze 2.0 split blur out of the core artifact, so `haze-blur` is the one to depend on; it pulls `haze`. It was built against Compose 1.12 (matches BOM 2026.06.01 resolution, build is clean) and Kotlin 2.4 metadata (reads fine on the project's 2.3.21). Blur is enabled by default only on SDK >= 31 (`HazeBlurDefaults.android.kt`); minSdk 24 builds and resolves.

## Changes
- `gradle/libs.versions.toml`, `ui-mobile/build.gradle.kts`: dependency.
- New `ui/chrome/Frost.kt`: `frostAvailable(sdk)`, `rememberFrost()` (null below API 31), `frostSource`, `frostedBackdrop`, `frostHairline`.
- `DepartmentsBar.kt`: `barBackground(solid, blend, frosted)`; frosted scrolled end = paper at 72% (web), cover end unchanged (`OverCoverBg`). Blur 18.dp sigma (CSS blur(18px) is also a sigma and 1 CSS px = 1dp, so unscaled), saturation 1.15 colour matrix, noise 0 (Haze defaults to 0.15; web has none), hairline 1dp at 8% ink scaled by blend (transparent over cover, as the web).
- `LibraryHome.kt`: content box is the haze source, EXPANDED only.
- Below API 31: no haze modifier, no hairline, opaque solid end: unchanged look.
- `TvDepartmentsBar.kt`: kdoc sentence that the TV bar stays unfrosted until the box is measured.
- Not frosted: the compact (phone portrait) header, which scrolls away rather than sitting over content; DepartmentsBar is EXPANDED-only.

## Tests
RED: `DepartmentsBarColorTest` rewritten first (new signature, 72% value, `frostAvailable`); compile failed. GREEN after implementation. Below-API-31 check is the pure `frostAvailable(30/31)` test plus `rememberFrost()` gating on it; a Robolectric `@Config(sdk=30)` composition test would only re-test that one-line gate, so not added. Full check.sh gradle set plus `:app:assembleDebug` pass (lint: no new errors).

## Screenshots (dir /tmp/claude-1000/-home-andre-Workspace-mediagram/35591186-acb4-4a07-9b7f-c6e887a63688/scratchpad/glass/)
after-A-cover.png, after-B-posters.png; composites (top = before, bottom = after): cmp-cover-before-after.png, cmp-posters-before-after.png. Posters are visibly smeared under the bar.

## gfxinfo (8 swipes down/up on Home, tablet, two runs each)
| | frames | janky (modern) | legacy janky | p50 | p90 | p95 | p99 | gpu p99 |
|---|---|---|---|---|---|---|---|---|
| before | 1634 / 1636 | 0 / 0 | 3.4% / 3.6% | 10 | 14 / 13 | 14 | 19 | 12 / 11 |
| after | 1394 / 1414 | 5 / 0 | 26.8% / 21.1% | 12 | 17 / 16 | 20 / 18 | 34 / 28 | 12 / 12 |

## Tuning round (same 8-swipe protocol, tablet, debug build; legacy-janky % / p50 / p99 ms; run1, run2)
The first baseline (3.4%) was the previously installed 0.118.1 build. Re-measured on the build under test with frost forced off, the "baseline" is not 3.5%: it is very noisy on this tablet (frame counts per run swing 500-1400, so some swipes barely render).

| variant | run1 | run2 |
|---|---|---|
| original installed build (first baseline) | 3.4 / 10 / 19 | 3.6 / 10 / 19 |
| this build, frost off (3 runs) | 27.0 / 11 / 53 | 18.6 / 10 / 31 ; 15.4 / 12 / 44 ; 27.6 / 12 / 36 |
| frost 18dp, Adaptive (first measure) | 26.8 / 12 / 34 | 21.1 / 12 / 28 |
| frost 18dp, Adaptive (re-measure x2) | 18.4 / 12 / 48 ; 21.1 / 14 / 46 | 44.9 / 13 / 42 ; 49.1 / 14 / 57 |
| Performance mode | 46.0 / 14 / 200 | 39.5 / 13 / 46 |
| Balanced mode | 17.4 / 13 / 200 | 30.3 / 12 / 36 |
| radius 12dp | 41.2 / 14 / 69 | 36.3 / 12 / 36 |
| radius 8dp | 16.1 / 13 / 38 | 46.8 / 14 / 46 |
| expandLayerBounds=false | 17.2 / 13 / 46 | 29.8 / 12 / 36 |

Not tried: combined variants, and gating the effect while the list is at the top. No single knob moved the numbers outside the noise, so a combination is unlikely to; and the dominant cost is capturing the whole content area into a layer every frame (`hazeSource`), which none of these shrink. Gating at the top only helps when not scrolling, which is not what is measured.

Result: frost costs roughly +2 ms p50 and somewhat worse tail versus the same build with frost off; nothing tested gets it under that. Committed settings stay at Haze defaults, 18dp. The decision (accept, or drop to a cheaper look) is the user's.

## Concerns
- Blur costs frame time (see tuning table); modern jank stays ~0. The first before/after in this report compared against a different installed build and overstates the gap.
- Over the cover the 35% black tint is weaker than the web's blur-on-opaque feel only in that the hero is smeared, as intended; the status-bar strip is blurred too.
- Versions not bumped; not pushed.
