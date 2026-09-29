# Phase 02 — four fixes from the on-box review of 0.83.0

Follow-up to `fullstack-developer-260929-0920-phase-02-home-report.md`, after the coordinator's
on-box review of the 0.83.0 benchmark and a rebase onto `main` `0cee8b18` (0.82.2). Branch now
`c9863405` → this commit, still `feat/tv-web-look-home`, still `0.83.0` (unreleased, no version
bump per the coordinator's own note).

## What was wrong, and the actual root cause of each

1. **Cover title colliding with the bar.** `TvCoverSlide`'s words column is
   `Alignment.BottomStart` inside a `Box` whose height only ever *floors* at the intended
   468dp — a title, a tagline and the facts line together taller than that floor grow the box
   to fit them, and with no top padding of its own the words then start at that (taller) box's
   own top edge, which is the screen's own y=0: squarely behind the bar. Fixed with an explicit
   top padding on the words column (`TvHomeBarClearance` — the bar's own drawn height plus a
   small margin) that holds regardless of how tall the content turns out to be: proven
   algebraically (bottom-alignment against a fixed floor never changes where visible text
   lands when the content is *shorter* than the floor; once it's taller, the column's own top
   padding is the *entire* distance between the box's top and the first visible glyph — a
   `Box(minHeight = 1.dp)` unit test forces that second case deterministically, since I have no
   device to confirm which case Fraunces' real metrics land in for "The Green Knight" at 61sp).
2. **Up from the cover's Watch now landing on a geometrically-above pill, not the selected
   one.** `TvLibraryChrome`'s content-region `onExit` already redirects Up to
   `selectedPillFocus` and — going by Back, which uses the same requester and is verified on
   the box — that mechanism itself works. Rather than chase why a *default* geometric search
   might still win from this specific, deeply-nested row (an `onExit` on a distant ancestor
   competing with whatever Compose's own 2D search finds first is not something I can prove
   either way without a device), I wired the same target *explicitly* on every stop in the
   cover's own action row — the same pattern `TvDepartmentsBar`'s own pills already carry for
   their `down` target, rather than relying on inherited/bubbled behaviour for a case this
   important.
3. **A focused row's own heading landing tight under (or behind) the bar.** Not a bug in the
   `contentPadding` I set to `0.dp` for the cover's own bleed — `contentPadding` on a
   `LazyColumn` only ever reserves space before the very first item and after the last; every
   *other* item, the moment it becomes the topmost visible one (by any scroll, not just the
   restore path below), sits flush against the viewport's own top regardless of that setting.
   Fixed with a custom `BringIntoViewSpec` on Home's own `LazyColumn` that adds the bar's own
   clearance to *every* bring-into-view calculation reaching toward the top — reset back to the
   platform's own default one level down inside the three sections that scroll sideways on
   their own (Continue, Recently added, Latest series), since the clearance is a vertical-axis
   concept a horizontal `Row` would otherwise apply to its own unrelated left edge.
4. **(Higher priority, reported separately) Restoring focus after a title page fell through to
   a bar pill instead of the poster that opened it.** Each non-cover section (Continue,
   Recently added, Latest series, Latest courses) requested its own focus the instant *it*
   mounted, via its own `LaunchedEffect(focusAt)` — independent of, and racing, the outer
   `LazyColumn`'s own `scrollToItem` in `TvHome`'s top-level effect. On a fresh mount (returning
   from a title page disposes and rebuilds the whole frame) a section within the cache window
   can mount *before* the outer list has actually scrolled to it, and a `requestFocus()` fired
   then can land on nothing the current layout owns — indistinguishable, from the chrome's own
   point of view, from nothing ever having asked, so its own fallback (the selected pill) wins.
   Fixed by removing every one of those four independent effects and making the top-level
   effect in `TvHome` the *only* place that ever calls `requestFocus()`, always after
   `scrollToItem` and a confirmed sighting of the target item in `layoutInfo.visibleItemsInfo` —
   proven by a test that fails against the reverted code and passes against the fix (see below).

## Files

- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvHome.kt` — top-level effect now the sole
  `requestFocus()` caller for every section; `TvHomeBringIntoViewSpec` + wiring; `upExit`
  threaded through to the cover.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/home/{TvContinueBand,TvRecentBand,TvPosterStrip,TvCourseList}.kt` —
  their own independent `LaunchedEffect(focusAt){ requestFocus() }` removed (`TvCourseList`'s
  was the only one actually *providing* the request before phase 02's own commit — the other
  three now lose a race instead of racing safely); `takesFocus` parameter dropped, no longer
  needed.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/home/TvCoverSlide.kt` — `TvHomeBarClearance`
  constant; top padding on the words column.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/home/TvHomeCover.kt` — `takesFocus` param
  (and its own now-redundant effect) replaced by `upExit`, threaded to `TvCoverActions`; the
  cover's own arrival focus is requested by `TvHome`'s top-level effect too now, for the same
  reason as point 4 above (not separately reported, but the same race existed for the cover).
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/home/TvCoverActions.kt` — `upExit: FocusRequester`
  wired explicitly (`focusProperties { up = upExit }`) on Watch now, + My List, Details and
  every dot.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/{TvCatalogBody,TvCatalogScreen}.kt` —
  `selectedPillFocus` threaded from `nav.chromeFocus.selectedPillFocus` down to `TvHome`'s
  `upExit`.
- Tests: `TvHomeStateTest.kt` (new `upExit` parameter); `TvCatalogScreenStateTest.kt`
  (`homeRestoresFocusToAPosterBelowTheFirstSectionRatherThanAPill`, point 4's own regression
  test); `TvCoverSlideStateTest.kt` (new file, point 1's own test);
  `androidTest/.../TvCatalogScreenTest.kt` (`upFromTheCoversWatchNowLandsOnTheSelectedPillNotWhicheverSitsAboveIt`,
  point 2's own test — compiles, unverified by execution, no device here).

## Tests

- **Point 1**: `TvCoverSlideStateTest.theTitleNeverStartsAboveItsOwnBarClearanceEvenWhenTheFloorIsFarTooSmall` —
  drives `TvCoverSlide` directly with `minHeight = 1.dp` (far smaller than any real content, so
  the overflow case is forced regardless of this JVM's own font metrics rather than depending on
  a long title actually wrapping under them — confirmed empirically: the same assertion with a
  realistic 468dp floor and a real long title/tagline passed *even with the fix's own padding
  reverted*, meaning Robolectric's fallback font rendering never reproduces the real device's
  overflow on its own). Fails against the reverted padding, passes against the fix — checked
  both ways.
- **Point 4**: `TvCatalogScreenStateTest.homeRestoresFocusToAPosterBelowTheFirstSectionRatherThanAPill` —
  opens a collection from "Latest series" (below "Recently added"'s own eight posters),
  captures the restore key `onOpenCollection` was actually called with, remounts with it as
  `restoreKey`, and asserts the poster — not "Movies" or "Series" — is focused. Fails against
  the four sections' own reverted independent effects, passes against the fix — checked both
  ways.
- **Point 2**: androidTest, real window manager, no device access here — compiles
  (`:ui-tv:compileDebugAndroidTestKotlin`), not run.
- **Point 3**: no dedicated test. The `BringIntoViewSpec` fix is reasoned through against the
  actual Compose Foundation API on this classpath (probed by compiling small snippets against
  it — `BringIntoViewSpec`/`LocalBringIntoViewSpec` exist as I used them; there is no accessible
  "default instance" constant, so the fix captures whatever `LocalBringIntoViewSpec.current`
  already is and delegates to it) rather than device-verified or covered by an automated
  assertion — left for the lead's own on-box check.
- `:ui-tv:testDebugUnitTest` (full suite) and `scripts/check.sh` — both green.

## Left for the lead (on-box, no device access here)

1. All four fixes, visually: the cover's own title clear of the bar with a real long title;
   Up from Watch now landing on Home's own pill; a focused "Latest series"/"Latest courses"
   heading clear of the bar when scrolled there normally (not just via a restore); the original
   restore repro (Home → Watch now → Down ×4 → OK → Back) landing back on the poster.
2. Point 3's own `BringIntoViewSpec`: does it actually fire for an ordinary Down-navigated
   focus move (not just the restore path), and does resetting to the platform default inside
   Continue/Recently added/Latest series actually leave their own sideways scrolling
   undisturbed? Reasoned through, not seen.
3. R2 (Watch now → 10s → Back) janky-frame regression noted separately (16–22% vs 2–7% on
   0.82.1, 0 Davey either way) — not addressed here, expected to improve once phase 04 keeps
   Home composed across a round trip instead of disposing and rebuilding the whole frame on
   every title-page visit, which is also what points 2 and 4 above both trace back to.

## Unresolved questions

- Point 2's actual root cause (why the existing `onExit` redirect doesn't already cover the
  cover's own action row) is not confirmed — the explicit `focusProperties` wire sidesteps it
  rather than explaining it, which is enough to fix the reported symptom but leaves open
  whether the same gap exists for some other "page's own first row" case phase 03/04 might add
  later without a cover in front of it.
- Point 3's disambiguation: the custom spec adds clearance to *any* bring-into-view request
  near Home's own list-top, on the reasoning that the cover's own focusable controls (Watch
  now, + My List, Details, the dots) are all bottom-anchored and never trigger a "near the top"
  request themselves — true by construction today, but worth remembering if the cover ever
  grows a focusable control nearer its own top.
