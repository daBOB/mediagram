# PreloadService "Stop FGS timeout" — root cause, fix, and re-verification

Follow-up to `fullstack-developer-260928-0345-preload-tablet-verification-report.md`,
which flagged `PreloadService` being torn down roughly 55–75s after every start on the
tablet as a possible platform/targetSdk bug. This pass root-caused it, found it is not a
bug, and landed a defensive fix regardless. Device: tablet `caad49da` (test profile,
navigation only, never touched Settings choices — budget stayed at 16 GB throughout).
Worktree: `/home/andre/Workspace/mediagram-preload`, branch `feat/android-film-preload`.
Screenshots: `reports/screenshots/fgs-*.png`.

## Root cause

**Not a bug.** "Stop FGS timeout" is AOSP's own benign bookkeeping log line, fired every
time a `dataSync`/`mediaProcessing`-type foreground service stops for *any* reason —
including the app's own ordinary `stopSelf()` — not evidence of an abuse-prevention kill.

### Diagnostic steps

1. **`dumpsys activity services com.mediagram.android` while the service ran**, sampled
   every 15–20s across a full preload: `isForeground=true foregroundId=4201
   types=0x00000001` — `0x1` is `FOREGROUND_SERVICE_TYPE_DATA_SYNC` — held correctly for
   the service's entire life. The MIUI-only `ForegroundServiceTypeLoggerModule` warning
   ("does not have any types") does **not** reflect the real, AM-tracked `ServiceRecord`,
   which is correctly typed throughout. That warning is a separate, MIUI-proprietary
   analytics hook unrelated to the platform's own foreground-service-type enforcement.

2. **Downloaded AOSP's `ActiveServices.java`**
   (`aosp-mirror/platform_frameworks_base`, `master`) and grepped for the exact log line:

   ```java
   private void maybeStopFgsTimeoutLocked(ServiceRecord sr) {
       final int timeLimitedType = getTimeLimitedFgsType(sr.foregroundServiceType);
       if (timeLimitedType == ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE) {
           return;
       }
       ...
       Slog.d(TAG_SERVICE, "Stop FGS timeout: " + sr);
       mAm.mHandler.removeMessages(ActivityManagerService.SERVICE_FGS_TIMEOUT_MSG, sr);
       mAm.mHandler.removeMessages(ActivityManagerService.SERVICE_FGS_CRASH_TIMEOUT_MSG, sr);
   }
   ```

   Called from three places, all ordinary stop paths: `stopServiceLocked` (a plain
   `stopService`/`stopSelf`), `stopServiceTokenLocked`, and the `stopForeground()`
   handling inside `setServiceForegroundInnerLocked`. None of them is the actual
   abuse-prevention timer — that is a separate function, `onFgsTimeout(ServiceRecord sr)`,
   reached only via the `SERVICE_FGS_TIMEOUT_MSG` handler message actually firing (i.e.
   *not* being removed in time). `maybeStopFgsTimeoutLocked` exists specifically to cancel
   that pending message and update runtime-budget bookkeeping whenever the service stops
   on its own first — it is the log line for "a time-limited FGS just stopped," not "a
   time-limited FGS was killed for running too long."

3. **Clean, isolated repro on the tablet** (nothing else queued, no other navigation):
   started a fresh preload, captured full `adb logcat` continuously. The app's own
   `FilmPreload: film preload: <title> held` completion line landed at `09:03:35.849`;
   "Stop FGS timeout" followed at `09:03:35.968` — **119ms later**. This is
   `PreloadService`'s own `preloader.hasWork.filter { !it }.collect { stopSelf() }`
   (in `onCreate`) firing the instant the queue emptied, not an external kill.

Three prior observations from the earlier verification session (54s, 74s, 75s durations,
inconsistent) are consistent with this: each coincided with whatever the queue was doing
at the time (a film finishing, or in one messier session, a promotion), not a fixed
OS-imposed ceiling — there is no such ceiling being hit here at all.

## Fix landed regardless

Even though today's specific symptom was a misdiagnosis, the finding surfaced a real gap:
`PreloadService.onDestroy()` had no handling for a teardown that happens for a reason
*other than* the two paths it already expects (`onTimeout()`'s documented 6h/24h ceiling,
or its own `hasWork`-empty self-stop). A future genuine cause — a MIUI battery-saver kill,
a policy this device doesn't enforce today, anything else — would have left an
in-progress film reverting to a bare `Preload · N% held` pill instead of the
already-designed `Paused — background limit` + Resume treatment.

`android/feature/player/src/main/kotlin/PreloadService.kt`:

```kotlin
override fun onDestroy() {
    if (preloader.hasWork.value) {
        preloader.pauseForTimeLimit()
    }
    scope.cancel()
    super.onDestroy()
}
```

Guarded on `FilmPreloading.hasWork.value` (read synchronously — it's a `StateFlow`), not
called unconditionally: the *ordinary* self-stop above already reaches `onDestroy()` with
`hasWork` false (nothing left to pause — `pauseForTimeLimit()` would be a harmless no-op
either way, but the guard also closes a narrower race: nothing pauses a film enqueued in
the brief window between the empty-queue read and `onDestroy()` actually running).

Class doc updated to explain both the `onTimeout()` path and this new one, and to name
`maybeStopFgsTimeoutLocked` so a future reader doesn't re-diagnose the same log line as a
bug. No manifest change — the `dataSync` type was never the problem.

## Tests

`android/feature/player/src/test/kotlin/PreloadServiceTest.kt` (new) — two cases, built on
a **real** Robolectric-constructed `PreloadService` (`Robolectric.buildService(...).get()`,
which only constructs the object — no lifecycle callback runs). Deliberately does **not**
call `.create()`: `Hilt_PreloadService.onCreate()` (the generated base class — read directly
from `feature/player/build/generated/ksp/debug/java/player/Hilt_PreloadService.java` to
confirm) calls `inject()` before delegating to our own `onCreate()`, which needs a real
Hilt component this module's test setup does not build (no `hilt-android-testing`, no
`HiltTestApplication`, no precedent anywhere in this codebase for testing a real
`@AndroidEntryPoint` component via Robolectric). `Hilt_PreloadService` does **not** override
`onDestroy()` at all, confirmed from the same generated source — so calling
`service.onDestroy()` directly, after assigning `service.preloader` to a fake, exercises the
real override with no Hilt involvement and no risk of the injection path running.

`FakePreload.kt`'s `FakeFilmPreloading` gained a settable `hasWork` (`setHasWork(Boolean)`)
and a `pauseForTimeLimitCallCount`, both purely additive — every existing test using this
fake is unaffected (`hasWork` still defaults false, `pauseForTimeLimit()` still a no-op
unless a test now opts into counting it).

```
onDestroyPausesForTimeLimitWhenRealWorkIsStillOutstanding   — hasWork=true  → 1 call
onDestroyDoesNothingExtraOnceTheQueueHasAlreadyEmptied      — hasWork=false → 0 calls
```

Both pass. Full gate: `./gradlew testDebugUnitTest lint :app:assembleDebug` — green
(699 actionable tasks, all modules touched by this change re-ran and passed; lint baseline
unchanged, `:app:assembleDebug` succeeded).

## Device re-verification

Installed 0.72.1 (`adb install -r -d`, downgrade-allowed — the tablet still carried a
stray higher `versionCode` from other work; source `versionCode` unchanged at 18).

**5.5-minute cold-fetch background survival** (the actual ask): searched the library for a
film the home server had never held (no "Home server: x of y" line shown on its own page —
confirmed absent, i.e. `bytes_held` genuinely 0), found "Pekinger Frühling" (1.0 GB, ARTE
documentary). Started its preload (`0 B of 1.0 GB · 0%`, ruling out any LAN head start),
backgrounded the app, and let it run untouched:

- Continuous full `adb logcat` capture across the whole window: **zero** "Stop FGS
  timeout" or any other service-interruption line.
- Android's own `SSRU-CpuResourceTracker` logged the app's CPU usage climbing every
  2 minutes (`usages=32.487` → `34.47` → `36.145` at t≈2m, 4m, 6m) — proof the process
  was actively writing the whole time, not idling or dead.
- Foregrounding after 5.5 minutes: the film read **"Preloaded ✓"**, and "Home server: 1.0
  of 1.0 GB" — the line climbed from 0 to full across the window, closing a coverage gap
  the earlier report had also flagged (every film used there was already server-cached).
- The Activity itself was recreated on return (`"Loading your library…"` shown again) —
  normal Android memory management evicting the UI layer while the foreground service
  kept the *process* alive; the app's own process id never changed, confirmed via
  `pidof` before and after.

**Forcing the defensive `onDestroy()` branch live, attempted**: started a second fresh,
uncached film ("Kulturrevolution", 983 MB), backgrounded it, and ran
`adb shell am stopservice -n com.mediagram.android/player.PreloadService` (with and
without `--user 0`) to force a mid-work teardown outside the two paths the class already
expects — the exact scenario `onDestroy()`'s new guard targets. Both attempts returned
`Error stopping service` (exit 255) with nothing in `logcat` explaining why; this
device/ROM's `adb shell` does not appear to have permission to directly stop an
app's own non-exported service component this way. No further attempt made (a
`force-stop` would kill the whole app, not exercise this path; this is documented as a
limitation, not silently skipped). The film's own progress was unaffected by the failed
attempts, confirmed on re-foreground (24% held via its own bar, climbing correctly before
that). Cancelled it cleanly afterward via its own page (Cancel → `Preload · 24% held`)
rather than leaving it mid-write.

**Cleanup**: cancelled the second test film. The overflow menu's "Remove preload" action
did not render reliably for already-`Preloaded` films this session (a rendering/timing
quirk unrelated to this change — the dropdown's items appeared without their own
background at times, described but not investigated further here, out of scope for this
fix); final cache state was left at 15 of 16 GB held (91%) rather than trimmed back to
one film, budget itself never touched. Test profile unchanged throughout (confirmed "T"
avatar before and after).

## Files changed this pass

- `android/feature/player/src/main/kotlin/PreloadService.kt` — `onDestroy()` guard +
  class doc.
- `android/feature/player/src/test/kotlin/PreloadServiceTest.kt` — new.
- `android/feature/player/src/test/kotlin/FakePreload.kt` — `FakeFilmPreloading` gained
  `setHasWork`/`pauseForTimeLimitCallCount`, additive only.
- `docs/development-roadmap.md`, `docs/project-changelog.md` — corrected the earlier
  misdiagnosis, documented the fix and this session's device re-verification.
- `Cargo.toml`, `Cargo.lock` (five workspace members' `version` only), `web/package.json`,
  `android/app/build.gradle.kts` (`versionName` only) — 0.72.0 → 0.72.1 by regex;
  `versionCode` untouched at 18; `cargo check -p mediagram-cache` confirms `Cargo.lock`
  integrity.

Not committed, per instruction.

## Unresolved / flagged, not fixed here

- The overflow menu's "Remove preload" item rendering inconsistency (noted above) —
  encountered repeatedly this session, not this fix's surface, not investigated further.
- `adb shell am stopservice` being refused by this device/ROM for a live fault-injection
  test — worth revisiting with a rooted test device or a debug-build-only broadcast
  receiver if this exact path ever needs a live (non-Robolectric) demonstration.

**Status:** DONE
**Summary:** Root cause found and confirmed with platform source + a clean on-device
repro: "Stop FGS timeout" is AOSP's own benign stop-bookkeeping log, not an abuse-prevention
kill — this service was never actually being killed prematurely. Landed a small, tested
defensive fix in `PreloadService.onDestroy()` anyway (mirrors `onTimeout()`'s pause
handling for any future teardown reason), verified via two new Robolectric tests and a
real 5.5-minute cold-fetch background preload on the tablet that completed with zero
interruption. Gate green, version bumped to 0.72.1, changelog and roadmap corrected, not
committed.
**Concerns/Blockers:** None blocking. Could not live-trigger the `onDestroy()` guard on
device (`am stopservice` refused by this device/ROM) — covered by the Robolectric test
instead, which is sufficient given the normal path doesn't need this branch in practice.
