# Phase 02 implementation report: web episode sidebar

Status: DONE (with deviations). Fast-forwarded to main 5fe377e0 first. No push, version bump or changelog.

## Commits
1. c40f52f9 feat(player): group a run into seasons or sections for the episode sidebar
2. 6ce98256 feat(player): add the episode sidebar
3. 79421268 feat(player): open the episode sidebar from the card for a show or course
4. docs(player): describe the episode sidebar (DESIGN.md + this report + screenshots)

## Checks
- `bun test`: 3083 pass, 0 fail (baseline 3060 on main; +23: the plan's 22 plus the sidebar-open class test). typecheck, lint clean.
- Ceilings unchanged: player.js 994, app.js 694. player-card.css is at 200 lines (limit); the new files are under 200.
- Red seen first for each task: missing module (tasks 1, 2), `Expected true / Received false` and a missing `children[1]` (task 3).

## Preview walk (stub preview :8795, profile "TV test", Californication S2E3, S2E1-2 watched, S2E4 part-watched)
- `reports/episode-sidebar-1440x900.png`: opens on Season 2, "Now playing" in view, watched rows greyed with a tick, progress line under S2E4, card to the left of the sidebar (right edge 100px short of it, centred in the remaining width).
- `reports/episode-sidebar-390x844.png`: sidebar 360px at left 30, `scrollLeft` 0, `scrollWidth` 390.
- Pick a row opened S1E1 after ‹ and the sidebar shut; the sidebar class cleared.
- Not walked in the preview (covered by tests only): Esc order with a menu open, film and list with no ☰.

## Deviations from the phase
- Plan CSS had `display: flex` on `.episode-sidebar`, which beats the `hidden` attribute; added `.episode-sidebar[hidden] { display: none; }`.
- The card-clears-sidebar rule is `dialog.sidebar-open .card-dock { right: calc(360px + 24px) }` at min-width 768px only. Below that the sidebar (360px of a 390 screen) covers the card, as the ruling allows. Class toggled in `episode-sidebar.js` (`show`/`close`) with a test.
- player-card.css hit the 200-line limit with the block; the card-clearance comment is one line.
- Task 2 test run and Task 3 hunks: phase 01's fix round did not conflict; plan line numbers matched text, applied by quoted text. `nextAfter` is gone as expected and nothing here used it. `holding` is the fix round's `menus.isOpen() || upNext.sidebarOpen()`.
- Task 4 DESIGN.md: also notes the card is laid out to the sidebar's left.

## Concerns
- While the sidebar is open it covers the right of the top bar (My List, Kids, the player's own close). The sidebar has its own close, Esc and Back close it; the top bar is not shifted. Cheap to fix later by starting the sidebar below the bar, but the bar's height varies at phone width.
- Preview cannot play media, so the HUD hold while the sidebar is open is test-covered only.

## Follow-up: top bar clears the open sidebar (lead ruling)
Commit 51727868. At 768px and above, `dialog.sidebar-open .hud-top` gets `right: 360px`, wraps, and the title takes its own row (same as the phone layout), so My List, Kids, Add to, Notes and the player's close stay visible and clickable beside the sidebar. Below 768px the sidebar still covers it.
- Test (red first): `web/test/episode-sidebar-clearance.test.ts` reads `player-card.css` and asserts the 768px block offsets both `.card-dock` and `.hud-top` by the sidebar's width.
- Two selector lists in the sidebar CSS were put on one line each to stay at the 200-line limit.
- `episode-sidebar-1440x900.png` re-shot in the preview: My List, FSK badge, Add to and the close are fully visible left of the sidebar. 3084 pass, typecheck and lint clean.
- The earlier concern is resolved. Notes was not on this title, so its button was not seen in the shot; it sits in the same bar.

## Fix round 1 (review: phase-02-review-report.md)
Commits c99d67b5 (H1, L2) and 6e82ba87 (M1, M2, L1, L3). Each test written and seen failing first. Final: 3093 pass, typecheck and lint clean.

| Item | Fix |
|---|---|
| H1 focus lost | `close()` returns focus to ☰ when focus was inside the panel (covers ✕, Esc, a pick; the Esc handler's own focus call is gone). `open()` moves focus to play/pause if ☰ had focus and the next title has no list. `draw()` hands focus to the other arrow (or the panel) when the pressed arrow disables itself. Tests: focus into the panel on open, ☰ after ✕/Esc/pick, play/pause when ☰ disappears, never a disabled arrow, Esc after stepping to an end closes only the sidebar. |
| M1 notes | `dialog.sidebar-open.with-notes` offsets card and top bar by `max(var(--notes-width), var(--sidebar-width))`. Preview (with-notes class forced at 1440): card right 872 = notes edge 896 - 24, top bar right 896. |
| M2 breakpoint | Card clearance moved from 768px to 900px. Measured in the preview: the bottom row needs a card about 482px wide, so a window of about 890px; at 888 the ☰ was 2px short of the card's padding, at 892 it fit. 900 keeps a margin. Recorded in the CSS comment. 768-899: the sidebar covers the card; the top bar still clears it from 768 (earlier ruling). Checked at 904, 880, 768. |
| L1 weak test | Width is `--sidebar-width: 360px`, used by the sidebar's own width and every clearance; the test asserts exact values (`calc(max(...) + 24px)` etc.), so `calc(360px - 360px)` fails. |
| L2 keys | A keydown listener on the panel stops arrows, Enter and Space from reaching the player's handler; Esc and Tab pass. Test with a spy on `stopPropagation`. |
| L3 titles | `.sidebar-title` clamped to two lines; `title=` holds the whole name. |
| L4 | not requested. |
| Parked | web/Android grouping edge case, as instructed. |

Deviation: the sidebar CSS moved out of `player-card.css` (at its 200-line limit) into `public/styles/episode-sidebar.css`, linked after it in `index.html`. `episode-sidebar-1440x900.png` re-shot. Focus after ✕ confirmed in Chrome via the preview (activeElement is ☰).
