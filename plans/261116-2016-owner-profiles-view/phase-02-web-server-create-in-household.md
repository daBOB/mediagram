# Phase 02 — Web server: create in a named household, or for a named parent

Status: todo · Depends on: Phase 01

## Context

- `web/src/state/profiles-manage.ts` (194 lines — at the limit; put new helpers in
  `web/src/state/profiles-insert.ts` or a new `profiles-create.ts` subclass the way
  `profiles-access.ts` extends `ProfileManager`)
  - `createGrownUp(actorId, pin, name, next)` ~L71, `createKid(...)` ~L78
  - `check(actorId, pin, action, targetId, unproven)` ~L162: refuses `not-found` when
    `targetId` names no profile row
  - `householdOf(actorId)` ~L149: Home is stored undated (`{}`)
- Route `web/src/state/profiles-routes.ts` ~L60-68 (`POST /api/profiles`)
- An admin-less household gets the moved grown-up as admin in
  `HouseholdManager.moveToHousehold` (`web/src/state/households-manage.ts` ~L135) via
  `writeHouseholdAdmin(db, id, profileId)`; creation mirrors that.

## Interfaces

- Produces (HTTP, consumed by Phase 04's `profile-api.js`):
  - `POST /api/profiles { actorId, pin, name, kids: false, newPin, household? }`
  - `POST /api/profiles { actorId, pin, name, kids: true, kidsAge, dailyLimit, parentId? }`
  - Answers exactly as today: the created `Profile` JSON, or `{ reason }` with its status.
- Produces (TS):
  - `createGrownUp(actorId: string, pin: unknown, name: unknown, next: unknown, household: unknown = null): Refused | Profile`
  - `createKid(actorId: string, pin: unknown, name: unknown, kidsAge: unknown, dailyLimit: unknown = null, parentId: unknown = null): Refused | Profile`

## Behaviour

- `household` absent/null → today's behaviour. Present but not a string, or not a live household
  id (`listHouseholds`) → `not-found`. Otherwise the rule decides with that id as target
  (`check`'s profile-existence test must not apply to `create-grown-up`'s household target).
- The new grown-up is stored in that household (Home stays undated). If that household's
  `adminId` is null, the same transaction makes the new grown-up its admin.
- `parentId` absent/null → the actor, as today. Otherwise `check(..., "create-kid", parentId)`
  (unknown id → `not-found`; the rule refuses a kid or a non-owner naming someone else).
  The kid is stored with that `parentId` and the parent's household.
- Refusal order unchanged: invalid input, name, not-found, kid acting, PIN, rule.

### Task 2.1: Manager + route

**Files:** Modify `profiles-manage.ts` (or new `web/src/state/profiles-create.ts`), `profiles-routes.ts`.
Test: `web/test/state-profiles-manage.test.ts`, `web/test/state-households-http.test.ts`.

- [ ] **Step 1: Write failing tests** in `state-profiles-manage.test.ts` (set up households the way
  `state-households-manage.test.ts` does):
  - `the owner adds a grown-up straight into another household` → listed with `household` = that id, `admin: false`
  - `a grown-up added to a household with no admin becomes its admin` → household's `adminId` = new id
  - `a household's admin cannot add a grown-up to another household` → `{ reason: "not-allowed" }`, nothing inserted
  - `a household that does not exist is not found` → `{ reason: "not-found" }`
  - `the owner adds a kid for a grown-up in another household` → `parentId` = that grown-up, household = theirs
  - `a grown-up cannot add a kid for somebody else` → `not-allowed`
  - `naming nobody keeps today's behaviour` → both creates without the new field land in the actor's household
- [ ] **Step 2:** `cd web && bun test test/state-profiles-manage.test.ts` → the new tests FAIL.
- [ ] **Step 3: Implement** the two signatures above; the route passes `said.household ?? null` and `said.parentId ?? null`.
- [ ] **Step 4:** add one HTTP round trip to `state-households-http.test.ts`
  (`POST /api/profiles` with `household` → 200 and the profile in that household), then
  `cd web && bun test && bun run typecheck` → PASS.
- [ ] **Step 5: Commit** — `feat(web): the owner adds a profile straight into any household`

## Success criteria

Old clients (no new fields) behave exactly as before; the owner can place a new grown-up or kid anywhere.
