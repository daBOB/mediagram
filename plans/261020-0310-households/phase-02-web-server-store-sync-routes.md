# Phase 02 — Web server: store, sync, management, routes

**Spec:** controller calls 1–5, 8, 11–14. **Status:** pending. **Depends on:** phase 01. **Runs beside** phase 03 (disjoint files: `web/src`, `web/test` here; `crates/`, `android/core/rust` there).
**Pattern to copy:** the hidden-sections commits on `feat/profiles-page` (`18f6670c` and its review fixes): `roles-schema.ts` group, `profiles.ts` row, `roles-record/merge/exchange.ts`, `profiles-access.ts` subclass, `profiles-routes.ts`.

**Line limits that bind here** (counts at planning time; re-measure): `profiles.ts` 196 (no ceiling: stays ≤200 by splitting), `profiles-manage.ts` ceiling 212, `store.ts` ceiling 729 (at it), `sync-record.ts` ceiling 293 (at it), `state/routes.ts` ceiling 209 (at it). A ceiling is raised by exactly the growth, with a dated sentence in `web/test/code-standards.test.ts`. One that shrinks is lowered the same way.

### Task 2.1: Store — the households table, Home, and every profile's household

**Files:**
- Modify: `web/src/state/roles-schema.ts`: `HOUSEHOLDS_GROUP`, appended to `ROLES_GROUPS`. It is the next version after hidden sections (v16 at planning time). Statements:
  - `CREATE TABLE IF NOT EXISTS households(id TEXT PRIMARY KEY, name TEXT NOT NULL, name_updated_at INTEGER NOT NULL DEFAULT 0, admin_id TEXT, admin_updated_at INTEGER NOT NULL DEFAULT 0, removed INTEGER NOT NULL DEFAULT 0)`. `admin_id` is not a foreign key, as `parent_id` is not.
  - `INSERT OR IGNORE INTO households(id, name) VALUES ('home', 'Home')`
  - `ALTER TABLE profiles ADD COLUMN household_id TEXT NOT NULL DEFAULT 'home'`
  - `ALTER TABLE profiles ADD COLUMN household_updated_at INTEGER NOT NULL DEFAULT 0`
- Create: `web/src/state/households.ts`, which never imports `profiles.ts`:
  - `HouseholdRow {id, name, nameUpdatedAt, adminId, adminUpdatedAt, removed}`, `householdRows(db)` (removed ones included);
  - `Household {id, name, adminId: string|null}` and `listHouseholds(db, profiles: Profile[])`: the live ones, `adminId` resolved (the profile with `admin` in it), sorted by `normalName(name)` in code-point order (not `localeCompare`: the core sorts the same way).
- Modify: `web/src/state/profiles.ts`:
  - `Profile` gains `owner: boolean` (the install-wide admin claim, a grown-up's) and `household: string` (resolved); `admin` now means "runs its household";
  - `ProfileRow` gains `householdId`, `householdUpdatedAt`, and the SELECT reads them;
  - `listProfiles(db)` resolves through `householdView` (`../../public/lib/household-rules.js`) over `householdRows(db)`;
  - `toProfile(row)` alone gives `owner`, the stored household, and `admin: false`. Only `listProfiles` says who runs what.
- Create: `web/src/state/profiles-insert.ts`: `NewProfile` (gains `household?: string`), `insertProfile` and `findOrCreateProfile`, moved out of `profiles.ts` so it stays ≤200. A profile made with `household` has it dated `createdAt`. `insertProfile` answers the profile as `listProfiles` lists it. Update every importer (`store.ts`, `profiles-manage.ts`, tests).
- Modify: `web/src/state/store.ts`: `households(): Household[]`.
- Modify: `web/src/state/profiles-routes.ts`: `GET /api/profiles` adds `households: state.households()`.

**Interfaces — Produces:** `GET /api/profiles` → `{ remembers, heard, profiles: Profile[] (each with owner, household, admin), households: {id, name, adminId}[] }`.

- [ ] **Step 1: Failing tests.**
  - In `web/test/state-migration.test.ts`, a describe `"v15 to v16"` (use the real numbers) with the test `"every profile already stored is in Home, Home is named Home with nothing dated, and the owner runs it"`: a v15 file holding andre (claim), test, TV test and TV kids (kid of andre) → after open, all four are `household: "home"`, the `households` row is `('home','Home',0,NULL,0,0)`, `profiles()` says andre `owner` and `admin`, nobody else either.
  - Create `web/test/state-households.test.ts`:
    - `"a kid is listed in its parent's household"`;
    - `"a profile whose household is gone is listed in Home"`;
    - `"the owner runs the household they are in"`;
    - `"households are listed live, by name, each with who runs it"`.
- [ ] **Step 2: Run** `cd web && bun test test/state-migration.test.ts test/state-households.test.ts` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the whole suite: `bun test`, `bunx tsc --noEmit -p .`, `bunx eslint .`. Fix any test that read `admin` as "holds the claim": it now means "runs its household", which is the same on a one-household store.
- [ ] **Step 5: Commit** `feat(web): profiles belong to households, starting with Home`.

### Task 2.2: Sync — households and each profile's household on the record

**Files:**
- Create: `web/src/state/households-record.ts`:
  - `HouseholdEntry` (the wire row from `households-sync.json`);
  - `parseHouseholds(value: unknown): HouseholdEntry[]`;
  - `householdIdOf(value): string|null` (the id rule).
- Create: `web/src/state/households-merge.ts`: `mergeHouseholds(records): HouseholdEntry[]`:
  - per id, `name` and `admin` each through `keep` (newest, ties by device);
  - `removed` sticky;
  - the admin name through `normalName`;
  - sorted by id.
- Create: `web/src/state/households-exchange.ts`:
  - `exportHouseholds(db)`: a row only once something in it is dated, or it is removed. Untouched Home says nothing. An admin whose profile is not here is not said.
  - `importHouseholds(db, entries)`: an unknown id is made only from a row with a name. Name and admin are taken by the same `newer()` rule `roles-exchange.ts` uses, the admin by name onto the first local profile of that name. `removed` is set and never cleared.
- Modify: `web/src/state/roles-record.ts`: `RoleKeys.household?: {id, updatedAt}`, parsed with `householdIdOf` and `isStamp`.
- Modify: `web/src/state/roles-merge.ts`: `household` through `keep` for any profile.
- Modify: `web/src/state/roles-exchange.ts`: export once `householdUpdatedAt > 0`; import by `newer()`.
- Modify: `web/src/state/sync-record.ts`: `SyncRecord.households?`, parsed only when present.
- Modify: `web/src/state/merged.ts`, `merge.ts`: `MergedState.households?`, filled by `mergeHouseholds`.
- Modify: `web/src/state/store.ts`:
  - `exportRecord` writes `households`;
  - `importMerged` calls `importHouseholds` after `importRoles`, since an admin may be a viewer this same import made.
- Modify: `web/test/shared-watch-state-fixtures.test.ts`: describes `"households-sync parse fixtures"` and `"households-sync merge fixtures"` (merge run in both record orders, as `profile-roles-merge` is). The canonical form is `{households: merged.households ?? [], profiles: profiles with a household → {name, household}}`.
- Modify: `web/test/code-standards.test.ts`: ceilings for `sync-record.ts` and `store.ts`.

- [ ] **Step 1: Failing tests:**
  - the two fixture describes;
  - create `web/test/state-households-exchange.test.ts`:
    - `"a household nobody changed is not said"`;
    - `"a renamed Home is said with its name alone"`;
    - `"an admin whose profile is not here is not said"`;
    - `"a profile's household is said once it is dated"`;
    - `"a household heard from another device is made here, its admin found by name"`;
    - `"a removal is taken, and never undone"`;
    - `"a newer local rename survives an older merge"`;
    - `"a profile's newer household is taken"`;
    - `"a document from an older build changes no household"`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** `bun test`, `tsc`, `eslint` → PASS.
- [ ] **Step 5: Commit** `feat(web): households and each profile's household travel in the state sync`.

### Task 2.3: Managing profiles stays inside each household

**Files:**
- Modify: `web/src/state/profiles-manage.ts`:
  - `check()` hands `allowed` the resolved `listProfiles(db)`, so the guard from Task 1.2 bites;
  - `createGrownUp` and `createKid` make the profile in the actor's resolved household;
  - `prove` becomes `protected`;
  - `claimAdmin` moves out, to the next file. Lower the ceiling by what leaves.
- Create: `web/src/state/households-manage.ts`: `class HouseholdManager extends ProfileAccess`, holding `claimAdmin(id, pin)` per household. Refusals keep today's order (`not-found`, `invalid` for a missing first PIN, PIN proof). Then:
  - no owner on the install: today's claim, which makes the owner, who runs the household they are in;
  - otherwise, when `householdAllowed(…, "claim-household", target.household, "")`: write the household's `admin_id`, dated like `writeDailyLimit` (`MAX(now, MIN(old, LAST_STAMP) + 1)`), plus a first PIN when the target had none;
  - else `not-allowed`.
- Modify: `web/src/state/store.ts`: `manage()` returns `HouseholdManager`.

- [ ] **Step 1: Failing tests:** create `web/test/state-households-manage.test.ts`:
  - `"a household's admin adds a grown-up into its own household"`;
  - `"a kid is made in its parent's household"`;
  - `"the admin of one household cannot remove a grown-up of another"`;
  - `"with no owner yet, the claim makes the owner, who runs Home"`;
  - `"a grown-up of a household nobody runs may claim it while the install has an owner"`;
  - `"nobody claims a household somebody runs"`.
  
  The existing `state-profiles-manage.test.ts` claim tests must still pass unchanged.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** `bun test`, `tsc`, `eslint` → PASS.
- [ ] **Step 5: Commit** `feat(web): managing profiles stays inside each household`.

### Task 2.4: The owner's actions and joining, with their routes

**Files:**
- Modify: `web/src/state/households-manage.ts`. If it would pass 200 lines, the writes go to a new `households-write.ts`. Methods:
  - `createHousehold(actorId, pin, name, admin: {id, newPin?} | {name, newPin})` → `Refused | Household`
  - `renameHousehold(actorId, pin, id, name)`
  - `setHouseholdAdmin(actorId, pin, id, adminId, newPin?)`
  - `moveToHousehold(actorId, pin, id, profileId)`
  - `deleteHousehold(actorId, pin, id)`
  - `join(id, pin)`
- Create: `web/src/state/households-routes.ts`:
  - `POST /api/households` `{actorId, pin, name, adminId?, adminName?, newPin?}` → 201 `{id, name, adminId}`
  - `PUT /api/households/{id}/name` `{actorId, pin, name}` → 204
  - `PUT /api/households/{id}/admin` `{actorId, pin, adminId, newPin?}` → 204
  - `POST /api/households/{id}/move` `{actorId, pin, profileId}` → 204
  - `DELETE /api/households/{id}` `{actorId, pin}` → 204
  - `POST /api/households/{id}/join` `{pin}` → 204
  - any other method → 405
- Modify: `web/src/state/profiles-routes.ts`: export `answer` (and its `CODES`) for reuse, typed for any 201 body.
- Modify: `web/src/state/routes.ts`: the prefix check admits `/api/households`; `profileRoute(request, state) ?? householdRoute(request, state)`.
- Modify: `web/src/routes.ts` `writeWorthSyncing`: `/join` is like `/unlock` and starts no sync round.

**The order every owner action answers in:**
1. input that cannot be used: `invalid` (a name `cleanName` refuses, a new PIN that is not four digits);
2. `name-taken`: a household name among the live ones, compared by `normalName(cleanName(…))`; a new grown-up's name across the whole install (`nameTaken`);
3. `not-found`: the actor, the target or the household is unknown, or the household is removed;
4. `not-allowed` for a kid actor;
5. the owner's PIN (`prove`: wait, no-pin, wrong-pin);
6. `householdAllowed` → `not-allowed`;
7. `no-pin`: the admin-to-be has no PIN and no valid `newPin` came.

**What each action writes**, every stamp dated like `writeDailyLimit`, all in one transaction:
- create: a new id (`crypto.randomUUID()`), name and admin dated now. For an existing grown-up, it and every kid whose `parent_id` is it move in. For a new one, it is made there with its PIN.
- rename: the name.
- change admin: `admin_id`, plus a first PIN when given.
- move: the grown-up and each of its kids.
- delete: `removed = 1`.
- `join`:
  - `not-found` for an unknown or removed household;
  - the candidates are the household's resolved admin and the owner, those with a PIN; none → `no-pin`;
  - `PinWait` keyed `household:<id>`: one wait per household, never charged to a profile;
  - the PIN matches either candidate → done, otherwise `wrong-pin`;
  - nothing stored: the browser keeps the household.

- [ ] **Step 1: Failing tests.**
  - In `state-households-manage.test.ts`:
    - `"the owner creates a household with a new grown-up, who runs it"`;
    - `"the owner creates one with an existing grown-up, who moves in with their kids"`;
    - `"a household's name is unique among the live ones"`;
    - `"a new grown-up's name is unique on the whole install"`;
    - `"an admin without a PIN is given one, and refused without"`;
    - `"the owner moves a grown-up and their kids, each dated"`;
    - `"a household's last grown-up moves out, and the emptied household can be deleted"`;
    - `"the owner deletes an empty household, never Home"`;
    - `"only the owner acts on households"`;
    - `"joining takes the household admin's PIN or the owner's"`;
    - `"wrong join PINs wait per household, not per profile"`.
  - Create `web/test/state-households-http.test.ts`:
    - `"POST /api/households answers 201 with the household"`;
    - `"each owner route answers 204, and a refusal its reason and code"`;
    - `"join answers 204 and starts no sync round"`;
    - `"a household write from another origin is refused"`;
    - `"GET /api/profiles lists the live households by name"`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** `bun test`, `tsc`, `eslint`, the `code-standards` ceilings → PASS.
- [ ] **Step 5: Commit** `feat(web): the owner sets up households, and a device joins one`.
