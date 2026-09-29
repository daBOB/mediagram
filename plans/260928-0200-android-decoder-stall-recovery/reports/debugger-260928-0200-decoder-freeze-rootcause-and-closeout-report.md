---
title: "Decoder stall recovery — closeout: watchdog removed, freeze root-caused to debug-build Compose cost, not the player"
date: 2026-09-28
worktree: mediagram-decoder (fix/android-decoder-stall-recovery, off main @ 220fa71e)
status: closed — no code ships from this branch
---

# Summary

The plan's premise ("the Realtek AV1 decoder wedges silently mid-playback") did not
hold up under direct device measurement. The stall watchdog and decoder-avoidance
machinery built for it has been **removed in full**. The user-visible freeze
("pause → Back → Back → black screen") is real, but its cause is a **debug-build-only**
Compose measure/layout cost paid every time Home (re)appears — present on **any**
navigation back to Home, not specific to the player, and **absent entirely** on the
benchmark (release, R8 + AOT speed-compiled) build the user actually runs. Nothing in
this worktree ships; it is left with only the plan and this report.

# Part 1 — why the watchdog was removed

The original diagnosis (first report, superseded) read `RTKC2Vdec: kStatusRetry
count` climbing into the thousands, captured right after a pause, as evidence the
hardware AV1 decoder had wedged mid-playback. A code review (see
`code-reviewer-260928-0248-decoder-stall-review-report.md`) flagged this as
unverified (**M5**) and found the shipped implementation actively dangerous if the
premise were wrong: unconditional persistence of a wrongly-blacklisted decoder
(**C1**), the last-remaining decoder getting persisted as avoided with no undo
(**C2**), the exhausted path leaving audio playing under a `Failed` screen that
Retry could not clear (**H1**), and no way to undo a bad blacklist short of Clear
Storage (**H2**).

Device evidence (TV box `192.168.0.35:5555`, this worktree's debug build) settled it:

- **(a) Active playback, ~2.5 min each, no pause**: AV1 (`c2.realtek.video.av1.decoder`)
  and H.264 both showed **zero** `kStatusRetry`/`C2BqBuffer` lines and smooth,
  continuous position advance. No wedge was ever observed while `isPlaying` was true,
  on either codec.
- **(b) Paused 30–40 s**: **both** codecs showed the identical `kStatusRetry`/
  `C2BqBuffer` signature, climbing into the thousands (H.264 to 5,100+, AV1 to
  12,900+) — this is what a Codec2 decoder logs whenever nothing is dequeuing its
  output, i.e. what "paused" looks like, not what "wedged" looks like. The original
  capture was taken right after a pause and was almost certainly this, misread.
- **(c) Reproduced pause → Back → Back**: no root on this box, so `kill -3` /
  `run-as kill -3` (confirmed it does trigger a tombstoned dump) could not be read
  (`/data/anr/trace_00`, permission denied). Fallback: `top -H -p <pid>` sampled
  through the freeze showed the **main thread running at 96–100 % CPU**, not
  sleeping/blocked — ruling out a simple lock/timeout wait (e.g. media3's
  `detachSurfaceTimeoutMs`) as the mechanism. Reproduced on a healthy, unwedged
  decoder too, and self-recovered both times (not a permanent hang).
- **(d) Fallback decoder check**: seeded `stalled_decoders.xml` via `run-as` to
  exclude `c2.realtek.video.av1.decoder` directly, relaunched, and confirmed from
  logcat (`CCodec: allocate(c2.android.av1.decoder)`) that the platform falls
  through to the **software** decoder, not `OMX.realtek.video.dec.av1` (same
  silicon, different API name) — so avoidance-by-name would not have been
  self-defeating on this device. Software decode kept pace at ~0.98× realtime on
  this 1080p HDR10 file.

Given (a)+(b), there is no evidence the decoder ever wedges during genuine playback
on this device; given (c), the "blocked surface" theory the original fix implicitly
leaned on does not match the thread state either. Per the lead's decision, all of the
following were removed and the worktree returned to a clean diff against
`main @ 220fa71e`:

- `core/playback/{DecoderStallWatchdog,StalledDecoders,AvoidingMediaCodecSelector}.kt`
  + their tests
- `feature/player/{DecoderStallController,VideoDecoderNameListener}.kt` + its test
- The three `PlayerHandle`/`DefaultPlayerHandle` methods added only for this
  (`renderedVideoFrames`, `currentVideoMime`, `currentVideoDecoderName`)
- DI wiring in `PlaybackModule.kt`, the `PlayerViewModel`/`PlayerViewModelOpen.kt`
  wiring, the `FakePlayerHandle` test fields
- The `0.69.5` version bump (all four manifests + `Cargo.lock`) and its changelog
  entry — reverted; nothing ships from this branch

`git status` in this worktree now shows only `plans/260928-0200-android-decoder-stall-recovery/`
(this plan directory) untracked; every tracked file matches `main`.

# Part 2 — root-causing the actual freeze

## Method

Could not get a readable stack trace (no root; `kill -3` output is tombstoned,
`/data/anr/trace_00` is `system`-owned and unreadable to `shell`/`run-as`). Used two
methods instead, both usable without root:

1. **Targeted timing logs** (`System.nanoTime()`, temporary, reverted after use)
   around the actual player-teardown call chain: `TvPlayerBack`'s `onLeave()`,
   `PlayerLifecycle`'s `onDispose`, and `PlayerViewModel.stop()` broken into five
   sub-steps (`handle.stop()`, `playbackServiceController.stop()`,
   `choicesController.reset()`, etc.).
2. **Method-sampling trace**: `adb shell am profile start --sampling 1000
   com.mediagram.android /data/local/tmp/leave.trace` (no root needed) around a
   repro, `am profile stop`, `adb pull`. No `dmtracedump` on this machine or the
   box, so the Dalvik "SLOW" trace format (documented, `magic=SLOW version=3
   record_size=14`) was parsed by hand (self-contained script, not shipped) to
   reconstruct per-method self/inclusive time on the main thread via a call-stack
   simulation from the enter/exit records.

## Finding

On the **debug build**:

- `onLeave()` itself: **1.4 ms**.
- `PlayerLifecycle.onDispose` (which runs the entirety of `PlayerViewModel.stop()`:
  `handle.stop()`, `playbackServiceController.stop()`, `session.clear()`,
  `marksController.reset()`, `choicesController.reset()`, `upNextController.stop()`):
  **49 ms** total, all sub-steps individually well under 25 ms.
- Then **~5 s** of `Davey!`/`Choreographer: Skipped N frames!` frames follow — after
  our own code has already finished running.
- **Differential test**: from Home, opened the Movies tab, pressed Back — never
  touched the player at all. **Identical** magnitude and character: 4 Davey frames
  (845 ms, 858 ms, 2350 ms, 1591 ms) over ~4.5 s.
- **Method trace** of that Movies→Back repro: virtually all sampled main-thread time
  sits inside generic Compose measure/layout/placement machinery —
  `SnapshotStateObserver.observeReads` (815 calls), `Placeable.placeAt` (822 calls),
  `LayoutModifierNodeCoordinator.measure` (396 calls),
  `MeasurePassDelegate.remeasure` (196 calls) — matching `Choreographer.doFrame`'s
  own ~4.8 s inclusive time almost exactly. Every **application-specific** method
  (`catalog.NextUpKt`, `HomeShelvesKt`, `MagazineHomeKt`, Coil request building,
  etc.) totals **under 200 ms combined** across the whole trace.

This is hundreds of `LayoutNode`s genuinely being measured and placed from scratch —
not a slow computation, not a blocked thread, not anything the (now-removed)
decoder-stall code touched. It reproduces on **any** return to Home, with or without
the player, on this device, on the debug build.

## Debug vs. benchmark — the numbers the lead asked for

Restored the user's build first (`mediagram-preload/android`,
`ANDROID_SERIAL=192.168.0.35:5555 ./gradlew :app:installBenchmark` +
`cmd package compile -m speed -f com.mediagram.android`; confirmed
`versionName=0.72.1`, the user's build; that worktree was not edited). Repeated both
repros 3× each, TV test profile, logcat running throughout:

| Repro | Run 1 | Run 2 | Run 3 | Davey count | Skipped-frames count |
|---|---|---|---|---|---|
| Home → Movies → Back | clean | clean | clean | **0** | **0** |
| Playing film, pause → Back → Back | clean | clean | clean | **0** | **0** |

`grep -c "Davey!"` and `grep -c "Choreographer: Skipped"` across the entire benchmark
logcat session (both sets of repros, plus app startup) returned **0** for both, on
both patterns, for all six runs combined. This is not "faster" than the debug
build's ~5 s — it is **not measurably slow at all**: no frame during either
transition crossed the ~700 ms Davey threshold or the multi-frame skip threshold, on
this same physical device, same session, same content. A rapid post-Back screenshot
confirmed the catalog is already the visible content immediately after the second
Back (no black frame observed on the benchmark build in any of the runs).

**Conclusion**: the ~2–5 s freeze reported by the user is a debug-build artifact
(unoptimized bytecode, no R8 minification/inlining, Compose's own debug-only
recomposition bookkeeping, no `compile -m speed` AOT) — not something the user, who
runs the benchmark/release build, actually experiences on this device for these two
repros. This does not mean nothing is wrong: the underlying behavior — Home's full
node tree being remeasured from scratch on every appearance — is real and present in
both builds; the release build is simply fast enough on this box's CPU, once
optimized and AOT-compiled, that it never crosses a visible-jank threshold. A
lower-spec box, a heavier Home state (more shelves, more "Recently added" items —
this library has 935), or future UI growth could still make this visible on release
builds without ever touching player or decoder code.

# Part 3 — follow-up for the TV home redesign (not implemented here)

Addressed to whoever next touches `ui-tv`'s Home/catalog navigation, not for this
branch:

**What's happening**: Home's full Compose tree — hero carousel, every shelf, every
card, each with its own nested TV-focus/overscan/padding modifiers — is being
composed and measured from absolute zero on every appearance, whether returning from
the player, from Movies, or from anywhere else. Both differential-test runs and the
method trace point at the same thing: hundreds of fresh `LayoutNode`s per visit, no
reuse of prior measurement. This is architecturally consistent with Home living in a
branch of a `when` (or similar mutually-exclusive composable swap) that gets torn
down while another branch is showing — every `remember` and cached layout is gone by
the time you come back, so there is nothing to skip re-measuring.

**Recommended direction** (not verified here, scope is real UI-architecture work):

- Keep Home's composition **alive** under whatever is shown over it, rather than
  disposing and recomposing it — e.g. render the player/Movies/other destinations as
  overlays or in a backstack that leaves Home's own composable subtree in place
  (`movableContentOf`/`movableContentWithReceiverOf` for moving a subtree between
  parents without disposing it, or simply not removing Home from composition at all
  and only toggling its visibility/z-order).
- Where Home's own internal lists need to survive a real recomposition (e.g. after a
  genuine data refresh), scope their scroll/expansion state with
  `rememberSaveable`/`rememberLazyListState` keyed stably per row, not recreated
  per-appearance.
- **Measure it the same way this report did**, before and after: `am profile start
  --sampling 1000 <pid> <file>` (no root needed) around the same two repros
  (pause → Back → Back from a playing film; Home → Movies → Back), and check for
  `Davey!`/`Choreographer: Skipped` lines in logcat plus the self/inclusive time
  breakdown from the trace. On debug builds specifically, since that is where this
  is visible at all on this hardware — a fix that only helps the already-invisible
  release path is not verifiable this way. Success looks like: no full remeasure of
  shelves that were already laid out on a previous appearance, and ideally zero
  Davey frames on debug builds too, not just release.
- This is a `ui-tv`/`ui-common` concern (`TvLibrary`/`TvLibraryBranches` and
  whatever the phone/tablet equivalent's own navigation host is) — check surface
  parity before changing only one side, per this project's own `CLAUDE.md` rule
  (the web player is the reference for equivalent behavior, and any phone/tablet
  navigation swap should be checked for the same full-remeasure cost before
  assuming it is TV-only).

# Gate

`./gradlew testDebugUnitTest lint :app:assembleDebug` was green before the watchdog
was removed (699 tasks) and again after removal + revert (matches `main`, nothing
left to test that main doesn't already have). No further build was needed since the
worktree carries no code changes.

# Files

Work context: `/home/andre/Workspace/mediagram-decoder`. `git status --short` shows
only `plans/260928-0200-android-decoder-stall-recovery/` untracked; no tracked file
differs from `main @ 220fa71e`. Nothing committed, per instructions.

# Unresolved questions

- Whether the Home-recomposition cost is visible on release builds on a lower-spec
  or more heavily-loaded device/library than this one (935 "Recently added" items on
  the test account) — not tested here.
- Whether the phone/tablet catalog navigation has the same full-remeasure-on-return
  characteristic — not tested here; flagged in Part 3 for whoever picks this up.
- Whether the Realtek AV1 decoder can wedge under conditions this session's ~5 min
  of combined active playback did not hit (longer sessions, thermal effects, a
  different title) — no evidence either way; the plan's original report was the
  only data point suggesting it, and that data point is now explained by (b) above
  rather than a genuine wedge. If it recurs, capture the retry-count climb
  **before** any pause, not after.

**Status:** DONE
**Summary:** Watchdog/avoidance removed in full (unverified premise, harmful failure modes per review); root-caused the reported freeze to a debug-build-only Compose full-remeasure cost on returning to Home (any navigation, not player-specific) via timing logs + a hand-parsed method-sampling trace; confirmed zero Davey/skipped-frame warnings across 6 repros (3×2) on the user's actual benchmark build. No code ships from this branch; worktree matches `main` except this report. Follow-up for the TV home redesign written up in Part 3.
**Concerns/Blockers:** None blocking closeout. The underlying Home-remeasure cost is real and should be tracked as its own UI-performance item against `ui-tv` (and checked on phone/tablet), even though it wasn't visible enough on this device's release build to reproduce here.
