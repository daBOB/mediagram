# Phase 09: Picture-in-picture and MediaSession

## Context links
- Web PiP: key `p` only (`web/public/lib/player-keys.js:82-83`, `transport.js:216-219`); no button in `web/public/index.html`
- `android/app/src/main/AndroidManifest.xml` (single `MainActivity`, no `supportsPictureInPicture`, no `configChanges`)
- `android/app/src/main/kotlin/com/mediagram/android/MainActivity.kt` (edge-to-edge; `WatchSync` on `onStart`/`onStop`)
- `android/ui-mobile/src/main/kotlin/PlayerScreen.kt:70-76,203` (stop on dispose unless `isChangingConfigurations`; save on `ON_STOP`)
- `android/feature/player/src/main/kotlin/di/PlaybackModule.kt:53-73` (singleton `ExoPlayer`)
- `android/gradle/libs.versions.toml:110-114` (`media3-session` declared, unused)

## Overview
Priority P2 · Status done (device check pending) · Scope of background playback depends on open question 3.

## Key insights
- The app-scoped player is what makes both cheap: PiP only shrinks the
  activity, and a `MediaSession` wraps the same `Player`.
- PiP needs API 26 (minSdk 24 → guard). Auto-enter: `setAutoEnterEnabled`
  (API 31+) while playing; `onUserLeaveHint` below that. A PiP button in the
  top bar mirrors web's `p` key.
- PiP transitions change window size: without `configChanges` the activity is
  recreated mid-PiP. Adding `configChanges=screenSize|smallestScreenSize|screenLayout|orientation`
  also stops recreation on rotation — `shouldStopOnDispose` and every
  `rememberSaveable` still work, but the tested recreation path is no longer exercised; test rotation again.
- Lock-screen controls on modern Android come from the media notification,
  which requires a `MediaSessionService` (foreground service type
  `mediaPlayback`) → playback continues with the screen off. Headset buttons
  need only a `MediaSession`. Recommended: service, pausing when the viewer
  leaves the player screen (not when the screen locks).

## Requirements
- PiP: button + auto-enter while playing or buffering with the intent to
  (`playWhenReady`); controls, sheet, up-next card hidden in PiP (up-next
  countdown still runs); `RemoteAction`s play/pause, ±(the real increment).
- Closing the PiP window **pauses and saves progress, keeping the title
  open** — user decision, superseding this section's original "stops
  playback" text: reopening the app finds the same title paused where it
  was, not back at the catalog. See `PlayerViewModel.pauseForPipDismissal`.
- MediaSession: title line + poster as metadata, reapplied on every real
  load (cold start, retry) even though `openSet` itself only resolves
  once; play/pause/seek from headset, notification and lock screen;
  session released when the service stops.

## Architecture
`PipController.kt` (ui-mobile): builds `PictureInPictureParams` (aspect from
video size, clamped 1:2.39..2.39:1), updates on play state, exposes `isInPip`
via `LocalActivity` + `onPictureInPictureModeChanged` → Compose state.
`PlaybackService.kt` (`:feature:player`, `@AndroidEntryPoint MediaSessionService`, declared in that module's merged manifest — `:app` does not depend on `:feature:player` directly and needs no new edge) holds `MediaSession(player)`;
started by the player VM on open, stopped on `stop()`. Metadata set on
`MediaItem.mediaMetadata` in `DefaultPlayerHandle.openOn`.

## Related code files
Create:
- `android/ui-mobile/src/main/kotlin/PipController.kt`, `PipActions.kt`
- `android/feature/player/src/main/kotlin/PlaybackService.kt`, `PendingMetadata.kt`, `AndroidPlaybackServiceController.kt`, `PlaybackServiceController.kt`, `PlayerMediaMetadata.kt`, `PlayerViewModelOpen.kt`, `android/feature/player/src/main/AndroidManifest.xml` (service entry)
- tests: `PipParamsTest`/`PipAutoEnterEligibleTest` (pure), `PipRationalBoundsTest` (Robolectric — the platform's own extremes), `PlayerMediaMetadataTest`, `DefaultPlayerHandleMetadataTest`, `PlayerPipDismissalTest`, `PlayerPlaybackServiceTest`, `PipDismissalTest` (`:app`)
Modify:
- `android/app/src/main/AndroidManifest.xml` (`supportsPictureInPicture`, `configChanges`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`; no `POST_NOTIFICATIONS` — see fix round, L3)
- `MainActivity.kt` (leave hint, PiP mode callback, dismissal detection), `PlayerScreen.kt` (hide chrome in PiP; Fit framing in PiP), `SubtitleLayer.kt` (the Fit override), `DefaultPlayerHandle.kt` (metadata remember/reapply), `PlayerHandle.kt`/`FakePlayerHandle.kt` (`pause()`), `android/feature/player/build.gradle.kts` (`media3-session`), `android/ui-mobile/build.gradle.kts` (`robolectric` test dep, for `PipRationalBoundsTest`)

## Implementation steps
1. `configChanges` + rotation regression check (resume, controls, sheet).
2. PiP params + button + auto-enter; chrome hidden in PiP.
3. Remote actions via `MediaSession`-backed PiP (media3 provides actions when a session exists).
4. `PlaybackService` + notification permission request on first play (API 33+), graceful when denied (headset still works).
5. Metadata on media items.
6. Device: PiP from button and home gesture; close PiP → progress saved (check Continue); lock screen controls; BT/wired headset play-pause.

## Todo
- [x] configChanges + rotation recheck
- [x] PiP (button, auto-enter, hidden chrome, actions)
- [x] MediaSessionService + metadata
- [x] permission flow
- [x] check.sh, bump, changelog
- [ ] device run (PiP, lock screen, headset — controller does the device check)

## Success criteria
- PiP survives 10 min with the app backgrounded; progress saved on close.
- Lock screen shows title and poster and controls work.
- `lint` clean for the new manifest entries (foreground service type declared).

## Risks
- `ON_STOP` fires when entering PiP on some OEMs → existing save-on-stop is fine,
  but stop-on-dispose must not fire; guard with `isInPictureInPictureMode`.
- Service leaks after player stop → stop service in `PlayerViewModel.stop()` and on `onTaskRemoved`.
- `WatchSync.onBackground` runs on `onStop` while PiP plays: harmless (a sync round).

## Security
Service not exported; session only accepts controllers from the system/own package (media3 default).

## Implementation notes
- `RemoteAction`s are built by hand, not by asking media3 for them: Android's
  PiP overlay actions come from `PictureInPictureParams.setActions`, a plain
  `Activity` API with no tie to a `MediaSession`; the three actions
  (rewind/play-pause/forward) are answered by a `BroadcastReceiver`
  (`PipActionReceiver`) acting on the same `Player` the transport bar's own
  buttons use, registered while `PipController` is composed. The
  architecture's "media3 provides actions when a session exists" did not
  hold up against the actual API — recorded here rather than silently
  changed.
- `onTaskRemoved` is not overridden in `PlaybackService`: media3's own
  `MediaSessionService` default already stops the service and releases the
  player when nothing is playing, covering the "service leaks" risk without
  a manual override.
- `PlayerHandle.open()` gained no metadata parameter. `MediaMetadata` (title
  line, poster) is pushed reactively from `PlayerViewModel.openSet` —
  resolved asynchronously already, same as the title line itself — through
  a new `PlayerHandle.setMetadata()` that calls `Player.replaceMediaItem`
  rather than `DefaultPlayerHandle.openOn`: a set's `MediaSet` is often not
  resolved yet at `open()` time, and `replaceMediaItem` on the current item
  is media3's own documented way to update metadata without a reload.
- `PlayerViewModel.open()` moved out to a top-level extension function
  (`PlayerViewModelOpen.kt`), the same pattern `retry()`/`setSpeed()` etc.
  already use, to keep the class body under the project's 200-line
  guideline after adding the service start call and the 9th constructor
  parameter; `_state`, `_openSetId`, `repository` and `upNextController`
  moved from `private` to `internal` to allow it, matching `handle`/
  `choicesController`/`session`/`openFsk` already being `internal` for the
  same reason.
- `PipEntryPoint` (a settable nullable lambda) and `LocalIsInPictureInPicture`
  (a `CompositionLocal`) are the two crossings between `:app`'s
  `MainActivity` and `ui-mobile`'s `PipController` — module direction only
  allows `:app` to import `ui-mobile`, never the reverse, so the
  home-gesture auto-enter path (API 26..30, where `setAutoEnterEnabled`
  does not exist) needed a slot `ui-mobile` owns and `MainActivity` writes
  to on `onUserLeaveHint`.
- Four small vector drawables (`ui_mobile_ic_pip_play/pause/rewind/forward.xml`)
  were added — the first drawable resources in `ui-mobile` (the rest of the
  app draws glyph text) — because `RemoteAction` needs a real `Icon`
  resource; Compose's own icon set cannot supply one. Rewind/forward use
  generic fast-rewind/fast-forward glyphs rather than an exact "10s" numeral,
  since the actual seek increment is configurable and not always 10s (see
  `PlayerGestures.kt`); the `RemoteAction`'s own title states the real amount.
- `shouldStopOnDispose` gained a second parameter,
  `isInPictureInPicture`, guarding the existing stop-on-dispose path against
  an OEM that still tears the composition down while the window is open (see
  Risks).

## Deliberate differences
- The PiP button and its auto-enter are phone-only touch equivalents of the
  web's `p` key (CLAUDE.md § Surface Parity, plan.md "Decided" section) —
  not web debt.
- Rewind/forward RemoteAction icons are a generic glyph rather than a
  literal "10s", for the reason above.
- In picture-in-picture the picture always shows [Framing.FIT], regardless
  of what the show remembers — a device check found a remembered 4:3/16:9
  crop letterboxing a second time inside a window already shaped to the
  video's own aspect. The remembered choice is read normally the moment
  the window closes; nothing is written for this.

## Fix round (code review + device run)
A review (`plans/260924-0139-android-web-parity/reports/code-reviewer-260925-1247-phase-09-pip-report.md`,
media3 behaviour verified by disassembly) and a device run on 0.49.0
(API 36, MIUI tablet) both landed before the device todo above was
checked off; every finding is fixed in place rather than left for a
second pass. **User decision, closing PiP:** pause and keep the title
(save progress; reopening the app shows it paused where it was; the
notification/foreground state goes away) — not the full stop this file
originally specified; recorded in Requirements above.

- **C1 (session never added to the service → no notification, no
  foreground state, no lock-screen controls).** `PlaybackService.onCreate`
  now calls `addSession(session)` right after building it (and
  `removeSession` before `release()` in `onDestroy`) — building a
  `MediaSession` alone never registered it with the service; only
  `addSession` does.
- **M1 (tapping the notification did nothing).** `MediaSession.Builder`
  now carries a `setSessionActivity` built from
  `packageManager.getLaunchIntentForPackage(packageName)` — `MainActivity`
  is a class `:feature:player` cannot import (wrong module direction), so
  it is found by package instead.
- **H1 (closing PiP neither paused nor stopped) + the user decision
  above.** The system stops the activity and moves its task back *before*
  calling `onPictureInPictureModeChanged(false)` for a real dismissal —
  never for expanding back to full screen, where the activity is already
  `STARTED`/`RESUMED` again by the time that runs. `MainActivity` now
  tells the two apart with `isPipDismissal(isInPictureInPictureMode,
  lifecycle.currentState)` (a pure function, tested without a real
  Activity) and signals `PipEntryPoint.onDismissed`, wired to a new
  `PlayerViewModel.pauseForPipDismissal()` — `handle.pause()` (a new
  `PlayerHandle` method) + `session.save()` + `playbackServiceController.stop()`,
  never `stop()`: the title, state and choices are left exactly as they
  were.
- **H2 (leaving the player left auto-enter armed on the activity, so the
  catalog could shrink into PiP next).** `PipController`'s `onDispose`
  now also resets `PictureInPictureParams` (`setAutoEnterEnabled(false)`,
  empty actions) on API 31+, on top of clearing `PipEntryPoint`.
- **H3 (crash on devices without the PiP feature).** `supported` now also
  checks `packageManager.hasSystemFeature(FEATURE_PICTURE_IN_PICTURE)`,
  gating every picture-in-picture call this file makes, not only the
  button.
- **M2 (lock-screen metadata lost on a cold start and after `retry()`).**
  `DefaultPlayerHandle` now remembers the last `setMetadata` (`PendingMetadata`,
  split out to stay under the line guideline) and reapplies it in `openOn`
  on every real load — a cold start's `pendingOpen` race and a retry's
  reload of the *same* title (whose `openSet` never re-emits) both used to
  lose it silently. `FakePlayerHandle.setMetadata` now only records once a
  player is installed, matching that early return, rather than recording
  unconditionally and hiding the gap the tests originally missed.
- **L1 (aspect clamp truncated below the platform's own minimum).**
  `pipAspectRational` no longer round-trips `clampedPipAspect`'s `Double`
  through `(ratio * 1000).toInt()` — `1/2.39` truncated to `0.418`, narrower
  than the true `0.41841…`, which `setPictureInPictureParams` rejected as
  "too extreme". The two clamped cases now build the platform's own bounds
  as exact fractions (`Rational(1000, 2390)`, `Rational(239, 100)`); the
  unclamped case passes the real width/height straight through.
- **L2 (`isInPip` started false on a recreated activity already pinned).**
  Seeded from `isInPictureInPictureMode` right after `super.onCreate`.
- **L3 (the notification-permission prompt was unnecessary and could
  repeat).** Verified against Android's own docs
  (developer.android.com/develop/ui/views/notifications/notification-permission#exemptions):
  "Notifications related to media sessions are exempt from this behavior
  change" on API 33+ — `PlaybackService`'s notification is exactly that.
  `NotificationPermissionEffect.kt` is deleted and its call site removed;
  the manifest's `POST_NOTIFICATIONS` entry is removed too (nothing in
  the app needs it).
- **L4 (home mid-rebuffer skipped PiP and kept playing with no window).**
  Auto-enter now keys on `pipAutoEnterEligible(isPlaying, playWhenReady)`
  — `isPlaying` alone missed a title still buffering
  (`playWhenReady` true, `Playing` not yet reached); a home press in that
  window used to background the app with no PiP window and no way back
  to it once playback actually started. Chosen over pausing on any
  backgrounding without PiP, which would also have fired on the screen
  locking — the one case the phase's own decision excludes.
- **Device finding (PiP letterboxing a remembered crop again).** See
  Deliberate differences above.
- **Test-infra finding (not a production bug).** A first `PlayerPipDismissalTest`
  hung: it started the ten-second save ticker (`emitPlaying(true)`) and
  then called `pauseForPipDismissal()`, but `FakePlayerHandle.pause()`
  only flipped a flag — it never told the ticker to stop, so `runTest`'s
  own drain to quiescence spun forever on a `while(true)` that always has
  one more save queued. In production this cannot happen: the real
  `ExoPlayer.pause()` fires a synchronous `onIsPlayingChanged(false)`
  through the already-attached listener bridge, which is exactly what
  stops the ticker (`PlayerSaveTickerTest.pausingStopsTheTicker` already
  covers that path). The fix is in the fake, not production:
  `FakePlayerHandle.pause()` now also calls `emitPlaying(false)` when it
  was playing, mirroring `open()`'s own existing synthetic emission for
  the same reason.

## Next steps
12 records web's missing PiP button as owed or phone-only.
