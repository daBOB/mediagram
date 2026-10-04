# Phase 01 — Web: schema, sync keys, merge, export/import

## Context links

- Contract (authoritative): [shared-contract.md](shared-contract.md) §1 roles, §6 schema, §7 wire/merge/import/export, §10 fixtures
- Spec: `docs/superpowers/specs/2026-09-28-profile-roles-design.md` §3 (data and sync)
- Plan: [plan.md](plan.md) — decisions 3 (marks carry an age) and 4 (rules on each synced profile, earliest admin claim wins)
- Bumping procedure every commit step uses: [phase-08 § Bumping](phase-08-verify-docs-version.md)
- Code (HEAD `220fa71e`): `web/src/state/{schema,store,sync-record,merge,tie-break,lists-exchange,sync}.ts`,
  `web/test/{state-migration,state-store,state-lists-sync,state-sync-record,state-merge,shared-watch-state-fixtures,code-standards}.test.ts`
- Core runner that reads the same fixture directory: `crates/mediagram-core/tests/shared_watch_state_fixtures.rs:22`

## Overview

Priority P1 (every later phase reads what this one stores and syncs). Status: done (pending merge) —
commits `b85dd7e8` (moves) and `2b37b552` (feature), built to the pre-flight rulings (schema **v12** in
`roles-schema.ts`, `record-scalars.ts`, `merged.ts`, `mergeRoles`, `kidsRow`, `isStamp` bounds, clamped
Kids writes). Three deviations the core must port, with reasons, in
`../261004-1508-open-tasks-sweep/reports/b3-phase-01-web-roles-sync-report.md`: a Kids-mark tie ranks
"from 6" before the device id; `toProfile` never calls a kid admin or `hasPin`; the fixture's expected
profiles carry `displayName`. All three adopted by review and written into `shared-contract.md` §7/§8/§10;
fixtures `profile-roles-merge.json` (20 cases) and `profile-roles-record-parse.json` (11 cases).
Give each profile room for a role — a kid's own limit and parent, the one admin claim, a
grown-up's PIN hash — and each Kids mark an age; carry both on the existing sync record as
new optional keys; merge them by the contract's rules; export and import them correctly.
No HTTP change beyond `GET /api/profiles` gaining the §8 fields (additive). No rule, PIN
check or management operation — that is phase 02.

## Key insights (verified)

- **`store.ts` has one line of room** — 799 lines, ceiling 800 (`web/test/code-standards.test.ts:61`;
  the test counts `split("\n").length - 1`, i.e. `wc -l`). The profile code is
  `store.ts:56-62` (`Profile`), `:92-93` (`MAX_NAME`), `:122-159` (`profiles`, `createProfile`,
  `renameProfile`, `deleteProfile`, `has`), `:479-500` (`findOrCreateProfile`), `:719-724`
  (`cleanName`, also used by collections at `:582`, `:600`). Moving it to a new
  `profiles.ts` frees 46 lines (799 → 753); everything this phase adds back is 22 (→ 775).
- **Keep `WatchState`'s profile methods as one-line delegates.** Outside `store.ts` the only
  source caller is `state/routes.ts` (`:99`, `:108`, `:116`, `:119`, `:124`, `:139`, `:188`,
  `:197`, `:214`, `:222`); ~60 test call sites use `state.createProfile(name[, kids])`,
  `state.profiles()`, `state.deleteProfile(id)` (e.g. `state-store.test.ts:24`,
  `state-sync.test.ts:18`, `state-http.test.ts:76`). The `Profile` type is imported nowhere
  outside `store.ts` (grep), so it can move.
- **`sync-record.ts` is at its ceiling** — 328/328 (`code-standards.test.ts:62`). Its four
  hostile-input readers (`asArray`, `objectRow`, `numberFromScalar`, `text_`, `:307-328`) are
  private and used only there; moving them to `record-scalars.ts` frees 23 lines and lets a
  new `roles-record.ts` read values the same way without a runtime import cycle.
- **`merge.ts` is unlisted, so it must stay ≤ 200** — it is 184. Role merging goes in a new
  `roles-merge.ts` with a 5-line hook in `mergeStates`, the way `tie-break.ts` was split out
  (`tie-break.ts:1-5`). `keep()` (`tie-break.ts:20-37`) is Map-keyed and ties by device id —
  reused for `kidsAge` and `pin` keyed by viewer. The `displayName` tie-break to mirror for
  `parent` is `merge.ts:116-135`; the sticky `kids` rule is `:137-139`; output `:168-177`.
- **`schema.ts` must exceed its ceiling.** 244 lines, ceiling 245 (`code-standards.test.ts:60`);
  the v11 group is 20 lines. The file's own comment (`code-standards.test.ts:18-33`) records
  dated revisions — one more dated sentence, not a split migration list.
- **Migrations run once per version, in a transaction** (`store.ts:765-788`); `ALTER TABLE …
  ADD COLUMN` plus one `UPDATE` is safe to add as group 11. An older build opening a v11 file
  skips every group (`version <= at`) and never names the new columns in its `INSERT`
  (`store.ts:138` lists its columns), so a rollback of the code is safe.
- **Export today writes `kids` only when true** (`store.ts:389`); **import upgrades `kids`
  sticky** (`store.ts:438-443`) and **creates unknown viewers** via `findOrCreateProfile`
  (`store.ts:434`). A kid's parent may be a viewer that same loop creates, so role import
  must run *after* the loop.
- **Kids and editor's-choice marks share one exchange** (`lists-exchange.ts:32-42`,
  `:77-103`), with the table name spliced into SQL; only `kids` gets an `age` column, so the
  shared SQL must select `NULL AS age` for `editors_choice`. The equal-time skip rule
  (`:86-87`) must compare the age on live rows only, or a tombstone that still holds an old
  age in its column would re-import every round and a sync would never settle.
- **`setKids` today refuses to bump a live mark** (`store.ts:520-522`,
  `WHERE removed_at IS NOT NULL`); an age change must bump it (contract §8: "updated in place
  (new `marked_at`)"), a same-age re-mark must not (`state-store.test.ts:316-320`).
- **Fixture runners compare field-picked copies** for `merge.json` and `lists-merge.json`
  (`shared-watch-state-fixtures.test.ts:62-77`, `:98-111`), so new `MergedProfile` keys do not
  disturb them; `record-parse.json` compares the whole parse (`:41-45`), so role keys must be
  conditional spreads (absent, never `undefined`-valued keys that a stricter core would see).
- **The shared runner's header forbids writing new behaviour through it**
  (`shared-watch-state-fixtures.test.ts:5-8`: every case must pass against the web as it
  stands). So unit tests drive the implementation, and the fixture lands last and passes on
  its first run.
- **Two tests pin the exact profile shape**: `state-migration.test.ts:284` and `:318`
  (`toEqual([{ id, name, createdAt, kids }])`); `state-metadata-failures.test.ts:62` compares
  `createProfile`'s return to `profiles()`, so both must return the same shape.
- **Sync path** (`sync.ts:127-137`): `exportRecord` → channel → `parseRecord` → `mergeStates`
  → `importMerged`; pushes only when the export changed (`sync.ts:141-153`), so an import
  that reports changes it did not make would not loop the channel but would lie to callers.
- **Contract PIN vectors verified**: `sha256("00112233445566778899aabbccddeeff1234")` =
  `f377124b…0924`, `sha256("ffeeddccbbaa998877665544332211000000")` = `48618b45…37f8` —
  used as realistic hashes in this phase's tests and fixture.

## Requirements

Functional
- Schema v11 exactly as contract §6; existing kids → `kids_age = 12`; existing marks `age NULL`.
- `Profile` (and `GET /api/profiles`) carries `kidsAge`, `parentId`, `admin`, `hasPin` (§8); never a hash or salt.
- `ProfileState` parses `admin`, `kidsAge`, `parent`, `pin`; `ListRow.age` parses only literal `6`, only on Kids rows; a malformed sub-key is dropped alone (§7).
- Merge (§7): `kidsAge` newest-wins (tie by device), default `{12, 0}` on a kid, only on a kid; `pin` newest-wins, only on a grown-up; `parent` first seen then device-id tie-break, output normalised, only on a kid; one `admin` household-wide, among grown-up viewers only (earliest `claimedAt`, tie → smaller name; a claim on a viewer any document calls a kid is ignored); Kids mark `age` rides on the kept row.
- Export (§7): `admin` when claimed; `kids` + `kidsAge` on kids; `parent` when `parent_id` resolves to a local name; `pin` when set. Kids marks: `age: 6` on a live mark only.
- Import (§7): `kids_age`/`pin_*` when merged `updatedAt` is newer, or equal with a different value; `admin` set on the named profile and cleared on every other, untouched when none named; `parent_id` only while `NULL`, to the local profile of that normalised name, never overwritten; Kids mark `age` written with the row.
- `setKids(setId, marked, age = 12)`: a live mark whose age changes gets a new `marked_at`; a mark, an age change and a removal are each stamped `max(now, stored + 1)` (§7 local writes); `kidsFromSix()` lists live "from 6" marks.

Non-functional
- Every touched listed file within its ceiling; `merge.ts` and every new file ≤ 200.
- No `SYNC_FORMAT` bump (§7); an older reader drops the new keys and keeps merging the rest.
- No plan/phase references in code, comments, test names or commit messages.

## Architecture

```
write (setKids age; phase 02: manage ops) ─► profiles.* / kids.age columns
                                                   │
exportRecord ─ profileRows ─► exportRoles ─► ProfileState{admin,kids,kidsAge,parent,pin}
             └ exportTitleMarks("kids") ─► ListRow{…, age?: 6 (live only)}
      │ JSON on the channel (sync.ts:141-153)
parseRecord ─► parseRoleKeys (roles-record.ts) ; listRow(aged=true) for top-level kids
      │
mergeStates ─► roleMerger.see(viewer, profile, device)  … every document (kids sticky, claims, limits…)
            └► roleMerger.keysFor(viewer)                … per merged viewer (admin decided once, grown-ups only)
            └► keep(kids rows)  — age rides on the kept row
      │
importMerged ─► findOrCreateProfile (kids upgrade sets kids_age = COALESCE(kids_age, 12))
             ─► lists/watched/collections (unchanged)
             ─► importRoles: kidsAge / pin / parent per profile, then importAdmin   (after the loop)
GET /api/profiles ─► listProfiles ─► toProfile (no hash, no salt)
```

Module dependencies (no runtime cycles): `store → profiles, roles-exchange, lists-exchange`;
`profiles → sync-record`; `sync-record → record-scalars, roles-record`;
`roles-record → record-scalars` (+ type-only `sync-record`); `merge → roles-merge → sync-record, tie-break`;
`roles-exchange → profiles, sync-record` (+ type-only `merge`, `roles-record`).

**Backwards compatibility.** Wire: new optional keys only; an older build drops them
(`parseRecord` keeps only what it knows) and a kid it writes (`kids: true`, no `kidsAge`)
merges to `{12, 0}`, older than any limit a parent sets. Database: additive columns; older
code still reads and writes its own columns. HTTP: `GET /api/profiles` gains fields; nothing
else changes in this phase.

## Interfaces

**Consumes:** contract §6, §7, §10. Nothing from other phases.

**Produces** (phase 02 relies on these exact signatures):

```ts
// web/src/state/profiles.ts
export interface Profile {
  id: string; name: string; createdAt: number; kids: boolean;
  kidsAge: 6 | 12 | null; parentId: string | null; admin: boolean; hasPin: boolean;
}
export interface ProfileRow {
  id: string; name: string; createdAt: number; kids: number; kidsAge: number | null;
  kidsAgeUpdatedAt: number; parentId: string | null; adminClaimedAt: number | null;
  pinHash: string | null; pinSalt: string | null; pinUpdatedAt: number;
}
export interface NewProfile { kids?: boolean }          // phase 02 adds kidsAge, parentId, pin, admin; writePin
export function cleanName(name: unknown): string | null;
export function profileRows(db: Database | null): ProfileRow[];
export function toProfile(row: ProfileRow): Profile;
export function listProfiles(db: Database | null): Profile[];
export function insertProfile(db: Database | null, name: unknown, role?: NewProfile): Profile | null;
export function deleteProfileById(db: Database | null, id: string): boolean;
export function profileExists(db: Database | null, id: string): boolean;
export function findOrCreateProfile(db: Database | null, name: string, displayName?: string, kids?: boolean):
  { id: string; created: boolean; kids: boolean } | null;

// web/src/state/store.ts — WatchState (unchanged names, new shapes)
profiles(): Profile[];  createProfile(name: unknown, kids?: boolean): Profile | null;
kids(): string[];  kidsFromSix(): string[];  setKids(setId: string, marked: boolean, age?: 6 | 12): void;

// web/src/state/sync-record.ts
ProfileState.admin?: { claimedAt: number };  ProfileState.kidsAge?: { age: 6 | 12; updatedAt: number };
ProfileState.parent?: string;  ProfileState.pin?: { hash: string; salt: string; updatedAt: number };
ListRow.age?: 6;

// web/src/state/roles-record.ts
export type RoleKeys = Pick<ProfileState, "admin" | "kids" | "kidsAge" | "parent" | "pin">;
export function parseRoleKeys(row: Record<string, unknown>): RoleKeys;
// web/src/state/roles-merge.ts
export type MergedRoles = Pick<ProfileState, "admin" | "kidsAge" | "parent" | "pin">;
export function roleMerger(): {
  see(viewer: string, profile: ProfileState, device: string): void;
  keysFor(viewer: string): MergedRoles;
};
// web/src/state/merge.ts:  export interface MergedProfile extends MergedRoles { … }
// web/src/state/roles-exchange.ts
export function exportRoles(db: Database | null): Map<string, RoleKeys>;
export function importRoles(db: Database, merged: MergedProfile[]): number;
```

For the core port (phase 04), behaviour the fixture alone does not pin:
- A Kids mark's `age: 6` lives on **live** marks only: export writes none on a tombstone, and
  parsing drops one a tombstone carries.
- Local Kids-mark writes (mark, age change, removal) stamp `max(now, stored + 1)`, as
  `setWatched`'s un-mark does (§7).
- `kidsAge.age` parses only the JSON numbers `6`/`12`; its `updatedAt`, `admin.claimedAt` and
  `pin.updatedAt` go through the same scalar coercion every other timestamp does.
- "A kid" for the merge's output rules and for admin candidacy is the same sticky test
  `merge.ts` uses for `kids`: any document calling the viewer a kid makes it one.
- Import's kids upgrade sets `kids_age = COALESCE(kids_age, 12)` so an upgraded grown-up never
  reads as a kid with no limit.

## Related code files

Modify
- `web/src/state/schema.ts` — v10 → v11 group
- `web/src/state/store.ts` — profile code out to `profiles.ts`; `setKids` age; `kidsFromSix`; export/import roles
- `web/src/state/sync-record.ts` — role keys and `ListRow.age` on the types; helpers out; `listRow(…, aged)`
- `web/src/state/merge.ts` — `MergedProfile extends MergedRoles`; `roleMerger` hook
- `web/src/state/lists-exchange.ts` — Kids mark `age` export/import
- `web/test/code-standards.test.ts` — `schema.ts` ceiling, dated
- `web/test/state-migration.test.ts` — v10 → v11 cases; profile shape at `:284`, `:318`
- `web/test/state-lists-sync.test.ts` — Kids mark age cases
- `web/test/shared-watch-state-fixtures.test.ts` — `profile-roles-merge.json` runner

Create
- `web/src/state/profiles.ts`, `web/src/state/record-scalars.ts`, `web/src/state/roles-record.ts`,
  `web/src/state/roles-merge.ts`, `web/src/state/roles-exchange.ts`
- `web/test/state-roles-record.test.ts`, `web/test/state-roles-merge.test.ts`, `web/test/state-roles-exchange.test.ts`
- `web/test/fixtures/watch-state/profile-roles-merge.json`

Delete: none.

## Implementation steps

All commands run from `/home/andre/Workspace/mediagram/web` unless shown otherwise. Line
numbers are as of `220fa71e`; match on the quoted text, not the number, once a file moves.

### Task 1 — Schema v11

**Files:** modify `web/src/state/schema.ts`, `web/test/state-migration.test.ts`, `web/test/code-standards.test.ts`.

- [ ] **Step 1.1: Write the failing test.** Append to `web/test/state-migration.test.ts`:

```ts
describe("v10 to v11", () => {
  test("an existing kid is FSK 12, a grown-up has no limit, and every hand mark is from 12", () => {
    const path = tempPath();
    atVersion(path, 10, () => [
      "INSERT INTO profiles(id, name, created_at, kids) VALUES ('p1', 'André', 1, 0)",
      "INSERT INTO profiles(id, name, created_at, kids) VALUES ('k1', 'TV kids', 2, 1)",
      "INSERT INTO kids(set_id, marked_at) VALUES ('01K', 3)",
    ]);

    new WatchState(path).close();

    const db = new Database(path);
    try {
      expect(
        db.query(
          `SELECT id, kids_age AS kidsAge, kids_age_updated_at AS kidsAgeUpdatedAt, parent_id AS parentId,
                  admin_claimed_at AS adminClaimedAt, pin_hash AS pinHash, pin_salt AS pinSalt,
                  pin_updated_at AS pinUpdatedAt
             FROM profiles ORDER BY created_at`,
        ).all(),
      ).toEqual([
        { id: "p1", kidsAge: null, kidsAgeUpdatedAt: 0, parentId: null, adminClaimedAt: null, pinHash: null, pinSalt: null, pinUpdatedAt: 0 },
        { id: "k1", kidsAge: 12, kidsAgeUpdatedAt: 0, parentId: null, adminClaimedAt: null, pinHash: null, pinSalt: null, pinUpdatedAt: 0 },
      ]);
      expect(db.query("SELECT set_id AS setId, age FROM kids").all()).toEqual([{ setId: "01K", age: null }]);
      expect(db.query("SELECT value FROM state_meta WHERE key = 'schema_version'").get()).toEqual({ value: "11" });
    } finally {
      db.close();
    }
  });

  test("a second open does not run it again", () => {
    const path = tempPath();
    atVersion(path, 10, () => ["INSERT INTO profiles(id, name, created_at, kids) VALUES ('k1', 'TV kids', 1, 1)"]);
    new WatchState(path).close();
    const first = new Database(path);
    first.query("UPDATE profiles SET kids_age = 6 WHERE id = 'k1'").run();
    first.close();

    // A replay of the group would put every kid back to 12.
    new WatchState(path).close();
    const again = new Database(path);
    try {
      expect(again.query("SELECT kids_age AS kidsAge FROM profiles").get()).toEqual({ kidsAge: 6 });
    } finally {
      again.close();
    }
  });
});
```

- [ ] **Step 1.2: Run it — expect failure**

```bash
bun test test/state-migration.test.ts -t "v10 to v11"
```

Expected: both fail with `SQLiteError: no such column: kids_age`.

- [ ] **Step 1.3: Add the group.** In `web/src/state/schema.ts`, after the v9 → v10 group's
  closing `],` (line 238) and before `];` (line 239), insert:

```ts

  // v10 -> v11: who may manage whom. A profile is still a kid or a grown-up;
  // a kid now carries its own limit (6 or 12) and the grown-up who made it,
  // one grown-up may be the household's admin, and a grown-up may hold a PIN.
  // Columns rather than tables, like `kids` in v7: each is one fact about one
  // profile. `parent_id` is deliberately not a foreign key — a parent removed
  // here must leave a kid another device still knows readable. Every kid that
  // exists was FSK 12 in code, so it says so now; every hand mark keeps
  // meaning "from 12", which is what a `NULL` age says.
  [
    `ALTER TABLE profiles ADD COLUMN kids_age INTEGER`,
    `ALTER TABLE profiles ADD COLUMN kids_age_updated_at INTEGER NOT NULL DEFAULT 0`,
    `ALTER TABLE profiles ADD COLUMN parent_id TEXT`,
    `ALTER TABLE profiles ADD COLUMN admin_claimed_at INTEGER`,
    `ALTER TABLE profiles ADD COLUMN pin_hash TEXT`,
    `ALTER TABLE profiles ADD COLUMN pin_salt TEXT`,
    `ALTER TABLE profiles ADD COLUMN pin_updated_at INTEGER NOT NULL DEFAULT 0`,
    `UPDATE profiles SET kids_age = 12 WHERE kids = 1`,
    `ALTER TABLE kids ADD COLUMN age INTEGER`,
  ],
```

- [ ] **Step 1.4: Run — expect pass, and the line limit to object**

```bash
bun test test/state-migration.test.ts && wc -l src/state/schema.ts && bun test test/code-standards.test.ts
```

Expected: migration tests pass; `264 src/state/schema.ts`; code-standards fails with
`src/state/schema.ts: 264 lines (limit 245)`.

- [ ] **Step 1.5: Raise the ceiling, dated.** In `web/test/code-standards.test.ts`, replace the
  comment's last lines (`:31-33`)

```ts
 * became one call into it. Lowered once more for `app.js`, when what plays
 * next and what the server preloads became one answer in
 * `lib/playback/plays-next.js`.
 */
```

with

```ts
 * became one call into it. Lowered once more for `app.js`, when what plays
 * next and what the server preloads became one answer in
 * `lib/playback/plays-next.js`. Raised 2026-09-28 for `src/state/schema.ts`,
 * by 19, for the v11 migration: a kid's own limit and parent, the
 * household's admin, and a grown-up's PIN. A migration is read top to bottom
 * as one list; moving one version into a second file would make that harder.
 */
```

and `"src/state/schema.ts": 245,` with `"src/state/schema.ts": 264,` (use the number `wc -l`
printed in 1.4 if it differs).

- [ ] **Step 1.6: Run — expect pass**

```bash
bun test test/state-migration.test.ts test/code-standards.test.ts test/state-metadata-failures.test.ts
```

Expected: all pass (`state-metadata-failures` reads `GROUPS.length`, now 11).

- [ ] **Step 1.7: Bump (phase-08 § Bumping B1–B4, **minor** — the feature's first commit) and commit**

```bash
cd /home/andre/Workspace/mediagram
git add web/src/state/schema.ts web/test/state-migration.test.ts web/test/code-standards.test.ts \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(web): profiles have room for a role, a PIN and a kid's own limit; release <next>"
```

### Task 2 — `profiles.ts`: one reader of the row, and a profile that says its role

**Files:** create `web/src/state/profiles.ts`, `web/test/state-roles-exchange.test.ts`; modify
`web/src/state/store.ts`, `web/test/state-migration.test.ts`.

- [ ] **Step 2.1: Write the failing test.** Create `web/test/state-roles-exchange.test.ts`:

```ts
/**
 * A profile's role in the store and on the sync record: what the page is told
 * about it, what a device writes, and what it takes in from the merge.
 */

import { afterEach, describe, expect, test } from "bun:test";
import { Database } from "bun:sqlite";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { WatchState } from "../src/state/store";

const HASH = "f377124b2c2ffeb096001d94cb0e3df86fbe20ad9981335bc624b960d4d80924";
const SALT = "00112233445566778899aabbccddeeff";

const dirs: string[] = [];
afterEach(() => {
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

/** A store, and the path a second connection reaches its file by. */
function store() {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-roles-"));
  dirs.push(dir);
  const path = join(dir, "state.db");
  return { state: new WatchState(path), path };
}

/** Sets columns no method here writes, through a second connection. */
function sql(path: string, write: (db: Database) => void): void {
  const db = new Database(path);
  try {
    write(db);
  } finally {
    db.close();
  }
}

describe("a profile as the page sees it", () => {
  test("a grown-up and a kid, as made", () => {
    const { state } = store();
    const me = state.createProfile("André")!;
    const mia = state.createProfile("Mia", true)!;
    expect(me).toEqual({
      id: me.id, name: "André", createdAt: me.createdAt,
      kids: false, kidsAge: null, parentId: null, admin: false, hasPin: false,
    });
    expect(mia).toEqual({
      id: mia.id, name: "Mia", createdAt: mia.createdAt,
      kids: true, kidsAge: 12, parentId: null, admin: false, hasPin: false,
    });
    expect(state.profiles()).toContainEqual(me);
    expect(state.profiles()).toContainEqual(mia);
    state.close();
  });

  test("says who is admin, who has a PIN, a kid's limit and parent — never the PIN itself", () => {
    const { state, path } = store();
    const me = state.createProfile("André")!;
    const mia = state.createProfile("Mia", true)!;
    sql(path, (db) => {
      db.query("UPDATE profiles SET admin_claimed_at = 500, pin_hash = ?2, pin_salt = ?3, pin_updated_at = 600 WHERE id = ?1")
        .run(me.id, HASH, SALT);
      db.query("UPDATE profiles SET kids_age = 6, parent_id = ?2 WHERE id = ?1").run(mia.id, me.id);
    });
    const byId = new Map(state.profiles().map((profile) => [profile.id, profile]));
    expect(byId.get(me.id)).toMatchObject({ admin: true, hasPin: true, kidsAge: null });
    expect(byId.get(mia.id)).toMatchObject({ kids: true, kidsAge: 6, parentId: me.id, admin: false, hasPin: false });
    const text = JSON.stringify(state.profiles());
    expect(text).not.toContain(HASH);
    expect(text).not.toContain(SALT);
    state.close();
  });
});
```

- [ ] **Step 2.2: Run it — expect failure**

```bash
bun test test/state-roles-exchange.test.ts
```

Expected: both fail — the profile has no `kidsAge`/`parentId`/`admin`/`hasPin`.

- [ ] **Step 2.3: Create `web/src/state/profiles.ts`**

```ts
/**
 * Who watches this library: the `profiles` rows, read and made.
 *
 * Kept out of `store.ts`, which carries every other read and write and sits
 * at its line limit, and because a profile is now more than a name: a kid
 * carries its own limit and the grown-up who made it, one grown-up may be
 * the household's admin, and a grown-up may hold a PIN. The page, the sync
 * exchange and the PIN-checked management all read the same row, so it is
 * read in one place and no two readers can disagree about a column.
 *
 * Every function takes the raw `Database | null` that `WatchState` holds; a
 * null database reads as nobody and makes nobody.
 */

import type { Database } from "bun:sqlite";
import { normalName } from "./sync-record";

/** A profile as the page sees it. Says whether there is a PIN, never what. */
export interface Profile {
  id: string;
  name: string;
  createdAt: number;
  /** Sees only what its own limit allows, and manages nothing. */
  kids: boolean;
  /** 6 or 12 on a kid; `null` on a grown-up. */
  kidsAge: 6 | 12 | null;
  /** The grown-up who made this kid. `null`, or one not here, is the admin's. */
  parentId: string | null;
  admin: boolean;
  hasPin: boolean;
}

/** Everything stored about a profile, the PIN included. Never sent as is. */
export interface ProfileRow {
  id: string;
  name: string;
  createdAt: number;
  kids: number;
  kidsAge: number | null;
  kidsAgeUpdatedAt: number;
  parentId: string | null;
  adminClaimedAt: number | null;
  pinHash: string | null;
  pinSalt: string | null;
  pinUpdatedAt: number;
}

/** What a new profile is, beside its name. */
export interface NewProfile {
  kids?: boolean;
}

/** How long a name may be. Long enough for a sentence, short enough to show. */
const MAX_NAME = 120;

/** A name with its edges trimmed, or `null` when there is nothing left. */
export function cleanName(name: unknown): string | null {
  if (typeof name !== "string") return null;
  const clean = name.trim().replace(/\s+/g, " ").slice(0, MAX_NAME);
  return clean === "" ? null : clean;
}

/** Every profile's row, oldest first. */
export function profileRows(db: Database | null): ProfileRow[] {
  if (!db) return [];
  return db
    .query(
      `SELECT id, name, created_at AS createdAt, kids, kids_age AS kidsAge,
              kids_age_updated_at AS kidsAgeUpdatedAt, parent_id AS parentId,
              admin_claimed_at AS adminClaimedAt, pin_hash AS pinHash, pin_salt AS pinSalt,
              pin_updated_at AS pinUpdatedAt
         FROM profiles ORDER BY created_at`,
    )
    .all() as ProfileRow[];
}

export function toProfile(row: ProfileRow): Profile {
  const kids = row.kids !== 0;
  return {
    id: row.id,
    name: row.name,
    createdAt: row.createdAt,
    kids,
    // A kid always has a limit, whatever the column holds: 12 is what every
    // kid saw before there was a choice.
    kidsAge: kids ? (row.kidsAge === 6 ? 6 : 12) : null,
    parentId: row.parentId,
    admin: row.adminClaimedAt !== null,
    hasPin: row.pinHash !== null,
  };
}

/** Who watches this library. Empty until someone says. */
export function listProfiles(db: Database | null): Profile[] {
  return profileRows(db).map(toProfile);
}

/**
 * Makes a profile. A kid nobody chose a limit for starts at FSK 12 dated 0 —
 * older than any limit a parent chooses, so the first real choice, made here
 * or synced in, wins.
 */
export function insertProfile(db: Database | null, name: unknown, role: NewProfile = {}): Profile | null {
  if (!db) return null;
  const clean = cleanName(name);
  if (clean === null) return null;
  const kids = role.kids === true;
  const createdAt = Date.now();
  const row: ProfileRow = {
    id: crypto.randomUUID(),
    name: clean,
    createdAt,
    kids: kids ? 1 : 0,
    kidsAge: kids ? 12 : null,
    kidsAgeUpdatedAt: 0,
    parentId: null,
    adminClaimedAt: null,
    pinHash: null,
    pinSalt: null,
    pinUpdatedAt: 0,
  };
  db.query(
    `INSERT INTO profiles(id, name, created_at, kids, kids_age, kids_age_updated_at, parent_id,
                          admin_claimed_at, pin_hash, pin_salt, pin_updated_at)
       VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)`,
  ).run(row.id, row.name, row.createdAt, row.kids, row.kidsAge, row.kidsAgeUpdatedAt, row.parentId,
    row.adminClaimedAt, row.pinHash, row.pinSalt, row.pinUpdatedAt);
  return toProfile(row);
}

/** Takes everything that was theirs with it — every table cascades. */
export function deleteProfileById(db: Database | null, id: string): boolean {
  if (!db) return false;
  return db.query("DELETE FROM profiles WHERE id = ?1").run(id).changes > 0;
}

export function profileExists(db: Database | null, id: string): boolean {
  if (!db) return false;
  return db.query("SELECT 1 FROM profiles WHERE id = ?1").get(id) !== null;
}

/**
 * This player's id for a viewer, made if it has never seen them.
 *
 * Made rather than skipped, because the first thing a second machine knows
 * about a viewer is a document written by the first — refusing to create
 * one would mean the sync could only ever flow towards a machine that had
 * already met them.
 */
export function findOrCreateProfile(
  db: Database | null,
  name: string,
  displayName?: string,
  kids = false,
): { id: string; created: boolean; kids: boolean } | null {
  const wanted = normalName(name);
  if (wanted === null) return null;
  const found = listProfiles(db).find((profile) => normalName(profile.name) === wanted);
  // Created from the spelling somebody typed, never from the normalised
  // identity — that would greet a viewer as "andré" on every new machine.
  if (found) return { id: found.id, created: false, kids: found.kids };
  const created = insertProfile(db, displayName ?? name, { kids });
  return created ? { id: created.id, created: true, kids } : null;
}
```

- [ ] **Step 2.4: Point `store.ts` at it.** In `web/src/state/store.ts`:

  1. Replace `import { normalName, SYNC_FORMAT, type SyncRecord } from "./sync-record";` (`:24`) with
     `import { SYNC_FORMAT, type SyncRecord } from "./sync-record";`, and after the
     `watched-exchange` import (`:34`) add:

```ts
import {
  cleanName,
  deleteProfileById,
  findOrCreateProfile,
  insertProfile,
  listProfiles,
  profileExists,
  type Profile,
} from "./profiles";
```

  2. Delete the `Profile` interface and the blank line after it (`:56-63`, from
     `export interface Profile {` to its closing `}`).
  3. Delete `MAX_NAME` with its doc comment and the blank line after (`:92-94`).
  4. Replace the bodies of `profiles`, `createProfile`, `deleteProfile` and `has` (`:122-159`;
     `renameProfile` stays exactly as it is) so the block reads:

```ts
  /** Who watches this library. Empty until someone says. */
  profiles(): Profile[] {
    return listProfiles(this.db);
  }

  createProfile(name: unknown, kids = false): Profile | null {
    return insertProfile(this.db, name, { kids });
  }

  renameProfile(id: string, name: unknown): boolean {
    if (!this.db) return false;
    const clean = cleanName(name);
    if (clean === null) return false;
    return this.db.query("UPDATE profiles SET name = ?2 WHERE id = ?1").run(id, clean).changes > 0;
  }

  /** Takes everything that was theirs with it — every table cascades. */
  deleteProfile(id: string): boolean {
    return deleteProfileById(this.db, id);
  }

  has(profileId: string): boolean {
    return profileExists(this.db, profileId);
  }
```

  5. In `importMerged`, replace
     `const matched = this.findOrCreateProfile(profile.name, profile.displayName, profile.kids === true);`
     with
     `const matched = findOrCreateProfile(this.db, profile.name, profile.displayName, profile.kids === true);`
  6. Delete the private `findOrCreateProfile` method with its doc comment (`:479-500`) and the
     blank line after it.
  7. Delete the module-level `cleanName` with its doc comment (`:719-724`) and the blank line after it.

- [ ] **Step 2.5: Update the two tests that pin the old shape.** In
  `web/test/state-migration.test.ts`, replace both occurrences (`:284`, `:318`) of

```ts
    expect(state.profiles()).toEqual([{ id: "p1", name: "André", createdAt: 1, kids: false }]);
```

with

```ts
    expect(state.profiles()).toEqual([{
      id: "p1", name: "André", createdAt: 1,
      kids: false, kidsAge: null, parentId: null, admin: false, hasPin: false,
    }]);
```

- [ ] **Step 2.6: Run — expect pass**

```bash
bun test test/state-roles-exchange.test.ts test/state-store.test.ts test/state-migration.test.ts \
  test/state-metadata-failures.test.ts test/state-http.test.ts test/state-sync.test.ts \
  test/state-two-machines.test.ts && bun run typecheck && wc -l src/state/store.ts src/state/profiles.ts
```

Expected: all pass; `store.ts` 753, `profiles.ts` 164 (both under their limits).

- [ ] **Step 2.7: Bump (§ Bumping, patch) and commit**

```bash
cd /home/andre/Workspace/mediagram
git add web/src/state/profiles.ts web/src/state/store.ts web/test/state-roles-exchange.test.ts \
  web/test/state-migration.test.ts \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(web): a profile says whether it is the admin, has a PIN, and its kid's limit; release <next>"
```

### Task 3 — The sync record reads a profile's role and a Kids mark's age

**Files:** create `web/src/state/record-scalars.ts`, `web/src/state/roles-record.ts`,
`web/test/state-roles-record.test.ts`; modify `web/src/state/sync-record.ts`.

- [ ] **Step 3.1: Write the failing test.** Create `web/test/state-roles-record.test.ts`:

```ts
/** Covers reading a profile's role keys, and a Kids mark's age, off another device's document. */

import { describe, expect, test } from "bun:test";
import { parseRecord } from "../src/state/sync-record";

const HASH = "f377124b2c2ffeb096001d94cb0e3df86fbe20ad9981335bc624b960d4d80924";
const SALT = "00112233445566778899aabbccddeeff";

/** A document with one profile, `Mia`, carrying `extra`, and `top` beside it. */
const withProfile = (extra: Record<string, unknown>, top: Record<string, unknown> = {}) =>
  parseRecord(JSON.stringify({
    format: 1,
    device: "laptop",
    writtenAt: 1,
    profiles: [{ name: "Mia", progress: [], watched: [], ...extra }],
    ...top,
  }))!;

describe("a profile's role keys", () => {
  test("read back as written", () => {
    const [profile] = withProfile({
      admin: { claimedAt: 1000 },
      kids: true,
      kidsAge: { age: 6, updatedAt: 0 },
      parent: "André",
      pin: { hash: HASH, salt: SALT, updatedAt: 2000 },
    }).profiles;
    expect(profile).toMatchObject({
      admin: { claimedAt: 1000 },
      kids: true,
      kidsAge: { age: 6, updatedAt: 0 },
      parent: "André",
      pin: { hash: HASH, salt: SALT, updatedAt: 2000 },
    });
  });

  test("an absent key is nothing said, not an empty one", () => {
    const [profile] = withProfile({}).profiles;
    for (const key of ["admin", "kids", "kidsAge", "parent", "pin"]) expect(key in profile!).toBe(false);
  });

  test("a malformed key is dropped on its own; the profile and its rows survive", () => {
    const bad: Record<string, unknown>[] = [
      { admin: { claimedAt: 0 } },
      { admin: { claimedAt: "soon" } },
      { admin: true },
      { kids: "yes" },
      { kidsAge: { age: 9, updatedAt: 1 } },
      { kidsAge: { age: "6", updatedAt: 1 } },
      { kidsAge: { age: 6, updatedAt: -1 } },
      { parent: "" },
      { parent: 7 },
      { pin: { hash: HASH.toUpperCase(), salt: SALT, updatedAt: 1 } },
      { pin: { hash: HASH, salt: "abc", updatedAt: 1 } },
      { pin: { hash: HASH, salt: SALT, updatedAt: 0 } },
    ];
    for (const extra of bad) {
      const [profile] = withProfile({ ...extra, progress: [{ setId: "01A", at: 5, updatedAt: 1 }] }).profiles;
      expect(profile!.name).toBe("Mia");
      expect(profile!.progress).toHaveLength(1);
      for (const key of Object.keys(extra)) expect(key in profile!).toBe(false);
    }
  });

  test("one bad key does not cost a good one beside it", () => {
    const [profile] = withProfile({ admin: { claimedAt: -5 }, kidsAge: { age: 12, updatedAt: 3 } }).profiles;
    expect("admin" in profile!).toBe(false);
    expect(profile!.kidsAge).toEqual({ age: 12, updatedAt: 3 });
  });
});

describe("a Kids mark's age", () => {
  test("only the literal 6 on a live mark is kept; anything else reads as from 12", () => {
    const record = withProfile({}, {
      kids: [
        { setId: "K6", updatedAt: 1, age: 6 },
        { setId: "K12", updatedAt: 1, age: 12 },
        { setId: "KS", updatedAt: 1, age: "6" },
        { setId: "KN", updatedAt: 1 },
        { setId: "KR", updatedAt: 1, removed: true, age: 6 },
      ],
    });
    expect(record.kids).toEqual([
      { setId: "K6", updatedAt: 1, age: 6 },
      { setId: "K12", updatedAt: 1 },
      { setId: "KS", updatedAt: 1 },
      { setId: "KN", updatedAt: 1 },
      { setId: "KR", updatedAt: 1, removed: true },
    ]);
  });

  test("a watchlist row and an editor's choice never carry one", () => {
    const record = withProfile(
      { watchlist: [{ setId: "W", updatedAt: 1, age: 6 }] },
      { editorsChoice: [{ setId: "E", updatedAt: 1, age: 6 }] },
    );
    expect(record.profiles[0]!.watchlist).toEqual([{ setId: "W", updatedAt: 1 }]);
    expect(record.editorsChoice).toEqual([{ setId: "E", updatedAt: 1 }]);
  });
});
```

- [ ] **Step 3.2: Run it — expect failure**

```bash
bun test test/state-roles-record.test.ts
```

Expected: "read back as written", "one bad key…" and "only the literal 6 on a live mark…" fail
(the keys are dropped); the others pass vacuously.

- [ ] **Step 3.3: Move the readers out.** Create `web/src/state/record-scalars.ts`:

```ts
/**
 * The readers every value in another device's document goes through.
 *
 * Moved out of `sync-record.ts` so that it and `roles-record.ts`, which each
 * read part of one document, read a value the same way — one idea of what a
 * number or a piece of text is, not two that could drift apart.
 */

export function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

export function objectRow(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

/** Older documents allow numeric strings and other primitives. Objects and
 * arrays are not numbers; coercing a JSON object's own `toString` can throw. */
export function numberFromScalar(value: unknown): number {
  return value !== null && typeof value === "object" ? Number.NaN : Number(value);
}

/** A non-empty string, trimmed — the only kind of text worth keeping here. */
export function text_(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const clean = value.trim();
  return clean === "" ? null : clean;
}
```

- [ ] **Step 3.4: Create `web/src/state/roles-record.ts`**

```ts
/**
 * A profile's role, read off another device's document: the admin claim, a
 * kid's limit and parent, a grown-up's PIN — and `kids`, which came first.
 *
 * Split out of `sync-record.ts` to keep it under the line limit;
 * `parseRecord` is the only caller. As hostile as the rest of that file: each
 * key is checked on its own and a bad one is dropped on its own, never the
 * profile, and an absent key is "nothing said" — exactly what a document
 * written before these keys existed says.
 */

import { numberFromScalar, objectRow, text_ } from "./record-scalars";
import type { ProfileState } from "./sync-record";

/** The keys of a profile's entry that say who it is, not what it watched. */
export type RoleKeys = Pick<ProfileState, "admin" | "kids" | "kidsAge" | "parent" | "pin">;

const HASH = /^[0-9a-f]{64}$/;
const SALT = /^[0-9a-f]{32}$/;

export function parseRoleKeys(row: Record<string, unknown>): RoleKeys {
  const keys: RoleKeys = {};

  const claimedAt = numberFromScalar(objectRow(row.admin)?.claimedAt);
  if (Number.isFinite(claimedAt) && claimedAt > 0) keys.admin = { claimedAt };

  // Only a literal `true`: a flag that restricts what a child sees must not
  // be switched on by a string that merely looks truthy.
  if (row.kids === true) keys.kids = true;

  // The age is a literal too, for the same reason: a limit is not guessed at.
  const limit = objectRow(row.kidsAge);
  const age = limit?.age;
  const limitAt = numberFromScalar(limit?.updatedAt);
  if ((age === 6 || age === 12) && Number.isFinite(limitAt) && limitAt >= 0) {
    keys.kidsAge = { age, updatedAt: limitAt };
  }

  const parent = text_(row.parent);
  if (parent !== null) keys.parent = parent;

  const pin = objectRow(row.pin);
  const hash = pin?.hash;
  const salt = pin?.salt;
  const pinAt = numberFromScalar(pin?.updatedAt);
  if (typeof hash === "string" && HASH.test(hash) && typeof salt === "string" && SALT.test(salt) &&
    Number.isFinite(pinAt) && pinAt > 0) {
    keys.pin = { hash, salt, updatedAt: pinAt };
  }
  return keys;
}
```

- [ ] **Step 3.5: Wire it into `web/src/state/sync-record.ts`**

  1. Between the header comment's closing `*/` (`:54`) and `/** Bumped when…` (`:56`), insert:

```ts

import { asArray, numberFromScalar, objectRow, text_ } from "./record-scalars";
import { parseRoleKeys } from "./roles-record";
```

  2. In `ListRow` (`:93-98`), after `removed?: true;` add:

```ts
  /** A Kids mark only: marked "from 6". Absent is from 12, as every mark was
   * before ages — so an older reader, dropping this, still reads 12. */
  age?: 6;
```

  3. In `ProfileState`, after `kids?: true;` (`:119`) add:

```ts
  /** The household's one admin, and when it was claimed — the earliest claim
   * anywhere wins; see `roles-merge.ts`. */
  admin?: { claimedAt: number };
  /** A kid's own limit and when it last changed; only ever on a kid. */
  kidsAge?: { age: 6 | 12; updatedAt: number };
  /** The grown-up who made this kid, by the name sync knows a viewer by. */
  parent?: string;
  /** A grown-up's PIN, salted and hashed. This document is the only place
   * the hash leaves the store. */
  pin?: { hash: string; salt: string; updatedAt: number };
```

  4. In `parseRecord`, replace the three lines (`:191-193`)

```ts
      // Only a literal `true`: a flag that restricts what a child sees must
      // not be switched on by a string that merely looks truthy.
      ...(row.kids === true ? { kids: true as const } : {}),
```

     with

```ts
      ...parseRoleKeys(row),
```

  5. Replace `kids: held.kids === undefined ? undefined : parseListRows(held.kids),` (`:213`) with
     `kids: held.kids === undefined ? undefined : parseListRows(held.kids, true),`
  6. Replace `parseListRows` and `listRow` (`:264-278`) with:

```ts
function parseListRows(value: unknown, aged = false): ListRow[] {
  return asArray(value).flatMap((entry) => {
    const row = listRow(entry, aged);
    return row === null ? [] : [row];
  });
}

/** `aged` for a Kids mark: only the literal 6 on a live mark is an age —
 * anything else is 12, and a removal has none. */
function listRow(value: unknown, aged = false): ListRow | null {
  const raw = objectRow(value);
  if (raw === null) return null;
  const setId = text_(raw.setId);
  const updatedAt = numberFromScalar(raw.updatedAt);
  if (setId === null || !Number.isFinite(updatedAt) || updatedAt <= 0) return null;
  const row: ListRow = raw.removed === true ? { setId, updatedAt, removed: true } : { setId, updatedAt };
  return aged && raw.removed !== true && raw.age === 6 ? { ...row, age: 6 } : row;
}
```

  7. Delete the blank line after `collectionRow`'s closing `}` and everything after it — the
     four helpers `asArray`, `objectRow`, `numberFromScalar`, `text_` (`:306-328`), now in
     `record-scalars.ts`.

- [ ] **Step 3.6: Run — expect pass**

```bash
bun test test/state-roles-record.test.ts test/state-sync-record.test.ts test/shared-watch-state-fixtures.test.ts \
  test/state-lists-sync.test.ts && bun run typecheck && wc -l src/state/sync-record.ts
```

Expected: all pass; `sync-record.ts` 322 (ceiling 328).

- [ ] **Step 3.7: Bump (§ Bumping, patch) and commit**

```bash
cd /home/andre/Workspace/mediagram
git add web/src/state/record-scalars.ts web/src/state/roles-record.ts web/src/state/sync-record.ts \
  web/test/state-roles-record.test.ts \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(web): the sync record reads a profile's role and a Kids mark's age; release <next>"
```

### Task 4 — Devices agree on one admin, a kid's limit, its parent and a PIN

**Files:** create `web/src/state/roles-merge.ts`, `web/test/state-roles-merge.test.ts`; modify `web/src/state/merge.ts`.

- [ ] **Step 4.1: Write the failing test.** Create `web/test/state-roles-merge.test.ts`:

```ts
/** Covers merging a profile's role across devices: a kid's limit, a PIN, a parent, the one admin. */

import { describe, expect, test } from "bun:test";
import { mergeStates } from "../src/state/merge";
import type { ProfileState, SyncRecord } from "../src/state/sync-record";

const HASH_A = "f377124b2c2ffeb096001d94cb0e3df86fbe20ad9981335bc624b960d4d80924";
const HASH_B = "48618b45393cde1c8841b73b2ca684f9b39a2efcbf35d4bf602d7f908b5137f8";
const SALT = "00112233445566778899aabbccddeeff";

/** One device's document; every profile is `Mia` unless it says otherwise. */
const doc = (device: string, ...profiles: Partial<ProfileState>[]): SyncRecord => ({
  format: 1,
  device,
  writtenAt: 0,
  profiles: profiles.map((one) => ({ name: "Mia", progress: [], watched: [], ...one })),
});

/** The merged viewer `name`, checked to be the same whichever order the documents came in. */
function merged(records: SyncRecord[], name = "mia") {
  const forward = mergeStates(records).profiles.find((profile) => profile.name === name);
  const backward = mergeStates([...records].reverse()).profiles.find((profile) => profile.name === name);
  expect(backward).toEqual(forward);
  return forward!;
}

describe("a kid's limit", () => {
  test("the newer change wins, whichever device made it", () => {
    expect(merged([
      doc("laptop", { kids: true, kidsAge: { age: 12, updatedAt: 1000 } }),
      doc("tv", { kids: true, kidsAge: { age: 6, updatedAt: 2000 } }),
    ]).kidsAge).toEqual({ age: 6, updatedAt: 2000 });
  });

  test("a tie breaks by device id, the same way on every machine", () => {
    expect(merged([
      doc("laptop", { kids: true, kidsAge: { age: 6, updatedAt: 1000 } }),
      doc("tv", { kids: true, kidsAge: { age: 12, updatedAt: 1000 } }),
    ]).kidsAge).toEqual({ age: 12, updatedAt: 1000 });
  });

  test("an older build's kid, with no limit anywhere, is FSK 12 at time 0", () => {
    expect(merged([doc("old-tv", { kids: true })]).kidsAge).toEqual({ age: 12, updatedAt: 0 });
  });

  test("and loses to a limit a parent set", () => {
    expect(merged([
      doc("old-tv", { kids: true }),
      doc("laptop", { kids: true, kidsAge: { age: 6, updatedAt: 5 } }),
    ]).kidsAge).toEqual({ age: 6, updatedAt: 5 });
  });

  test("is never said of a grown-up", () => {
    expect("kidsAge" in merged([doc("laptop", { kidsAge: { age: 6, updatedAt: 5 } })])).toBe(false);
  });
});

describe("a grown-up's PIN", () => {
  test("the newer one wins", () => {
    expect(merged([
      doc("laptop", { name: "André", pin: { hash: HASH_A, salt: SALT, updatedAt: 1000 } }),
      doc("tv", { name: "André", pin: { hash: HASH_B, salt: SALT, updatedAt: 2000 } }),
    ], "andré").pin).toEqual({ hash: HASH_B, salt: SALT, updatedAt: 2000 });
  });

  test("is never carried by a kid", () => {
    expect("pin" in merged([doc("laptop", { kids: true, pin: { hash: HASH_A, salt: SALT, updatedAt: 1 } })])).toBe(false);
  });
});

describe("a kid's parent", () => {
  test("is the parent's normalised name", () => {
    expect(merged([doc("laptop", { kids: true, parent: " André " })]).parent).toBe("andré");
  });

  test("two documents disagreeing settle by device id", () => {
    expect(merged([
      doc("laptop", { kids: true, parent: "André" }),
      doc("tv", { kids: true, parent: "Maja" }),
    ]).parent).toBe("maja");
  });

  test("a document that says nothing does not unsay it", () => {
    expect(merged([doc("zz-old", { kids: true }), doc("laptop", { kids: true, parent: "André" })]).parent).toBe("andré");
  });

  test("is never said of a grown-up", () => {
    expect("parent" in merged([doc("laptop", { name: "André", parent: "Maja" })], "andré")).toBe(false);
  });
});

describe("the household's admin", () => {
  test("two devices claiming before they meet: the earliest claim is the only admin", () => {
    const profiles = mergeStates([
      doc("laptop", { name: "André", admin: { claimedAt: 2000 } }),
      doc("tv", { name: "Maja", admin: { claimedAt: 1000 } }),
    ]).profiles;
    expect(profiles.find((profile) => profile.name === "maja")!.admin).toEqual({ claimedAt: 1000 });
    expect("admin" in profiles.find((profile) => profile.name === "andré")!).toBe(false);
  });

  test("a tie goes to the smaller name", () => {
    const profiles = mergeStates([
      doc("laptop", { name: "Maja", admin: { claimedAt: 1000 } }),
      doc("tv", { name: "André", admin: { claimedAt: 1000 } }),
    ]).profiles;
    expect(profiles.filter((profile) => profile.admin).map((profile) => profile.name)).toEqual(["andré"]);
  });

  test("one viewer claimed on two devices keeps its earliest claim", () => {
    expect(merged([
      doc("laptop", { name: "André", admin: { claimedAt: 3000 } }),
      doc("tv", { name: "André", admin: { claimedAt: 1000 } }),
    ], "andré").admin).toEqual({ claimedAt: 1000 });
  });

  test("nobody claimed is no admin at all", () => {
    expect(mergeStates([doc("laptop", { name: "André" })]).profiles.some((profile) => profile.admin)).toBe(false);
  });

  test("a claim on a viewer any device calls a kid is ignored, however early", () => {
    const profiles = mergeStates([
      doc("laptop", { name: "Mia", admin: { claimedAt: 500 } }),
      doc("tv", { name: "Mia", kids: true }, { name: "André", admin: { claimedAt: 1000 } }),
    ]).profiles;
    expect(profiles.filter((profile) => profile.admin).map((profile) => profile.name)).toEqual(["andré"]);
  });
});
```

- [ ] **Step 4.2: Run it — expect failure**

```bash
bun test test/state-roles-merge.test.ts
```

Expected: every test that expects a role key fails (`received: undefined`); the
"never …" and "nobody claimed" tests pass vacuously.

- [ ] **Step 4.3: Create `web/src/state/roles-merge.ts`**

```ts
/**
 * A viewer's role, reconciled across devices: a kid's limit, a grown-up's
 * PIN, who made a kid, and which one viewer is the household's admin. Split
 * out of `merge.ts` to keep it under the line limit; `mergeStates` is the
 * only caller.
 *
 * **A limit and a PIN: the newest `updatedAt` wins.** Not sticky the way
 * `kids` is — a parent lowers a limit as often as it raises one, and an admin
 * resets a PIN. Ties break by device id through `keep`, as every row's do.
 *
 * **A kid nobody gave a limit is FSK 12 at time 0.** That is what a document
 * from before limits says by saying only `kids: true`, and 0 is older than
 * any real change, so the first limit a parent sets wins wherever it lands.
 *
 * **A parent is set once, and only a kid has one.** Two documents naming
 * different parents is a bug, not a case, but it is still settled the way
 * `displayName` is — by device id — so the answer never depends on the order
 * documents arrive in.
 *
 * **One admin, household-wide, and a grown-up.** The earliest claim wins,
 * ties by the smaller name, and only that viewer carries the key: however
 * devices wake up, the merge never names two. A claim on a viewer any
 * document calls a kid is ignored — a kid manages nothing.
 *
 * "A kid" here is the sticky test `merge.ts` applies to `kids`: once any
 * device's document says so, no document lacking the flag undoes it.
 */

import { normalName, type ProfileState } from "./sync-record";
import { keep, type Held } from "./tie-break";

type KidsAge = NonNullable<ProfileState["kidsAge"]>;
type Pin = NonNullable<ProfileState["pin"]>;

/** The role keys a merged profile carries beside `kids`. */
export type MergedRoles = Pick<ProfileState, "admin" | "kidsAge" | "parent" | "pin">;

const FROM_TWELVE: KidsAge = { age: 12, updatedAt: 0 };

/** Gathers every document's role keys, then answers per viewer. */
export function roleMerger() {
  const kids = new Set<string>();
  const limits = new Map<string, Held<KidsAge>>();
  const pins = new Map<string, Held<Pin>>();
  const parents = new Map<string, { name: string; from: string }>();
  const claims = new Map<string, number>();
  let admin: string | null | undefined;

  return {
    /** One device's entry for `viewer`, a normalised name. */
    see(viewer: string, profile: ProfileState, device: string): void {
      if (profile.kids === true) kids.add(viewer);
      if (profile.kidsAge) keep(limits, viewer, profile.kidsAge, device);
      if (profile.pin) keep(pins, viewer, profile.pin, device);
      const parent = normalName(profile.parent);
      const standing = parents.get(viewer);
      if (parent !== null && (standing === undefined || device > standing.from)) {
        parents.set(viewer, { name: parent, from: device });
      }
      const claimed = profile.admin?.claimedAt;
      if (claimed !== undefined && claimed < (claims.get(viewer) ?? Infinity)) claims.set(viewer, claimed);
    },

    /** `viewer`'s role keys — asked only once every document has been seen. */
    keysFor(viewer: string): MergedRoles {
      if (admin === undefined) admin = earliest(claims, kids);
      const kid = kids.has(viewer);
      const parent = parents.get(viewer)?.name;
      const pin = pins.get(viewer)?.row;
      return {
        ...(admin === viewer ? { admin: { claimedAt: claims.get(viewer)! } } : {}),
        ...(kid ? { kidsAge: limits.get(viewer)?.row ?? FROM_TWELVE } : {}),
        ...(kid && parent !== undefined ? { parent } : {}),
        ...(!kid && pin !== undefined ? { pin } : {}),
      };
    },
  };
}

/** The grown-up with the earliest claim; a tie goes to the smaller name. */
function earliest(claims: Map<string, number>, kids: Set<string>): string | null {
  let best: { viewer: string; at: number } | null = null;
  for (const [viewer, at] of claims) {
    if (kids.has(viewer)) continue;
    if (best === null || at < best.at || (at === best.at && viewer < best.viewer)) best = { viewer, at };
  }
  return best?.viewer ?? null;
}
```

- [ ] **Step 4.4: Hook it into `web/src/state/merge.ts`**

  1. After `import { keep, type Held } from "./tie-break";` (`:42`) add
     `import { roleMerger, type MergedRoles } from "./roles-merge";`
  2. Replace (`:44-45`)

```ts
/** Everything the devices agree on, once they have been reconciled. */
export interface MergedProfile {
```

     with

```ts
/** Everything the devices agree on, once they have been reconciled. Its role
 * keys — `admin`, `kidsAge`, `parent`, `pin` — are `roles-merge.ts`'s. */
export interface MergedProfile extends MergedRoles {
```

  3. After `const editorsChoice = new Map<string, Held<ListRow>>();` (`:105`) add:

```ts
  // A viewer's role, and the one admin across all of them: `roles-merge.ts`.
  const roles = roleMerger();
```

  4. After `if (profile.kids === true) held.kids = true;` (`:139`) add
     `      roles.see(name, profile, device);`
  5. After `...(held.kids ? { kids: true as const } : {}),` (`:171`) add
     `      ...roles.keysFor(name),`

- [ ] **Step 4.5: Run — expect pass**

```bash
bun test test/state-roles-merge.test.ts test/state-merge.test.ts test/shared-watch-state-fixtures.test.ts \
  test/state-sync-import-ties.test.ts && bun run typecheck && wc -l src/state/merge.ts src/state/roles-merge.ts
```

Expected: all pass; `merge.ts` 190, `roles-merge.ts` 88.

- [ ] **Step 4.6: Bump (§ Bumping, patch) and commit**

```bash
cd /home/andre/Workspace/mediagram
git add web/src/state/roles-merge.ts web/src/state/merge.ts web/test/state-roles-merge.test.ts \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(web): devices agree on one admin, a kid's limit, its parent and a PIN; release <next>"
```

### Task 5 — A Kids mark can be from 6, here and on the wire

**Files:** modify `web/src/state/store.ts`, `web/src/state/lists-exchange.ts`, `web/test/state-lists-sync.test.ts`.

- [ ] **Step 5.1: Write the failing test.** Append to `web/test/state-lists-sync.test.ts`:

```ts
describe("a Kids mark's age", () => {
  test("a mark is from 12 unless it says 6, and changing it moves its clock", () => {
    const state = new WatchState(tempPath());
    state.setKids("01A", true);
    state.setKids("01B", true, 6);
    expect([...state.kids()].sort()).toEqual(["01A", "01B"]);
    expect(state.kidsFromSix()).toEqual(["01B"]);

    const before = state.exportRecord("laptop").kids!.find((row) => row.setId === "01A")!;
    Bun.sleepSync(2);
    state.setKids("01A", true, 6);
    const after = state.exportRecord("laptop").kids!.find((row) => row.setId === "01A")!;
    expect(after.age).toBe(6);
    expect(after.updatedAt).toBeGreaterThan(before.updatedAt);
    expect([...state.kidsFromSix()].sort()).toEqual(["01A", "01B"]);
    state.close();
  });

  test("marking again at the age it has changes nothing", () => {
    const state = new WatchState(tempPath());
    state.setKids("01A", true, 6);
    const before = state.exportRecord("laptop").kids![0];
    Bun.sleepSync(2);
    state.setKids("01A", true, 6);
    expect(state.exportRecord("laptop").kids![0]).toEqual(before);
    state.close();
  });

  test("the wire says 6 on a live mark only — never 12, a tombstone or the editor's choice", () => {
    const state = new WatchState(tempPath());
    state.setKids("K6", true, 6);
    state.setKids("K12", true, 12);
    state.setKids("GONE", true, 6);
    state.setKids("GONE", false);
    state.setEditorsChoice("E", true);
    const record = state.exportRecord("laptop");
    const byId = new Map(record.kids!.map((row) => [row.setId, row]));
    expect(byId.get("K6")!.age).toBe(6);
    expect("age" in byId.get("K12")!).toBe(false);
    expect(byId.get("GONE")).toMatchObject({ removed: true });
    expect("age" in byId.get("GONE")!).toBe(false);
    expect("age" in record.editorsChoice![0]!).toBe(false);
    state.close();
  });

  test("a mark moved from 12 to 6 on one machine arrives as 6 on the other", () => {
    const laptop = new WatchState(tempPath());
    const tv = new WatchState(tempPath());
    const wire = (state: WatchState, device: string) => parseRecord(JSON.stringify(state.exportRecord(device)))!;
    laptop.setKids("01A", true);
    tv.importMerged(mergeStates([wire(tv, "tv"), wire(laptop, "laptop")]));
    expect(tv.kidsFromSix()).toEqual([]);

    Bun.sleepSync(2);
    laptop.setKids("01A", true, 6);
    expect(tv.importMerged(mergeStates([wire(tv, "tv"), wire(laptop, "laptop")]))).toBe(1);
    expect(tv.kidsFromSix()).toEqual(["01A"]);
    laptop.close();
    tv.close();
  });

  test("an equal-time winner with a different age is taken, as any tie the merge broke", () => {
    const state = new WatchState(tempPath());
    state.importMerged({ profiles: [], kids: [{ setId: "01A", updatedAt: 1000 }] });
    expect(state.importMerged({ profiles: [], kids: [{ setId: "01A", updatedAt: 1000, age: 6 }] })).toBe(1);
    expect(state.kidsFromSix()).toEqual(["01A"]);
    expect(state.importMerged({ profiles: [], kids: [{ setId: "01A", updatedAt: 1000, age: 6 }] })).toBe(0);
    state.close();
  });

  test("a change here outdates what another device's clock stamped, however far ahead", () => {
    const state = new WatchState(tempPath());
    const ahead = Date.now() + 60_000;
    state.importMerged({ profiles: [], kids: [{ setId: "01A", updatedAt: ahead }] });
    state.setKids("01A", true, 6);
    const moved = state.exportRecord("laptop").kids![0]!;
    expect(moved.age).toBe(6);
    expect(moved.updatedAt).toBeGreaterThan(ahead);
    state.setKids("01A", false);
    const removed = state.exportRecord("laptop").kids![0]!;
    expect(removed.removed).toBe(true);
    expect(removed.updatedAt).toBeGreaterThan(moved.updatedAt);
    state.close();
  });

  test("its own document, a removed mark from 6 included, imports as no change", () => {
    const state = new WatchState(tempPath());
    state.setKids("LIVE", true, 6);
    state.setKids("GONE", true, 6);
    state.setKids("GONE", false);
    expect(state.importMerged(mergeStates([state.exportRecord("self")]))).toBe(0);
    state.close();
  });
});
```

- [ ] **Step 5.2: Run it — expect failure**

```bash
bun test test/state-lists-sync.test.ts -t "a Kids mark's age"
```

Expected: fails with `state.kidsFromSix is not a function` (and `age` undefined).

- [ ] **Step 5.3: `setKids` with an age, and `kidsFromSix`.** In `web/src/state/store.ts`, replace
  `setKids` with its two-line doc comment (`:513-529`) by:

```ts
  /**
   * Marks `setId` for Kids — "from 6", or from 12 as every mark was before
   * ages — or takes the mark off. A removal is a tombstone, not a delete: the
   * same reason and shape as `setWatchlisted`. Changing a live mark's age is
   * a new mark, so another device hears of it; marking it again at the age it
   * already has changes nothing. Both directions are dated at least a
   * millisecond past what they replace, as `setWatched` is: a mark imported
   * here can carry another device's clock, and this one running behind must
   * not write a change that loses to the value it replaced.
   */
  setKids(setId: string, marked: boolean, age: 6 | 12 = 12): void {
    if (marked) {
      tolerate(() =>
        this.db
          ?.query(
            `INSERT INTO kids(set_id, marked_at, removed_at, age) VALUES (?1, ?2, NULL, ?3)
               ON CONFLICT(set_id) DO UPDATE SET
                 marked_at = MAX(excluded.marked_at, COALESCE(removed_at, marked_at) + 1),
                 removed_at = NULL, age = excluded.age
                 WHERE removed_at IS NOT NULL OR age IS NOT excluded.age`,
          )
          .run(setId, Date.now(), age === 6 ? 6 : null),
      );
    } else {
      this.db
        ?.query("UPDATE kids SET removed_at = MAX(?2, marked_at + 1) WHERE set_id = ?1 AND removed_at IS NULL")
        .run(setId, Date.now());
    }
  }

  /** The live marks made "from 6" — a subset of `kids()`, which is every live mark. */
  kidsFromSix(): string[] {
    return this.setIds("SELECT set_id AS setId FROM kids WHERE removed_at IS NULL AND age = 6 ORDER BY marked_at DESC");
  }
```

- [ ] **Step 5.4: The age on the wire.** In `web/src/state/lists-exchange.ts`:

  1. After `export type TitleMarkTable = "kids" | "editors_choice";` (`:32`) add:

```ts

/** Only a Kids mark has an age; the editor's choice reads as having none. */
const ageOf = (table: TitleMarkTable) => (table === "kids" ? "age" : "NULL");
```

  2. Replace `exportTitleMarks` (`:34-42`, doc comment included) with:

```ts
/** A table's marks, tombstones included — everything the wire needs to say.
 * `kids()` on `WatchState` is the live-only half of this. "From 6" rides on
 * a live mark; from 12 is said by saying nothing, which is all an older
 * reader, dropping the key, will ever hear. */
export function exportTitleMarks(db: Database | null, table: TitleMarkTable): ListRow[] {
  if (!db) return [];
  const rows = db
    .query(`SELECT set_id AS setId, marked_at AS markedAt, removed_at AS removedAt, ${ageOf(table)} AS age FROM ${table}`)
    .all() as { setId: string; markedAt: number; removedAt: number | null; age: number | null }[];
  return rows.map((row) => {
    const wire = toListRow(row.setId, row.markedAt, row.removedAt);
    return row.age === 6 && !wire.removed ? { ...wire, age: 6 as const } : wire;
  });
}
```

  3. In `importTitleMarks`, replace the `standing` read and the skip test (`:81-87`) with:

```ts
    const standing = db
      .query(
        `SELECT removed_at AS removedAt, ${ageOf(table)} AS age, CASE WHEN removed_at IS NOT NULL THEN removed_at ELSE marked_at END AS updatedAt FROM ${table} WHERE set_id = ?1`,
      )
      .get(row.setId) as { updatedAt: number; removedAt: number | null; age: number | null } | null;
    // A tombstone's age is not compared: the column can still hold the one it
    // had while live, and a removal says nothing about it.
    if (standing !== null && (standing.updatedAt > row.updatedAt ||
      (standing.updatedAt === row.updatedAt && (standing.removedAt !== null) === !!row.removed &&
        (!!row.removed || (standing.age === 6) === (row.age === 6))))) continue;
```

  4. In the live branch, after the `INSERT … removed_at = NULL` statement's `.run(row.setId, row.updatedAt);` (`:98`), add:

```ts
      if (table === "kids") db.query("UPDATE kids SET age = ?2 WHERE set_id = ?1").run(row.setId, row.age === 6 ? 6 : null);
```

- [ ] **Step 5.5: Run — expect pass**

```bash
bun test test/state-lists-sync.test.ts test/state-store.test.ts test/state-sync-import-ties.test.ts \
  test/state-sync.test.ts && bun run typecheck && wc -l src/state/store.ts src/state/lists-exchange.ts
```

Expected: all pass (`state-store.test.ts:316-320` "marking twice marks once" still holds);
`store.ts` 770, `lists-exchange.ts` 190.

- [ ] **Step 5.6: Bump (§ Bumping, patch) and commit**

```bash
cd /home/andre/Workspace/mediagram
git add web/src/state/store.ts web/src/state/lists-exchange.ts web/test/state-lists-sync.test.ts \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(web): a Kids mark can be from 6, and says so across devices; release <next>"
```

### Task 6 — Roles travel between devices

**Files:** create `web/src/state/roles-exchange.ts`; modify `web/src/state/store.ts`, `web/test/state-roles-exchange.test.ts`.

- [ ] **Step 6.1: Write the failing test.** In `web/test/state-roles-exchange.test.ts`, add to the
  imports:

```ts
import { mergeStates, type MergedProfile } from "../src/state/merge";
import { normalName, parseRecord, type SyncRecord } from "../src/state/sync-record";
```

and append:

```ts
describe("the record a device writes", () => {
  test("says who is admin, a kid's limit and parent, and a grown-up's PIN", () => {
    const { state, path } = store();
    const me = state.createProfile("André")!;
    const mia = state.createProfile("Mia", true)!;
    sql(path, (db) => {
      db.query("UPDATE profiles SET admin_claimed_at = 500, pin_hash = ?2, pin_salt = ?3, pin_updated_at = 600 WHERE id = ?1")
        .run(me.id, HASH, SALT);
      db.query("UPDATE profiles SET kids_age = 6, kids_age_updated_at = 700, parent_id = ?2 WHERE id = ?1").run(mia.id, me.id);
    });
    const byName = new Map(state.exportRecord("laptop").profiles.map((profile) => [profile.name, profile]));
    expect(byName.get("André")).toMatchObject({ admin: { claimedAt: 500 }, pin: { hash: HASH, salt: SALT, updatedAt: 600 } });
    expect(byName.get("Mia")).toMatchObject({ kids: true, kidsAge: { age: 6, updatedAt: 700 }, parent: "André" });
    for (const key of ["kids", "kidsAge", "parent"]) expect(key in byName.get("André")!).toBe(false);
    for (const key of ["admin", "pin"]) expect(key in byName.get("Mia")!).toBe(false);
    state.close();
  });

  test("a kid from before limits is FSK 12 at time 0, and a parent not here is not named", () => {
    const { state, path } = store();
    const mia = state.createProfile("Mia", true)!;
    sql(path, (db) => db.query("UPDATE profiles SET parent_id = 'gone' WHERE id = ?1").run(mia.id));
    const [written] = state.exportRecord("laptop").profiles;
    expect(written!.kidsAge).toEqual({ age: 12, updatedAt: 0 });
    expect("parent" in written!).toBe(false);
    state.close();
  });
});

/** A merged viewer with nothing but what a test says. */
const viewer = (name: string, extra: Partial<MergedProfile> = {}): MergedProfile =>
  ({ name: normalName(name)!, displayName: name, progress: [], watched: [], ...extra });

describe("taking in what the devices agreed on", () => {
  test("a newer limit or PIN is applied, an older one is not", () => {
    const { state } = store();
    const mia = state.createProfile("Mia", true)!;
    const me = state.createProfile("André")!;
    expect(state.importMerged({ profiles: [
      viewer("Mia", { kids: true, kidsAge: { age: 6, updatedAt: 1000 } }),
      viewer("André", { pin: { hash: HASH, salt: SALT, updatedAt: 1000 } }),
    ] })).toBe(2);
    expect(state.importMerged({ profiles: [
      viewer("Mia", { kids: true, kidsAge: { age: 12, updatedAt: 999 } }),
      viewer("André", { pin: { hash: "0".repeat(64), salt: SALT, updatedAt: 999 } }),
    ] })).toBe(0);
    const byId = new Map(state.profiles().map((profile) => [profile.id, profile]));
    expect(byId.get(mia.id)!.kidsAge).toBe(6);
    expect(byId.get(me.id)!.hasPin).toBe(true);
    expect(state.exportRecord("x").profiles.find((profile) => profile.name === "André")!.pin!.hash).toBe(HASH);
    state.close();
  });

  test("an equal time with a different value takes the merge's winner, once", () => {
    const { state } = store();
    state.createProfile("Mia", true);
    const at = (age: 6 | 12) => ({ profiles: [viewer("Mia", { kids: true, kidsAge: { age, updatedAt: 1000 } })] });
    expect(state.importMerged(at(6))).toBe(1);
    expect(state.importMerged(at(12))).toBe(1);
    expect(state.importMerged(at(12))).toBe(0);
    expect(state.profiles()[0]!.kidsAge).toBe(12);
    state.close();
  });

  test("a kid's parent is set once, to the local profile of that name, and never moved", () => {
    const { state } = store();
    const maja = state.createProfile("Maja")!;
    state.createProfile("André");
    const mia = state.createProfile("Mia", true)!;
    expect(state.importMerged({ profiles: [viewer("Mia", { kids: true, parent: "maja" })] })).toBe(1);
    expect(state.importMerged({ profiles: [viewer("Mia", { kids: true, parent: "andré" })] })).toBe(0);
    expect(state.profiles().find((profile) => profile.id === mia.id)!.parentId).toBe(maja.id);
    state.close();
  });

  test("a parent met in the same import as its kid is still found", () => {
    const { state } = store();
    state.importMerged({ profiles: [viewer("Mia", { kids: true, parent: "maja" }), viewer("Maja")] });
    const byName = new Map(state.profiles().map((profile) => [profile.name, profile]));
    expect(byName.get("Mia")!.parentId).toBe(byName.get("Maja")!.id);
    state.close();
  });

  test("the merge's admin becomes the only admin here, claimed when the merge says", () => {
    const { state, path } = store();
    state.createProfile("André");
    const maja = state.createProfile("Maja")!;
    sql(path, (db) => db.query("UPDATE profiles SET admin_claimed_at = 2000 WHERE id = ?1").run(maja.id));
    expect(state.importMerged({ profiles: [viewer("André", { admin: { claimedAt: 1000 } }), viewer("Maja")] })).toBe(2);
    expect(state.profiles().filter((profile) => profile.admin).map((profile) => profile.name)).toEqual(["André"]);
    expect(state.exportRecord("x").profiles.find((profile) => profile.name === "André")!.admin).toEqual({ claimedAt: 1000 });
    state.close();
  });

  test("a merge that names no admin changes nothing", () => {
    const { state, path } = store();
    const maja = state.createProfile("Maja")!;
    sql(path, (db) => db.query("UPDATE profiles SET admin_claimed_at = 2000 WHERE id = ?1").run(maja.id));
    expect(state.importMerged({ profiles: [viewer("Maja")] })).toBe(0);
    expect(state.profiles()[0]!.admin).toBe(true);
    state.close();
  });

  test("an older build's document, saying only kids, does not undo a limit a parent set", () => {
    const { state, path } = store();
    const mia = state.createProfile("Mia", true)!;
    sql(path, (db) => db.query("UPDATE profiles SET kids_age = 6, kids_age_updated_at = 700 WHERE id = ?1").run(mia.id));
    const old: SyncRecord = {
      format: 1, device: "old-tv", writtenAt: 0,
      profiles: [{ name: "Mia", kids: true, progress: [], watched: [] }],
    };
    expect(state.importMerged(mergeStates([state.exportRecord("laptop"), old]))).toBe(0);
    expect(state.profiles()[0]!.kidsAge).toBe(6);
    state.close();
  });

  test("a limit set on one machine is the limit on the other, and a second round changes nothing", () => {
    const laptop = store();
    const tv = store();
    const kid = laptop.state.createProfile("Mia", true)!;
    sql(laptop.path, (db) => db.query("UPDATE profiles SET kids_age = 6, kids_age_updated_at = 5000 WHERE id = ?1").run(kid.id));
    const wire = (state: WatchState, device: string) => parseRecord(JSON.stringify(state.exportRecord(device)))!;
    const round = () => tv.state.importMerged(mergeStates([wire(tv.state, "tv"), wire(laptop.state, "laptop")]));
    expect(round()).toBeGreaterThan(0);
    expect(tv.state.profiles()).toEqual([expect.objectContaining({ name: "Mia", kids: true, kidsAge: 6 })]);
    expect(round()).toBe(0);
    laptop.state.close();
    tv.state.close();
  });

  test("importing its own document changes nothing", () => {
    const { state, path } = store();
    const me = state.createProfile("André")!;
    const mia = state.createProfile("Mia", true)!;
    sql(path, (db) => {
      db.query("UPDATE profiles SET admin_claimed_at = 500, pin_hash = ?2, pin_salt = ?3, pin_updated_at = 600 WHERE id = ?1")
        .run(me.id, HASH, SALT);
      db.query("UPDATE profiles SET kids_age = 6, kids_age_updated_at = 700, parent_id = ?2 WHERE id = ?1").run(mia.id, me.id);
    });
    expect(state.importMerged(mergeStates([state.exportRecord("self")]))).toBe(0);
    state.close();
  });
});
```

- [ ] **Step 6.2: Run it — expect failure**

```bash
bun test test/state-roles-exchange.test.ts
```

Expected: the two "record a device writes" tests fail (no `admin`/`pin`/`kidsAge`/`parent` on
the export), and every import test expecting a change fails (`received: 0`).

- [ ] **Step 6.3: Create `web/src/state/roles-exchange.ts`**

```ts
/**
 * A profile's role on the sync record: the admin claim, a kid's limit and
 * parent, a grown-up's PIN.
 *
 * Kept out of `store.ts` for the reason `lists-exchange.ts` is. Corrective,
 * like everything `importMerged` calls: newer local news is kept, and an
 * equal time with a different value takes the winner the merge already
 * chose by device id. SQLite failures propagate, so `importMerged` rolls the
 * whole import back.
 */

import type { Database } from "bun:sqlite";
import type { MergedProfile } from "./merge";
import { profileRows, type ProfileRow } from "./profiles";
import type { RoleKeys } from "./roles-record";
import { normalName } from "./sync-record";

/** Each profile's role keys, by local id — what `exportRecord` spreads in. */
export function exportRoles(db: Database | null): Map<string, RoleKeys> {
  const rows = profileRows(db);
  const names = new Map(rows.map((row) => [row.id, row.name]));
  return new Map(rows.map((row): [string, RoleKeys] => {
    const keys: RoleKeys = {};
    if (row.adminClaimedAt !== null) keys.admin = { claimedAt: row.adminClaimedAt };
    if (row.kids !== 0) {
      // `kids` is still written beside the limit: an older build that knows
      // nothing of limits then keeps filtering this kid at 12, rather than
      // reading it as a grown-up.
      keys.kids = true;
      keys.kidsAge = { age: row.kidsAge === 6 ? 6 : 12, updatedAt: row.kidsAgeUpdatedAt };
    }
    // By name, because a name is what sync knows a viewer by; a parent
    // removed here is simply not said.
    const parent = row.parentId === null ? undefined : names.get(row.parentId);
    if (parent !== undefined) keys.parent = parent;
    if (row.pinHash !== null && row.pinSalt !== null) {
      keys.pin = { hash: row.pinHash, salt: row.pinSalt, updatedAt: row.pinUpdatedAt };
    }
    return [row.id, keys];
  }));
}

/**
 * Takes in the merged role keys. Run after every merged profile has a local
 * row, because a kid's parent may be a viewer this same import made.
 */
export function importRoles(db: Database, merged: MergedProfile[]): number {
  // The first profile of each name, the one `findOrCreateProfile` matches.
  const local = new Map<string, ProfileRow>();
  for (const row of profileRows(db)) {
    const name = normalName(row.name);
    if (name !== null && !local.has(name)) local.set(name, row);
  }

  let changed = 0;
  for (const profile of merged) {
    const row = local.get(profile.name);
    if (row === undefined) continue;
    const limit = profile.kidsAge;
    if (limit && newer(limit.updatedAt, row.kidsAgeUpdatedAt, limit.age !== row.kidsAge)) {
      db.query("UPDATE profiles SET kids_age = ?2, kids_age_updated_at = ?3 WHERE id = ?1")
        .run(row.id, limit.age, limit.updatedAt);
      changed += 1;
    }
    const pin = profile.pin;
    if (pin && newer(pin.updatedAt, row.pinUpdatedAt, pin.hash !== row.pinHash || pin.salt !== row.pinSalt)) {
      db.query("UPDATE profiles SET pin_hash = ?2, pin_salt = ?3, pin_updated_at = ?4 WHERE id = ?1")
        .run(row.id, pin.hash, pin.salt, pin.updatedAt);
      changed += 1;
    }
    // Set once and never moved: two documents disagreeing about a kid's
    // parent is a bug to notice, not a change to follow.
    const parent = profile.parent === undefined ? undefined : local.get(profile.parent);
    if (row.parentId === null && parent !== undefined && parent.id !== row.id) {
      db.query("UPDATE profiles SET parent_id = ?2 WHERE id = ?1").run(row.id, parent.id);
      changed += 1;
    }
  }
  return changed + importAdmin(db, merged, local);
}

/** Merged news beats local when it is newer, or as new and different. */
function newer(merged: number, local: number, different: boolean): boolean {
  return merged > local || (merged === local && different);
}

/**
 * The merge's admin becomes the only one here. A merge that names nobody
 * says nothing — it does not say "no admin" — so it changes nothing.
 */
function importAdmin(db: Database, merged: MergedProfile[], local: Map<string, ProfileRow>): number {
  const named = merged.find((profile) => profile.admin !== undefined);
  const row = named === undefined ? undefined : local.get(named.name);
  if (named?.admin === undefined || row === undefined) return 0;
  return db.query("UPDATE profiles SET admin_claimed_at = NULL WHERE id <> ?1 AND admin_claimed_at IS NOT NULL")
    .run(row.id).changes +
    db.query("UPDATE profiles SET admin_claimed_at = ?2 WHERE id = ?1 AND admin_claimed_at IS NOT ?2")
      .run(row.id, named.admin.claimedAt).changes;
}
```

- [ ] **Step 6.4: Wire it into `web/src/state/store.ts`**

  1. After the `./profiles` import add `import { exportRoles, importRoles } from "./roles-exchange";`
  2. In `exportRecord`, make the first line of the body `const roles = exportRoles(this.db);` and
     replace `...(profile.kids ? { kids: true as const } : {}),` with `...roles.get(profile.id),`
  3. In `importMerged`, replace
     `this.db.query("UPDATE profiles SET kids = 1 WHERE id = ?1").run(profileId);` with
     `this.db.query("UPDATE profiles SET kids = 1, kids_age = COALESCE(kids_age, 12) WHERE id = ?1").run(profileId);`
  4. In `importMerged`, after the `for (const profile of merged.profiles) { … }` loop closes and
     before `this.db.exec("COMMIT");`, add:

```ts
      // After the loop, not in it: a kid's parent may be a viewer this same
      // import has only just made.
      changed += importRoles(this.db, merged.profiles);
```

- [ ] **Step 6.5: Run — expect pass**

```bash
bun test test/state-roles-exchange.test.ts test/state-store.test.ts test/state-sync.test.ts \
  test/state-two-machines.test.ts test/state-sync-import-ties.test.ts test/state-lists-sync.test.ts \
  && bun run typecheck && wc -l src/state/store.ts src/state/roles-exchange.ts
```

Expected: all pass (existing import counts unchanged — a kid created or upgraded by sync
reads 12 at 0, equal to the merge's default); `store.ts` 775, `roles-exchange.ts` 99.

- [ ] **Step 6.6: Bump (§ Bumping, patch) and commit**

```bash
cd /home/andre/Workspace/mediagram
git add web/src/state/roles-exchange.ts web/src/state/store.ts web/test/state-roles-exchange.test.ts \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(web): who is admin, a kid's limit and parent, and a PIN travel between devices; release <next>"
```

### Task 7 — The shared fixture the core is held to

**Files:** create `web/test/fixtures/watch-state/profile-roles-merge.json`; modify `web/test/shared-watch-state-fixtures.test.ts`.

The implementation is already in place, so this task's run is expected to **pass on first
run** — that is what the runner's header requires of every case it holds.

- [ ] **Step 7.1: Write the fixture** (the generator is not committed; its output is):

```bash
cd /home/andre/Workspace/mediagram/web && bun run - <<'EOF'
const A = { hash: "f377124b2c2ffeb096001d94cb0e3df86fbe20ad9981335bc624b960d4d80924", salt: "00112233445566778899aabbccddeeff" };
const B = { hash: "48618b45393cde1c8841b73b2ca684f9b39a2efcbf35d4bf602d7f908b5137f8", salt: "ffeeddccbbaa99887766554433221100" };
const twelveAtZero = { age: 12, updatedAt: 0 };
const doc = (device: string, profiles: object[], kids?: object[]) => ({
  format: 1, device, writtenAt: 0,
  profiles: profiles.map((profile) => ({ ...profile, progress: [], watched: [] })),
  ...(kids ? { kids } : {}),
});
const cases = [
  {
    name: "a limit changed on two devices: the newer change wins",
    records: [
      doc("laptop", [{ name: "Mia", kids: true, kidsAge: { age: 12, updatedAt: 1000 } }]),
      doc("tv", [{ name: "Mia", kids: true, kidsAge: { age: 6, updatedAt: 2000 } }]),
    ],
    expect: { profiles: [{ name: "mia", kids: true, kidsAge: { age: 6, updatedAt: 2000 } }], kids: [] },
  },
  {
    name: "a limit changed at the same moment on two devices: the larger device id wins",
    records: [
      doc("laptop", [{ name: "Mia", kids: true, kidsAge: { age: 6, updatedAt: 1000 } }]),
      doc("tv", [{ name: "Mia", kids: true, kidsAge: { age: 12, updatedAt: 1000 } }]),
    ],
    expect: { profiles: [{ name: "mia", kids: true, kidsAge: { age: 12, updatedAt: 1000 } }], kids: [] },
  },
  {
    name: "an older build's document says kids and nothing more: FSK 12 at time 0",
    records: [doc("old-tv", [{ name: "Mia", kids: true }])],
    expect: { profiles: [{ name: "mia", kids: true, kidsAge: twelveAtZero }], kids: [] },
  },
  {
    name: "an older build's document loses to a limit a parent set",
    records: [
      doc("old-tv", [{ name: "Mia", kids: true }]),
      doc("laptop", [{ name: "Mia", kids: true, kidsAge: { age: 6, updatedAt: 5 } }]),
    ],
    expect: { profiles: [{ name: "mia", kids: true, kidsAge: { age: 6, updatedAt: 5 } }], kids: [] },
  },
  {
    name: "a grown-up is never given a limit",
    records: [doc("laptop", [{ name: "André", kidsAge: { age: 6, updatedAt: 5 } }])],
    expect: { profiles: [{ name: "andré" }], kids: [] },
  },
  {
    name: "admin claimed on two devices before they met: the earliest claim is the only admin",
    records: [
      doc("laptop", [{ name: "André", admin: { claimedAt: 2000 } }]),
      doc("tv", [{ name: "Maja", admin: { claimedAt: 1000 } }]),
    ],
    expect: { profiles: [{ name: "andré" }, { name: "maja", admin: { claimedAt: 1000 } }], kids: [] },
  },
  {
    name: "admin claimed at the same moment: the smaller name wins",
    records: [
      doc("laptop", [{ name: "Maja", admin: { claimedAt: 1000 } }]),
      doc("tv", [{ name: "André", admin: { claimedAt: 1000 } }]),
    ],
    expect: { profiles: [{ name: "andré", admin: { claimedAt: 1000 } }, { name: "maja" }], kids: [] },
  },
  {
    name: "one viewer claimed on two devices keeps its earliest claim",
    records: [
      doc("laptop", [{ name: "André", admin: { claimedAt: 3000 } }]),
      doc("tv", [{ name: "André", admin: { claimedAt: 1000 } }]),
    ],
    expect: { profiles: [{ name: "andré", admin: { claimedAt: 1000 } }], kids: [] },
  },
  {
    name: "a claim on a kid is ignored, however early: the earliest grown-up claim wins",
    records: [
      doc("laptop", [{ name: "Mia", kids: true, admin: { claimedAt: 500 } }]),
      doc("tv", [{ name: "André", admin: { claimedAt: 1000 } }]),
    ],
    expect: {
      profiles: [{ name: "andré", admin: { claimedAt: 1000 } }, { name: "mia", kids: true, kidsAge: twelveAtZero }],
      kids: [],
    },
  },
  {
    name: "a viewer one device calls a kid is never admin, even holding the only claim",
    records: [
      doc("laptop", [{ name: "Mia", admin: { claimedAt: 500 } }]),
      doc("tv", [{ name: "Mia", kids: true }]),
    ],
    expect: { profiles: [{ name: "mia", kids: true, kidsAge: twelveAtZero }], kids: [] },
  },
  {
    name: "a PIN reset on another device wins, and a kid never carries one",
    records: [
      doc("laptop", [{ name: "André", pin: { ...A, updatedAt: 1000 } }, { name: "Mia", kids: true, pin: { ...A, updatedAt: 1 } }]),
      doc("tv", [{ name: "André", pin: { ...B, updatedAt: 2000 } }]),
    ],
    expect: {
      profiles: [{ name: "andré", pin: { ...B, updatedAt: 2000 } }, { name: "mia", kids: true, kidsAge: twelveAtZero }],
      kids: [],
    },
  },
  {
    name: "a kid's parent is its parent's normalised name, settled by device id when two disagree",
    records: [
      doc("laptop", [{ name: "Mia", kids: true, parent: "André" }]),
      doc("tv", [{ name: "Mia", kids: true, parent: " Maja " }]),
    ],
    expect: { profiles: [{ name: "mia", kids: true, kidsAge: twelveAtZero, parent: "maja" }], kids: [] },
  },
  {
    name: "a grown-up is never given a parent",
    records: [doc("laptop", [{ name: "André", parent: "Maja" }])],
    expect: { profiles: [{ name: "andré" }], kids: [] },
  },
  {
    name: "a Kids mark moved from 12 to 6 rides on the newer row",
    records: [doc("laptop", [], [{ setId: "K1", updatedAt: 1000 }]), doc("tv", [], [{ setId: "K1", updatedAt: 2000, age: 6 }])],
    expect: { profiles: [], kids: [{ setId: "K1", updatedAt: 2000, age: 6 }] },
  },
  {
    name: "a Kids mark tie is settled by device id, age and all",
    records: [doc("laptop", [], [{ setId: "K1", updatedAt: 1000, age: 6 }]), doc("tv", [], [{ setId: "K1", updatedAt: 1000 }])],
    expect: { profiles: [], kids: [{ setId: "K1", updatedAt: 1000 }] },
  },
  {
    name: "a Kids mark from 6, then taken off, is a plain tombstone",
    records: [
      doc("laptop", [], [{ setId: "K1", updatedAt: 1000, age: 6 }]),
      doc("tv", [], [{ setId: "K1", updatedAt: 2000, removed: true }]),
    ],
    expect: { profiles: [], kids: [{ setId: "K1", updatedAt: 2000, removed: true }] },
  },
];
await Bun.write("test/fixtures/watch-state/profile-roles-merge.json", JSON.stringify(cases, null, 2) + "\n");
EOF
```

- [ ] **Step 7.2: Add its runner.** In `web/test/shared-watch-state-fixtures.test.ts`, after the
  `lists-merge fixtures` describe (`:119`), insert:

```ts
describe("profile-roles-merge fixtures", () => {
  interface Case {
    name: string;
    records: SyncRecord[];
    expect: ReturnType<typeof canonicalRoles>;
  }

  /** The role fields only, as the fixture states them — positions, lists and
   * spellings are the other fixtures' concern. */
  function canonicalRoles(state: MergedState) {
    return {
      profiles: state.profiles
        .map((profile) => ({
          name: profile.name,
          ...(profile.admin ? { admin: profile.admin } : {}),
          ...(profile.kids ? { kids: profile.kids } : {}),
          ...(profile.kidsAge ? { kidsAge: profile.kidsAge } : {}),
          ...(profile.parent !== undefined ? { parent: profile.parent } : {}),
          ...(profile.pin ? { pin: profile.pin } : {}),
        }))
        .sort((a, b) => a.name.localeCompare(b.name)),
      kids: [...(state.kids ?? [])].sort((a, b) => a.setId.localeCompare(b.setId)),
    };
  }

  for (const one of load<Case[]>("profile-roles-merge.json")) {
    test(one.name, () => {
      expect(canonicalRoles(mergeStates(one.records))).toEqual(one.expect);
      expect(canonicalRoles(mergeStates([...one.records].reverse()))).toEqual(one.expect);
    });
  }
});
```

- [ ] **Step 7.3: Run — expect pass on first run**

```bash
bun test test/shared-watch-state-fixtures.test.ts
```

Expected: every `profile-roles-merge fixtures` case passes (16), and every older case still does.
A failure here means the fixture is wrong, not the merge — fix the fixture.

- [ ] **Step 7.4: Bump (§ Bumping, patch) and commit**

```bash
cd /home/andre/Workspace/mediagram
git add web/test/fixtures/watch-state/profile-roles-merge.json web/test/shared-watch-state-fixtures.test.ts \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "test(web): a shared fixture for merging profile roles and Kids mark ages; release <next>"
```

### Task 8 — Phase gate (no commit unless something had to change)

- [ ] **Step 8.1: Whole web suite, types, lint, line limits**

```bash
cd /home/andre/Workspace/mediagram/web && bun test && bun run typecheck && bun run lint && \
  wc -l src/state/{schema,store,sync-record,merge,lists-exchange,profiles,record-scalars,roles-record,roles-merge,roles-exchange}.ts
```

Expected: every test passes (including `code-standards.test.ts`); no type or lint errors;
`schema.ts` 264, `store.ts` ≤ 800, `sync-record.ts` ≤ 328, `merge.ts` ≤ 200, every new file ≤ 200.

## Todo list

- [x] Task 1 — schema **v12** (`roles-schema.ts`), migration tests, `schema.ts` ceiling 245 (no bump: the lead bumps at merge)
- [x] Task 2 — `profiles.ts`; `Profile` carries its role; delegates in `store.ts`
- [x] Task 3 — `record-scalars.ts`, `roles-record.ts`; `ListRow.age` via `kidsRow`
- [x] Task 4 — `roles-merge.ts` (`mergeRoles`, `kidsMarkRank`), `merged.ts`, the `mergeStates` hook
- [x] Task 5 — `setKids` age (clamped), `kidsFromSix`, Kids mark age on the wire
- [x] Task 6 — `roles-exchange.ts`; export and import in `store.ts`
- [x] Task 7 — `profile-roles-merge.json` (20 cases) and `profile-roles-record-parse.json` (11 cases) + runners
- [x] Task 8 — phase gate green (2742 pass)

## Success criteria

- `bun test` green in `web/`, including `code-standards.test.ts`; `bun run typecheck` clean.
- A v10 database opens at v11 with every existing kid at `kids_age = 12` and every mark `age NULL`; a second open changes nothing (Task 1 tests).
- `GET /api/profiles` returns the §8 shape and `JSON.stringify` of it contains no hash or salt (Task 2 tests).
- Merging is order-independent for every role key (`merged()` helper and the fixture's reversed run).
- Two stores exchanging through `parseRecord(JSON.stringify(exportRecord()))` converge on a limit, an admin and a parent, and a second round reports 0 changes (Task 6 tests).
- `profile-roles-merge.json` exists with 16 cases and passes against the web; the core can run it in phase 04.

## Risk assessment

| Risk | L×I | Mitigation |
|---|---|---|
| A tombstone's stale `age` re-imports every round, so imports never settle | M×M | Age compared on live rows only (Step 5.4); "its own document … imports as no change" test |
| A device whose clock runs behind marks or unmarks a title and loses to the row it replaced | M×M | Kids-mark writes stamp `max(now, stored + 1)` (§7); "however far ahead" test in Task 5 |
| Parent imported before the parent's row exists (same import) | M×M | `importRoles` runs after the loop; "a parent met in the same import" test |
| Two admins after devices wake in different orders | L×H | Admin decided across all viewers (`earliest`), import clears every other; fixture cases both orders |
| An older build's `kids: true` document resets a parent's FSK 6 | M×H | Default `{12, 0}` loses to any dated change; test + fixture case |
| `schema.ts` ceiling raised | certain×L | Dated sentence with the reason in `code-standards.test.ts`, the house precedent |
| `store.ts`/`lists-exchange.ts`/`merge.ts` creep past a limit | L×M | Measured in every run step; phase gate runs the ratchet |
| Two local profiles with one normalised name (possible before this change) | L×L | Import matches the first by `created_at`, as `findOrCreateProfile` does; unchanged behaviour |

**Rollback.** Each task is one commit and reverts cleanly in reverse order. The v11 columns
stay behind after a code revert; the older code neither reads nor writes them, and its
migration loop skips a file already past its last group. Documents written with the new keys
are read by older builds as if the keys were absent.

## Security considerations

- The PIN hash and salt leave the store only in the sync record (the household's own channel);
  `Profile`/`GET /api/profiles` say `hasPin` only (`toProfile`), pinned by a test.
- Every new wire key is parsed as hostile: exact hex shapes for hash (64) and salt (32), finite
  positive timestamps, literal `6`/`12` for a limit and literal `true` for `kids`.
- The admin claim is decided by the merge, not by any one document; a forged document can at
  most claim an earlier `claimedAt` — which requires write access to the household's channel,
  the same trust boundary every synced row already has.

## Next steps

- Phase 02 builds the rule, PIN, wait, management operations and routes on `profiles.ts`,
  `ProfileRow`, `insertProfile` (extending `NewProfile`) and `setKids(…, age)`.
- Phase 04 (core) ports parse/merge/export/import and runs `profile-roles-merge.json`.
