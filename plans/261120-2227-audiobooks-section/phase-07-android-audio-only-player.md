# Phase 07 — Android: cover in the player, cast and together start

**Context:** spec § Android (player, cast bug, screen-locked listening).
**Status:** todo. **Depends on:** Phase 06.

## Requirements

- One signal, "audio-only": the player's current tracks are non-empty and hold no video track. Exposed by the player view model as `audioOnly: StateFlow<Boolean>`, false whenever a new title opens until its tracks arrive.
- With `audioOnly` true, the player stage (phone and TV, both through `ui-common/.../player/PlayerVideo.kt`) draws the title's poster whole (`HeroArtwork(path, contentScale = ContentScale.Fit)`) centred on black where the picture would be. No poster → black, as today.
- Watch-together and cast-to-TV start an audio-only title: the same signal calls `TogetherController.firstFrame()` (today fed only by `onRenderedFirstFrame`, which an MP3 never fires).
- Screen-locked listening: no code change expected — `PlaybackService` (MediaSessionService, `mediaPlayback`) already gives lock-screen controls with `mediaMetadataFor(set)` artwork, and `ON_STOP` pauses only while presenting to a wireless display. Proved on the device in step 5; if it pauses, fix here.

## Files

- Modify: `android/feature/player/src/main/kotlin/PlayerViewModelTogether.kt:67-71` (add `onTracksChanged(tracks)`: when `!tracks.isEmpty && !tracks.containsType(C.TRACK_TYPE_VIDEO)` → `controller.firstFrame()` and `audioOnly = true`)
- Modify: the player view model file that owns the `Player.Listener` and presentation state (`audioOnly` flow; reset on `open`)
- Modify: `android/ui-common/src/main/kotlin/ui/common/player/PlayerVideo.kt:72-111` — `Video(player, framing, onWindowBottomChanged, overlay, coverPath: String? = null)`; draw the cover when `coverPath != null` and the caller says audio-only
- Modify: `android/ui-mobile/.../VideoWithSubtitles.kt:42-51`, `android/ui-tv/.../TvSubtitles.kt:73-80` (pass `coverPath = if (audioOnly) set.posterPath else null`)
- Test: `android/feature/player/src/test/kotlin/` (together start for an audio-only title), `ui-common` test for the cover decision

## Interfaces

- Consumes: `Kind.AUDIOBOOK` not needed — the signal is the tracks, so any audio-only file is covered.
- Produces: `PlayerViewModel.audioOnly: StateFlow<Boolean>`.

## Steps

- [ ] **1: Failing tests**
  - `aTogetherJoinPlaysAnAudioOnlyTitle`: a controller armed to play at first frame, fed tracks with one audio group and no video → plays; fed empty tracks first → does not play yet.
  - `audioOnlyIsFalseUntilTracksArrive`: open → false; audio-only tracks → true; open another → false.
  - Cover decision (pure function, e.g. `fun stageCoverOf(audioOnly: Boolean, posterPath: String?): String?`): `(true, "/p.jpg") → "/p.jpg"`, `(false, "/p.jpg") → null`, `(true, null) → null`.
- [ ] **2: Run, expect failures** (`:feature:player:testDebugUnitTest :ui-common:testDebugUnitTest`).
- [ ] **3: Implement** per Files.
- [ ] **4: Android unit tests** green.
- [ ] **5: Device walk** (tablet `caad49da`, test profile — check the avatar first; TV emulator): open a book → cover whole; seek far (toward the end of the longest book); press power to lock → keeps playing; pause and play from the lock screen; unlock, back out, reopen → resumes. Cast a book from the tablet to the TV → it starts. Record results in `plans/261120-2227-audiobooks-section/reports/`.
- [ ] **6: Commit** `feat(android): show an audiobook's cover in the player, and start cast and together sessions without a picture`.

## Success criteria

Tests green; the device walk passes every item, written down.

## Risks

The shared player first reports empty tracks for each title (`TvDisplayModeMatch.kt:44-47`); the `isEmpty` guard keeps a video title from being taken for audio-only.
