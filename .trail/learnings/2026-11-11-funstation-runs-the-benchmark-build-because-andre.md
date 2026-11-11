---
title: "Funstation runs the benchmark build, because the release build is ARM-only and cannot update it"
author: Andre
date: 2026-11-11
branch: main
cost: { minutes: 5, tokens: 30460 }
id: l-770dd2e5
scope: repo
status: active
src: agent
touches:
  - Cargo.lock
  - Cargo.toml
  - android/app/build.gradle.kts
  - android/ui-tv/.claude/agent-memory/code-reviewer/MEMORY.md
  - android/ui-tv/.claude/agent-memory/code-reviewer/robolectric-misses-tv-bring-into-view.md
  - android/ui-tv/src/main/kotlin/ui/tv/catalog/TvBarClearanceBringIntoView.kt
  - android/ui-tv/src/main/kotlin/ui/tv/catalog/TvCatalogBlend.kt
  - android/ui-tv/src/main/kotlin/ui/tv/chrome/TvCentredTabBar.kt
  - android/ui-tv/src/main/kotlin/ui/tv/chrome/TvDepartmentsBar.kt
  - android/ui-tv/src/main/kotlin/ui/tv/chrome/TvLibraryChrome.kt
---

## Decided
- 2026-11-12: funstation (an x86_64 mini PC running Android TV in WayDroid) stays on the media app's benchmark build, installed by hand after each release. The release build packs only arm64-v8a and armeabi-v7a, which keeps the channel download small for the box, phones and tablets.

## Tried and ruled out
- Installing the release build on funstation so it would update itself from the channel. Android accepted it, but chose arm64-v8a and would have run the whole app, Rust core and FFmpeg included, through WayDroid's ARM translation. Rolled back to a benchmark build of the same version in minutes, with the data kept.

## Watch out
- Before installing anything on funstation, check `dumpsys package com.mediagram.android | grep primaryCpuAbi` afterwards: it must say x86_64.
- The benchmark and release builds share the release key (the old debug key), so either installs over the other with sign-in kept. A benchmark build of the same version installs over a release with `pm install -r`.

In the code:
- android/app/build.gradle.kts: release `ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }`, benchmark `abiFilters.clear()`
- deploy to funstation: `./gradlew :app:assembleBenchmark`, copy the APK to ~/.local/share/waydroid/data/local/tmp, `waydroid shell -- pm install -r`, then `cmd package compile -m speed-profile -f com.mediagram.android`
