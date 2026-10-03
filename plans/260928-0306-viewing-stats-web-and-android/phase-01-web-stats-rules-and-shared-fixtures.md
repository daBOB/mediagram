# Phase 01 — Web: pure stats rules and shared fixtures

**Goal:** the web's viewing-stats rules exist as pure TypeScript and are pinned
by four new shared fixtures the Rust core will run in phase 03: what one
position write counts (`stepSeconds`), when a write is "watching again"
(`againNow`), reading `titleStats`/`dayStats` off a sync document as hostile
input, merging them per viewer, and the page's summary (`summarize`).

**Architecture:** four new pure modules under `web/src/state/`, two hooks into
existing ones, no I/O, no schema, no export. Data flow:

```
document text ──parseRecord──▶ ProfileState{…, titleStats?, dayStats?}      (stats-record.ts, hooked into sync-record.ts)
SyncRecord[] ──mergeStates──▶ MergedProfile{…, titleStats?, dayStats?}      (stats-merge.ts via keep(), hooked into merge.ts)
(prev tick, at, now) ──stepSeconds──▶ seconds                                (stats-step.ts; used by phase 02's recorder)
{today, titles, days, watched} ──summarize──▶ StatsSummary                   (stats-summary.ts; used by phase 02's route)
```

Nothing writes the new keys yet, so no document on the channel changes: a
phase-01 build parses and merges stats rows other devices do not send yet.

**Context**
- Contract (names, keys, rules — authoritative): [shared-contract.md](shared-contract.md) §1 (step, againNow), §3 (wire), §4 (summary), §8.
- Decisions: [plan.md](plan.md) § Decisions 1–11 (user; never reverse).
- Code: `web/src/state/sync-record.ts:56` (imports), `:112` (`ProfileState`), `:192-204` (`parseRecord` profile rows); `web/src/state/merge.ts:43` (imports), `:46` (`MergedProfile`), `:162-188` (profiles built); `web/src/state/tie-break.ts:20` (`keep`); `web/test/shared-watch-state-fixtures.test.ts` (runner; `canonical()` at `:62` picks fields explicitly, so existing `merge.json` cases never see the new keys).
- Precedent for a new optional key that older readers drop: commit `fce5a757` (`unwatched`).
- Rust runner `crates/mediagram-core/tests/shared_watch_state_fixtures.rs` reads files by name (`record-parse.json`, `merge.json`, `lists-merge.json`) and is **not touched here**; phase 03 adds the four new files to it. No existing fixture file changes, so `cargo test` is unaffected.

**Global constraints (shared-contract.md §8)**
- No plan references (phase numbers, decision numbers) in code, test names or commit messages.
- Web `CEILINGS` (`web/test/code-standards.test.ts`) are never raised; make room by extracting. New files ≤ 200 lines. Rust: ≤ 200 lines per non-test file.
- Versions: all three manifests bumped by pattern, once per phase on its last commit (CLAUDE.md § Versioning; memory "bump versions by pattern").
- Branch `feat/viewing-stats` in a worktree off `main`.

Line budget (verified): `sync-record.ts` 314 → 318 (ceiling 328); `merge.ts` 194 → 198 (limit 200, not in `CEILINGS`); every new file ≤ 109.

## Review focus

Each item names the test that guards it.

1. **A document without the new keys — every pre-stats document — must parse and merge exactly as before.** `parseRecord` returns `titleStats: undefined` for an absent key, which `toEqual` treats as missing; the unchanged `record-parse.json`, `merge.json` and `lists-merge.json` cases run in every task's check (Tasks 1.2, 1.3).
2. **A key present but empty must not appear in merged output.** `stats-merge.json` "a viewer with no stats rows has no stats keys", with a runner that passes the keys through as `mergeStates` left them, so a stray `[]` fails (Task 1.3).
3. **One bad row costs that row, never the document.** `stats-record-parse.json`: negative, `"NaN"`, numeric string, `null`, `true`, `{}`, missing field, blank id, bad day, day over 86 400 s, non-object entries, non-array key (Task 1.2).
4. **`againAt: null` is no restart, not a bad row** (contract §3, amended 2026-10-03): the row is kept without `againAt`; a negative or string `againAt` still drops it — "an againAt of null is no restart; a negative or string one drops the row" (Task 1.2). Exporters still omit the key when empty.
5. **Gossip never double-counts; merge order never matters.** The runner merges every case forward and reversed; "the same row in two documents is kept once", "a device passing on every row it holds counts nothing twice", "an exact tie with different contents is decided by the document's device" (Task 1.3).
6. **Two engines must agree to the bit.** Sort by code unit, not `localeCompare` ("set ids sort by character code, not by locale"); fixture seconds are integers or binary-exact fractions (0.5), so a different summation order in Rust cannot change a sum; day arithmetic in UTC so DST cannot skip a day ("thirty days across a new year") (Tasks 1.1, 1.4).
7. **Week starts Monday; a future day counts only in all-time.** Summary cases "on a Monday…", "on a Sunday…", "a day after today counts in all time only" (Task 1.4).

---

## Task 1.1: `stepSeconds` and `againNow`

**Files:**
- Create: `web/src/state/stats-step.ts`
- Create: `web/test/fixtures/watch-state/stats-step.json`
- Modify: `web/test/shared-watch-state-fixtures.test.ts` (one import, one `describe`)

**Interfaces:**
- Produces: `STEP_CAP_SECONDS = 15`; `interface LastTick { at: number; wallMs: number }`; `stepSeconds(prev: LastTick | null | undefined, at: number, nowMs: number): number`; `againNow(hadProgress: boolean, watchedLive: boolean): boolean` — contract §1, consumed by phase 02's recorder.
- Fixture shape: the `resume-point.json` shape — `{ name, fn, args, expect }` — so the Rust runner can reuse its function-dispatch pattern.

- [ ] **Step 1: The fixture** — `web/test/fixtures/watch-state/stats-step.json`

```json
[
  { "name": "a first write has nothing to measure from", "fn": "stepSeconds",
    "args": [null, 120, 1789000010000], "expect": 0 },
  { "name": "one ordinary save tick counts its ten seconds", "fn": "stepSeconds",
    "args": [{ "at": 100, "wallMs": 1789000000000 }, 110, 1789000010000], "expect": 10 },
  { "name": "a pause counts only the time since playing resumed", "fn": "stepSeconds",
    "args": [{ "at": 103, "wallMs": 1789000003000 }, 113, 1789000613000], "expect": 10 },
  { "name": "a seek forward counts the wall time spent, not the jump", "fn": "stepSeconds",
    "args": [{ "at": 100, "wallMs": 1789000000000 }, 1900, 1789000010000], "expect": 10 },
  { "name": "a seek back counts nothing", "fn": "stepSeconds",
    "args": [{ "at": 500, "wallMs": 1789000000000 }, 120, 1789000010000], "expect": 0 },
  { "name": "a position that did not move counts nothing", "fn": "stepSeconds",
    "args": [{ "at": 100, "wallMs": 1789000000000 }, 100, 1789000010000], "expect": 0 },
  { "name": "double speed counts wall time", "fn": "stepSeconds",
    "args": [{ "at": 100, "wallMs": 1789000000000 }, 120, 1789000010000], "expect": 10 },
  { "name": "half speed counts the distance moved", "fn": "stepSeconds",
    "args": [{ "at": 100, "wallMs": 1789000000000 }, 105, 1789000010000], "expect": 5 },
  { "name": "a fraction of a second counts as one", "fn": "stepSeconds",
    "args": [{ "at": 100, "wallMs": 1789000000000 }, 100.5, 1789000002000], "expect": 0.5 },
  { "name": "a long gap is capped", "fn": "stepSeconds",
    "args": [{ "at": 100, "wallMs": 1789000000000 }, 160, 1789000060000], "expect": 15 },
  { "name": "exactly the cap counts in full", "fn": "stepSeconds",
    "args": [{ "at": 0, "wallMs": 1789000000000 }, 15, 1789000015000], "expect": 15 },
  { "name": "a clock that stepped back counts nothing", "fn": "stepSeconds",
    "args": [{ "at": 100, "wallMs": 1789000000000 }, 110, 1788999999000], "expect": 0 },
  { "name": "two writes in the same millisecond count nothing", "fn": "stepSeconds",
    "args": [{ "at": 100, "wallMs": 1789000000000 }, 110, 1789000000000], "expect": 0 },
  { "name": "a finished title with no position is being watched again", "fn": "againNow",
    "args": [false, true], "expect": true },
  { "name": "a restarted title already underway again is not restarted twice", "fn": "againNow",
    "args": [true, true], "expect": false },
  { "name": "a first play is a start, not watching again", "fn": "againNow",
    "args": [false, false], "expect": false },
  { "name": "carrying on with an unfinished title is not watching again", "fn": "againNow",
    "args": [true, false], "expect": false }
]
```

- [ ] **Step 2: The runner block** — in `web/test/shared-watch-state-fixtures.test.ts`, add below the `import { parseRecord, type SyncRecord } from "../src/state/sync-record";` line:

```ts
import { againNow, stepSeconds } from "../src/state/stats-step";
```

and append at the end of the file:

```ts
describe("stats-step fixtures", () => {
  const fns = { stepSeconds, againNow } as const;

  interface Case {
    name: string;
    fn: keyof typeof fns;
    args: unknown[];
    expect: unknown;
  }

  for (const one of load<Case[]>("stats-step.json")) {
    test(`${one.fn}: ${one.name}`, () => {
      const run = fns[one.fn] as (...args: unknown[]) => unknown;
      expect(run(...one.args)).toEqual(one.expect);
    });
  }
});
```

- [ ] **Step 3: Run, expect a failure**

Run: `cd web && bun test test/shared-watch-state-fixtures.test.ts`
Expected: FAIL — `Cannot find module "../src/state/stats-step"`.

- [ ] **Step 4: Implement** — `web/src/state/stats-step.ts`

```ts
/**
 * How much of a position write counts as watching.
 *
 * Watch time is the wall-clock time between two consecutive position writes
 * of the same title, counted only when the position moved forward, and never
 * more than either the distance moved or `STEP_CAP_SECONDS`. That one rule
 * keeps a pause, a seek and a sleeping laptop out of the total without the
 * engine having to know which of them happened: a seek forward counts the
 * wall time spent, a pause counts only the time since playing resumed, 2×
 * speed counts wall time, and a seek back counts nothing.
 *
 * Known ceiling: a throttled background tab that saves once a minute counts
 * 15 s per minute.
 *
 * Pure, and pinned by `test/fixtures/watch-state/stats-step.json`, which the
 * Android core runs too.
 */

/** 1.5 × the player's 10 s save tick: one late tick still counts in full. */
export const STEP_CAP_SECONDS = 15;

/** A title's previous position write in this process: where, and when. */
export interface LastTick {
  /** The position, in seconds. */
  at: number;
  /** The wall clock at that write, in epoch ms. */
  wallMs: number;
}

/** Seconds of watching between `prev` and a write at `at`, made at `nowMs`. */
export function stepSeconds(prev: LastTick | null | undefined, at: number, nowMs: number): number {
  if (!prev) return 0;
  const dPos = at - prev.at;
  const dWall = (nowMs - prev.wallMs) / 1000;
  if (!(dPos > 0) || !(dWall > 0)) return 0;
  return Math.min(dPos, dWall, STEP_CAP_SECONDS);
}

/**
 * Whether this write starts a finished title over: there was no position
 * for it, yet it is marked watched. A title underway since its restart —
 * which has a position again — is not started over a second time.
 */
export function againNow(hadProgress: boolean, watchedLive: boolean): boolean {
  return !hadProgress && watchedLive;
}
```

`!(dPos > 0)` rather than `dPos <= 0` so a `NaN` (never expected — the route rejects a non-finite `at`) also counts nothing.

- [ ] **Step 5: Run, expect PASS**

Run: `cd web && bun test test/shared-watch-state-fixtures.test.ts`
Expected: 138 pass, 0 fail (121 existing + 17 new).

- [ ] **Step 6: Commit**

```bash
git add web/src/state/stats-step.ts web/test/fixtures/watch-state/stats-step.json web/test/shared-watch-state-fixtures.test.ts
git commit -m "feat(web): count watch time between position writes, pinned by a shared fixture"
```

---

## Task 1.2: `titleStats` / `dayStats` on the wire, parsed as hostile input

**Files:**
- Create: `web/src/state/stats-record.ts`
- Create: `web/test/fixtures/watch-state/stats-record-parse.json`
- Modify: `web/src/state/sync-record.ts` (one import, `ProfileState extends StatsRows`, two lines in `parseRecord`)
- Modify: `web/test/shared-watch-state-fixtures.test.ts` (one `describe`)

**Interfaces:**
- Produces: `TitleStatRow`, `DayStatRow` (contract §3, verbatim fields); `StatsRows { titleStats?: TitleStatRow[]; dayStats?: DayStatRow[] }`; `parseTitleStatRows(value: unknown): TitleStatRow[] | undefined`; `parseDayStatRows(value: unknown): DayStatRow[] | undefined` — `undefined` when the key is not an array ("treated as absent"), the rows that survive otherwise.
- `ProfileState` gains `titleStats?` / `dayStats?` through `extends StatsRows`. `SYNC_FORMAT` stays 1.
- Rules pinned (contract §3, as amended 2026-10-03): numbers must be JSON numbers (no numeric-string coercion, unlike the older rows — no writer ever produced strings for these keys); ids are trimmed like every other id in `sync-record.ts`; a day is checked for shape only (`2026-13-45` is kept); `againAt: null` reads as absent; extra fields on a row are not carried.

- [ ] **Step 1: The fixture** — `web/test/fixtures/watch-state/stats-record-parse.json` (the `record-parse.json` shape: `input` is the document text)

```json
[
  {
    "name": "title and day rows read back as written",
    "input": "{\"format\":1,\"device\":\"laptop\",\"writtenAt\":1789000000000,\"profiles\":[{\"name\":\"André\",\"progress\":[],\"watched\":[],\"titleStats\":[{\"setId\":\"01A\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":600,\"updatedAt\":1789000600000,\"againAt\":1789000300000}],\"dayStats\":[{\"day\":\"2026-09-10\",\"device\":\"laptop\",\"seconds\":600,\"updatedAt\":1789000600000}]}]}",
    "expect": {
      "format": 1,
      "device": "laptop",
      "writtenAt": 1789000000000,
      "profiles": [
        {
          "name": "André",
          "progress": [],
          "watched": [],
          "titleStats": [
            { "setId": "01A", "device": "laptop", "startedAt": 1789000000000, "lastWatchedAt": 1789000600000, "seconds": 600, "updatedAt": 1789000600000, "againAt": 1789000300000 }
          ],
          "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 1789000600000 }]
        }
      ]
    }
  },
  {
    "name": "a title row without againAt reads without one",
    "input": "{\"format\":1,\"device\":\"laptop\",\"writtenAt\":1789000000000,\"profiles\":[{\"name\":\"André\",\"progress\":[],\"watched\":[],\"titleStats\":[{\"setId\":\"01A\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":600,\"updatedAt\":1789000600000}]}]}",
    "expect": {
      "format": 1,
      "device": "laptop",
      "writtenAt": 1789000000000,
      "profiles": [
        {
          "name": "André",
          "progress": [],
          "watched": [],
          "titleStats": [
            { "setId": "01A", "device": "laptop", "startedAt": 1789000000000, "lastWatchedAt": 1789000600000, "seconds": 600, "updatedAt": 1789000600000 }
          ]
        }
      ]
    }
  },
  {
    "name": "a stats key that is not an array is read as absent",
    "input": "{\"format\":1,\"device\":\"laptop\",\"writtenAt\":1789000000000,\"profiles\":[{\"name\":\"André\",\"progress\":[],\"watched\":[],\"titleStats\":{\"setId\":\"01A\"},\"dayStats\":\"lots\"}]}",
    "expect": { "format": 1, "device": "laptop", "writtenAt": 1789000000000, "profiles": [{ "name": "André", "progress": [], "watched": [] }] }
  },
  {
    "name": "an empty stats key reads as empty, not absent",
    "input": "{\"format\":1,\"device\":\"laptop\",\"writtenAt\":1789000000000,\"profiles\":[{\"name\":\"André\",\"progress\":[],\"watched\":[],\"titleStats\":[],\"dayStats\":[]}]}",
    "expect": {
      "format": 1,
      "device": "laptop",
      "writtenAt": 1789000000000,
      "profiles": [{ "name": "André", "progress": [], "watched": [], "titleStats": [], "dayStats": [] }]
    }
  },
  {
    "name": "a negative number drops the row, not the document",
    "input": "{\"format\":1,\"device\":\"laptop\",\"writtenAt\":1789000000000,\"profiles\":[{\"name\":\"André\",\"progress\":[{\"setId\":\"01A\",\"at\":742,\"duration\":1204,\"updatedAt\":1789000000000}],\"watched\":[],\"titleStats\":[{\"setId\":\"01A\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":600,\"updatedAt\":1789000600000},{\"setId\":\"01B\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":-5,\"updatedAt\":1789000600000}],\"dayStats\":[{\"day\":\"2026-09-10\",\"device\":\"laptop\",\"seconds\":600,\"updatedAt\":1789000600000},{\"day\":\"2026-09-11\",\"device\":\"laptop\",\"seconds\":-1,\"updatedAt\":1789000600000}]}]}",
    "expect": {
      "format": 1,
      "device": "laptop",
      "writtenAt": 1789000000000,
      "profiles": [
        {
          "name": "André",
          "progress": [{ "setId": "01A", "at": 742, "duration": 1204, "updatedAt": 1789000000000 }],
          "watched": [],
          "titleStats": [
            { "setId": "01A", "device": "laptop", "startedAt": 1789000000000, "lastWatchedAt": 1789000600000, "seconds": 600, "updatedAt": 1789000600000 }
          ],
          "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 1789000600000 }]
        }
      ]
    }
  },
  {
    "name": "a value that is not a finite JSON number drops the row",
    "input": "{\"format\":1,\"device\":\"laptop\",\"writtenAt\":1789000000000,\"profiles\":[{\"name\":\"André\",\"progress\":[],\"watched\":[],\"titleStats\":[{\"setId\":\"01B\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":\"NaN\",\"updatedAt\":1789000600000},{\"setId\":\"01C\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":null,\"updatedAt\":1789000600000},{\"setId\":\"01D\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":true,\"updatedAt\":1789000600000},{\"setId\":\"01E\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":{},\"updatedAt\":1789000600000},{\"setId\":\"01F\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":\"600\",\"updatedAt\":1789000600000},{\"setId\":\"01A\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":600,\"updatedAt\":1789000600000}],\"dayStats\":[{\"day\":\"2026-09-11\",\"device\":\"laptop\",\"seconds\":\"600\",\"updatedAt\":1789000600000},{\"day\":\"2026-09-12\",\"device\":\"laptop\",\"seconds\":600,\"updatedAt\":null},{\"day\":\"2026-09-10\",\"device\":\"laptop\",\"seconds\":600,\"updatedAt\":1789000600000}]}]}",
    "expect": {
      "format": 1,
      "device": "laptop",
      "writtenAt": 1789000000000,
      "profiles": [
        {
          "name": "André",
          "progress": [],
          "watched": [],
          "titleStats": [
            { "setId": "01A", "device": "laptop", "startedAt": 1789000000000, "lastWatchedAt": 1789000600000, "seconds": 600, "updatedAt": 1789000600000 }
          ],
          "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 1789000600000 }]
        }
      ]
    }
  },
  {
    "name": "a missing field drops the row",
    "input": "{\"format\":1,\"device\":\"laptop\",\"writtenAt\":1789000000000,\"profiles\":[{\"name\":\"André\",\"progress\":[],\"watched\":[],\"titleStats\":[{\"setId\":\"01B\",\"device\":\"laptop\",\"lastWatchedAt\":1789000600000,\"seconds\":600,\"updatedAt\":1789000600000},{\"setId\":\"01C\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"seconds\":600,\"updatedAt\":1789000600000},{\"setId\":\"01D\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"updatedAt\":1789000600000},{\"setId\":\"01E\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":600},{\"setId\":\"01F\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":600,\"updatedAt\":1789000600000},{\"setId\":\"01A\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":600,\"updatedAt\":1789000600000}],\"dayStats\":[{\"day\":\"2026-09-11\",\"device\":\"laptop\",\"seconds\":600},{\"day\":\"2026-09-12\",\"seconds\":600,\"updatedAt\":1789000600000},{\"device\":\"laptop\",\"seconds\":600,\"updatedAt\":1789000600000},{\"day\":\"2026-09-10\",\"device\":\"laptop\",\"seconds\":600,\"updatedAt\":1789000600000}]}]}",
    "expect": {
      "format": 1,
      "device": "laptop",
      "writtenAt": 1789000000000,
      "profiles": [
        {
          "name": "André",
          "progress": [],
          "watched": [],
          "titleStats": [
            { "setId": "01A", "device": "laptop", "startedAt": 1789000000000, "lastWatchedAt": 1789000600000, "seconds": 600, "updatedAt": 1789000600000 }
          ],
          "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 1789000600000 }]
        }
      ]
    }
  },
  {
    "name": "an empty, blank or non-string id drops the row",
    "input": "{\"format\":1,\"device\":\"laptop\",\"writtenAt\":1789000000000,\"profiles\":[{\"name\":\"André\",\"progress\":[],\"watched\":[],\"titleStats\":[{\"setId\":\"\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":600,\"updatedAt\":1789000600000},{\"setId\":\"01B\",\"device\":\"  \",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":600,\"updatedAt\":1789000600000},{\"setId\":7,\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":600,\"updatedAt\":1789000600000},{\"setId\":\"01A\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":600,\"updatedAt\":1789000600000}],\"dayStats\":[{\"day\":\"2026-09-11\",\"device\":\"\",\"seconds\":600,\"updatedAt\":1789000600000},{\"day\":\"2026-09-10\",\"device\":\"laptop\",\"seconds\":600,\"updatedAt\":1789000600000}]}]}",
    "expect": {
      "format": 1,
      "device": "laptop",
      "writtenAt": 1789000000000,
      "profiles": [
        {
          "name": "André",
          "progress": [],
          "watched": [],
          "titleStats": [
            { "setId": "01A", "device": "laptop", "startedAt": 1789000000000, "lastWatchedAt": 1789000600000, "seconds": 600, "updatedAt": 1789000600000 }
          ],
          "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 1789000600000 }]
        }
      ]
    }
  },
  {
    "name": "an againAt of null is no restart; a negative or string one drops the row",
    "input": "{\"format\":1,\"device\":\"laptop\",\"writtenAt\":1789000000000,\"profiles\":[{\"name\":\"André\",\"progress\":[],\"watched\":[],\"titleStats\":[{\"setId\":\"01B\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":600,\"updatedAt\":1789000600000,\"againAt\":null},{\"setId\":\"01C\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":600,\"updatedAt\":1789000600000,\"againAt\":-1},{\"setId\":\"01D\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":600,\"updatedAt\":1789000600000,\"againAt\":\"1789000300000\"},{\"setId\":\"01A\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":600,\"updatedAt\":1789000600000}]}]}",
    "expect": {
      "format": 1,
      "device": "laptop",
      "writtenAt": 1789000000000,
      "profiles": [
        {
          "name": "André",
          "progress": [],
          "watched": [],
          "titleStats": [
            { "setId": "01B", "device": "laptop", "startedAt": 1789000000000, "lastWatchedAt": 1789000600000, "seconds": 600, "updatedAt": 1789000600000 },
            { "setId": "01A", "device": "laptop", "startedAt": 1789000000000, "lastWatchedAt": 1789000600000, "seconds": 600, "updatedAt": 1789000600000 }
          ]
        }
      ]
    }
  },
  {
    "name": "a day not written as YYYY-MM-DD drops the row",
    "input": "{\"format\":1,\"device\":\"laptop\",\"writtenAt\":1789000000000,\"profiles\":[{\"name\":\"André\",\"progress\":[],\"watched\":[],\"dayStats\":[{\"day\":\"2026-9-10\",\"device\":\"laptop\",\"seconds\":600,\"updatedAt\":1789000600000},{\"day\":\"yesterday\",\"device\":\"laptop\",\"seconds\":600,\"updatedAt\":1789000600000},{\"day\":20260910,\"device\":\"laptop\",\"seconds\":600,\"updatedAt\":1789000600000},{\"day\":\"2026-09-10T00:00\",\"device\":\"laptop\",\"seconds\":600,\"updatedAt\":1789000600000},{\"day\":\" 2026-09-10\",\"device\":\"laptop\",\"seconds\":600,\"updatedAt\":1789000600000},{\"day\":\"2026-09-10\",\"device\":\"laptop\",\"seconds\":600,\"updatedAt\":1789000600000}]}]}",
    "expect": {
      "format": 1,
      "device": "laptop",
      "writtenAt": 1789000000000,
      "profiles": [
        {
          "name": "André",
          "progress": [],
          "watched": [],
          "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 1789000600000 }]
        }
      ]
    }
  },
  {
    "name": "a day row over a whole day is dropped; exactly a day is kept",
    "input": "{\"format\":1,\"device\":\"laptop\",\"writtenAt\":1789000000000,\"profiles\":[{\"name\":\"André\",\"progress\":[],\"watched\":[],\"dayStats\":[{\"day\":\"2026-09-10\",\"device\":\"laptop\",\"seconds\":86400.5,\"updatedAt\":1789000600000},{\"day\":\"2026-09-11\",\"device\":\"laptop\",\"seconds\":86400,\"updatedAt\":1789000600000}]}]}",
    "expect": {
      "format": 1,
      "device": "laptop",
      "writtenAt": 1789000000000,
      "profiles": [
        {
          "name": "André",
          "progress": [],
          "watched": [],
          "dayStats": [{ "day": "2026-09-11", "device": "laptop", "seconds": 86400, "updatedAt": 1789000600000 }]
        }
      ]
    }
  },
  {
    "name": "zero is a value, not a missing one",
    "input": "{\"format\":1,\"device\":\"laptop\",\"writtenAt\":1789000000000,\"profiles\":[{\"name\":\"André\",\"progress\":[],\"watched\":[],\"titleStats\":[{\"setId\":\"01A\",\"device\":\"laptop\",\"startedAt\":0,\"lastWatchedAt\":0,\"seconds\":0,\"updatedAt\":0,\"againAt\":0}],\"dayStats\":[{\"day\":\"2026-09-10\",\"device\":\"laptop\",\"seconds\":0,\"updatedAt\":0}]}]}",
    "expect": {
      "format": 1,
      "device": "laptop",
      "writtenAt": 1789000000000,
      "profiles": [
        {
          "name": "André",
          "progress": [],
          "watched": [],
          "titleStats": [{ "setId": "01A", "device": "laptop", "startedAt": 0, "lastWatchedAt": 0, "seconds": 0, "updatedAt": 0, "againAt": 0 }],
          "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 0, "updatedAt": 0 }]
        }
      ]
    }
  },
  {
    "name": "an entry that is not an object is dropped",
    "input": "{\"format\":1,\"device\":\"laptop\",\"writtenAt\":1789000000000,\"profiles\":[{\"name\":\"André\",\"progress\":[],\"watched\":[],\"titleStats\":[null,7,\"01A\",[],{\"setId\":\"01A\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":600,\"updatedAt\":1789000600000}],\"dayStats\":[null,[\"2026-09-10\"],{\"day\":\"2026-09-10\",\"device\":\"laptop\",\"seconds\":600,\"updatedAt\":1789000600000}]}]}",
    "expect": {
      "format": 1,
      "device": "laptop",
      "writtenAt": 1789000000000,
      "profiles": [
        {
          "name": "André",
          "progress": [],
          "watched": [],
          "titleStats": [
            { "setId": "01A", "device": "laptop", "startedAt": 1789000000000, "lastWatchedAt": 1789000600000, "seconds": 600, "updatedAt": 1789000600000 }
          ],
          "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 1789000600000 }]
        }
      ]
    }
  },
  {
    "name": "unknown fields on a row are not carried",
    "input": "{\"format\":1,\"device\":\"laptop\",\"writtenAt\":1789000000000,\"profiles\":[{\"name\":\"André\",\"progress\":[],\"watched\":[],\"titleStats\":[{\"setId\":\"01A\",\"device\":\"laptop\",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":600,\"updatedAt\":1789000600000,\"note\":\"hi\"}],\"dayStats\":[{\"day\":\"2026-09-10\",\"device\":\"laptop\",\"seconds\":600,\"updatedAt\":1789000600000,\"extra\":1}]}]}",
    "expect": {
      "format": 1,
      "device": "laptop",
      "writtenAt": 1789000000000,
      "profiles": [
        {
          "name": "André",
          "progress": [],
          "watched": [],
          "titleStats": [
            { "setId": "01A", "device": "laptop", "startedAt": 1789000000000, "lastWatchedAt": 1789000600000, "seconds": 600, "updatedAt": 1789000600000 }
          ],
          "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 1789000600000 }]
        }
      ]
    }
  },
  {
    "name": "ids are trimmed, as every other id is",
    "input": "{\"format\":1,\"device\":\"laptop\",\"writtenAt\":1789000000000,\"profiles\":[{\"name\":\"André\",\"progress\":[],\"watched\":[],\"titleStats\":[{\"setId\":\" 01A \",\"device\":\" laptop \",\"startedAt\":1789000000000,\"lastWatchedAt\":1789000600000,\"seconds\":600,\"updatedAt\":1789000600000}],\"dayStats\":[{\"day\":\"2026-09-10\",\"device\":\"laptop \",\"seconds\":600,\"updatedAt\":1789000600000}]}]}",
    "expect": {
      "format": 1,
      "device": "laptop",
      "writtenAt": 1789000000000,
      "profiles": [
        {
          "name": "André",
          "progress": [],
          "watched": [],
          "titleStats": [
            { "setId": "01A", "device": "laptop", "startedAt": 1789000000000, "lastWatchedAt": 1789000600000, "seconds": 600, "updatedAt": 1789000600000 }
          ],
          "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 1789000600000 }]
        }
      ]
    }
  },
  {
    "name": "a day shaped right is kept even where no calendar has it",
    "input": "{\"format\":1,\"device\":\"laptop\",\"writtenAt\":1789000000000,\"profiles\":[{\"name\":\"André\",\"progress\":[],\"watched\":[],\"dayStats\":[{\"day\":\"2026-13-45\",\"device\":\"laptop\",\"seconds\":600,\"updatedAt\":1789000600000}]}]}",
    "expect": {
      "format": 1,
      "device": "laptop",
      "writtenAt": 1789000000000,
      "profiles": [
        {
          "name": "André",
          "progress": [],
          "watched": [],
          "dayStats": [{ "day": "2026-13-45", "device": "laptop", "seconds": 600, "updatedAt": 1789000600000 }]
        }
      ]
    }
  }
]
```

- [ ] **Step 2: The runner block** — append to `web/test/shared-watch-state-fixtures.test.ts`:

```ts
describe("stats-record-parse fixtures", () => {
  interface Case {
    name: string;
    input: string;
    expect: SyncRecord | null;
  }

  for (const one of load<Case[]>("stats-record-parse.json")) {
    test(one.name, () => {
      expect(parseRecord(one.input)).toEqual(one.expect);
    });
  }
});
```

- [ ] **Step 3: Run, expect failures**

Run: `cd web && bun test test/shared-watch-state-fixtures.test.ts`
Expected: FAIL — the cases that expect `titleStats`/`dayStats` in the result (e.g. "title and day rows read back as written") fail because `parseRecord` drops unknown keys; "a stats key that is not an array is read as absent" already passes.

- [ ] **Step 4: Implement the rows** — `web/src/state/stats-record.ts`

```ts
/**
 * Viewing stats on the sync record: two new keys on a profile, `titleStats`
 * and `dayStats`, and reading them as if a stranger wrote them.
 *
 * New keys rather than a `SYNC_FORMAT` bump, for the reason `sync-record.ts`
 * gives for `unwatched`: a reader that predates them drops what it does not
 * know and goes on merging everything else, where a bumped format would make
 * it refuse the whole document.
 *
 * **Every row names the device that counted it.** Each device writes only its
 * own rows and passes on everyone else's unchanged, so a total is a sum over
 * devices of rows each one owns — two documents carrying the same row can
 * never count it twice.
 *
 * Stricter than the older rows' parsing: a number must be a JSON number.
 * Those coerce numeric strings because documents from before they were
 * checked carry them; nothing ever wrote these keys that way.
 */

/** One title, as one device counted it. Times are epoch ms. */
export interface TitleStatRow {
  setId: string;
  device: string;
  startedAt: number;
  lastWatchedAt: number;
  seconds: number;
  /** When this device last started the title over after finishing it. */
  againAt?: number;
  updatedAt: number;
}

/** One local day (`YYYY-MM-DD`, the counting device's own date), as one device counted it. */
export interface DayStatRow {
  day: string;
  device: string;
  seconds: number;
  updatedAt: number;
}

/** The two keys as a profile carries them: each omitted when it has no rows. */
export interface StatsRows {
  titleStats?: TitleStatRow[];
  dayStats?: DayStatRow[];
}

const DAY = /^\d{4}-\d{2}-\d{2}$/;

/** One device cannot watch more than a day in a day. */
const MAX_DAY_SECONDS = 86_400;

/** A non-empty string, trimmed — the same rule every other id here follows. */
function idOf(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const clean = value.trim();
  return clean === "" ? null : clean;
}

/** A JSON number, finite and not negative, or `null`. */
function amountOf(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) && value >= 0 ? value : null;
}

function objectOf(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function titleRow(value: unknown): TitleStatRow | null {
  const raw = objectOf(value);
  if (raw === null) return null;
  const setId = idOf(raw.setId);
  const device = idOf(raw.device);
  const startedAt = amountOf(raw.startedAt);
  const lastWatchedAt = amountOf(raw.lastWatchedAt);
  const seconds = amountOf(raw.seconds);
  const updatedAt = amountOf(raw.updatedAt);
  if (setId === null || device === null || startedAt === null || lastWatchedAt === null) return null;
  if (seconds === null || updatedAt === null) return null;
  const row = { setId, device, startedAt, lastWatchedAt, seconds, updatedAt };
  // `null` is no restart, the same as absent: a writer that serialises an
  // empty optional as `null` has said nothing, not something malformed.
  if (raw.againAt === undefined || raw.againAt === null) return row;
  const againAt = amountOf(raw.againAt);
  return againAt === null ? null : { ...row, againAt };
}

function dayRow(value: unknown): DayStatRow | null {
  const raw = objectOf(value);
  if (raw === null) return null;
  const day = typeof raw.day === "string" && DAY.test(raw.day) ? raw.day : null;
  const device = idOf(raw.device);
  const seconds = amountOf(raw.seconds);
  const updatedAt = amountOf(raw.updatedAt);
  if (day === null || device === null || seconds === null || updatedAt === null) return null;
  return seconds > MAX_DAY_SECONDS ? null : { day, device, seconds, updatedAt };
}

/** Row by row: a bad row is dropped, never the document. Not an array → absent. */
function rowsOf<T>(value: unknown, one: (entry: unknown) => T | null): T[] | undefined {
  if (!Array.isArray(value)) return undefined;
  return value.flatMap((entry) => {
    const row = one(entry);
    return row === null ? [] : [row];
  });
}

export const parseTitleStatRows = (value: unknown) => rowsOf(value, titleRow);
export const parseDayStatRows = (value: unknown) => rowsOf(value, dayRow);
```

Self-contained helpers rather than importing `sync-record.ts`'s private ones: `sync-record.ts` imports this module, and the reverse import would be a cycle. `preferences-record.ts` makes the same choice.

- [ ] **Step 5: Hook it into `parseRecord`** — `web/src/state/sync-record.ts`

After line 56 (`import { parsePreferenceRows, type PreferenceRow } from "./preferences-record";`) add:

```ts
import { parseDayStatRows, parseTitleStatRows, type StatsRows } from "./stats-record";
```

Replace line 112 `export interface ProfileState {` with:

```ts
/** `titleStats`/`dayStats` come from `StatsRows`: see `stats-record.ts`. */
export interface ProfileState extends StatsRows {
```

After line 203 (`      preferences: row.preferences === undefined ? undefined : parsePreferenceRows(row.preferences),`) add:

```ts
      titleStats: parseTitleStatRows(row.titleStats),
      dayStats: parseDayStatRows(row.dayStats),
```

- [ ] **Step 6: Run, expect PASS — new cases and every existing one**

Run: `cd web && bun test test/shared-watch-state-fixtures.test.ts test/state-sync-record.test.ts test/state-merge.test.ts && bun run typecheck`
Expected: shared fixtures 154 pass, 0 fail; the two state suites 0 fail; `tsc` silent.

- [ ] **Step 7: Commit**

```bash
git add web/src/state/stats-record.ts web/src/state/sync-record.ts web/test/fixtures/watch-state/stats-record-parse.json web/test/shared-watch-state-fixtures.test.ts
git commit -m "feat(web): read viewing-stats rows off a sync document, row by row"
```

---

## Task 1.3: merge both keys per viewer

**Files:**
- Create: `web/src/state/stats-merge.ts`
- Create: `web/test/fixtures/watch-state/stats-merge.json`
- Modify: `web/src/state/merge.ts` (two imports, `MergedProfile extends StatsRows`, two lines in `mergeStates`)
- Modify: `web/test/shared-watch-state-fixtures.test.ts` (one `describe`)

**Interfaces:**
- Consumes: `normalName` (`sync-record.ts:153`), `keep`/`Held` (`tie-break.ts:7-38`).
- Produces: `mergeStats(records: SyncRecord[]): Map<string, StatsRows>` keyed by normalised name; `MergedProfile` gains `titleStats?`/`dayStats?`, each omitted when empty (contract §3).
- Merge key: `JSON.stringify([setId, device])` / `JSON.stringify([day, device])`; the tie-break device is the **document's** device, as for every other kept row.

- [ ] **Step 1: The fixture** — `web/test/fixtures/watch-state/stats-merge.json` (inputs omit empty `progress`/`watched`, which both engines default)

```json
[
  {
    "name": "one device's rows pass through unchanged",
    "records": [
      {
        "format": 1,
        "device": "laptop",
        "writtenAt": 0,
        "profiles": [
          {
            "name": "André",
            "titleStats": [{ "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 2000, "seconds": 600, "updatedAt": 2000 }],
            "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 2000 }]
          }
        ]
      }
    ],
    "expect": {
      "profiles": [
        {
          "name": "andré",
          "displayName": "André",
          "titleStats": [{ "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 2000, "seconds": 600, "updatedAt": 2000 }],
          "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 2000 }]
        }
      ]
    }
  },
  {
    "name": "the newer row for one title and device wins over an older copy passed on",
    "records": [
      {
        "format": 1,
        "device": "laptop",
        "writtenAt": 0,
        "profiles": [
          {
            "name": "André",
            "titleStats": [{ "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 2000, "seconds": 600, "updatedAt": 2000 }],
            "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 2000 }]
          }
        ]
      },
      {
        "format": 1,
        "device": "desktop",
        "writtenAt": 0,
        "profiles": [
          {
            "name": "André",
            "titleStats": [{ "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 1000, "seconds": 300, "updatedAt": 1000 }],
            "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 300, "updatedAt": 1000 }]
          }
        ]
      }
    ],
    "expect": {
      "profiles": [
        {
          "name": "andré",
          "displayName": "André",
          "titleStats": [{ "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 2000, "seconds": 600, "updatedAt": 2000 }],
          "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 2000 }]
        }
      ]
    }
  },
  {
    "name": "the same row in two documents is kept once",
    "records": [
      {
        "format": 1,
        "device": "laptop",
        "writtenAt": 0,
        "profiles": [
          {
            "name": "André",
            "titleStats": [{ "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 2000, "seconds": 600, "updatedAt": 2000 }],
            "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 2000 }]
          }
        ]
      },
      {
        "format": 1,
        "device": "desktop",
        "writtenAt": 0,
        "profiles": [
          {
            "name": "André",
            "titleStats": [{ "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 2000, "seconds": 600, "updatedAt": 2000 }],
            "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 2000 }]
          }
        ]
      }
    ],
    "expect": {
      "profiles": [
        {
          "name": "andré",
          "displayName": "André",
          "titleStats": [{ "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 2000, "seconds": 600, "updatedAt": 2000 }],
          "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 2000 }]
        }
      ]
    }
  },
  {
    "name": "two devices' rows for one title and one day are both kept, never added together",
    "records": [
      {
        "format": 1,
        "device": "laptop",
        "writtenAt": 0,
        "profiles": [
          {
            "name": "André",
            "titleStats": [{ "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 2000, "seconds": 600, "updatedAt": 2000 }],
            "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 2000 }]
          }
        ]
      },
      {
        "format": 1,
        "device": "phone",
        "writtenAt": 0,
        "profiles": [
          {
            "name": "André",
            "titleStats": [
              { "setId": "01A", "device": "phone", "startedAt": 100, "lastWatchedAt": 3000, "seconds": 300, "updatedAt": 3000, "againAt": 2000 }
            ],
            "dayStats": [{ "day": "2026-09-10", "device": "phone", "seconds": 300, "updatedAt": 3000 }]
          }
        ]
      }
    ],
    "expect": {
      "profiles": [
        {
          "name": "andré",
          "displayName": "André",
          "titleStats": [
            { "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 2000, "seconds": 600, "updatedAt": 2000 },
            { "setId": "01A", "device": "phone", "startedAt": 100, "lastWatchedAt": 3000, "seconds": 300, "updatedAt": 3000, "againAt": 2000 }
          ],
          "dayStats": [
            { "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 2000 },
            { "day": "2026-09-10", "device": "phone", "seconds": 300, "updatedAt": 3000 }
          ]
        }
      ]
    }
  },
  {
    "name": "a device passing on every row it holds counts nothing twice",
    "records": [
      {
        "format": 1,
        "device": "laptop",
        "writtenAt": 0,
        "profiles": [
          {
            "name": "André",
            "titleStats": [
              { "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 2000, "seconds": 600, "updatedAt": 2000 },
              { "setId": "01A", "device": "phone", "startedAt": 100, "lastWatchedAt": 3000, "seconds": 300, "updatedAt": 3000, "againAt": 2000 }
            ],
            "dayStats": [
              { "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 2000 },
              { "day": "2026-09-10", "device": "phone", "seconds": 300, "updatedAt": 3000 }
            ]
          }
        ]
      },
      {
        "format": 1,
        "device": "phone",
        "writtenAt": 0,
        "profiles": [
          {
            "name": "André",
            "titleStats": [
              { "setId": "01A", "device": "phone", "startedAt": 100, "lastWatchedAt": 3000, "seconds": 300, "updatedAt": 3000, "againAt": 2000 },
              { "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 1000, "seconds": 300, "updatedAt": 1000 }
            ],
            "dayStats": [
              { "day": "2026-09-10", "device": "phone", "seconds": 300, "updatedAt": 3000 },
              { "day": "2026-09-10", "device": "laptop", "seconds": 300, "updatedAt": 1000 }
            ]
          }
        ]
      }
    ],
    "expect": {
      "profiles": [
        {
          "name": "andré",
          "displayName": "André",
          "titleStats": [
            { "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 2000, "seconds": 600, "updatedAt": 2000 },
            { "setId": "01A", "device": "phone", "startedAt": 100, "lastWatchedAt": 3000, "seconds": 300, "updatedAt": 3000, "againAt": 2000 }
          ],
          "dayStats": [
            { "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 2000 },
            { "day": "2026-09-10", "device": "phone", "seconds": 300, "updatedAt": 3000 }
          ]
        }
      ]
    }
  },
  {
    "name": "an exact tie with different contents is decided by the document's device",
    "records": [
      {
        "format": 1,
        "device": "desktop",
        "writtenAt": 0,
        "profiles": [
          {
            "name": "André",
            "titleStats": [{ "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 2000, "seconds": 900, "updatedAt": 2000 }],
            "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 900, "updatedAt": 2000 }]
          }
        ]
      },
      {
        "format": 1,
        "device": "laptop",
        "writtenAt": 0,
        "profiles": [
          {
            "name": "André",
            "titleStats": [{ "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 2000, "seconds": 600, "updatedAt": 2000 }],
            "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 2000 }]
          }
        ]
      }
    ],
    "expect": {
      "profiles": [
        {
          "name": "andré",
          "displayName": "André",
          "titleStats": [{ "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 2000, "seconds": 600, "updatedAt": 2000 }],
          "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 2000 }]
        }
      ]
    }
  },
  {
    "name": "one viewer typed two ways is one viewer",
    "records": [
      {
        "format": 1,
        "device": "laptop",
        "writtenAt": 0,
        "profiles": [
          {
            "name": "André",
            "titleStats": [{ "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 2000, "seconds": 600, "updatedAt": 2000 }],
            "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 2000 }]
          }
        ]
      },
      {
        "format": 1,
        "device": "phone",
        "writtenAt": 0,
        "profiles": [
          {
            "name": " andré ",
            "titleStats": [
              { "setId": "01A", "device": "phone", "startedAt": 100, "lastWatchedAt": 3000, "seconds": 300, "updatedAt": 3000, "againAt": 2000 }
            ],
            "dayStats": [{ "day": "2026-09-10", "device": "phone", "seconds": 300, "updatedAt": 3000 }]
          }
        ]
      }
    ],
    "expect": {
      "profiles": [
        {
          "name": "andré",
          "displayName": "andré",
          "titleStats": [
            { "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 2000, "seconds": 600, "updatedAt": 2000 },
            { "setId": "01A", "device": "phone", "startedAt": 100, "lastWatchedAt": 3000, "seconds": 300, "updatedAt": 3000, "againAt": 2000 }
          ],
          "dayStats": [
            { "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 2000 },
            { "day": "2026-09-10", "device": "phone", "seconds": 300, "updatedAt": 3000 }
          ]
        }
      ]
    }
  },
  {
    "name": "two viewers keep their own rows for the same title",
    "records": [
      {
        "format": 1,
        "device": "laptop",
        "writtenAt": 0,
        "profiles": [
          {
            "name": "André",
            "titleStats": [{ "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 2000, "seconds": 600, "updatedAt": 2000 }]
          },
          {
            "name": "Ben",
            "titleStats": [{ "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 1200, "seconds": 120, "updatedAt": 1200 }]
          }
        ]
      }
    ],
    "expect": {
      "profiles": [
        {
          "name": "andré",
          "displayName": "André",
          "titleStats": [{ "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 2000, "seconds": 600, "updatedAt": 2000 }]
        },
        {
          "name": "ben",
          "displayName": "Ben",
          "titleStats": [{ "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 1200, "seconds": 120, "updatedAt": 1200 }]
        }
      ]
    }
  },
  {
    "name": "a viewer with no stats rows has no stats keys",
    "records": [
      {
        "format": 1,
        "device": "laptop",
        "writtenAt": 0,
        "profiles": [{ "name": "André", "progress": [{ "setId": "01A", "at": 742, "duration": 1204, "updatedAt": 500 }] }]
      },
      { "format": 1, "device": "phone", "writtenAt": 0, "profiles": [{ "name": "André", "titleStats": [], "dayStats": [] }] }
    ],
    "expect": { "profiles": [{ "name": "andré", "displayName": "André" }] }
  },
  {
    "name": "a document from before stats existed changes nothing",
    "records": [
      {
        "format": 1,
        "device": "laptop",
        "writtenAt": 0,
        "profiles": [
          {
            "name": "André",
            "titleStats": [{ "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 2000, "seconds": 600, "updatedAt": 2000 }],
            "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 2000 }]
          }
        ]
      },
      { "format": 1, "device": "old-tablet", "writtenAt": 0, "profiles": [{ "name": "André" }] }
    ],
    "expect": {
      "profiles": [
        {
          "name": "andré",
          "displayName": "André",
          "titleStats": [{ "setId": "01A", "device": "laptop", "startedAt": 100, "lastWatchedAt": 2000, "seconds": 600, "updatedAt": 2000 }],
          "dayStats": [{ "day": "2026-09-10", "device": "laptop", "seconds": 600, "updatedAt": 2000 }]
        }
      ]
    }
  }
]
```

- [ ] **Step 2: The runner block** — append to `web/test/shared-watch-state-fixtures.test.ts`:

```ts
describe("stats-merge fixtures", () => {
  interface Case {
    name: string;
    records: SyncRecord[];
    expect: ReturnType<typeof canonicalStats>;
  }

  /** Code-unit order, as the engines sort; rows by their merge key. */
  const order = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0);

  /**
   * The stats keys only, passed through as `mergeStates` left them: a key
   * present but empty would show here as `[]` and fail a case that omits it,
   * which is the omitted-when-empty rule being checked.
   */
  function canonicalStats(state: MergedState) {
    return {
      profiles: state.profiles
        .map((profile) => ({
          name: profile.name,
          displayName: profile.displayName,
          ...(profile.titleStats
            ? { titleStats: [...profile.titleStats].sort((a, b) => order(a.setId, b.setId) || order(a.device, b.device)) }
            : {}),
          ...(profile.dayStats
            ? { dayStats: [...profile.dayStats].sort((a, b) => order(a.day, b.day) || order(a.device, b.device)) }
            : {}),
        }))
        .sort((a, b) => order(a.name, b.name)),
    };
  }

  for (const one of load<Case[]>("stats-merge.json")) {
    test(one.name, () => {
      expect(canonicalStats(mergeStates(one.records))).toEqual(one.expect);
      expect(canonicalStats(mergeStates([...one.records].reverse()))).toEqual(one.expect);
    });
  }
});
```

- [ ] **Step 3: Run, expect failures**

Run: `cd web && bun test test/shared-watch-state-fixtures.test.ts`
Expected: FAIL — every `stats-merge` case that expects rows (the merged profile has no `titleStats`); "a viewer with no stats rows has no stats keys" passes already.

- [ ] **Step 4: Implement** — `web/src/state/stats-merge.ts`

```ts
/**
 * Merging viewing stats: per viewer, the newest row per (title, device) and
 * per (day, device), ties broken the way every other kept row's are.
 *
 * Nothing is added up here. Two devices' rows for one title stay two rows,
 * and the same row arriving in two documents — one device's own and another
 * passing it on — stays one: totals are sums over devices, taken later by
 * `stats-summary.ts`, so a merge can never count anything twice.
 *
 * Split out of `merge.ts`, which `mergeStates` calls this from.
 */

import { normalName, type SyncRecord } from "./sync-record";
import type { DayStatRow, StatsRows, TitleStatRow } from "./stats-record";
import { keep, type Held } from "./tie-break";

interface Viewer {
  titles: Map<string, Held<TitleStatRow>>;
  days: Map<string, Held<DayStatRow>>;
}

/** Each viewer's merged stats by normalised name; a key is omitted when it has no rows. */
export function mergeStats(records: SyncRecord[]): Map<string, StatsRows> {
  const viewers = new Map<string, Viewer>();
  for (const record of records) {
    const device = typeof record?.device === "string" ? record.device : "";
    for (const profile of record?.profiles ?? []) {
      const name = normalName(profile.name);
      if (name === null) continue;
      let held = viewers.get(name);
      if (held === undefined) {
        held = { titles: new Map(), days: new Map() };
        viewers.set(name, held);
      }
      // JSON for the key: a set id and a device id may hold any text.
      for (const row of profile.titleStats ?? []) keep(held.titles, JSON.stringify([row.setId, row.device]), row, device);
      for (const row of profile.dayStats ?? []) keep(held.days, JSON.stringify([row.day, row.device]), row, device);
    }
  }
  const merged = new Map<string, StatsRows>();
  for (const [name, held] of viewers) {
    const titleStats = [...held.titles.values()].map((one) => one.row);
    const dayStats = [...held.days.values()].map((one) => one.row);
    merged.set(name, {
      ...(titleStats.length > 0 ? { titleStats } : {}),
      ...(dayStats.length > 0 ? { dayStats } : {}),
    });
  }
  return merged;
}
```

- [ ] **Step 5: Hook it into `mergeStates`** — `web/src/state/merge.ts`

After line 43 (`import { keep, type Held } from "./tie-break";`) add:

```ts
import { mergeStats } from "./stats-merge";
import type { StatsRows } from "./stats-record";
```

Replace line 46 `export interface MergedProfile {` with `export interface MergedProfile extends StatsRows {`.

Replace line 162 `  const profiles: MergedProfile[] = [];` with:

```ts
  const stats = mergeStats(records);
  const profiles: MergedProfile[] = [];
```

After line 186 (`      preferences: [...held.preferences.values()].map((one) => one.row),`) add:

```ts
      ...stats.get(name),
```

`mergeStats` walks the same records with the same `normalName`, so every viewer it returns is one `mergeStates` already made; a viewer without stats rows gets `{}`, which spreads to nothing.

- [ ] **Step 6: Run, expect PASS**

Run: `cd web && bun test test/shared-watch-state-fixtures.test.ts test/state-merge.test.ts test/state-lists-sync.test.ts test/preferences-sync.test.ts && bun run typecheck`
Expected: shared fixtures 164 pass, 0 fail; the other suites 0 fail; `tsc` silent. `wc -l web/src/state/merge.ts` → 198.

- [ ] **Step 7: Commit**

```bash
git add web/src/state/stats-merge.ts web/src/state/merge.ts web/test/fixtures/watch-state/stats-merge.json web/test/shared-watch-state-fixtures.test.ts
git commit -m "feat(web): merge viewing-stats rows per viewer and device, newest wins"
```

---

## Task 1.4: `summarize`, then the phase's checks and version

**Files:**
- Create: `web/src/state/stats-summary.ts`
- Create: `web/test/fixtures/watch-state/stats-summary.json`
- Modify: `web/test/shared-watch-state-fixtures.test.ts` (one import, one `describe`)
- Modify: `Cargo.toml`, `Cargo.lock`, `web/package.json`, `android/app/build.gradle.kts` (patch bump)

**Interfaces:**
- Produces (contract §4, verbatim): `SummaryInput`, `HistoryEntry`, `StatsSummary`, `summarize(input: SummaryInput): StatsSummary`. Consumed by phase 02's route and pinned for phase 03's Rust port.
- Fixture shape: `{ name, input, expect }` where `expect` names only the summary keys the case is about — the runner compares exactly those keys (a case about the week need not spell out 30 bars). Phase 03's runner must compare the same way.

- [ ] **Step 1: The fixture** — `web/test/fixtures/watch-state/stats-summary.json` (2026-10-03 is a Saturday, its ISO week starts Monday 2026-09-28; 2027-01-02 is a Saturday, its week starts 2026-12-28)

```json
[
  {
    "name": "the week starts on Monday and reaches back into last month; the month does not",
    "input": {
      "today": "2026-10-03",
      "titles": [],
      "days": [
        { "day": "2026-09-27", "device": "laptop", "seconds": 100, "updatedAt": 1000 },
        { "day": "2026-09-28", "device": "laptop", "seconds": 200, "updatedAt": 1000 },
        { "day": "2026-09-30", "device": "laptop", "seconds": 300, "updatedAt": 1000 },
        { "day": "2026-10-01", "device": "laptop", "seconds": 400, "updatedAt": 1000 },
        { "day": "2026-10-03", "device": "laptop", "seconds": 500, "updatedAt": 1000 }
      ],
      "watched": []
    },
    "expect": { "weekSeconds": 1400, "monthSeconds": 900, "allSeconds": 1500 }
  },
  {
    "name": "on a Monday the week is that day alone",
    "input": {
      "today": "2026-09-28",
      "titles": [],
      "days": [
        { "day": "2026-09-27", "device": "laptop", "seconds": 100, "updatedAt": 1000 },
        { "day": "2026-09-28", "device": "laptop", "seconds": 50, "updatedAt": 1000 }
      ],
      "watched": []
    },
    "expect": { "weekSeconds": 50, "monthSeconds": 150, "allSeconds": 150 }
  },
  {
    "name": "on a Sunday the week still began on Monday",
    "input": {
      "today": "2026-10-04",
      "titles": [],
      "days": [
        { "day": "2026-09-27", "device": "laptop", "seconds": 40, "updatedAt": 1000 },
        { "day": "2026-09-28", "device": "laptop", "seconds": 10, "updatedAt": 1000 },
        { "day": "2026-10-04", "device": "laptop", "seconds": 20, "updatedAt": 1000 }
      ],
      "watched": []
    },
    "expect": { "weekSeconds": 30, "monthSeconds": 20, "allSeconds": 70 }
  },
  {
    "name": "across a new year: the week spans both years, the month only January",
    "input": {
      "today": "2027-01-02",
      "titles": [],
      "days": [
        { "day": "2026-12-27", "device": "laptop", "seconds": 7, "updatedAt": 1000 },
        { "day": "2026-12-28", "device": "laptop", "seconds": 100, "updatedAt": 1000 },
        { "day": "2026-12-31", "device": "laptop", "seconds": 200, "updatedAt": 1000 },
        { "day": "2027-01-01", "device": "laptop", "seconds": 300, "updatedAt": 1000 },
        { "day": "2027-01-02", "device": "laptop", "seconds": 400, "updatedAt": 1000 }
      ],
      "watched": []
    },
    "expect": { "weekSeconds": 1000, "monthSeconds": 700, "allSeconds": 1007 }
  },
  {
    "name": "every device's row for a day is added",
    "input": {
      "today": "2026-10-03",
      "titles": [],
      "days": [
        { "day": "2026-10-03", "device": "laptop", "seconds": 600, "updatedAt": 1000 },
        { "day": "2026-10-03", "device": "phone", "seconds": 300, "updatedAt": 1000 },
        { "day": "2026-10-02", "device": "tv", "seconds": 30, "updatedAt": 1000 }
      ],
      "watched": []
    },
    "expect": { "weekSeconds": 930, "monthSeconds": 930, "allSeconds": 930 }
  },
  {
    "name": "thirty days, oldest first, ending today, a day nobody watched as 0",
    "input": {
      "today": "2026-10-03",
      "titles": [],
      "days": [
        { "day": "2026-09-03", "device": "laptop", "seconds": 120, "updatedAt": 1000 },
        { "day": "2026-09-04", "device": "laptop", "seconds": 60, "updatedAt": 1000 },
        { "day": "2026-09-20", "device": "laptop", "seconds": 15, "updatedAt": 1000 },
        { "day": "2026-09-20", "device": "phone", "seconds": 5, "updatedAt": 1000 },
        { "day": "2026-10-03", "device": "laptop", "seconds": 30, "updatedAt": 1000 }
      ],
      "watched": []
    },
    "expect": {
      "allSeconds": 230,
      "last30": [
        { "day": "2026-09-04", "seconds": 60 },
        { "day": "2026-09-05", "seconds": 0 },
        { "day": "2026-09-06", "seconds": 0 },
        { "day": "2026-09-07", "seconds": 0 },
        { "day": "2026-09-08", "seconds": 0 },
        { "day": "2026-09-09", "seconds": 0 },
        { "day": "2026-09-10", "seconds": 0 },
        { "day": "2026-09-11", "seconds": 0 },
        { "day": "2026-09-12", "seconds": 0 },
        { "day": "2026-09-13", "seconds": 0 },
        { "day": "2026-09-14", "seconds": 0 },
        { "day": "2026-09-15", "seconds": 0 },
        { "day": "2026-09-16", "seconds": 0 },
        { "day": "2026-09-17", "seconds": 0 },
        { "day": "2026-09-18", "seconds": 0 },
        { "day": "2026-09-19", "seconds": 0 },
        { "day": "2026-09-20", "seconds": 20 },
        { "day": "2026-09-21", "seconds": 0 },
        { "day": "2026-09-22", "seconds": 0 },
        { "day": "2026-09-23", "seconds": 0 },
        { "day": "2026-09-24", "seconds": 0 },
        { "day": "2026-09-25", "seconds": 0 },
        { "day": "2026-09-26", "seconds": 0 },
        { "day": "2026-09-27", "seconds": 0 },
        { "day": "2026-09-28", "seconds": 0 },
        { "day": "2026-09-29", "seconds": 0 },
        { "day": "2026-09-30", "seconds": 0 },
        { "day": "2026-10-01", "seconds": 0 },
        { "day": "2026-10-02", "seconds": 0 },
        { "day": "2026-10-03", "seconds": 30 }
      ]
    }
  },
  {
    "name": "thirty days across a new year",
    "input": {
      "today": "2027-01-02",
      "titles": [],
      "days": [
        { "day": "2026-12-04", "device": "laptop", "seconds": 10, "updatedAt": 1000 },
        { "day": "2027-01-02", "device": "laptop", "seconds": 20, "updatedAt": 1000 }
      ],
      "watched": []
    },
    "expect": {
      "last30": [
        { "day": "2026-12-04", "seconds": 10 },
        { "day": "2026-12-05", "seconds": 0 },
        { "day": "2026-12-06", "seconds": 0 },
        { "day": "2026-12-07", "seconds": 0 },
        { "day": "2026-12-08", "seconds": 0 },
        { "day": "2026-12-09", "seconds": 0 },
        { "day": "2026-12-10", "seconds": 0 },
        { "day": "2026-12-11", "seconds": 0 },
        { "day": "2026-12-12", "seconds": 0 },
        { "day": "2026-12-13", "seconds": 0 },
        { "day": "2026-12-14", "seconds": 0 },
        { "day": "2026-12-15", "seconds": 0 },
        { "day": "2026-12-16", "seconds": 0 },
        { "day": "2026-12-17", "seconds": 0 },
        { "day": "2026-12-18", "seconds": 0 },
        { "day": "2026-12-19", "seconds": 0 },
        { "day": "2026-12-20", "seconds": 0 },
        { "day": "2026-12-21", "seconds": 0 },
        { "day": "2026-12-22", "seconds": 0 },
        { "day": "2026-12-23", "seconds": 0 },
        { "day": "2026-12-24", "seconds": 0 },
        { "day": "2026-12-25", "seconds": 0 },
        { "day": "2026-12-26", "seconds": 0 },
        { "day": "2026-12-27", "seconds": 0 },
        { "day": "2026-12-28", "seconds": 0 },
        { "day": "2026-12-29", "seconds": 0 },
        { "day": "2026-12-30", "seconds": 0 },
        { "day": "2026-12-31", "seconds": 0 },
        { "day": "2027-01-01", "seconds": 0 },
        { "day": "2027-01-02", "seconds": 20 }
      ]
    }
  },
  {
    "name": "a day after today counts in all time only",
    "input": {
      "today": "2026-10-03",
      "titles": [],
      "days": [
        { "day": "2026-10-03", "device": "laptop", "seconds": 100, "updatedAt": 1000 },
        { "day": "2026-10-04", "device": "laptop", "seconds": 700, "updatedAt": 1000 }
      ],
      "watched": []
    },
    "expect": {
      "weekSeconds": 100,
      "monthSeconds": 100,
      "allSeconds": 800,
      "last30": [
        { "day": "2026-09-04", "seconds": 0 },
        { "day": "2026-09-05", "seconds": 0 },
        { "day": "2026-09-06", "seconds": 0 },
        { "day": "2026-09-07", "seconds": 0 },
        { "day": "2026-09-08", "seconds": 0 },
        { "day": "2026-09-09", "seconds": 0 },
        { "day": "2026-09-10", "seconds": 0 },
        { "day": "2026-09-11", "seconds": 0 },
        { "day": "2026-09-12", "seconds": 0 },
        { "day": "2026-09-13", "seconds": 0 },
        { "day": "2026-09-14", "seconds": 0 },
        { "day": "2026-09-15", "seconds": 0 },
        { "day": "2026-09-16", "seconds": 0 },
        { "day": "2026-09-17", "seconds": 0 },
        { "day": "2026-09-18", "seconds": 0 },
        { "day": "2026-09-19", "seconds": 0 },
        { "day": "2026-09-20", "seconds": 0 },
        { "day": "2026-09-21", "seconds": 0 },
        { "day": "2026-09-22", "seconds": 0 },
        { "day": "2026-09-23", "seconds": 0 },
        { "day": "2026-09-24", "seconds": 0 },
        { "day": "2026-09-25", "seconds": 0 },
        { "day": "2026-09-26", "seconds": 0 },
        { "day": "2026-09-27", "seconds": 0 },
        { "day": "2026-09-28", "seconds": 0 },
        { "day": "2026-09-29", "seconds": 0 },
        { "day": "2026-09-30", "seconds": 0 },
        { "day": "2026-10-01", "seconds": 0 },
        { "day": "2026-10-02", "seconds": 0 },
        { "day": "2026-10-03", "seconds": 100 }
      ]
    }
  },
  {
    "name": "nothing watched is nothing, everywhere",
    "input": { "today": "2026-10-03", "titles": [], "days": [], "watched": [] },
    "expect": {
      "weekSeconds": 0,
      "monthSeconds": 0,
      "allSeconds": 0,
      "last30": [
        { "day": "2026-09-04", "seconds": 0 },
        { "day": "2026-09-05", "seconds": 0 },
        { "day": "2026-09-06", "seconds": 0 },
        { "day": "2026-09-07", "seconds": 0 },
        { "day": "2026-09-08", "seconds": 0 },
        { "day": "2026-09-09", "seconds": 0 },
        { "day": "2026-09-10", "seconds": 0 },
        { "day": "2026-09-11", "seconds": 0 },
        { "day": "2026-09-12", "seconds": 0 },
        { "day": "2026-09-13", "seconds": 0 },
        { "day": "2026-09-14", "seconds": 0 },
        { "day": "2026-09-15", "seconds": 0 },
        { "day": "2026-09-16", "seconds": 0 },
        { "day": "2026-09-17", "seconds": 0 },
        { "day": "2026-09-18", "seconds": 0 },
        { "day": "2026-09-19", "seconds": 0 },
        { "day": "2026-09-20", "seconds": 0 },
        { "day": "2026-09-21", "seconds": 0 },
        { "day": "2026-09-22", "seconds": 0 },
        { "day": "2026-09-23", "seconds": 0 },
        { "day": "2026-09-24", "seconds": 0 },
        { "day": "2026-09-25", "seconds": 0 },
        { "day": "2026-09-26", "seconds": 0 },
        { "day": "2026-09-27", "seconds": 0 },
        { "day": "2026-09-28", "seconds": 0 },
        { "day": "2026-09-29", "seconds": 0 },
        { "day": "2026-09-30", "seconds": 0 },
        { "day": "2026-10-01", "seconds": 0 },
        { "day": "2026-10-02", "seconds": 0 },
        { "day": "2026-10-03", "seconds": 0 }
      ],
      "history": []
    }
  },
  {
    "name": "history: started at the earliest device's start, again at the latest restart, each with the title's total",
    "input": {
      "today": "2026-10-03",
      "titles": [
        { "setId": "01A", "device": "laptop", "startedAt": 1791000001000, "lastWatchedAt": 1791000001000, "seconds": 600, "updatedAt": 1791000001000, "againAt": 1791000005000 },
        { "setId": "01A", "device": "phone", "startedAt": 1791000002000, "lastWatchedAt": 1791000002000, "seconds": 300, "updatedAt": 1791000002000, "againAt": 1791000004000 },
        { "setId": "01B", "device": "laptop", "startedAt": 1791000003000, "lastWatchedAt": 1791000003000, "seconds": 120, "updatedAt": 1791000003000 }
      ],
      "days": [],
      "watched": [{ "setId": "01A", "finishedAt": 1791000006000 }]
    },
    "expect": {
      "history": [
        { "kind": "finished", "setId": "01A", "at": 1791000006000, "seconds": 900 },
        { "kind": "again", "setId": "01A", "at": 1791000005000, "seconds": 900 },
        { "kind": "started", "setId": "01B", "at": 1791000003000, "seconds": 120 },
        { "kind": "started", "setId": "01A", "at": 1791000001000, "seconds": 900 }
      ]
    }
  },
  {
    "name": "a title finished before stats existed is still history, with no minutes",
    "input": { "today": "2026-10-03", "titles": [], "days": [], "watched": [{ "setId": "01C", "finishedAt": 1791000000000 }] },
    "expect": { "history": [{ "kind": "finished", "setId": "01C", "at": 1791000000000, "seconds": 0 }] }
  },
  {
    "name": "a title never restarted has no again entry",
    "input": {
      "today": "2026-10-03",
      "titles": [
        { "setId": "01B", "device": "laptop", "startedAt": 1791000000000, "lastWatchedAt": 1791000000000, "seconds": 120, "updatedAt": 1791000000000 }
      ],
      "days": [],
      "watched": []
    },
    "expect": { "history": [{ "kind": "started", "setId": "01B", "at": 1791000000000, "seconds": 120 }] }
  },
  {
    "name": "equal times sort by set id, then finished before again before started",
    "input": {
      "today": "2026-10-03",
      "titles": [
        { "setId": "01B", "device": "laptop", "startedAt": 1791000000000, "lastWatchedAt": 1791000000000, "seconds": 10, "updatedAt": 1791000000000 },
        { "setId": "01A", "device": "laptop", "startedAt": 1791000000000, "lastWatchedAt": 1791000000000, "seconds": 20, "updatedAt": 1791000000000, "againAt": 1791000000000 }
      ],
      "days": [],
      "watched": [{ "setId": "01A", "finishedAt": 1791000000000 }]
    },
    "expect": {
      "history": [
        { "kind": "finished", "setId": "01A", "at": 1791000000000, "seconds": 20 },
        { "kind": "again", "setId": "01A", "at": 1791000000000, "seconds": 20 },
        { "kind": "started", "setId": "01A", "at": 1791000000000, "seconds": 20 },
        { "kind": "started", "setId": "01B", "at": 1791000000000, "seconds": 10 }
      ]
    }
  },
  {
    "name": "set ids sort by character code, not by locale",
    "input": {
      "today": "2026-10-03",
      "titles": [
        { "setId": "01a", "device": "laptop", "startedAt": 1791000000000, "lastWatchedAt": 1791000000000, "seconds": 10, "updatedAt": 1791000000000 },
        { "setId": "01B", "device": "laptop", "startedAt": 1791000000000, "lastWatchedAt": 1791000000000, "seconds": 10, "updatedAt": 1791000000000 }
      ],
      "days": [],
      "watched": []
    },
    "expect": {
      "history": [
        { "kind": "started", "setId": "01B", "at": 1791000000000, "seconds": 10 },
        { "kind": "started", "setId": "01a", "at": 1791000000000, "seconds": 10 }
      ]
    }
  }
]
```

- [ ] **Step 2: The runner block** — in `web/test/shared-watch-state-fixtures.test.ts` add below the `stats-step` import:

```ts
import { summarize, type StatsSummary, type SummaryInput } from "../src/state/stats-summary";
```

and append:

```ts
describe("stats-summary fixtures", () => {
  interface Case {
    name: string;
    input: SummaryInput;
    expect: Partial<StatsSummary>;
  }

  for (const one of load<Case[]>("stats-summary.json")) {
    test(one.name, () => {
      const summary = summarize(one.input);
      // Only the keys a case names: one about the week need not spell out
      // thirty bars.
      const named = Object.fromEntries(
        Object.keys(one.expect).map((key) => [key, summary[key as keyof StatsSummary]]),
      );
      expect(named).toEqual(one.expect);
    });
  }
});
```

- [ ] **Step 3: Run, expect a failure**

Run: `cd web && bun test test/shared-watch-state-fixtures.test.ts`
Expected: FAIL — `Cannot find module "../src/state/stats-summary"`.

- [ ] **Step 4: Implement** — `web/src/state/stats-summary.ts`

```ts
/**
 * What a profile's stats page shows, from the rows every device counted.
 *
 * Pure, and pinned by `test/fixtures/watch-state/stats-summary.json`, which
 * the Android core runs too. Days are `YYYY-MM-DD` strings throughout and
 * compared as strings, which orders them correctly; arithmetic on them is
 * done in UTC so a daylight-saving change cannot skip or repeat a day.
 */

import type { DayStatRow, TitleStatRow } from "./stats-record";

export interface SummaryInput {
  /** The reading engine's local date. */
  today: string;
  titles: TitleStatRow[];
  days: DayStatRow[];
  /** Live `watched` rows only — a title un-marked is not finished. */
  watched: { setId: string; finishedAt: number }[];
}

export interface HistoryEntry {
  kind: "started" | "finished" | "again";
  setId: string;
  /** Epoch ms. */
  at: number;
  /** The title's total across devices; 0 when it has no title row. */
  seconds: number;
}

export interface StatsSummary {
  /** Monday of today's ISO week through today. */
  weekSeconds: number;
  /** Today's calendar month, through today. */
  monthSeconds: number;
  /** Every day row, a future one included. */
  allSeconds: number;
  /** Thirty days, oldest first, ending today; a day nobody watched is 0. */
  last30: { day: string; seconds: number }[];
  /** Newest first. */
  history: HistoryEntry[];
}

/** Equal times sort a finish first, then a restart, then a first play. */
const KIND_ORDER = { finished: 0, again: 1, started: 2 } as const;

/** `day` moved by `by` days. */
function shiftDay(day: string, by: number): string {
  const [year, month, date] = day.split("-").map(Number) as [number, number, number];
  return new Date(Date.UTC(year, month - 1, date + by)).toISOString().slice(0, 10);
}

/** 0 for Monday through 6 for Sunday. */
function isoWeekday(day: string): number {
  return (new Date(`${day}T00:00:00Z`).getUTCDay() + 6) % 7;
}

/** Code-unit order, the same on every engine — not `localeCompare`. */
const byText = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0);

export function summarize(input: SummaryInput): StatsSummary {
  const { today } = input;
  const byDay = new Map<string, number>();
  let allSeconds = 0;
  for (const row of input.days) {
    byDay.set(row.day, (byDay.get(row.day) ?? 0) + row.seconds);
    allSeconds += row.seconds;
  }
  const weekStart = shiftDay(today, -isoWeekday(today));
  const monthStart = `${today.slice(0, 7)}-01`;
  let weekSeconds = 0;
  let monthSeconds = 0;
  for (const [day, seconds] of byDay) {
    if (day > today) continue;
    if (day >= weekStart) weekSeconds += seconds;
    if (day >= monthStart) monthSeconds += seconds;
  }
  const last30 = Array.from({ length: 30 }, (_, index) => {
    const day = shiftDay(today, index - 29);
    return { day, seconds: byDay.get(day) ?? 0 };
  });
  return { weekSeconds, monthSeconds, allSeconds, last30, history: historyOf(input) };
}

function historyOf(input: SummaryInput): HistoryEntry[] {
  const titles = new Map<string, { seconds: number; started: number; again: number | null }>();
  for (const row of input.titles) {
    const held = titles.get(row.setId);
    const again = row.againAt ?? null;
    if (held === undefined) {
      titles.set(row.setId, { seconds: row.seconds, started: row.startedAt, again });
      continue;
    }
    held.seconds += row.seconds;
    held.started = Math.min(held.started, row.startedAt);
    if (again !== null) held.again = held.again === null ? again : Math.max(held.again, again);
  }
  const history: HistoryEntry[] = [];
  for (const [setId, held] of titles) {
    history.push({ kind: "started", setId, at: held.started, seconds: held.seconds });
    if (held.again !== null) history.push({ kind: "again", setId, at: held.again, seconds: held.seconds });
  }
  // Finishes from before stats existed have no title row and are history all the same.
  for (const row of input.watched) {
    history.push({ kind: "finished", setId: row.setId, at: row.finishedAt, seconds: titles.get(row.setId)?.seconds ?? 0 });
  }
  return history.sort((a, b) => b.at - a.at || byText(a.setId, b.setId) || KIND_ORDER[a.kind] - KIND_ORDER[b.kind]);
}
```

- [ ] **Step 5: Run, expect PASS**

Run: `cd web && bun test test/shared-watch-state-fixtures.test.ts`
Expected: 178 pass, 0 fail.

Sanity-check that the fixtures bite (then restore): swapping `KIND_ORDER` to `{ finished: 2, again: 1, started: 0 }` fails 1 case; `getUTCDay() + 0` fails 4; dropping `if (day > today) continue;` fails 1. (Verified while writing this plan; do not commit the mutations.)

- [ ] **Step 6: The phase's whole check**

Run, from the worktree root:

```bash
(cd web && bun test && bun run typecheck && bun run lint)
cargo test -p mediagram-core --test shared_watch_state_fixtures
```

Expected: `bun test` 0 fail; `tsc` and `eslint` silent; the Rust fixture runner passes unchanged (it reads no new file).

- [ ] **Step 7: Bump the patch version, by pattern** (read the current version; never type it)

```bash
current=$(grep -m1 -oP '^version = "\K[0-9]+\.[0-9]+\.[0-9]+' Cargo.toml)
next=$(echo "$current" | awk -F. '{print $1"."$2"."$3+1}')
sed -i -E '0,/^version = "[0-9]+\.[0-9]+\.[0-9]+"$/s//version = "'"$next"'"/' Cargo.toml
sed -i -E 's/^(  "version": ")[0-9]+\.[0-9]+\.[0-9]+(",)$/\1'"$next"'\2/' web/package.json
sed -i -E 's/^(        versionName = ")[0-9]+\.[0-9]+\.[0-9]+(")$/\1'"$next"'\2/' android/app/build.gradle.kts
cargo metadata -q --format-version 1 >/dev/null
grep -m1 '^version' Cargo.toml; grep '"version"' web/package.json; grep 'versionName = ' android/app/build.gradle.kts
git diff --stat Cargo.lock
```

Expected: all three print `$next`; `Cargo.lock` changes only the workspace crates' `version` lines. If another session moved `main` meanwhile, the pattern still matches — read the printed value, do not assume it.

- [ ] **Step 8: Commit**

```bash
git add web/src/state/stats-summary.ts web/test/fixtures/watch-state/stats-summary.json web/test/shared-watch-state-fixtures.test.ts \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts
git commit -m "feat(web): summarize a profile's viewing stats — week, month, all time, thirty days, history"
```

---

## Risk

| Risk | L × I | Mitigation |
|---|---|---|
| Rust port reads a fixture differently (float sums, sort, day maths) | M × H | Binary-exact seconds; code-unit sort; UTC day arithmetic; summary compared per named key — all stated in the fixture runner and above for phase 03. |
| Parse change alters an existing fixture's result | L × H | Absent key → `undefined` (ignored by `toEqual`; Rust `Vec` defaults); existing fixture files untouched; their runners run in Tasks 1.2/1.3. |
| `merge.ts` passes 200 lines | L × L | Verified 198 after Task 1.3; the merge itself lives in `stats-merge.ts`. |

**Rollback:** revert the four commits. Nothing writes the new keys or touches `state.db` in this phase, so no device, document or database is affected.

## Success criteria

- `web/test/fixtures/watch-state/stats-{step,record-parse,merge,summary}.json` exist with 17 / 16 / 10 / 14 cases; `bun test test/shared-watch-state-fixtures.test.ts` → 178 pass, 0 fail, merge cases green in both orders.
- `record-parse.json`, `merge.json`, `lists-merge.json`, `next-up.json`, `resume-point.json` byte-identical to `main` (`git diff main -- web/test/fixtures/watch-state/` lists only the four new files).
- `cd web && bun test && bun run typecheck && bun run lint` → 0 fail, silent; `cargo test -p mediagram-core --test shared_watch_state_fixtures` passes.
- `SYNC_FORMAT` is still 1 (`grep -n "SYNC_FORMAT = 1" web/src/state/sync-record.ts`).
- All three manifests carry the same new patch version; `Cargo.lock` agrees.
