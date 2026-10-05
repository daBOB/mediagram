# Phase 01 review: web control card

2026-10-05. Branch `worktree-agent-aad087f4ede7b98bb`, `main...` = eb8f6a33..2188c80d (12 commits, 40 files, +1465/−592). Read-only review; temporary breaks reverted, `git status` clean afterwards.

## Verdicts

- **A (spec compliance): ✅ 12 of 13, with one ⚠.** The stats overlay sits "under the top bar" only while the top bar fits on one row. At phone width it covers ✕ (M2).
- **B (quality): changes needed.** Two Medium findings, both small fixes. Nothing Critical or High.

## Checks run

- `bun test` → 3056 pass / 0 fail (217 files). `bun run typecheck` and `bun run lint` (max-warnings 0) are clean.
- Ceilings: `player.js` 994/994, `transport.js` 350/350, `playback.css` 625/625, `app.js` 694/694. New files are 60–147 lines.
- No added comment cites a plan, phase, task or review code (grep of the added lines).
- Stub preview on :8797 with headless Chromium, probed through `gstack browse`. The live player on :8770 was not touched. The preview was stopped afterwards.

## A. Spec compliance

| Item | Result | Evidence |
|---|---|---|
| Top bar: title, My List, Kids, Add to, Notes, ✕ | ✅ | `index.html` hud-top. In the preview, with no profile, an unrated lesson that has notes shows My List, the "Not for kids" age select, Add to…, Notes and ✕. The 1440 screenshot lacks Kids and Notes only because of its state: the "TV kids" profile hides Kids/age (`player-library-marks.js:35-36`), and Boba Fett has no notes. |
| Card rows 1/2/3 | ✅ | Row 1: at-now, seek, at-end, ends. Row 2: CC ▾, speed, audio (hidden below 2 tracks, `player.js:769`), framing, volume, fullscreen. Row 3: ↺ ⏮ −15 ▶ +15 ⏭, then ⓘ. |
| Card look | ✅ | `player-card.css:14-27`: `color-mix(var(--stage) 45%)`, blur(24px) saturate(1.2), 8% border, `--radius-card`, no shadow, `min(880px, 100%-48px)`, bottom 24px. |
| Blur exception documented | ✅ | `shell.css:83-84` names both places. The `DESIGN.md` Player section has been added. |
| 15 s | ✅ | `SKIP_SECONDS` = 15, `SKIP` = 15. The aria labels "Back/Forward 15 seconds" are generated in `transport.js:92-95`. |
| Restart keeps play/pause | ✅ | `onSeekTo(0)`. A direct file only seeks. A conversion re-converts with `autoplay` taken from `!video.paused` (`player.js:212`). |
| Previous | ✅ | Never restarts. It is disabled when `previous` is null and hidden when `!inRun` (`player-next-title.js:55-61`). The run comes from `playsNext`, which handles queues and collections. |
| CC | ✅ | It toggles back to the last shown or chosen track for the title, and otherwise uses `toggleOn` (preferred, then audio language, then first). It is disabled with no regular track. With a forced-only track, ▾ stays enabled for Style…. |
| Menus | ✅ | One open at a time (`open()` closes first). A choice closes the menu. Esc calls `preventDefault` and closes only the menu. See L2 for fullscreen. |
| Stats rows; unknown omitted | ✅ | video, audio, buffer, cache, dropped (dropped only when > 0). Empty rows are filtered out (`player-stats.js:41-42`). |
| Hiding held | ✅ | The `holding()` check runs again when the timer fires, and a pointer on the card holds it. I confirmed both in the real browser: the card did not rest with a menu open, did not rest with the pointer on the card, and did rest 2.6 s after the pointer left. |
| Accessible names | ✅ | All twelve names for this phase are exact ("Episodes" belongs to phase 02). |
| Retired | ✅ | `.hud-bottom`, `.rail`, `.bar`, the three selects, `#tech`, `#preload`, `#cue-settings`, `fillChooser` and `technicalLine`/`partsLabel` are gone. A grep finds no leftover CSS or JS. |
| Stats "under the top bar" | ⚠ | See M2. |

## B. Findings

### Medium

**M1. Choosing a menu value drops focus to `<body>`, and the player's keys stop working.** `web/public/lib/playback/player-menus.js:73-76`
- The row that has focus is hidden by `close()`, and nothing receives the focus. The player's key handler listens on the dialog (`player.js:816`), so it never sees keys pressed on `<body>`.
- Preview probe:
  1. Tab to Speed and press Enter. Focus lands on the "1×" row.
  2. Press Tab, then Enter. The speed becomes 1.25× and `activeElement` is BODY.
  3. Press `k` and `z`. Nothing happens; framing stays "Fit".
- The same thing happens after a mouse choice.
- On `main`, the `<select>` kept focus inside the dialog. The Esc path here already returns focus to the opener (`:97`).
- Fix:
  ```js
  row.addEventListener("click", () => { close()?.focus(); pick(item.value); });
  ```
  Style… still works after this, because `panel()` then focuses the style panel.

**M2. At phone width the top bar crushes the title, and the stats overlay covers ✕.**
- Where: `player-card.css:119` (`top: 4.5rem`), `player-card.css:142` (`flex-wrap`) and `playback.css:72-74` (`.what { flex: 1; min-width: 0 }`).
- Preview at 390×844, with no profile and an unrated lesson that has notes:
  - `#now` is 6 px wide; the title is unreadable.
  - ✕ wraps to a second row at y 68–112.
  - With ⓘ on, the overlay spans y 72–182 at z-index 2. `elementFromPoint` at the centre of ✕ returns `#stats-panel`, and a click on `#close` times out.
- On `main` the top bar held only the title and Close, so this is new.
- Fix:
  - At ≤767px, give `.hud .what` `flex-basis: 100%`, so the title gets its own row.
  - Place the stats overlay from the top bar's real height, not a fixed 4.5rem. One option is to lay it out after `.hud-top` in a shared column. Another is to set a CSS variable from the top bar's `offsetHeight` on open and on resize.

### Low

- **L1. With Notes open, the card loses its 24 px inset.**
  - Where: `player-card.css:6-12`. `width: min(880px, calc(100% - 48px))` resolves against the dialog, while `.hud` is `right: var(--notes-width)`.
  - At 1440 with notes open, the card sits 8 px from each edge of the stage and touches the notes column (screenshot taken in the preview).
  - Fix: `left: 24px; right: 24px; width: auto; max-width: 880px; margin-inline: auto`, plus `dialog.with-notes .card-dock { right: calc(var(--notes-width) + 24px) }`.
- **L2. Esc in fullscreen.**
  - In real Chrome the browser takes Esc to leave fullscreen. The menu stays open; a second Esc closes it, and a third closes the player. This was accepted in the phase risks.
  - The page logic is correct. A CDP-dispatched Esc in fullscreen closed only the menu: fullscreen stayed on and the dialog stayed open.
  - Related: the Esc listener is on the dialog. If a menu were open while focus sat on `<body>`, Esc would close the player instead. In the probe, a forced blur produced exactly that. Normal use reaches this state only through M1.
- **L3. `player-hud.js` `clear()` does not reset `over`.**
  - If a pointerleave is missed when the dialog closes under a pointer resting on the card, the next title never rests until the pointer crosses the card.
  - This was not reproduced. Resetting `over` in `clear()` is a one-line hardening.
- **L4. Hygiene.**
  - The `shell.css:1-2` header still says `.rail` "is the player's own control row".
  - `nextAfter` (`library.js:243`, `library.d.ts:141`) no longer has a production caller, because `plays-next` now uses `nextInQueue(flattenCollection(...))`.
  - The old `#tech` facts for container, size and "N parts" are no longer shown anywhere in the player. The spec allows this; it is noted here so the loss is on record.

### Pre-existing (not this phase; worth an issue)

- **Clicking the picture keeps the card up for good while playing.**
  - In Chrome, clicking the picture focuses the `<dialog>`, and `dialog.contains(dialog)` is true. The focus rule at `player-hud.js:29` therefore holds the card up indefinitely while playing.
  - Preview with `paused` patched to false: with focus on DIALOG the card never rested; with focus on BODY it rested after 2.6 s.
  - The rule is unchanged from `main`. Adding `focused !== dialog` would fix it.
- **+15 near the end of a conversion converts from exactly `runtime`.** The clamp in `seek-model.js` is the same as on `main`, and no test covers the conversion case.

## Teeth check (tests for tasks 1, 3 and 6–11 were never seen red)

For each line below I made a temporary break, ran the named tests, and reverted the break. 14 of the 17 breaks were caught.

| Break | Caught by |
|---|---|
| RF1: skipTo clamp removed | `player-card` "back 15 near the start…" |
| RF2: Esc `preventDefault` removed / Esc does not close | `player-menus` "Esc closes the menu…" (+ the panel test) |
| RF3: timer ignores `holding()` | `player-features` "an open menu or a pointer…" + `player-card` "the card stays up…" |
| RF3: pointer-over ignored | `player-features` "an open menu or a pointer…" |
| RF4: on ignores the audio language / ignores last | `subtitle-picker` "on, with nothing remembered…" / 3 tests |
| CC never disabled | `subtitle-picker` forced-only + "no subtitles at all" |
| Restart plays / restart pauses | `player-card` "Restart … leaves playing or paused" |
| Previous never disabled / shown with no run | `player-card` run test / "a film has no run" |
| `previousInQueue` off by one | `plays-next` (5 tests) |
| Stats keeps empty rows | `player-stats` (3 tests) |
| Skip back to 10 on buttons / keys | `browser-html-player` / `player-keys` (2) |
| A choice keeps the menu open; two menus open at once | `player-menus` + `player-card` / `player-menus` (2) |

The 3 breaks that survived are all redundant guards; the behaviour they protect still held:

- the `holding()` check in `show()`: the timer callback checks again;
- `if (cc.disabled) return`: `toggleOn` returns null when there is no regular track;
- ⏮ falling back to restart on the first title: the button is disabled there, and that is pinned. The fake DOM ignores `disabled`, and no test clicks the button.

No test lacks teeth on behaviour that could actually break.

## Listener and lifetime review

- Everything is mounted once in `mountPlayer`: the menus, HUD, stats, framing resize, next-title and audio list. Opening a title adds no listeners.
- Menu rows are rebuilt on each open and dropped with their nodes.
- `menus.close()` runs on a title switch (`player.js:636`) and on dialog close. On close, `hud.clear()` runs first, so `onClose → show()` does not start a timer.
- The stats overlay stays toggled across titles and across close/reopen. It is redrawn in `openPlayer` (`refreshPreload`), so no stale rows persist.
- No keyboard trap: the menu precedes the card in DOM order, and the style panel follows it.

## Plan follow-ups (for the lead)

- All of phase 01's tasks appear complete.
- Fix M1 and M2 before merge. L1–L4 are optional.
- File the pre-existing focus-hold item as an issue.

## Unresolved questions

- M2 fix shape: title on its own row at phone width, or move the marks into a "⋯" overflow? The first is the smallest change; the second changes the user-decided top bar, so it needs the user's word.

## Re-review (fix round 1, `2188c80d..21698654`)

Checks: `bun test` 3060 pass / 0 fail, typecheck and lint clean. Worktree `git status` clean after the breaks.

| Item | Verdict | Evidence |
|---|---|---|
| M1 | fixed | `player-menus.js:76` `close()?.focus()` before `pick`. Test checks activeElement is the opener and `k` plays. |
| M2 | fixed (CSS read, not re-probed in a browser) | `player-card.css` at <=767px: `.what` flex-basis 100%, `#close` absolute top/right (`.hud` is absolute, so it anchors to the bar), bar padding-right 72px. `player-stats.js` `place()` sets `top` from the bar's measured height on open, draw and resize. The resize listener is added once in `mountPlayerStats` (like every other mount-once listener) and is a no-op while the panel is hidden, so there is no per-title or per-open leak and nothing to remove on close. |
| M3 | fixed | `player-hud.js:31` excludes `focused === dialog`; focus on any inner element still holds (existing hold tests pass). |
| L1 | fixed | `.card-dock` left/right 24px; with-notes `right: notes-width + 24px`, guarded to >=768px. |
| L3 | fixed | `clear()` resets `over`. |
| L4 | fixed | `nextAfter` gone from js/d.ts/comments; grep finds no callers; its tests now run over `nextInQueue(flattenCollection(...))` and keep the season/folder/workbook cases. |

Teeth: M1 break (drop `?.focus()`) -> 1 fail; M3 break (drop `!== dialog`) -> 1 fail; stats break (no `top` set) -> 1 fail; L3 break -> 2 fail. All reverted.

New findings: none blocking. Low: the stats test drives `clientHeight` through a fake `getBoundingClientRect`, so the real wrap behaviour at 390 px is covered only by the worktree's own preview screenshot.
