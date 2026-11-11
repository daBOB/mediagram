---
title: "A channel self-update installs the app at dexopt \"verify\" (no baseline profile applied),…"
author: Andre
date: 2026-11-08
id: l-ca10dda8
scope: repo
status: active
touches:
  - scripts/release-android.sh
---

## Decided
Measured on the TV box 2026-10-08 (0.117.4, self-updated 2026-10-06 13:12): `dumpsys package dexopt` showed `[status=verify] [reason=install]` two days after install. The APK carries `assets/dexopt/baseline.prof` and profileinstaller had written a profile (a manual `cmd package compile -m speed-profile -f` succeeded), but the box's background dexopt never ran for the app. It sleeps via HDMI-CEC and is busy when awake.

Same walks, cold start, verify → speed-profile: Movies median frame 26 → 18 ms, p99 77 → 44 ms, frames over 16.7 ms 75% → 28%; Home p99 133 → 69 ms.

`./gradlew :app:installRelease` lands as `speed-profile` / `reason=install-dm`, because AGP sends its dex-metadata file with the APK. The self-update (`core/update/.../PackageApkInstaller.kt`) writes only `base.apk` into the session, and `scripts/release-android.sh` publishes only the APK.

**Why:** "navigation is not smooth" on the box was mostly this. It began when the box moved from adb installs plus `compile -m speed` to self-updates (0.95, 2026-10-02).

## Watch out
before judging TV smoothness, check the dexopt status first. Compare frame stats only between builds with the same compile state (`dumpsys gfxinfo <pkg> reset`, walk, dump). Remaining cost once compiled is GPU: ~14 ms per frame navigating Movies, ~22 ms per frame during Home's cover crossfade (UI thread idle). Related: android-tv-self-update, tv-box-4k-facts.
…

_From Claude Code's memory of this repo, last updated Oct 8._
