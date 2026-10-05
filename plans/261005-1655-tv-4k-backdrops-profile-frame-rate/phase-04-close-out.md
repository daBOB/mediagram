# Phase 04: Close-out (version, docs, device walk)

**Priority:** high. **Status:** in progress (profile generated; refresh-rate walk waits for the TV). **Depends on:** 01–03.

## Steps

- [x] **Version bump, minor: 0.116.0 → 0.117.0, or the next free minor if `main` has moved.**
  - Files: `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` `versionName`.
  - Bump by pattern, not by exact string (memory `bump-versions-by-pattern`).
  - Re-check `main` first, because another session commits to it.
- [x] **Merge any duplicated TV check.** If phase 03 had to read `UiModeManager` itself, switch it to the shared `isTelevision`.
- [x] **Docs.**
  - `docs/project-changelog.md` gets one entry.
  - Update the research report's "Applies to mediagram" checklist to done.
  - Note the TV-only frame-rate decision in `docs/system-architecture.md` (Android section).
- [x] **Full checks.** `cargo test --workspace`; `./gradlew testDebugUnitTest lint :app:assembleRelease`; `scripts/check.sh` if present.
- [ ] **Device walk on the TV box.**
  - Pin it: `ANDROID_SERIAL=192.168.0.35:5555`.
  - Navigate only, on the "TV test" profile.
  - Hero sharpness: does Home show the w1280 backdrop after enrichment?
  - Movies wall held-key gfxinfo.
  - One 24p play: no mode change, normal exit.
- [x] **Profile generated on the box** (2026-10-06), shipped as 0.117.1 because the generation build installs 0.117.0 and the self-updater only moves to a strictly higher versionCode.
  - Three generator fixes were needed: focus asked as one query (a found node went stale), a focused node containing the label counts (bar pill labels sit in a child), and a left walk along the bar.
  - Still misses the bar on most iterations (focus sticks on a bottom-edge row, or falls into the side rail). 4 of 13 got through; ART merges iterations, so the profile covers Home 139, Movies page 68, wall 53, title page 28 rules.
  - Relaunch between Home and Movies was tried and dropped: the uncompiled cold start often outlasts the 15 s library wait.
- [x] **Release.** Approved by the user ("Generate on box, then release"). `scripts/release-android.sh` published 0.117.1 (versionCode 117001, message 21906, pinned); the APK carries `assets/dexopt/baseline.prof`.
  - The box could not self-update to it: the later generation runs happened after the bump, so they had already installed the unminified build *as* 0.117.1, and the updater needs a strictly higher versionCode. The published APK was installed over it with `adb install -r` (sha256 matches; sign-in kept).
- [ ] **Device walk, still open.**
  - Refresh rate: the TV was off (no EDID, fallback 1080p60 only), so the 24p switch is untested.
  - Hero sharpness: inconclusive. The hero looked soft at 1:1, the next library update may not have run yet, and release builds can't be read with `run-as` to see the width records.
