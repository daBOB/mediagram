# Phase 09: Picture-in-picture and MediaSession

## Context links
- Web PiP: key `p` only (`web/public/lib/player-keys.js:82-83`, `transport.js:216-219`); no button in `web/public/index.html`
- `android/app/src/main/AndroidManifest.xml` (single `MainActivity`, no `supportsPictureInPicture`, no `configChanges`)
- `android/app/src/main/kotlin/com/mediagram/android/MainActivity.kt` (edge-to-edge; `WatchSync` on `onStart`/`onStop`)
- `android/ui-mobile/src/main/kotlin/PlayerScreen.kt:70-76,203` (stop on dispose unless `isChangingConfigurations`; save on `ON_STOP`)
- `android/feature/player/src/main/kotlin/di/PlaybackModule.kt:53-73` (singleton `ExoPlayer`)
- `android/gradle/libs.versions.toml:110-114` (`media3-session` declared, unused)

## Overview
Priority P2 · Status pending · Scope of background playback depends on open question 3.

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
- PiP: button + auto-enter while playing; controls, sheet, up-next card hidden
  in PiP (up-next countdown still runs); `RemoteAction`s play/pause, ±10 s.
- Closing the PiP window stops playback and saves progress (existing stop path).
- MediaSession: title line + poster as metadata; play/pause/seek from headset,
  notification and lock screen; session released when player is stopped.

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
- `android/feature/player/src/main/kotlin/PlaybackService.kt`, `android/feature/player/src/main/AndroidManifest.xml` (service entry)
- tests: `PipParamsTest` (aspect clamp, pure), `MediaMetadataTest` (title line → metadata)
Modify:
- `android/app/src/main/AndroidManifest.xml` (`supportsPictureInPicture`, `configChanges`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`)
- `MainActivity.kt` (leave hint, PiP mode callback), `PlayerScreen.kt` (hide chrome in PiP; do not stop on the PiP-close `ON_STOP` race), `DefaultPlayerHandle.kt` (metadata), `android/feature/player/build.gradle.kts` (`media3-session`, already in the catalog `libs.versions.toml:113`)

## Implementation steps
1. `configChanges` + rotation regression check (resume, controls, sheet).
2. PiP params + button + auto-enter; chrome hidden in PiP.
3. Remote actions via `MediaSession`-backed PiP (media3 provides actions when a session exists).
4. `PlaybackService` + notification permission request on first play (API 33+), graceful when denied (headset still works).
5. Metadata on media items.
6. Device: PiP from button and home gesture; close PiP → progress saved (check Continue); lock screen controls; BT/wired headset play-pause.

## Todo
- [ ] configChanges + rotation recheck
- [ ] PiP (button, auto-enter, hidden chrome, actions)
- [ ] MediaSessionService + metadata
- [ ] permission flow
- [ ] check.sh, bump, changelog, device run

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

## Next steps
12 records web's missing PiP button as owed or phone-only.
