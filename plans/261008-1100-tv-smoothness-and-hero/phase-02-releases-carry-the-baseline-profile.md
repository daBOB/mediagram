# Phase 02 — releases carry the baseline profile

**Status:** done. Verified end to end on the TV box with 0.119.0.

## Why

A self-updated app lands at dexopt `verify` and stays there until the box's background dexopt runs, which it had not done after two days. Android compiles at install when the session carries a dex-metadata file (`base.dm` beside `base.apk`). AGP already writes one per profile format: `baselineProfiles/0/app-release.dm` for API 31+ and `/1/` for API 28–30 (`output-metadata.json`).

## Design

- **Channel (spec §7a).** `publish-app <apk> [--profile <dm>]` first posts the `.dm` as an unpinned document, then the APK. The APK's v=1 caption gains an optional `"profile":{"message":id,"bytes":n,"sha256":"…"}`. v=1 readers ignore unknown fields, so already-installed apps keep updating. A caption without `profile` is unchanged.
- **Core.** `AppRelease` (UniFFI) gains `profile: Option<AppFile>` (message, bytes, sha256); APK and profile go through one `fetch`, and the verified write moved to `app_release/verified_file.rs` (the 200-line limit). `download_app_release` also fetches the profile, verified, to `<path>.dm` when the caption names one. A failed profile download never fails the APK.
- **Updater.** `PackageApkInstaller` writes `base.dm` into the session beside `base.apk` when the file exists and `SDK_INT >= 31`. The channel carries the API 31+ format, and ART changed its profile format at API 31.
- **Release script.** It passes the API 31+ `.dm` from `output-metadata.json` (`minApi == 31`).

## Todo

- [x] mlib-spec: optional `profile` on `AppRelease`, round-trip test, v=1 caption without it still parses
- [x] uploader: `publish-app --profile`, posts the profile before the APK; test with the fake remote
- [x] core: `AppFile`, download plus verification of the profile; test
- [x] Android: `base.dm` in the session; `staleFiles` keeps and removes `.dm` like `.apk`
- [x] release script passes the profile
- [x] docs: mlib-spec §7a, changelog; version bump
- [x] box end to end: 0.119.0 published (message 27408) → box self-updated 17:02:04 → `speed-profile` / `install-dm`; launches clean with the regenerated bindings

## Risks

- **A rejected `.dm` fails the whole install.** Mitigation: AGP's `.dm` already installed cleanly on this box through adb's session. An install failure with a profile drops only the profile, so the retry goes in without it.
- **The first release with the new updater still installs without a profile**, because the old updater does the install. The release after it is the first to carry one.

## Notes

- The committed Kotlin bindings dated from 2026-10-05, before the doc changes of 2026-10-06 (`a39aa4ad`), and had drifted from the Rust surface (checksums of five functions). They are regenerated here together with the native core.
