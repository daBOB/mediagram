# Phase 08 — Docs, version, box walk and frame times

## Context
- Spec: § Device verification, § Delivery.
- CLAUDE.md § Surface Parity: a deliberate difference is written down where someone will find it; § Versioning: bump all three manifests in step, by pattern (another session commits to main).
- The frost measurement protocol this reuses: `plans/reports/frosted-masthead-261009-report.md` on `feat/frosted-masthead` (§ the box protocol, steps 0–4).
- The box: `192.168.0.46:5555`, sleeps with the TV ("No route to host"), runs a sideloaded 0.120.5. Installing this branch there raises its versionCode, so the next channel release must be above it.

## Task 8.1: Docs and version

**Files:**
- Modify: `docs/system-architecture.md`, `docs/project-changelog.md`, `plans/261011-0052-tv-themes/plan.md`
- Modify: `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` (version)

- [ ] **Step 1: Choose the version**

```bash
for ref in main feat/motion feat/tv-parity HEAD; do printf "%s " $ref; git show $ref:android/app/build.gradle.kts | grep -m1 -o 'versionName = "[^"]*"'; done
```

Take the highest minor among them, plus one, patch 0 (with main at 0.120.0, motion at 0.121.0 and tv-parity at 0.120.5 that is **0.122.0**). Bump by pattern, never by the exact old string:

```bash
V=0.122.0
sed -i -E "0,/^version = \"[0-9]+\.[0-9]+\.[0-9]+\"/s//version = \"$V\"/" Cargo.toml
sed -i -E "0,/\"version\": \"[0-9]+\.[0-9]+\.[0-9]+\"/s//\"version\": \"$V\"/" web/package.json
sed -i -E "s/versionName = \"[0-9]+\.[0-9]+\.[0-9]+\"/versionName = \"$V\"/" android/app/build.gradle.kts
git diff --stat   # exactly three files, one line each
```

If `Cargo.lock` carries the workspace crates' version, run `cargo metadata --format-version 1 >/dev/null` (refreshes it without fetching) and include it.

- [ ] **Step 2: Write the docs**

`docs/system-architecture.md`, in its TV section (find it with `grep -n "TV\|television" docs/system-architecture.md | head`), add a subsection:

```markdown
### TV themes

Settings › Appearance › Theme picks one of three TV styles (`designsystem.TvStyle`,
stored per device under `appearance_settings/style`): **mediagram** (the default),
**Apple TV style** and **Google TV style**. `ui.tv.TvTheme` provides it as
`LocalTvStyle` and builds the scheme from the style's `TvLook` neutrals and the type
from its face (`TvType` reads each role through `inTvStyle()`; `TvType.Base` holds
mediagram's). Focus (`TvFocus`), colours (every TV colour reads the scheme — pinned by
`TvColoursComeFromTheSchemeTest`) and type follow the style everywhere; layout differs
in three places only: the chrome (`TvDepartmentsBar` + rail / `TvCentredTabBar` /
`TvTopBar`; no rail outside mediagram, its rows in the avatar's menu, every rail-row
focus request through `TvChromeFocus.railTarget`), the Home cover (`TvCoverSlide` /
`TvInsetCardSlide`) and rows of titles (16:9 stills, `TvRowCards.kt`; walls stay posters).
No style blurs anything: TV blur measured too slow on the box (2026-10-10).

**Deliberate difference:** the phone, the tablet and the web have no themes yet. They
store the setting (phone/tablet) or ignore it; each gets its own design round, TV first
at the user's choice (2026-10-10).
```

`docs/project-changelog.md`, newest first, in the file's existing entry format:

```markdown
## 0.122.0 — 2026-10-11

- **TV themes.** Settings › Appearance › Theme: mediagram (default), Apple TV style
  (black, Geist, a centred tab bar, wide cards that lift on focus) and Google TV style
  (dark grey, Google Sans Flex, a top bar with avatar and gear, an inset rounded cover,
  wide cards with a white ring). Row and tab names are the same in every style.
- Shows carry their episodes' backdrop, so a row of shows has a still to show.
- Fonts: Geist 700 and Google Sans Flex 400–700 as static Latin cuts (OFL).
```

`plan.md`: set phases 01–07 to `done` (08 stays `in progress` until Task 8.2 ends).

- [ ] **Step 3: Check and commit**

Run: `scripts/check.sh` — expected green (fix anything it reports before committing).

```bash
git add Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/system-architecture.md docs/project-changelog.md plans/261011-0052-tv-themes/plan.md
git commit -m "chore: 0.122.0 — three TV themes"
```

## Task 8.2: The box — walk each style, measure frames

Only with nobody else on the box: if `adb -s 192.168.0.46:5555 shell dumpsys activity activities | grep -m1 mResumedActivity` shows the player (`PlayerActivity` or a playing frame) or another app, stop and ask the user. The TV must be on (`KEYCODE_WAKEUP` if the box answers but sleeps).

**Files:**
- Create: `plans/261011-0052-tv-themes/reports/box-walk-and-frame-times-261011-report.md`
- Modify: `plans/261011-0052-tv-themes/plan.md` (phase 08 done, Review section)

- [ ] **Step 1: Build the release APK and its profile without publishing**

Write this to the session scratchpad as `build-tv-release.sh` (a hook blocks shell lines containing the path segment `build`, so it runs as a file):

```bash
#!/usr/bin/env bash
# Build the release APK + API31 .dm from a checkout without publishing; copy both to $2.
set -euo pipefail
cd "$1"
EXPECTED_CERT=5840181d3da5f44c9f0185bf4ac3345e5fe08786347350db04aaa6e69df4f576
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
BUILD_TOOLS=$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)
export PATH="$BUILD_TOOLS:$PATH"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$SDK/ndk/28.2.13676358}"
OUT=android/app/build/outputs/apk/release
rm -rf "$OUT"/*.apk "$OUT"/baselineProfiles
scripts/build-android-core.sh
(cd android && ./gradlew -q :app:assembleRelease)
CERT=$(apksigner verify --print-certs "$OUT/app-release.apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
[ "$CERT" = "$EXPECTED_CERT" ] || { echo "WRONG CERT: $CERT"; exit 1; }
DM=$OUT/$(jq -r '.baselineProfiles[] | select(.minApi == 31) | .baselineProfiles[0]' "$OUT/output-metadata.json")
cp "$OUT/app-release.apk" "$2/app-release.apk"; cp "$DM" "$2/app-release.dm"
aapt2 dump badging "$2/app-release.apk" | grep -o "versionCode='[0-9]*' versionName='[^']*'"
```

A fresh checkout lacks the FFmpeg audio libraries: copy `android/core/ffmpeg/src/main/jniLibs/` from `../mediagram-tv` first if it is missing. Run `bash <scratch>/build-tv-release.sh /home/andre/Workspace/mediagram-themes <scratch>/tv-themes-0.122.0`. Expected: `versionCode='122000' versionName='0.122.0'`.

- [ ] **Step 2: Install, keeping data**

```bash
adb connect 192.168.0.46:5555
adb -s 192.168.0.46:5555 install-multiple -r <scratch>/tv-themes-0.122.0/app-release.apk <scratch>/tv-themes-0.122.0/app-release.dm
adb -s 192.168.0.46:5555 shell dumpsys package dexopt | grep -A3 '\[com.mediagram.android\]'   # speed-profile / install-dm
```

- [ ] **Step 3: Walk each style**

Rules: navigate only (D-pad, Back; OK on pills, plates, the avatar, the menu's rows and the rail's Settings row). The one setting this walk changes is Settings › Appearance › Theme. Check the avatar shows **T** before anything plays (nothing in this walk plays). Before each OK, confirm where focus is with `adb -s 192.168.0.46:5555 shell uiautomator dump /sdcard/ui.xml && adb -s 192.168.0.46:5555 pull /sdcard/ui.xml <scratch>/ui.xml` and the `focused="true"` node.

For each of mediagram → Apple TV style → Google TV style:
1. Reach Settings: mediagram — Left to the rail, down to Settings, OK; Apple/Google — Up to the bar, to the avatar, OK, down to Settings, OK.
2. In Settings: the Appearance section, Right into it, down to Theme, onto the style's swatch, **OK**. Back to the catalogue.
3. Screenshot Home (`adb -s 192.168.0.46:5555 exec-out screencap -p > <scratch>/theme-<style>-home.png`), Movies (Up, Right, OK on the pill, Down twice), My List (mediagram: the rail; Apple/Google: avatar → My List), the avatar menu, and Settings.
4. Note anything that reads wrong: focus lost, a control unreachable, text clipped, a colour left from another style.

- [ ] **Step 4: Frame times**

Define the frost report's helpers (`k`, `n`, `dump`, `round`) with `D=<scratch>/themes-frames-261011`. For each style, set it (Step 3.1–3.2), then `round <style> warm; for r in 1 2 3; do round <style> $r; done` — W1 Home at rest, W2 Movies rows, W3 Series hero, the warm round discarded. Take each walk's median p90 per style.

Rule (spec): if Apple's median p90 is more than 2 ms above mediagram's on any walk, drop the shadow and keep the lift: `focusedCardGlowOf` returns `null` for every style, `TvFocusStylesTest.appleCardsLiftWithAShadowAndNoBorder` becomes `appleCardsLiftWithNoBorderAndNoShadow` asserting `assertNull(focusedCardGlowOf(TvStyle.APPLE.look))`, the `TvFocus` KDoc's Apple sentence drops "on a soft shadow", commit `perf(tv): Apple TV style's lift without its shadow`, rebuild, reinstall and measure Apple once more. Google and mediagram add no drawing cost; record their numbers anyway.

- [ ] **Step 5: Put the box back**

Set Theme back to **mediagram** (Step 3.1–3.2) and leave the remote on Home. The box keeps 0.122.0 installed: say in the report that the next channel release must be **≥ 0.122.1**.

- [ ] **Step 6: Report and close the plan**

The report holds: the build (versionCode, cert, dexopt), a table per walk (style × run: frames, janky %, p50, p90, p99, GPU p90) with medians, the rule's verdict, the screenshots' paths, what read wrong per style, and "Unresolved questions". `plan.md`: phase 08 `done`, and a `## Review` section of three to five lines (what shipped, the measurement verdict, open items).

```bash
git add plans/261011-0052-tv-themes
git commit -m "docs(plan): TV themes walked and measured on the box"
```

## Success criteria
- 0.122.0 in all three manifests; docs name the themes and the written difference.
- Each style walked on the box with screenshots; frame times recorded; the shadow rule applied.
- The box ends on mediagram, on 0.122.0, with the release floor written down.
