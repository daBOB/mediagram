# TV chrome intermittent self-close — root cause, fix, test

Worktree `agent-ad2335b005b156856`, branch `feat/tv-web-look-chrome`, fix commit `571f7f4e`
(0.82.1, on top of the reported 6d2dc721/0.82.0). No device access used; all evidence is from
code, the branch's own diff against `main`, and the prior, on-this-exact-device debugging report
cited below.

## Root cause (proven for symptom 2; best-evidenced, unconfirmed candidate for symptom 1)

`TvLibraryChrome`'s Back chain gates its two handlers on real focus signals that both **default
to `false`**:

```
android/ui-tv/src/main/kotlin/ui/tv/chrome/TvLibraryChrome.kt:141-142 (before fix)
    var barHasFocus by remember { mutableStateOf(false) }
    var contentHasFocus by remember { mutableStateOf(false) }
    ...
    BackHandler(enabled = contentHasFocus) { ... }   // :151
    BackHandler(enabled = barHasFocus) { ... }        // :156
    // Rail: no handler at all — an unhandled Back falls through and
    // finishes the activity, by design.                              // :159-161
```

Arrival focus is only requested from a `LaunchedEffect` in whichever page is showing — e.g.
`TvHome.kt:102-105`:

```kotlin
LaunchedEffect(restoreKey, allRows.isNotEmpty(), landOnCover) {
    if (!takesFocus) return@LaunchedEffect
    if (landOnCover) coverFocus.requestFocus() else if (allRows.isNotEmpty()) first.requestFocus()
}
```

A `LaunchedEffect` body runs **after** the composition that launched it has already applied —
i.e. after `TvLibraryChrome`'s own `BackHandler`s have already registered with the activity's
`OnBackPressedDispatcher`. On every fresh mount of the chrome (first launch, or returning to Home
from a title/season/collection/search/genre/person/franchise/Latest/Genres/menu/**player** frame —
all of them dispatched through `TvLibrary.kt`'s exclusive `when (top)`, which fully disposes and
rebuilds `TvLibraryHomeFrame`/`TvLibraryChrome` each time), there is a real window — at least one
frame — where **none** of content, bar or rail has focus yet. In that window
`contentHasFocus == false && barHasFocus == false` is indistinguishable from "the remote is
genuinely resting on the rail" (the one state the design leaves unhandled on purpose so Back
closes the app). A Back landing in that window falls through and finishes the activity, uninvited.

**This is a regression, not present in the design it replaced.** The old `TvCatalogRoot`
(`TvLibraryBranches.kt`, pre-phase, via `git diff main...HEAD`) had the same structural gap but
the opposite, fail-safe polarity:

```kotlin
// main, before this phase
val masthead = remember { FocusRequester() }
var onMasthead by remember { mutableStateOf(false) }
BackHandler(enabled = !onMasthead) { masthead.requestFocus() }
```

`onMasthead` also starts `false`, but the handler's `enabled` is `!onMasthead` — so the *default*
state is **enabled** (catches a stray Back, redirects to the masthead, no exit) and only the
*confirmed* "focus reached the masthead" state disables it. The new chrome inverted this: the
default state is **disabled** (falls through, exits), and only a *confirmed* signal (which the
rail never had at all — `TvLibraryChrome.kt:210`, `onHasFocusChanged = {}`, a no-op) would have
made it safe.

**Proven for symptom 2** (`R1 run 3`: "Home → OK on a poster → Back" rendered only 2 frames and
exited): this repro's Back is dispatched right as `TvTitleFrame` pops and `TvLibraryHomeFrame`
freshly remounts — exactly the gap above. A hardware/OS double-delivered Back (a second
ACTION_DOWN/UP pair for one physical press — not unheard of on TV remotes/Bluetooth) landing in
that gap, after the first Back has already been consumed by the title page's own unconditional
`BackHandler(onBack = leave)` (`TvLibraryFrames.kt`), finds the freshly-mounted chrome with all
three regions still `false` and exits — matching "only one intended Back", "no crash", "no ANR"
(finish() is a clean activity lifecycle event, not a crash), and "only 2 frames rendered" (the
task is torn down almost immediately after the remount starts drawing).

**Not proven for symptom 1** (Watch now → OK → player session created then destroyed, ~5 s later
close, *no further key at all*): this mechanism requires an incoming Back key, and none was
pressed. I did not find a code path in this branch's new files, nor in the (bit-for-bit
unmodified by this phase, confirmed via `git diff main...HEAD -- android/ui-tv/src/main/kotlin/ui/tv/player`
returning nothing) player-open path, that explains symptom 1 with confidence. Ranked candidates
below.

## What was ruled out

- **Explicit `finish()`/`moveTaskToBack()`/programmatic `onBackPressed()`**: none exist anywhere
  in `ui-tv`, `ui-common` or `app` (`grep -rn "onBackPressedDispatcher\|\.finish()\|moveTaskToBack"`).
  The only way this app's activity finishes is the documented "rail: unhandled Back" fallthrough.
- **Double dispatch of the OK key on "Watch now"**: `TvCoverStory.kt` (unmodified by this phase)
  wires a single `onClick = onPlay` on one `TvTextRow`; no `onKeyEvent`/`onPreviewKeyEvent`
  duplicate handler sits over it.
- **`LibraryPositions.pop()`/`openPlayer()` underflow or double-pop**: `pop()` guards an empty
  stack (`ui-common/.../LibraryPositions.kt:262-265`); this file is unmodified by this phase.
- **Rail/bar composables (`TvLibraryRail.kt`, `TvDepartmentsBar.kt`) having a hidden delay/timeout
  or auto-hide**: read in full — no `delay()`, no auto-hide, no Back handling at all beyond the
  `onExit`/`focusProperties` already accounted for above.
- **`TvPlayerBack`/`PlayerLifecycle`/`PlayerNavigationEffects`**: `TvPlayerBack`'s `BackHandler` is
  unconditional and only ever calls `onLeave` (=`leave`) from an actual Back key; `PlayerLifecycle`
  only calls `viewModel.stop()` from its own `onDispose` (a consequence of leaving, not a cause);
  neither calls navigation on a timer.

## Ranked candidates for symptom 1 (not fixed — no on-device access to confirm)

1. **Likely**: Home's Compose tree is measurably heavier after this phase (new
   `TvLibraryRail`/`TvDepartmentsBar`/`TvChromeControls` — 6 rail rows + counts + tally + wordmark,
   plus a scrolling pill row — as new Z-order overlay siblings) and `TvLibrary.kt`'s `when (top)`
   still fully disposes/rebuilds that entire tree on every transition into or out of Home, exactly
   as `plans/260928-0200-android-decoder-stall-recovery/reports/debugger-260928-0200-decoder-freeze-rootcause-and-closeout-report.md`
   already measured on **this same device** for the *lighter*, pre-phase masthead ("~5 s of
   Davey!/Skipped frames… after our own code has already finished running", invisible on the
   release/benchmark build for that lighter tree, explicitly flagged there as a risk for "future UI
   growth" on `ui-tv`'s navigation). Pressing Watch now tears this heavier tree down at the same
   moment `PlaybackService.onCreate()` (`feature/player/.../PlaybackService.kt:43-53`) launches a
   `Dispatchers.Main.immediate` coroutine to build the session and call `addSession()`; if the main
   thread is saturated by that teardown long enough, the session's own startup could be delayed
   past whatever margin the OS/launcher allows, matching "session created, then destroyed" plus a
   ~5 s-later close. **Confirming log**: re-run this report's own Part 2 method (`am profile start
   --sampling 1000 <pid> <file>`, or just `grep -c "Davey!\|Choreographer: Skipped"`) around a
   Watch now repro on 0.82.1's benchmark build; a multi-second stall starting right at the OK press,
   with the method trace's self/inclusive time sitting in generic Compose measure/layout
   (`SnapshotStateObserver`, `Placeable.placeAt`, `LayoutModifierNodeCoordinator.measure`) rather
   than app code, would confirm. `dumpsys activity services player.PlaybackService` or a
   `ForegroundServiceDidNotStartInTimeException`/ANR line in the full (not just crash-buffer)
   logcat at the ~5 s mark would confirm the service-timeout half specifically.
2. **Possible**: an intermittent, hardware/OS-level hiccup on this box unrelated to this phase's
   code (thermal, GC, a rare Codec2/ExoPlayer init race) — consistent with the very low
   reproduction rate (2/20) and the fact the player-open path itself is unmodified. **Ruling out**:
   repeat the same Watch now repro ≥20× on 0.81.1 (pre-phase); a nonzero self-close rate there too
   would clear this phase's chrome of symptom 1 entirely.
3. **Unlikely**: the Back-gap fix above also happening to matter here — ruled out by symptom 1's
   own account ("no further key"); kept last only because "no further key" is the tester's own
   recollection, not a logcat-confirmed absence of a key event.

## Test — fails before the fix, passes after

Added to `android/ui-tv/src/test/kotlin/ui/tv/catalog/TvCatalogScreenStateTest.kt`:
`aBackBeforeArrivalFocusHasLandedAnywhereIsStillCaught`. Mounts `TvCatalogScreen` via
`setContent`, then — **inside the same `runOnUiThread` block, before `compose.waitForIdle()` lets
the arrival-focus `LaunchedEffect` run** — asserts `onBackPressedDispatcher.hasEnabledCallbacks()`
is `true`. Verified by checking out the pre-fix `TvLibraryChrome.kt` (`git show HEAD:...` — this
worktree's own prior commit, i.e. 0.82.0) and running just this test:

```
TvCatalogScreenStateTest > aBackBeforeArrivalFocusHasLandedAnywhereIsStillCaught FAILED
    java.lang.AssertionError at TvCatalogScreenStateTest.kt:339
```

Restored the fixed file; `:ui-tv:testDebugUnitTest` (full suite, 360 tests including this one and
the pre-existing `TvLibraryTest.backAtTheCatalogRootGoesUpThroughThePillThenTheRailBeforeTheAppFinishes`,
which still passes — the fix leaves the genuine "rail, Back closes the app" case untouched since
`railHasFocus` is `true` by the time a viewer has actually navigated there) — **BUILD SUCCESSFUL**.

## Fix

`android/ui-tv/src/main/kotlin/ui/tv/chrome/TvLibraryChrome.kt`:
- Added `railHasFocus` state, wired from `TvLibraryRail`'s own `onHasFocusChanged` (previously a
  no-op, `{}`).
- Added a third `BackHandler`, enabled only while `!contentHasFocus && !barHasFocus &&
  !railHasFocus` (i.e. exactly the "not yet settled anywhere" gap), whose body is empty — it
  absorbs the Back rather than redirecting focus anywhere, since the already-queued arrival-focus
  effect settles this a frame later on its own.
- Does not touch the designed chain (content → pill/rail row; bar → rail; rail → exit): the rail's
  own "no handler, Back exits" case is unaffected, since `railHasFocus` is confirmed `true` by the
  time a viewer has genuinely navigated there.

## Gate

`./gradlew :ui-tv:testDebugUnitTest` (from `android/`) and `scripts/check.sh` (from the worktree
root) both green: clippy, `cargo test --all`, `bun run lint`/`bun test`, and the full Gradle
`testDebugUnitTest lint :core:model:test lint :ui-tv:compileDebugAndroidTestKotlin
:core:ffmpeg:compileDebugAndroidTestKotlin` — 14 pre-existing lint warnings, 1 pre-existing
baselined error, no new lint failures, same as the phase's own report.

## Version / commit

0.82.0 → 0.82.1 (patch: bug fix, no behavior change to the designed Back chain) across
`Cargo.toml`, `Cargo.lock` (5 workspace crates), `web/package.json`,
`android/app/build.gradle.kts` (`versionName`; `versionCode` untouched at 18); `cargo metadata
--locked --offline` succeeds. Changelog entry added at the top of `docs/project-changelog.md`.
Committed on `feat/tv-web-look-chrome` as `571f7f4e` ("fix(tv): catch a Back that lands before
chrome focus settles"). Not pushed, not merged, per instructions.

## Unresolved questions

- Symptom 1's own root cause is **not** confirmed — see ranked candidates above. Needs on-device
  profiling (this report's own recommended commands) that this session had no access to.
- Whether the hardware/OS actually double-delivers Back on this remote/box (the mechanism symptom
  2's fix assumes puts a stray Back into the gap) is inferred, not logged — the original repro's
  own log was already cleared. The fix closes the gap regardless of what puts a Back there, so it
  does not depend on this being confirmed, but the *trigger* for symptom 2 specifically remains a
  reasoned inference, not a captured log line.
- Symptom 2 was one occurrence in R1 run 3, not reproduced elsewhere in the box session — no
  independent on-device confirmation that this specific fix eliminates it (that verification is
  for whoever next runs the box session, per this task's hard limit against device access here).
