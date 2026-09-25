# Phase 09 review: PiP + MediaSession (uncommitted, base cc394bc)

Scope: the `git diff HEAD` changes plus the untracked files in `android/`. media3 is 1.10.1 and targetSdk is 37. I checked media3 behaviour by disassembling `media3-session-1.10.1` from the gradle cache, not from its docs.

## Critical

### C1. The session is never added to the service, so there is no notification, no foreground state and no lock-screen controls
`android/feature/player/src/main/kotlin/PlaybackService.kt:42-45`
- The code builds `MediaSession` in `onCreate` but never calls `addSession(session)`.
- In media3 1.10.1, `MediaSessionService.addSession` is called from only two places:
  1. `MediaSessionServiceStub`, when a `MediaController` binds to the service with the `androidx.media3.session.MediaSessionService` action.
  2. `onStartCommand`, and only for media-button or custom-action intents.
- The app never builds a `MediaController`, and `AndroidPlaybackServiceController.start()` sends a plain intent. That plain intent takes neither path (the `onStartCommand` bytecode jumps straight to offset 183).
- Result: `MediaNotificationManager` never sees the session. No media notification is posted, `startForeground` never runs, and the lock screen shows no controls.
- The service stays a plain background started service:
  - The system stops it as idle about 1 minute after the app goes to the background. `onDestroy` then releases the session, so headset buttons die too.
  - The process then becomes cached and frozen, so audio stops about a minute after the screen locks.
- This breaks both user decisions: lock-screen controls, and background audio that keeps going when the screen locks.
- Unit tests cannot see this. The device check will.
- Fix: call `addSession(it)` right after `build()` in the `onCreate` coroutine, and also `setSessionActivity(...)` (see M1).

## High

### H1. Closing the PiP window does not stop or pause playback
`android/ui-mobile/src/main/kotlin/PlayerScreenLifecycle.kt:27-43` and `android/app/src/main/kotlin/com/mediagram/android/MainActivity.kt:120-123`
- When the viewer dismisses PiP, the platform stops the activity and moves the task to the back (`removePinnedRootTaskInSurfaceTransaction`). It does not destroy it. The callback order is `onStop`, then `onPictureInPictureModeChanged(false)`.
- The composition is not disposed, so `viewModel.stop()` never runs. `ON_STOP` only calls `save()`. The player keeps playing audio with no window.
- Once C1 is fixed, the foreground service keeps that audio alive indefinitely.
- This breaks the requirement "Closing the PiP window stops playback" and the decision "pause when the viewer leaves the player screen".
- Fix: in `onPictureInPictureModeChanged(false)` with `lifecycle.currentState == CREATED`, signal a "PiP closed" callback. It can go through `PipEntryPoint`, the same way the leave hint does. The player screen then pauses and saves, or calls `stop()`.
- Screen lock while in PiP is not affected: the mode stays true and no callback fires.

### H2. Leaving the player while a film plays leaves auto-enter on, so the catalog goes into PiP
`android/ui-mobile/src/main/kotlin/PipController.kt:58-64` and `PipActions.kt:60`
- `setPictureInPictureParams(... setAutoEnterEnabled(true))` stays on the activity after the player screen is disposed. `onDispose` only clears `PipEntryPoint`.
- Scenario on API 31+:
  1. The viewer taps back mid-film, which is the normal exit. `stop()` then flips `isPlaying`, but the effect is already disposed, so the params are never rewritten.
  2. On the catalog, the viewer swipes home.
  3. The catalog shrinks into a PiP window. Its play/pause/±10 s actions go nowhere because the receiver is unregistered.
- Fix: in `onDispose`, call `setPictureInPictureParams(Builder().setAutoEnterEnabled(false).setActions(emptyList()).build())` on API 31+.

### H3. The player screen crashes on devices without PiP support
`android/ui-mobile/src/main/kotlin/PipController.kt:56,61,79` and `MainActivity.kt:117`
- `supported` checks only `SDK_INT >= 26 && activity != null`.
- On devices without `FEATURE_PICTURE_IN_PICTURE` (Android Go/low-RAM devices, some OEM builds), `setPictureInPictureParams` and `enterPictureInPictureMode` throw `IllegalStateException` ("Device doesn't support picture-in-picture mode").
- `setPictureInPictureParams` runs unconditionally in a `DisposableEffect` on every play-state change, so opening any title crashes the app.
- The manifest's `uses-feature ... required="false"` shows such devices are expected.
- Fix: add `packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)` to `supported`, and gate both effects on it.

## Medium

### M1. Tapping the notification or lock-screen entry does nothing
`PlaybackService.kt:44`
- There is no `setSessionActivity(PendingIntent)`, so the media notification has no content intent (once C1 is fixed).
- Tapping it while audio plays in the background (screen locked, or after `onTaskRemoved` keeps a playing session) cannot bring the player back.
- Fix: pass a `FLAG_IMMUTABLE` launch-intent `PendingIntent` for `MainActivity`.

### M2. Lock-screen metadata is lost on a cold start and after `retry()`
`DefaultPlayerHandleOps.kt:21-22,191-193` and `PlayerViewModelDelegates.kt:354-356` (diff lines)
- `setMetadataReal` is a no-op while the player is still being built (the `pendingOpen` path in `DefaultPlayerHandle.open`).
- `openReal` builds `MediaItem.fromUri(...)` with no metadata. The KDoc at `DefaultPlayerHandleOps.kt:188` says "[openReal] carries whatever metadata is current"; that is false.
- `openSet` is a `StateFlow` and never re-emits, so:
  - Cold start: resolve finishes before the cache/player is ready. The first title shows no title or poster on the lock screen or notification.
  - `retry()` after an error: `sameTitle` is true, so there is no re-resolve, but the handle reloads a fresh item. Metadata is gone for the rest of the title.
- Fix: keep the last metadata in `DefaultPlayerHandle` and apply it in `openOn`, or re-push after every real load.
- The test at `PlayerMetadataSyncTest.kt` passes only because `FakePlayerHandle.setMetadata` records the value unconditionally. It does not model "no current item", so this path is invisible to it.

## Low

### L1. The PiP aspect clamp truncates below the platform minimum
`android/ui-mobile/src/main/kotlin/PipActions.kt:40-41`
- `(1/2.39 * 1000).toInt()` gives `418/1000 = 0.418`, which is below the framework's 0.41841.
- A video taller than 1:2.39 (for example 1080×2400+) makes `setPictureInPictureParams` throw `IllegalArgumentException` ("Aspect ratio is too extreme"), and the app crashes.
- `PipParamsTest` checks only the `Double`, not the `Rational`.
- Fix: use `ceil` for the min side, or build `Rational(1000, 2390)` / `Rational(239, 100)` directly.

### L2. `isInPip` starts false on a recreated activity
`MainActivity.kt:86`
- It starts at `false` even when the new activity instance is already in PiP. A change that is not in `configChanges` can cause this (uiMode/dark theme, fontScale, locale, density).
- The full chrome then draws inside the PiP window.
- Fix: seed it with `isInPictureInPictureMode` in `onCreate`.

### L3. The notification-permission prompt is unnecessary and repeats
`NotificationPermissionEffect.kt:28-37`
- Android 13+ exempts media-session notifications from `POST_NOTIFICATIONS`. So the prompt shown over the first playing frame gains nothing, and the manifest comment that "a denial only hides the notification" is wrong.
- `asked` is `rememberSaveable` per player composition, so the prompt also comes back on the next title until the OS treats the permission as permanently denied (two denials).
- It never blocks playback, which is the requirement, but it is an avoidable modal interrupting the film.
- Recommend dropping the prompt, or persisting "asked" in preferences.

### L4. Pressing home while a film is buffering skips PiP but keeps playing
`PipActions.kt:60` and `PipController.kt:62`
- Auto-enter keys on `PlayerUiState.Playing` only. A home gesture during a rebuffer backgrounds the app without PiP, and playback then continues audio-only with no window.
- This is the same leak as H1 by another route. The H1 fix does not cover it, because PiP was never entered.
- Consider keying auto-enter on `playWhenReady`.

## Checked and fine
- **Service start from the background:** `open()` only runs from `LaunchedEffect(setId)` / `retry()` while the activity is STARTED. PiP counts as visible, and `pendingSwitch` is collected with STARTED as the minimum state. Plain `startService` is correct here, because `startForegroundService` would need `startForeground` within 5 s.
- **Manifest:** `foregroundServiceType="mediaPlayback"`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `exported="false"` are all present.
- **Session release:** released once in `onDestroy` and nulled; the coroutine is cancelled if the player never arrived. `stopService` followed by `startService` is ordered on the main thread, so there is no session-ID collision on the singleton player. `release()` does not release the player.
- **`onTaskRemoved` (media3 1.10 default):** pauses and stops unless a session is playing. If one is playing, the activity destroy then disposes the composition, which calls `stop()`.
- **RemoteAction `PendingIntent`s:** explicit package plus `FLAG_IMMUTABLE`. The receiver uses `ContextCompat.RECEIVER_NOT_EXPORTED`, and `PendingIntent` broadcasts carry the creator's uid, so they are still delivered.
- **`replaceMediaItem`:** with the same URI, `ProgressiveMediaSource.canUpdateMediaItem` returns true, so there is no reload or position reset.
- **`configChanges`:** grid columns use `currentWindowAdaptiveInfo()`, which is reactive. Nothing else reads the configuration, and nothing in the app depended on the activity being recreated. `ImmersiveEffect` re-hides the bars on focus and `ON_RESUME` after leaving PiP.
- **Loosened `internal` members:** written only by `PlayerViewModelOpen.kt`; no misuse.
- **File size:** every touched file is under 200 lines (`PlayerScreen.kt` 198, `PlayerScreenParts.kt` 194, `DefaultPlayerHandle.kt` 192).
- **Versions:** 0.49.0 in all three manifests.

## Unresolved questions
- Closing PiP (H1): should it `stop()`, returning to the catalog on reopen, or only pause? The spec says "stops playback and saves progress". Pausing keeps the title on screen when the app is reopened from recents.

**Status:** DONE_WITH_CONCERNS
**Summary:** C1 (the session is never added to the service) means the lock-screen and background-audio decisions are not met. H1–H3 are real lifecycle and crash bugs.
**Concerns/Blockers:** Fix C1, H1, H2 and H3 before the device run.
