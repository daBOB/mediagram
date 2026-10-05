# Phase 04: Close-out (version, docs, device walk)

**Priority:** high. **Status:** pending. **Depends on:** 01–03.

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
- [ ] **Full checks.** `cargo test --workspace`; `./gradlew testDebugUnitTest lint :app:assembleRelease`; `scripts/check.sh` if present.
- [ ] **Device walk on the TV box.**
  - Pin it: `ANDROID_SERIAL=192.168.0.35:5555`.
  - Navigate only, on the "TV test" profile.
  - Hero sharpness: does Home show the w1280 backdrop after enrichment?
  - Movies wall held-key gfxinfo.
  - One 24p play: no mode change, normal exit.
- [ ] **Release.** Ask the user before running `scripts/release-android.sh`, because it is outward-facing: TVs self-update from the channel.
