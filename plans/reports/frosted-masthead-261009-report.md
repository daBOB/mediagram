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

## TV (2026-10-10)

Status: built, not measured. The box measurement below decides whether it ships.

### What changed (`f4522beb`)
- **Shared, not copied.** `Frost.kt` moved from `ui-mobile` to `ui-common/src/main/kotlin/ui/common/chrome/Frost.kt`, beside `CoverBlend.kt`, along with `barBackground` and `OverCoverBg`, which both bars had defined separately. `ui-common` is the module that both bars, and only they, depend on. `core:designsystem` is lower, but `feature:setup` depends on it too, so putting the frost there would pull Haze into a module that draws no bar. Haze is declared `api` in `ui-common`, because both bars take its `HazeState`. `ui-mobile` no longer declares it. The test moved too: `DepartmentsBarColorTest` became `ui-common/.../chrome/BarBackgroundTest.kt`.
- **TV.** `TvLibraryChrome` calls `rememberFrost()`, marks its content box as the source, and passes the frost to `TvDepartmentsBar`. The bar applies `frostedBackdrop`, `barBackground(..., frosted)` and `frostHairline`, the same chain the tablet uses, with the web's numbers: 18dp sigma, saturate 1.15, 72% paper at the solid end, and an 8% hairline that is transparent over a cover. The frost is on for every tab. On the web the masthead frosts on every page, because rows always scroll up under it, and the tablet does the same. Over Home's cover and a department hero (blend 0) it blurs the picture under the 35% black tint. On other tabs (blend 1) it shows 72% paper over a blur of the rows. Below API 31 `rememberFrost()` returns null, and the bar looks as it does on 0.120.0. Batch 1's layout is unchanged: pills fill the row, search and the avatar sit at the far end, and the edge fade still works.
- `FrostKey`, a semantics property holding the shared `HazeState`, is set by both `frostSource` and `frostedBackdrop`. Tests read the frost from it rather than from pixels, as they already do with `EdgeFadesKey`.
- Kdoc: the TV bar is no longer described as "unfrosted until measured". Where the TV chrome said "opaque" for blend 1, it now says "solid".
- **Switch: none.** The comparison is the published 0.120.0 (frost off) against this branch (frost on), so no debug flag or system property exists in either build.

### Tests
- New `ui-tv/.../chrome/TvDepartmentsBarFrostTest.kt` (Robolectric, sdk 35, 960x540dp):
  - Over a cover (blend 0) and on a tab with no hero (blend 1), the bar's `FrostKey` state is the same instance as the page box's. So the bar is frosted, and what it blurs is the page.
  - A bar handed no frost (what the chrome passes below API 31) carries no `FrostKey`.
  - The API 30/31 gate stays in `BarBackgroundTest`. Robolectric only has sdk 35 cached here, and every test in the project runs at 35.
- Mutation check: with `.frostSource(frost)` removed from `TvLibraryChrome`, 2 of the 3 tests fail.
- `./gradlew :ui-common:testDebugUnitTest :ui-tv:testDebugUnitTest :ui-mobile:testDebugUnitTest` (rerun): 85 + 598 + 393 tests, 0 failed.
- `:ui-common:lintDebug :ui-tv:lintDebug :ui-mobile:lintDebug`: pass.
- `:app:assembleRelease` (R8, release key) builds.
- Not done: a pixel check of the blur. Robolectric's native canvas is not the GPU path Haze takes.

### Measurement protocol (box; not run)
The box is `192.168.0.46:5555`, a Hisense/Google TV on Android 14 with a 1080p UI at density 2.0. Never press OK in Settings or a menu, and never choose "Start over". The only OK in these walks is on a department pill, which opens that tab and changes nothing.

**0. Before.**
- No other session should be driving the box.
- Connect with `adb connect 192.168.0.46:5555`. If that fails, run `nmap -p 5555` across the LAN.
- Keep the TV on for both builds, so the box has its 4K EDID. If it is asleep, send `adb -s 192.168.0.46:5555 shell input keyevent KEYCODE_WAKEUP`.
- Measure both builds in one sitting, on the same profile.

```sh
D=$HOME/frost-tv-261010; mkdir -p $D
k() { for key in "$@"; do adb -s 192.168.0.46:5555 shell input keyevent "$key"; sleep 0.4; done; }
n() { printf "$1 %.0s" $(seq $2); }   # n KEYCODE_DPAD_DOWN 12
dump() { adb -s 192.168.0.46:5555 shell dumpsys gfxinfo com.mediagram.android | grep -E 'Total frames|Janky frames|percentile' | tee "$D/$1.txt"; }
dexopt() { adb -s 192.168.0.46:5555 shell dumpsys package dexopt | grep -A3 '\[com.mediagram.android\]'; }
```

**1. Baseline state, and keep the published APK for the restore.**
- `dexopt` must show `speed-profile` / `install-dm`, which is what a channel update installs since 0.119.0.
- The `base.dm` pull may be refused. Step 5 covers both cases.

```sh
adb -s 192.168.0.46:5555 shell dumpsys package com.mediagram.android | grep -m1 versionName   # 0.120.0
dexopt
P=$(adb -s 192.168.0.46:5555 shell pm path com.mediagram.android | sed -n 's/^package://p' | tr -d '\r')
adb -s 192.168.0.46:5555 pull "$P" $D/base.apk
adb -s 192.168.0.46:5555 pull "${P%base.apk}base.dm" $D/base.dm
```

**2. One round = three walks.** Each walk starts with `dumpsys gfxinfo com.mediagram.android reset`. These are the walks the earlier box measurements used.
- First take one `adb -s 192.168.0.46:5555 exec-out screencap -p > $D/pills.png` to confirm the pills read Home, Movies, Series. Adjust the RIGHT/LEFT counts if they do not.
- If a profile picker appears at launch, stop and choose the profile by hand. It is not part of the walk.

```sh
round() {  # round <build> <run>
  adb -s 192.168.0.46:5555 shell am force-stop com.mediagram.android
  adb -s 192.168.0.46:5555 shell am start -W -n com.mediagram.android/.MainActivity; sleep 15
  # W1 Home at rest, remote on the Home pill so the cover turns (two 1.4 s crossfades in 20 s)
  k KEYCODE_DPAD_UP KEYCODE_DPAD_UP; sleep 2
  adb -s 192.168.0.46:5555 shell dumpsys gfxinfo com.mediagram.android reset; sleep 20; dump $1-home-$2
  # W2 Movies rows: the hero, then rows scrolling up under the bar, and back to the pill
  k KEYCODE_DPAD_RIGHT KEYCODE_DPAD_CENTER; sleep 3
  adb -s 192.168.0.46:5555 shell dumpsys gfxinfo com.mediagram.android reset
  k $(n KEYCODE_DPAD_DOWN 12) $(n KEYCODE_DPAD_UP 12); sleep 1; dump $1-movies-$2
  # W3 a department hero page (Series' grid): blend 0 -> 1 -> 0 as the hero scrolls away and back
  k KEYCODE_DPAD_RIGHT KEYCODE_DPAD_CENTER; sleep 3
  adb -s 192.168.0.46:5555 shell dumpsys gfxinfo com.mediagram.android reset
  k $(n KEYCODE_DPAD_DOWN 6) $(n KEYCODE_DPAD_UP 6); sleep 1; dump $1-series-$2
}
round base warm; for r in 1 2 3; do round base $r; done   # the warm round is discarded
```

**3. Install this branch's release build** (`0.120.0`, same versionCode, release key, so data is kept). AGP sends `app-release.dm` with the APK, so it lands compiled, like a channel update. Because the channel's pinned release has the same versionCode, the branch build's self-updater finds nothing newer and stays installed for the run.

```sh
cd /home/andre/Workspace/mediagram-glass/android && ANDROID_SERIAL=192.168.0.46:5555 ./gradlew :app:installRelease
dexopt   # must read speed-profile / install-dm; if it reads verify, stop: the numbers would not compare
```

**4. Measure the branch.** Run `round frost warm; for r in 1 2 3; do round frost $r; done`.

**5. Restore the published 0.120.0** with the same key, so data is kept:
- With `base.dm`: `adb -s 192.168.0.46:5555 install-multiple -r $D/base.apk $D/base.dm`
- Without it: `adb -s 192.168.0.46:5555 install -r $D/base.apk`. A plain `install -r` lands at `verify`, so then:
  - launch once: `adb -s 192.168.0.46:5555 shell am start -W -n com.mediagram.android/.MainActivity; sleep 15`
  - compile: `adb -s 192.168.0.46:5555 shell cmd package compile -m speed-profile -f com.mediagram.android`
- Check that `dexopt` reads `speed-profile`.
- Check that `adb -s 192.168.0.46:5555 shell sha256sum "$P"` matches `sha256sum $D/base.apk`. Re-read `$P` with `pm path`, because the install moves the directory.
- Delete `$D/*.apk`.

**Ship rule.** For each walk, take the baseline's range over its three runs and the branch's median.
- The frost stays if, on all three walks, the branch's median 90th percentile, its median `Janky frames` share and its median `Janky frames (legacy)` share are each no higher than the baseline's worst run.
- Otherwise, record the table here: frames, both janky shares, and p50/p90/p99 plus GPU p50/p90 per run, which matter most on this GPU-bound box. The user decides. Dropping the TV frost alone is one line: `rememberFrost()` becomes `null` in `TvLibraryChrome`.
- Expect W1 to be the walk that fails, if any does. `hazeSource` records the whole page, including the crossfading cover, into a layer on every frame. Home's crossfade already costs ~22 ms of GPU per frame.

**Bias to know about.** The checked-in baseline profile has no rules for `ui.common.chrome.FrostKt` or Haze, or for `TvDepartmentsBar`, whose signature gained `frost`. They JIT during the warm round. If the result is borderline, regenerate the profile before deciding.

### Concerns
- `TvLibraryChrome.kt` is 257 lines (253 on main). It was already over the 200-line limit, and this change does not split it.
- Not measured: the TV verdict is the box's.
- Version not bumped (that happens at merge); not pushed; no device touched.

## TV measurement (2026-10-10)

Status: measured on the box. **The frost fails the ship rule on all three walks.** The user decides; dropping the TV frost alone is still one line (`rememberFrost()` → `null` in `TvLibraryChrome`).

### Setup
- Box `192.168.0.46:5555`, TV on, 3840x2160 at 59.94 Hz, UI override 1920x1080 at 320 dpi. Profile "andre" (no picker at launch), the same for both builds, measured 14:56-15:16 in one sitting.
- **Off:** the installed sideloaded 0.120.3 release (versionCode 120003, `speed-profile` / `install-dm`), measured first, as it was.
- **On:** branch `measure/tv-frost` (worktree `../mediagram-tvfrost`) = `feat/tv-parity` 8a30b7e1 + `a07fbb40` + `f4522beb`. The second pick conflicted in `TvDepartmentsBar.kt` (imports; the row's padding, where tv-parity's `TvContentGutter` ends were kept and `frostHairline` added) and `TvLibraryChrome.kt` (the `top` comment and `val frost = rememberFrost()` beside tv-parity's padding, with `TvContentGutter` at both ends). Diff against 8a30b7e1 is the frost chain only. Tests: 85 + 624 + 393, 0 failed. Release built as `release-android.sh` lines 1-37 (not published), cert 5840181d…f576, API 31+ `.dm` taken from `output-metadata.json`, installed with `install-multiple -r`, `dexopt` read `speed-profile` / `install-dm`, versionName 0.120.3.
- Walks as in the protocol (W1 Home at rest 20 s on the Home pill; W2 Movies 12 down / 12 up; W3 Series 6 down / 6 up); one warm round discarded, then three. Changes, and notation:
  - Before each OK, a `uiautomator dump` confirmed focus was on exactly the Movies or Series pill; the round would abort otherwise (it never did, 8 of 8 checks per build). The launch wait also polls the dump until the library has loaded (the first cold start was still on "Loading your library…" at 15 s). Both builds ran the identical script.
  - Unit: ms; "janky" is `Janky frames`, "legacy" is `Janky frames (legacy)`.
- Restore: plain 0.120.3 apk + dm reinstalled with `install-multiple -r`; versionName 0.120.3, `speed-profile` / `install-dm`, and the box's `base.apk` sha256 equals the kept one (80bfe66a…7033).

### Results
**W1 Home at rest**

| build | run | frames | janky % | legacy janky % | p50 | p90 | p99 | GPU p50 | GPU p90 |
|---|---|---|---|---|---|---|---|---|---|
| off | 1 | 163 | 6.13 | 99.39 | 34 | 42 | 69 | 10 | 14 |
| off | 2 | 162 | 6.17 | 99.38 | 34 | 42 | 65 | 10 | 14 |
| off | 3 | 163 | 5.52 | 98.16 | 34 | 40 | 69 | 10 | 14 |
| off | **median** | 163 | 6.13 | 99.38 | 34 | 42 | 69 | 10 | 14 |
| on | 1 | 159 | 6.92 | 91.19 | 25 | 46 | 81 | 9 | 21 |
| on | 2 | 155 | 7.74 | 87.74 | 25 | 48 | 89 | 9 | 19 |
| on | 3 | 157 | 7.01 | 92.99 | 25 | 44 | 89 | 9 | 18 |
| on | **median** | 157 | 7.01 | 91.19 | 25 | 46 | 89 | 9 | 19 |

**W2 Movies rows**

| build | run | frames | janky % | legacy janky % | p50 | p90 | p99 | GPU p50 | GPU p90 |
|---|---|---|---|---|---|---|---|---|---|
| off | 1 | 296 | 2.36 | 76.01 | 24 | 38 | 69 | 7 | 11 |
| off | 2 | 304 | 1.97 | 38.16 | 18 | 30 | 48 | 7 | 11 |
| off | 3 | 294 | 3.4 | 72.45 | 24 | 36 | 61 | 7 | 11 |
| off | **median** | 296 | 2.36 | 72.45 | 24 | 36 | 61 | 7 | 11 |
| on | 1 | 296 | 7.43 | 77.36 | 27 | 40 | 81 | 10 | 13 |
| on | 2 | 299 | 8.36 | 85.28 | 28 | 42 | 89 | 10 | 14 |
| on | 3 | 297 | 7.41 | 80.13 | 27 | 44 | 77 | 10 | 13 |
| on | **median** | 297 | 7.43 | 80.13 | 27 | 42 | 81 | 10 | 13 |

**W3 Series hero grid**

| build | run | frames | janky % | legacy janky % | p50 | p90 | p99 | GPU p50 | GPU p90 |
|---|---|---|---|---|---|---|---|---|---|
| off | 1 | 352 | 1.7 | 90.91 | 24 | 32 | 57 | 7 | 11 |
| off | 2 | 348 | 2.3 | 87.93 | 24 | 34 | 57 | 7 | 11 |
| off | 3 | 352 | 2.27 | 88.35 | 24 | 36 | 57 | 7 | 13 |
| off | **median** | 352 | 2.27 | 88.35 | 24 | 34 | 57 | 7 | 11 |
| on | 1 | 365 | 5.21 | 90.41 | 28 | 40 | 77 | 9 | 12 |
| on | 2 | 360 | 6.94 | 94.17 | 29 | 48 | 69 | 10 | 13 |
| on | 3 | 362 | 7.46 | 94.75 | 30 | 46 | 69 | 9 | 13 |
| on | **median** | 362 | 6.94 | 94.17 | 29 | 46 | 69 | 9 | 13 |

### Verdict
The protocol's rule: on each walk, the frost build's median p90, janky share and legacy-janky share must each be no higher than the baseline's worst run.

| walk | p90 median off → on (off worst) | janky % median off → on (off worst) | legacy % median off → on (off worst) | rule |
|---|---|---|---|---|
| W1 Home | 42 → 46 (42) | 6.13 → 7.01 (6.17) | 99.38 → 91.19 (99.39) | fails (p90, janky) |
| W2 Movies | 36 → 42 (38) | 2.36 → 7.43 (3.40) | 72.45 → 80.13 (76.01) | fails (all three) |
| W3 Series | 34 → 46 (36) | 2.27 → 6.94 (2.30) | 88.35 → 94.17 (90.91) | fails (all three) |

The fallback rule (p90 ≤ 16.7 ms or within 2 ms of off, janky within 5 points) fails too: p90 is +4/+6/+12 ms, and Movies' janky share is +5.1 points.

Reading: on scrolling walks the frost costs about +3-5 ms p50, +6-12 ms p90 and +12-28 ms p99, and roughly triples the modern janky share (≈2% → ≈7%). GPU time rises only 1-3 ms at p50/p90 there, so most of the cost is on the CPU/RenderThread side (recording the page into Haze's layer each frame), not the blur shader. W1 is mixed: p50 fell 34 → 25 and legacy jank fell, while p90/p99 and GPU p90 (14 → 19) rose, so the crossfade frames under the blur are the slow ones, as the protocol expected.

Decision: the TV bar stays unfrosted (the user's rule: ship only if smooth); the tablet frost ships.

Tablet, release build, for comparison (motion's tablet benchmark, `/home/andre/Workspace/mediagram-motion/plans/261010-0112-android-motion-parity/reports/frame-times-261010-motion-report.md`):

| build | backdrop | walk | frames | janky % | p50 | p90 | p99 |
|---|---|---|---|---|---|---|---|
| frosted-masthead | Default | scroll | 948 | 0.00 | 11 | 14 | 27 |
| main | Default | scroll | 880 | 0.11 | 9 | 12 | 25 |

### Odd
- The baseline itself is far from 16.7 ms on this box (p90 30-42 ms, legacy jank 38-99%), so "smooth" here is relative to an already heavy page.
- The frost code is not in the baseline profile (see "Bias" above). The warm round was discarded, but if the user wants the frost, regenerate the profile and re-run before deciding; the gap is wide enough that it is unlikely to close.
- The TV blur is `18.dp`, the web's 18 px unscaled, while feat/tv-parity sizes everything else by `tvDp` (0.75), which would make it 13.5 dp. Not tried; the tablet tuning found the radius did not move the cost.
- The tvfrost checkout lacked the gitignored FFmpeg decoder libs (`verifyFfmpeg` failed). They were copied from `../mediagram-tv`; the armeabi-v7a lib is byte-identical to the one in the off APK.
- Series showed 11010 episodes in the off rounds and 11015 in the on rounds: the other uploader published mid-session. Five more episodes in a 173-show page is noise.
- Raw dumps, screenshots and scripts: `/tmp/claude-1000/-home-andre-Workspace-mediagram/35591186-acb4-4a07-9b7f-c6e887a63688/scratchpad/frost-tv-261010/`; frost APK and dm in `…/scratchpad/tv-frost-0.120.3/`.
