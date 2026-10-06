# TV box walk: the player control card (0.117.1)

**When:** 2026-10-06, 02:20–02:36.
**Where:** the real box at 192.168.0.35. It had 0.117.1 from the channel, installed by the other session's release at 01:52.
**Profile:** "andre". The user ruled this, because "TV test" can only be entered after choosing a PIN, and getting back to andre would then put a PIN on andre on every device.
**Method:** the walk ran entirely while paused, so no settings were changed. Screenshots are in [tv-box-walk-261006/](tv-box-walk-261006/).

## Footprint

| Title | Position | In Continue? | Stats |
|---|---|---|---|
| Trio mit vier Fäusten S1E7 | none saved: under the 1 s floor, then left through the sidebar | no | none |
| Trio mit vier Fäusten S1E8 | 0.2 s | no (under 30 s) | 0.0 s |
| Videodrome | 0.3 s | no (under 30 s) | 0.0 s |

The 30 s rule is the same on Android (`ResumePoint.kt:20-23`) and the web (`resume-point.js:14-17`). The stats rows were written by the box (`53e1e965`). Nothing else was touched. The user was watching Boston Legal S4 on `e9c95772` the whole time, so Boston Legal was avoided.

## Checks

| Check | Result |
|---|---|
| Focus opens on ▶/❚❚ (R1) | ✅ `w1-open` |
| Row 3 stops at ↺ and at ☰ | ✅ |
| Row 3 on a film: ⏮ ⏭ ☰ are hidden and ⓘ ends the row and stops focus (R3) | ✅ `w7-film-row-end` |
| Up into row 2, row 2 stops at CC, Up again reaches the seek bar | ✅ Up from ☰ lands on CC (far left), not on Fit; harmless |
| CC and ▾ are dimmed with no subtitle tracks, and OK does nothing | ✅ |
| Speed, Audio and Framing menus open on their current value; Back returns to the button | ✅ `w3-*` |
| Sidebar opens on the "Now playing" row; header reads "Season 1"; E4 shows a progress line | ✅ `w4-sidebar` |
| Watched rows are greyed with ✓; a focused watched row gets the full accent border | ✅ `w4-watched-row` |
| Picking E8 in the sidebar switches title, closes the sidebar, and puts focus on ▶ | ✅ `w5-switched` |
| Back order: sidebar (focus to ☰), then stats, then card, then out to the show page on the originating row | ✅ |
| Notes fence (R2) | not checked: these titles have no notes |

## Findings

1. **The Speed menu overlaps the card's seek row** (`w3-speed-menu`). It drops below the card's top edge, so "2×" sits over "0:00" and the seek bar shows through the panel. The menu should end above the card.
2. **The top bar shows through the sidebar header** (`w4-sidebar`). "For kids from 12 · FSK 12" and "Add to list" collide with "‹ Season 1 ›" and ✕. On the web the top bar clears the sidebar; on the TV it does not.
3. **With the sidebar open, the card covers the stats** (`w6-stats-sidebar`). The narrowed card wraps ⓘ ☰ onto a fourth line and grows upward over the "cache" and "reads" lines. Stats sit clear of the card when the sidebar is closed (`w6-back1`).

## Unresolved questions

- R2 (the Notes fence) still needs a title that has notes.
