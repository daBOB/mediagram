# Phase 09 — Web: Who's managing? and the calm view

Status: todo · Depends on: Phase 04 · Spec: `docs/superpowers/specs/2026-11-17-who-is-managing-and-calm-owner-view-design.md`

## Context

- Picker tiles: `web/public/lib/profile-picker.js` (`tiles()`, `initialOf`, `chooseProfile`) — 200 lines, full.
- Picker route page: `web/public/lib/profiles-page-overlay.js`; Settings page `web/public/lib/catalog/settings-profiles.js`.
- Manage: `web/public/lib/profile-manage.js` (200, full), `profile-manage-owner.js`, `profile-manage-rows.js`,
  `profile-manage-households.js`; household rows `catalog/household-rows.js`; gate `catalog/settings-households.js`.
- Words: `household-words.js` + `web/test/fixtures/watch-state/household-rules.json` `words` + `android/core/model/src/main/kotlin/HouseholdWords.kt`.
- Tests: `web/test/profile-manage-owner.test.ts`, `settings-profiles.test.ts`, `settings-households*.test.ts`, `profile-manage*.test.ts`, `profile-picker*.test.ts`.

## Interfaces

- New `web/public/lib/profile-tiles.js`: `profileTiles(people, { onChoose, extra? }): HTMLElement` — the avatar
  tiles *Who's watching?* draws today, moved out of `profile-picker.js` (picker uses it, no behaviour change).
- `mountManage(container, { promptHost, onChange, chosen })` keeps its return shape; *Who are you?* is drawn
  with `profileTiles` under the heading *Who's managing?* (`HOUSEHOLD_WORDS.whosManaging`).
- `settings-households.js`'s gate goes away; the *Owner* tile (only where the owner is not among this
  household's grown-ups) calls the same owner path.

### Task 9.1: Who's managing?

- [ ] **Step 1:** Move the picker's tiles to `profile-tiles.js`; picker tests pass unchanged. Commit
  `refactor(web): the picker's avatar tiles can be drawn elsewhere`.
- [ ] **Step 2: Failing tests:** `the picker's manage route asks who's managing with the picker's tiles`,
  `the owner's tile says Owner and opens the owner view after the owner's PIN`,
  `a grown-up's tile opens their own management after their PIN`,
  `a device showing another household ends the tiles with an Owner tile`,
  `a wrong PIN says so on the tiles and nothing opens`,
  `Settings for a grown-up who is not the owner shows the same tiles and no Everyone here`,
  `the manage route shows no Everyone here, no Households gate and no This device block`.
- [ ] **Step 3: Implement.** **Step 4:** `cd web && bun test && bun run typecheck && bun run lint` → PASS.
- [ ] **Step 5: Commit** — `feat(web): who's managing asks with the picker's tiles`

### Task 9.2: The calm view

- [ ] **Step 1: Failing tests:** `a person is one row with their role line until tapped`,
  `tapping a person opens their controls under the row and closes the one open before`,
  `someone the manager cannot change has no chevron and does not open`,
  `the household line's menu holds the actions the rule allows`,
  `Add someone opens one form that asks grown-up or kid`,
  `Add someone in a household with no grown-up offers only a grown-up`,
  `a kid added from Add someone belongs to the chosen grown-up`,
  `a refused add keeps the open form as typed`,
  `This device is one line at the end`,
  `a household's admin sees the same rows for their household and no menu`.
  Keep the existing owner-view tests passing (adapt only their way in: tap to open, menu, Add someone).
- [ ] **Step 2: Implement** with the frontend design skill (`frontend-design:frontend-design` or `impeccable`) for the look:
  the player's dark-first style, the existing tokens and pills; check light theme and phone width.
- [ ] **Step 3:** `cd web && bun test && bun run typecheck && bun run lint` → PASS.
- [ ] **Step 4:** Stub harness only (`cd web && bun run preview`): screenshots, dark and light, desktop and 390px wide —
  picker → Manage profiles; owner view closed; a person open; Add someone open; the ⋯ menu open; a non-owner admin's view.
- [ ] **Step 5: Commit** — `feat(web): the Profiles page shows people as calm rows that open on a tap`
