# Phase 01 — Web: Profiles tab in Settings

**Plan decisions:** 1–4. **Status:** pending.

**Context:**
- `web/public/lib/profile-manage.js` (195 lines): `openManage(parent, onClose)` builds an overlay (`div.who.who-manage` → `div.who-card`) holding a body, a message line and Done, and draws "Who are you?" (grown-ups as pills; `askGrownUp` asks the PIN) then the role view (`drawRole`: Grown-ups for the admin, Kids, Your PIN). PIN prompts are appended to the overlay (`askPin(screen, …)`, `askGrownUp(screen, …)` from `pin-prompt.js`), and Escape closes the panel only when no prompt lies over it.
- `web/public/lib/catalog/settings-page.js`: `renderSettings(main, { profile, switchProfile, systemVisible, admin })` mounts `tabbed([...])` with Appearance, Profile and, for the admin-gated case, "Library & Telegram"; it returns a teardown.
- `web/public/lib/profile-api.js`: `manageable(profiles, actorId)` → `{ actor, grownUps, kids }`; `ownerOf`.
- `web/public/lib/watch-state.js`: `profiles()`, `profile()`, `loadProfiles()`.
- Tests: `web/test/profile-manage.test.ts` (harness: `browserEnvironment`, `Node`, `answerPin`, household fixture ANDRE/MAJA/TIM/LEA), `web/test/settings-page.test.ts`.

### Task 1.1: One manage body for the overlay and the page

**Files:**
- Modify: `web/public/lib/profile-manage.js` — lift the drawing (who-are-you, role view, PIN prompts, refusal reporting, held `{ actorId, pin }`) into an exported `mountManage(container, { promptHost, onSignedOut? })` (name is the implementer's call) that returns a teardown which drops the held PIN. `openManage` keeps its overlay, Done and Escape, and mounts the shared body inside its card. If the file passes 200 lines, move the overlay into its own small file rather than grow this one.
- Test: `web/test/profile-manage.test.ts` stays green unchanged; add `theBodyMountsOutsideTheOverlay` (mounting into a plain container draws "Who are you?" with the grown-ups, and a PIN prompt opens over `promptHost`).

- [ ] **Step 1:** failing test. **Step 2:** run → FAIL. **Step 3:** implement. **Step 4:** `cd web && bun test profile-manage settings-page` → PASS; `bun run lint`; `bun x tsc --noEmit -p .`.
- [ ] **Step 5: Commit** `refactor(web): Manage profiles draws into any container`.

### Task 1.2: The Profiles tab

**Files:**
- Modify: `web/public/lib/catalog/settings-page.js` — a **Profiles** tab after Profile, only when the chosen profile is a grown-up (`profile && !profile.kids`). Its panel has:
  - **Everyone here** (decision 3): one row per profile from `state.profiles()`, grown-ups first then kids, each with its name and one quiet line: `Admin`, `Grown-up`, or `Kid · <parent name> · FSK <n>` plus ` · <daily limit words>` when one is set (reuse `DAILY_LIMIT_WORDS` / `daily-limit-choice.js` wording, never retyped); then `PIN set` / `No PIN` for a grown-up.
  - **Manage** below it: the shared body from Task 1.1, prompts over the page. After any change, the Everyone list redraws from `state.loadProfiles()`.
  - Leaving the tab or the page tears the body down and drops the held PIN (the settings page's teardown and the tabs' switch both reach it).
- Create (if the panel passes ~80 lines): `web/public/lib/catalog/settings-profiles.js`.
- Styles: reuse the settings panel's own faces (`.settings-panel`, `.setting`, `.setting-title`, `.setting-value`) and the manage rows' classes; no new colours.
- Test: `web/test/settings-profiles.test.ts`:
  - `aGrownUpSeesAProfilesTab`; `aKidAndNobodySeeNoProfilesTab`;
  - `everyoneIsListedWithTheirRole` (fixture: andre admin, Maja grown-up, Tim and Lea kids with parents and limits);
  - `managingAsksWhoYouAreAndThePin`, then a kid added appears in Everyone;
  - `leavingTheTabDropsThePin`: switch tab and back → "Who are you?" again, no request carries the old PIN.

- [ ] **Step 1:** failing tests. **Step 2:** run → FAIL. **Step 3:** implement. **Step 4:** focused tests, then full `bun test`, lint, tsc → PASS.
- [ ] **Step 5:** preview walk (`cd web && bun run preview`, gstack browse): Settings → Profiles as a grown-up at desktop and 390 px; screenshot both into the plan's `reports/`.
- [ ] **Step 6: Commit** `feat(web): a Profiles tab in Settings to manage the household`.
