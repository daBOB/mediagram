# Android app updates itself from the library channel

Status: design approved in conversation 2026-10-02; this document awaits review.

## Intent

The Android app runs on the user's tablet, the Google TV box and family
devices (other people's phones and TVs, signed in to the same Telegram
account by QR). Today every new version reaches a device only by `adb` over
USB or the network. A release published once should reach every device on its
own, keep the device signed in, and never interrupt playback. Family viewers
never see an APK, a prompt they have to understand, or a Settings screen.

## Decisions (user, 2026-10-02 — do not reverse without asking)

1. **Audience:** family devices too, not only the user's own.
2. **Update experience:** automatic — download in the background, install
   when nothing is playing; no manual "check now" control.
3. **Signing key:** keep this machine's existing key
   (`~/.android/debug.keystore`, certificate SHA-256
   `58:40:18:1D:3D:A5:F4:4C:9F:01:85:BF:4A:C3:34:5E:5F:E0:87:86:34:73:50:DB:04:AA:A6:E6:9D:F4:F5:76`),
   so installed apps update in place and keep their Telegram session.
4. **versionCode:** derived from semver in the build (reverses the CLAUDE.md
   rule that kept it a separate counter; CLAUDE.md is updated with it).
5. **Source:** the Mediagram library channel, with the assumptions below.
6. **Assumptions accepted:** a release build (minified, not debuggable) with
   both ARM ABIs ships; debug and benchmark builds never update themselves;
   the install happens when the app goes to the background; the one-time
   install permission is granted by `adb` at device setup, and a Settings row
   offers it where it is missing.
7. **Television devices only (after the spike, 2026-10-02):** the updater runs
   only on Android TV devices. Phones and tablets keep getting new versions by
   `adb`: on a Play-certified tablet Google Play Protect blocked every
   app-driven update from our never-seen signing key, from the background and
   even after "Install anyway" (`plans/261002-0213-android-self-update/reports/spike-silent-self-update-results.md`).
   Whether registering the key with Google would change that is researched in
   parallel.

## Out of scope

- The web player. It runs from source on the user's machine and is never
  installed on a device, so it has nothing to update. A deliberate surface
  difference, stated here per CLAUDE.md § Surface Parity.
- A manual "check for updates" control, release notes, staged rollouts,
  downgrades, x86 builds (emulators run debug builds).
- Publishing from the other uploader machine: only this machine holds the key.

## Platform facts this rests on

From `plans/reports/researcher-261002-0100-android-self-update-packageinstaller-rules-report.md`:

- Android 12+ (API 31) installs a PackageInstaller session without a prompt
  when `setRequireUserAction(USER_ACTION_NOT_REQUIRED)` is set, the app
  declares `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, the installed APK targets
  API 29+ (we target 37), and the installer is the installer of record **or
  is updating itself** — the self-update branch covers apps first installed by
  `adb` (AOSP `PackageInstallerSession`, `isSelfUpdate`).
- Android 10–11 has no silent path: the commit returns
  `STATUS_PENDING_USER_ACTION` with a confirm intent, which needs a
  foreground activity to show.
- A debuggable → non-debuggable update with the same key and a higher
  versionCode keeps the app's data.
- Measured by the spike: `REQUEST_INSTALL_PACKAGES` must be granted (app-op)
  on top — without it Android answers `STATUS_PENDING_USER_ACTION` with an
  "unknown apps from this source" screen, which cannot open from the
  background. With it, the Google TV box (Android 14) updates silently from
  the foreground and from `onStop`. On the Play-certified tablet (Android 16),
  Google Play Protect verifies every app-driven install and blocks an app
  from a developer it has never seen; adb installs are not verified.

## Architecture

```
release-android.sh ──► assembleRelease (arm64-v8a + armeabi-v7a, release key)
        │                └─ apksigner: certificate digest must match decision 3
        ▼
mediagram publish-app app-release.apk
        │  aapt2 → versionName/versionCode · sha256 · bytes
        ▼
Telegram channel: document + caption "#mlib-app v=1\n{json}"  ── pinned
        │                                                       (previous release unpinned)
        ▼
App (release build) onStart, ≤ 1/hour, nothing playing
        │  core.latest_app_release(handle)   ← same pinned-messages read as the index
        │  newer versionCode? → core.download_app_release(...)  ← capped, sha256-verified
        ▼
cacheDir/updates/<versionCode>.apk  (verified)
        │
App onStop (Home, TV standby, screen off), not playing
        │  check package name, versionCode, signer vs the running app
        ▼
PackageInstaller session, USER_ACTION_NOT_REQUIRED
        ├─ API 31+: installs silently; next launch is the new version
        └─ API < 31: confirm intent kept, shown at the next onStart (one tap)
```

## Components

### Build and signing (`android/app/build.gradle.kts`)

- `signingConfigs.release` reads `mediagram.signing.storeFile`,
  `storePassword`, `keyAlias`, `keyPassword` from Gradle properties
  (`~/.gradle/gradle.properties`); the keystore is a copy of the debug
  keystore at `~/.config/mediagram/release.keystore`. Absent properties →
  the release build is unsigned and the release script stops (no fallback to
  some other key).
- `release` becomes minified with resource shrinking (what `benchmark` proves
  on the TV box today), signed with `signingConfigs.release`, and packages
  only `arm64-v8a` and `armeabi-v7a`. `benchmark` keeps its current setup.
- `versionCode` is computed from `versionName` `major.minor.patch` as
  `major × 1_000_000 + minor × 1_000 + patch` (0.93.0 → 93000); a build fails
  on a `versionName` that does not parse or on a minor or patch ≥ 1000.
- A build-type flag (`resValue` bool `self_update`) is `true` only in
  `release`, so the updater is switched off in debug and benchmark builds at
  build time. At run time it is also off on anything that is not a
  television (decision 7).

### Caption (`crates/mlib-spec`, documented in `docs/mlib-spec.md` beside §7)

```
#mlib-app v=1
{"version":"0.93.0","code":93000,"bytes":47185920,"sha256":"<64 hex>","published_at":1790900000}
```

- `app_caption` module: `render`, `parse` (rejects a missing field, a bad
  sha256 shape, a non-positive code or size), `newest` (largest `code`, then
  message id).
- Not a pin the index readers can mistake: the core's `pick_index` and the
  web's index pick filter by the index marker. The plan verifies both, and
  that the uploader's `unpin_previous` only unpins index pins.

### Uploader (`crates/mediagram`)

- `mediagram publish-app <apk>`: reads `versionName`/`versionCode` with
  `aapt2 dump badging` (SDK build-tools), refuses a code not greater than the
  newest published release, computes sha256 and size, sends with
  `ChannelRemote::send_document` streaming from the file, pins the message,
  unpins the previous `#mlib-app` pin. Holds its own lock file so two runs
  cannot interleave.
- `scripts/release-android.sh`: `build-android-core.sh` for the two ARM
  ABIs, `./gradlew :app:assembleRelease`, `apksigner verify --print-certs`
  against the digest in decision 3, then `mediagram publish-app`.

### Core (`crates/mediagram-core`, uniffi)

- `latest_app_release(handle) -> Option<AppRelease>`: reuses the pinned-
  messages read the index lookup makes; returns version, code, bytes, sha256,
  chat id and message id of the newest valid `#mlib-app` pin.
- `download_app_release(handle, release, path)`: modelled on
  `subtitles_cache::fetch_into` — `download_with` with a cap (256 MiB), temp
  file, sha256 check, fsync, rename. A mismatch deletes the temp file and
  returns an error.
- `FakeCore`/`FakeCoreHandle` and the generated bindings grow accordingly.

### Android updater (new `android/feature/update`)

- `AppUpdater` (Hilt singleton):
  - `onForeground()` (from `MainActivity.onStart`, beside
    `watchSync.onForeground()`): if `self_update` and ≥ 1 h since the last
    check and nothing is playing → `latest_app_release`; newer than the
    installed `versionCode` and not already verified on disk → download.
    Playback starting cancels the download; the next check starts it again.
  - Deletes `cacheDir/updates/*.apk` at or below the installed versionCode.
  - On API < 31, launches a kept confirm intent (from a previous commit).
- `UpdateInstaller`: `onBackground()` (from `MainActivity.onStop`): a
  verified APK is waiting and nothing is playing → check
  `getPackageArchiveInfo` (package name, versionCode greater, signer equal to
  the installed signer) → PackageInstaller session with
  `USER_ACTION_NOT_REQUIRED`, streamed from the file, committed with a
  mutable `PendingIntent` to `InstallResultReceiver`.
- `InstallResultReceiver`: records the outcome (success, failure message, or
  the pending-user-action intent for API < 31).
- "Nothing is playing" reads the player's existing playback state; the plan
  names the exact signal.
- Manifest: `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, `REQUEST_INSTALL_PACKAGES`
  (kept or dropped by the spike's result), the receiver.

### Settings → System (phone and TV)

One "Updates" row, from `AppUpdater`'s state:

- `up to date · checked 5 min ago`
- `0.93.0 ready · installs when you leave the app`
- `0.93.0 · downloading`
- `can't install — Allow` → opens `ACTION_MANAGE_UNKNOWN_APP_SOURCES` (only
  where the permission is needed and missing)
- `last update failed: <reason>`

Hidden in debug and benchmark builds and on phones and tablets (they never update).

## Error handling

Every failure leaves the installed app untouched and is retried at the next
check: no network, a caption that does not parse (skipped, older valid
release used), a sha256 mismatch (file deleted), an APK whose package,
versionCode or signer does not match (file deleted, reason shown), Android
refusing the session (reason shown). Nothing is ever half-installed —
PackageInstaller swaps the whole package or nothing.

## Testing

- `mlib-spec`: caption render/parse round trip, each rejection, `newest`.
- Core: download verification against a fake channel — a good file is
  renamed into place, a corrupt one is refused and removed.
- Kotlin unit tests: the decision rules (flag off, not newer, playing,
  already verified, throttle), cleanup, and the Settings row text.
- Uploader: `publish-app` against the fake remote — caption, pin, previous
  pin removed, a non-increasing code refused.
- Device acceptance (PackageInstaller cannot run in unit tests): see Rollout.

## Rollout

1. **Spike (throwaway code):** a separate probe app with its own package
   name (the installed Mediagram app is never touched), signed with the
   release key, versions 1 and 2, on the tablet (`caad49da`) and the TV box
   (`192.168.0.35:5555`): installed by `adb`, does version 1 replace itself
   with version 2 without a prompt, also when committed from the background; is `REQUEST_INSTALL_PACKAGES` needed; does HyperOS interfere; which
   Android version the box runs. Results reported before the build phases;
   a device that cannot update silently goes back to the user for a decision.
2. Phases: release build + versionCode + CLAUDE.md → caption + `publish-app`
   + release script → core exports → Android updater + Settings row. Each
   reviewed, versioned per CLAUDE.md, `scripts/check.sh` green.
3. Device acceptance: publish a release; the tablet and the TV box each move
   to it on their own after going to the background, still signed in; a check
   during playback downloads nothing; a corrupted download is refused.
4. First switch per device, by `adb`, same key, data kept: TV box benchmark →
   release; family TV devices get the release build at their next setup.
   Install permission granted by `adb` at the same time. Phones and tablets
   (the user's tablet included) stay on `adb` updates (decision 7).

## Risks

| Risk | Mitigation |
|---|---|
| HyperOS blocks or prompts on session installs | Spike on the tablet first; fall back per user decision |
| The release key is lost | Backed up by the user outside this machine; losing it means one reinstall + login per device |
| APK download competes with playback for Telegram file requests (GetFile floods) | Download only while nothing plays; cancelled when playback starts |
| A pinned release confuses the index pickers | Plan verifies both pickers filter by marker and `unpin_previous` only touches index pins |
| Android 10–11 family devices never confirm | The confirm screen reappears at every launch until accepted; the row says an update waits |
