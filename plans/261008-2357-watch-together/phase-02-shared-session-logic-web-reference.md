# Phase 02 — Shared session logic (web reference) and fixtures

**Spec:** `../spec.md` §1, §2. **Status:** pending. **Depends on:** phase 01 verdict (history read finds a fresh message: yes, see reports/probe-261009-history-read-and-edit-push-report.md).

## Context

This is the pure logic of watch together. It has no Telegram, no DOM and no clock of its own: every function takes `now`. The web module is the reference. Phase 06 ports it to Kotlin (`android/core/model`) and checks the port against the same fixtures, as `ProfileRolesFixtureTest` already does for `profile-rules.json`.

**Plain JavaScript in `web/public/lib/together/`, with a `.d.ts` beside each file.** The browser loads these files directly, and the server imports them (`web/src/catalog/routes.ts:20` already imports `public/lib/library.js`). `address.js` with `address.d.ts` shows the shape: `checkJs` is off, so the `.d.ts` is what TypeScript sees. Use the TS code blocks below as the `.d.ts` declarations, and write the JS bodies without type annotations. JSDoc `@param` tags are optional, as in `address.js`.

The fixture pattern to copy is `web/test/channel-updates-fixtures.test.ts` alongside `crates/mediagram-core/tests/shared_channel_update_fixtures.rs`: a JSON array of `{name, ..., expect}` cases, with one loop per file.

## Files

- Create: `web/public/lib/together/session.js` + `session.d.ts`: the format (types, parse, write).
- Create: `web/public/lib/together/moves.js` + `moves.d.ts`: start, join, leave and act, as session → session.
- Create: `web/public/lib/together/follow.js` + `follow.d.ts`: the expected position, liveness, `follow`, `decide` and `changeOf`.
- Create: `web/test/fixtures/together/{format,expected,liveness,follow,decide,moves,change}.json`
- Create: `web/test/together-fixtures.test.ts`

## Interfaces produced (later phases use these exact names)

```ts
// session.d.ts
export const TOGETHER_MARKER = "#mlib-together v=1";
export interface Member { screen: string; label: string }
export interface Session { session: string; set: string; playing: boolean; pos: number; rate: number; at: number; by: string; members: Member[]; ended: boolean }
export function parseSession(message: string): Session | null;
export function writeSession(s: Session): string;
// moves.d.ts
export interface Playback { set: string; playing: boolean; pos: number; rate: number }
export function started(id: string, me: Member, own: Playback, now: number): Session;
export function joined(s: Session, me: Member): Session;
export function left(s: Session, screen: string): Session;            // ended = no members left
export function acted(s: Session, change: Partial<Playback>, me: string, now: number): Session;
export function finished(s: Session, screen: string): Session;      // the end of the last title: over for everyone
// follow.d.ts
export const DRIFT_TOLERANCE_SECS = 0.5;
export interface ScreenState { set: string | null; playing: boolean; pos: number; rate: number }
export type Step = { kind: "open"; set: string; pos: number; playing: boolean; rate: number } | { kind: "rate"; rate: number } | { kind: "seek"; pos: number } | { kind: "play" } | { kind: "pause" };
export type Change = "played" | "paused" | "sought" | "rate" | "title" | "joined" | "left";
export function expectedPosition(s: Session, now: number): number;
export function isLive(s: Session, durationSecs: number | null, now: number): boolean;
export function follow(current: Session | null, incoming: Session, me: string): Session;
export function decide(own: ScreenState, s: Session, now: number): Step[];
export function changeOf(prev: Session, next: Session): { change: Change; label: string | null } | null;
```

## Key decisions (from the spec, made concrete)

- **The echo rule refines "ignore your own echo".** A screen keeps its current session when its own edit comes back *and its current session is still its own write*. If another screen's edit arrived in between, then its own echo arriving later means its write landed last, so it is the message now and the screen follows it.

  Telegram delivers one channel's updates in pts order, so the last update a screen hears is the message's real state. Without this refinement, two screens acting in the same second would each end up in the other's state.
- `acted` rounds `pos` to milliseconds, which keeps the message short. Liveness (spec §1) uses `pos + elapsed × rate` against `duration + 120 s`.
- `writeSession` is checked as a round trip (`parse(write(x))` equals `x`), not byte for byte. JS and Kotlin print numbers differently (`1` vs `1.0`).

## Tasks

### Task 2.1: Format

- [ ] **Write `web/test/fixtures/together/format.json`.** Each case is `{name, text, expect}`, where `expect` is a `Session` or `null`:

```json
[
  {"name":"a full session parses","text":"#mlib-together v=1\n{\"session\":\"s1\",\"set\":\"film-1\",\"playing\":true,\"pos\":12.5,\"rate\":1,\"at\":1791500000000,\"by\":\"tv-1\",\"members\":[{\"screen\":\"tv-1\",\"label\":\"TV\"}],\"ended\":false}",
   "expect":{"session":"s1","set":"film-1","playing":true,"pos":12.5,"rate":1,"at":1791500000000,"by":"tv-1","members":[{"screen":"tv-1","label":"TV"}],"ended":false}},
  {"name":"unknown keys are dropped","text":"#mlib-together v=1\n{\"session\":\"s1\",\"set\":\"film-1\",\"playing\":false,\"pos\":0,\"rate\":1.5,\"at\":1,\"by\":\"tv-1\",\"members\":[],\"ended\":true,\"later\":7}",
   "expect":{"session":"s1","set":"film-1","playing":false,"pos":0,"rate":1.5,"at":1,"by":"tv-1","members":[],"ended":true}},
  {"name":"another version is not a session","text":"#mlib-together v=2\n{}","expect":null},
  {"name":"a marker with text after it is not a session","text":"#mlib-together v=1 x\n{}","expect":null},
  {"name":"no JSON line","text":"#mlib-together v=1","expect":null},
  {"name":"broken JSON","text":"#mlib-together v=1\n{\"session\":","expect":null},
  {"name":"a missing field","text":"#mlib-together v=1\n{\"session\":\"s1\",\"set\":\"film-1\",\"playing\":true,\"pos\":1,\"rate\":1,\"at\":1,\"by\":\"tv-1\",\"members\":[]}","expect":null},
  {"name":"a negative position","text":"#mlib-together v=1\n{\"session\":\"s1\",\"set\":\"f\",\"playing\":true,\"pos\":-1,\"rate\":1,\"at\":1,\"by\":\"a\",\"members\":[],\"ended\":false}","expect":null},
  {"name":"a zero rate","text":"#mlib-together v=1\n{\"session\":\"s1\",\"set\":\"f\",\"playing\":true,\"pos\":1,\"rate\":0,\"at\":1,\"by\":\"a\",\"members\":[],\"ended\":false}","expect":null},
  {"name":"a member without a label","text":"#mlib-together v=1\n{\"session\":\"s1\",\"set\":\"f\",\"playing\":true,\"pos\":1,\"rate\":1,\"at\":1,\"by\":\"a\",\"members\":[{\"screen\":\"a\"}],\"ended\":false}","expect":null},
  {"name":"an index caption is not a session","text":"#mlib-index v=2\n{}","expect":null}
]
```

- [ ] **Write the failing test** `web/test/together-fixtures.test.ts`. It uses the same loader as `channel-updates-fixtures.test.ts`, with `FIXTURES = join(import.meta.dir, "fixtures", "together")`:

```ts
describe("format fixtures", () => {
  for (const one of load<{ name: string; text: string; expect: Session | null }[]>("format.json")) {
    test(one.name, () => {
      const parsed = parseSession(one.text);
      expect(parsed).toEqual(one.expect);
      if (parsed !== null) expect(parseSession(writeSession(parsed))).toEqual(parsed);
    });
  }
});
```

- [ ] **Run it and see it fail:** `cd web && bun test test/together-fixtures.test.ts`, which fails with "Cannot find module ../public/lib/together/session.js".
- [ ] **Implement `web/public/lib/together/session.js` and `session.d.ts`.** Below is the TypeScript form: the `interface`s and signatures go in the `.d.ts`, and the bodies, without annotations, go in the `.js`.

```ts
/**
 * The watch-together session message: `#mlib-together v=1` on the first line
 * and its JSON on the second. Each session is one unpinned message in the
 * library channel, edited in place, and the last edit wins.
 * `android/core/model/src/main/kotlin/together/` is the Kotlin port, held to
 * this by `web/test/fixtures/together/`.
 */

export const TOGETHER_MARKER = "#mlib-together v=1";

export interface Member {
  screen: string;
  /** The surface kind the others see: "TV", "Phone", "Tablet" or "Browser". */
  label: string;
}

export interface Session {
  session: string;
  /** The set id playing: a film, or one episode or lesson. */
  set: string;
  playing: boolean;
  /** Seconds into `set` at the moment `at`. */
  pos: number;
  rate: number;
  /** The writer's wall clock, epoch milliseconds. */
  at: number;
  /** The screen whose change this is. */
  by: string;
  members: Member[];
  ended: boolean;
}

const named = (value: unknown): value is string => typeof value === "string" && value.length > 0;
const finite = (value: unknown): value is number => typeof value === "number" && Number.isFinite(value);

function memberOf(value: unknown): Member | null {
  if (typeof value !== "object" || value === null) return null;
  const { screen, label } = value as Record<string, unknown>;
  return named(screen) && named(label) ? { screen, label } : null;
}

/** The session a message holds, or `null` for anything that is not exactly one. */
export function parseSession(message: string): Session | null {
  const newline = message.indexOf("\n");
  if (newline < 0 || message.slice(0, newline) !== TOGETHER_MARKER) return null;
  let json: unknown;
  try {
    json = JSON.parse(message.slice(newline + 1));
  } catch {
    return null;
  }
  if (typeof json !== "object" || json === null || Array.isArray(json)) return null;
  const v = json as Record<string, unknown>;
  if (
    !named(v.session) || !named(v.set) || typeof v.playing !== "boolean" ||
    !finite(v.pos) || v.pos < 0 || !finite(v.rate) || v.rate <= 0 || !finite(v.at) ||
    !named(v.by) || !Array.isArray(v.members) || typeof v.ended !== "boolean"
  ) return null;
  const members = v.members.map(memberOf);
  if (members.some((m) => m === null)) return null;
  return {
    session: v.session, set: v.set, playing: v.playing, pos: v.pos, rate: v.rate,
    at: v.at, by: v.by, members: members as Member[], ended: v.ended,
  };
}

export function writeSession(s: Session): string {
  const { session, set, playing, pos, rate, at, by, ended } = s;
  const members = s.members.map(({ screen, label }) => ({ screen, label }));
  return `${TOGETHER_MARKER}\n${JSON.stringify({ session, set, playing, pos, rate, at, by, members, ended })}`;
}
```

- [ ] **Run it and see it pass.** Then commit with `git commit -m "feat(web): watch-together session format"`.

### Task 2.2: Moves (start, join, leave, act)

- [ ] **Write `moves.json`.** Each case is `{name, move, session?, args, expect}`, where `move` is one of `started`, `joined`, `left` or `acted`:

```json
[
  {"name":"starting puts the starter alone in a fresh session","move":"started",
   "args":{"id":"s1","me":{"screen":"tv-1","label":"TV"},"own":{"set":"film-1","playing":true,"pos":61.25,"rate":1},"now":1000},
   "expect":{"session":"s1","set":"film-1","playing":true,"pos":61.25,"rate":1,"at":1000,"by":"tv-1","members":[{"screen":"tv-1","label":"TV"}],"ended":false}},
  {"name":"joining adds the screen and changes no playback field","move":"joined",
   "session":{"session":"s1","set":"film-1","playing":true,"pos":10,"rate":1,"at":1000,"by":"tv-1","members":[{"screen":"tv-1","label":"TV"}],"ended":false},
   "args":{"me":{"screen":"tab-1","label":"Tablet"}},
   "expect":{"session":"s1","set":"film-1","playing":true,"pos":10,"rate":1,"at":1000,"by":"tab-1","members":[{"screen":"tv-1","label":"TV"},{"screen":"tab-1","label":"Tablet"}],"ended":false}},
  {"name":"joining twice keeps one entry","move":"joined",
   "session":{"session":"s1","set":"film-1","playing":true,"pos":10,"rate":1,"at":1000,"by":"tv-1","members":[{"screen":"tab-1","label":"Tablet"}],"ended":false},
   "args":{"me":{"screen":"tab-1","label":"Tablet"}},
   "expect":{"session":"s1","set":"film-1","playing":true,"pos":10,"rate":1,"at":1000,"by":"tab-1","members":[{"screen":"tab-1","label":"Tablet"}],"ended":false}},
  {"name":"leaving removes the screen","move":"left",
   "session":{"session":"s1","set":"film-1","playing":true,"pos":10,"rate":1,"at":1000,"by":"tv-1","members":[{"screen":"tv-1","label":"TV"},{"screen":"tab-1","label":"Tablet"}],"ended":false},
   "args":{"screen":"tab-1"},
   "expect":{"session":"s1","set":"film-1","playing":true,"pos":10,"rate":1,"at":1000,"by":"tab-1","members":[{"screen":"tv-1","label":"TV"}],"ended":false}},
  {"name":"the last screen to leave ends the session","move":"left",
   "session":{"session":"s1","set":"film-1","playing":false,"pos":10,"rate":1,"at":1000,"by":"tv-1","members":[{"screen":"tv-1","label":"TV"}],"ended":false},
   "args":{"screen":"tv-1"},
   "expect":{"session":"s1","set":"film-1","playing":false,"pos":10,"rate":1,"at":1000,"by":"tv-1","members":[],"ended":true}},
  {"name":"a pause stamps the position, the time and the actor","move":"acted",
   "session":{"session":"s1","set":"film-1","playing":true,"pos":10,"rate":1,"at":1000,"by":"tv-1","members":[],"ended":false},
   "args":{"change":{"playing":false,"pos":14.12345},"me":"tab-1","now":5000},
   "expect":{"session":"s1","set":"film-1","playing":false,"pos":14.123,"rate":1,"at":5000,"by":"tab-1","members":[],"ended":false}},
  {"name":"a change without a position keeps the expected one","move":"acted",
   "session":{"session":"s1","set":"film-1","playing":true,"pos":10,"rate":2,"at":1000,"by":"tv-1","members":[],"ended":false},
   "args":{"change":{"rate":1},"me":"tv-1","now":3000},
   "expect":{"session":"s1","set":"film-1","playing":true,"pos":14,"rate":1,"at":3000,"by":"tv-1","members":[],"ended":false}},
  {"name":"the end of the last title ends the session for everyone","move":"finished",
   "session":{"session":"s1","set":"film-1","playing":true,"pos":5400,"rate":1,"at":1000,"by":"tv-1","members":[{"screen":"tv-1","label":"TV"},{"screen":"tab-1","label":"Tablet"}],"ended":false},
   "args":{"screen":"tab-1"},
   "expect":{"session":"s1","set":"film-1","playing":false,"pos":5400,"rate":1,"at":1000,"by":"tab-1","members":[{"screen":"tv-1","label":"TV"},{"screen":"tab-1","label":"Tablet"}],"ended":true}},
  {"name":"the next episode starts at zero, playing","move":"acted",
   "session":{"session":"s1","set":"ep-1","playing":true,"pos":1300,"rate":1,"at":1000,"by":"tv-1","members":[],"ended":false},
   "args":{"change":{"set":"ep-2","pos":0,"playing":true},"me":"tab-1","now":2000},
   "expect":{"session":"s1","set":"ep-2","playing":true,"pos":0,"rate":1,"at":2000,"by":"tab-1","members":[],"ended":false}}
]
```

- [ ] **Add the failing loop** to `together-fixtures.test.ts`. It dispatches on `move` and calls `started(args.id, args.me, args.own, args.now)`, `joined(session, args.me)`, `left(session, args.screen)`, `finished(session, args.screen)` or `acted(session, args.change, args.me, args.now)`, then checks `toEqual(expect)`. Run it and see it fail.
- [ ] **Implement `web/public/lib/together/moves.js` and `moves.d.ts`.** Same split: TS shown, types go in the `.d.ts`.

```ts
/**
 * What each screen writes to the session. Starting, joining and leaving touch
 * only who is in it. Acting is the viewer's own play, pause, seek, speed or
 * title change, stamped with this screen's clock. A change a screen makes
 * because it is following is never one of these.
 */

import { expectedPosition } from "./follow.js";
/** @typedef {import("./session.js").Member} Member */

export interface Playback { set: string; playing: boolean; pos: number; rate: number }

const toMillis = (secs: number) => Math.round(secs * 1000) / 1000;

export function started(id: string, me: Member, own: Playback, now: number): Session {
  return { session: id, ...own, pos: toMillis(own.pos), at: now, by: me.screen, members: [me], ended: false };
}

export function joined(s: Session, me: Member): Session {
  const others = s.members.filter((m) => m.screen !== me.screen);
  return { ...s, by: me.screen, members: s.members.length === others.length ? [...others, me] : s.members };
}

export function left(s: Session, screen: string): Session {
  const members = s.members.filter((m) => m.screen !== screen);
  return { ...s, by: screen, members, ended: members.length === 0 };
}

export function acted(s: Session, change: Partial<Playback>, me: string, now: number): Session {
  const pos = change.pos ?? expectedPosition(s, now);
  return { ...s, ...change, pos: toMillis(pos), at: now, by: me };
}

/** The first screen to reach the end of a film, or of the last episode, ends the session for everyone. */
export function finished(s: Session, screen: string): Session {
  return { ...s, playing: false, by: screen, ended: true };
}
```

- [ ] **Run it, see it pass, and commit:** `git commit -m "feat(web): watch-together session moves"`

### Task 2.3: Following (expected position, liveness, follow, decide, changeOf)

- [ ] **Write the fixtures.** Compare positions with a tolerance of 1e-6.
  - `expected.json`: `{name, session, now, expect}`. Cases:
    - playing 10 s after `pos: 100`, `rate: 1` → 110;
    - rate 1.5 → 115;
    - paused → `pos`, whatever `now` is;
    - `now` before `at` (the writer's clock is ahead) → `pos − elapsed`, clamped at 0.
  - `liveness.json`: `{name, session, duration, now, expect}`. Cases:
    - ended → false;
    - playing, 60 s past the end → true;
    - playing, 121 s past the end → false;
    - playing with an unknown duration (`null`), 5 h 59 min after `at` → true;
    - the same at 6 h → false;
    - paused 2 h 59 min → true;
    - paused 3 h → false.
  - `follow.ts` cases (`follow.json`, `{name, current, incoming, me, expect: "current" | "incoming"}`):
    - another screen's edit → incoming;
    - my own echo while my last write is current → current;
    - **my own echo after another screen's edit replaced my write → incoming**, because mine landed last;
    - my own echo with no current → incoming;
    - my own echo from a different session id → incoming.
  - `decide.json`: `{name, own, session, now, expect: Step[]}`. Cases:
    - a different set → only `open`, at the expected position;
    - the same state within 0.5 s → `[]`;
    - 0.6 s behind → `seek`;
    - playing locally, paused in the session → `pause`;
    - a rate differs and the position is off → `[rate, seek]`, in that order;
    - nothing open yet (`set: null`) → `open`.
  - `change.json`: `{name, prev, next, expect}`. Cases:
    - pause → `{change:"paused", label:"TV"}`;
    - play → `played`;
    - seek by more than 2 s → `sought`;
    - a rate change → `rate`;
    - a set change → `title`;
    - a member added → `joined`, with the joiner's label;
    - a member removed → `left`, with the label taken from `prev`;
    - a keep-up edit with the same state → `null`;
    - a `by` naming an unknown screen → that change with `label: null`.
- [ ] **Add the failing loops**, one `describe` per file, then run them and see them fail.
- [ ] **Implement `web/public/lib/together/follow.js` and `follow.d.ts`.** Same split.

```ts
/**
 * Following a session: where it is now, whether it is still live, which edit
 * a screen follows, and what that screen must do to match it.
 */


/** Further apart than this, a screen seeks. */
export const DRIFT_TOLERANCE_SECS = 0.5;
/** A playing session lasts this long past its title's end, then it is stale. */
const PAST_END_GRACE_SECS = 120;
const UNKNOWN_LENGTH_LIFETIME_MS = 6 * 60 * 60 * 1000;
const PAUSED_LIFETIME_MS = 3 * 60 * 60 * 1000;
/** A jump this big between two edits reads as a seek, not as playing on. */
const SEEK_NOTICE_SECS = 2;

export interface ScreenState { set: string | null; playing: boolean; pos: number; rate: number }

export type Step =
  | { kind: "open"; set: string; pos: number; playing: boolean; rate: number }
  | { kind: "rate"; rate: number }
  | { kind: "seek"; pos: number }
  | { kind: "play" }
  | { kind: "pause" };

export type Change = "played" | "paused" | "sought" | "rate" | "title" | "joined" | "left";

export function expectedPosition(s: Session, now: number): number {
  return s.playing ? Math.max(0, s.pos + ((now - s.at) / 1000) * s.rate) : s.pos;
}

/**
 * Liveness comes from the state itself, with no keep-alive edits. A film nobody
 * pauses sends no edits for hours, and a keep-alive could overwrite a real
 * action sent in the same second.
 */
export function isLive(s: Session, durationSecs: number | null, now: number): boolean {
  if (s.ended) return false;
  if (!s.playing) return now - s.at < PAUSED_LIFETIME_MS;
  if (durationSecs === null) return now - s.at < UNKNOWN_LENGTH_LIFETIME_MS;
  return expectedPosition(s, now) <= durationSecs + PAST_END_GRACE_SECS;
}

/**
 * The session to follow after hearing `incoming`. A screen's own edit coming
 * back is old news while its current session is still that write. If another
 * screen's edit came in between, the echo arriving after it means this
 * screen's write landed last, and is the message now.
 */
export function follow(current: Session | null, incoming: Session, me: string): Session {
  const stillMine = current !== null && current.session === incoming.session && current.by === me;
  return incoming.by === me && stillMine ? current : incoming;
}

export function decide(own: ScreenState, s: Session, now: number): Step[] {
  const pos = expectedPosition(s, now);
  if (own.set !== s.set) return [{ kind: "open", set: s.set, pos, playing: s.playing, rate: s.rate }];
  const steps: Step[] = [];
  if (own.rate !== s.rate) steps.push({ kind: "rate", rate: s.rate });
  if (Math.abs(own.pos - pos) > DRIFT_TOLERANCE_SECS) steps.push({ kind: "seek", pos });
  if (own.playing !== s.playing) steps.push({ kind: s.playing ? "play" : "pause" });
  return steps;
}

/** What another screen did, for the two-second notice; `null` when nothing visible changed. */
export function changeOf(prev: Session, next: Session): { change: Change; label: string | null } | null {
  const label = [...next.members, ...prev.members].find((m) => m.screen === next.by)?.label ?? null;
  const had = new Set(prev.members.map((m) => m.screen));
  const has = new Set(next.members.map((m) => m.screen));
  let change: Change | null = null;
  if (next.set !== prev.set) change = "title";
  else if (next.members.some((m) => !had.has(m.screen))) change = "joined";
  else if (prev.members.some((m) => !has.has(m.screen))) change = "left";
  else if (next.playing !== prev.playing) change = next.playing ? "played" : "paused";
  else if (next.rate !== prev.rate) change = "rate";
  else if (Math.abs(expectedPosition(prev, next.at) - next.pos) > SEEK_NOTICE_SECS) change = "sought";
  return change === null ? null : { change, label };
}
```

- [ ] **Run everything:** `cd web && bun test test/together-fixtures.test.ts test/code-standards.test.ts && bun run typecheck && bun run lint`. All of them should pass (lint covers `public/`).
- [ ] **Commit:** `git commit -m "feat(web): watch-together follow and decide"`

## Todo

- [ ] 2.1 Format
- [ ] 2.2 Moves
- [ ] 2.3 Follow and decide

## Success criteria

- Every fixture passes. The functions are pure: no `Date.now()` and no I/O.
- Each file stays under 200 lines.

## Risks

- **Float compare differences between JS and Kotlin.** The fixtures compare positions within 1e-6, and `acted` rounds to milliseconds.
- **`changeOf` picks one change per edit.** An edit that both seeks and pauses reads as "paused". That is acceptable: the screen still matches the state, and only the wording is simplified.

## Pre-flight rulings (binding; they override the text above where they differ)

- Tasks 2.1–2.3 are one dispatch. `moves.js` needs `expectedPosition` before 2.3: create `follow.js` with `expectedPosition` during 2.2 and fill in the rest in 2.3.
- `moves.json` has a `finished` case: the JS moves test dispatches `started`, `joined`, `left`, `acted` **and `finished`**.
- The `.d.ts` files use `import type` for `Session`/`Member` from `session.d.ts` (don't rely on `skipLibCheck`).
- The probe (phase 01) checked a history read (`getMessages` limit 100), not search.
