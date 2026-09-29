---
title: "Review — Android decoder stall recovery (uncommitted, fix/android-decoder-stall-recovery)"
date: 2026-09-28
reviewer: code-reviewer
scope: uncommitted diff in /home/andre/Workspace/mediagram-decoder off main @ 220fa71e
---

# Summary

`./gradlew testDebugUnitTest lint :app:assembleDebug` is green (re-run, 699 tasks). The pure
pieces are small, readable and correctly threaded. **Do not ship this as is.** The watchdog's
signal (`renderedOutputBufferCount` only) stops moving whenever the video surface is gone. A
screen locked with the film playing (lock-screen controls are a shipped feature) or a TV Home press
therefore reads as a stall about 5 s later. That false stall is then **persisted forever**, with no
undo. One path ends with a device that plays every title of that codec audio-only on a black
screen, with no error: the same symptom this change set out to remove.

The recovery mechanics (reopen at the live position, audio override survives, one retry per open)
are sound. The damage comes from the unconditional persistence plus the exhaustion path.

Verification: I read the media3 1.10.1 sources (fetched to the scratchpad), ran probe tests in a
scratchpad copy of the worktree, and ran one mutation test. No device was touched.

# Findings (ranked)

## Critical

**C1. Background playback reads as a stall, and the working hardware decoder gets blacklisted for good.**
`DefaultPlayerHandle.kt:178-185`, `DecoderStallController.kt:84-85`
- Scenario (phone): an H.264 or AV1 film is playing. The viewer presses power. `PlayerLifecycle.kt:44`
  only saves on `ON_STOP` and nothing pauses, and `PlaybackService` keeps audio going with
  lock-screen controls. The activity stops and the `SurfaceView` surface is destroyed:
  `ExoPlayerImpl.surfaceDestroyed` → `setVideoOutputInternal(null)` (ExoPlayerImpl.java:3535-3540).
  With no output surface, `VideoFrameReleaseControl.getFrameReleaseAction` returns `SKIP` for every
  frame (VideoFrameReleaseControl.java:386-397). Skipped frames go to `skippedOutputBufferCount`
  (MediaCodecVideoRenderer.java:2214-2218), never to `renderedOutputBufferCount`. The renderer still
  reports ready (`frameReadyWithoutSurface`, :311), so `isPlaying` stays true. The `viewModelScope`
  ticker keeps running in the background.
- What follows, in order:
  1. About 5.5 s later the hardware decoder is `avoid()`ed and persisted, and the title reopens on the
     software decoder, still with no surface.
  2. About 5.5 s after that, the second "stall" leads to `onExhausted`, so the viewer comes back to
     "This film's video can't be decoded on this device" with the audio still running (see H1).
- The same happens on a TV Home press, on Recents, and on a phone with PiP off or unavailable. Every
  codec is affected, H.264 and HEVC included. For the rest of the device's life every later title of
  that codec is software-decoded: HDR is lost, 4K HEVC or AV1 on the TV box becomes a slideshow or a
  failure (see M3).
- Fix, both parts, in `renderedVideoFrames()`:
  - (a) Return `null` while `exo.surfaceSize` has width or height ≤ 0. Surface destroy sets
    `Size.ZERO`, and nothing is attached yet at `UNKNOWN`.
  - (b) Count every output the decoder produced, not only rendered ones:
    `rendered + skippedOutputBufferCount + droppedBufferCount`. A wedged codec produces none of the
    three.
  - In the controller, a `null` must also `watchdog.reset()`. Today `?: return` keeps the old
    baseline, so the first tick after the surface returns would fire at once.

**C2. The last remaining decoder is persisted as avoided, which leaves permanent audio-only playback with no error.**
`DecoderStallController.kt:98-102`
- `avoid()` runs **before** `hasAlternateDecoder()`. On a device with a single decoder for the MIME,
  one stall (true or false) stores that decoder, then reports exhausted. Common case: phones whose
  only AV1 decoder is `c2.android.av1.decoder`; C1 on such a phone is enough to trigger it.
- From then on, `avoidingSelector` returns an empty list for that MIME. `MediaCodecVideoRenderer`
  reports the format unsupported, `DefaultTrackSelector` leaves video unselected, and every title
  of that codec plays audio-only on black.
- The watchdog cannot see it either: `videoDecoderCounters` is null with no video renderer
  enabled, so `tick()` returns early.
- The same end state is reached through repeated Retry presses, since each walks one more decoder
  down the list (see H1).
- Probe `probeOnlyDecoderIsPersistedAsAvoided`: after exhaustion, `avoided("video/av01") ==
  [c2.only.av1.decoder]`.
- Fix:
  - Ask "is there an alternate once *this one* is excluded" before writing anything, and never
    persist when the answer is no.
  - Better, keep the avoidance session-only for the reopen, and persist it only once the alternate
    has produced frames where the stalled one produced none. This one rule also neutralises C1
    (with no surface the alternate fails too, so nothing is stored) and M4 (still or VFR video).

## High

**H1. The exhausted path leaves the player playing under the error screen; the error then flips back and forth and Retry does nothing.**
`DecoderStallController.kt:94-96,100-102`, `PlayerViewModel.kt:178-195`
- A real `PlaybackException` drops the player to `STATE_IDLE`. Here nothing stops it: audio keeps
  playing behind `Failed`, and the TV's `KeepScreenOnWhile(Playing)` drops, so the screensaver can
  come on over running audio.
- The next `isPlaying` edge (the media play/pause key, a rebuffer) overwrites `Failed` with
  `Playing`. The ticker then restarts and about 5 s later shows `Failed` again.
- Probe `probeFailedIsOverwrittenAndPlayerNeverStopped` (VM level, wall clock advanced for real):
  `Failed` → `Playing` → `Failed`, with `stopCalled=false` and `pauseCalled=false` throughout.
- Retry makes it worse: `retry()` → `open(same)` → `DefaultPlayerHandle.open` sees the same set,
  not IDLE, and only republishes, so nothing reloads. `stallController.open()` resets the budget,
  and the next "stall" persists **another** decoder (usually the software one recovery had just
  moved to).
- The existing test `noAlternateDecoderSkipsRecoveryAndReportsExhaustedImmediately` asserts
  `assertFalse(stopCalled)`, which writes this defect into the test suite.
- Fix: before `onExhausted`, call `session.save(); handle.stop()`, the same state a real error
  leaves. Retry then reloads through `openOn`.

**H2. A wrong blacklist cannot be undone.** `StalledDecoders.kt:43-60`
- There is no expiry, no key on firmware or app version, and no control in Settings or System.
- On the TV box's benchmark build, `run-as` is not available, so the only way out is Clear storage,
  which wipes the Telegram session and the local index too.
- A vendor firmware update that fixes the Realtek driver would never be picked up.
- Fix, minimal: store `Build.FINGERPRINT` beside the entries and drop them all when it changes.
  Add a "Reset video decoders" row on the System screen that lists what is avoided. That is an
  Android-only difference (the web has no decoder choice), so write it down per Surface Parity.

## Medium

**M1. Recovery drops the viewer's speed to 1x.**
`DecoderStallController.kt:108-109`, `DefaultPlayerHandleOps.kt:25`
- `handle.stop(); handle.open(...)` is a real reload, and `openReal` floors the rate at
  `setPlaybackSpeed(1f)`. `retry()` corrects this explicitly (`PlayerViewModelDelegates.kt:32-36`,
  whose comment describes this exact trap); recovery does not.
- After recovery the menu still shows 1.5x while playback runs at 1x.
- Fix: have the VM hand the controller a callback that re-applies
  `choicesController.choices.value.speed` after the reopen.

**M2. Dolby Vision is keyed under the wrong MIME.** `DefaultPlayerHandle.kt:187`
- With a DV stream on a display without DV (the Realtek box), media3 queries decoders under the
  alternative MIME `video/hevc` (MediaCodecVideoRenderer.java:879-890).
- `currentVideoMime()` returns `video/dolby-vision`, so the avoidance is stored under a key the
  `video/hevc` query never reads. The retry opens the same HEVC decoder.
- `hasAlternateDecoder("video/dolby-vision")` may also answer false straight away.
- Fix: filter by decoder name alone in `avoidingSelector` (these names are codec-specific), or also
  record under `MediaCodecUtil.getAlternativeCodecMimeType(format)`.

**M3. The "alternate" is not checked against the format.** `AvoidingMediaCodecSelector.kt:41-51`
- `hasAlternateDecoder` counts any remaining decoder. The box's `c2.android.av1.decoder` tops out
  at 2048×2048, so after one AV1 avoidance every 4K AV1 title fails at codec configure
  ("Playback failed") for good.
- A software decoder that is too slow does not loop and is not detected either: media3
  force-renders a late frame every ≥100 ms (VideoFrameReleaseControl.java:523), so the counter keeps
  moving. The result is a silent slideshow on every later title, never a clean failure.
- Fix: pass the current `Format` and require `info.isFormatSupported(format)`. With C2's
  confirm-before-persist rule, a slideshow at least never becomes permanent.

**M4. Still, VFR and ended-video content can trip the watchdog.** `DecoderStallWatchdog.kt:32-39`
- A video track with gaps of 4.5 s or more between frames looks exactly like a wedge on this
  signal. Examples: screen-recorded lessons with VFR or "static" encoding, a single-frame "video"
  over long audio, a video stream that ends before the audio.
- The class doc's "genuinely finished" (`DecoderStallController.kt:19`) is not handled.
- It is rare for films but plausible for courses. It cannot be told apart cheaply from media3's
  public surface, so rely on C2's confirm-before-persist rule rather than on a better heuristic.

**M5. Premise check: the report does not show the wedge happening while `isPlaying` was true.**
- The logged signature (`C2BqBuffer: last successful dequeue was 27 s ago`, with `kStatusRetry`
  climbing) is also what a Codec2 decoder prints while **paused**. The renderer stops releasing
  frames, the codec fills its output buffers and retries dequeueing forever. The repro path was
  "pause → Back → Back", and 27 s lines up with the time since pausing.
- The user-visible freeze (≈2.6 s + 1.4 s on the main thread on leaving the player) reproduced
  unchanged after the fix, on a healthy decoder. It is not addressed here, and the plan's scope item
  about surface teardown was deferred.
- **Device check, not done here:** pause an AV1 title and an H.264 title for 30 s on the box and
  look for the same log lines. Then confirm, once, that frames froze while `isPlaying` was true
  (a per-tick debug log of the counter would do).

## Low

- **L1.** `DecoderStallController.kt:36` uses the wall clock (`System::currentTimeMillis`).
  - An NTP jump on a freshly booted box can shorten or stretch the window.
  - It also makes the controller untestable at VM level under virtual time: my first VM probe never
    fired until I added `Thread.sleep`.
  - Fix: use `SystemClock::elapsedRealtime`, or count ticks.
- **L2.** `PlayerViewModelOpen.kt:34`: `stallController.open()` runs on the same-set reopen that
  every rotation performs, so a rotation hands out a fresh retry (and one more persisted decoder).
  Gate it on `!sameTitle`.
- **L3.** `PlayerFactory.kt:142-146`: the doc says "Video decoders come from avoidingSelector",
  but `setMediaCodecSelector` also feeds the audio MediaCodec renderer. It is harmless (no audio
  keys are ever stored), but the doc is wrong.
- **L4.** In-memory default parameters on production signatures (`buildPlayer`,
  `renderersFactory`, `PlayerViewModel`) mean a missed argument silently stops persisting. This
  matches the existing `SummarySource.None` precedent, so it is acceptable.
- **L5.** Coverage gap: a wedge while BUFFERING (right after a seek, or before the first frame)
  never ticks. The viewer gets a spinner until media3's stuck-buffering detector fires at
  600 000 ms (ExoPlayer.java:232), then "Playback failed". Consider it once M5 settles whether
  wedges are real.
- **L6.** `@Volatile` in `VideoDecoderNameListener` is unnecessary (analytics callbacks arrive on
  the application thread). Harmless.

# Checklist answers

**(a) False positives.**
- Paused, buffering, rebuffering, seeking, suppressed audio focus, end of stream, first frame not
  yet rendered: safe. Each makes `isPlaying` false; seeking goes to masked BUFFERING
  (ExoPlayerImplInternal.java:1753).
- Speed changes: safe.
- Audio-only titles: safe (null counters).
- PiP: safe (the surface is kept).
- TV screensaver while playing: safe (`KeepScreenOnWhile(Playing)`).
- **Not safe:** a detached surface (lock screen, TV Home, Recents) → C1; still, VFR or ended video
  → M4.
- The threshold (4.5 s, 1 s tick, about 5–5.5 s effective) is reasonable for a real wedge. The
  flaws are in the signal and the persistence, not the number.
- Resets:
  - Across seeks and new titles: correct.
  - Across a surface loss: not reset (C1).
  - On a rotation: it also resets the retry budget (L2).

**(b) Avoidance.**
- The name recorded is the last `onVideoDecoderInitialized`. That is correct: `stop()` disables
  the renderers, so every reopen re-initializes a codec and reports its name. The one mismatch is
  the MIME for Dolby Vision (M2).
- Avoidance is per MIME, and H.264/HEVC are untouched unless a stall fires on them, which C1 makes
  easy.
- A software decoder that cannot keep up gives a slideshow, not a clean failure (M3). It does not
  loop within a title, but across titles and Retry presses it walks down the decoder list (C2, H1).
- No undo (H2).

**(c) Reopen path.**
- Resumes at the same, live position: good.
- The audio override lives in `trackSelectionParameters` and survives. `AudioChoiceController`
  ignores the synthetic empty `Tracks`.
- Subtitles are app-side VTT keyed by position, so they are unaffected. Metadata is reapplied.
- Speed is lost (M1).
- No duplicate progress write: the pause-save after `stop()` sees IDLE, so a null position.
- No second player or surface; renderers re-query the selector because `availableCodecInfos` is
  cleared on release (MediaCodecRenderer.java:1128).
- Phone and TV share the VM path.
- Preload interaction (branch not here): recovery bypasses `PlayerViewModel.open`, so
  `preloadController.startTitle` is not re-run (good). If the preload branch's "title open" pause
  keys off player IDLE, `currentMediaItem` or `isPlaying`, the momentary `stop()` could release it
  mid-recovery. Check this at merge.

**(d) Threading.**
- Counters, `videoFormat` and the ticker are all on Main, which is the player's application
  thread: the player is built on `Dispatchers.Main.immediate` and `getVideoDecoderCounters()` calls
  `verifyApplicationThread`. `ensureUpdated()` is the right way to synchronize the counters.
- Selector reads of `SharedPreferences` happen on the playback thread, which is fine (small,
  cached).
- The ticker lives in `viewModelScope` and is stopped by `stop()` and `onCleared`. It is **not**
  lifecycle-aware, which is what lets it run in the background (C1).

**(e) Tests and style.**
- Green.
- `pausingStopsTheWatchdogSoAPausedFilmIsNeverRecoveredFrom` is vacuous. It never advances time
  after the pause. Under a mutation that makes `onPlayingChanged(false)` a no-op it still
  **passes**; only an incidental `UncompletedCoroutinesError` in `risingFrameCounts…` fails.
  Advance time after pausing and assert.
- Missing tests:
  - a VM-level exhaustion test (state and player stop);
  - a speed-after-recovery test;
  - a `renderersFactory` wiring test (a Robolectric `ShadowMediaCodecList` with two decoders, one
    avoided, expecting the video renderer to report the other).
- Style: prose "why" comments, no plan or phase references. `DefaultPlayerHandle.kt` is 224 lines
  and `PlayerViewModel.kt` 206 (both were already near 200 before this change).

# Device-only verification

1. M5: the log signature while merely paused, and frames actually frozen while `isPlaying` was true.
2. C1: lock the tablet or press TV Home during H.264 playback, wait 15 s, then check
   `logcat -s DecoderStall` and `shared_prefs/stalled_decoders.xml`. Expected today: a hardware
   decoder gets stored. Run this on a scratch device only, since it poisons the prefs.
3. The box's decoder order once `c2.realtek.video.av1.decoder` is removed. Is the next one
   `c2.android.av1.decoder` or `OMX.realtek.video.dec.av1` (same hardware)?
4. Throughput of `c2.android.av1.decoder` on 1080p HDR10 10-bit AV1 on the box: dropped frames in
   the stats overlay, and HDR output.

# Probes (scratchpad only, nothing written to the worktree except this report)

- `…/scratchpad/probe/android/feature/player/src/test/kotlin/DecoderStallProbeTest.kt`
  - `probeOnlyDecoderIsPersistedAsAvoided`: passes; shows C2.
  - `probeFailedIsOverwrittenAndPlayerNeverStopped`: passes; shows H1. Output:
    `Failed(...)` → `Playing` → `Failed(...)`, `stopCalled=false`.
- Mutation: removing `stopTicking()` for `isPlaying=false` leaves the pause test green (e).
- media3 1.10.1 sources used for the citations: `…/scratchpad/m3/`.

# Recommended actions

1. C1: gate on the surface, count all decoder outputs, and reset on `null`.
2. C2: check for an alternate before persisting; persist only after the alternate renders frames.
3. H1: stop the player (after saving) before `onExhausted`; fix the test that asserts the
   opposite.
4. H2: key entries on the firmware fingerprint, and add a reset row on the System screen.
5. M1: restore speed after the reopen.
6. M2 and M3: key by decoder name or alternative MIME, and check format support for the alternate.
7. Run device checks 1–2 before merging. If M5 shows no wedge while playing, reconsider whether to
   ship the watchdog at all versus the surface-teardown freeze the user actually saw.

# Unresolved questions

- Is the Realtek wedge ever observed with `isPlaying` true (M5)?
- Does the library hold Dolby Vision, 4K AV1, or VFR screen-recorded lessons (sizes M2–M4)?
- Should background audio with the screen locked stay a supported mode on TV (Home), or should the
  TV pause on `ON_STOP` as Android TV guidance expects? Pausing there would also remove the TV side
  of C1.
