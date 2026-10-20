# Phase 04 — Web player: this device's household, Join a household, Switch, the owner's part

**Spec:** controller calls 6, 7, 11, 13, 15. **Status:** pending. **Depends on:** phase 02 (routes, `GET /api/profiles` households).
**Pattern to copy:** `web/public/lib/profile-manage.js` (a PIN held per visit, dropped on teardown), `profile-add-form.js` (an inline form), `catalog/settings-profiles.js` (the one Profiles page from `feat/one-profiles-page`), `pin-prompt.js` (`askPin`, `refusalText`).
**The web is the reference** (`CLAUDE.md` § Surface Parity): Android copies the look and words settled here, so take screenshots.
**Line limits:** `watch-state.js` ceiling 485 (one line of room at planning time), `profile-picker.js` 197 (no ceiling; stays ≤200), new files ≤200.
**UI checks:** the preview harness only (`cd web && bun run preview` → a copy of `state.db`, no Telegram), driven by gstack `/browse`; never the real player. The copy migrates to v16 on open.

### Task 4.1: This device's household — the filter, Join a household, Switch household

**Files:**
- Create: `web/public/lib/household.js`. It keeps the device's household in `localStorage` key `mediagram.household`, wrapped as `watch-state.js` wraps `mediagram.profile`. Exports:
  - `here(everyone, households, chosenId, heard)`: computes `deviceHousehold(stored, chosen profile's household, live ids, heard)`, stores the answer when non-null and changed, remembers it, and returns `everyone` filtered to it (`[]` when `null`);
  - `thisHousehold()` → `{id, name, adminId} | null`;
  - `needsJoin()` → `thisHousehold() === null && heard`;
  - `joined(id)`: stores `id`.
- Modify: `web/public/lib/watch-state.js` `loadProfiles`. Hold `everyone` and `households` as said, and `profiles = here(everyone, households, remembered(), heard)`. Export `everyone()` and `households()`. `profiles()`, `profile()` and `rememberedProfile()` keep reading the filtered list, so the picker, Everyone here, Manage and the index count follow by themselves. Raise the ceiling by the growth, with a dated sentence.
- Modify: `web/public/lib/profile-api.js`: export its `call` and `change` for reuse; `REASONS` unchanged.
- Create: `web/public/lib/household-api.js`:
  - `join(id, pin)`: `POST /api/households/{id}/join`; on success `joined(id)`, `loadProfiles()`, and `useProfile(null)` when the chosen profile is not in the new household;
  - `create`, `rename`, `setAdmin`, `move`, `remove`: the Task 2.4 routes, through `change`.
- Create: `web/public/lib/household-join.js`: `joinChoices(host, {except, onJoined})`. It draws:
  - an `h2` with `HOUSEHOLD_WORDS.joinAHousehold`, then `joinNote`;
  - one `pill pill-line` button per live household except `except`;
  - choosing one → `askPin(host, joinPinTitle(adminName))` → `join(id, pin)`; refusals through `refusalText`; done → `onJoined()`.
- Modify: `web/public/lib/profile-picker.js` `draw()`: `if (needsJoin()) { choices.append(joinChoices(screen, {onJoined: draw})); return; }` before the grown-ups logic. The claim question ("Who runs this household?") stays as is: it now asks per household (Task 2.3).
- Modify: `web/public/lib/catalog/settings-profiles.js`: the "This device" row below Manage, for anyone but a kid, and only while more than one household is live:
  - `setting-title` `thisDevice`;
  - `setting-value` the household's name;
  - a `Switch household` pill opening `joinChoices` with `except` the current one.
- Modify: `web/public/lib/pin-prompt.js`: `"name-taken"` reads `HOUSEHOLD_WORDS.profileNameTaken` (spec call 11).

- [ ] **Step 1: Failing tests.**
  - Create `web/test/household.test.ts` (as the browser tests do it, after `profile-picker.test.ts`):
    - `"a device that chose someone before joins that profile's household and keeps it"`;
    - `"a new device joins the only household once the household is heard"`;
    - `"with two households a new device asks to join one"`;
    - `"the picker lists only this device's household"`;
    - `"a profile remembered from another household is nobody here"`.
  - Create `web/test/household-join.test.ts`:
    - `"Join a household lists the households, then asks the admin's PIN"`;
    - `"the owner's PIN joins too"` (the route answers 204);
    - `"a wrong PIN says so and stays"`;
    - `"joining shows that household's profiles"`;
    - `"switching away from the chosen profile's household lets go of it"`.
  - In `web/test/settings-profiles.test.ts`:
    - `"the Profiles page says this device's household and offers Switch only with two"`;
    - `"a kid sees no household row"`.
  - In `web/test/pin-prompt.test.ts` (or the file holding refusal-text tests): `"a taken name says it is used on this install"`.
- [ ] **Step 2: Run** `cd web && bun test test/household*.test.ts test/settings-profiles.test.ts` → FAIL.
- [ ] **Step 3: Implement.** Words come only from `household-words.js`, never retyped.
- [ ] **Step 4: Run** `bun test`, `bunx tsc --noEmit -p .`, `bunx eslint .`, the code-standards test → PASS.
- [ ] **Step 5: Preview walk.** The owner's real PIN is never asked for or used.
  - Copy `~/.local/share/mediagram-player/state.db` to the scratchpad.
  - In that copy only, give the owner (the claim holder) the PIN `1357`: a fresh salt, and `hashPin(salt, "1357")` from `web/src/state/profiles-pin.ts`, written with `sqlite3`.
  - Start `MEDIAGRAM_STATE_DB=<copy> bun run preview`.
  - Make a second household as the owner through `curl` against the preview's routes.
  - Then, in a fresh browser profile (empty `localStorage`):
  - screenshot "Join a household", its PIN prompt, and the picker after joining;
  - screenshot the Profiles page's "This device" row and Switch;
  - all at desktop and 390 px, into `plans/261020-0310-households/reports/` (kept out of git as the one-page branch does).
- [ ] **Step 6: Commit** `feat(web): a device joins one household and sees only its profiles`.

### Task 4.2: The owner's Households part on the Profiles page

**Files:**
- Create: `web/public/lib/catalog/settings-households.js`: `householdsPart(promptHost, chosen)` → `{ box, stop }`, mounted by `settings-profiles.js` under the "This device" row, never for a kid. What it shows:
  - **heading** `households`. When the chosen profile is not the owner, or nobody is chosen: `ownerNote` and an `Owner` pill. The pill is shown only when an owner exists (`everyone().some(p => p.owner)`). It asks `ownersPin` (`askPin` → `unlock(ownerId, pin)`), holds the PIN for the visit, then opens the part. With the owner chosen, the part shows at once, and the PIN is asked at the first action, then held.
  - **one row per live household**:
    - its name, `householdLine(admin name, members)`, members counted from `everyone()`;
    - `rename`;
    - `changeAdmin`: a choice of that household's grown-ups; a new PIN typed twice when the choice has none. Not offered for the household the owner is in;
    - `moveHere`: a choice of the grown-ups of other households that `move-to-household` allows (those who run nothing, or are their household's last grown-up);
    - `useHere` (absent on this device's own): `join(id, heldPin)`;
    - `delete`: only when it is empty and not `home`.
    - What each row offers follows `householdAllowed` over `everyone()`, imported from `household-rules.js`; the server decides all the same.
  - **at the end** `createAHousehold`: an inline form with `householdName`, then `admin`. The admin is `aNewGrownUp` (name + PIN typed twice) or a grown-up who runs nothing (+ a PIN when it has none).
  - **refusals**: `"name-taken"` on a household form reads `householdNameTaken`; on a new grown-up's name, `profileNameTaken`; anything else goes through `refusalText`.
  - `stop` drops the held PIN and closes open prompts, as `mountManage`'s does. `profilesPanel`'s `stop` calls it.
- Create: `web/public/lib/catalog/household-forms.js` if the part would pass 200 lines (the create / rename / admin forms).
- Modify: `web/public/lib/catalog/settings-profiles.js`:
  - mount the part;
  - `roleLine` prefixes `Owner · ` for the owner (`HOUSEHOLD_WORDS.owner`), e.g. "Owner · Admin · PIN set".
- Style: in `appearance.css` beside the in-page manage faces only if the settings faces don't already cover it. No new colours.

- [ ] **Step 1: Failing tests.** Create `web/test/settings-households.test.ts`:
  - `"the owner sees every household with its admin and member count"`;
  - `"anyone else sees an Owner button that asks the owner's PIN once"`;
  - `"no Owner button on an install with no owner"`;
  - `"create with a new grown-up sends the name, the admin's name and PIN"`;
  - `"create with an existing grown-up asks a PIN only when it has none"`;
  - `"rename, change admin, move and delete send the held PIN"`;
  - `"Change admin is not offered for the owner's own household, Delete only for an empty one"`;
  - `"Use on this device switches the device with the owner's PIN"`;
  - `"a taken household name says so in the household's own words"`;
  - `"leaving the page drops the owner's PIN"`.
  
  In `settings-profiles.test.ts`: `"the owner's line reads Owner · Admin"`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** `bun test`, `tsc`, `eslint`, the code-standards test → PASS.
- [ ] **Step 5: Preview walk:**
  - as the owner, create household "Oma" with a new grown-up, rename it, move a test grown-up there, change its admin, delete an emptied one;
  - screenshot the part (desktop + 390 px) and the Owner prompt as a non-owner, into `reports/`;
  - view them before reporting.
- [ ] **Step 6: Commit** `feat(web): the owner sets up households from the Profiles page`.
