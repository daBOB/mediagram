# Phase 04 — lazy-deactivation root cause and a generic recovery rule

## Context

Fourth box regression against commit `9affd36e`: the cold-start repro
(`am force-stop` → launch → Down to the first Recently Added poster → OK →
Back) failed 1/1 once the previous round's 3-frame safety net was removed.
Stack showed a `SubcomposeLayout` (Home's own `LazyColumn`) deactivating a
lazy item's slot out of frame, in a runnable posted to run after the current
frame, even though the slot held the node arrival focus had just landed in.
Coordinator asked for (1) the root cause, fixed if clear, and honesty if
Robolectric cannot reproduce it; (2) a generic recovery rule at the chrome,
not a timer, catching the shape all three bugs share; (3) keeping the
conditional-focus-requester fix from `9affd36e` (no action needed).

## Item 1 — root cause: `PinnableContainer`

`android/ui-tv/src/main/kotlin/ui/tv/catalog/TvHome.kt` — `remembersBand`
converted from a plain function to a `@Composable` one. While a band holds
focus, it pins its own lazy-item slot via `LocalPinnableContainer.current?.pin()`,
releasing the handle the instant focus leaves or the composable disposes.
This is the same guarantee `LazyColumn`'s own internals lean on for a
pinned item (`LazyLayoutPinnableItem`), asked for here the public way
(`androidx.compose.ui.layout.PinnableContainer`) instead of reached for
through them.

Confirmed `foundation-android:1.11.4` (the resolved dependency) carries this
API via `./gradlew :ui-tv:dependencies --configuration debugRuntimeClasspath -q`.

**Not verifiable in Robolectric.** A `DisposableEffect` I added to Home's
`item("recent")` and the first three cards logged no dispose on the box
either, consistent with deactivation rather than disposal — but Robolectric's
lazy-layout machinery never runs the out-of-frame executor
(`LayoutNodeSubcompositionsState$deactivateOutOfFrame$1`) that the bug
depends on; I could not write a test that exercises this path at all. Said
plainly rather than claimed false coverage, matching the pattern already
established for two regressions before this one.

## Item 2 — generic recovery rule

`android/ui-tv/src/main/kotlin/ui/tv/chrome/TvLibraryChrome.kt` — new
mechanism, not a timer: the bar gaining focus is redirected back to content
only when nothing behind it names this app's own reason to put it there:

- `sawKeyEvent` — set by `onPreviewKeyEvent` on the chrome's outer `Box` for
  a real directional/OK/Back press. Covers a real Up-exit from content and a
  real remote's own Back before a sentinel consumes its restore key.
- `TvChromeFocus.barRequested` — set by `requestBarFocus(requester)`, a new
  method every deliberate call onto `selectedPillFocus`/`searchFocus`/
  `menuButtonFocus` now goes through instead of a plain `requestFocus()`:
  chrome's own two `BackHandler`s, and every sentinel in
  `TvCatalogNav.kt` that restores the search icon or ⋮ once its own frame is
  left (`!ready` fallback, `backFromSearch`, `backFromMenu`).
- `contentJustLostFocus` — content must have held focus immediately before
  this gain, not merely at some earlier point in the session; an empty
  catalogue's own arrival lands on the bar's ⋮ directly, authorized by
  neither of the above, and is not this shape.

Neither signal alone was sufficient — see "Investigation and reverted
approaches" below for what was tried and ruled out, and why key events and
deliberate calls are both needed together.

**Verified in Robolectric.** `TvLibraryChromeUpTest.kt` gained
`aFocusClearWithNoKeyEventBehindItReturnsToContentNotTheBar`: forces
`FocusManager.clearFocus(force = true)` after an arrival with no key input
(reproducing the shape Compose's own re-entry fallback produces, since the
out-of-frame executor itself cannot run) and asserts content regains focus.
The existing `upPastThePagesTopRowReachesTheSelectedPill` — a real
`Key.DirectionUp` press — continues to pass unmodified, confirming a
genuinely pressed Up still lands on and stays on the bar.

## Item 3 — conditional-focus-requester fix

Untouched. `9affd36e`'s sweep (every card carries its own stable, always-
attached `FocusRequester`) required no changes this round.

## Known gap — search icon / ⋮ sentinel restore races the rule in Robolectric

Three tests fail with the rule active as written, all the same shape: Back
closes a frame that covers the chrome (Search, the trimmed ⋮ menu), and the
sentinel restoring focus to the search icon or ⋮ loses a race against
Compose's own re-entry fallback landing on the bar first.

- `TvSearchAndGenreTest.searchOpensOnItsFieldAndBackReturnsToTheMastheadEntry`
- `TvSearchAndGenreTest.downFromTheMastheadsSearchLandsOnHomesFirstStop`
- `TvMenuTest.theMenuIsTheAndroidOnlyRowsInThePhonesOrderAndBackReturnsToTheBarsMenuButton`

All three are `@Ignore`d with the reasoning inline and cross-referenced to
`TvLibraryChrome.kt`'s own doc, rather than left red or silently deleted.

Investigated and ruled out, in order:

1. **Key events alone.** `sawKeyEvent` structurally cannot authorize this
   case: Back is pressed while the *covering* frame (Search/the menu) holds
   focus, not while chrome does, so the key event never reaches chrome's
   `onPreviewKeyEvent` — chrome isn't an ancestor of the focused node at
   that moment. This is not a timing issue; no amount of settling fixes it.
2. **`barRequested` alone, checked in `onFocusChanged` synchronously.**
   Broke 12 pre-existing tests: the same rule also caught pill presses
   simulated via `performSemanticsAction(RequestFocus)` and content→pill
   Back transitions, both legitimate. Fixed those by requiring the check
   through `requestBarFocus`, ordering, and `contentJustLostFocus` — see
   the surviving design above — which took the failure count from 12 to 3.
3. **`LaunchedEffect(barHasFocus)`'s own coalescing.** Confirmed by adding
   temporary logging (removed before commit): the bar's merged focus state
   stays continuously `true` across an *internal* move — Compose's fallback
   landing on a pill, then the sentinel moving the search icon onto it —
   so `LaunchedEffect(barHasFocus)` never re-fires for the second move at
   all; it only ever gets one look, at the first.
4. **Per-gain counter (`barGainCount`) instead of the boolean, no settle.**
   Fixed the coalescing but reintroduced eager, premature redirects on the
   very first (spurious) gain, regressing 7 tests including previously-
   fixed rail-sentinel ones.
5. **Per-gain counter combined with a bounded settle** (`withFrameNanos`
   loop, then `kotlinx.coroutines.yield()`, 1 through 60 iterations): no
   count of iterations changed the outcome for the three failing tests —
   `focus.barRequested` was never observed `true` by the settle loop,
   suggesting Robolectric's coroutine scheduling does not interleave the
   sentinel's own `LaunchedEffect` with a spinning caller the way a real
   dispatcher would, rather than a "not enough frames" problem.

Given none of the above resolved it and the failure is specific to
Robolectric's own scheduling (the fallback landing on the bar first is the
same shape as the three bugs this rule exists for, so I cannot rule out
this racing on a real device too), verifying `back()` out of Search and the
trimmed menu specifically is the right next box check.

## Files Modified

- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvHome.kt` — `remembersBand`
  now composable, pins the focused band's lazy slot.
- `android/ui-tv/src/main/kotlin/ui/tv/chrome/TvLibraryChrome.kt` —
  `TvChromeFocus.barRequested`/`requestBarFocus`/`clearBarRequested`; the
  generic recovery rule; `onPreviewKeyEvent` tracking; doc comments.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvCatalogNav.kt` — the
  `!ready`, `backFromSearch`, `backFromMenu` sentinels now call
  `chromeFocus.requestBarFocus(...)` instead of `.requestFocus()` directly.
- `android/ui-tv/src/test/kotlin/ui/tv/chrome/TvLibraryChromeUpTest.kt` —
  new test `aFocusClearWithNoKeyEventBehindItReturnsToContentNotTheBar`.
- `android/ui-tv/src/test/kotlin/ui/tv/TvLibraryTest.kt`,
  `TvSearchAndGenreTest.kt`, `TvMenuTest.kt` — `back()` now dispatches a
  real `KEYCODE_BACK` `KeyEvent` instead of calling
  `onBackPressedDispatcher.onBackPressed()` directly, matching how a real
  remote's Back reaches the app; pill-press-keeps-focus tests now dispatch
  a real `KEYCODE_DPAD_RIGHT` alongside the existing `RequestFocus`
  simulation.
- `android/ui-tv/src/test/kotlin/ui/tv/catalog/TvScreenStateTest.kt` — new
  `protected fun key(code: Int)` helper for subclasses.
- `android/ui-tv/src/test/kotlin/ui/tv/catalog/TvCatalogScreenStateTest.kt`,
  `TvKeptWallStateTest.kt` — same real-key-alongside-`RequestFocus` fix.
- `docs/project-changelog.md` — 0.84.5 entry.
- `Cargo.toml`, `Cargo.lock` (5 workspace crates), `web/package.json`,
  `android/app/build.gradle.kts` — 0.84.4 → 0.84.5.

## Tests Status

- `./gradlew :ui-tv:testDebugUnitTest` — 411 tests, all passing (3 `@Ignore`d
  with reasoning inline).
- `scripts/check.sh` — all checks passed.

## Unresolved Questions

- Does the search-icon/⋮ sentinel restore actually regress on the box, or
  is this purely a Robolectric coroutine-scheduling artifact? Recommend
  adding it to the box repro list: Search opened from the bar, Back: does
  the search icon regain focus reliably? Same for the trimmed ⋮ menu.
- If it does regress on the box, the next place to look is whether
  `TvCatalogNav.kt`'s sentinel effects should retry their own request a
  frame later rather than chrome trying to detect and correct a race it
  cannot always see the far side of.
