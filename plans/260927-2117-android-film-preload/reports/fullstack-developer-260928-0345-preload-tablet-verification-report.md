# Phase 04 — Preload device verification (tablet)

Plan: `plans/260927-2117-android-film-preload/`. Device: tablet `caad49da`
(Xiaomi Redmi Pad Pro, Android 16 / API 36, MIUI/HyperOS V816), test profile
("T" avatar confirmed before and after — never switched). Home cache server
`192.168.0.240:7788` throughout, already on a version that answers
`GET /v1/sets/{id}`. HEAD stayed at d687cda3, 0.72.0, throughout; no code
changed, no commit, no version bump (nothing needed one — see Defects).
Covers this phase's own remaining item (tablet) and phase 3b's device check
(the queue view, Queued-ahead label, NeedsSpace-with-budget label, Preloads
page, menu entry — all built on this branch, never previously run on a
device).

Screenshots: `reports/screenshots/tablet-*.png` (chronological).

Install note: the tablet already carried `versionCode 19` (0.71.1) from
other work this session's own device use; this worktree's `versionCode` is
18. Installed via `adb install -r -d` (explicit downgrade) rather than
`gradlew installDebug`, which refuses a lower `versionCode`. Purely a local
test-install mechanic — no manifest or version file touched.

## What was verified

**Step 1 — button → Preloading → progress ("Chihiros Reise ins
Zauberland", 1.1 GB)**: tapped Preload, bar + "x of y · n%" appeared within
~1s, climbed steadily. Throughput ~16 MB/s sustained — **this is a LAN
figure, not a cold Telegram fetch**: the home server already held this
title in full from the prior TV-box session, so the device read it over
the LAN cache-server path, not Telegram. Unlike the TV box's own pass
(genuinely cold, ~3.5–4 MB/s), no cold-fetch throughput number was
collected this session — every title used had already been seeded server-
side. Flagged as a gap below.

**Step 2 — queue a second film while the first runs**: queued "Lockere
Geschäfte" (Risky Business, 1.1 GB) behind Chihiro. Its own page read
`Queued · after Chihiros Reise ins…, 85%` — exact spec wording, correctly
naming the running title and its live percent. Menu showed `Preloads · 2`.
Preloads page listed both: Preloading (bar, Cancel) then Queued (Cancel).
Rows open their film (confirmed via the "On this device" section; the
Preloading row's own tap landed inconclusively — see Defects, minor).

**Step 3 — background survival**: backgrounded the app (`KEYCODE_HOME`) for
70s+ (a clean, isolated run — nothing else queued, no other navigation)
while a fresh Lockere Geschäfte preload ran. Confirmed `topResumedActivity`
was the launcher throughout, app process (`pid 5099`) never died. Progress
continued and the film reached "Preloaded ✓" by the time the app was
foregrounded again — **despite** `PreloadService` itself being torn down by
the OS partway through the window (see Defects: the write is not actually
served by the foreground service's own lifetime).

**Step 4 — pause while a different film plays**: started Chihiro preloading
fresh, then opened and played the already-preloaded Lockere Geschäfte
(instant offline playback). Chihiro's own progress kept advancing across
the interruption (200 MB → 544 MB across the play/stop window) — consistent
with the engine's documented "pauses while anything plays" rule, though the
playback window was too short (a few seconds, stopped via back navigation)
to catch the literal "Paused (playing)" label on screen mid-transition.
This exact caveat is already noted as non-blocking in the TV box's own
report — the mechanism is covered by `FilmPreloaderTest.kt` at the engine
level; this is corroborating device evidence.

**Step 5 — cancel/resume, both surfaces**:
- **Queued item, from the Preloads page**: with Chihiro queued behind
  Lockere, tapped Cancel on Chihiro's row. It dropped from the Queued
  section immediately, Preloading section unaffected. Clean.
- **Running item, from its own page**: tapped the "Preloading" pill on
  Chihiro's own page mid-run (94% held) — reverted to `Preload · 94% held`.
  Re-tapped Preload — resumed from 94% (not 0), confirmed by the bar
  jumping straight to 97% on the next read. Clean, matches the TV box
  report's own "resumes from the held share, not restarting" finding.

**Step 6 — completion, Remove, re-preload to leave one**: both test films
reached "Preloaded ✓" at different points this session. Used the film
page's own `...` → "Remove preload" action (not a separate button on the
pill itself) to bring the held count back down; confirmed the pill reverts
to `Preload · <size>` immediately. Ended the session with exactly one film
preloaded (Lockere Geschäfte) — confirmed via Settings › Storage (12 of
16 GB held) and the film's own page.

**Step 7 — offline playback**: `svc wifi disable` (confirmed via `dumpsys
wifi`), played the preloaded Lockere Geschäfte — instant start (`0:02` on
screen within 3s of tapping Play), continuous playback confirmed at both
the 15s and 30s marks (Warner Bros. logo, then opening titles advancing
normally), **zero** `telegram`/`mtproto`/`grammers` log lines across the
whole window (`adb logcat -c` before, grep after). `svc wifi enable`
restored connectivity; confirmed back online via `dumpsys connectivity`
(WIFI network `HOMELAB`, `VALIDATED`) and a successful ping to the home
cache server (7.7ms RTT). USB adb was unaffected throughout, as expected.

**Step 8 — NeedsSpace**: "Underworld: Awakening" (23 GB) against the
tablet's 16 GB budget read `Needs 23 GB · budget is 16 GB · Try again`,
plus a "Raise the cache budget" link beneath it (a UI element not
mentioned in the TV box's own report — present on this build, links to
Settings, not tapped). Budget confirmed unchanged at 16 GB throughout, via
Settings › Storage before and after.

**Step 9 — home server line**: present and correct on every film page
throughout (`Home server: x of y GB`), and the underlying route answered
correctly (paired, "Connected to 192.168.0.240:7788"). **Could not confirm
it climbing from a low starting point**: both films used this session
(Chihiro, Lockere Geschäfte) were already fully held server-side from the
prior TV-box session, so the line read "1.1 of 1.1 GB" and stayed there —
never observed to climb mid-preload as it did in the TV box's own pass
(bytes_held tracked the device figure closely there). Not a defect — the
field, the poll, and the route are all confirmed working; this is a gap in
this session's own coverage, not the feature's.

**Step 10 — kids profile**: the profile switcher lists `andre`, `test`,
`TV test`, `TV kids` (marked KIDS). `TV kids` reads as a test double built
for the TV surface (paired naming with `TV test`, both plainly test
profiles) rather than a real family profile — but per instruction, did
**not** switch into it; stayed on `test` throughout. Flagging its existence
here rather than deciding on the caller's behalf.

## Defects found

### 1. `PreloadService`'s `dataSync` foreground service is torn down by the OS roughly 55–75s after every start, on this device (headline finding)

**Reproducible, 3 for 3.** Every time `PreloadService` started during this
session, `adb logcat` showed the same sequence:

```
I ActivityManager: Background started FGS: Allowed [... cmp=.../player.PreloadService ...]
I ActivityManager: Background started FGS: Allowed [... cmp=.../player.PreloadService ...]  (a second start, ~20-30ms later)
W ForegroundServiceTypeLoggerModule: Foreground service start for UID: 10431 does not have any types
  ... 55–75s later ...
D ActivityManager: Stop FGS timeout: ServiceRecord{... player.PreloadService ...}
I vendor.qti.hardware.servicetrackeraidl-service: destroyService is called for service : .../player.PreloadService
```

Three independent starts this session, three kills, timings 54s, 74s, 75s
after start — none anywhere near the class doc's own claimed "6h a run, 24h
a day" ceiling. This happened **regardless of whether the app was
foregrounded or backgrounded** — the 74s instance ran with the app the
whole time actively on-screen; the 75s instance was mid-backgrounding.

Both the app manifest (`feature/player/src/main/AndroidManifest.xml`) and
`PreloadService.startForegroundDataSync()` (`ServiceCompat.startForeground(
this, NOTIFICATION_ID, notification(null),
ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)`) correctly declare
`dataSync` — confirmed by reading the actually-packaged manifest
(`app/build/intermediates/packaged_manifests/debug/.../AndroidManifest.xml`),
not just the source. The `does not have any types` warning is real despite
that correct declaration.

**Leading hypothesis, not confirmed against platform source**: the app's
`targetSdk` is 37 (`app/build.gradle.kts:12`), one level ahead of this
device's actual platform API level (36, confirmed via
`getprop ro.build.version.sdk`). A targetSdk the device's own platform
doesn't recognize could plausibly make the OS's own `dataSync`-type
recognition path fail closed, landing the service in whatever short-lived
"no type" bucket a real `dataSync` service would never hit. Not verified
against AOSP source in this session — would need either testing at
targetSdk 36 or checking `ActiveServices.java`/`ForegroundServiceTypeLogger`
for this exact platform build.

**User-visible impact, only sometimes**: because `FilmPreloader`'s actual
write job runs on an app-level (Hilt `@Singleton`) `CoroutineScope`
independent of `PreloadService` — confirmed by reading
`di/PreloadModule.kt`'s `provideFilmPreloader` — killing the service alone
does not necessarily stop the write. In the clean, isolated repro (step 3
above), a preload killed at the service level *finished successfully*
anyway. But in an earlier, messier stretch of this session (extensive
navigation, several mis-scaled taps while I was still calibrating screen
coordinates), a running preload for the same film genuinely stopped
advancing and sat static at 31% for several minutes until I manually
re-tapped Preload to resume it — reverting to a bare `Preload · 31% held`
pill, not the designed `Paused — background limit` + Resume treatment.

I could not cleanly reproduce that second, worse symptom in isolation this
session (one occurrence out of two attempts), so I can't be fully certain
whether it was caused by the FGS kill itself, by one of my own miscalibrated
taps during that period, or by both. What I can say with confidence: **the
FGS-kill-at-~1-minute is real and reproducible on its own** (3/3), and
**even in the best case it leaves a preload running unprotected by any
foreground service for the remainder of its download**, which is a real
risk under real memory pressure this session's short, low-pressure tests
didn't exercise.

**Why not fixed here**: root-causing the platform mismatch, and building
the test infrastructure to safely change `PreloadService`'s lifecycle
handling (this codebase has no precedent for testing a real Android
`Service` via Robolectric's `ServiceController` — every existing "service"
test goes through a fake controller instead), are both bigger than a
device-verification pass should absorb blind. Two candidate directions for
whoever picks this up:
- Confirm the targetSdk/platform-level hypothesis (test at targetSdk 36, or
  find the relevant platform check), and decide whether 37 is worth
  keeping given `ACCESS_LOCAL_NETWORK` is the only feature that needs it.
- Regardless of root cause: mirror `onTimeout()`'s `pauseForTimeLimit()`
  call into `onDestroy()` too, so a service teardown for *any* reason
  (documented ceiling, this platform quirk, a future MIUI battery
  restriction) always leaves an in-progress film in the already-designed
  "Paused — background limit" state rather than reverting to a bare,
  cancelled-looking pill. This is a small, safe, idempotent change
  (`pauseForTimeLimit()` no-ops on an empty queue) — but needs the Service
  test harness above to land with a real test, which is why it's listed
  here rather than made under this task.

### 2. Preloads page menu entry disappears entirely once nothing is preloading or queued — even with films still "On this device"

Confirmed as *designed* behavior (the spec explicitly reads "visible only
while something is preloading or queued, with the count"), not a bug — but
worth flagging as a real UX gap: once every preload finishes, the "Preloads
· n" menu row vanishes, and there is no other route to the Preloads page
— so a viewer who wants to check what's already on the device, or Remove
one, has no way to reach that page once idle except starting a new preload
first. Not fixed here (matches the written spec exactly); flagged for the
next person who touches this page to confirm it's still wanted this way.

### 3. Minor — one Preloads-page row tap did not navigate

Tapping the "Preloading" row (title text, not the Cancel button) on the
Preloads page did not open the film's own page in one attempt (screenshot
`tablet-19`/`tablet-19b`); a later tap on an "On this device" row also did
not navigate in one attempt. Given every other row-tap and button-tap in
this session worked once coordinates were correctly scaled, this is more
likely a mis-scaled tap on my part (see note below) than a real click-
target bug — not confirmed either way, listed for awareness rather than as
a fix item.

**Coordinate-scaling note, for whoever runs this walk again**: this
device's screenshots come back at 3200×2136 but display at 2000×1335 (a
1.6× factor); `adb shell input tap` wants the raw 3200×2136 coordinates.
Several early taps in this session used the *displayed* coordinates
directly (unscaled), landing short of the intended target — mostly
harmless (taps landed on empty space), but plausibly the cause of Defect 3
above, and worth being deliberate about up front next time.

## Not completed / out of scope this pass

- **Cold-fetch throughput on the tablet** — every title used was already
  fully cached on the home server from the prior TV-box session, so no LAN-
  vs-Telegram-speed comparison was collected here (the TV box's own report
  already has one, ~3.5–4 MB/s cold vs. this session's ~13–16 MB/s LAN).
- **Home server line climbing from a low starting point on the tablet** —
  same root cause; the field and its poll are confirmed correct, just never
  observed moving on this device this session.
- **The FGS-timeout root cause** — hypothesis only (targetSdk 37 vs. device
  API 36), not confirmed against platform source.
- **A literal on-screen "Paused (playing)" capture** — as on the TV box,
  the interruption window was too short to catch mid-transition; covered by
  existing unit tests instead.

## Docs updated

- `docs/development-roadmap.md` — the "Android: film preload" section:
  phase table now reads both devices done; new paragraph naming the tablet
  pass and its headline finding (the FGS-timeout issue), replacing the
  "tablet pending" language.
- `plans/260927-2117-android-film-preload/plan.md` — phase 03b and 04 rows
  updated from "pending"/"TV box done" to device-checked / both devices
  done.
- `plans/260927-2117-android-film-preload/phase-04-verify-docs-version.md`
  — new Status section pointing at both device reports and naming the
  headline finding.
- `plans/260927-2117-android-film-preload/phase-03b-preload-queue-view.md`
  — Todo's own "device check... still pending" line corrected.
- `docs/project-changelog.md` — no entry added; no code changed this pass
  (0.72.0's own entry already covers what shipped).
- `docs/system-architecture.md` / `DESIGN.md` — no change needed; both
  already match what was observed on device (confirmed by re-reading
  during this pass, not just trusting the prior report).

## Version

No bump. No code changed this pass — the FGS-timeout finding is
documented, not patched (see Defects § 1 for why). Manifests untouched;
`android/app/build.gradle.kts` still reads 0.72.0 / `versionCode` 18.

## Unresolved questions

- Whether the FGS-timeout is genuinely the targetSdk-37-on-API-36 mismatch,
  or something else entirely (a MIUI/HyperOS-specific policy independent of
  targetSdk) — would need a build at targetSdk 36 to isolate, which this
  session did not attempt (out of scope for a verification pass).
- Whether the one observed "preload silently stopped mid-write" incident
  was caused by the FGS kill, an accidental mis-scaled tap, or something
  else — one clean repro attempt did not reproduce a stopped write (the
  film finished despite the same kill happening), so this remains
  genuinely uncertain rather than confirmed either way.

**Status:** DONE_WITH_CONCERNS
**Summary:** Full preload walk verified on the tablet — button, bar, queue
(both Preloading and Queued sections, cancel from each surface, resume from
the held share), completion, Remove, offline playback with zero Telegram
reads, and NeedsSpace with the live budget and no budget change — all
matching the design across phase 3 and 3b. Found and thoroughly
characterized a significant, reproducible platform issue: this device
tears down `PreloadService`'s `dataSync` foreground service roughly 55–75s
after every start regardless of foreground/background state, which at
least once cost a running preload its progress with no user-visible
explanation. Not fixed in this pass — root cause (leading hypothesis:
targetSdk 37 vs. this device's platform API 36) unconfirmed, and the
smallest safe mitigation (mirroring `onTimeout()`'s pause handling into
`onDestroy()`) needs Service-level test infrastructure this codebase
doesn't yet have. Ended the session with exactly one film preloaded
(Lockere Geschäfte), test profile untouched throughout, no budget change,
no commit, no version bump.
**Concerns:** the FGS-timeout finding above is the main one — real,
reproducible, currently undocumented-as-a-known-issue anywhere else in the
codebase, and affects every preload run past about a minute on this
device. Secondary: two coverage gaps (cold-fetch throughput, home-server-
line climbing) this session couldn't exercise because both test films were
already server-cached; worth a follow-up pass with fresh titles if that
matters before this ships more broadly.
