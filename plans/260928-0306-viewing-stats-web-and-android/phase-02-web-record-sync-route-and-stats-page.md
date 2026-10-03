# Phase 02 — Web: record, sync, stats route, Stats page

**Goal:** the web player counts each profile's watching inside its own
position writes, carries the rows through the existing `#mlib-state`
documents, answers `GET /api/profiles/{profileId}/stats`, and shows a Stats
page from a new rail item between Genres and Settings — the reference the
Android surface (phase 04) copies.

**Architecture.** Data flow, end to end:

```
player.js saveProgress (10 s tick :491, pause/close final saves)
  └─ PUT/POST /api/profiles/{p}/progress/{set} ─ routes.ts:136 ─▶ WatchState.setProgress
       └─ stats-recorder.writeProgress   one transaction:
            read hadProgress, watchedLive → upsert progress → upsert stats_titles(+step, again_at) → upsert stats_days(+step)
            then ticks[(p,set)] = {at, wallMs}            (in memory, per WatchState = per process; dropped by setWatched(…, true))
sync round (sync.ts:126-153)
  exportRecord ─▶ ProfileState{…, titleStats?, dayStats?}   every device's rows; a key omitted when empty
  channel docs ─parseRecord─▶ mergeStates ─▶ importMerged ─▶ importStats   newer-only upsert, never delete; never recorded
GET /api/profiles/{p}/stats ─ stats-routes.ts (dispatched before routes.ts's write gate) ─▶ WatchState.stats
  └─ readSummary(rows of every device + live watched, localDay(now)) ─▶ summarize ─▶ JSON StatsSummary
#/stats ─ address.js ─ app.js drawRoute ─▶ stats-page.renderStats ─ fetch (route-generation guard) ─▶ totals · 30 bars · history (stats-format.js)
```

New tables (`stats_titles`, `stats_days`) are schema v11, one new group in
`GROUPS`. `SYNC_FORMAT` stays 1: older readers drop the two keys, as they drop
`unwatched` (commit `fce5a757`).

**Context**
- Contract (authoritative for every name, key, rule, route and UI string): [shared-contract.md](shared-contract.md) §1, §2, §3, §5, §6, §8. The store class is `WatchState` (`web/src/state/store.ts:98`).
- Decisions: [plan.md](plan.md) § Decisions 1–11 (user; never reverse) — esp. 4 (own stats only), 5 (kept forever), 9 (rail item placement), 10 (watched again).
- Builds on phase 01: `stats-step.ts` (`stepSeconds`, `againNow`, `LastTick`), `stats-record.ts` (`TitleStatRow`, `DayStatRow`, `StatsRows`), `stats-summary.ts` (`summarize`, `StatsSummary`); `MergedProfile` already carries `titleStats?`/`dayStats?`.
- Code: `store.ts:215-228` (`setProgress`), `:278-293` (`setWatched`), `:347` (`deviceId`), `:380-402` (`exportRecord`), `:420-476` (`importMerged`, its transaction `:422-470`); `schema.ts:19-239` (`GROUPS`), `:241-244` (`migrationsUpTo`, only caller `test/state-migration.test.ts`); `routes.ts:17-26` (`P`), `:122-129` (state GET), `:132` (`if (reading) return status(405)`), `:255-267` (`parse`/`json`/`status`); `app.js:24` (shelf-mode import), `:146-183` (`shelfToggle`, its doc and trailing blank), `:263` (its one caller), `:483` (route-generation guard pattern in `viewSearch`), `:599-604` (`nav a` highlight loop), `:630` (system route); `public/lib/address.js:26-30, :63, :97`; `public/index.html:55-56` (Genres, Settings rail links); `public/lib/catalog/home-resume.js:32-33` (how Continue watching names an episode: the show, then `episodeLabel`).
- Pattern files: `watched-exchange.ts`, `lists-exchange.ts` (exchange shape), `cast.js:85` (`renderPerson`, a `stillHere()` guard).
- UI verification: `cd web && bun run preview` (copies of the library and `state.db`, no Telegram — memory "verify player UI with a stub harness"); screenshots with the gstack `/browse` skill.

**Global constraints (shared-contract.md §8)**
- No plan references (phase numbers, decision numbers) in code, test names or commit messages.
- Web `CEILINGS` (`web/test/code-standards.test.ts`) are never raised; make room by extracting. New files ≤ 200 lines. Rust: ≤ 200 lines per non-test file.
- Versions: all three manifests bumped by pattern, once per phase on its last commit (CLAUDE.md § Versioning; memory "bump versions by pattern").
- Branch `feat/viewing-stats` in a worktree off `main`.

**Line budget (verified by applying every task in order):** `store.ts` 798 → 791 (Task 2.2) → 799 (Task 2.3), ceiling 800; `schema.ts` 244 → 243 (ceiling 245); `routes.ts` 267 → 246 (ceiling 267); `app.js` 753 → 717 (ceiling 753). Task 2.7 lowers those three ceilings to their new sizes, the ratchet's own convention. Largest new file: `stats-recorder.ts`, 87 lines.

**File ownership:** `web/src/state/**`, `web/public/**`, `web/test/**` (the tests of both), plus the three manifests and `Cargo.lock` on the last commit. No `docs/**` (phase 05), no `crates/**`, no `android/**` beyond the version line.

## Review focus

Each item names the test that guards it.

1. **A sync import never records minutes.** Positions merged in from other devices go through `importMerged`, never `writeProgress`, and leave no last tick behind — Task 2.2 "a position merged in from another device counts nothing here".
2. **A profile with no stats exports exactly what it did before.** Each key omitted when empty, so pre-stats documents and existing fixtures keep their bytes — Task 2.3 "a profile with no stats says exactly what it said before stats existed".
3. **`againAt` is omitted, not written as `null`.** Readers take `null` as absent (contract §3, amended), but omission keeps a document minimal and matches the Rust exporter — Task 2.3 "a title never restarted crosses the wire without an againAt, and is kept".
4. **Server restart mid-title.** Ticks live in memory only; the first write after a restart counts 0 and the title row keeps its `started_at` — Task 2.2 "a restarted server counts nothing for its first write of each title".
5. **Two tabs (or two televisions) on one title.** One tick per (profile, set) means steps interleave and the sum can never exceed the wall clock — Task 2.2 "two tabs on one title never count more than the wall clock".
6. **Midnight.** A step that spans midnight counts on the day of the write that ends it — Task 2.2 "a step across midnight counts on the day it ends".
7. **A refused write leaves nothing behind.** A profile deleted on another device makes the progress insert fail its foreign key; the whole transaction rolls back and `tolerate` swallows it — Task 2.2 "a write for a profile another device deleted is refused quietly and counts nothing".
8. **A clock that stepped back never lowers a row's stamp.** Own title and day rows take `updated_at = MAX(now, stored + 1)` (contract §1, amended), so a copy another device holds can never outrank this device's newer row — Task 2.2 "a clock that stepped back still moves each row's stamp forward".
9. **Finishing drops the tick.** `setWatched(…, true)` deletes it, so the next write is a fresh first write — Task 2.2 "finishing forgets the last write…".
10. **Gossip never double-counts; a vanished device keeps its minutes; a reinstall gets its own rows back** — Task 2.3 "minutes counted on one show up on the other, and later rounds add nothing", "a device that went away keeps its minutes…", "this device's own row comes back when newer…".
11. **Stats import is inside the import transaction.** A failing `stats_titles` insert rolls back the whole import and publishes nothing — Task 2.3 adds `"stats_titles"` to `state-sync.test.ts`'s rollback table.
12. **Existing import-count assertions move by exactly the title row a position write now starts.** The full suite was run with recording and exchange in place: only `state-sync.test.ts:274` (1 → 2) and `:337` (6 → 7) change — Task 2.3.
13. **The stats GET is answered before the write gate.** Placed after `routes.ts:132` it would be a 405 — Task 2.4 "answers the profile's summary…" asserts 200.
14. **Each profile sees only its own.** The route takes the profile from the path; the page asks for `state.profileId()` — Task 2.6 asserts the URL is `/api/profiles/viewer/stats`.
15. **A title this profile cannot see is never named.** The page resolves titles through `app.js`'s `byId`, which is the profile's own (Kids-filtered) catalog; anything else reads "No longer in the library" — Task 2.5 `historyTitle(null)`, Task 2.6 the `01GONE` line.
16. **A late answer after navigating away draws nothing** — Task 2.6 "an answer that lands after the viewer left draws nothing".
17. **Calendar days, not 24-hour spans; time-zone-proof tests.** Task 2.5 "calendar days, not 24-hour spans, across the clocks going back"; all new tests were run under `TZ=America/Los_Angeles` and `TZ=Pacific/Kiritimati` as well as the host zone.
18. **No false "Watched again" at the end of a title.** `againNow` fires when a finished title gets a position again. The player never sends one after finishing: `player.js:479-489` `saveProgress` sends `markFinished` instead of a position once `isFinished`, for the tick, the pause and the close alike. Known consequence of the contract's rule, accepted: opening a finished title for a few seconds logs "Watched again" — Task 2.2 "starting a finished title over marks it watched again, once, and counts on top".

---

## Task 2.1: schema v11 — `stats_titles`, `stats_days`

**Files:**
- Create: `web/src/state/stats-schema.ts`
- Modify: `web/src/state/schema.ts` (one import, one `GROUPS` entry; `migrationsUpTo` moves to its only caller)
- Modify: `web/test/state-migration.test.ts`

**Interfaces:**
- Produces: `STATS_GROUP: readonly string[]` (contract §2 DDL, verbatim; `REFERENCES … ON DELETE CASCADE` as `progress` has since v2, `schema.ts:74-81`). Schema version becomes `GROUPS.length` = 11.
- `schema.ts` has one spare line under its ceiling and needs two (the import and the entry), so the test-only `migrationsUpTo` (`schema.ts:241-244`) moves into `state-migration.test.ts`, its only caller (`:278`, `:293`, `:312` use it).

- [ ] **Step 1: Failing test** — in `web/test/state-migration.test.ts`, replace

```ts
import { GROUPS, migrationsUpTo } from "../src/state/schema";
```

with

```ts
import { GROUPS } from "../src/state/schema";
```

add below the `afterEach(…)` block:

```ts
/** Every statement needed to reach `version` from nothing. */
function migrationsUpTo(version: number): string[] {
  return GROUPS.slice(0, Math.max(0, version)).flatMap((group) => [...group]);
}
```

and append:

```ts
describe("v10 to v11", () => {
  test("a database from before viewing stats gains its two tables, kept across opens and gone with the profile", () => {
    const path = tempPath();
    const db = new Database(path, { create: true });
    for (const statement of migrationsUpTo(10)) db.exec(statement);
    db.query("INSERT INTO state_meta(key, value) VALUES ('schema_version', '10')").run();
    db.query("INSERT INTO profiles(id, name, created_at) VALUES ('p1', 'André', 1)").run();
    db.close();

    new WatchState(path).close();
    const raw = new Database(path);
    raw.query("INSERT INTO stats_days(profile_id, day, device, seconds, updated_at) VALUES ('p1', '2026-10-03', 'laptop', 600, 1)").run();
    raw.query(`INSERT INTO stats_titles(profile_id, set_id, device, started_at, last_watched_at, seconds, again_at, updated_at)
      VALUES ('p1', '01SET', 'laptop', 1, 1, 600, NULL, 1)`).run();
    expect(raw.query("SELECT value FROM state_meta WHERE key = 'schema_version'").get()).toEqual({ value: "11" });
    raw.close();

    // A second open replays nothing over them.
    const state = new WatchState(path);
    const check = new Database(path);
    expect(check.query("SELECT seconds FROM stats_days").all()).toEqual([{ seconds: 600 }]);
    state.deleteProfile("p1");
    expect(check.query("SELECT COUNT(*) AS n FROM stats_days").get()).toEqual({ n: 0 });
    expect(check.query("SELECT COUNT(*) AS n FROM stats_titles").get()).toEqual({ n: 0 });
    check.close();
    state.close();
  });
});
```

- [ ] **Step 2: Run, expect a failure**

Run: `cd web && bun test test/state-migration.test.ts`
Expected: 15 pass, 1 fail — "v10 to v11 …" (`no such table: stats_days`).

- [ ] **Step 3: Implement** — `web/src/state/stats-schema.ts`

```ts
/**
 * v10 -> v11: viewing stats — how long each title was watched, and how long
 * each day — per profile and per counting device.
 *
 * Its own file only because `schema.ts` is at its line limit; `GROUPS` lists
 * it like every other group, so the version is still `GROUPS.length`.
 *
 * `device` is in both keys because a device writes only its own rows and
 * imports everyone else's unchanged: a total is a sum over devices, so a
 * merge that sees the same row twice cannot count it twice. Kept forever —
 * a day row is a few dozen bytes, and the history is the point.
 *
 * Cascades with the profile, like every other table that belongs to one.
 */
export const STATS_GROUP: readonly string[] = [
  `CREATE TABLE IF NOT EXISTS stats_titles(
     profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
     set_id TEXT NOT NULL,
     device TEXT NOT NULL,
     started_at INTEGER NOT NULL,
     last_watched_at INTEGER NOT NULL,
     seconds REAL NOT NULL,
     again_at INTEGER,
     updated_at INTEGER NOT NULL,
     PRIMARY KEY(profile_id, set_id, device)
   )`,
  `CREATE TABLE IF NOT EXISTS stats_days(
     profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
     day TEXT NOT NULL,
     device TEXT NOT NULL,
     seconds REAL NOT NULL,
     updated_at INTEGER NOT NULL,
     PRIMARY KEY(profile_id, day, device)
   )`,
];
```

In `web/src/state/schema.ts`, after the header comment (line 12, ` */`) and its blank line, add:

```ts
import { STATS_GROUP } from "./stats-schema";

```

and replace the end of the file — from line 239 (`];`) through line 244 (the closing `}` of `migrationsUpTo`) — with:

```ts

  STATS_GROUP,
];
```

so the array ends `  ],` (the v9 → v10 group), a blank line, `  STATS_GROUP,`, `];`.

- [ ] **Step 4: Run, expect PASS**

Run: `cd web && bun test test/state-migration.test.ts test/state-metadata-failures.test.ts test/state-settings.test.ts test/cache-budget.test.ts test/code-standards.test.ts`
Expected: 0 fail (the migration file 16 pass). `wc -l src/state/schema.ts` → 243.

- [ ] **Step 5: Commit**

```bash
git add web/src/state/stats-schema.ts web/src/state/schema.ts web/test/state-migration.test.ts
git commit -m "feat(web): state schema v11 — per-title and per-day viewing time"
```

---

## Task 2.2: count watching inside the position write

**Files:**
- Create: `web/src/state/stats-recorder.ts`
- Create: `web/test/state-stats-recording.test.ts`
- Modify: `web/src/state/store.ts` (one import, the `ticks` field, `setProgress` delegates, `setWatched(…, true)` drops the tick)

**Interfaces:**
- Consumes: `stepSeconds`, `againNow`, `LastTick` (`stats-step.ts`).
- Produces: `type Ticks = Map<string, LastTick>`; `tickKey(profileId, setId): string`; `localDay(ms): string` (`YYYY-MM-DD` from the server's local date fields — contract §1); `writeProgress(db, ticks, device, profileId, setId, at, duration): void`. Own rows' `updated_at = MAX(now, stored + 1)` in SQL (contract §1, amended) — the `setWatched` clamp (`store.ts:282-284`).
- Lifetime check (verification rule 5): `WatchState` is constructed once per process in `src/index.ts:138` (and freshly per test), so `ticks` on the instance is per process and never shared between test stores.
- The progress upsert moves verbatim from `store.ts:216-227` into `stats-recorder.ts`; that is what makes room in `store.ts`.

- [ ] **Step 1: Failing tests** — `web/test/state-stats-recording.test.ts` (rows are read through a second, read-only connection, so this task needs nothing from the export)

```ts
/**
 * Counting watch time inside the position write.
 *
 * What one step counts is pinned by `stats-step.json`; this is the store
 * around it: that a step lands on the title and on the right day, that only
 * this device's own writes count, that a restart and a finish forget what
 * they should, and that two writers cannot count more than the wall clock.
 */

import { afterEach, describe, expect, setSystemTime, test } from "bun:test";
import { Database } from "bun:sqlite";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { mergeStates } from "../src/state/merge";
import { WatchState } from "../src/state/store";

const dirs: string[] = [];
/** A store with André in it — at `path` again, to reopen one. */
function stateAt(path?: string) {
  let file = path;
  if (file === undefined) {
    const dir = mkdtempSync(join(tmpdir(), "mediagram-stats-"));
    dirs.push(dir);
    file = join(dir, "state.db");
  }
  const state = new WatchState(file);
  const me = state.profiles()[0]?.id ?? state.createProfile("André")!.id;
  return { state, me, path: file };
}
afterEach(() => {
  setSystemTime();
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

/** 21:00 local on 2026-10-03, and `seconds` after it. */
const T0 = new Date(2026, 9, 3, 21, 0, 0).getTime();
const at = (seconds: number) => setSystemTime(new Date(T0 + seconds * 1000));

/** Every stats row in the file, read beside the store's own connection. */
function counted(path: string) {
  const db = new Database(path, { readonly: true });
  try {
    const titles = db.query(
      `SELECT set_id AS setId, device, started_at AS startedAt, last_watched_at AS lastWatchedAt,
              seconds, again_at AS againAt, updated_at AS updatedAt FROM stats_titles ORDER BY set_id, device`,
    ).all() as {
      setId: string; device: string; startedAt: number; lastWatchedAt: number;
      seconds: number; againAt: number | null; updatedAt: number;
    }[];
    const days = db.query("SELECT day, device, seconds, updated_at AS updatedAt FROM stats_days ORDER BY day, device").all();
    return { titles, days };
  } finally {
    db.close();
  }
}

describe("a position write", () => {
  test("ten-second ticks add up on the title and on today", () => {
    const { state, me, path } = stateAt();
    at(0); state.setProgress(me, "01FILM", 100, 7200);
    at(10); state.setProgress(me, "01FILM", 110, 7200);
    at(20); state.setProgress(me, "01FILM", 120, 7200);

    const device = state.deviceId();
    expect(counted(path)).toEqual({
      titles: [{ setId: "01FILM", device, startedAt: T0, lastWatchedAt: T0 + 20_000, seconds: 20, againAt: null, updatedAt: T0 + 20_000 }],
      days: [{ day: "2026-10-03", device, seconds: 20, updatedAt: T0 + 20_000 }],
    });
    // The position itself is written as it always was.
    expect(state.snapshot(me).progress).toEqual([{ setId: "01FILM", at: 120, duration: 7200, updatedAt: T0 + 20_000 }]);
  });

  test("a pause, a seek forward and a seek back count only the time spent watching", () => {
    const { state, me, path } = stateAt();
    at(0); state.setProgress(me, "01FILM", 100, 7200);
    at(3); state.setProgress(me, "01FILM", 103, 7200); // paused here
    at(613); state.setProgress(me, "01FILM", 113, 7200); // ten seconds after resuming
    at(623); state.setProgress(me, "01FILM", 1900, 7200); // seeked forward
    at(633); state.setProgress(me, "01FILM", 50, 7200); // seeked back
    expect(counted(path).titles[0]!.seconds).toBe(23);
  });

  test("a step across midnight counts on the day it ends", () => {
    const { state, me, path } = stateAt();
    setSystemTime(new Date(2026, 9, 3, 23, 59, 55));
    state.setProgress(me, "01FILM", 100, 7200);
    setSystemTime(new Date(2026, 9, 4, 0, 0, 5));
    state.setProgress(me, "01FILM", 110, 7200);
    expect(counted(path).days).toMatchObject([{ day: "2026-10-04", seconds: 10 }]);
  });

  test("a restarted server counts nothing for its first write of each title", () => {
    const first = stateAt();
    at(0); first.state.setProgress(first.me, "01FILM", 100, 7200);
    at(10); first.state.setProgress(first.me, "01FILM", 110, 7200);
    first.state.close();

    const second = stateAt(first.path);
    at(20); second.state.setProgress(second.me, "01FILM", 120, 7200);
    expect(counted(first.path).titles[0]).toMatchObject({ seconds: 10, startedAt: T0 });
    at(30); second.state.setProgress(second.me, "01FILM", 130, 7200);
    expect(counted(first.path).titles[0]!.seconds).toBe(20);
  });

  test("two tabs on one title never count more than the wall clock", () => {
    const { state, me, path } = stateAt();
    // Tab A near the start, tab B further on, saving in turn every 5 s.
    for (const [second, position] of [[0, 100], [5, 500], [10, 110], [15, 510], [20, 120]] as const) {
      at(second); state.setProgress(me, "01FILM", position, 7200);
    }
    const { seconds } = counted(path).titles[0]!;
    expect(seconds).toBeLessThanOrEqual(20);
    expect(seconds).toBe(10);
  });

  test("a clock that stepped back still moves each row's stamp forward", () => {
    const { state, me, path } = stateAt();
    at(100); state.setProgress(me, "01FILM", 100, 7200);
    at(110); state.setProgress(me, "01FILM", 110, 7200);
    // The clock goes back a minute; playing carries on.
    at(50); state.setProgress(me, "01FILM", 120, 7200);
    expect(counted(path).titles[0]!.updatedAt).toBe(T0 + 110_001);
    at(60); state.setProgress(me, "01FILM", 130, 7200);
    const { titles, days } = counted(path);
    expect(titles[0]).toMatchObject({ seconds: 20, lastWatchedAt: T0 + 60_000, updatedAt: T0 + 110_002 });
    expect(days).toMatchObject([{ seconds: 20, updatedAt: T0 + 110_001 }]);
  });

  test("finishing forgets the last write, so playing on counts from a fresh first write", () => {
    const { state, me, path } = stateAt();
    at(0); state.setProgress(me, "01FILM", 100, 7200);
    at(10); state.setProgress(me, "01FILM", 110, 7200);
    at(12); state.setWatched(me, "01FILM", true);
    at(20); state.setProgress(me, "01FILM", 115, 7200);
    expect(counted(path).titles[0]!.seconds).toBe(10);
  });

  test("starting a finished title over marks it watched again, once, and counts on top", () => {
    const { state, me, path } = stateAt();
    at(0); state.setProgress(me, "01FILM", 100, 7200);
    at(10); state.setProgress(me, "01FILM", 110, 7200);
    at(20); state.setWatched(me, "01FILM", true);
    at(60); state.setProgress(me, "01FILM", 0, 7200);
    at(70); state.setProgress(me, "01FILM", 10, 7200);
    at(80); state.setProgress(me, "01FILM", 20, 7200);
    expect(counted(path).titles[0]).toMatchObject({ startedAt: T0, againAt: T0 + 60_000, seconds: 30 });
  });

  test("a position merged in from another device counts nothing here", () => {
    const { state, me, path } = stateAt();
    at(0);
    state.importMerged(mergeStates([{
      format: 1, device: "phone", writtenAt: 0,
      profiles: [{ name: "André", progress: [{ setId: "01FILM", at: 900, duration: 7200, updatedAt: T0 }], watched: [] }],
    }]));
    expect(counted(path)).toEqual({ titles: [], days: [] });

    // Nor does it leave a last write behind for this device's next one to count from.
    at(10); state.setProgress(me, "01FILM", 910, 7200);
    expect(counted(path).titles).toMatchObject([{ seconds: 0, againAt: null }]);
  });

  test("a write for a profile another device deleted is refused quietly and counts nothing", () => {
    const { state, path } = stateAt();
    const gone = state.createProfile("Ben")!.id;
    state.deleteProfile(gone);
    at(0); expect(() => state.setProgress(gone, "01FILM", 100, 7200)).not.toThrow();
    at(10); expect(() => state.setProgress(gone, "01FILM", 110, 7200)).not.toThrow();
    expect(counted(path)).toEqual({ titles: [], days: [] });
  });

  test("a player that cannot remember counts nothing and does not throw", () => {
    expect(() => new WatchState(null).setProgress("p1", "01FILM", 100, 7200)).not.toThrow();
  });
});
```

- [ ] **Step 2: Run, expect failures**

Run: `cd web && bun test test/state-stats-recording.test.ts`
Expected: 2 pass ("a write for a profile another device deleted…", "a player that cannot remember…" — both already hold), 9 fail — the stats tables stay empty.

- [ ] **Step 3: Implement** — `web/src/state/stats-recorder.ts`

```ts
/**
 * A position write, and the watching it adds up to — in one transaction.
 *
 * `WatchState.setProgress` calls this rather than writing the position
 * itself, so a position and the minutes it implies can never land apart.
 * Only this device's own writes come through here: positions merged in from
 * other devices (`importMerged`) are never counted, or every sync round would
 * count another device's evening again.
 *
 * The last write per title is held in memory by the caller and never stored
 * or synced: a restarted server starts empty, and its first write per title
 * counts nothing. See `stats-step.ts` for what one step counts.
 */

import type { Database } from "bun:sqlite";
import { againNow, stepSeconds, type LastTick } from "./stats-step";

/** Each title's last position write, keyed by `tickKey`. */
export type Ticks = Map<string, LastTick>;

/** The key a title's last write is held under: JSON, as ids may hold any text. */
export const tickKey = (profileId: string, setId: string) => JSON.stringify([profileId, setId]);

/** `YYYY-MM-DD` on this machine's own calendar — the household server's. */
export function localDay(ms: number): string {
  const date = new Date(ms);
  const two = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())}`;
}

const UPSERT_PROGRESS = `INSERT INTO progress(profile_id, set_id, at_seconds, duration, updated_at)
  VALUES (?1, ?2, ?3, ?4, ?5)
  ON CONFLICT(profile_id, set_id) DO UPDATE SET
    at_seconds = excluded.at_seconds,
    duration = excluded.duration,
    updated_at = excluded.updated_at`;

// `started_at` is written once, by the insert; `again_at` only ever moves
// forward to a later restart, never back to null. `updated_at` never moves
// back either: a clock that stepped back must not give this device's newer
// row an older stamp than a copy other devices hold, or the next import would
// put the older seconds back over it.
const UPSERT_TITLE = `INSERT INTO stats_titles(profile_id, set_id, device, started_at, last_watched_at, seconds, again_at, updated_at)
  VALUES (?1, ?2, ?3, ?4, ?4, ?5, ?6, ?4)
  ON CONFLICT(profile_id, set_id, device) DO UPDATE SET
    seconds = seconds + excluded.seconds,
    last_watched_at = excluded.last_watched_at,
    again_at = COALESCE(excluded.again_at, again_at),
    updated_at = MAX(excluded.updated_at, updated_at + 1)`;

const UPSERT_DAY = `INSERT INTO stats_days(profile_id, day, device, seconds, updated_at)
  VALUES (?1, ?2, ?3, ?4, ?5)
  ON CONFLICT(profile_id, day, device) DO UPDATE SET
    seconds = seconds + excluded.seconds,
    updated_at = MAX(excluded.updated_at, updated_at + 1)`;

/**
 * Writes where `profileId` is in `setId`, and counts the step since the
 * title's last write into its title row and today's day row, all or none.
 * A refusal (a profile another device deleted) rolls everything back and
 * leaves `ticks` as it was.
 */
export function writeProgress(
  db: Database,
  ticks: Ticks,
  device: string,
  profileId: string,
  setId: string,
  rawAt: number,
  duration: number | null,
): void {
  const at = Math.max(0, rawAt);
  const now = Date.now();
  const key = tickKey(profileId, setId);
  const step = stepSeconds(ticks.get(key), at, now);
  db.transaction(() => {
    const hadProgress = db.query("SELECT 1 FROM progress WHERE profile_id = ?1 AND set_id = ?2")
      .get(profileId, setId) !== null;
    const watchedLive = db.query("SELECT 1 FROM watched WHERE profile_id = ?1 AND set_id = ?2 AND removed_at IS NULL")
      .get(profileId, setId) !== null;
    db.query(UPSERT_PROGRESS).run(profileId, setId, at, duration, now);
    db.query(UPSERT_TITLE).run(profileId, setId, device, now, step, againNow(hadProgress, watchedLive) ? now : null);
    // A step that spans midnight counts on the day of the write that ends it.
    if (step > 0) db.query(UPSERT_DAY).run(profileId, localDay(now), device, step, now);
  })();
  ticks.set(key, { at, wallMs: now });
}
```

In `web/src/state/store.ts`:

After line 28 (`import { exportWatched, importUnwatched, importWatched } from "./watched-exchange";`) add:

```ts
import { tickKey, writeProgress, type Ticks } from "./stats-recorder";
```

After line 99 (`  private readonly db: Database | null;`) add:

```ts
  private readonly ticks: Ticks = new Map(); // each title's last position write, this process only
```

Replace lines 214-228 (the doc line `/** Where this profile is in \`setId\`. */` through the closing `}` of `setProgress`) with:

```ts
  /** Where this profile is in `setId`, and the watching that adds — see `stats-recorder.ts`. */
  setProgress(profileId: string, setId: string, at: number, duration: number | null): void {
    const db = this.db;
    if (db) tolerate(() => writeProgress(db, this.ticks, this.deviceId(), profileId, setId, at, duration));
  }
```

In `setWatched`, after line 287 (`      })());`, the end of the finishing transaction) add:

```ts
      this.ticks.delete(tickKey(profileId, setId));
```

- [ ] **Step 4: Run, expect PASS**

Run: `cd web && bun test test/state-stats-recording.test.ts test/state-store.test.ts test/state-two-machines.test.ts test/state-write-failures.test.ts test/state-http.test.ts test/code-standards.test.ts && bun run typecheck`
Expected: 0 fail; `tsc` silent. `wc -l src/state/store.ts` → 791.

- [ ] **Step 5: Commit**

```bash
git add web/src/state/stats-recorder.ts web/src/state/store.ts web/test/state-stats-recording.test.ts
git commit -m "feat(web): count watch time inside each position write"
```

---

## Task 2.3: export, import and the summary read

**Files:**
- Create: `web/src/state/stats-exchange.ts`
- Create: `web/test/state-stats-exchange.test.ts`
- Modify: `web/src/state/store.ts` (imports, `stats()`, one line each in `exportRecord` and `importMerged`)
- Modify: `web/test/state-sync.test.ts` (two counts, one rollback case)

**Interfaces:**
- Produces: `exportStats(db, profileId): StatsRows` (every device's rows, ordered `set_id, device` / `day, device` so an unchanged export stays byte-identical for `sync.ts`'s `pushIfChanged`, each key omitted when empty, `againAt` omitted when null); `importStats(db, profileId, rows: StatsRows): number` (an `INSERT … ON CONFLICT DO UPDATE … WHERE excluded.updated_at > updated_at` — inserts what is missing, replaces only with newer, never deletes; counts changes); `readSummary(db, profileId, today): StatsSummary` (rows of every device + live `watched` only); `WatchState.stats(profileId)` — `today` = `localDay(Date.now())`, the server's local date (contract §5).
- `importMerged` passes the whole `MergedProfile` (it `extends StatsRows`) inside its existing transaction (`store.ts:422-470`).

- [ ] **Step 1: Failing tests** — `web/test/state-stats-exchange.test.ts`

```ts
/**
 * Viewing stats between machines, and the summary read from them: exported
 * whole, taken in only when newer, never counted twice, never deleted.
 */

import { afterEach, describe, expect, setSystemTime, test } from "bun:test";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { mergeStates, type MergedState } from "../src/state/merge";
import type { DayStatRow, TitleStatRow } from "../src/state/stats-record";
import { parseRecord } from "../src/state/sync-record";
import { WatchState } from "../src/state/store";

const dirs: string[] = [];
function machine(device: string) {
  const dir = mkdtempSync(join(tmpdir(), `mediagram-${device}-`));
  dirs.push(dir);
  const state = new WatchState(join(dir, "state.db"));
  const me = state.createProfile("André")!.id;
  return { state, me, device };
}
afterEach(() => {
  setSystemTime();
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

const T0 = new Date(2026, 9, 3, 21, 0, 0).getTime();
const at = (seconds: number) => setSystemTime(new Date(T0 + seconds * 1000));

/** What crosses the channel: text, parsed as a stranger's. */
const publish = (m: ReturnType<typeof machine>) => parseRecord(JSON.stringify(m.state.exportRecord(m.device)))!;
const sync = (m: ReturnType<typeof machine>, channel: ReturnType<typeof publish>[]) =>
  m.state.importMerged(mergeStates([publish(m), ...channel]));

/** Twenty seconds of `setId` watched on `m`, from `from` seconds past T0. */
function watch(m: ReturnType<typeof machine>, setId: string, from: number) {
  for (const step of [0, 10, 20]) {
    at(from + step);
    m.state.setProgress(m.me, setId, 100 + step, 7200);
  }
}

/** A hand-built merge for André that carries only stats rows. */
const merged = (titleStats: TitleStatRow[], dayStats: DayStatRow[] = []): MergedState =>
  ({ profiles: [{ name: "andré", displayName: "André", progress: [], watched: [], titleStats, dayStats }] });

const row = (seconds: number, updatedAt: number, device = "phone"): TitleStatRow =>
  ({ setId: "01FILM", device, startedAt: 1000, lastWatchedAt: updatedAt, seconds, updatedAt });
const day = (seconds: number, updatedAt: number): DayStatRow =>
  ({ day: "2026-10-03", device: "phone", seconds, updatedAt });

describe("the export", () => {
  test("a profile with no stats says exactly what it said before stats existed", () => {
    const laptop = machine("laptop");
    laptop.state.setPreference(laptop.me, "profile", "subtitle", "de");
    const text = JSON.stringify(laptop.state.exportRecord("laptop"));
    expect(text).not.toContain("titleStats");
    expect(text).not.toContain("dayStats");
  });

  test("a title never restarted crosses the wire without an againAt, and is kept", () => {
    const laptop = machine("laptop");
    watch(laptop, "01FILM", 0);
    expect(publish(laptop).profiles[0]!.titleStats).toEqual([{
      setId: "01FILM", device: laptop.state.deviceId(), startedAt: T0, lastWatchedAt: T0 + 20_000, seconds: 20, updatedAt: T0 + 20_000,
    }]);
  });
});

describe("taking rows in", () => {
  test("a newer row replaces the one held; an older or equal one changes nothing", () => {
    const { state, me } = machine("laptop");
    expect(state.importMerged(merged([row(600, 2000)], [day(600, 2000)]))).toBe(2);
    expect(state.importMerged(merged([row(300, 1000)], [day(300, 1000)]))).toBe(0);
    expect(state.importMerged(merged([row(900, 2000)]))).toBe(0);
    expect(state.stats(me).history[0]!.seconds).toBe(600);

    expect(state.importMerged(merged([row(900, 3000)]))).toBe(1);
    expect(state.stats(me).history[0]!.seconds).toBe(900);
  });

  test("a merge that says nothing about stats deletes nothing", () => {
    const laptop = machine("laptop");
    watch(laptop, "01FILM", 0);
    laptop.state.importMerged({ profiles: [{ name: "andré", displayName: "André", progress: [], watched: [] }] });
    expect(laptop.state.stats(laptop.me).allSeconds).toBe(20);
  });

  test("this device's own row comes back when newer — a reinstall that kept its id", () => {
    const { state, me } = machine("laptop");
    expect(state.importMerged(merged([row(1200, 5000, state.deviceId())]))).toBe(1);
    expect(state.stats(me).history).toEqual([{ kind: "started", setId: "01FILM", at: 1000, seconds: 1200 }]);
  });
});

describe("two machines", () => {
  test("minutes counted on one show up on the other, and later rounds add nothing", () => {
    const laptop = machine("laptop");
    const desktop = machine("desktop");
    watch(laptop, "01FILM", 0);

    sync(desktop, [publish(laptop)]);
    expect(desktop.state.stats(desktop.me).allSeconds).toBe(20);
    // The laptop's rows now travel in the desktop's document too.
    sync(desktop, [publish(laptop)]);
    sync(laptop, [publish(desktop)]);
    expect(desktop.state.stats(desktop.me).allSeconds).toBe(20);
    expect(laptop.state.stats(laptop.me).allSeconds).toBe(20);

    watch(desktop, "01FILM", 100);
    sync(laptop, [publish(desktop)]);
    expect(laptop.state.stats(laptop.me)).toMatchObject({
      allSeconds: 40,
      history: [{ kind: "started", setId: "01FILM", at: T0, seconds: 40 }],
    });
  });

  test("a device that went away keeps its minutes, passed on by one that heard them", () => {
    const laptop = machine("laptop");
    const desktop = machine("desktop");
    const phone = machine("phone");
    watch(laptop, "01FILM", 0);
    sync(desktop, [publish(laptop)]);

    sync(phone, [publish(desktop)]); // the laptop's own document is gone
    expect(phone.state.stats(phone.me).allSeconds).toBe(20);
  });
});

describe("the summary", () => {
  test("totals and bars as of today, and a restart as its own history line", () => {
    const { state, me } = machine("laptop");
    at(0); state.setProgress(me, "01FILM", 100, 7200);
    at(10); state.setProgress(me, "01FILM", 110, 7200);
    at(20); state.setWatched(me, "01FILM", true);
    at(60); state.setProgress(me, "01FILM", 0, 7200);
    at(70); state.setProgress(me, "01FILM", 10, 7200);

    const summary = state.stats(me);
    expect(summary).toMatchObject({ weekSeconds: 20, monthSeconds: 20, allSeconds: 20 });
    expect(summary.last30.at(-1)).toEqual({ day: "2026-10-03", seconds: 20 });
    expect(summary.history).toEqual([
      { kind: "again", setId: "01FILM", at: T0 + 60_000, seconds: 20 },
      { kind: "finished", setId: "01FILM", at: T0 + 20_000, seconds: 20 },
      { kind: "started", setId: "01FILM", at: T0, seconds: 20 },
    ]);
  });

  test("a title un-marked as finished is not finished history", () => {
    const { state, me } = machine("laptop");
    state.setWatched(me, "01DONE", true);
    state.setWatched(me, "01DONE", false);
    expect(state.stats(me).history).toEqual([]);
  });

  test("a player that cannot remember answers an empty summary", () => {
    const summary = new WatchState(null).stats("p1");
    expect(summary).toMatchObject({ weekSeconds: 0, monthSeconds: 0, allSeconds: 0, history: [] });
    expect(summary.last30).toHaveLength(30);
  });
});
```

and in `web/test/state-sync.test.ts`:

- line 274: replace `    expect(first).toEqual({ pulled: 1, pushed: false, failed: message });` with

```ts
    // The position, and the title row its write started.
    expect(first).toEqual({ pulled: 2, pushed: false, failed: message });
```

- line 306: replace `  test.each(["profiles", "progress", "watched", "collection_items"])(` with

```ts
  test.each(["profiles", "progress", "watched", "collection_items", "stats_titles"])(
```

- line 337: replace `        expect(await sync.once()).toEqual({ pulled: 6, pushed: true });` with

```ts
        // Six rows as before, and the title row Sam's position write started.
        expect(await sync.once()).toEqual({ pulled: 7, pushed: true });
```

- [ ] **Step 2: Run, expect failures**

Run: `cd web && bun test test/state-stats-exchange.test.ts test/state-sync.test.ts`
Expected: 16 pass, 16 fail — nine of the ten new cases (`state.stats is not a function`, or no `titleStats` on the export; "a profile with no stats says exactly what it said…" already holds), and seven `state-sync` cases: the two count assertions and the five rollback cases, which now expect 7.

- [ ] **Step 3: Implement** — `web/src/state/stats-exchange.ts`

```ts
/**
 * Viewing stats between the tables and the wire, and the summary the page
 * reads.
 *
 * Kept out of `store.ts` for the reason `watched-exchange.ts` is: turning
 * rows into wire rows and back is one job. Every device's rows go out, not
 * only this one's — passed on, so a device that goes away keeps its minutes —
 * and nothing is ever deleted on the way back in.
 */

import type { Database } from "bun:sqlite";
import type { DayStatRow, StatsRows, TitleStatRow } from "./stats-record";
import { summarize, type StatsSummary } from "./stats-summary";

/** Every row this profile holds, every device's, in a stable order. */
function statsRows(db: Database | null, profileId: string): { titles: TitleStatRow[]; days: DayStatRow[] } {
  if (!db) return { titles: [], days: [] };
  const titles = (db
    .query(
      `SELECT set_id AS setId, device, started_at AS startedAt, last_watched_at AS lastWatchedAt,
              seconds, again_at AS againAt, updated_at AS updatedAt
         FROM stats_titles WHERE profile_id = ?1 ORDER BY set_id, device`,
    )
    .all(profileId) as (Omit<TitleStatRow, "againAt"> & { againAt: number | null })[])
    // Absent, never `null`: a reader drops a row whose `againAt` is not a number.
    .map(({ againAt, ...row }) => (againAt === null ? row : { ...row, againAt }));
  const days = db
    .query("SELECT day, device, seconds, updated_at AS updatedAt FROM stats_days WHERE profile_id = ?1 ORDER BY day, device")
    .all(profileId) as DayStatRow[];
  return { titles, days };
}

/** The two keys for the wire, each omitted when empty — so a profile with no
 * stats exports exactly what it did before stats existed. */
export function exportStats(db: Database | null, profileId: string): StatsRows {
  const { titles, days } = statsRows(db, profileId);
  return {
    ...(titles.length > 0 ? { titleStats: titles } : {}),
    ...(days.length > 0 ? { dayStats: days } : {}),
  };
}

/**
 * Takes in each merged row that is newer than the local one, or missing.
 * Never deletes. A row naming this device is taken too when newer: a
 * reinstall that kept its device id gets its minutes back.
 */
export function importStats(db: Database, profileId: string, rows: StatsRows): number {
  let changed = 0;
  for (const row of rows.titleStats ?? []) {
    changed += db.query(
      `INSERT INTO stats_titles(profile_id, set_id, device, started_at, last_watched_at, seconds, again_at, updated_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
         ON CONFLICT(profile_id, set_id, device) DO UPDATE SET
           started_at = excluded.started_at, last_watched_at = excluded.last_watched_at, seconds = excluded.seconds,
           again_at = excluded.again_at, updated_at = excluded.updated_at
         WHERE excluded.updated_at > stats_titles.updated_at`,
    ).run(profileId, row.setId, row.device, row.startedAt, row.lastWatchedAt, row.seconds, row.againAt ?? null, row.updatedAt)
      .changes;
  }
  for (const row of rows.dayStats ?? []) {
    changed += db.query(
      `INSERT INTO stats_days(profile_id, day, device, seconds, updated_at) VALUES (?1, ?2, ?3, ?4, ?5)
         ON CONFLICT(profile_id, day, device) DO UPDATE SET seconds = excluded.seconds, updated_at = excluded.updated_at
         WHERE excluded.updated_at > stats_days.updated_at`,
    ).run(profileId, row.day, row.device, row.seconds, row.updatedAt).changes;
  }
  return changed;
}

/** What the stats page shows for `profileId`, as of `today`. */
export function readSummary(db: Database | null, profileId: string, today: string): StatsSummary {
  const { titles, days } = statsRows(db, profileId);
  const watched = (db
    ?.query("SELECT set_id AS setId, finished_at AS finishedAt FROM watched WHERE profile_id = ?1 AND removed_at IS NULL")
    .all(profileId) ?? []) as { setId: string; finishedAt: number }[];
  return summarize({ today, titles, days, watched });
}
```

In `web/src/state/store.ts`:

Replace `import { tickKey, writeProgress, type Ticks } from "./stats-recorder";` with:

```ts
import { localDay, tickKey, writeProgress, type Ticks } from "./stats-recorder";
import { exportStats, importStats, readSummary } from "./stats-exchange";
```

After `setProgress`'s closing `}` add:

```ts

  /** This profile's viewing stats, as of today on this machine's calendar. */
  stats(profileId: string) {
    return readSummary(this.db, profileId, localDay(Date.now()));
  }
```

In `exportRecord`, after `        preferences: exportPreferences(this.db, profile.id),` add:

```ts
        ...exportStats(this.db, profile.id),
```

In `importMerged`, after `        changed += importPreferences(this.db, profileId, profile.preferences ?? []);` add:

```ts
        changed += importStats(this.db, profileId, profile);
```

- [ ] **Step 4: Run, expect PASS — and the whole suite, since export now carries rows**

Run: `cd web && bun test && bun run typecheck`
Expected: 0 fail; `tsc` silent. `wc -l src/state/store.ts` → 799. (When this plan was written, only the three `state-sync.test.ts` lines above moved with this change.)

- [ ] **Step 5: Commit**

```bash
git add web/src/state/stats-exchange.ts web/src/state/store.ts web/test/state-stats-exchange.test.ts web/test/state-sync.test.ts
git commit -m "feat(web): sync viewing-stats rows between devices and summarize them"
```

---

## Task 2.4: `GET /api/profiles/{profileId}/stats`

**Files:**
- Create: `web/src/state/route-shared.ts` (`P`, `parse`, `json`, `status` — moved out of `routes.ts`)
- Create: `web/src/state/stats-routes.ts`
- Create: `web/test/state-stats-route.test.ts`
- Modify: `web/src/state/routes.ts`

**Interfaces:**
- Produces: `statsRoute(state: WatchState, request: PlayerRequest): PlayerResponse | null` — `null` off its path; `405` for anything but GET/HEAD; `404` when `!state.has(profileId)`; `200` `application/json` `StatsSummary` otherwise (contract §5).
- Why `route-shared.ts`: `routes.ts` is at its ceiling (267/267) and `stats-routes.ts` needs `P`, `json` and `status`. Importing them from `routes.ts` would be a cycle evaluated at load (`STATS` is built from `P` at module scope), so both import a shared module instead; `routes.ts` drops to 246.
- `src/routes.ts:96-100` treats a GET as no write, so this read never triggers a sync push.

- [ ] **Step 1: Failing tests** — `web/test/state-stats-route.test.ts`

```ts
/** `GET /api/profiles/{profileId}/stats`, through the state router. */

import { afterEach, expect, setSystemTime, test } from "bun:test";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { createStateRouter } from "../src/state/routes";
import { WatchState } from "../src/state/store";

const dirs: string[] = [];
function routed() {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-stats-route-"));
  dirs.push(dir);
  const state = new WatchState(join(dir, "state.db"));
  const me = state.createProfile("André")!.id;
  const route = createStateRouter({ state, isPlayable: () => true });
  const ask = (method: string, path = `/api/profiles/${me}/stats`) => route({ method, path, range: null })!;
  return { state, me, ask };
}
afterEach(() => {
  setSystemTime();
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

const bodyOf = (response: { body: unknown }) => JSON.parse(new TextDecoder().decode(response.body as Uint8Array));

test("answers the profile's summary, as of today on this server's calendar", () => {
  const { state, me, ask } = routed();
  setSystemTime(new Date(2026, 9, 3, 21, 0, 0));
  state.setProgress(me, "01FILM", 100, 7200);
  setSystemTime(new Date(2026, 9, 3, 21, 0, 10));
  state.setProgress(me, "01FILM", 110, 7200);

  const response = ask("GET");
  expect(response.status).toBe(200);
  expect(response.headers["content-type"]).toBe("application/json");
  const summary = bodyOf(response);
  expect(summary).toMatchObject({ weekSeconds: 10, monthSeconds: 10, allSeconds: 10 });
  expect(summary.last30.at(-1)).toEqual({ day: "2026-10-03", seconds: 10 });
  expect(summary.history).toEqual([
    { kind: "started", setId: "01FILM", at: new Date(2026, 9, 3, 21, 0, 0).getTime(), seconds: 10 },
  ]);
});

test("an unknown profile is a 404, a write is a 405, a HEAD has no body", () => {
  const { ask } = routed();
  expect(ask("GET", "/api/profiles/nobody/stats").status).toBe(404);
  expect(ask("POST").status).toBe(405);
  expect(ask("DELETE").status).toBe(405);
  const head = ask("HEAD");
  expect(head.status).toBe(200);
  expect(head.body).toBeNull();
});
```

- [ ] **Step 2: Run, expect failures**

Run: `cd web && bun test test/state-stats-route.test.ts`
Expected: FAIL — the GET is answered `405` by the write gate (`routes.ts:132`), and the unknown-profile GET likewise.

- [ ] **Step 3: Implement** — `web/src/state/route-shared.ts`

```ts
/**
 * What every state route module shares: the profile path segment, reading a
 * body, and the two shapes of answer.
 *
 * Its own module so `routes.ts` and `stats-routes.ts` both import it rather
 * than one importing the other.
 */

import type { PlayerResponse } from "../http/contracts";
import { bodiless, withBody } from "../response";

/**
 * The profile is a path segment, not a parameter.
 *
 * `navigator.sendBeacon` is how a position survives the tab closing, and it
 * cannot set a header — so whoever is watching has to travel in the URL. In
 * the path rather than the query because a missing segment is then a route
 * that does not match, and the alternative to a 404 is a write that quietly
 * lands in somebody else's rows.
 */
export const P = "([A-Za-z0-9-]{1,64})";

export function parse(body: string | null | undefined): unknown {
  if (typeof body !== "string" || body === "") return null;
  try {
    return JSON.parse(body);
  } catch {
    return null;
  }
}

export const json = (body: string, headOnly: boolean, code = 200): PlayerResponse =>
  withBody(body, "application/json", { headOnly, status: code });

export const status = bodiless;
```

`web/src/state/stats-routes.ts`

```ts
/**
 * `GET /api/profiles/{profileId}/stats`: one profile's viewing stats, as of
 * today on this server's calendar — the household's.
 *
 * A read, so it sits ahead of the write checks in `routes.ts`, which
 * dispatches here. Each profile asks for its own; nothing here lists anyone
 * else's.
 */

import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import { json, P, status } from "./route-shared";
import type { WatchState } from "./store";

const STATS = new RegExp(`^/api/profiles/${P}/stats$`);

/** The answer for a stats path, or `null` when the path is not this one. */
export function statsRoute(state: WatchState, request: PlayerRequest): PlayerResponse | null {
  const matched = STATS.exec(request.path);
  if (!matched) return null;
  if (request.method !== "GET" && request.method !== "HEAD") return status(405);
  const profileId = matched[1]!;
  if (!state.has(profileId)) return status(404);
  return json(JSON.stringify(state.stats(profileId)), request.method === "HEAD");
}
```

In `web/src/state/routes.ts`:

Replace lines 12-27 — from `import type { PlayerRequest, PlayerResponse } from "../http/contracts";` through `const PROFILES = /^\/api\/profiles$/;`, which takes out the `../response` import and the `P` doc comment and constant — with:

```ts
import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import { refuseUnsafeBrowserWrite } from "../http/browser-write";
import type { WatchState } from "./store";
import { json, P, parse, status } from "./route-shared";
import { statsRoute } from "./stats-routes";

const PROFILES = /^\/api\/profiles$/;
```

After the state-snapshot block (line 129, its closing `}`), before `// Past here everything writes, so everything is checked.`, add:

```ts
    const stats = statsRoute(state, request);
    if (stats) return stats;
```

Delete lines 254-267 (the blank line, `function parse…`, `const json…`, `const status = bodiless;`) — they now come from `route-shared.ts`.

- [ ] **Step 4: Run, expect PASS**

Run: `cd web && bun test test/state-stats-route.test.ts test/state-http.test.ts test/settings-admin-gate.test.ts test/code-standards.test.ts && bun run typecheck`
Expected: 0 fail; `tsc` silent. `wc -l src/state/routes.ts` → 246.

- [ ] **Step 5: Commit**

```bash
git add web/src/state/route-shared.ts web/src/state/stats-routes.ts web/src/state/routes.ts web/test/state-stats-route.test.ts
git commit -m "feat(web): answer a profile's viewing stats over the state API"
```

---

## Task 2.5: the page's words — durations, times, titles, history lines

**Files:**
- Create: `web/public/lib/catalog/stats-format.js`
- Create: `web/test/stats-format.test.ts`

**Interfaces:**
- Produces: `watchTime(seconds)`, `whenLabel(at, now)`, `shortDate(day)`, `weekdayInitial(day)`, `historyTitle(set | null)`, `historyLine(entry, set | null, now)`, `KIND_LABELS` — every string from contract §6: "under a minute" / "42 min" / "3 h 12 min" / "3 h"; "today 21:14" / "Sat 21:14" (1–6 calendar days back) / "21 Sep" / "21 Sep 2025"; "Started", "Finished", "Watched again"; "No longer in the library".
- Title rule (contract §6, "the way Continue watching names it", `home-resume.js:32-33`): a film by `title`; an episode or lesson by `show` + `episodeLabel` — "Crime 101 S1E4", "Geldhochschule 3". Phase 04 copies this.
- Also contract §6: a history line with `seconds === 0` (a finish from before stats, no title row) carries no duration; a total of 0 s reads "under a minute".

- [ ] **Step 1: Failing tests** — `web/test/stats-format.test.ts`

```ts
/** The Stats page's words: durations, times, names and history lines. */

import { describe, expect, test } from "bun:test";
import { historyLine, historyTitle, shortDate, watchTime, weekdayInitial, whenLabel } from "../public/lib/catalog/stats-format.js";

describe("watchTime", () => {
  test.each([
    [0, "under a minute"],
    [59.9, "under a minute"],
    [60, "1 min"],
    [42 * 60 + 59, "42 min"],
    [59 * 60, "59 min"],
    [3600, "1 h"],
    [3 * 3600, "3 h"],
    [3 * 3600 + 12 * 60 + 59, "3 h 12 min"],
    [250 * 3600 + 60, "250 h 1 min"],
  ])("%p seconds is %p", (seconds, words) => {
    expect(watchTime(seconds)).toBe(words);
  });
});

describe("whenLabel", () => {
  // Saturday 3 October 2026, 21:30 on this machine's clock.
  const now = new Date(2026, 9, 3, 21, 30).getTime();
  test.each([
    ["earlier today", new Date(2026, 9, 3, 0, 10), "today 00:10"],
    ["late last night", new Date(2026, 9, 2, 23, 50), "Fri 23:50"],
    ["six days ago", new Date(2026, 8, 27, 9, 5), "Sun 09:05"],
    ["seven days ago", new Date(2026, 8, 26, 21, 14), "26 Sep"],
    ["earlier this year", new Date(2026, 0, 2, 8, 0), "2 Jan"],
    ["another year", new Date(2025, 8, 21, 21, 14), "21 Sep 2025"],
    ["a clock ahead of this one, on a later day", new Date(2026, 9, 5, 9, 0), "5 Oct"],
  ])("%s", (_label, then, words) => {
    expect(whenLabel(then.getTime(), now)).toBe(words);
  });
});

test("calendar days, not 24-hour spans, across the clocks going back", () => {
  // In a zone with daylight saving (Europe's ends 25 October 2026) one of
  // these days is 25 hours long; elsewhere this is an ordinary week.
  const now = new Date(2026, 9, 27, 0, 30).getTime();
  expect(whenLabel(new Date(2026, 9, 21, 23, 59).getTime(), now)).toBe("Wed 23:59");
  expect(whenLabel(new Date(2026, 9, 20, 23, 59).getTime(), now)).toBe("20 Oct");
});

test("a day's date and weekday initial", () => {
  expect(shortDate("2026-10-03")).toBe("3 Oct");
  expect(weekdayInitial("2026-10-03")).toBe("S");
  expect(weekdayInitial("2026-09-28")).toBe("M");
});

describe("historyTitle names a set the way Continue watching does", () => {
  test("a film by its title", () => {
    expect(historyTitle({ kind: "movie", title: "Der Pate", show: null, setId: "01A" })).toBe("Der Pate");
  });
  test("an episode by its show and number", () => {
    expect(historyTitle({ kind: "ep", title: "Pilot", show: "Crime 101", season: 1, episode: "4", setId: "01B" })).toBe("Crime 101 S1E4");
  });
  test("a lesson by its course and number", () => {
    expect(historyTitle({ kind: "tut", title: "Zinsen", show: "Geldhochschule", episode: "3", setId: "01C" })).toBe("Geldhochschule 3");
  });
  test("a set the library no longer holds", () => {
    expect(historyTitle(null)).toBe("No longer in the library");
  });
});

describe("historyLine", () => {
  const now = new Date(2026, 9, 3, 21, 30).getTime();
  const film = { kind: "movie", title: "Der Pate", show: null, setId: "01A" };
  const saturday = new Date(2026, 9, 3, 21, 14).getTime();
  const friday = new Date(2026, 9, 2, 20, 0).getTime();

  test.each([
    [{ kind: "started", at: friday, seconds: 42 * 60 }, "Started · Der Pate · Fri 20:00 · 42 min"],
    [{ kind: "again", at: saturday, seconds: 3 * 3600 }, "Watched again · Der Pate · today 21:14 · 3 h"],
    [{ kind: "finished", at: saturday, seconds: 30 }, "Finished · Der Pate · today 21:14 · under a minute"],
    // Finished before stats existed: nothing was counted, so no duration is claimed.
    [{ kind: "finished", at: friday, seconds: 0 }, "Finished · Der Pate · Fri 20:00"],
  ] as const)("%j", (entry, line) => {
    expect(historyLine(entry, film, now)).toBe(line);
  });
});
```

- [ ] **Step 2: Run, expect a failure**

Run: `cd web && bun test test/stats-format.test.ts`
Expected: FAIL — `Cannot find module "../public/lib/catalog/stats-format.js"`.

- [ ] **Step 3: Implement** — `web/public/lib/catalog/stats-format.js`

```js
/**
 * The words and numbers on the Stats page, from the summary the server sends.
 *
 * Hand-rolled English and a 24-hour clock rather than `toLocale…`, for the
 * reason `format.js`'s `endsAt` gives: the page should read the same on every
 * host, and the Android app prints the same strings. Takes the clock rather
 * than reading it, so a test can say what "today" is.
 */

import { episodeLabel } from "../format.js";

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const DAY_MS = 86_400_000;

/** What a history line calls each kind of entry. */
export const KIND_LABELS = { started: "Started", finished: "Finished", again: "Watched again" };

const two = (n) => String(n).padStart(2, "0");
const midnight = (date) => new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();

/**
 * How long, in words: "under a minute", "42 min", "3 h 12 min", "3 h".
 * Minutes are floored — a bar never claims a minute nobody finished.
 * @param {number} seconds
 */
export function watchTime(seconds) {
  const minutes = Math.floor(seconds / 60);
  if (!(minutes >= 1)) return "under a minute";
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`;
}

/**
 * When, relative to `now`: "today 21:14", "Sat 21:14" within the last six
 * days, "21 Sep" before that, "21 Sep 2025" in another year. Calendar days on
 * this device's clock, so 00:10 is "today" and 23:50 last night is not.
 * @param {number} at epoch ms
 * @param {number} now epoch ms
 */
export function whenLabel(at, now) {
  const then = new Date(at);
  const today = new Date(now);
  // Rounded: a day with a daylight-saving change is 23 or 25 hours long.
  const daysAgo = Math.round((midnight(today) - midnight(then)) / DAY_MS);
  const clock = `${two(then.getHours())}:${two(then.getMinutes())}`;
  if (daysAgo === 0) return `today ${clock}`;
  if (daysAgo > 0 && daysAgo <= 6) return `${WEEKDAYS[then.getDay()]} ${clock}`;
  const date = `${then.getDate()} ${MONTHS[then.getMonth()]}`;
  return then.getFullYear() === today.getFullYear() ? date : `${date} ${then.getFullYear()}`;
}

/** "3 Oct", for a `YYYY-MM-DD` day. @param {string} day */
export function shortDate(day) {
  const [, month, date] = day.split("-").map(Number);
  return `${date} ${MONTHS[month - 1]}`;
}

/** "S" for Saturday — the letter under a day's bar. @param {string} day */
export function weekdayInitial(day) {
  return WEEKDAYS[new Date(`${day}T00:00:00Z`).getUTCDay()].slice(0, 1);
}

/**
 * The library's own name for a set, the way Continue watching names it: a
 * film by its title, an episode or lesson by its show and number. A set the
 * library no longer holds — or that this profile cannot see — is not named.
 */
export function historyTitle(set) {
  if (!set) return "No longer in the library";
  if (set.kind !== "movie" && set.show) return [set.show, episodeLabel(set)].filter(Boolean).join(" ");
  return set.title ?? set.setId;
}

/**
 * One history line: "Started · Der Pate · Sat 21:14 · 42 min". No duration
 * when nothing was counted — a finish from before stats existed was not
 * watched in under a minute.
 * @param {{ kind: "started"|"finished"|"again", at: number, seconds: number }} entry
 * @param {any} set the catalog set, or `null`
 * @param {number} now
 */
export function historyLine(entry, set, now) {
  const parts = [KIND_LABELS[entry.kind], historyTitle(set), whenLabel(entry.at, now)];
  if (entry.seconds > 0) parts.push(watchTime(entry.seconds));
  return parts.join(" · ");
}
```

- [ ] **Step 4: Run, expect PASS — in more than one zone**

Run: `cd web && bun test test/stats-format.test.ts && TZ=America/Los_Angeles bun test test/stats-format.test.ts && TZ=Europe/Berlin bun test test/stats-format.test.ts && bun run lint`
Expected: 26 pass, 0 fail each time; `eslint` silent.

- [ ] **Step 5: Commit**

```bash
git add web/public/lib/catalog/stats-format.js web/test/stats-format.test.ts
git commit -m "feat(web): say viewing durations, times and history lines the way the stats page shows them"
```

---

## Task 2.6: the Stats page renderer and its stylesheet

**Files:**
- Create: `web/public/lib/catalog/stats-page.js`
- Create: `web/public/styles/stats.css`
- Create: `web/test/stats-page.test.ts`

**Interfaces:**
- Produces: `renderStats(main, { byId }, stillHere): Promise<void>` — the `renderPerson` shape (`cast.js:85`). Draws the heading "Stats" at once; fetches `/api/profiles/{state.profileId()}/stats`; draws nothing more unless `stillHere()`; "Nothing watched yet." when the history is empty (contract §6 "Empty"); otherwise totals ("This week", "This month", "All time"), "Last 30 days" (30 bars, height ∝ seconds of the busiest day, today rightmost, weekday initial hidden under 768 px), "History" (every entry; CSS `content-visibility: auto` keeps a years-long list cheap to lay out — no pagination code).
- Contract §6 also fixes the failure line "Could not read your stats: <reason>" and each bar's label "3 Oct · 42 min".
- Not wired to a route yet — Task 2.7 does that, so no commit here leaves a link to a page that is not there.

- [ ] **Step 1: Failing tests** — `web/test/stats-page.test.ts` (the shared browser stub fixes `Date.now()` at 0, so times are matched by shape)

```ts
/** The Stats page, drawn from a stubbed `/stats` answer. */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { renderStats } from "../public/lib/catalog/stats-page.js";
import * as state from "../public/lib/watch-state.js";
import { browserEnvironment, settle } from "./support/player-environment";
import { descendants, textOf } from "./support/browser-application";

let env: ReturnType<typeof browserEnvironment>;
beforeEach(async () => {
  env = browserEnvironment();
  env.respondWith(async () => Response.json({}));
  await state.useProfile("viewer");
});
afterEach(async () => {
  await settle();
  await state.useProfile(null);
  env.restore();
});

const days = (seconds: (day: number) => number) =>
  Array.from({ length: 30 }, (_, i) => ({ day: `2026-09-${String(i + 1).padStart(2, "0")}`, seconds: seconds(i) }));

const SUMMARY = {
  weekSeconds: 42 * 60, monthSeconds: 3 * 3600 + 12 * 60, allSeconds: 10 * 3600,
  last30: days((i) => (i === 29 ? 600 : i === 0 ? 300 : 0)),
  history: [
    { kind: "finished", setId: "01A", at: 0, seconds: 7200 },
    { kind: "started", setId: "01GONE", at: 0, seconds: 120 },
  ],
};

function answer(body: unknown, status = 200) {
  env.respondWith(async (url) =>
    url.endsWith("/stats") ? Response.json(body, { status }) : Response.json({}));
}

test("asks for this profile's own stats and draws totals, thirty bars and the history", async () => {
  answer(SUMMARY);
  const main = env.node("main");
  const byId = new Map([["01A", { setId: "01A", kind: "movie", title: "Der Pate", show: null }]]);
  await renderStats(main, { byId }, () => true);

  expect(env.requests.at(-1)!.url).toBe("/api/profiles/viewer/stats");
  const text = textOf(main);
  expect(text).toContain("This week42 min");
  expect(text).toContain("This month3 h 12 min");
  expect(text).toContain("All time10 h");
  const bars = descendants(main).filter((node) => node.className === "stats-bar");
  expect(bars).toHaveLength(30);
  expect(bars.at(-1)!.style.height).toBe("100%");
  expect(bars[0]!.style.height).toBe("50%");
  expect(bars[1]!.style.height).toBe("0%");
  // The page's clock is the test environment's, which stands at 0.
  const lines = descendants(main).filter((node) => node.tagName === "LI").map(textOf);
  expect(lines).toHaveLength(2);
  expect(lines[0]).toMatch(/^Finished · Der Pate · today \d\d:\d\d · 2 h$/);
  expect(lines[1]).toMatch(/^Started · No longer in the library · today \d\d:\d\d · 2 min$/);
});

test("nothing watched yet says so under the heading, and nothing else", async () => {
  answer({ ...SUMMARY, weekSeconds: 0, monthSeconds: 0, allSeconds: 0, history: [] });
  const main = env.node("main");
  await renderStats(main, { byId: new Map() }, () => true);
  expect(textOf(main)).toBe("StatsNothing watched yet.");
});

test("an answer that lands after the viewer left draws nothing", async () => {
  answer(SUMMARY);
  const main = env.node("main");
  await renderStats(main, { byId: new Map() }, () => false);
  expect(textOf(main)).toBe("Stats");
});

test("a failed read says so", async () => {
  answer({}, 500);
  const main = env.node("main");
  await renderStats(main, { byId: new Map() }, () => true);
  expect(textOf(main)).toContain("Could not read your stats: the server answered 500");
});
```

- [ ] **Step 2: Run, expect a failure**

Run: `cd web && bun test test/stats-page.test.ts`
Expected: FAIL — `Cannot find module "../public/lib/catalog/stats-page.js"`.

- [ ] **Step 3: Implement** — `web/public/lib/catalog/stats-page.js`

```js
/**
 * The Stats page: this profile's watching this week, this month and ever, the
 * last thirty days as bars, and every start, restart and finish, newest first.
 *
 * Asked of the server each time it opens — the minutes live there, counted
 * from every device — and drawn only if the viewer is still on the page when
 * the answer lands. Each profile sees only its own.
 */

import { el } from "../dom.js";
import { profileId } from "../watch-state.js";
import { heading } from "./shelf-view.js";
import { historyLine, shortDate, watchTime, weekdayInitial } from "./stats-format.js";

/**
 * @param {HTMLElement} main
 * @param {{ byId: Map<string, any> }} context
 * @param {() => boolean} stillHere
 */
export async function renderStats(main, { byId }, stillHere) {
  heading(main, "Stats");
  const id = profileId();
  if (id === null) return main.append(el("p", "empty", "Nothing watched yet."));
  let summary;
  try {
    const response = await fetch(`/api/profiles/${encodeURIComponent(id)}/stats`);
    if (!response.ok) throw new Error(`the server answered ${response.status}`);
    summary = await response.json();
  } catch (error) {
    if (stillHere()) main.append(el("p", "error", `Could not read your stats: ${error.message}`));
    return;
  }
  if (!stillHere()) return;
  if (summary.history.length === 0) return main.append(el("p", "empty", "Nothing watched yet."));
  main.append(totals(summary), lastThirty(summary.last30), history(summary.history, byId, Date.now()));
}

function totals(summary) {
  const row = el("dl", "stats-totals");
  for (const [label, seconds] of [
    ["This week", summary.weekSeconds],
    ["This month", summary.monthSeconds],
    ["All time", summary.allSeconds],
  ]) {
    const item = el("div", "stats-total");
    item.append(el("dt", null, label), el("dd", null, watchTime(seconds)));
    row.append(item);
  }
  return row;
}

/** One bar per day, today rightmost, each as tall as its share of the busiest. */
function lastThirty(days) {
  const section = el("section", "stats-days");
  section.append(el("h2", "shelf-sub", "Last 30 days"));
  const bars = el("div", "stats-bars");
  bars.setAttribute("role", "list");
  const most = Math.max(0, ...days.map((entry) => entry.seconds));
  for (const entry of days) {
    const day = el("div", "stats-day");
    day.setAttribute("role", "listitem");
    day.title = `${shortDate(entry.day)} · ${watchTime(entry.seconds)}`;
    day.setAttribute("aria-label", day.title);
    const bar = el("span", "stats-bar");
    bar.style.height = `${most > 0 ? (entry.seconds / most) * 100 : 0}%`;
    day.append(bar, el("span", "stats-weekday", weekdayInitial(entry.day)));
    bars.append(day);
  }
  section.append(bars);
  return section;
}

/** Every entry; the stylesheet's `content-visibility` keeps a long one cheap. */
function history(entries, byId, now) {
  const section = el("section", "stats-history");
  section.append(el("h2", "shelf-sub", "History"));
  const list = el("ol", "stats-history-list");
  for (const entry of entries) list.append(el("li", null, historyLine(entry, byId.get(entry.setId) ?? null, now)));
  section.append(list);
  return section;
}
```

`web/public/styles/stats.css`

```css
/* The Stats page: totals, the last thirty days as bars, and the history. */
.stats-totals { display: flex; flex-wrap: wrap; gap: 24px 56px; margin: 0 0 8px; }
.stats-total dt { color: var(--ink-3); font-size: 0.6875rem; letter-spacing: 0.24em; text-transform: uppercase; }
.stats-total dd { margin: 6px 0 0; font: 600 clamp(1.6rem, 3vw, 2.4rem)/1.1 var(--display); font-variant-numeric: tabular-nums; }

.stats-bars { display: grid; grid-template-columns: repeat(30, minmax(0, 1fr)); gap: 4px; align-items: end; height: 160px; }
.stats-day { display: flex; flex-direction: column; justify-content: flex-end; align-items: center; gap: 6px; height: 100%; }
.stats-bar { display: block; width: 100%; min-height: 2px; border-radius: 2px 2px 0 0; background: var(--accent); }
.stats-weekday { color: var(--ink-3); font-size: 0.6875rem; line-height: 1; }
/* The initials only where there is room for thirty of them. */
@media (max-width: 767px) {
  .stats-weekday { display: none; }
}

.stats-history-list { margin: 0; padding: 0; list-style: none; }
.stats-history-list li {
  padding: 12px 0;
  border-bottom: 1px solid var(--rule-soft);
  color: var(--ink-2);
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
  /* Kept forever, so it can be long: off-screen lines are not laid out. */
  content-visibility: auto;
  contain-intrinsic-size: auto 2.75rem;
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `cd web && bun test test/stats-page.test.ts test/code-standards.test.ts && bun run lint`
Expected: 0 fail; `eslint` silent.

- [ ] **Step 5: Commit**

```bash
git add web/public/lib/catalog/stats-page.js web/public/styles/stats.css web/test/stats-page.test.ts
git commit -m "feat(web): draw a profile's stats — totals, thirty days of bars, history"
```

---

## Task 2.7: the rail item and the route, then the phase's checks and version

**Files:**
- Modify: `web/public/lib/address.js`, `web/public/lib/address.d.ts`, `web/test/address.test.ts`
- Modify: `web/public/index.html` (rail link between Genres and Settings; stylesheet link)
- Modify: `web/public/app.js` (one import, one `if`; `shelfToggle` moves out)
- Modify: `web/public/lib/catalog/shelf-mode.js`, `web/test/shelf-mode.test.ts` (the moved `shelfToggle` and its first test)
- Modify: `web/test/code-standards.test.ts` (lower three ceilings)
- Modify: `Cargo.toml`, `Cargo.lock`, `web/package.json`, `android/app/build.gradle.kts` (minor bump)

**Interfaces:**
- `Address` gains `{ page: "stats" }`; `parse("#/stats")` → it; `href` → `"#/stats"`; `sectionOf` → `"stats"`, which lights the rail link through `drawRoute`'s existing `nav a` loop (`app.js:599-604`) and sets `body[data-page]`.
- `shelfToggle(redraw)` moves verbatim from `app.js:146-182` into `shelf-mode.js`, which already owns `LIST`, `GRID`, `shelfMode`, `setShelfMode`; its one caller (`app.js:263`) passes `route`. That is the extraction that makes room in `app.js` (753 → 717).
- Docs are phase 05's: when it adds `#/stats` to `docs/web-player.md`'s address table, `web/test/address.test.ts`'s `kinds` map needs `"#/stats": "stats"` in the same commit (that test fails on an unknown row, not on a missing one).

- [ ] **Step 1: Failing tests**

`web/test/address.test.ts` — in the round-trip `cases`, after `["settings", { page: "settings" }],` add:

```ts
    ["stats", { page: "stats" }],
```

in the "parse accepts" `cases`, after `["#/settings", { page: "settings" }],` add:

```ts
    ["#/stats", { page: "stats" }],
    ["#/statsxyz", { page: "department", section: "movies" }],
```

and in the `sectionOf` table, after `["#/film/abc", "film"],` add:

```ts
    ["#/stats", "stats"],
```

`web/test/shelf-mode.test.ts` — replace the first import line with:

```ts
import { GRID, LIST, modeFrom, setShelfMode, shelfMode, shelfToggle } from "../public/lib/catalog/shelf-mode.js";
import { browserEnvironment } from "./support/player-environment";
```

and append:

```ts
describe("the list-or-grid control", () => {
  test("presses the current mode; the other one stores itself and redraws once", () => {
    const env = browserEnvironment();
    try {
      useStorage(fakeStorage());
      setShelfMode(LIST);
      let redraws = 0;
      const control = shelfToggle(() => { redraws += 1; });
      const [list, grid] = control.children;
      expect(list.getAttribute("aria-pressed")).toBe("true");
      expect(grid.getAttribute("aria-pressed")).toBe("false");
      list.fire("click");
      expect(redraws).toBe(0);
      grid.fire("click");
      expect(shelfMode()).toBe(GRID);
      expect(redraws).toBe(1);
    } finally {
      env.restore();
    }
  });
});
```

- [ ] **Step 2: Run, expect failures**

Run: `cd web && bun test test/address.test.ts test/shelf-mode.test.ts`
Expected: FAIL — `#/stats` parses as Movies; `shelfToggle` is not exported.

- [ ] **Step 3: Implement the address** — `web/public/lib/address.js`

In `KNOWN_SECTIONS`, after the line ending `"settings", "person",` add:

```js
  "stats",
```

after `  if (known === "settings") return { page: "settings" };` add:

```js
  if (known === "stats") return { page: "stats" };
```

after `    case "settings": return "#/settings";` add:

```js
    case "stats": return "#/stats";
```

`web/public/lib/address.d.ts` — after `  | { page: "settings" }` add:

```ts
  | { page: "stats" }
```

- [ ] **Step 4: Move the shelf toggle** — append to `web/public/lib/catalog/shelf-mode.js`:

```js
/**
 * List or plates, for the two shelves that have artwork worth showing.
 *
 * Two buttons rather than a select: there are two states, and a menu that
 * opens to offer a choice of two is a menu that should have been the choice.
 * `redraw` rebuilds the shelf the same way it is built on arrival — the mode
 * is read at render time, not passed around.
 * @param {() => void} redraw
 */
export function shelfToggle(redraw) {
  const current = shelfMode();
  const control = el("div", "shelf-modes");
  control.setAttribute("role", "group");
  control.setAttribute("aria-label", "How to show this shelf");

  for (const [mode, label] of [
    [LIST, "List"],
    [GRID, "Grid"],
  ]) {
    const button = el("button", "mode", label);
    button.type = "button";
    if (mode === current) {
      button.classList.add("on");
      // The pressed state rather than `disabled`: a viewer reading with a
      // screen reader is told which they are on, and the control does not
      // lose focus when the shelf rebuilds under it.
      button.setAttribute("aria-pressed", "true");
    } else {
      button.setAttribute("aria-pressed", "false");
      button.addEventListener("click", () => {
        setShelfMode(mode);
        redraw();
      });
    }
    control.append(button);
  }
  return control;
}
```

and at its top, between the header comment and `const KEY = "mediagram.shelfView";`, add:

```js
import { el } from "../dom.js";

```

In `web/public/app.js`:
- line 24: replace `import { GRID, LIST, setShelfMode, shelfMode } from "./lib/catalog/shelf-mode.js";` with `import { shelfMode, shelfToggle } from "./lib/catalog/shelf-mode.js";`
- after line 47 (`import { renderGenre, renderGenres, renderLatest } from "./lib/catalog/utility-pages.js";`) add `import { renderStats } from "./lib/catalog/stats-page.js";`
- delete lines 146-183: the `shelfToggle` doc comment (`/**` above "List or plates, for the two shelves…") through the function's closing `}` (line 182) and the blank line after it;
- line 263: replace `  controls.append(shelfToggle());` with `  controls.append(shelfToggle(route));`
- before line 630 (`  if (address.page === "system") return viewSystem();`) add:

```js
  if (address.page === "stats") return renderStats(main, { byId }, () => generation === routeGeneration);
```

- [ ] **Step 5: The rail item and the stylesheet** — `web/public/index.html`

After line 23 (`    <link rel="stylesheet" href="/styles/playback.css" />`) add:

```html
    <link rel="stylesheet" href="/styles/stats.css" />
```

Between the Genres link (line 55) and the Settings link (line 56) add — same `class="kept"` and stroke-icon style as its neighbours, a baseline and three bars:

```html
        <a href="#/stats" data-section="stats" class="kept"><svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M4.5 20.5h15M7 17v-4.5M12 17V7M17 17v-7.5"></path></svg><span class="label">Stats</span></a>
```

- [ ] **Step 6: Lower the ceilings this phase shrank** — `web/test/code-standards.test.ts`

Replace the end of the `CEILINGS` doc comment, ` * the startup reconcile of held titles.` + ` */`, with:

```ts
 * the startup reconcile of held titles. Lowered 2026-10-03 for `app.js`,
 * `src/state/routes.ts` and `src/state/schema.ts`, once the shelf toggle, the
 * shared route helpers and the test-only `migrationsUpTo` moved out to make
 * room for viewing stats.
 */
```

and set `"public/app.js": 717,`, `"src/state/routes.ts": 246,`, `"src/state/schema.ts": 243,`.

- [ ] **Step 7: Run everything**

Run: `cd web && bun test && bun run typecheck && bun run lint`
Expected: 0 fail (`browser-module-assets.test.ts` walks `app.js`'s graph into `stats-page.js` and `stats-format.js`); `tsc` and `eslint` silent.

- [ ] **Step 8: Look at it** — the preview (copies, no Telegram; never the real player):

```bash
cd web && bun run preview            # http://127.0.0.1:8795; leave it running
P=$(curl -s 127.0.0.1:8795/api/profiles | jq -r '.profiles[0].id')
S=$(curl -s 127.0.0.1:8795/api/sets | jq -r '.[0].setId')
for at in 100 110; do
  curl -s -X PUT -H 'content-type: application/json' -d "{\"at\":$at,\"duration\":7200}" "127.0.0.1:8795/api/profiles/$P/progress/$S"
done
curl -s "127.0.0.1:8795/api/profiles/$P/stats" | jq '{weekSeconds, allSeconds, today: .last30[-1], first: .history[0]}'
```

Expected: `history[0]` is `started` for `$S` (or `again`, if that set is finished on the copied profile); `today.seconds` is the fraction of a second between the two PUTs. Then with `/browse`: open `http://127.0.0.1:8795/#/stats` (choose the profile first if asked — memory note on the picker), and screenshot at 1440 px and 390 px wide. Check: "Stats" sits between Genres and Settings and is lit; three totals; thirty bars with today's at full height, weekday letters only at 1440; history newest first in the contract's line format; finishes from the copied database appear as "Finished · … · 21 Sep" lines with no duration. Stop the preview with `fuser -k -n tcp 8795`.

- [ ] **Step 9: Bump the minor version, by pattern**

```bash
current=$(grep -m1 -oP '^version = "\K[0-9]+\.[0-9]+\.[0-9]+' Cargo.toml)
next=$(echo "$current" | awk -F. '{print $1"."$2+1".0"}')
sed -i -E '0,/^version = "[0-9]+\.[0-9]+\.[0-9]+"$/s//version = "'"$next"'"/' Cargo.toml
sed -i -E 's/^(  "version": ")[0-9]+\.[0-9]+\.[0-9]+(",)$/\1'"$next"'\2/' web/package.json
sed -i -E 's/^(        versionName = ")[0-9]+\.[0-9]+\.[0-9]+(")$/\1'"$next"'\2/' android/app/build.gradle.kts
cargo metadata -q --format-version 1 >/dev/null
grep -m1 '^version' Cargo.toml; grep '"version"' web/package.json; grep 'versionName = ' android/app/build.gradle.kts
```

Expected: all three print `$next`. `versionCode` is derived from `versionName`; never edit it.

- [ ] **Step 10: Commit**

```bash
git add web/public/lib/address.js web/public/lib/address.d.ts web/public/index.html web/public/app.js \
  web/public/lib/catalog/shelf-mode.js web/test/address.test.ts web/test/shelf-mode.test.ts web/test/code-standards.test.ts \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts
git commit -m "feat(web): a Stats page in the rail, between Genres and Settings"
```

---

## Test matrix

| Layer | What | Where |
|---|---|---|
| Shared fixtures (both engines) | step, againNow, parse, merge, summary | phase 01 |
| Store integration, real SQLite | recording, ticks, restart, midnight, two tabs, clock stepped back, refused write | `state-stats-recording.test.ts` |
| Store integration, two/three machines | export bytes, wire round trip, newer-only import, gossip, own row back, summary read | `state-stats-exchange.test.ts`, `state-sync.test.ts` |
| Migration | v10 → v11, second open, cascade | `state-migration.test.ts` |
| HTTP (router) | 200 / 404 / 405 / HEAD | `state-stats-route.test.ts` |
| Pure UI helpers | durations, times (3 zones + DST), titles, lines | `stats-format.test.ts` |
| DOM (stub browser) | fetch URL, totals, bars, history, empty, stale answer, failure | `stats-page.test.ts` |
| Routing | `#/stats` round trip, typo fallback, `sectionOf`; moved toggle | `address.test.ts`, `shelf-mode.test.ts` |
| Manual, end to end | preview + `/browse` at two widths | Task 2.7 Step 8 |

## Risk

| Risk | L × I | Mitigation |
|---|---|---|
| A sync round counts minutes, or counts them twice | L × H | Import path never calls `writeProgress`; totals are sums of per-device rows; tests in 2.2 and 2.3. |
| A clock stepped back on this server: its fresh row would carry an older `updatedAt` than a copy another device gossips back, and the import would overwrite newer seconds with older | L × M | `updated_at = MAX(now, stored + 1)` on own rows (contract §1, amended); Task 2.2 test. The core must clamp the same way (phase 03). |
| Day row over 86 400 s dropped by every reader | VL × M | Needs > 24 h of concurrent viewing of different titles on one server in one day; not guarded. |
| `store.ts` ceiling (799/800) leaves no room for the next change there | M × L | The next change extracts (e.g. `exportRecord`'s progress query) rather than raising. |
| Old build opens a v11 database | L × L | `migrate` skips every group `≤` the stored version; the two tables are simply unused; older readers drop the two keys. |

**Rollback:** revert the seven commits. The v11 tables stay in each `state.db` unused (an older build skips groups at or below the stored version, `store.ts:771-773`); documents already sent carry two keys every older reader drops. No data migration either way.

## Success criteria

- `cd web && bun test && bun run typecheck && bun run lint` → 0 fail, silent; code-standards green with the three lowered ceilings.
- `GET /api/profiles/{p}/stats` answers 200 with every `StatsSummary` key; 404 for an unknown profile; 405 for a write.
- After two position writes 10 s apart on a fresh store, `stats_titles` holds one row (`seconds = 10`) and `stats_days` one row for today; a merged-in position adds no row; a write after the clock stepped back stamps both rows `stored + 1`.
- An export of a profile with no stats contains neither `titleStats` nor `dayStats`; `SYNC_FORMAT` is 1.
- `#/stats` renders from the rail item placed between Genres and Settings, lit when open, with totals, 30 bars and the history in contract §6's strings (screenshots at 1440 px and 390 px).
- All three manifests carry the same new minor version; `Cargo.lock` agrees.
