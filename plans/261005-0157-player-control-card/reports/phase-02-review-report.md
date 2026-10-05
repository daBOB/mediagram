# Phase 02 review: web episode sidebar

- **Branch:** `worktree-agent-a5b82c92394c64cae`, 6 commits on `5fe377e0`.
- **Reviewed:** 2026-10-05.
- **Checks:** `bun test` 3084 pass, 0 fail; `typecheck` and `lint` clean.
- **Browser probe:** stub preview on :8797 (copies of the real index), gstack browse at 1440×900, 1024, 900, 880–820, 800×600 and 768. The preview was stopped afterwards and `git status` is clean.

## Verdict

- **A, spec compliance:** ✅ for every listed item except one. ❌ "Esc closes the sidebar": after ‹/› reach the first or last season, Esc closes the whole player instead (H1).
- **B, quality:** **changes needed.** One High, three Medium, four Low.

## (A) Spec compliance

| Item | Result | Evidence |
|---|---|---|
| ☰ for a series, anime, course or documentary collection; hidden for a film or a list | ✅ | `app.js:314` passes `collection: queue ? null : collectionOf(...)`. Covered by `browser-application.test.ts` (show vs list) and `player-card.test.ts` (film). |
| Switcher opens on the current season; no arrows with one season | ✅ | `episode-list.js:51`, `episode-sidebar.js:62-66`. Seen in the preview: Dragonball S1E90 opens on Season 1 with the row centred. |
| Row shows number, title, runtime | ✅ | `S1E4 · title · 23m`. The codec line and badges are hidden by CSS. |
| Watched: 45% opacity with ✓, still playable | ✅ | `.is-watched` sets opacity .45, and `watchedTick` draws the ✓. The current row resets opacity to 1. |
| Partly watched: progress line | ✅ | `progressRuleFor`. A watched row keeps its line, as on Android. |
| Current row: "Now playing", not actionable, scrolled into view | ✅ | `disabled`, `aria-current="true"`, `scrollIntoView({block:"center"})`. Preview: scrollTop 3900 on a 101-row season. |
| A pick opens the row the way ⏭ does and closes the sidebar | ✅ | `openInRun(set)` → `onOpenNext(set, {autoplay:"asap"})`, pinned by a test. |
| Closes on ✕, ☰, Esc or a pick; Esc closes a menu first | ⚠️ | Real browser: Esc with Speed open closes only the menu, and a second Esc closes the sidebar and focuses ☰. **But** once focus has fallen to `<body>` (H1), Esc closes the player. |
| The card does not rest while the sidebar is open | ✅ | `holding: menus.isOpen() \|\| upNext.sidebarOpen()`, pinned (mutation M7 is caught). |
| A new title closes the sidebar | ✅ | `open()` calls `close()` first (mutation M8 is caught). |
| ARIA names; focus into the sidebar on open and back to ☰ on Esc | ✅ behaviour, ❌ tests | The names, `aria-expanded`, `aria-controls` and `aside[aria-label]` are correct, and the browser confirms both focus moves. Neither move is tested (M5 and M6 survive, see M2). |
| Review Focus 5: an unknown id, or an episode with no season | ✅ | The model still builds, opens on group 0, and puts the season-less "Episodes" group last. ⏮/⏭ keep the run order. |
| Parity with Android `episodeListOf` | ✅ | **Real-catalogue diff:** web `episodeGroups` against Android's grouping, ported 1:1, over all 121 collections: 0 differences in titles, order or rows. "Episodes" last, course sections named by their last segment, watched rows keep the line. |

## (B) Findings

### High

**H1. Three paths leave focus on a hidden or disabled button.** Focus then falls to `<body>`, outside the modal dialog. This is the same class of bug phase 01 fixed in `player-menus.js:74-76`.

- **The paths:**
  - **✕** (`episode-sidebar.js:88-94`): `close()` hides the panel while ✕ holds focus.
  - **A row pick:** `close()` hides the focused row, and the new title moves focus nowhere.
  - **‹/› stepping to the first or last group** (`episode-sidebar.js:65-66,112-119`): the arrow that was pressed becomes `disabled`, by mouse or by keyboard.
- **Failure, all reproduced in Chrome via the preview:**
  1. After ✕ or a pick, `document.activeElement` is BODY, and `m` no longer toggles mute. Every player key is dead until the viewer clicks the picture.
  2. Open the sidebar on S1E4 and click › then ‹. ‹ becomes disabled and focus is on BODY. Press Esc: **the player closes** (`player.open === false`) with the sidebar still up. Esc goes to the dialog's native close request, because the sidebar's `keydown` listener is on the dialog and no longer hears the key.
- **Fix (minimal):**
  - In `close()`, if `panel.contains(document.activeElement)`, call `button.focus()`. That covers ✕, a pick and Esc, so the Esc handler can drop its own `button.focus()`.
  - In each arrow handler, after `draw()`, if the arrow just pressed is now disabled, call `panel.focus()`.
- **Tests:** the fake DOM's `focus()` already sets `activeElement`. Assert `activeElement === ☰` after ✕ and after a pick, and that focus is not on a disabled arrow after stepping to an end.

### Medium

**M1. With a lesson's Notes open, the open sidebar pushes the card under the Notes column** (`player-card.css:181` overrides `:16`).
- **Cause:** `dialog.sidebar-open .card-dock { right: 384px }` has the same specificity as `dialog.with-notes .card-dock { right: notes-width + 24px }` and comes later, so it wins. Notes is `min(34rem, 38vw)` wide, 544px at 1440, which is wider than the sidebar.
- **Failure (1440×900, Forex Mentor lesson 7, Notes then ☰):** the card spans 100–980 and Notes starts at 896. `elementFromPoint` on ☰ returns `notes-panel`. ☰, part of ⓘ, fullscreen and the end of the volume slider are covered. The top bar is also widened over Notes, so a long title runs under it.
- **Fix:** `dialog.sidebar-open.with-notes .card-dock { right: calc(max(var(--notes-width), 360px) + 24px); }`. The same `max()` applies to `.hud-top`. Alternatively, close Notes when the sidebar opens.

**M2. Between 768 and about 875px the card is narrower than row 3, so ☰ overflows it and lands under the sidebar.**
- **Failure:**
  - At 768 the card spans 24–384 while ☰ sits at 446–490 under the sidebar (sidebar left edge 408). `elementFromPoint` returns `episode-sidebar`.
  - At 800 and 820 it is still covered.
  - From about 840 to 875 ☰ is visible, but it hangs outside the card.
- **Cause:** the narrow-card rules sit under `max-width: 767px` only. The lead's ruling says the card "narrows if it has to (rows wrap, targets never shrink)", and row 3 does not wrap.
- **Fix options:**
  - (a) Let row 3 wrap, or apply the narrow-card rules inside the `sidebar-open` block.
  - (b) Move the clearance breakpoint up to about 900px.
  - The 768px breakpoint is a lead ruling, so (b) needs the lead's word. (a) keeps the ruling as written.

**M3. Two required focus behaviours have no tests.**
- Deleting `panel.focus()` (M6) or `button.focus()` (M5) leaves the whole suite green, yet the spec lists both moves.
- This is the same blind spot that let H1 through.
- Add these assertions alongside the H1 tests.

### Low

**L1. The clearance test only reads the CSS text** (`episode-sidebar-clearance.test.ts`).
- **What it guards:** deleting the rule or moving the breakpoint (M3b fails as it should).
- **What it misses:** any value that still contains "360px". `right: calc(360px - 360px)` passes (M3c survives) and clears nothing. It also cannot see M1 or M2.
- **Verdict:** keep it as a cheap guard against deletion. The real guard is a preview check at 768, 900 and 1440, with Notes open once.

**L2. Keyboard navigation inside the list.**
- ArrowUp/ArrowDown on a row change the volume, because `player.js:825-826` treats a focused button as handling only Space and Enter.
- Focus opens on the panel rather than the playing row, so Tab starts at ‹ › ✕ and then row 1.
- On Dragonball S1, the largest real season at 101 rows, reaching E90 takes about 93 Tabs.
- **Suggestion:** focus the playing row on open, as TV does, or let ↑/↓ move between rows while focus is inside the sidebar.

**L3. Long course section titles wrap to five lines in the header.**
- Example: "Module 7 - Using Horizontal And Diagonal SupportResistance Lines In Combination" pushes the rows down by about 110px.
- **Suggestion:** clamp `.sidebar-title` to two lines with an ellipsis.

**L4. The "unplaced" test is looser than Android's** (`episode-list.js:36-37`).
- **Web:** any season-less group that holds an `ep` goes last, which includes an episode with a `path` or `chap`.
- **Android** (`sectionOf`): such an episode stays in run order.
- **Latency:** this is latent today. The real catalogue has 0 episodes with a path, a chap or no season.
- **Fix:** `set.kind === "ep" && set.season == null && !set.path && !set.chap`.

**Code read only, not verified:** `.up-next` has z-index 3 and sits later in the DOM than the sidebar, so near the end of a title it draws over the sidebar's rows. `with-notes` moves it; `sidebar-open` does not.

## Teeth (one break at a time, each reverted)

| Break | Result |
|---|---|
| M1: a list shows ☰ (`collection: collectionOf(...)` for a queue) | caught by 1 test |
| M2: Esc ignores `defaultPrevented` | caught by 2 tests |
| M3a: no `sidebar-open` class | caught by 1 test |
| M3b: CSS `right: 24px` | caught by 1 test (clearance) |
| M3c: CSS `right: calc(360px - 360px)` | **survives** (text guard) |
| M4: Review Focus 5 reorder removed | caught by 2 tests |
| M5: no `button.focus()` on Esc | **survives** |
| M6: no `panel.focus()` on open | **survives** |
| M7: the HUD ignores the sidebar | caught by 1 test |
| M8: a new title leaves the sidebar open | caught by 5 tests |
| M9, M10, M11: no scroll / no `is-watched` / current row actionable | each caught by 1 test |

## Other checks

- **Listener leaks:** none.
  - Every listener is added once, in `mountEpisodeSidebar`.
  - `draw()` replaces the rows, and the old rows' click closures are dropped with them.
  - Teardown runs `upNext.clear()`, which calls `sidebar.close()`, before `hud.clear()`, so the timer that `show()` starts there is cleared.
- **Render cost:** opening plus the first draw of a 101-row season takes about 3 ms each. The model is built eagerly per title open; this is O(n) and negligible at 780 episodes.
- **Many groups:** a ‹/›-only switcher across 36 seasons, or up to 98 groups in one real collection, is the accepted risk in the plan.
- **Line ceilings:** `episode-sidebar.js` 129, `episode-list.js` 53, `player-card.css` 199 (at the limit), `player.js` 994, `app.js` 694. `code-standards.test.ts` is green.
- **Comments:** none cite plans. No dead code beyond `onClose?` being optional while always passed.

## Plan follow-ups

- Tasks 1–4 and both lead rulings are implemented.
- Fix round: H1 (with tests), M1, and M2, where the lead picks (a) or (b). M3 lands together with the H1 tests. L1–L4 are optional.

## Unresolved questions

1. M2: wrap row 3 at 768–900px, or move the clearance breakpoint above 768? The breakpoint is a lead ruling.
2. M1: when ☰ opens over a lesson with Notes up, should the card clear the wider of the two panels, or should Notes close?

## Re-review (c99d67b5, 6e82ba87)

- **Checks:** `bun test` 3093 pass, 0 fail; typecheck and lint clean. Worktree clean afterwards.
- **H1 focus:** fixed. `close()` hands focus to ☰ when the panel held it (covers ✕, pick, Esc); `draw()` moves focus to the other arrow (panel if both disabled); ☰ hiding moves focus to play/pause. Esc after stepping to an end closes only the sidebar (tested).
- **Notes + sidebar:** fixed. Card and top bar use `max(var(--notes-width), var(--sidebar-width))` (rule is more specific than `with-notes`, so it wins). Pinned by exact-string tests.
- **Breakpoint:** fixed, coherent. Card clears from 900px (measured 890), below that the sidebar covers it as on a phone; top bar clears from 768px so My List and the player's close stay reachable. Not re-measured in a browser.
- **`--sidebar-width`:** fixed. Tests compare the exact value, so `calc(360px - 360px)` fails.
- **List keys:** fixed. Arrows, Enter and Space stop at the panel; Esc and Tab pass. Click activation is unaffected.
- **Title clamp:** applied (2 lines + `title`).
- **`episode-sidebar.css`:** linked after player-card.css and before stats.css; the moved rules were removed from player-card.css; tests read the new file; preview serves `public/` unchanged.
- **Teeth:** focus back to ☰ removed: 3 tests fail. Handoff removed: 1 fails. List keys not stopping: 1 fails. All reverted.
- **New findings:** none blocking. Low: the Esc-at-end test does not focus the arrow first, so it pins the outcome, not the path; the handoff test covers the path.
- **merge-ready:** yes.
