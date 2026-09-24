# Phase 02 — Web state and API

**Context:** spec § Data, § Sync (import/export). Files: `web/src/state/schema.ts`, `web/src/state/store.ts` (profiles `:118-155`, `exportRecord` `:366-382`, `importMerged` `:397-460`, `findOrCreateProfile` `:470-486`), `web/src/state/routes.ts` (`POST /api/profiles` `:85-96`). Depends on Phase 01 Task 1 (`ProfileState.kids`, `MergedProfile.kids`).

---

### Task 3: `profiles.kids` column, store, export/import, HTTP

**Files:**
- Modify: `web/src/state/schema.ts` (`STATE_SCHEMA`, append a `GROUPS` entry)
- Modify: `web/src/state/store.ts`
- Modify: `web/src/state/routes.ts`
- Test: `web/test/state-migration.test.ts`, `web/test/state-store.test.ts`, `web/test/state-http.test.ts`

**Interfaces:**
- Consumes: `ProfileState.kids?: true`, `MergedProfile.kids?: true` (Task 1).
- Produces:
  - `interface Profile { id: string; name: string; createdAt: number; kids: boolean }`
  - `WatchState.createProfile(name: unknown, kids = false): Profile | null`
  - `GET /api/profiles` → `{ remembers, profiles: [{ id, name, createdAt, kids }] }`
  - `POST /api/profiles` body `{ name, kids?: boolean }` → 201 `{ id, name, createdAt, kids }`

- [ ] **Step 1: Write the failing tests**

`state-migration.test.ts` — add:

```ts
describe("v6 to v7", () => {
  test("profiles that existed before kids profiles stay ordinary", () => {
    const path = tempPath();
    const db = new Database(path, { create: true });
    for (const statement of migrationsUpTo(6)) db.exec(statement);
    db.query("INSERT INTO state_meta(key, value) VALUES ('schema_version', '6')").run();
    db.query("INSERT INTO profiles(id, name, created_at) VALUES ('p1', 'André', 1)").run();
    db.close();

    const state = new WatchState(path);
    expect(state.profiles()).toEqual([{ id: "p1", name: "André", createdAt: 1, kids: false }]);
    state.close();
  });
});
```

(import `migrationsUpTo` from `../src/state/schema` beside `GROUPS`; if `state_meta` does not exist until a later group, it does exist by v6 — `migrationsUpTo(6)` creates it.)

`state-store.test.ts` — add:

```ts
describe("kids profiles", () => {
  test("a profile is created as a kids profile only when asked", () => {
    const { state } = stateIn();
    const mia = state.createProfile("Mia", true)!;
    expect(mia.kids).toBe(true);
    expect(state.profiles().map((p) => [p.name, p.kids])).toEqual([["André", false], ["Mia", true]]);
  });

  test("the record carries kids only on the kids profile", () => {
    const { state } = stateIn();
    state.createProfile("Mia", true);
    const record = state.exportRecord("laptop");
    const byName = new Map(record.profiles.map((p) => [p.name, p]));
    expect(byName.get("Mia")!.kids).toBe(true);
    expect("kids" in byName.get("André")!).toBe(false);
  });

  test("an import makes an existing ordinary profile of that name a kids profile", () => {
    const { state } = stateIn();
    state.createProfile("Mia");
    const changed = state.importMerged({
      profiles: [{ name: "mia", displayName: "Mia", kids: true, progress: [], watched: [] }],
    });
    expect(changed).toBe(1);
    expect(state.profiles().find((p) => p.name === "Mia")!.kids).toBe(true);
  });

  test("an import creates a profile it has never met with the flag", () => {
    const { state } = stateIn();
    state.importMerged({ profiles: [{ name: "ben", displayName: "Ben", kids: true, progress: [], watched: [] }] });
    expect(state.profiles().find((p) => p.name === "Ben")!.kids).toBe(true);
  });

  test("an import never turns a kids profile back into an ordinary one", () => {
    const { state } = stateIn();
    state.createProfile("Mia", true);
    const changed = state.importMerged({ profiles: [{ name: "mia", displayName: "Mia", progress: [], watched: [] }] });
    expect(changed).toBe(0);
    expect(state.profiles().find((p) => p.name === "Mia")!.kids).toBe(true);
  });
});
```

`state-http.test.ts` — add (next to the existing profile tests, using the file's `server`, `rawRequest`, `JSON_HEAD`):

```ts
test("a profile can be created as a kids profile and is listed as one", async () => {
  const made = await rawRequest(server.port, "/api/profiles", {
    method: "POST",
    headers: JSON_HEAD,
    body: JSON.stringify({ name: "Mia", kids: true }),
  });
  expect(made.status).toBe(201);
  expect(JSON.parse(new TextDecoder().decode(made.body))).toMatchObject({ name: "Mia", kids: true });

  // Anything but a literal true makes an ordinary profile.
  const loose = await rawRequest(server.port, "/api/profiles", {
    method: "POST",
    headers: JSON_HEAD,
    body: JSON.stringify({ name: "Ben", kids: "yes" }),
  });
  expect(JSON.parse(new TextDecoder().decode(loose.body))).toMatchObject({ name: "Ben", kids: false });

  const listed = JSON.parse(new TextDecoder().decode((await rawRequest(server.port, "/api/profiles")).body));
  expect(listed.profiles.find((p: { name: string }) => p.name === "Mia").kids).toBe(true);
});
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd web && bun test test/state-migration.test.ts test/state-store.test.ts test/state-http.test.ts`
Expected: FAIL — `kids` missing / `createProfile` ignores its second argument.

- [ ] **Step 3: Implement**

`schema.ts`:

```ts
export const STATE_SCHEMA = 7;
```

Append to `GROUPS` (after the v6 group):

```ts
  // v7: a kids profile sees only titles rated for children. A column rather
  // than a table: it is one fact about the profile, set when it is made, and
  // every existing profile is ordinary — hence the default.
  [`ALTER TABLE profiles ADD COLUMN kids INTEGER NOT NULL DEFAULT 0`],
```

`store.ts` — the type:

```ts
export interface Profile {
  id: string;
  name: string;
  createdAt: number;
  /** Sees only titles rated FSK 12 or under, or marked for Kids by hand. */
  kids: boolean;
}
```

`profiles()`:

```ts
  profiles(): Profile[] {
    if (!this.db) return [];
    const rows = this.db
      .query("SELECT id, name, created_at AS createdAt, kids FROM profiles ORDER BY created_at")
      .all() as { id: string; name: string; createdAt: number; kids: number }[];
    return rows.map((row) => ({ ...row, kids: row.kids !== 0 }));
  }
```

`createProfile`:

```ts
  createProfile(name: unknown, kids = false): Profile | null {
    if (!this.db) return null;
    const clean = cleanName(name);
    if (clean === null) return null;

    const profile = { id: crypto.randomUUID(), name: clean, createdAt: Date.now(), kids };
    this.db
      .query("INSERT INTO profiles(id, name, created_at, kids) VALUES (?1, ?2, ?3, ?4)")
      .run(profile.id, profile.name, profile.createdAt, kids ? 1 : 0);
    return profile;
  }
```

`exportRecord` — in the per-profile object, after `localId: profile.id,`:

```ts
      ...(profile.kids ? { kids: true as const } : {}),
```

`findOrCreateProfile` — take the flag and report it:

```ts
  private findOrCreateProfile(
    name: string,
    displayName?: string,
    kids = false,
  ): { id: string; created: boolean; kids: boolean } | null {
    const wanted = normalName(name);
    if (wanted === null) return null;
    const found = this.profiles().find((profile) => normalName(profile.name) === wanted);
    if (found) return { id: found.id, created: false, kids: found.kids };
    const created = this.createProfile(displayName ?? name, kids);
    return created ? { id: created.id, created: true, kids } : null;
  }
```

`importMerged` — replace the match lines at the top of the per-profile loop:

```ts
        const matched = this.findOrCreateProfile(profile.name, profile.displayName, profile.kids === true);
        if (matched === null) continue;
        const profileId = matched.id;
        if (matched.created) changed += 1;
        // Another device made this viewer a kids profile. Only ever upgraded:
        // a merge without the flag says nothing, it does not say "not kids".
        if (profile.kids === true && !matched.kids) {
          this.db.query("UPDATE profiles SET kids = 1 WHERE id = ?1").run(profileId);
          changed += 1;
        }
```

`routes.ts` — the POST:

```ts
      const body = parse(request.body) as { name?: unknown; kids?: unknown } | null;
      // Only a literal true: a restricting flag is not switched on by accident.
      const made = state.createProfile(body?.name, body?.kids === true);
      return made === null ? status(400) : json(JSON.stringify(made), false, 201);
```

Run `bunx tsc --noEmit -p .` from `web/` and fix every other `Profile` literal or `createProfile` call the compiler reports (tests constructing `Profile` objects need `kids: false`).

- [ ] **Step 4: Run to verify they pass**

Run: `cd web && bun test && bunx tsc --noEmit -p .`
Expected: all tests PASS, no type errors.

- [ ] **Step 5: Commit**

```bash
git add web/src/state web/test
git commit -m "feat(web-state): store, sync and create kids profiles"
```

## Success criteria

Existing databases migrate with every profile ordinary; the flag round-trips through HTTP, export and import; import upgrades but never downgrades.
