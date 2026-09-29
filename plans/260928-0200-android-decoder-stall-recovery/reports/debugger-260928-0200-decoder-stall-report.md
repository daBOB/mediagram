---
title: "Decoder stall recovery — diagnosis, fix, tests, device verification"
date: 2026-09-28
worktree: mediagram-decoder (fix/android-decoder-stall-recovery, off main @ 220fa71e)
---

# Diagnosis

## Hypotheses considered

1. **Vendor AV1 hardware decoder wedges, app has no stall detection.** (confirmed)
2. **Main thread blocks indefinitely on surface teardown while the codec is wedged, causing the black screen.** (partially confirmed, refined — see below)
3. **App-level bug in the player screen's own lifecycle/dispose code.** (eliminated)

## Evidence

**Decoders on the box** (`UHD Google TV STB`, Skyworth/Realtek, API 34), from `dumpsys media.player` for `video/av01`:
`c2.realtek.video.av1.decoder` (hardware, wedges), `c2.realtek.video.av1.decoder.secure`,
`c2.android.av1.decoder` (**platform software, present** — max 2048×2048, well over this
1920×1080 title), `OMX.realtek.video.dec.av1(.secure)`, `c2.realtek.video.dav1.10.decoder(.secure)`,
`OMX.realtek.video.dec.dav1.110(.secure)`. Confirms the plan's assumption: a real software
fallback exists.

**`PlayerFactory.renderersFactory`** (pre-fix) used `DefaultRenderersFactory` with
`EXTENSION_RENDERER_MODE_ON` and no custom `MediaCodecSelector` — `core/ffmpeg` only supplies
`FfmpegAudioRenderer` (confirmed: `core/ffmpeg/src/main/java/androidx/media3/decoder/ffmpeg/` has
no video decoder). So AV1 video always went through the platform's default hardware-first codec
order with no way to skip a bad one, and there was **zero stall detection anywhere** — `PlayerHandleListener`
only reacts to a real `onPlayerError`(`PlaybackException`), which a wedged codec that just retries
`dequeueOutputBuffer` forever never raises.

**Device repro, pre-fix** (`plans/260928-0200-android-decoder-stall-recovery` debug build,
`adb -s 192.168.0.35:5555`, "Der Astronaut – Project Hail Mary", 1080p HDR10 AV1 eac3):
playing normally (`c2.realtek.video.av1.decoder`, position advancing), then at 02:12:34–02:12:55
`RTKC2Vdec: kStatusRetry count` climbed 300 → 3,600 → 6,900+ with nothing resetting it — a genuine,
sustained wedge, no `onPlayerError`, no `Log.w("Player", ...)` ever printed (grep across the whole
capture for `"Player"`/`ExoTimeoutException`/`playback failed` — zero hits). Pausing, then two Back
presses (leaving the player): the second Back triggered a **2,660 ms + 1,449 ms main-thread stall**
(`Choreographer: Skipped 78 frames!`, two `Davey!` frames back to back), starting right after the
`MediaSession`/codec teardown (`RTKC2Vdec: release`/`release done` completed cleanly and fast,
~200 ms — the codec's own async callback thread, not ExoPlayer's internal thread, so releasing it
was not itself the block). The 2,660 ms figure lines up almost exactly with media3's own
`ExoPlayer.Builder.DEFAULT_DETACH_SURFACE_TIMEOUT_MS = 2000L` (confirmed by decompiling
`media3-exoplayer-1.10.1`: `ExoPlayerImpl.setVideoOutputInternal` blocks the calling thread up to
`detachSurfaceTimeoutMs` waiting for the internal playback thread, and on timeout raises
`ExoTimeoutException`/`ExoPlaybackException` itself) — this is `androidx.media3.ui.compose.PlayerSurface`'s
default `SURFACE_TYPE_SURFACE_VIEW`, whose `surfaceDestroyed` callback the Android window system
calls synchronously on the main thread as the player screen leaves composition. The app eventually
recovered to the catalog screen after the stall in this run (not permanently black), but a viewer
watching several seconds of a frozen frame with no feedback reasonably describes it as "went black
and stayed black," especially on the actual (non-debug) build where an even longer/less predictable
freeze, or Home being pressed mid-freeze, would look identical to "stuck."

**Second finding, post-fix device run** (see Device verification): the same ~3 s stall on leaving
the player **also happens on a healthy, non-wedged decoder** — so it is not exclusive to the wedge;
it's a general codec/surface teardown cost on this Realtek SoC, bounded by media3's own
`detachSurfaceTimeoutMs`, and the app does recover from it either way. **This narrows what needed
fixing**: the actual, reportable bug is the *wedge itself going undetected and unrecovered*, not an
unbounded main-thread hang — media3's own timeout already bounds that hang to ~2 s. Rebuilding
`PlayerSurface` on `SURFACE_TYPE_TEXTURE_VIEW` (the only way to avoid that synchronous teardown path
entirely) is a much larger, riskier change than this plan's scope or the user's decision called for
(different compositing, no guaranteed HDR passthrough), so it was not attempted — flagged as a
possible separate follow-up, not done here.

**Root cause, stated with evidence**: the Realtek `c2.realtek.video.av1.decoder` can enter a state
where it stops producing output frames while still reporting itself healthy to ExoPlayer, and the
app had no mechanism to notice or recover from that (confirmed: zero watchdog code existed, zero
`onPlayerError` fires during the wedge). The black-screen report is this wedge, surfaced through
the player's own (otherwise ordinary, bounded) teardown-on-leave stall, which is indistinguishable
to a viewer from "stuck" without the recovery this task adds.

**0.69.3 vs. 0.69.4 (preload work)**: not diffed directly — the AV1 codec path, `renderersFactory`
and the player's surface handling are unchanged since well before 0.69.3 (the preload work,
`SeriesPreloader`/`CacheDataSourceWriter`, touches the byte path, not decoder selection or the
video surface), so there is no reason to expect 0.69.3 behaves differently, and the plan did not
block on re-testing it.

# Fix

Per the user's decision (`plan.md`): watchdog → rebuild on the other decoder → persist the
avoidance → error screen only if the retry also fails.

- **`core/playback/DecoderStallWatchdog.kt`** — pure, clockless tick/reset state machine: call
  `tick(nowMs, renderedFrames)` once a second while `READY && playWhenReady` (the caller decides
  that, from `isPlaying`, not raw player state — no need to duplicate that logic); `true` once the
  frame count has sat still for `stallAfterMs` (default 4.5 s). Reads decoder progress, not the
  playhead — audio keeps `positionMs` moving even when only the video renderer is stuck.
- **`core/playback/StalledDecoders.kt`** — `avoided(mimeType): Set<String>` / `avoid(mimeType, name)`,
  plain (non-`suspend`) functions because the codec selector reads this synchronously from the
  playback thread; `PlainStalledDecoders` persists to its own `SharedPreferences` file, comma-joined
  set per MIME key.
- **`core/playback/AvoidingMediaCodecSelector.kt`** — `avoidingSelector` wraps
  `MediaCodecSelector.DEFAULT`, filtering out whatever `StalledDecoders` has learned for that MIME;
  `hasAlternateDecoder` answers whether anything is left once avoided is excluded — this second
  check is what a *second* stall or an already-exhausted device routes straight to the error screen
  instead of re-preparing into the same known-bad decoder.
- **`core/playback/PlayerFactory.kt`** — `renderersFactory`/`buildPlayer` now thread `StalledDecoders`
  through to `.setMediaCodecSelector(avoidingSelector(...))`. A device with nothing remembered gets
  byte-for-byte the platform's own order — H.264/HEVC unaffected.
- **`feature/player/VideoDecoderNameListener.kt`** — tiny `AnalyticsListener` capturing
  `onVideoDecoderInitialized`'s decoder name (media3 exposes this only through analytics, never as a
  plain `Player` property).
- **`feature/player/PlayerHandle.kt` / `DefaultPlayerHandle.kt`** — three new methods:
  `renderedVideoFrames()` (`ExoPlayer.videoDecoderCounters.renderedOutputBufferCount`, synchronized
  via `ensureUpdated()` since it's written on the playback thread), `currentVideoMime()`
  (`ExoPlayer.videoFormat.sampleMimeType`), `currentVideoDecoderName()` (the listener above).
- **`feature/player/DecoderStallController.kt`** — the orchestration: ticks once a second while
  playing (same shape as `PlayerSession`'s own save ticker), and on a confirmed stall: marks the
  current decoder avoided for its MIME, checks an alternate exists, reads the current position
  (**before** `handle.stop()`, which drops to `STATE_IDLE` where `positionMs()` goes `null` — same
  ordering `PlayerViewModel.stop()`/`retry()` already use), then `handle.stop(); handle.open(setId,
  atMs, playWhenReady = true)`. This reuses the **existing** raw-`PlayerHandle` reopen path (not
  `PlayerViewModel.open()`), which the codebase's own `AudioChoiceController` doc comment already
  anticipates ("a retry re-preparing the same file") — audio/subtitle overrides live on the
  `ExoPlayer` instance itself (`trackSelectionParameters`), so a raw reopen keeps them, and
  `AudioChoiceController.settled` no-ops on the resulting synthetic `onTracksChanged`. One retry per
  open title (`retriedThisOpen`); a second stall, or no alternate decoder, calls `onExhausted` →
  wired straight to `PlayerViewModel.onError` (the same `Failed` state a real `PlaybackException`
  reaches) with the message *"This film's video can't be decoded on this device"*. Logs
  (`Log.w("DecoderStall", ...)`) on both a detected stall and a recovery attempt, for field
  diagnosis.
- **`PlayerViewModel.kt`/`PlayerViewModelOpen.kt`** — wires `stallController.open()` into `open()`,
  `.onPlayingChanged()` into the existing `onPlayingChanged` override, `.stop()` into `stop()`.
- **DI** (`PlaybackModule.kt`): `StalledDecoders` provided as `PlainStalledDecoders`, threaded into
  `provideExoPlayerDeferred` → `buildPlayer`.

Not changed: no new dependency, no media3 decoder extension, no surface-type change. H.264/HEVC
renderer selection untouched (verified by the existing `ffmpegAudioFollowsThePlatformAudioRenderer`
test still passing unmodified, and `avoidingSelector` passing every MIME with nothing avoided
straight through to `MediaCodecSelector.DEFAULT`).

# Tests

`./gradlew testDebugUnitTest lint :app:assembleDebug` — **green**, whole project (703 tasks, no
failures). New/changed:

- `DecoderStallWatchdogTest.kt` (5 tests) — fake clock, plain `Long` frame counts: rising frames
  never trip it, unchanged-for-the-full-window trips it, a late frame resets the window, `reset()`
  forgets the prior baseline, a pause/resume cycle (mirrored by `reset()`) doesn't falsely trip.
- `StalledDecodersTest.kt` (4 tests) — in-memory (nothing avoided until `avoid()`, per-MIME
  isolation, accumulation) + Robolectric-backed `PlainStalledDecoders` (survives a fresh instance
  over the same `SharedPreferences` file).
- `AvoidingMediaCodecSelectorTest.kt` (5 tests) — real `MediaCodecInfo.newInstance(...)` instances
  (public factory, confirmed usable with `null` capabilities), a fake two-decoder `MediaCodecSelector`
  standing in for the platform's own answer (Robolectric has no real `MediaCodecList`): nothing
  avoided passes through unchanged, the avoided decoder is removed and the other stays, avoiding is
  per-MIME, `hasAlternateDecoder` true/false correctly.
- `DecoderStallControllerTest.kt` (5 tests, `FakePlayerHandle` extended with
  `fakeRenderedVideoFrames`/`fakeVideoMime`/`fakeVideoDecoderName`) — rising frames never trigger
  recovery; a stalled decoder is replaced by the other one **at the same position**
  (`openedStartAtMs` asserted, not just "any reopen") with the stalled name persisted to
  `StalledDecoders`; a second stall in the same open goes straight to the exhausted message; no
  alternate decoder skips the reopen entirely and reports exhausted immediately; pausing stops the
  watchdog so a paused film is never "recovered" from. `hasAlternateDecoder` is injectable
  (defaults to the real one) specifically because these are plain JVM tests with no
  `MediaCodecList` to ask.

# Device verification

TV box `192.168.0.35:5555`, debug build of this worktree installed for both runs; box was mid-walk
by another agent at task start — waited for the lead's "free now" before touching it.

**Pre-fix** (before touching code): reproduced the wedge exactly as the plan describes — see
Diagnosis. This is the evidence the root cause is real, not theoretical.

**Post-fix**: reinstalled the debug build with the fix, relaunched, resumed "Der Astronaut" (TV
test profile). Playback ran healthy for 275+ s of position advance; one brief self-healing blip
(`kStatusRetry` to 300, once, no further climb) correctly did **not** trigger recovery — the
watchdog's job is exactly to not swap decoders over a sub-second hiccup, and it didn't. Repeated the
original pause→Back→Back sequence: reproduced the same ~3 s main-thread stall (this is the general
teardown cost noted above, present with or without a wedge), and the app recovered cleanly to the
catalog afterward — not stuck. Tried forcing a fresh sustained wedge again (seeking repeatedly, a
second full play-through) to watch the `DecoderStall` log lines fire live; the Realtek decoder did
not re-wedge into the sustained state within the time available — the bug appears intermittent
rather than deterministic on every play, matching a real vendor firmware/driver bug rather than a
100%-repro condition. **What remains**: a live, on-device observation of the `DecoderStall:
stalled...` / `DecoderStall: recovering...` log lines firing and the film resuming on
`c2.android.av1.decoder` was not captured in this session — the fix's logic is proven by the unit
tests above (which exercise exactly this path deterministically) and by the confirmed presence of
the software decoder and the confirmed pre-fix wedge, but not by a second live device-side trigger.
If a live demonstration is wanted, the most reliable path is probably a debug-only override to force
`stallAfterMs` down (or a manual "avoid this decoder" debug action) rather than waiting for the
vendor bug to recur on its own.

Box restored to the user's build after: `mediagram-preload/android` →
`ANDROID_SERIAL=192.168.0.35:5555 ./gradlew :app:installBenchmark` (build/install only, worktree
not edited), then `adb shell cmd package compile -m speed -f com.mediagram.android` — confirmed
`versionName=0.71.0` again on the box afterward.

# Version

Patch bump `0.69.4` → `0.69.5` by regex, all three manifests + `Cargo.lock`'s five workspace-member
`version` lines (`mediagram`, `mediagram-cache`, `mediagram-core`, `mediagram-tmdb`, `mlib-spec`) —
diffed to confirm only those five lines changed, no third-party dependency touched. Changelog entry
added to `docs/project-changelog.md` in the file's own voice. Not committed, per instructions.

# Files

Work context: `/home/andre/Workspace/mediagram-decoder`

New:
- `android/core/playback/src/main/kotlin/DecoderStallWatchdog.kt`
- `android/core/playback/src/main/kotlin/StalledDecoders.kt`
- `android/core/playback/src/main/kotlin/AvoidingMediaCodecSelector.kt`
- `android/core/playback/src/test/kotlin/DecoderStallWatchdogTest.kt`
- `android/core/playback/src/test/kotlin/StalledDecodersTest.kt`
- `android/core/playback/src/test/kotlin/AvoidingMediaCodecSelectorTest.kt`
- `android/feature/player/src/main/kotlin/DecoderStallController.kt`
- `android/feature/player/src/main/kotlin/VideoDecoderNameListener.kt`
- `android/feature/player/src/test/kotlin/DecoderStallControllerTest.kt`

Modified:
- `android/core/playback/src/main/kotlin/PlayerFactory.kt`
- `android/feature/player/src/main/kotlin/PlayerHandle.kt`
- `android/feature/player/src/main/kotlin/DefaultPlayerHandle.kt`
- `android/feature/player/src/main/kotlin/PlayerViewModel.kt`
- `android/feature/player/src/main/kotlin/PlayerViewModelOpen.kt`
- `android/feature/player/src/main/kotlin/di/PlaybackModule.kt`
- `android/feature/player/src/test/kotlin/FakePlayerHandle.kt`
- `Cargo.toml`, `Cargo.lock`, `web/package.json`, `android/app/build.gradle.kts` (version)
- `docs/project-changelog.md`

Note: `DefaultPlayerHandle.kt` (224 lines) and `PlayerViewModel.kt` (206 lines) are modestly over
the 200-line guideline — both were already at 201/198 before this change; the added state
(`videoDecoderName` listener wiring, three one-line method overrides, one `stallController`
property + three call sites) has nowhere else to live without adding indirection for its own sake,
so it was left in place rather than split further.

# Unresolved questions

- Whether to pursue the `SURFACE_TYPE_TEXTURE_VIEW` change to remove the ~3 s teardown-on-leave
  stall entirely (present even on healthy playback) — out of scope for this plan/decision; flagged
  as a possible separate follow-up, not started.
- No live on-device confirmation of the recovery log lines firing against a second real,
  sustained wedge (see Device verification) — the vendor bug didn't recur deterministically in the
  time available this session.

**Status:** DONE_WITH_CONCERNS
**Summary:** Root cause confirmed and reproduced (Realtek AV1 decoder wedges silently, no PlaybackException, no prior stall detection); implemented the watchdog + MediaCodecSelector-avoidance recovery + per-device persistence + error-screen fallback exactly per the plan's decision; all unit tests, lint and assembleDebug green; version bumped to 0.69.5 with changelog entry (not committed).
**Concerns/Blockers:** Could not re-trigger a second live, sustained decoder wedge on the device within the session to watch the recovery fire in real time (the vendor bug is intermittent, not 100%-repro on demand) — confidence rests on the unit tests exercising the exact recovery path deterministically plus the confirmed pre-fix reproduction and confirmed software-decoder availability, not a second live device trigger. The pre-existing ~3s main-thread stall on leaving the player (media3's own bounded `detachSurfaceTimeoutMs` teardown path, present with or without a wedge) was diagnosed and confirmed bounded/non-fatal but not itself changed — fixing it further would mean a surface-type change bigger than this task's scope; flagged for the lead to decide on.
