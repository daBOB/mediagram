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
