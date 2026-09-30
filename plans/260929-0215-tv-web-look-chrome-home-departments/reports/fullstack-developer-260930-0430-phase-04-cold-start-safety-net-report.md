# Phase 04 — cold-start return, third round

Follow-up to `fullstack-developer-260930-0345-phase-04-home-pill-return-fix-report.md`,
addressing the lead's box report against 4fa10823: the first title-page
return after a cold start (`am force-stop` → launch → Down → OK → Back)
still landed on the Home pill, 2/2; later returns worked, 7/7.

## What the lead's stacks show

1. "bar gained focus" via `AndroidComposeView.clearOwnerFocus` — the
   removal reset from the first bug, now correctly landing *after* Home's
   own arrival request (the delay fix holds).
2. "arrival requesting RECENT" — the request succeeds.
3. "bar gained focus" *again*, moments later, via
   `AndroidComposeView.outOfFrameRunnable → FocusTargetNode.onReset →
   FocusOwnerImpl.clearFocus`. A lazy-layout slot holding the just-focused
   node is being reset (reused or deactivated) after the grant already
   landed, and the root's own re-entry search picks the bar again.

The lead's own hypothesis: `heldWhile` releases the catalogue's real state
the instant Home uncovers; on a cold start a startup refresh changed it
while the title page was open, so this is the *first* time Home's tree
recomposes with different data since launch — and something in that
recompose recycles the focused node's own slot.

## What I found

**`TvRecentBand`'s own row is not a lazy layout.** `TvRecentPosterRow`
(`TvRecentBand.kt`) is a plain `Row` with `.horizontalScroll(...)`, each
card wrapped in `androidx.compose.runtime.key(set.setId)` — the general
composition-identity primitive, not `LazyRow`'s own keyed `items()`. Plain
composition supports moving a keyed subtree across positions without
disposing it; it has no reuse pool and no `FocusTargetNode.onReset` at all
— that mechanism exists specifically for lazy layouts' own reusable nodes.
So the reset in stack #3 cannot be this row directly; it has to be the
*outer* `LazyColumn` in `TvHome.kt` (`item(key = "recent") { ... }`) —
recycling that whole section's own slot would tear down and rebuild
everything inside it, the plain row and its focused card included, without
either needing its own reuse pool for that to happen.

**I could not reproduce this in Robolectric.** Tried the lead's own
suggested shape directly: opened a title page from a two-plain-film Home,
restubbed the fake repository mid-visit (`coEvery { repository.sets() }`)
to add a new upload at the front of Recently Added, called
`catalog.reload()`, then Back — no focus loss. Tried it again with the new
upload also carrying a poster and a backdrop (a cover appearing for the
first time, the more drastic layout shift a real cold start's own
enrichment pass would plausibly cause) — same result, no loss. Both are
committed as `TvHomeKeptAliveTest.aRefreshHeldWhileCoveredStillRestoresThePosterOnceRecomposed`.
I do not know whether Robolectric's `LazyLayout` reuse-pool bookkeeping
genuinely differs from a real device's here, or whether the box's own
trigger needs a shape neither of these two attempts reproduced (a larger
shift, a different section gaining/losing its own conditional presence,
timing specific to a real Choreographer). Said so rather than claiming a
root-cause fix I could not verify.

## What I built instead: the safety net, root-cause fix left undone

Given I could not pin down or verify the exact trigger, I did not guess at
a root-cause change to `TvHome`'s `LazyColumn` keys/content types or to
`heldWhile`'s own release timing — an unverified change to either risks
trading one unproven bug for another. Built the safety net the lead asked
for instead, in the one place this already lives (`TvHome.kt`'s own grant
effect, no new file):

- A `homeHasFocus` flag, read from `onFocusChanged` on Home's own
  `LazyColumn` — is focus anywhere in this page right now.
- Right after the grant's own `requester.requestFocus()` succeeds, wait
  three frames (`withFrameNanos`, a fixed, small, named constant), then
  check: if nothing in Home holds focus and this page is still uncovered,
  ask the same requester once more.

Why this cannot answer for a deliberate move to the bar instead of the
symptom this fixes: three frames is ~50ms at 60fps. Back has to be pressed,
the frame that pops it processed, the one-frame arrival delay from the
first fix elapsed, the grant's own scroll-and-confirm awaited, *then* this
three-frame window — a viewer physically pressing Up in that remaining
window, on top of everything already elapsed, is not a real scenario at
human reaction times. If it ever were, the cost is one extra bounce back
into content the viewer corrects with a second Up press, not a stuck state.

## Verification

`:ui-tv:testDebugUnitTest --rerun`: full suite green, including the two new
cold-start-shaped tests and everything from the two prior reports.
`scripts/check.sh`: green. I did not write a dedicated test proving the
safety net's own *trigger* fires under a real steal, for the same reason I
could not reproduce the underlying bug: I have no reliable way in
Robolectric to interject a focus steal between the grant's own
`requestFocus()` and its three-frame check without racing Compose's own
frame scheduling by hand, which risks a test that passes for the wrong
reason. The grant path itself (`requester`, `target`, `entryFocus`) is the
same, already-tested path the rest of this page's own tests exercise.

## For the lead

1. **Re-run the cold-start repro** (`am force-stop` → launch → Down → OK →
   Back) a good number of times — the safety net should catch the case the
   stacks showed even without knowing exactly which layout event triggers
   it, but the trigger itself is still unconfirmed, so a repeat with a
   longer hold before Back, or a second refresh landing later, is worth
   trying too.
2. If it still fails: the three-frame window is a guess bounded by the
   single `outOfFrameRunnable` hop the stack showed, not something I could
   tune against the real timing. Log timestamps on grant vs. reset if it
   recurs, and I can widen it — a fixed, named constant, one line.
3. Player return, cover round trip, deeper round trips, sentinel returns —
   still exactly the checklist from the second report; nothing here changes
   what to look for there.

## Files touched (this round)

- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvHome.kt` — `homeHasFocus`,
  the grant effect's own three-frame safety net, `SafetyNetFrames`.
- `android/ui-tv/src/test/kotlin/ui/tv/TvHomeKeptAliveTest.kt` — two
  cold-start-shaped repro attempts (both green, neither shows the box's
  failure).
- Version 0.84.2 → 0.84.3 (`Cargo.toml`, `Cargo.lock` ×5 workspace crates,
  `web/package.json`, `android/app/build.gradle.kts`); changelog entry.

## Unresolved questions

- The exact lazy-layout trigger (which section, which condition) is still
  unknown — flagged, not fixed. If the safety net does not resolve it on
  the box, the next step is adding real device logging around
  `TvHome`'s own `LazyColumn` composition/disposal for the "recent" item
  specifically, which needs the box, not Robolectric.
