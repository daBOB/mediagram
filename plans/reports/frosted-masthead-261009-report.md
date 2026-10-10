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
