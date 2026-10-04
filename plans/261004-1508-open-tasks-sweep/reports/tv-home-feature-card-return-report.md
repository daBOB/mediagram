# TV Home — Back from the Editor's choice card lands on the Home pill

Branch `worktree-agent-a1a7072cf38440327`, from `main` 0687a760 (0.109.0). Fix commit
`faaf4458`. No version bump (lead bumps). Not pushed. No logging added or left.

## Mechanism (reproduced in Robolectric, not only reasoned)

1. Back removes the title frame. Its focused Play button detaches; at the end of that
   apply Compose clears owner focus and Android (non-touch mode) re-grants it to the
   first focusable from the top: the bar's **Home pill**. Same frame as Home is rebuilt.
2. Home's arrival effect (`TvHome.kt`, `LaunchedEffect(target)`) scrolls to the features
   item, waits for it in `visibleItemsInfo`, then `featuresFocus.requestFocus()`.
3. That request **enters** the `LazyColumn` from outside (from the pill on the box, from
   nothing in Robolectric), so the list's `focusRestorer` `onEnter` runs
   (`performCustomRequestFocus` → `performCustomEnter`, Compose ui 1.11.4).
   `restoreFocusedChild()` finds nothing — the restorer's own `onExit` (which saves) is
   replaced by the chrome's outer `focusProperties { onExit }` (one `onExit` per focus
   target; outer wins), so it never saves. It then calls
   `fallback.requestFocus()`, and the fallback was `included.first()` = **the cover's Watch now**.
4. The redirect only succeeds when the cover is still composed. Focusing the lead feature
   card (box bounds 480..1080 of 1080) leaves the cover half in view, so the restored
   scroll composes the cover again on return. Watch now takes focus while the arrival has
   just scrolled it out of view; the list then lets the item go → focused node removed →
   owner refocus → bar's first pill. Trending/Best-rated, Continue, Recent, Series, Courses
   are focused with the cover scrolled away, so `coverFocus` is unattached, the fallback
   request fails, no redirect, and the card gets focus — why only Editor's choice was seen.

Diagnostic trace (temporary `println`s, removed), Back from Editor's choice, cover drawn:
`request FEATURES/0 visible=[features]` → `band COVER ActiveParent` → `result=true` →
`band COVER Inactive` → `bar ActiveParent`. Trending: `band COVER` never composed, card focused.

Why the earlier walks passed: their fixture gave films a backdrop but no poster, and the
cover's pick needs a poster, so no cover was drawn; the list's first section was FEATURES,
i.e. the fallback was the target itself. Not the "first return after launch" theory.
Collections/franchise, search and person pages work because their restorer fallback *is*
the target's own requester (`TvCollectionsPage.kt:146`, department pages likewise).

## Fix (`android/ui-tv/src/main/kotlin/ui/tv/catalog/TvHome.kt`)

- Restorer fallback = the pending arrival's own stop until `arrived`, then the first
  section as before → the redirect lands where the request was going; Down from the bar
  onto the page still lands on the cover's Watch now (unchanged).
- `drawn`: one list of the sections the `LazyColumn` actually draws an item for, used for
  both the `item {}` conditions and the arrival's `scrollToItem`/wait index. `included`
  skipped Continue-with-only-the-quote and Recent-with-only-This-month, so the arrival
  scrolled to and waited for the wrong item for every band below them (B1h noted it).

## Tests

`TvHomeReturnTest` (replaces `TvHomeFeatureReturnTest`): full app, cover drawn, one walk
per band — cover Details (lands on Watch now of the same story), Editor's choice,
Trending, Best-rated, Continue (via the player, Back ×2), Recently added poster, Latest
series, Latest courses — plus the remote's own key walk (non-touch, DPAD) for Editor's choice.

Before the fix: **2 of 9 fail** (Editor's choice semantic walk and key walk — focus ends on
the cover, then off the page). The other 7 pass before and after (cover not composed on
their return, as above) — kept as regression guards. The `drawn` index change has no
failing-first test (the cache window composes the next item anyway in Robolectric).

`./gradlew -q :ui-tv:testDebugUnitTest :ui-tv:compileDebugAndroidTestKotlin lint` — exit 0;
ui-tv 497 tests, 0 failures, 0 skipped; lint: nothing new on changed files (the one
`TvHomeCover.kt:95` warning predates this).

## Box check

Home → Down onto Editor's choice → OK → Back: the remote is on the Editor's choice card
(not the Home pill, not Watch now). Also worth one pass: Trending, a Continue card (via
the player), a Recently added poster. And Up to the Home pill → Down: lands on the cover's
Watch now as before.

## Observed, not changed

- The chrome's outer `onExit` disables every page restorer's save, so the rail's Right
  ("the page's own restorer puts the remote back on the plate it left",
  `TvLibraryChrome.kt:248`) can only reach a page's fallback stop, never the plate left.
  Not part of this bug; worth a ticket if the rail's Right is expected to restore.

## Unresolved questions

- Back from the cover's Details lands on Watch now of that story (existing design), not
  on Details itself. Change only if the user wants the pressed button back.
