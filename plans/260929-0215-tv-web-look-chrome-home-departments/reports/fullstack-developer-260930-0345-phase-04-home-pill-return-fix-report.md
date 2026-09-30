# Phase 04 — Home-pill-instead-of-poster fix

Follow-up to `fullstack-developer-260930-0246-phase-04-home-kept-alive-report.md`,
addressing the box regression the lead reported after that commit
(b682b204): Back from a title page landed on the departments bar's Home
pill instead of the poster that opened it, 3/3, on benchmark 0.84.1.

## Root causes (two, found by tracing — not by guessing)

**1. Android's own focus-clear cascade wins a same-frame race.** Diagnosed
by the lead on the box (`Log.e` instrumentation, since reverted): the frame
that removes a pushed frame from the composition is the same frame Android
notices that frame's own focused view just detached, clears focus on the
whole `AndroidComposeView`, and re-grants it to the first focusable it
finds — the bar's own pill — ahead of whatever Home's own arrival effect
asked for a moment earlier in that same frame. Before keep-alive, Home was
torn down and rebuilt after the pushed frame was already gone, so its
arrival request came *after* that reset and won; kept alive, both now
happen in the same frame and the reset wins.

Fix, in one generic place: `rememberArrivalReady` (new,
`TvArrivalFocus.kt`) — `LocalLibraryCovered`'s own value, except turning
`false` waits one frame (`withFrameNanos`). `TvCatalogScreen.kt` combines
this (not raw `!covered`) with the chrome's own redirect logic into
`LocalTakesArrivalFocus`, so every arrival-restore effect already reading
that local — `TvWall`, `TvCollectionsPage`, every department page,
`TvHome` — gets its retry one frame after the pushed frame is actually
gone, not in the same frame it disappears in. Turning `true` (something is
freshly covered) is not delayed — the layer's own inertness must not lag
behind the frame it protects against.

**2. The catalogue's own restore keys were being read and written at
whatever depth currently topped the position stack, not its own fixed
depth.** `TvLibrary.kt` handed `TvLibraryHomeFrame` the same `here =
at.depth` it hands every pushed-frame branch. Before keep-alive this was
harmless — `TvLibraryHomeFrame` only ever existed while `at.depth == 0`,
since anything pushed replaced it entirely in the `when`. Kept alive, it
keeps recomposing under a pushed frame, and once one exists `at.depth` is
no longer 0 — so `restore.of(here)` inside it read (and any `restore.opened(here,
…)` from a Home-drawn control would have written) *the pushed frame's own*
restore slot, not the catalogue's. This surfaced once the frame-delay fix
above needed `restoreKey` to read correctly *the moment a frame opens*, not
only once it closes (see "how these two interacted" below) — a title-page
round trip masked it by coincidence (the derived arrival target happened to
default to the same stop either way in small fixtures); a deeper one
(episode played from a show opened from Home) did not, which is what
caught it while fixing the first bug.

Fix: `TvLibrary.kt` now hands `TvLibraryHomeFrame` a fixed `HomeDepth = 0`,
never `here`. Every pushed-frame branch keeps using `here` — that one is
correct for them, since each names its own real depth on the stack.

## How these two interacted (why this took three passes, not one)

Fixing bug 1 alone (delay only) broke six sentinel-return tests (`TvMenuTest`,
`TvSearchAndGenreTest`) that were passing before: Home's own arrival effect,
now keyed so it gets a retry on every uncover, started firing its own
fallback grant the moment a Search/Menu/Latest/Genres/Settings/System
return's restore-key consumption cleared `takesFocus` — stealing focus back
from the search icon, the bar's own ⋮, or a rail row a frame after the
chrome's own restore had already put it there. Traced to the `arrived`
latch: resetting it unconditionally on every cover (needed for a real
content return) also re-armed it for a sentinel's own redirect, which
should never get a fallback grant of its own. Fixed by keying that reset on
`covered && restoreKey != null` — a sentinel's own redirect leaves this
page's own `restoreKey` `null` throughout, on the way in and after its own
consumption alike, so it never re-arms.

That fix, in turn, needed `restoreKey` to read correctly *while covered* —
which is exactly where bug 2 lived, previously masked because the old,
unconditional reset never looked at `restoreKey`'s value at all. Fixing
bug 2 then exposed a third, narrower race: `target` and the `restoreKey`-gated
reset above both react in the very same composition `covered` turns `true`
in, but `rememberArrivalReady`'s own `false` for that same instant is one
recomposition behind it — for that one pass, `takesFocus` could still read
`true` while a page was already covered, letting the just-reset `arrived`
flag through into an accidental grant into a hidden page. Fixed by reading
`covered` directly (never lagged) as an extra guard in the grant effect
itself, alongside the delayed `takesFocus`.

## Verification

`:ui-tv:testDebugUnitTest --rerun`: full suite green, 408 tests, including
`TvMenuTest`, `TvSearchAndGenreTest`, `TvLibraryTest` (all of it, including
the multi-depth show → season → episode → player round trip), the two new
classes from the first report, and `TvHomeChromeUpTest`/`TvHomeStateTest`.
`scripts/check.sh`: green (clippy, `cargo test --all`, gradle
`testDebugUnitTest`/`core:model:test`/`lint`/`compileDebugAndroidTestKotlin`).

**Robolectric cannot show the box's own failure — confirmed, not assumed.**
Temporarily removed the one-frame delay (`withFrameNanos` call) with bug 2
already fixed, re-ran `TvHomeKeptAliveTest`/`TvHomeCoverWatchNowKeptAliveTest`:
both still passed. Robolectric's Compose test harness does not reproduce
`View.clearFocus()` → `rootViewRequestFocus()` → "compose grants initial
focus to the first focusable" the way a real `AndroidComposeView` does, so
no Robolectric test — the existing one or a new one built the way the lead
suggested — can fail on this specific race either way. The existing
`TvHomeKeptAliveTest`/`TvHomeCoverWatchNowKeptAliveTest` still cover the
*logic* (arrival grants the right stop once `covered`/`restoreKey`/`target`
settle, and never steals it back from a sentinel's own redirect) — box
verification is what proves the *timing* fix.

## What the lead should re-verify on the box

1. **The reported repro itself**: fresh launch → Down to first Recently
   Added poster → OK → Back, 3+ times. Expect the poster refocused, Home's
   own scroll unchanged, not the Home pill.
2. **Player return** (Continue card played → player → Back): the played
   title should be focused, now first in Continue — this path exercises the
   same `covered`/`restoreKey`/delayed-arrival machinery through a card
   click rather than a poster click.
3. **Cover round trip** (Watch now → player → Back → Watch now
   refocused) — `TvHomeCoverWatchNowKeptAliveTest` covers this in
   Robolectric already; the box's own timing is what still needs proving.
4. **Deeper round trips**: show → season → episode → player → Back ×N back
   to the show's own plate on Home — this is the specific shape that caught
   bug 2, worth walking deliberately rather than only the one-level case.
5. **The sentinel-return item already flagged in the first report** (item
   5 there): Search/⋮/Latest/Genres/Settings/System close with nothing else
   pressed — does focus land on and stay on the sentinel's own target now,
   or still jump a frame later? This fix directly targets exactly this
   interaction (see "How these two interacted" above) and all 408
   Robolectric tests pass including the ones that previously caught a
   regression here, but the box is what actually proves the timing, the
   same way it proved the original bug this report fixes.

## Files touched (this fix only)

- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvArrivalFocus.kt` — new
  `rememberArrivalReady`.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvCatalogScreen.kt` —
  combines it into `LocalTakesArrivalFocus` instead of raw `!covered`.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvHome.kt` — `arrived`'s
  reset keyed on `covered && restoreKey != null`; grant effect also reads
  `covered` directly as an extra guard.
- `android/ui-tv/src/main/kotlin/ui/tv/TvLibrary.kt` — `HomeDepth`
  constant, used instead of `here` for `TvLibraryHomeFrame`.
- `android/ui-tv/src/test/kotlin/ui/tv/TvHomeKeptAliveTest.kt` — added
  `TvHomeCoverWatchNowKeptAliveTest` (Watch now → player → Back).
- Version 0.84.1 → 0.84.2 (`Cargo.toml`, `Cargo.lock` ×5 workspace crates,
  `web/package.json`, `android/app/build.gradle.kts`); changelog entry.

## Unresolved questions

None from this side. The sentinel-return timing (item 4 above) is the one
thing only the box can close out.
