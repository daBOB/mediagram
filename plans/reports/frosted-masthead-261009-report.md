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

## Concerns
- Blur costs frame time: p50 +2 ms, p99 +9..15 ms; modern jank stays ~0 but legacy (16.7 ms deadline) goes from ~3% to ~20-27%. GPU time is flat; extra cost is CPU-side layer recording. Tuning options if wanted: `HazePerformanceMode`, a smaller radius, or `expandLayerBounds = false`. Not tried (out of scope, no user decision).
- Over the cover the 35% black tint is weaker than the web's blur-on-opaque feel only in that the hero is smeared, as intended; the status-bar strip is blurred too.
- Versions not bumped; not pushed.
