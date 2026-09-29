# Phase 02 — the restore-to-pill regression, root-caused

Follow-up to `fullstack-developer-260929-1015-phase-02-onbox-fixes-report.md`, after the
coordinator's second on-box pass of `48be6cbc` (0.83.0 benchmark) found the three fixes there
holding but a fourth, higher-priority regression still live: leaving the player, or a title opened
from Home, could land the remote on a bar pill instead of the plate that opened it. Same branch,
`feat/tv-web-look-home`, still `0.83.0` (unreleased, no version bump).

## Root cause

Home's own top-level effect in `TvHome.kt` is keyed on `target` — the section and stop a restore
key resolves to — so that a request only fires once the outer list has actually scrolled there and
confirmed it in `visibleItemsInfo`. What it did not account for: `target` is derived from
`magazine`, and Continue Watching's own order is live, not frozen at the moment Home remounts —
playing a title writes a new position, and that write can land *while this page is already back
on screen*, moving the just-played card to the row's own front. Each time it moved, `target`
changed value, and `LaunchedEffect(target)` restarted from scratch: re-scroll, re-wait, re-request
— calling `requestFocus()` again on every move, however many times the position write settled in.
Two (or more) of those calls arriving close enough together is indistinguishable, to the chrome
above this list, from nothing ever having asked at all, which is exactly what sends the remote to
its own fallback — the bar's selected pill, `TvLibraryChrome`'s documented "nothing in content
claimed arrival focus" case, not a bug in that fallback itself.

Confirmed the fix's own necessity, not just its plausibility: `TvHomeStateTest.kt` already carried
a test claiming "content arriving or reordering while the viewer is elsewhere never pulls the
remote back to a restored stop a second time" — but its own reorder handed `TvHome` a *structurally
equal* `MagazineHome` (both empty), so `remember`'s own key comparison never even saw a change and
the claim went unexercised. A new test with a **genuine** reorder (below) fails against today's
code exactly as the box did — a card the viewer had explicitly moved away from pulls the remote
back the instant its own row reorders under it — and passes once the fix below is in.

## Fix

`TvHome.kt`: arrival focus is now granted once per mount, not once per value `target` happens to
take. A `remember { mutableStateOf(false) }` flag, checked and set inside the same effect:
once the one grant lands, a `target` that changes afterward still updates which card carries the
section's own `FocusRequester` (so the row's visuals stay correct), but the effect's own early
return skips ever calling `requestFocus()` a second time. A `target` that changes *before* the
first grant lands (the wait for `scrollToItem`/`visibleItemsInfo` still pending) still restarts
cleanly with the newer, more current value — the guard only stops chasing *after* a request has
actually succeeded once, not before.

Whether the pill is ever reached with nowhere else to land was also asked to be confirmed as a
structural property, not something needing its own new fallback: `homeTargetOf` already resolves
to "the first section with anything in it, at its own first stop" whenever a restore key names
nothing (or names something no section still carries), and `target`/`included` are both derived
from the identical `sections` snapshot within one recomposition, so `target.section` is always a
member of `included` by construction — the effect's own `itemIndex == -1` early return is dead
code today, not a second path to the pill. The only way the pill was ever reached was the effect
never completing at all, which the guard above removes.

## Files

- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvHome.kt` — the `arrived` guard on the top-level
  effect; three new imports (`getValue`, `mutableStateOf`, `setValue`).
- `android/ui-tv/src/test/kotlin/ui/tv/catalog/TvHomeStateTest.kt` — new test
  `aReorderAfterArrivalMovesTheCardNotTheRemote`, plus `film`/`continueMagazine` helpers
  (`emptyMagazine` now delegates to `continueMagazine(emptyList())` rather than duplicating it).

## Test

`TvHomeStateTest.aReorderAfterArrivalMovesTheCardNotTheRemote` — two Continue cards, restore key
naming the one *not* first; arrival lands on it (sanity check), the viewer explicitly moves on to
a series poster, then the same two cards are handed back **reordered** (the restore key's own card
now first) — a real value change, not the sibling test's equal-content one. Checked both ways:
reverted the guard (kept `arrived` declared but unread, so the diff stayed to exactly the one
condition) and confirmed this test fails there — the reorder pulls focus straight back off the
poster onto the card — then restored the guard and confirmed it passes.

`:ui-tv:testDebugUnitTest` (full suite, all classes), `:ui-tv:compileDebugAndroidTestKotlin`, and
`scripts/check.sh` — all green.

## On the two on-box repros

FAIL A (Watch now/a Continue card → play → Back lands on a pill) and FAIL B (chained from A's
already-wrong state: a Recently Added poster's own title round trip *also* landing on a pill) were
asked to be confirmed as the same root cause. The guard above is not Continue-specific — it lives
in the one effect every section funnels through — so once Continue's own reorder is what pulled
focus to the pill in FAIL A, the state that left Home in was already wrong going into FAIL B: Back
from FAIL A's own broken state does not undo the reorder still settling, and the guard blocking a
second `requestFocus()` for the *original* target could in principle leave a later, unrelated
target similarly one-shot — but a later target is a **different** mount's own fresh `arrived`
flag (Home fully remounts between rounds, per the existing R2 note), so FAIL B's own title-page
round trip gets its own clean attempt. I read this as confirming the same root cause rather than a
second one: FAIL B's repro starts from a pill already wrongly focused only because FAIL A left it
there, and the fix that stops FAIL A from reaching the pill removes the state FAIL B's own repro
was chained from. Left for the box, not provable further from here without a device.

## Left for the lead (on-box, no device access here)

1. The original repro (Home → Watch now → Down ×4 → OK on a Continue card → wait 10s → Back) and
   its Hercules repeat, landing on the played card rather than the pill.
2. FAIL B's own chain, confirming it no longer reproduces once FAIL A does not leave Home in a
   wrong state to chain from.
3. Whether a burst of reorders during a *longer* watch (more than the two writes this fix was
   reasoned against) still only ever grants focus once — the guard's own contract holds for any
   number of restarts before the first grant, but only a real device proves whether Continue's own
   writes actually arrive in a burst that size.

## Unresolved questions

- Whether the real device failure is exactly "a second `requestFocus()` call lands on nothing" (my
  working theory, matching the coordinator's own "gives up" wording) or some other Compose-internal
  timing detail of a `FocusRequester` moving between two Row slots without an explicit `key()` per
  card — the fix removes the *entire class* of repeated-restart behavior either way, so it holds
  regardless of which exact internal mechanism was firing, but I could not pin down the one true
  mechanism without a device to instrument.
