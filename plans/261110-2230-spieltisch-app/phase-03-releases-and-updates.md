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
- Status: pending.
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

- [ ] caption field + spec
- [ ] publish per package + unpin per package
- [ ] core lookup/download per package
- [ ] `ApkSource` seam; media app unchanged
- [ ] provider release paths + Spieltisch updater
- [ ] release script; channel dry run

## Success criteria

- Publishing Spieltisch leaves the media app's pinned release in place, and the reverse.
- A media app with the older caption parser ignores Spieltisch's post: it never reads that tag.

## Risk assessment

- Unpinning the wrong post would cut the TV boxes off from their updates. That rule is covered by tests on both packages before any real publish.

## Security considerations

- The APK reaches Spieltisch only through the signature-guarded provider. Its sha256 is checked against the caption before install, and `apkRefusal` checks the signer.

## Next steps

Phase 4: the media app drops its own games.
