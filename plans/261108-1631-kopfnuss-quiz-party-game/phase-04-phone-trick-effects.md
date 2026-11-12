---
phase: 4
title: "Phone trick effects"
status: completed
priority: P1
effort: "3h"
dependencies: [1]
---

# Phase 04: Phone trick effects

## Overview
`ChoiceScreen` draws `screen.tricks`. The effect logic is pure functions of time, so
bun can test it. With no tricks, every other game looks exactly as before.

## Requirements
- **Wechselbad:** every 1.5 s after `opens_at` the button order permutes (seeded by `opens_at`, deterministic). The tap still sends the **original** index.
- **Schneckenpost:** each label shows `ceil(len * t / 4000)` characters, `t` = ms since `opens_at`.
- **Spiegel:** labels mirrored (`transform: scaleX(-1)` on the label span only). The screen reader reads them normally.
- **Kleber:** a pick fires only after a 1 s hold (pointer or Enter/Space key), with a visible fill. Releasing early cancels it. `at` = server time when the hold completes.
- Effects stack. A small trick badge row in the header shows the trick names.
- **No tricks:** identical DOM and behavior to today.

## Architecture
- `client/src/phone/choice-tricks.ts`: `slotOrder(n, opensAt, now)`, `revealed(label, opensAt, now)`, `holdDone(startedAt, now)`, `HOLD_MS`, `SWAP_MS`, `REVEAL_MS`.
- `PhoneApp.tsx` ChoiceScreen: maps over `slotOrder` for display and keeps the original index for `pick`; `useServerNow` already re-renders.
- CSS in `client/src/styles.css` (or a new `choice-tricks.css`).

## Related Code Files
- Create: `client/src/phone/choice-tricks.ts`, `client/tests/choice-tricks.test.ts`
- Modify: `client/src/phone/PhoneApp.tsx` (ChoiceScreen; move it to `phone/ChoiceScreen.tsx` if PhoneApp passes 200 lines), CSS

## Tests first (`bun test`)
1. `slotOrder` is the identity before 1.5 s, changes at 1.5 s, and is deterministic for the same inputs.
2. It is always a permutation.
3. `revealed` returns "" at t = 0, the full label at ≥ 4 s, and the right partial length in between.
4. `holdDone` is false at 999 ms and true at 1000 ms.

## Implementation Steps
1. Write the tests (red), then `choice-tricks.ts` (green).
2. Wire up ChoiceScreen: display order, label transform, hold handler (pointerdown/up/leave, keydown/keyup).
3. Manual check in the browser at phone width; take screenshots with each trick and with all 3 stacked.

## Success Criteria
- [ ] bun tests green; typecheck green
- [ ] The Quiz phone looks the same as before (screenshot diff by eye)

## Risk Assessment
- A swap during a tap could hit the wrong button → the swap animates over 150 ms and a tap uses the slot under the pointer at pointerdown. Accepted as part of the comedy.
- The Kleber hold vs. the existing `onPointerDown` instant pick → the hold path is used only when `tricks` includes Kleber.
