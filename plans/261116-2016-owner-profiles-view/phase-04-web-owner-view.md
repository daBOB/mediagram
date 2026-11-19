# Phase 04 — Web: one owner view (the reference surface)

Status: todo · Depends on: Phase 02

## Context

- Page: `web/public/lib/catalog/settings-profiles.js` (151) → `profilesPanel()` composes You,
  *Everyone here* (`drawEveryone`), Manage (`mountManage`), *This device*, Households (`householdsPart`).
- Manage: `web/public/lib/profile-manage.js` (200, full) — `mountManage`, `session {actorId, pin}`,
  `drawWho`, `drawRole`, `draws.addGrownUp/addKid`; `web/public/lib/profile-manage-households.js` (51)
  — `byHousehold`: owner sees every household, adds only in `mine`.
- Owner's households: `web/public/lib/catalog/settings-households.js` (194) — `householdsPart`
  (Owner gate, held PIN, `act`, `row()`, create form); `catalog/household-forms.js` (74).
- Picker route: `web/public/lib/profiles-page-overlay.js` (`profile: null`).
- Words: `web/public/lib/household-words.js`; Android holds the same words via
  `web/test/fixtures/watch-state/household-rules.json` `words` → any new word goes there too.
- Tests: `web/test/profile-manage-owner.test.ts`, `settings-households.test.ts`,
  `settings-households-pin.test.ts`, `settings-profiles.test.ts`, `profile-manage.test.ts`;
  helpers `test/support/households-part.ts`, `household-fixture.ts`, `browser-application.ts`.

## Interfaces

- `profile-api.js` `create(actorId, pin, fields)`: unchanged; `fields` may carry `household` or `parentId` (doc comment only).
- New `web/public/lib/catalog/household-rows.js` — moved out of `settings-households.js`, no behaviour change:
  - `householdHeader(household, ctx): HTMLElement` (name, `householdLine`, the rule's actions, form slot, message)
  - `createHouseholdBlock(ctx): HTMLElement`
  - `ctx = { everyone, households, may(action, householdId, targetId?), act(said, call, opts), switchProfile }`
- `settings-households.js` keeps only the gate for non-owner pages: note + *Owner* button.
  `householdsPart(promptHost, chosen, { onOwner(pin) })` — after the owner's PIN it calls `onOwner(pin)` instead of opening a households-only part.
- `mountManage(container, { promptHost, onChange, chosen })` now returns
  `{ stop(): void, asOwner(pin: string): void, onOwnerView(listener: (showing: boolean) => void): void }`.
  `settings-profiles.js` is its only caller.
- New `web/public/lib/profile-manage-owner.js` holds the owner view's drawing so `profile-manage.js` stays ≤ 200 lines.

## Behaviour (from the spec)

- `chosen` is the owner → no *Who are you?*; owner view drawn at once with `session = { actorId: owner, pin: null }`.
  The first change asks the owner's PIN (title `HOUSEHOLD_WORDS.ownersPin`), holds it, then sends.
- Picker route → *Who are you?* as today; choosing the owner opens the owner view with that PIN.
- *Owner* button (shown wherever the owner view is not) → `asOwner(pin)`.
- Owner view, per household (device's first, then live order): `householdHeader`, then grown-ups and kids
  with their role line (the text `roleLine` makes today, moved where both can use it), then
  *Add a grown-up* (sends `household: id`) and — only if the household has a grown-up — *Add a kid*
  (sends `parentId`; *Belongs to* `<select>` when >1 grown-up, default the household's admin, else its first grown-up).
  Then `createHouseholdBlock`. *Everyone here* and the gate are hidden while it shows.
- A wrong PIN clears the held PIN; the next change asks again. Leaving the page drops it.
- Non-owners: unchanged.

### Task 4.1: Move household rows out (pure refactor)

- [ ] **Step 1:** Create `household-rows.js` from `settings-households.js`'s `row()` and create form; `householdsPart` uses it.
- [ ] **Step 2:** `cd web && bun test test/settings-households*.test.ts test/profile-manage-owner.test.ts` → PASS unchanged.
- [ ] **Step 3: Commit** — `refactor(web): household rows can be drawn outside the owner's part`

### Task 4.2: Owner view

- [ ] **Step 1: Failing tests** in `profile-manage-owner.test.ts`:
  - `watching as the owner, the page shows every household with its actions and no Who are you`
  - `the owner's first change asks the PIN once, and the next change does not ask again`
  - `Add a grown-up in another household sends that household` → body has `household: "flat"`
  - `Add a kid in another household belongs to its admin` → body `parentId` = admin id
  - `a household with two grown-ups asks who a new kid belongs to`
  - `an empty household offers Add a grown-up and Delete but not Add a kid`
  - `Everyone here and the Owner button are gone while the owner view shows`
  - `from the picker, choosing the owner opens the owner view`
  - `on a device in another household, the Owner button opens the whole owner view`
  - `a wrong PIN is asked again at the next change, and nothing is sent`
  - `a taken name is said beside the form it was typed in`
  - `leaving the page and coming back asks the PIN again`
  - `a household added or removed elsewhere redraws the view and keeps the PIN`
- [ ] **Step 2:** `cd web && bun test test/profile-manage-owner.test.ts` → new tests FAIL.
- [ ] **Step 3: Implement** per Interfaces/Behaviour. Update older tests that pinned the two-halves page
  (`settings-households*.test.ts`, `settings-profiles.test.ts`) to the new page, keeping their non-owner cases as they are.
- [ ] **Step 4:** `cd web && bun test && bun run typecheck && bun run lint` → PASS.
- [ ] **Step 5:** Look at it in the stub harness (`cd web && bun run preview`, never the real player):
  as owner from Settings, from the picker, and on a device joined to another household. Screenshot each.
- [ ] **Step 6: Commit** — `feat(web): the owner manages every household from one view`

## Success criteria

The four-screenshot path from 2026-11-16 becomes: Profiles → Madani → *Add a grown-up* → name →
owner's PIN (first change only) → the new PIN twice.
