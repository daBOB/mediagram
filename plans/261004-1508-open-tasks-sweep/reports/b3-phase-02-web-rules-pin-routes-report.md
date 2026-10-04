# B3 phase 02: web rules, PIN, wrong-PIN wait, routes

2026-10-04, fullstack-developer subagent, worktree branch `worktree-agent-ad8df52e65ce72537`.
The branch was fast-forwarded from 0.99.9 to main `0d539026` (0.105.1, phase 01 included) before work began.
Plan `260928-0047-profile-roles-pins-kids-age-limits`, phase 02, built to the pre-flight rulings.

**Status:** done (pending merge). Not pushed, versions not bumped, changelog not touched. Ship together with phase 03: the current picker's `POST { name, kids }` now reads as create-first, and its body-less `DELETE` is `404 not-found`.

## Commits
| Hash | What |
|---|---|
| `6d843d10` | `profiles-pin.ts`, `profiles-wait.ts`, `profiles-rules.ts`; `pin-hash.json`, `profile-rules.json` (42 cases); `shared-profile-fixtures.test.ts`; unit tests |
| `6b7ac8a6` | `ProfileManager` (`profiles-manage.ts`); `NewProfile` role fields, `writePin`, `writeKidsAge` (`profiles.ts`); `WatchState.manage()` and a per-store `PinWait` |
| `0fdc3f88` | `profiles-routes.ts`; `state/routes.ts` delegates to it; `/api/kids` age; `renameProfile`/`PATCH` removed; DELETE bodies read; `/unlock` skipped by `writeWorthSyncing`; ceilings lowered |
| (this) | docs(plan): phase-02 status, plan row, contract §2/§8 amendment, this report |

## Test-first record
RED, with every test and fixture written before any source file (7 files, 109 tests): **39 pass, 70 fail, 4 load errors**.
- Load errors: `Cannot find module '../src/state/profiles-{pin,wait,rules}'`.
- Failures: `TypeError: state.manage is not a function` and the new HTTP cases (`claim-admin`/`unlock`/`pin`/`kids-age` → 404; `PATCH` → 204; `fromSix` undefined).

A mutation check confirmed that the deviations below are pinned:
- With `isAdmin` reverted to `one.admin`, 2 fixture cases fail.
- With the admin-exists check reverted to the raw column, and with the cascade made unconditional, 1 manage test fails for each.

GREEN in `web/`:
- `bun run lint`: clean.
- `bun test`: **2867 pass / 0 fail** across 206 files. Baseline was 2754 across 201, so this phase adds 113 tests and 5 files.
- `bunx tsc --noEmit -p .`: clean.
- `code-standards.test.ts`: passes.

## Files (lines)
- New: `profiles-pin.ts` 39, `profiles-wait.ts` 45, `profiles-rules.ts` 55, `profiles-manage.ts` 183, `profiles-routes.ts` 89.
- Changed: `profiles.ts` 166→199, `state/routes.ts` 243→223, `store.ts` 759→760, `src/routes.ts` 166→168, `server.ts` 269 (line-neutral), `http/browser-write.ts` 31.
- Ceilings, with a dated sentence: `state/routes.ts` 243→223, `store.ts` 800→760, `sync-record.ts` 328→301.
- Tests:
  - New: `state-profiles-{pin,wait,rules,manage}.test.ts` and `shared-profile-fixtures.test.ts`.
  - Rewritten: `state-http.test.ts`, whole `profiles` describe plus a Kids-age case.
  - Changed: `state-write-triggers-sync.test.ts`. The profile write is now claim-admin; added "unlock never fires" and "a refused PIN never fires".

## Rulings applied (pre-flight overrides)
- **No `route-json.ts`.** `profiles-routes.ts` imports `P`, `parse`, `json` and `status` from the existing `route-shared.ts`. No `PROFILE_ID` export is needed.
- **`writeWorthSyncing`:** the only addition is the `/unlock` exclusion. No `/preferences` exclusion was added back.
- **Clamped own writes:** `pin_updated_at` and `kids_age_updated_at` are stamped `MAX(now, MIN(stored, 2^53−2) + 1)`. Each has a far-ahead-clock test and a test with the stored stamp at `2^53−1`, which stays at `2^53−1` and does not overflow.
- **Line-cap ratchet:** respected.
- **Admin PIN separate from the Settings admin token:** `src/settings/admin-gate.ts` is untouched.
- **PIN hash and salt never leave through `GET /api/profiles`:** `toProfile` exposes only `hasPin`. An HTTP test asserts the body never matches `pin_?hash|pin_?salt|[0-9a-f]{32}`. Nothing logs a request body; `server.ts` logs only the method and URL.

## Deviations from the phase file (decisions for the lead)
1. **The rule treats `admin` as meaningful only on a grown-up** (`isAdmin = admin && !kids` in `ownerOf` and `remove`). The plan's code trusted `RoleView.admin` as given.
   - A view built from `toProfile` never says a kid is admin, but `allowed` is the function the core ports against the fixture, so the rule is now total over its input.
   - Three fixture cases pin it: "a kid said to be the admin …". That brings the fixture to 42 cases; the plan had 39.
   - Contract §2 and §8 now say so.
2. **`claimAdmin`'s "an admin exists" counts only a grown-up's claim** (`toProfile(row).admin`, not `admin_claimed_at IS NOT NULL`).
   - Otherwise a claim left on a row that sync later turned into a kid blocks the household from ever claiming again. That row keeps its column after the sticky `kids` upgrade, and the merge already ignores it.
   - Tested: "an admin another device later called a kid runs nothing, so a grown-up may claim".
3. **Remove cascades only from a grown-up, and only to kids**: `DELETE … WHERE parent_id = ?1 AND kids = 1`, run only when the target is a grown-up.
   - The plan's unconditional `WHERE parent_id = ?1` would also delete a kid that names the removed kid as parent. Sync sets `parent_id` by name, so that can happen.
   - This matches contract §2 ("removing a grown-up also removes…"). Tested.
4. **`PUT /api/kids/:setId { age: null }` → 400.** Only an absent `age` means 12. The plan used `?? 12`, which accepted `null`.
5. **The rule, manager and routes take a `Profile` (from `toProfile`) as their `RoleView`**, so the "a kid is never admin" rule is applied in one place. There is no separate `roleView` mapper.
6. **`store.ts` grew by 1 line** (759→760: the `PinWait` field, `manage()` and two imports, minus `renameProfile`). The plan expected it to shrink, but phase 01 had already moved profiles out. The ceiling is now 760.

## Routes phase 03 uses (contract §8, as built)
- `GET /api/profiles` → `{ remembers, profiles: Profile[] }`. Profile = `{ id, name, createdAt, kids, kidsAge, parentId, admin, hasPin }`.
- `POST /api/profiles`:
  - `{ actorId, pin, name, kids: true, kidsAge }` → 201 kid.
  - `{ actorId, pin, name, kids: false|absent, newPin }` → 201 grown-up.
  - No `actorId` key: `{ name, newPin }` → 201 admin, or 403 `not-allowed` once a grown-up exists.
- `DELETE /api/profiles/:id { actorId, pin }` → 204. Without a body → 404 `not-found`.
- `POST …/unlock { pin }`, `POST …/claim-admin { pin }`, `PUT …/pin { actorId, pin, newPin }`, `PUT …/kids-age { actorId, pin, age }` → 204.
- A wrong method on any profile path → 405, and `PATCH /api/profiles/:id` → 405.
- Refusals carry `{ reason, retryAfter? }`: 400 `invalid`, 404 `not-found`, 409 `no-pin`, 403 `wrong-pin` | `not-allowed`, 429 `wait` plus a `Retry-After` header.
- A cross-origin write is a **bodiless** 403, and a non-JSON non-DELETE write is a bodiless 415. Phase 03 must read a 403 with no body as "no reason", not as `wrong-pin`.
- `GET /api/kids` → `{ kids, fromSix }`. `PUT /api/kids/:setId { age?: 6|12 }` → 204, or 400 for any other age. `DELETE` is unchanged.
- **The FSK 6 default for a new kid (user decision) belongs to phase 03's "Add a kid" form.** The server requires `kidsAge`, per contract §8. Phase 03's own form test picks "12" explicitly; the select's initial value must be 6.

## What the core port (phase 05) must match
- `pin-hash.json` (2 vectors) and `profile-rules.json` (**42** cases, including the 3 "a kid said to be the admin" cases: `admin` counts only with `!kids`).
- Refusal order:
  1. `invalid`: only what would be stored (name, `newPin`, age, and claim-admin's PIN when the target has none).
  2. `not-found`.
  3. Structural `not-allowed`: a kid actor; for claim-admin, a kid target or a **grown-up** admin present; for create-first, any grown-up present.
  4. `no-pin`.
  5. `wait`, checked only when a PIN is about to be compared.
  6. `wrong-pin`: a malformed current PIN is compared and counts.
  7. The rule's `not-allowed`.
- **Self set-pin on a PIN-less grown-up** skips the PIN entirely.
- **A kid's unlock** is free and never waits.
- **Create-first** works only while no grown-up exists. Kids alone do not count.
- **The wait:** superseded by the review follow-up below — counted per profile.
- **Clamped stamps:** `MAX(now, MIN(stored, 2^53−2) + 1)` for `pin_updated_at` and `kids_age_updated_at`. Every age write re-stamps, including a rewrite of the same age.
- **Claim-admin and the first admin's claim** are stamped plain `now`.
- **Remove:** a grown-up target deletes `kids = 1 AND parent_id = target` in the same transaction; a kid target deletes only itself.
- **New rows:** a created kid gets `kids_age_updated_at = created_at`, and a created grown-up with a PIN gets `pin_updated_at = created_at`. Only a sync-made kid keeps `12 @ 0`.

## Review follow-up (2026-10-04, `28afa205`)
Contract §3 and §4 are amended; the core matches these:
- **`name-taken`** (409, `ProfileOutcome::NameTaken`), checked right after `invalid` in create-first, create-grown-up and create-kid. It fires when the new name collides with any profile, kid or grown-up, compared as `normalName(cleanName(·))` on both sides.
  - Why: sync keys viewers by name and `kids` only ever turns on, so a kid named after the admin made the admin a kid everywhere.
  - Pinned by `profile-names.json` (9 cases). That fixture is separate from `profile-rules.json` because a `RoleView` carries no name and the check runs before the rule.
- **The wait is per profile whose PIN is compared.**
  - That profile's 5th failure starts 60 s for that profile only; seconds left are rounded up.
  - Only that profile's own right PIN clears its count, as does its wait running out.
  - One wait store per store/`Core`.
  - Pinned by `pin-wait.json` (8 cases, injectable clock).

## Concerns
- `profiles.ts` is at 199/200. Phase 03 does not touch it, but any later addition there must split first. `writePin`/`writeKidsAge` are the natural candidates.
- Deviations 1–3 change what the core must do. Contract §2 and §8 are amended (dated 2026-10-04), and the lead should confirm them before phase 05.

**Status:** DONE_WITH_CONCERNS
**Summary:** Phase 02 is built test-first in 3 code commits. Web lint, 2867 tests and types are green, and both shared fixtures are created and run.
**Concerns:** Three small rule/manager deviations that make roles safe against a kid's leftover admin claim, plus a cascade that stays on kids, all pinned by tests and written into the contract. The FSK 6 default lands with phase 03's form.
