---
title: "Android app updates itself from the library channel"
status: pending
priority: P2
branch: main
tags: [android, release, signing, uploader, core, update]
created: 2026-10-02
---

# Android Self-Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A release published once from this machine reaches every family device's Android app by itself, keeps it signed in, and never interrupts playback.

**Architecture:** `scripts/release-android.sh` builds a minified, release-key-signed APK and hands it to `mediagram publish-app`, which sends it to the library channel with a pinned `#mlib-app v=1` caption. The app's Rust core finds the newest such pin and downloads it sha256-verified; a new `feature:update` module installs it through PackageInstaller (`USER_ACTION_NOT_REQUIRED`) when the app goes to the background.

**Tech Stack:** Rust (mlib-spec, uploader `crates/mediagram`, core `crates/mediagram-core` + uniffi), Kotlin/Compose/Hilt, Android PackageInstaller, Gradle (AGP 9), aapt2/apksigner.

**Spec:** `docs/superpowers/specs/2026-10-02-android-self-update-design.md` · research: `plans/reports/researcher-261002-0100-android-self-update-packageinstaller-rules-report.md`

## Global Constraints

- Release key = this machine's key, cert SHA-256 `58:40:18:1D:3D:A5:F4:4C:9F:01:85:BF:4A:C3:34:5E:5F:E0:87:86:34:73:50:DB:04:AA:A6:E6:9D:F4:F5:76`; never in the repo or the channel.
- `versionCode = major × 1_000_000 + minor × 1_000 + patch`, derived from `versionName`; minor and patch < 1000.
- Caption: `#mlib-app v=1` + one JSON line `{"version","code","bytes","sha256","published_at"}`; the release message is pinned, the previous release unpinned; index pins never touched.
- Updater runs only in the `release` build type **and on a television** (`isTelevision(context)`, `app/.../SurfaceSelection.kt`); debug and benchmark builds, phones and tablets never update themselves (spec decision 7 — Play Protect blocks phones/tablets, spike report).
- `REQUEST_INSTALL_PACKAGES` is declared and granted by adb at device setup (`appops set … allow`); without it the install needs the user.
- Release APK packages only `arm64-v8a` and `armeabi-v7a`.
- Never install while something plays; never download while something plays.
- Every commit bumps all three manifests by pattern (CLAUDE.md § Versioning; memory "bump versions by pattern").
- Device commands always pin a serial: tablet `caad49da`, TV box `192.168.0.35:5555`.
- No plan references (phase numbers, finding codes) in code comments, test names or commit messages.

## Review Focus

1. A release arrives while a TV binges for hours → nothing downloads or installs until playback stops / the app backgrounds (Task 4.1 `shouldCheck` test, 4.3 `nothingInstallsWhileSomethingPlays`).
2. Network drops or Telegram cuts the APK download midway → no "ready" APK, the partial file is removed, the next check retries (Task 3.2 corrupt/short/oversize download tests, 4.3 `aFailedDownloadSaysWhyAndInstallsNothing`).
3. Two releases published back to back → a device holding the older download fetches the newer one and deletes the old file (Task 4.1 `staleFiles` test, 4.3 `anAlreadyDownloadedReleaseIsNotFetchedAgainAndOlderOnesGo`).
4. An APK signed with another key gets published by mistake → `release-android.sh` refuses it before sending; the app refuses it before installing, with the reason on the Updates row (Task 2.4 certificate check, 4.2 `ApkRefusalTest`, 4.3 `aRefusedApkIsDeletedAndNotInstalled`).
5. A device with no library chosen yet, or signed out → the updater skips silently, no crash, no core call (Task 4.3 `noLibraryChosenOrADisabledBuildNeverAsksTheCore`).

## Phases

| # | Phase | Bump | Status |
|---|---|---|---|
| 00 | [Spike: can the app replace itself silently?](phase-00-spike-silent-self-update-probe.md) | none (throwaway) | completed — TV silent; tablet blocked by Play Protect ([report](reports/spike-silent-self-update-results.md)) |
| 01 | [Release key, release build, derived versionCode](phase-01-release-signing-build-and-derived-versioncode.md) | patch | pending |
| 02 | [`#mlib-app` caption, `publish-app`, release script](phase-02-app-release-caption-publish-app-and-release-script.md) | minor | pending |
| 03 | [Core: find and download the newest release](phase-03-core-latest-app-release-and-verified-download.md) | minor | pending |
| 04 | [Android updater and the Updates row](phase-04-android-updater-module-and-settings-row.md) | minor | pending |
| 05 | [Device switch-over and acceptance](phase-05-device-switch-over-and-acceptance.md) | none | pending |

**Order and gates:** 00 first; its report goes to the user before 01 starts (a device that cannot update silently is the user's decision). 01 → 02 → 03 → 04 in order (each consumes the previous phase's interface). 05 needs the user present (TV box on, tablet connected).

**Spec deviation (flagged for the user):** the spec's device check "a corrupted download is refused" cannot be forced on a non-debuggable release build (no `run-as`); it is covered by the core test in Task 3.2 instead.
