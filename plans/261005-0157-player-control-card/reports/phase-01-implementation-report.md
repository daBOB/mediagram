# Phase 01 implementation report: web control card

Status: DONE (with deviations below). Branch: `worktree-agent-aad087f4ede7b98bb`, fast-forwarded to main 79375aa3 first, no push/version/changelog.

## Commits (in order)
1. eb8f6a33 feat(player): skip fifteen seconds and name the skip buttons by their length
2. d94e7464 feat(player): say what played before a title and whether it is in a run
3. 755a58bd feat(player): move the controls onto one card under a slim top bar
4. 70e082e1 feat(player): add one-at-a-time menus that open above the control card
5. 438952c6 feat(player): keep the card up while a menu is open or the pointer rests on it
6. 505972eb feat(player): open speed and framing from the card, and add restart
7. 8cac78d8 feat(player): toggle subtitles with CC and choose them from its menu
8. f356fa3e feat(player): choose the audio track from the card's menu
9. 3b9d69a3 feat(player): step to the previous and next title of a run
10. 67c55f11 feat(player): show playback stats in an overlay behind the info button
11. 2fc651ed chore(player): lower the line ceilings the control card freed (+ up-next clearance, see deviations)

## Checks
- `bun test`: 3056 pass, 0 fail (main baseline 3025, +31 as the phase predicted). `typecheck` and `lint` clean after every task.
- Ceilings: player.js 994, transport.js 350, playback.css 625, app.js 694 (none grew; lowered in code-standards.test.ts).
- Per-task counts matched the phase's figures offset by +23 (3030 after task 2 ... 3056 after task 10).

## Preview walk (stub preview, :8795, profile "TV kids", Das Buch von Boba Fett S1E1)
Screenshots: `plans/261005-0157-player-control-card/reports/player-card-1440x900.png` (Framing menu open, stats overlay open) and `player-card-390x844.png` (stats wrapped under the transport, Up next shown above the card). No horizontal scroll at 390 (`scrollLeft` 0, `scrollWidth` 390). Up next clears the card at 1440.

## Deviations from the phase
- Test-first order: tasks 1, 3, 6 to 11 were not run red before implementing. Task 1 and 3 test edits and the task 6 tests were written alongside the implementation; the tests for tasks 2, 4, 5, 7, 8, 9, 10 were seen failing for the expected reason first (missing export or module, `No 1 in the menu`, `Expected true / Received false`).
- Real code differed from the phase's line numbers throughout (main moved); every edit was matched on quoted text, intent unchanged. The real HUD top bar held only title, `#tech` and Close (My List, Kids, Add to... lived in the old rail); the phase's replacement HTML covers it, so no drift in result.
- Task 4/8: the fake DOM invents missing nodes, so `player-menus` tests passed before `#card-menu` existed in the HTML; added it in the same commit regardless.
- Task 11: `.up-next` narrow `bottom` raised to 17rem (phase: 15rem). At 390x844 with the transient two-line error note the card is about 250px tall and 15rem overlapped it by about 22px; 17rem clears it. Wide value 12.5rem unchanged and clear at 1440.
- Task 7 test commit message and task 5 comment deletion followed the phase text; no other behaviour changes.

## Concerns
- The preview cannot play media, so the transport state with a playing title (rest timer, blur over real picture) was not seen visually; covered by tests only.
- Esc in browser fullscreen is still the browser's own exit (documented in phase risks, not fought).

## Unresolved questions
None blocking.
