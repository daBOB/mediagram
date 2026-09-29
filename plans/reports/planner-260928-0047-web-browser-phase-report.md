# Planner report — phase 03, web browser (picker, PIN prompt, manage panel, filter, marks)

Phase file: `plans/260928-0047-profile-roles-pins-kids-age-limits/phase-03-web-browser-picker-manage-filter.md`
Written against the contract as amended 2026-09-28 (§3 order, §8 `create-first`, §12 picker states).

## Plan summary

Six commits, TDD, each ending with a patch bump per phase-08 § Bumping:

1. **Per-kid filter and marks with an age.** Touches `age-rating.js` (`kidsLimitOf`, `kidsVerdict(set, limit)`, `forKidsProfile(sets, Map, limit)`; `KIDS_AGE_LIMIT` goes), the `watch-state.js` kids section (`Map`, `fromSix`, `kidsAge`, `kidsMarks`, `setKids(id, 6|12|null)`), `library-session.js` and its `.d.ts`, and the player's Kids control, which becomes a native `<select id="kids-age">` on unrated titles.
2. **The kid's own limit in words.** The empty shelf says "Nothing rated FSK N or under yet.", Settings shows "Kids · FSK N", and the kids flag is replaced in `app.js` and `department-pages.js`.
3. **`profile-api.js`.** Covers every §8 route including `createFirst`. Each call returns an outcome instead of throwing. A successful change re-reads the profile list and lets go of this device's profile if it is gone. It also holds the pure `ownerOf` and `manageable` view, which is tested against the shared `profile-rules.json`.
4. **`pin-prompt.js`.** One `<dialog>` with a masked field (4 digits, numeric, autocomplete off), an ask-twice mode, and the server's refusal text including wait seconds.
5. **`profile-manage.js`.** The panel runs: pick yourself, give the PIN once (held in a closure), then see what your role allows. A wrong PIN drops the held PIN. Changing your own PIN updates the held one.
6. **Picker rewrite.** Covers the three §12 start states. A kid opens at once; a grown-up asks for a PIN, or sets one twice. New profile and `window.prompt` go, the note is honest, and "Stay as I am" is hidden once this device's profile is gone. The three profile writers leave `watch-state.js`, and `switchProfile` redraws when the current profile's content changed.

Task 7 checks everything by eye on `bun run preview` using a copy of the household's state and an empty state. It produces 9 screenshots.

Verified baseline on 2026-09-28: the 7 affected test files pass (73 tests), and typecheck and lint are clean.

## File-size decisions (ratchet)

| File | Now → after | How |
|---|---|---|
| `watch-state.js` (ceiling 502) | 502 → 502 → ~478 | Task 1 rewrites the kids section in exactly 30 lines. Task 6 deletes create/rename/delete (−24) and lowers the ceiling. |
| `app.js` (742) | 742 → 741 | +1 import, −2 (`kidsProfile` and a blank line). `switchProfile` changes but stays net 0. Ceiling lowered. |
| `library-session.js` (200) | 199 | Filter from 4 lines to 3. |
| `shelf-view.js` (285) | 285 | `emptyState` edited in place (`{ kids }` → `{ kidsLimit }`). |
| `playback.css` (752) | untouched | `.ghost` and `.hud select` already style the new select. |
| New files | 94 / 116 / 169 / 174 lines | `profile-api` / `pin-prompt` / `profile-manage` / `profile-picker`, all measured from the plan's code. |

Found while checking this: the web TS env has no DOM lib (`HTMLElement` is TS2304). Tests must pass the fake `Node` without casts, or typecheck fails.

## Contract questions

The update settled three questions I had: the bootstrap (`create-first`), a kid's unlock not waiting, and a malformed current PIN answering `wrong-pin`. These remain:

1. **§12 row 1 with only kids.** Does "Create the first profile" replace the kid tiles, or sit above them? The phase keeps the kid tiles (spec: a kid opens freely) and hides "Manage profiles", because with no grown-up nobody could manage.
2. **Where does `allowed()` live?** Phase 02 owns it under `web/src/state/`, which the browser cannot import. The panel keeps its own `manageable()` view, pinned to the server by a test over `profile-rules.json`. If phase 02 wrote `allowed()` as `web/public/lib/profile-rules.js` instead, both sides would share one copy. There is precedent: `src/transcode/video-copy.ts:21` imports `public/lib/playable.js`.
3. **After `create-first` or `claim-admin`: enter, or redraw?** §12 does not say. The phase redraws, and the viewer then taps their tile and gives the PIN again.

## Questions for the lead or user (not contract)

- **A limit changed on another device** reaches an open web tab only on reload or the next picker open. The `state` event re-reads positions, not profiles. Fixing it needs a line `app.js` does not have, or a `library-session` change. The phase accepts this and lists it as a follow-up.
- **A new kid starts at FSK 6** in the panel (the stricter limit; the parent raises it). Nobody decided this; confirm or change.
- **Refusal wording** is one line for every `not-allowed`: "That is not allowed." It is used both in manage and for a first profile that lost a race.
- **Transition risk.** Until andre claims admin, anyone at any screen can claim it or set a PIN for an unprotected grown-up, and nobody can reset the admin's own PIN. Claim admin on the web player right after the release.

**Status:** DONE_WITH_CONCERNS
**Summary:** The phase-03 plan is written against the amended contract (§3/§8/§12): six TDD commits plus a preview walkthrough, with every ratchet file held at or below its ceiling. The concerns are the three contract questions above and the accepted gap that a limit changed on another device does not reach an open tab.
