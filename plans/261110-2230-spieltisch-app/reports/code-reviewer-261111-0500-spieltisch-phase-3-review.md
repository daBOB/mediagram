# Phase 3 review: Spieltisch self-update through the media app

Scope: the uncommitted work on top of 77981392 (23 changed and 11 new files, about 450 lines of Kotlin and shell). Read in full: AppUpdater (old and new), ReleaseSource, InstallResultReceiver/UpdaterHost, PackageApkInstaller, UpdateModule, CoreReleaseSource, MediagramApp, GameNightProvider, SpieltischReleaseExport, MediaReleaseSource, NightPlayback, SpieltischApp, MainActivity, GameNightViewModel stop/start, TvGamesFrame ON_STOP, the release scripts, the core's `app_release.rs` and `verified_file.rs`, `publish.rs`, the uniffi async glue and the tests.
Ran: `:core:update:testDebugUnitTest`, which passed. Also dumped the merged manifest of the built release APK (receiver and both install permissions are present) and listed its native libs (both ABIs present).

**Verdict: not commit-ready until M1 is fixed (about 5 lines plus a test). No other blockers.**

## Critical
None.

## High
None.

## Medium

**M1. Media app: a core that can't be built now starts the hourly throttle. Before, it was retried at the next foreground.**
- Where: `AppUpdater.kt:85-86` sets `lastCheckAtMs = now()` before `source.latest()`. `CoreReleaseSource.kt:22-23` lets exceptions from `settings.read()` and `coreProvider.coreOrNull()` escape (the latter rethrows: `CoreProvider.kt:191-202`).
- HEAD set `lastCheckAtMs` only after both had returned (HEAD `AppUpdater.kt:98-100`).
- Scenario: the box boots and the keystore or EncryptedSharedPreferences refuses, or the core build fails. The status becomes Failed and no check runs for an hour. Before, the next foreground checked again.
- Impact: a delay, not a cutoff. But it is the one behaviour change I found in the media app's update path, and `docs/project-changelog.md` says "The media app's behaviour is unchanged". No test pins it: HEAD's `aCoreThatCannotBeBuiltFailsTheCheckInsteadOfCrashing` checked only the status.
- Fix: put it in `CoreReleaseSource`, which is the only place that can tell "could not ask" apart from "asked and failed". A failure of `latestAppRelease` must keep its throttle, as before.
  ```kotlin
  val handle: String; val core: CoreInterface
  try {
      handle = settings.read() ?: return Lookup.Unavailable()
      core = coreProvider.coreOrNull() ?: return Lookup.Unavailable()
  } catch (e: CancellationException) { throw e } catch (e: Exception) {
      Log.w("update", "update check failed", e)
      return Lookup.Unavailable(e.coreSentence() ?: "the update check did not finish")
  }
  ```
  This restores both the old status text and the old retry. Pin it with a test: a broken `coreOrNull` produces Failed, and a second `checkAndDownload` asks again.

## Low

**L1. The known edge case: rated Low, cheap to fix.**
- Scenario: the user returns while the stop is still running. The new night's start (`GameNightViewModel.kt:51`) and the pending install (`NightPlayback.kt:17`) wait on the same `stopping` job. Both resume together, and the install kills the new night seconds after it starts.
- Why Low:
  - It needs an update that is already downloaded, plus a return within the stop's duration (about a second).
  - It costs one relaunch and one rejoin. No game is lost: the earlier night had already ended.
  - The larger window (session write of about 75 MB, then the commit and PackageManager's own install) exists anyway, for both apps.
- Smallest fix: drop the install when `onForeground` came after `onBackground`. Check it as late as possible, right before `installer.install`:
  ```kotlin
  @Volatile private var inFront = false
  fun onForeground() { inFront = true; /* unchanged */ }
  fun onBackground() { inFront = false; /* unchanged */ }
  // installIfReady, after the refusal check, before installer.install(...):
  // Back in front since the install was asked for: it would end what the person came back to; the next background installs it.
  if (inFront) return
  ```
- Where it belongs: the shared `AppUpdater`. It is the only place that sees both lifecycle calls. `PlaybackActivity` can't carry it, for two reasons. The same gate also stops checks (`shouldCheck`). And `downloadWhileIdle` polls it every 2 s and would cancel downloads. Since `onForeground` only runs while the app is in front, a "playing while in front" gate would never check at all.
- Does it change the media app? Yes, but only inside the gap between `onStop` and the start of the session write: one main-thread hop plus `refusal()`'s APK parse, a fraction of a second. There, a returning user's app is no longer killed; the install moves to the next background. Every other path stays the same: the confirm-intent flow is onStart, then the confirm activity, then onStop, as before.
- If "exactly as before" is absolute, the Spieltisch-only alternative is this: in `MainActivity.onStop`, launch on main `night.stopping?.join()`, then call `onBackground()` only if no activity started in the meantime. That needs its own in-front flag and leaves the media app byte-identical. It is your call; I'd take the shared version.

**L2. While copying, Spieltisch holds a *stable* reference to the media app, so the media app's death kills Spieltisch.**
- Where: `MediaReleaseSource.kt:94-107`.
- Cause: `ContentResolver.openFileDescriptor` takes a stable provider reference that lives as long as the returned PFD (the whole 75 MB read and hash). After a `DeadObjectException` it also retries the entire `openFile`, which means the whole download, on a stable reference.
- Scenario: the user switches from the media app (its own update ready) to Spieltisch. The media app's install commits at exactly that moment, while Spieltisch copies. Android kills Spieltisch ("depends on provider … in dying proc"). A media-app crash during a copy that runs during a night ends the night the same way.
- Fix: `contentResolver.acquireUnstableContentProviderClient(MediaApp.AUTHORITY)?.use { it.openFile(MediaApp.releaseApk, "r", signal) }`, and the same for the query. The media app's death then becomes an exception, the status becomes Failed, and the next foreground tries again.

**L3. On `/release`, a core failure looks like "no release", so Spieltisch deletes an APK that was ready.**
- Where: `GameNightProvider.kt:65-70` answers an empty cursor for any failure. Spieltisch maps that to `Found(null)`, and `AppUpdater.kt:97-100` then sets `ready = null` and runs `removeStale(null)`.
- Scenario: Telegram is unreachable at boot. The verified APK is deleted, and the next look is an hour later.
- Cheap today, because the media app's cached copy makes the re-copy local. Fix: answer `null` (giving `Unavailable`) when `latest()` throws, and keep the empty cursor for "nothing pinned or no library".

**L4. Spieltisch's update failures go to nothing a person can see.** Spieltisch never collects `updater.status`. Refusals (`AppUpdater.kt:130-135`) and Android's install failures (`dropReady`) are not logged, so the box run will have no trace of why an install did not happen. One line in `SpieltischApp` fixes it: `scope.launch { updater.status.collect { Log.i("update", "$it") } }`.

**L5. Release script.** Nothing beyond a note; see "Verified" below for what it does check.
- It does not check that the APK carries `lib/{arm64-v8a,armeabi-v7a}/libspieltisch_core.so`. Today's APK does. This matters because a release that crashes in `MainActivity.onCreate` (`gameCatalogue()`) never reaches `onStart`, so it can never update itself away.
- An optional `unzip -l "$APK" | grep -q lib/armeabi-v7a/libspieltisch_core.so` would cover it (the same gap exists in `release-android.sh`).
- The avatar count uses `-lt 22` where the media script uses `-ne 22`. Trivial.

**Note.** `SpieltischReleaseExport.kt:44` keeps the served 75 MB APK in the media app's cache until a newer release arrives, even after Spieltisch has installed it. Android's cache trimming reclaims it. No action needed.

## Verified correct

**1. The media app's own update path is unchanged apart from M1.**
- These are line-for-line the same as HEAD: `shouldCheck`/throttle, the `Unavailable` paths (which restore the previous `lastCheckAtMs`, as HEAD's `?: return` did), playback gating in check, download and install, `removeStale`/`staleFiles`, `.dm` (`profileOf`, `onInstallResult` deletes every `.dm`, `{code}.dm` beside the same `updates/{code}.apk` path), the confirm intent, `dropReady` and the error sentences (`coreSentence` through `sentence`).
- `PackageApkInstaller`: only re-indented, with the DI removed.
- `updateConfig()`: the same values as before.
- The `MainActivity` calls are untouched.
- `@Provides @Singleton` gives the same single instance to MainActivity, SystemViewModel and the receiver.
- A process restarted by an install result: Hilt injects `MediagramApp` in `super.onCreate()` before any receiver runs, and `Lazy.get()` builds the updater exactly when HEAD's `@AndroidEntryPoint` field injection did.
- The `as? UpdaterHost` cast can't fail in either app.

**2. Security.**
- The provider's `android:permission` (signature) covers read for every path. There is no `grantUriPermissions` and no path-permission, and `guard()` re-checks in both `query` and `openFile`. `getType` answers `null` for everything.
- The permission can't be squatted, because a different-signer definition fails install.
- `/release` can't answer the media app's own release, at three layers:
  - `ReleaseApp.SPIELTISCH` is hard-coded, and the core matches the caption marker exactly (`app_caption.rs:82`, with a test for the prefix case).
  - `publish-app` picks the tag from the APK's badging package.
  - Spieltisch's `apkRefusal` checks the package.
- Spieltisch can't install an unverified file. The only writer of `updates/{code}.apk` is `part.renameTo(apk)`, which runs after the size and sha256 match. `apkRefusal` (package, higher versionCode, same signers) runs before every install, and PackageInstaller enforces the signer again.

**3. Provider robustness.**
- Downloads are serialized by the Mutex, which is released on cancellation.
- On cancel, the binder thread's `runBlocking` is cancelled and uniffi's `finally freeFunc` drops the Rust future.
- The core's `{code}.part` from a cancelled or killed download is deleted by the next `apk()` under the lock.
- Serving `{code}.apk` on a length match is safe, because only the core's verified rename creates it.
- "Newest only" while an older file is being served is safe: the delete runs under the lock, and an already-returned PFD keeps reading the unlinked inode. If the release changes between `/release` and `/release.apk`, the hash fails and the next hourly check tries again.
- A full disk fails cleanly on both sides: the core removes its `.part` on error, and Spieltisch deletes its `.part` in `finally`. Spieltisch's leftover `release*.part` files go at the next `removeStale`.

**4. The night invariant.**
- ON_STOP is dispatched before `onStop()` returns. `model.stop()` sets `stopping` on main, and `NightPlayback` joins it on `Main.immediate`.
- Uncaught throws on the shared SupervisorJob scope: the Spieltisch-specific code has no new path that throws outside a try.

**5. Release script.**
- Each way a wrong APK could be published is refused:
  - a stale APK (deleted before the build)
  - an unsigned build (no `spieltisch-release.apk`)
  - the wrong key (cert pin)
  - the wrong package (publish-app's package-to-tag mapping)
  - a versionCode that isn't newer (publish-app)
  - a dex-metadata file named but missing
- It stops cleanly at `--no-publish`, and the log shows it never ran further.

**6. Standards.** Every touched file is under 200 lines (largest: AppUpdater at 180). There are no plan or phase references in code, and the comments explain the why.

## Unresolved questions
- L1: shared `AppUpdater` (a tiny, benign change to the media app) or Spieltisch-only? This is your decision.

**Status:** DONE_WITH_CONCERNS
**Summary:** The media app's update path is unchanged except for M1: a core that can't be built now waits out the hourly throttle instead of being retried at the next foreground. It needs a 5-line fix in `CoreReleaseSource` before commit. Security and verification hold, and the Lows (stable provider reference, failure read as "no release", the night edge case) can follow.
