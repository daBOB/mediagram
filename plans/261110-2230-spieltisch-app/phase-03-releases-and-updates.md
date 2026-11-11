# Phase 3: Releases and updates per app

## Context links

- Plan: [plan.md](plan.md)
- Self-update design: `docs/superpowers/specs/2026-10-02-android-self-update-design.md`
- Code today:
  - `android/core/update/` (`AppUpdater.kt:97-108`, `ApkInstaller.kt:26-36`, `PackageApkInstaller.kt:24-39`)
  - `crates/mediagram-core/src/api/channel/app_release.rs:75-147`
  - `crates/mlib-spec/src/app_caption.rs:25,91`
  - `crates/mediagram/src/app_release/publish.rs:11,31,54,72,76-87`
  - `scripts/release-android.sh`

## Overview

- Priority: third.
- Status: Android half and release script done; nothing published. The channel dry run and the box run wait for the user.
- What it does: a Spieltisch release is published to the channel on its own and installs
  itself on the TV boxes, without the media app being released.

## Key insights

- The caption `#mlib-app v=1 {json}` has no package field. `publish.rs` allows only `com.mediagram.android` and unpins every older `#mlib-app` post.
- Only the media app can read the channel (Telegram session). Spieltisch therefore asks the media app, through phase 2's provider, for its newest release and the APK file.
- Spieltisch then installs the APK itself. That is the self-update path the 2026-10-02 spike proved on the box; `apkRefusal` already checks own package, higher versionCode and same signer.

## Requirements

- Functional:
  - `mediagram publish-app` publishes the media app or Spieltisch, read from the APK's package. Files are named per app, and only older posts of the same package are unpinned.
  - The media app's updater takes only its own package's posts.
  - Spieltisch checks in the background, like the media app, and installs when it goes to the background.
- Old clients: they read only `#mlib-app`, which keeps meaning the media app.

## Architecture

- `mlib-spec`:
  - Spieltisch's posts use their own tag, `#spieltisch-app v=1`, in the same JSON shape as `#mlib-app`.
    Media apps already out there read only `#mlib-app`, so they never pick a Spieltisch post, whatever its versionCode.
  - The caption parser takes the tag to read.
  - `docs/mlib-spec.md` §7a is updated.
- Uploader: `publish.rs` takes a list of allowed packages, names files `{app}-{v}.apk`/`.dm`, and unpins by package.
- Core: `latest_app_release(package)` + `download_app_release(package)`.
- Media app provider:
  - `query(/release/spieltisch)` returns version, versionCode, size and sha256.
  - `openFile(/release/spieltisch.apk)` returns the APK, downloaded by the core, verified, and cached.
- Spieltisch: `core/update` is reused through an `ApkSource` seam. The media app's source is the core; Spieltisch's is the provider.
- `scripts/release-spieltisch.sh`: avatars, client, `.so`, release APK, publish.

## Related code files

- Modify:
  - `crates/mlib-spec/src/app_caption.rs`, `docs/mlib-spec.md`
  - `crates/mediagram/src/app_release/publish.rs` and its test
  - `crates/mediagram-core/src/api/channel/app_release.rs`
  - `android/core/update/*` (`ApkSource` seam), the media app's `UpdateModule.kt`
- Create:
  - the provider's release paths
  - Spieltisch's `UpdateModule`
  - `scripts/release-spieltisch.sh`

## Implementation steps

1. Caption tag per app + tests: `#mlib-app` stays the media app's, and a `#spieltisch-app` post is invisible to the media app's lookup.
2. `publish-app` per package, with the unpin rule per package + tests.
3. Core: release lookup and download per package + tests.
4. `ApkSource` seam in `core/update`. The media app's behaviour is unchanged (its updater tests stay green).
5. Provider release paths. Spieltisch's updater wiring (release builds only, as for the media app).
6. Dry run on this machine: publish a Spieltisch build to the channel and check that the media app's own update check ignores it.

## Todo

- [x] caption field + spec
- [x] publish per package + unpin per package
- [x] core lookup/download per package
- [x] `ReleaseSource` seam (was `ApkSource`); media app unchanged
- [x] provider release paths + Spieltisch updater
- [x] release script (build half run with `--no-publish`)
- [ ] channel dry run and the box update: waits for the user's go to publish

## Success criteria

- Publishing Spieltisch leaves the media app's pinned release in place, and the reverse.
- A media app with the older caption parser ignores Spieltisch's post: it never reads that tag.

## Risk assessment

- Unpinning the wrong post would cut the TV boxes off from their updates. That rule is covered by tests on both packages before any real publish.

## Security considerations

- The APK reaches Spieltisch only through the signature-guarded provider. Its sha256 is checked against the caption before install, and `apkRefusal` checks the signer.

## Next steps

Phase 4: the media app drops its own games.

## Result

Built (Android half and script; nothing was published):

- **Seam.** `core:update` is a plain library (no Hilt, no `:core:data`). `AppUpdater(config, source, playback, installer, scope)`; `ReleaseSource.latest(): Lookup` (`Found(Release?)` / `Unavailable(reason?)`), `Release(versionName, versionCode, download)` carries its own fetch, `sentence(Throwable)` gives a person-readable failure. The media app's `CoreReleaseSource` and its DI are in the app module; Spieltisch's `MediaReleaseSource` reads the provider. `InstallResultReceiver` reaches the updater through `UpdaterHost` on the Application. `:spieltisch:dependencies` shows only `:core:designsystem` and `:core:update` (`reports/update-spieltisch-dependencies.log`).
- **Provider.** `/release` (row: `version_name`, `version_code`, `bytes`, `sha256`; empty cursor for no library, signed out, nothing pinned, any failure) and `/release.apk` (`SpieltischReleaseExport`: core download into the media app's cache, newest only, reused when its size matches). Same signature gate.
- **Wait choice.** `openFile` blocks its binder thread while the core downloads: the media app's process stays protected while Spieltisch holds the call. Spieltisch passes a `CancellationSignal`; the provider cancels the download when it fires (`OperationCanceledException`), and never runs on the main thread. No `.dm` is served: Spieltisch has no baseline profile, so AGP makes none.
- **Spieltisch updater.** Copies `/release.apk` to a unique `.part`, checks size and sha256 against the row, renames on a match and deletes otherwise; `apkRefusal` still applies at install. Release builds only (`R.bool.self_update`). Checks and downloads run during a night (they cost the box bandwidth, not the night); only an install must never meet one. The night lives only while its screen shows: `TvGamesFrame` stops it on ON_STOP, and `MainActivity` calls `onBackground` after `super.onStop()`, so `NightPlayback` only waits out a stop under way and otherwise reports "not playing". Media app missing or silent: `Failed("the media app did not answer")`, tried again at the next foreground. The install permissions and `InstallResultReceiver` merge from `core:update`'s manifest (checked in the release APK).
- **Script.** `scripts/release-spieltisch.sh [--no-publish]`; ran with `--no-publish` (`reports/update-release-script.log`): 75 MB APK signed with the release key. Publishes without `--profile` when AGP made none. The publish step was never run.

Tests (each watched failing against a broken version: `reports/update-broken-versions.log`): `AppUpdaterTest` (17, moved onto a fake source), `CoreReleaseSourceTest` (4), `GameNightReleaseTest` (7), `MediaReleaseSourceTest` (5), `SpieltischUpdaterTest` (7, including the invariant `goingToTheBackgroundStopsTheNightBeforeTheInstallProceeds`; its screen half is the existing `TvGamesFrameTest.goingToTheBackgroundEndsTheNightAndLeaves`), `MediaAppContractTest` extended. `AppUpdaterTest` split by theme into `AppUpdaterTest`, `AppUpdaterSourceTest`, `AppUpdaterInstallTest` over `UpdaterTestBase`.

Emulator (`Google_TV_1080p`, x86, shut down after; `reports/update-emulator.log`; both apps debug-signed, Spieltisch built with `self_update` on for this run only): nothing pinned: the check answered "none pinned" in 42 ms with the media app warm (right after the night's own warm-up); a second foreground made no new look (hourly throttle); media app uninstalled: "unavailable" in 3 ms, no crash, Spieltisch kept running.

Left open:
- Real end-to-end (publish a Spieltisch build, download ~75 MB through the provider, install over itself) needs the user's go to publish and the box run. On the box Spieltisch also needs install-from-this-app granted (`REQUEST_INSTALL_PACKAGES` shows `granted=false` until `adb shell appops set com.mediagram.spieltisch REQUEST_INSTALL_PACKAGES allow`).

Review fixes:
- A core or settings read that fails before the channel is asked now ends the media app's check as `Unavailable` with the core's sentence (`CoreReleaseSource`), so the next foreground retries as before; a failing channel lookup keeps its hourly throttle. Tests: `CoreReleaseSourceTest`, `AppUpdaterSourceTest`.
- `AppUpdater` knows whether the app is in front (`onForeground`/`onBackground`) and skips an install while it is, leaving the APK ready for the next background. Tests: `AppUpdaterInstallTest`, `SpieltischUpdaterTest.comingBackWhileTheStopIsUnderWayLeavesTheInstallForTheNextBackground`.
- Spieltisch reads the media app (release row, APK copy, night, images) through unstable provider clients, released in `finally`, so the media app dying or self-installing is an exception, not the end of Spieltisch's process. Test: `MediaProviderAccessTest` (spies the resolver: unstable client used, no stable `query`/`openFileDescriptor`/`openInputStream`).
- `/release` answers no cursor when the lookup fails and an empty one for nothing pinned or no library; Spieltisch maps no cursor to `Unavailable`, so a ready APK is never dropped for a failure. Tests: `GameNightReleaseTest`, `MediaReleaseSourceTest`.
- Spieltisch logs each update status change under the tag `update` (`UpdateStatusLog.kt`). Test: `UpdateStatusLogTest`.
- `release-spieltisch.sh` refuses an APK without `libspieltisch_core.so` for arm64-v8a and armeabi-v7a, before the cert check (`reports/update-release-script-2.log`).
