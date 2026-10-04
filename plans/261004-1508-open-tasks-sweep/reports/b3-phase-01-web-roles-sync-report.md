# B3 phase 01: web roles schema, sync keys, merge, export/import

2026-10-04, fullstack-developer subagent, worktree `worktree-agent-a15222cdd2a2a2eae` (branched off main `53f07216`, 0.103.0).
Plan `260928-0047-profile-roles-pins-kids-age-limits`, phase 01. Built to the pre-flight rulings.

**Status:** done (pending merge). Not pushed, versions not bumped (the lead bumps at merge).

## Commits
| Hash | What |
|---|---|
| `b85dd7e8` | refactor(web): pure moves, no behaviour change. `profiles.ts` (profile rows out of `store.ts`), `record-scalars.ts` (`asArray`, `objectRow`, `numberFromScalar`, `text_`, `isStamp` out of `sync-record.ts`; `stats-record.ts` repointed), `merged.ts` (`MergedProfile`/`MergedState`, re-exported from `merge.ts`) |
| `2b37b552` | feat(web): schema v12, role keys, merge, exchange, Kids-mark age, fixture, tests |
| (this) | docs(plan): phase-01 status, this report |

## Test-first record
RED, with the tests and fixture written before any source change (6 files, 315 tests): **260 pass, 55 fail**. Sample failures:
`TypeError: state.kidsFromSix is not a function`, `SQLiteError: no such column: kids_age`, every role-key merge/parse/exchange case, and 13 of the then 16 fixture cases. The other 3 fixture cases passed vacuously: a raw row's `age` already rode through `keep`.
After the moves (`b85dd7e8`): the same 55 failed and nothing else did (2682 pass).
A second RED during the compatibility check (see below): 2 fail (`… an older build's echo … does not strip the age`, fixture tie case), then green.

GREEN in `web/`: `bun run lint` clean · `bun test` **2742 pass / 0 fail** (201 files; baseline 2669) · `bunx tsc --noEmit -p .` clean · `code-standards.test.ts` passes.

## Files (lines)
New: `roles-schema.ts` 25, `roles-record.ts` 69, `roles-merge.ts` 103, `roles-exchange.ts` 98, `profiles.ts` 166, `record-scalars.ts` 41, `merged.ts` 47.
Changed: `schema.ts` 245 (ceiling raised 243→245 with a dated sentence), `store.ts` 759/800, `sync-record.ts` 301/328, `merge.ts` 168, `lists-exchange.ts` 195, `tie-break.ts` 40.
Tests: new `state-roles-{record,merge,exchange}.test.ts`, `fixtures/watch-state/profile-roles-merge.json` (19 cases) and its runner in `shared-watch-state-fixtures.test.ts`. Extended `state-lists-sync.test.ts` (Kids-mark age, 9 cases) and `state-migration.test.ts` (v11→v12; the two profile-shape assertions; the v10→v11 test's `"11"` now reads `String(GROUPS.length)`, because opening a v10 file now ends at 12).
`record-parse.json`, `merge.json`, `lists-merge.json`, `stats-*.json`: unchanged, all still pass.

## What was built (signatures phase 02 relies on)
- `profiles.ts`: `Profile {…, kidsAge: 6|12|null, parentId, admin, hasPin}`, `ProfileRow`, `NewProfile { kids? }`, `cleanName`, `profileRows`, `toProfile`, `listProfiles`, `insertProfile(db, name, role = {})`, `deleteProfileById`, `profileExists`, `findOrCreateProfile(db, …)`. `WatchState`'s profile methods are one-line delegates, as before.
- `roles-record.ts`: `RoleKeys {admin?, kids?, kidsAge?, parent?, pin?}`; `ProfileState extends StatsRows, RoleKeys`; `parseRoleKeys(row)`.
- `roles-merge.ts`: `MergedRoles = Omit<RoleKeys,"kids">`, `mergeRoles(records): Map<name, MergedRoles>` (precomputed, like `mergeStats`), `kidsMarkRank`.
- `roles-exchange.ts`: `exportRoles(db): Map<id, RoleKeys>`, `importRoles(db, merged)` (runs after the per-profile loop).
- `WatchState.setKids(setId, marked, age: 6|12 = 12)`, `WatchState.kidsFromSix()`. `ListRow.age?: 6`.
- `GET /api/profiles` returns the new fields through `profiles()`. The hash and salt are never included (tested).

## Rulings applied
- Bounds: `admin.claimedAt` and `pin.updatedAt` pass `isStamp`, and `kidsAge.updatedAt` is `0 || isStamp`. Each runs through `numberFromScalar` coercion, as every older row's stamp does. Tested at 2^53, 2^63−1 and 1e300, with 2^53−1 kept.
- `kidsRow` wraps today's `listRow`, which keeps the 2^53−1 bound. `age` is only the literal `6`, only on a live mark.
- Own writes are clamped: a Kids mark, an age change and a removal are each stamped `MAX(now, MIN(stored, MAX_SAFE−1) + 1)`. A re-mark after a removal is clamped too, which today's `setKids` did not do. `kids_age` and PIN writes belong to phase 02.
- Import upgrade: `kids = 1, kids_age = COALESCE(kids_age, 12)`. Role import runs after the loop, so a parent made in the same import is found.

## Deviations the core (phase 04) must port: decisions for the lead
1. **Kids-mark tie: "from 6" outranks before the device id.** This deviates from contract §7, which says age rides on the row `keep` chose. It also replaces the plan fixture case "a Kids mark tie is settled by device id, age and all".
   - Found by a one-off check that ran main's `sync-record`/`merge`/`store` (0.103.0) against the new code: a build from before ages drops `age` when it imports a mark and writes the mark back at the same `updatedAt`. Under a plain device-id tie, that echo wins whenever the older device's id sorts higher, and then every device strips "from 6".
   - This is the trap `sync-record.ts` documents for `watched` removals. Here it is fixed with a total order at a tie: `(updatedAt, liveAged ? 1 : 0, device)`.
   - A real age change always moves the mark's clock, so an equal-time pair that differs only in age is the echo.
   - Done through an optional `rank` on `keep` (`tie-break.ts`) and `kidsMarkRank` (`roles-merge.ts`). Pinned by 2 fixture cases, merge unit tests and a store-level echo test.
2. **`toProfile`: a kid is never `admin` and never `hasPin`, whatever its columns hold.** The case: a grown-up holding a claim or PIN that another device later calls a kid. The sticky `kids` upgrade leaves the old columns in place, and phase 02's rule would otherwise let a PIN-less kid act as admin. Export still follows contract §7 literally (`admin`/`pin` whenever the columns are set); the merge drops them on a kid.
3. **Fixture expected profiles carry `displayName`.** This goes beyond contract §10's listed fields. The core's `MergedProfile.display_name` has no `serde(default)`, so with it the core can deserialize `expect` as `MergedState` and add a `roles_only` projection like `stats_only`.

## Compatibility story (verified)
- **Wire:** only new optional keys; `SYNC_FORMAT` stays 1. A one-off script (scratchpad; it imports main's modules, so it is not committable) ran main's `parseRecord`, `mergeStates` and `importMerged` against a new device's export:
  - Today's build accepts the document. Kids stay kids, positions and the Kids mark arrive (read as from 12), and the role keys are dropped.
  - Today's echo back to the new device changes **0** rows: FSK 6, the admin and the mark's "from 6" all survive.
  - A third, new device merging both documents lands on FSK 6 and the same admin, and its second round is quiet.
- **An older build's kid** (`kids: true`, no `kidsAge`) merges to `{12, 0}`, which loses to any limit a parent sets. Covered by tests and fixture cases.
- **Database:** additive columns; an older build opening a v12 file skips every group and never names the new columns. A tombstone that an older build's removal left with a stale `age` is never exported or compared, so imports still settle.
- **HTTP:** `GET /api/profiles` gains fields; nothing else changes.
- **Android/core** on today's format: its `parse_record` reads only named keys off a `serde_json::Value` (`state/record/parse.rs`, `list_record.rs:37-44`), so it drops the new keys as the old web does. It also echoes Kids marks without `age`, and the tie rank above covers that.

## User decisions touching this phase
- **New kid starts at FSK 6:** no "new kid" path exists in phase 01. The only paths are sync import, where `{12, 0}` must lose to any choice, and today's `createProfile(name, true)`, which phase 02 replaces with a create-kid route that requires `kidsAge`. So the FSK 6 default lands with the create-kid form (phases 02/03). A kid made through the old route before then reads FSK 12 at 0, which matches the client filter that ships until phase 03.
- **Household PIN separate from the Settings admin token:** `src/settings/admin-gate.ts` was not touched.

## Review follow-up (resolved)
The review adopted deviations 1–3. The follow-up commit:
- adds a merge-fixture case where a from-6 mark beats a same-stamp removal outright (20 cases now);
- adds `profile-roles-record-parse.json` (11 cases, run through the real `parseRecord`). It covers: role keys read back, malformed keys dropped alone, the bounds (`kidsAge.updatedAt` 0 kept; 2^53−1 kept; 2^53 dropped for all three stamps), numeric-string coercion, and a Kids tombstone's `age` stripped at parse;
- writes the rules into `shared-contract.md`: §7 (Kids-mark tie and the port note that the core's `set_kids` must move the clock on every age change, 6→12 included; role-stamp bounds; clamped local writes), §8 (a kid is never admin/`hasPin`) and §10 (both fixtures, `displayName`).

**Status:** DONE_WITH_CONCERNS
**Summary:** Phase 01 is built test-first in 2 code commits, with web lint, tests (2742) and types green, and verified to interoperate with 0.103.0 both ways.
**Concerns:** Deviations 1–2 change contract §7/§8 behaviour that the core port must match. The lead should confirm them before phase 04.
