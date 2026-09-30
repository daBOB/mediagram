# Phase 04 — the conditional-`focusRequester` root cause, fourth round

Follow-up to `fullstack-developer-260930-0430-phase-04-cold-start-safety-net-report.md`,
implementing the root cause the lead found on the box against 13cc3130:
playing the second Continue card, then Back, lost focus to the Home pill
every time — not the held-state release, not lazy item reuse, but every
row on this surface attaching its own arrival `FocusRequester` to whichever
card an index currently named and omitting it from every other one.

## The mechanism, confirmed by the lead's own stacks

`TvContinueBand`/`TvResumeCard`'s own pattern:
`focusRequester = if (index == focusAt) focus else null`, then
`.let { if (focusRequester != null) it.focusRequester(focusRequester) else it }`.
The instant `focusAt` moves (a played card reordering to the front), the
card that just had the modifier loses it and the card now at that index
gains it. Compose detaches and rebuilds a focus target's whole modifier
chain when a modifier structurally appears or disappears ahead of it in
that chain — the currently focused card's own `FocusTargetNode` resets,
clearing focus, and the root's own directional search lands on the first
focusable it finds: the bar.

## Fix: every card carries its own stable requester, always attached

Grepped `index == focusAt` and every conditional `focusRequester` across
`ui-tv/src/main` and fixed each the same way — never omit the modifier;
change only which requester it carries:

```kotlin
val ownRequester = remember { FocusRequester() }
...
.focusRequester(if (index == focusAt) focus else ownRequester)
```

`ownRequester` is `remember`ed inside each item's own keyed composition
scope (`key(id) { ... }` or a lazy `items(key = ...)` block, already
present everywhere this pattern lived, or added where it was missing) —
stable across a reorder the same way the card's own visual identity is.

**Leaves fixed once, covering every caller that passes them a nullable
requester:**
- `TvResumeCard.kt` — `TvContinueBand`, `TvDepartmentResumeRow`.
- `TvTextRow.kt` — `TvListRow`, `TvCollectionRows`' non-document rows,
  `TvLists`' "＋ New list", `TvSearchRows`' show/destination rows, every
  settings/menu text row already routed through it.
- `TvSettingsChoices.kt`'s `TvChoiceRow`, `TvProfileTiles.kt`'s
  `TileCard`, `TvAppearanceBlock.kt`'s `TvAccentSwatch` — static choice
  lists that never reorder, fixed anyway per "fix them all the same way":
  the pattern is identical and the cost of leaving one is a second
  instance of exactly this bug turning up later on a screen nobody thought
  to check.

**Fixed directly, one call site at a time (the conditional lives in the
row itself, not a shared leaf's own nullable parameter):**
`TvRecentBand.kt`, `TvPosterStrip.kt`, `TvCourseList.kt`,
`TvHomeFeatures.kt` (two call sites; wrapped in `key(id) { }`, which it did
not have before — features never had a stable per-item scope), `TvWall.kt`
(the arrival requester only — see below), `TvDepartmentRows.kt` (`DeptRow`,
`DeptEntryRow`, `GenreTileRow`), `TvCollectionRows.kt`'s `DocumentRow`,
`TvCastRow.kt`, `TvSimilarRow.kt` (also gained `key(id) { }` — it had none,
a plain unkeyed `forEachIndexed`), `TvSearchRows.kt`'s `TvSearchRow`/
`TvPersonSearchRow` (direct fix) and `TvShowSearchRow`/
`TvDestinationSearchRow` (switched to passing `focusRequester =` into the
now-fixed `TvTextRow` instead of building their own conditional modifier),
`TvDepartmentsBar.kt`'s own pills, `TvCollectionsPage.kt`'s franchise row.

**Left alone, on purpose:** `TvWall.kt`'s `crossingRequester` (the Up/Down
section-crossing override) stays conditional. It is not the same
mechanism — it names whichever card currently sits at a section boundary
*by position*, not a card that must keep a specific identity as it moves,
so there is no "the focused card's own modifier disappears out from under
it" case to cause. Touching it risks the crossing feature itself
(`TvLibraryChromeUpTest`'s own kind of coverage) for no bug it has.

## Test

Added `aRestoredSecondCardKeepsTheRemoteWhenItMovesToTheFront` to
`TvHomeStateTest.kt`, matching the lead's own repro shape directly (a
*second* card, index 1, moving to the front) alongside the existing
`aRestoredCardKeepsTheRemoteWhenItsRowReordersUnderIt` (a *last* card
moving to the front, from phase 02).

**Neither test can show this regression in Robolectric — checked, not
assumed.** Hand-reverted `TvResumeCard.kt`'s own fix (the file the lead's
own stacks named), re-ran both tests unchanged: still green. Compose's
`LazyLayout` reuse pool and the `FocusTargetNode.onReset` it fires when a
modifier's own presence toggles are pure Compose-runtime mechanisms, not
Android-View shadows the way the first regression's cascade was — so this
was worth checking rather than assuming the same limitation carried over,
and it did carry over. Restored the fix immediately after confirming.
Kept both tests: they prove the *logic* (the right card is targeted, a
reorder does not otherwise break restoration) even though only the box can
prove the *modifier-chain* fix holds.

## The safety net: removed, per the lead's own instruction

0.84.3's three-frame re-check in `TvHome.kt`'s own grant effect
(`homeHasFocus`, `SafetyNetFrames`, the `repeat(3) { withFrameNanos {} }`)
is gone — `TvHome`'s own arrival grant is back to a single
`requestFocus()` call, no follow-up. The instruction was to remove it if
the new tests pass without it; they do, though as above that is not strong
evidence either way given Robolectric's own limitation here. Removed
anyway because the root cause is now fixed everywhere the same pattern
existed, not only in `TvHome`'s own Recently Added row, and the lead's own
stated preference was removal absent a specific case the root fix does not
cover — I have none to show. If the box still loses focus somewhere this
sweep did not reach, the safety net's own diff is in git history (13cc3130)
and is a small, mechanical thing to bring back for that one path rather
than something to redesign from scratch.

## Verification

`:ui-tv:testDebugUnitTest --rerun`: full suite green. `scripts/check.sh`:
green.

## For the lead

The full re-verification list is unchanged from the second report:
cold start, second-card play (now the specific regression this round
fixes), the cover round trip, title trips, deep trips, sentinel returns.
Two additions specific to this round:
1. **The franchise row on Collections** and **the departments bar's own
   pills** now carry the same fix — no box report named these broken, but
   they had the identical pattern; worth a glance during the walk in case
   a franchise list ever reorders (a library growing a franchise's films)
   or a pill row does something unexpected mid-press.
2. **If the cold-start repro (0.84.2/0.84.3's own regression) or the
   second-card repro (this round's) still fails on the box** after this
   fix, that is a materially different signal than before: it would mean
   the modifier-chain mechanism was not the only cause, or this sweep
   missed an instance of the pattern. Worth a fresh `Log.e` pass at that
   point rather than assuming it is the same bug again.

## Files touched

`android/ui-tv/src/main/kotlin/ui/tv/catalog/home/TvResumeCard.kt`,
`TvContinueBand.kt` (doc only), `TvRecentBand.kt`, `TvPosterStrip.kt`,
`TvCourseList.kt`, `TvHomeFeatures.kt`;
`android/ui-tv/src/main/kotlin/ui/tv/TvTextRow.kt`;
`android/ui-tv/src/main/kotlin/ui/tv/catalog/TvDepartmentRows.kt`,
`TvWall.kt`, `TvCollectionRows.kt`, `TvCastRow.kt`, `TvSimilarRow.kt`,
`TvSearchRows.kt`, `TvCollectionsPage.kt`, `TvDepartmentResumeRow.kt`
(unchanged; fixed via `TvResumeCard.kt`), `TvLists.kt` (unchanged; fixed
via `TvTextRow.kt`);
`android/ui-tv/src/main/kotlin/ui/tv/chrome/TvDepartmentsBar.kt`;
`android/ui-tv/src/main/kotlin/ui/tv/player/TvSettingsChoices.kt`;
`android/ui-tv/src/main/kotlin/ui/tv/profile/TvProfileTiles.kt`;
`android/ui-tv/src/main/kotlin/ui/tv/system/TvAppearanceBlock.kt`;
`android/ui-tv/src/main/kotlin/ui/tv/catalog/TvHome.kt` (safety net
removed); `android/ui-tv/src/test/kotlin/ui/tv/catalog/TvHomeStateTest.kt`.
Version 0.84.3 → 0.84.4 (`Cargo.toml`, `Cargo.lock` ×5 workspace crates,
`web/package.json`, `android/app/build.gradle.kts`); changelog entry.

## Unresolved questions

None from this side. Whether the fix holds is, as with the two rounds
before it, a box question now.
