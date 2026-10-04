# Phase 02 — Web: rules, PIN, wrong-PIN wait, routes

## Context links

- Contract (authoritative): [shared-contract.md](shared-contract.md) §1 roles, §2 `allowed`, §3 outcomes and order, §4 wait, §5 PIN, §8 HTTP, §10 fixtures
- Spec: `docs/superpowers/specs/2026-09-28-profile-roles-design.md` §1 (not a login), §2 (who can do what), §5 (web server)
- Previous phase (data this one builds on): [phase-01-web-schema-sync-merge.md](phase-01-web-schema-sync-merge.md) — `profiles.ts`, `ProfileRow`, `insertProfile`, `setKids(…, age)`, `kidsFromSix()`
- Bumping procedure every commit step uses: [phase-08 § Bumping](phase-08-verify-docs-version.md)
- Code (HEAD `220fa71e`, plus phase 01): `web/src/state/{routes,store,profiles}.ts`, `web/src/routes.ts`,
  `web/src/server.ts`, `web/src/http/browser-write.ts`, `web/src/settings/admin-gate.ts`
- Tests: `web/test/{state-http,state-write-triggers-sync,http,code-standards}.test.ts`

## Overview

Priority P1. Status: done (pending merge) — see `../261004-1508-open-tasks-sweep/reports/b3-phase-02-web-rules-pin-routes-report.md` for what differs from the steps below. The pure rule (`allowed`, contract §2), the PIN format and
hash (§5), the wrong-PIN wait (§4), the PIN-checked management operations answering in the
amended §3 order — including `create-first`, the bootstrap that gives a player with no
grown-up its first profile and admin — and the §8 HTTP routes — `PATCH /api/profiles/:id` (rename) and
`WatchState.renameProfile` removed, `GET/PUT /api/kids` gaining the age. Two shared fixtures
(`pin-hash.json`, `profile-rules.json`) the core runs in phase 05.

**Ship with phase 03.** `POST` and `DELETE /api/profiles` now require `{ actorId, pin, … }`,
which the current browser picker does not send — its `{ name, kids }` reads as a first profile
with no PIN and is refused `invalid`, and its body-less `DELETE` likewise — so after this phase
alone the picker can neither add nor remove a profile. Commit phase by phase, but push this phase and phase 03
together (plan.md § Dependencies); do not release it alone.

## Key insights (verified)

- **`routes.ts` has no room** — 267/267 (`web/test/code-standards.test.ts:59`). The profile block
  is `routes.ts:94-120` (27 lines: `GET`/`POST /api/profiles`, `DELETE`/`PATCH /api/profiles/:id`,
  rename at `:117-119`); `PROFILES`/`PROFILE` are `:27-28`; the JSON helpers `parse`/`json`/`status`
  are `:255-267`. Moving the profile routes to `profiles-routes.ts` and the helpers to
  `route-json.ts` (both need them) takes `routes.ts` to ≈ 235 after this phase's additions.
- **The wait cannot live in the state router.** `createStateRouter` is rebuilt on every catalog
  swap (`web/src/routes.ts:88-90`, via `server.ts:145-147` and `:216`), so a count held in its
  closure would reset whenever the catalog updates. `WatchState` is built once per process
  (`web/src/index.ts:135`); a field on it is "one per server process" (§4) and one per test
  store. Precedent for an accessor over the same database: `settings()` (`store.ts:680-682`).
- **The server never reads a `DELETE` body** — `server.ts:160-162` skips it for `GET`, `HEAD`
  and `DELETE`. §8's `DELETE /api/profiles/:id { actorId, pin }` needs it read. `server.ts` is
  at its ceiling (269, `code-standards.test.ts:56`), so the edit is line-neutral. A body-less
  `DELETE` (HLS session end, `http.test.ts:437-444`) still works: its body is empty.
- **`refuseUnsafeBrowserWrite` exempts `DELETE` from the JSON type check** (`browser-write.ts:25-28`),
  on the stated ground that it carries no body. The exemption stays safe with a body — no form
  can send `DELETE`, and a cross-origin script needs a preflight this server does not answer;
  the `Origin` check still applies — but the comment must say so rather than something false.
- **Every successful non-GET state response triggers a sync round** (`web/src/routes.ts:97-101`)
  unless `writeWorthSyncing` (`:75-81`) excludes it. `POST …/unlock` changes nothing a document
  says; left in, every PIN entry would cost a channel read. A refused PIN (403) never triggers.
- **Tests that call the removed or reshaped routes**: `state-http.test.ts:232-296` (`POST
  { name }`, `PATCH` rename, `DELETE` with no body) and `state-write-triggers-sync.test.ts:157-162`
  (`PATCH` as "a profile write"). The browser-side tests that mention profile routes
  (`management-controls.test.ts`, `watch-state-async.test.ts`) stub `fetch` and never reach the
  server; phase 03 owns them. `state-http.test.ts:199-229` (`POST { name }` to a player that
  cannot remember → 400) still holds: with no `actorId` it is `create-first`, and a missing
  `newPin` is `invalid`, checked first.
- **A fresh player has no way in without `create-first`** (§3): `create-grown-up` needs an
  admin actor and `claim-admin` an existing grown-up. "No grown-up" counts kids-only players
  too (§12), and a pre-upgrade grown-up without a PIN is a grown-up — that household claims
  an admin instead. `insertProfile`'s `INSERT` already names `admin_claimed_at` (phase 01),
  so the bootstrap is one more `NewProfile` field, not another statement.
- **`timingSafeEqual` precedent** — `admin-gate.ts:54-58` hashes both sides first because
  `timingSafeEqual` throws on a length mismatch. Here both sides are already SHA-256 digests;
  a stored hash of the wrong length is answered `false` before the call.
- **Contract §5 vectors verified** with `sha256sum`: `00112233…eeff` + `1234` →
  `f377124b…0924`; `ffeeddcc…1100` + `0000` → `48618b45…37f8`.
- **`tsconfig.json` has `noUncheckedIndexedAccess`** — regex groups need `!`, as `routes.ts`
  already writes them (`named[1]!`).

## Requirements

Functional
- `validPin` = exactly `/^[0-9]{4}$/`; `hashPin` = lowercase hex SHA-256 of UTF-8 `salt + pin`; salt = 16 random bytes, lowercase hex; comparison with `timingSafeEqual` (§5).
- `allowed(profiles, actorId, action, targetId)` pure, never throws, every row of §2; `ownerOf` per §1.
- `PinWait`: 5 failed comparisons (any profile) start a 60 s wait; during it every PIN-checking call answers `wait` with whole seconds left, rounded up, without comparing; a success or the wait running out resets the count; clock injectable (§4).
- Operations, answering in the amended §3 order — `invalid` (name, new PIN, age only) → `not-found` → structural `not-allowed` (kid actor; claim-admin with an admin present or a kid target; create-first beside a grown-up) → `wait` (only when about to compare) → `no-pin` → `wrong-pin` (a malformed current PIN is compared and counts) → rule `not-allowed` — with its exceptions: create-first (no actor; only while no grown-up exists; the new grown-up is admin with that PIN), create grown-up (admin, with `newPin`), create kid (any grown-up, `kidsAge` 6/12, parent = actor), remove (removing a grown-up also removes every kid whose `parent_id` is it; never the admin), unlock (kid free), claim-admin (once; a PIN-less target takes the given PIN, format-checked), set-pin (own, or admin for anyone; PIN-less self sets a first PIN with nothing to prove), set-kids-age (owner only).
- HTTP exactly §8: profile JSON everywhere, error body `{ reason, retryAfter? }`, 429 + `Retry-After`; `PATCH /api/profiles/:id` gone (405); `GET /api/kids` → `{ kids, fromSix }`; `PUT /api/kids/:setId { age? }`.

Non-functional
- New files < 200 lines; `routes.ts` ≤ 267, `store.ts` ≤ 800, `server.ts` ≤ 269; then the three state ceilings lowered to what they measure.
- PIN hash and salt never in an HTTP response; no PIN in any log.
- No plan/phase references in code, comments, test names or commit messages.

## Architecture

```
browser ──HTTP──► web/src/routes.ts (onWrite unless unlock/progress-tick/preference)
                   └► state/routes.ts ──► profiles-routes.ts: profileRoute(request, state)
                                           │ refuseUnsafeBrowserWrite (Origin, JSON type)
                                           │ parse body (route-json.ts)
                                           ▼
                        state.manage() ─► ProfileManager(db, pins)            (profiles-manage.ts)
                           op(…): invalid? (name/newPin/age)
                             ─► check(): not-found? ─► kid actor → not-allowed
                                 ─► prove(): no-pin? ─► wait? ─► compare → wrong-pin (counts)
                                              └ PinWait (profiles-wait.ts, one per WatchState)
                                              └ pinMatches (profiles-pin.ts)
                                 ─► allowed(roleViews…) (profiles-rules.ts) ─► not-allowed?
                           createFirst / claimAdmin: their own structural not-allowed before any PIN
                           write: insertProfile / DELETE (+ kids by parent_id) / writePin / UPDATE kids_age
                        answer(): 204 | 201 + Profile | CODES[reason] + { reason, retryAfter? } (+ Retry-After)
```

How the amended order lands in code: `invalid` looks only at what would be *stored* — a
name, a new PIN, an age, and claim-admin's `pin` when the target has none (it becomes the
PIN). A current PIN is never `invalid`: `prove` compares whatever arrives, and a malformed one
fails and counts. The wait is checked inside `prove`, after "has it a PIN at all" — a call
with nothing to compare (a kid's unlock, a PIN-less self set-pin, claim-admin on a PIN-less
grown-up, any PIN-less actor) never waits, and a PIN-less actor answers `no-pin` even during a
wait (§3 item 4). The 5th wrong PIN answers `wrong-pin`; the next comparing call answers
`wait`. Every local write of a limit or a PIN is stamped `max(now, stored + 1)` (§7).

**Backwards compatibility.** `GET /api/profiles` is a superset of today's. `POST`/`DELETE
/api/profiles` change shape and `PATCH` goes — the only client is the player's own page, which
ships in the same package and changes in phase 03 (hence "ship together"); Android does not use
these routes. `GET /api/kids` gains `fromSix` beside `kids`; `PUT /api/kids/:setId {}` still
means "mark", now explicitly from 12.

## Interfaces

**Consumes** (phase 01): `profiles.ts` — `Profile`, `ProfileRow`, `NewProfile`, `cleanName`,
`profileRows`, `insertProfile`; `WatchState.profiles()`, `kids()`, `kidsFromSix()`,
`setKids(setId, marked, age?)`, `has()`; schema v11 columns. Contract §2–§5, §8, §10.

**Produces** — phase 03 consumes **only the HTTP of contract §8**. Phase 05 ports the rule
and PIN against `pin-hash.json` and `profile-rules.json`, and mirrors the order decisions above.

```ts
// web/src/state/profiles-pin.ts
export function validPin(pin: unknown): pin is string;
export function hashPin(salt: string, pin: string): string;
export function newPin(pin: string): { hash: string; salt: string };
export function pinMatches(hash: string, salt: string, pin: string): boolean;
// web/src/state/profiles-wait.ts
export const MAX_WRONG_PINS = 5;
export const WAIT_MS = 60_000;
export class PinWait { constructor(now?: () => number); secondsLeft(): number; failed(): void; succeeded(): void }
// web/src/state/profiles-rules.ts
export interface RoleView { id: string; kids: boolean; admin: boolean; parentId: string | null }
export type Action = "create-grown-up" | "create-kid" | "remove" | "set-pin" | "set-kids-age";
export function ownerOf(profiles: RoleView[], kid: RoleView): string | null;
export function allowed(profiles: RoleView[], actorId: string, action: Action, targetId: string): boolean;
// web/src/state/profiles-manage.ts
export type Refusal = "invalid" | "not-found" | "wait" | "no-pin" | "wrong-pin" | "not-allowed";
export interface Refused { reason: Refusal; retryAfter?: number }
export type Outcome = Refused | null;                     // null = done
export class ProfileManager {
  constructor(db: Database | null, wait: PinWait);
  createFirst(name: unknown, next: unknown): Refused | Profile;
  createGrownUp(actorId: string, pin: unknown, name: unknown, next: unknown): Refused | Profile;
  createKid(actorId: string, pin: unknown, name: unknown, kidsAge: unknown): Refused | Profile;
  remove(actorId: string, pin: unknown, id: string): Outcome;
  unlock(id: string, pin: unknown): Outcome;
  claimAdmin(id: string, pin: unknown): Outcome;
  setPin(actorId: string, pin: unknown, id: string, next: unknown): Outcome;
  setKidsAge(actorId: string, pin: unknown, id: string, age: unknown): Outcome;
}
// web/src/state/profiles.ts (extended)
export interface NewProfile { kids?: boolean; kidsAge?: 6 | 12; parentId?: string;
  pin?: { hash: string; salt: string }; admin?: boolean }
export function writePin(db: Database, id: string, pin: string): void;   // stamps max(now, stored + 1)
// web/src/state/store.ts — WatchState
manage(): ProfileManager;                                  // renameProfile() removed
// web/src/state/profiles-routes.ts
export const PROFILE_ID: string;                           // "([A-Za-z0-9-]{1,64})"
export function profileRoute(request: PlayerRequest, state: WatchState): PlayerResponse | null;
// web/src/state/route-json.ts
export function parse(body: string | null | undefined): unknown;
export const json: (body: string, headOnly: boolean, code?: number) => PlayerResponse;
export const status: (status: number) => PlayerResponse;
```

## Related code files

Modify
- `web/src/state/profiles.ts` — `NewProfile` gains `kidsAge`, `parentId`, `pin`, `admin`; `writePin`
- `web/src/state/store.ts` — `pins` field, `manage()`; `renameProfile` removed
- `web/src/state/routes.ts` — profile block → `profileRoute`; helpers → `route-json.ts`; `/api/kids` age
- `web/src/routes.ts` — `writeWorthSyncing` leaves out `…/unlock`
- `web/src/server.ts` — read a `DELETE` body (line-neutral)
- `web/src/http/browser-write.ts` — the `DELETE` exemption's comment
- `web/test/code-standards.test.ts` — lower `store.ts`, `routes.ts`, `sync-record.ts` ceilings, dated
- `web/test/state-http.test.ts` — the `profiles` describe rewritten; Kids age case
- `web/test/state-write-triggers-sync.test.ts` — profile write via claim-admin; unlock does not trigger

Create
- `web/src/state/profiles-pin.ts`, `web/src/state/profiles-wait.ts`, `web/src/state/profiles-rules.ts`,
  `web/src/state/profiles-manage.ts`, `web/src/state/profiles-routes.ts`, `web/src/state/route-json.ts`
- `web/test/state-profiles-pin.test.ts`, `web/test/state-profiles-wait.test.ts`,
  `web/test/state-profiles-rules.test.ts`, `web/test/state-profiles-manage.test.ts`,
  `web/test/shared-profile-fixtures.test.ts`
- `web/test/fixtures/watch-state/pin-hash.json`, `web/test/fixtures/watch-state/profile-rules.json`

Delete: none (`renameProfile` and the `PATCH` branch are removed from existing files).

## Implementation steps

All commands run from `/home/andre/Workspace/mediagram/web` unless shown otherwise. Line
numbers are as of `220fa71e` plus phase 01; match on the quoted text.

### Task 1 — A grown-up's PIN: four digits, salted and hashed

**Files:** create `web/src/state/profiles-pin.ts`, `web/test/state-profiles-pin.test.ts`,
`web/test/shared-profile-fixtures.test.ts`, `web/test/fixtures/watch-state/pin-hash.json`.

- [ ] **Step 1.1: Write the fixture exactly as contract §5 gives it** —
  `web/test/fixtures/watch-state/pin-hash.json`:

```json
[
  { "salt": "00112233445566778899aabbccddeeff", "pin": "1234",
    "hash": "f377124b2c2ffeb096001d94cb0e3df86fbe20ad9981335bc624b960d4d80924" },
  { "salt": "ffeeddccbbaa99887766554433221100", "pin": "0000",
    "hash": "48618b45393cde1c8841b73b2ca684f9b39a2efcbf35d4bf602d7f908b5137f8" }
]
```

- [ ] **Step 1.2: Write its runner** — `web/test/shared-profile-fixtures.test.ts`:

```ts
/**
 * Runs `pin-hash.json` and `profile-rules.json` against the web's PIN hash
 * and its rule for who may manage whom. The Android core runs the same files
 * against its port, so these are what hold the two surfaces to one answer: a
 * PIN set on the television opens the profile on the laptop, and what a
 * parent may do on one it may do on the other.
 */

import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { hashPin, pinMatches } from "../src/state/profiles-pin";

const FIXTURES = join(import.meta.dir, "fixtures", "watch-state");

function load<T>(file: string): T {
  return JSON.parse(readFileSync(join(FIXTURES, file), "utf8")) as T;
}

describe("pin-hash fixtures", () => {
  for (const one of load<{ salt: string; pin: string; hash: string }[]>("pin-hash.json")) {
    test(`salt ${one.salt.slice(0, 8)}… with PIN ${one.pin}`, () => {
      expect(hashPin(one.salt, one.pin)).toBe(one.hash);
      expect(pinMatches(one.hash, one.salt, one.pin)).toBe(true);
    });
  }
});
```

- [ ] **Step 1.3: Write the unit test** — `web/test/state-profiles-pin.test.ts`:

```ts
/** Covers a grown-up's PIN: what counts as one, and how it is kept. */

import { describe, expect, test } from "bun:test";
import { hashPin, newPin, pinMatches, validPin } from "../src/state/profiles-pin";

describe("a PIN", () => {
  test("is exactly four ASCII digits", () => {
    for (const pin of ["0000", "1234", "9999"]) expect(validPin(pin)).toBe(true);
    for (const pin of ["", "123", "12345", "12a4", " 1234", "1234 ", "1234\n", "١٢٣٤", 1234, null, undefined]) {
      expect(validPin(pin)).toBe(false);
    }
  });

  test("is kept as a fresh salt and the hash of salt and PIN", () => {
    const first = newPin("1234");
    const second = newPin("1234");
    expect(first.salt).toMatch(/^[0-9a-f]{32}$/);
    expect(first.hash).toMatch(/^[0-9a-f]{64}$/);
    expect(first.hash).toBe(hashPin(first.salt, "1234"));
    expect(second.salt).not.toBe(first.salt);
  });

  test("matches only the PIN it was made from", () => {
    const { hash, salt } = newPin("2468");
    expect(pinMatches(hash, salt, "2468")).toBe(true);
    expect(pinMatches(hash, salt, "2469")).toBe(false);
  });

  test("a stored hash of the wrong length is a mismatch, not a crash", () => {
    const { salt } = newPin("2468");
    expect(pinMatches("abcd", salt, "2468")).toBe(false);
  });
});
```

- [ ] **Step 1.4: Run — expect failure**

```bash
bun test test/shared-profile-fixtures.test.ts test/state-profiles-pin.test.ts
```

Expected: both files fail to load: `Cannot find module '../src/state/profiles-pin'`.

- [ ] **Step 1.5: Implement** — `web/src/state/profiles-pin.ts`:

```ts
/**
 * A grown-up's PIN: four digits, kept as a salted SHA-256.
 *
 * Not a password hash in any strong sense, and not meant as one: anyone
 * holding the sync document can try all ten thousand. That document lives in
 * the household's own channel, readable only by the account that owns the
 * library, and the people a PIN is for cannot read it — a slow hash would buy
 * nothing against that. The Android core computes the same string for the
 * same salt and PIN (`pin-hash.json`), which is what lets a PIN set on the
 * television open the profile on the laptop.
 */

import { createHash, randomBytes, timingSafeEqual } from "node:crypto";

/** Exactly four ASCII digits; anything else is refused where it arrives. */
export function validPin(pin: unknown): pin is string {
  return typeof pin === "string" && /^[0-9]{4}$/.test(pin);
}

export function hashPin(salt: string, pin: string): string {
  return createHash("sha256").update(salt + pin, "utf8").digest("hex");
}

/** A fresh salt, and the hash of `pin` under it. */
export function newPin(pin: string): { hash: string; salt: string } {
  const salt = randomBytes(16).toString("hex");
  return { hash: hashPin(salt, pin), salt };
}

/**
 * Whether `pin` is the one stored. Every byte of the two digests is compared
 * however early they differ; a stored hash of the wrong length cannot be
 * this PIN's, and says so before `timingSafeEqual`, which would throw.
 */
export function pinMatches(hash: string, salt: string, pin: string): boolean {
  const given = Buffer.from(hashPin(salt, pin), "hex");
  const held = Buffer.from(hash, "hex");
  return given.length === held.length && timingSafeEqual(given, held);
}
```

- [ ] **Step 1.6: Run — expect pass**

```bash
bun test test/shared-profile-fixtures.test.ts test/state-profiles-pin.test.ts && bun run typecheck
```

- [ ] **Step 1.7: Bump (§ Bumping, patch) and commit**

```bash
cd /home/andre/Workspace/mediagram
git add web/src/state/profiles-pin.ts web/test/state-profiles-pin.test.ts web/test/shared-profile-fixtures.test.ts \
  web/test/fixtures/watch-state/pin-hash.json \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(web): a grown-up's PIN is four digits, salted and hashed; release <next>"
```

### Task 2 — Five wrong PINs make the player wait a minute

**Files:** create `web/src/state/profiles-wait.ts`, `web/test/state-profiles-wait.test.ts`.

- [ ] **Step 2.1: Write the failing test** — `web/test/state-profiles-wait.test.ts`:

```ts
/** Covers the wait after wrong PINs, on a clock the test moves. */

import { describe, expect, test } from "bun:test";
import { MAX_WRONG_PINS, PinWait, WAIT_MS } from "../src/state/profiles-wait";

function clock() {
  let now = 1_000_000;
  return { now: () => now, pass: (ms: number) => { now += ms; } };
}

describe("the wrong-PIN wait", () => {
  test("four wrong PINs cost nothing; the fifth starts a minute", () => {
    const wait = new PinWait(clock().now);
    for (let wrong = 1; wrong < MAX_WRONG_PINS; wrong++) wait.failed();
    expect(wait.secondsLeft()).toBe(0);
    wait.failed();
    expect(wait.secondsLeft()).toBe(WAIT_MS / 1000);
  });

  test("the seconds left round up, so a wait is never said to be over early", () => {
    const time = clock();
    const wait = new PinWait(time.now);
    for (let wrong = 0; wrong < MAX_WRONG_PINS; wrong++) wait.failed();
    time.pass(WAIT_MS - 1);
    expect(wait.secondsLeft()).toBe(1);
  });

  test("when the wait runs out the count starts again from nothing", () => {
    const time = clock();
    const wait = new PinWait(time.now);
    for (let wrong = 0; wrong < MAX_WRONG_PINS; wrong++) wait.failed();
    time.pass(WAIT_MS);
    expect(wait.secondsLeft()).toBe(0);
    for (let wrong = 1; wrong < MAX_WRONG_PINS; wrong++) wait.failed();
    expect(wait.secondsLeft()).toBe(0);
    wait.failed();
    expect(wait.secondsLeft()).toBe(WAIT_MS / 1000);
  });

  test("a right PIN wipes the count", () => {
    const wait = new PinWait(clock().now);
    for (let wrong = 1; wrong < MAX_WRONG_PINS; wrong++) wait.failed();
    wait.succeeded();
    for (let wrong = 1; wrong < MAX_WRONG_PINS; wrong++) wait.failed();
    expect(wait.secondsLeft()).toBe(0);
  });
});
```

- [ ] **Step 2.2: Run — expect failure**

```bash
bun test test/state-profiles-wait.test.ts
```

Expected: `Cannot find module '../src/state/profiles-wait'`.

- [ ] **Step 2.3: Implement** — `web/src/state/profiles-wait.ts`:

```ts
/**
 * The wait after wrong PINs. Five in a row — for whichever profiles — and no
 * PIN is compared for a minute; a right PIN wipes the count, and so does the
 * wait running out.
 *
 * Four digits are ten thousand guesses; at five a minute that is more than a
 * day of a child pressing buttons. In memory only: a restart forgets the
 * count, which costs a guesser a restart and is not worth a table. One per
 * `WatchState`, which is one per server process — so a wrong guess on the
 * laptop makes the television wait too. That is the simpler rule, and a
 * minute is short.
 */

export const MAX_WRONG_PINS = 5;
export const WAIT_MS = 60_000;

export class PinWait {
  private wrong = 0;
  private until = 0;

  /** `now` is the clock, so a test can move it instead of waiting. */
  constructor(private readonly now: () => number = () => Date.now()) {}

  /** Whole seconds until a PIN is compared again, rounded up; 0 is now. */
  secondsLeft(): number {
    const left = this.until - this.now();
    if (left > 0) return Math.ceil(left / 1000);
    if (this.until !== 0) {
      // The wait has run out: the count starts again from nothing.
      this.until = 0;
      this.wrong = 0;
    }
    return 0;
  }

  failed(): void {
    this.secondsLeft();
    this.wrong += 1;
    if (this.wrong >= MAX_WRONG_PINS) this.until = this.now() + WAIT_MS;
  }

  succeeded(): void {
    this.wrong = 0;
  }
}
```

- [ ] **Step 2.4: Run — expect pass**

```bash
bun test test/state-profiles-wait.test.ts && bun run typecheck
```

- [ ] **Step 2.5: Bump (§ Bumping, patch) and commit**

```bash
cd /home/andre/Workspace/mediagram
git add web/src/state/profiles-wait.ts web/test/state-profiles-wait.test.ts \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(web): five wrong PINs make the player wait a minute; release <next>"
```

### Task 3 — One rule for who may manage whom

**Files:** create `web/src/state/profiles-rules.ts`, `web/test/state-profiles-rules.test.ts`,
`web/test/fixtures/watch-state/profile-rules.json`; modify `web/test/shared-profile-fixtures.test.ts`.

`create-first` is deliberately not a case here: §2 has no row for it and its actor is
nobody, so `allowed` would say `false` by its own first line. Its one condition — no grown-up
on this player — is structural (§3 step 3) and lives in `ProfileManager.createFirst`, covered by
the outcome tests in Tasks 4–6.

- [ ] **Step 3.1: Write the fixture** (every row of contract §2, allowed and refused; the
  generator is not committed, its output is):

```bash
cd /home/andre/Workspace/mediagram/web && bun run - <<'EOF'
const grownUp = (id: string, admin = false) => ({ id, kids: false, admin, parentId: null });
const kid = (id: string, parentId: string | null) => ({ id, kids: true, admin: false, parentId });
// The admin, two parents, and a kid with every kind of parent: a grown-up's,
// the admin's own, none, one not here, and one that is itself a kid.
const household = [
  grownUp("admin", true), grownUp("maja"), grownUp("ben"),
  kid("mia", "maja"), kid("leo", "admin"), kid("tv-kids", null), kid("lost", "gone"), kid("odd", "mia"),
];
const noAdmin = [grownUp("maja"), kid("orphan", null)];
const rows: [string, string, string, string, boolean, object[]?][] = [
  ["the admin may create a grown-up", "admin", "create-grown-up", "", true],
  ["a grown-up who is not the admin may not create a grown-up", "maja", "create-grown-up", "", false],
  ["a kid may not create a grown-up", "mia", "create-grown-up", "", false],
  ["somebody not here may create nobody", "nobody", "create-kid", "", false],
  ["a grown-up may create a kid", "maja", "create-kid", "", true],
  ["the admin may create a kid", "admin", "create-kid", "", true],
  ["a kid may not create a kid", "mia", "create-kid", "", false],
  ["the admin may remove a grown-up", "admin", "remove", "maja", true],
  ["the admin may not remove itself", "admin", "remove", "admin", false],
  ["a grown-up may not remove the admin", "maja", "remove", "admin", false],
  ["a grown-up may not remove another grown-up", "maja", "remove", "ben", false],
  ["a grown-up may not remove itself", "maja", "remove", "maja", false],
  ["a parent may remove its own kid", "maja", "remove", "mia", true],
  ["the admin may not remove another parent's kid", "admin", "remove", "mia", false],
  ["a parent may not remove the admin's kid", "maja", "remove", "leo", false],
  ["the admin may remove its own kid", "admin", "remove", "leo", true],
  ["a kid with no parent is the admin's to remove", "admin", "remove", "tv-kids", true],
  ["a kid with no parent is not another grown-up's to remove", "maja", "remove", "tv-kids", false],
  ["a kid whose parent is not here is the admin's", "admin", "remove", "lost", true],
  ["a kid whose parent is a kid is the admin's", "admin", "remove", "odd", true],
  ["a kid may not remove anyone", "mia", "remove", "leo", false],
  ["somebody not here cannot be removed", "admin", "remove", "nobody", false],
  ["with no admin, a kid with no parent is nobody's to remove", "maja", "remove", "orphan", false, noAdmin],
  ["a grown-up may set its own PIN", "maja", "set-pin", "maja", true],
  ["the admin may set a grown-up's PIN", "admin", "set-pin", "maja", true],
  ["the admin may set its own PIN", "admin", "set-pin", "admin", true],
  ["a grown-up may not set the admin's PIN", "maja", "set-pin", "admin", false],
  ["a grown-up may not set another grown-up's PIN", "maja", "set-pin", "ben", false],
  ["a kid has no PIN to set, even by the admin", "admin", "set-pin", "mia", false],
  ["a kid may not set a PIN", "mia", "set-pin", "mia", false],
  ["a parent may set its own kid's limit", "maja", "set-kids-age", "mia", true],
  ["the admin may not set another parent's kid's limit", "admin", "set-kids-age", "mia", false],
  ["the admin may set its own kid's limit", "admin", "set-kids-age", "leo", true],
  ["the admin sets the limit of a kid with no parent", "admin", "set-kids-age", "tv-kids", true],
  ["a grown-up may not set the limit of a kid with no parent", "maja", "set-kids-age", "tv-kids", false],
  ["a grown-up has no limit to set", "maja", "set-kids-age", "maja", false],
  ["another grown-up may not set a parent's kid's limit", "ben", "set-kids-age", "mia", false],
  ["a kid may not set its own limit", "mia", "set-kids-age", "mia", false],
  ["with no admin, nobody sets the limit of a kid with no parent", "maja", "set-kids-age", "orphan", false, noAdmin],
];
const line = (value: unknown) => JSON.stringify(value);
const text = "[\n" + rows.map(([name, actorId, action, targetId, expect, profiles]) => [
  "  {",
  `    "name": ${line(name)},`,
  `    "profiles": [`,
  (profiles ?? household).map((profile) => `      ${line(profile)}`).join(",\n"),
  `    ],`,
  `    "actorId": ${line(actorId)},`,
  `    "action": ${line(action)},`,
  `    "targetId": ${line(targetId)},`,
  `    "expect": ${expect}`,
  "  }",
].join("\n")).join(",\n") + "\n]\n";
JSON.parse(text);
await Bun.write("test/fixtures/watch-state/profile-rules.json", text);
EOF
```

- [ ] **Step 3.2: Add its runner.** In `web/test/shared-profile-fixtures.test.ts`, add
  `import { allowed, type Action, type RoleView } from "../src/state/profiles-rules";` after the
  `profiles-pin` import, and append:

```ts
describe("profile-rules fixtures", () => {
  interface Case {
    name: string;
    profiles: RoleView[];
    actorId: string;
    action: Action;
    targetId: string;
    expect: boolean;
  }

  for (const one of load<Case[]>("profile-rules.json")) {
    test(one.name, () => {
      expect(allowed(one.profiles, one.actorId, one.action, one.targetId)).toBe(one.expect);
    });
  }
});
```

- [ ] **Step 3.3: The two things the fixture cannot say** — `web/test/state-profiles-rules.test.ts`:

```ts
/** Covers what `profile-rules.json` cannot: the rule never throws, whatever it is asked. */

import { describe, expect, test } from "bun:test";
import { allowed, ownerOf, type Action } from "../src/state/profiles-rules";

describe("the rule", () => {
  test("refuses an action it does not know, rather than throwing", () => {
    const profiles = [{ id: "admin", kids: false, admin: true, parentId: null }];
    expect(allowed(profiles, "admin", "rename" as Action, "admin")).toBe(false);
  });

  test("an empty household allows nothing and owns nothing", () => {
    expect(allowed([], "anyone", "create-kid", "")).toBe(false);
    expect(ownerOf([], { id: "kid", kids: true, admin: false, parentId: null })).toBeNull();
  });
});
```

- [ ] **Step 3.4: Run — expect failure**

```bash
bun test test/shared-profile-fixtures.test.ts test/state-profiles-rules.test.ts
```

Expected: `Cannot find module '../src/state/profiles-rules'`.

- [ ] **Step 3.5: Implement** — `web/src/state/profiles-rules.ts`:

```ts
/**
 * Who may manage whom. Pure, and the one place the rule is written; the
 * Android core runs the same `profile-rules.json` against its port.
 *
 * A kid manages nothing. The admin adds and removes grown-ups and may set any
 * grown-up's PIN, but is never removed — by anyone, itself included. A kid
 * belongs to exactly one grown-up, the one who made it, and only that one
 * removes it or sets its limit; a kid whose parent is not a grown-up here —
 * none recorded, removed, or never met — is the admin's.
 */

/** What the rule needs to know about a profile, and nothing else. */
export interface RoleView {
  id: string;
  kids: boolean;
  admin: boolean;
  parentId: string | null;
}

export type Action = "create-grown-up" | "create-kid" | "remove" | "set-pin" | "set-kids-age";

/** Who manages `kid`: its parent while that is a grown-up here, else the admin, else nobody. */
export function ownerOf(profiles: RoleView[], kid: RoleView): string | null {
  const parent = profiles.find((one) => one.id === kid.parentId && !one.kids);
  return parent?.id ?? profiles.find((one) => one.admin)?.id ?? null;
}

/** Whether `actorId` may do `action` to `targetId`. Never throws. */
export function allowed(profiles: RoleView[], actorId: string, action: Action, targetId: string): boolean {
  const actor = profiles.find((one) => one.id === actorId);
  if (actor === undefined || actor.kids) return false;
  if (action === "create-grown-up") return actor.admin;
  if (action === "create-kid") return true;

  const target = profiles.find((one) => one.id === targetId);
  if (target === undefined) return false;
  switch (action) {
    case "remove":
      if (target.admin) return false;
      return target.kids ? ownerOf(profiles, target) === actor.id : target.id !== actor.id && actor.admin;
    case "set-pin":
      return !target.kids && (target.id === actor.id || actor.admin);
    case "set-kids-age":
      return target.kids && ownerOf(profiles, target) === actor.id;
    default:
      return false;
  }
}
```

- [ ] **Step 3.6: Run — expect pass**

```bash
bun test test/shared-profile-fixtures.test.ts test/state-profiles-rules.test.ts && bun run typecheck
```

Expected: 2 PIN cases, 39 rule cases and 2 unit tests pass.

- [ ] **Step 3.7: Bump (§ Bumping, patch) and commit**

```bash
cd /home/andre/Workspace/mediagram
git add web/src/state/profiles-rules.ts web/test/state-profiles-rules.test.ts web/test/shared-profile-fixtures.test.ts \
  web/test/fixtures/watch-state/profile-rules.json \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(web): one rule for who may manage whom; release <next>"
```

### Task 4 — The first profile, entering one, claiming the admin, and a grown-up's PIN

**Files:** create `web/src/state/profiles-manage.ts`, `web/test/state-profiles-manage.test.ts`;
modify `web/src/state/profiles.ts` (role fields, `writePin`), `web/src/state/store.ts`.

- [ ] **Step 4.1: Write the failing test** — `web/test/state-profiles-manage.test.ts`:

```ts
/**
 * Managing profiles, PIN and rule checked: every operation's answer, and the
 * order the refusals are checked in.
 */

import { afterEach, describe, expect, test } from "bun:test";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import type { Profile } from "../src/state/profiles";
import type { Refused } from "../src/state/profiles-manage";
import { WatchState } from "../src/state/store";

const dirs: string[] = [];
afterEach(() => {
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

function store(): WatchState {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-manage-"));
  dirs.push(dir);
  return new WatchState(join(dir, "state.db"));
}

const profile = (state: WatchState, id: string) => state.profiles().find((one) => one.id === id);

describe("the first profile", () => {
  test("a player with no grown-up makes one, and it runs the household", () => {
    const state = store();
    const first = state.manage().createFirst("André", "1111") as Profile;
    expect(first).toMatchObject({ name: "André", kids: false, admin: true, hasPin: true });
    expect(state.profiles()).toEqual([first]);
    expect(state.manage().unlock(first.id, "1111")).toBeNull();
  });

  test("only while no grown-up exists — kids alone do not count", () => {
    const state = store();
    state.createProfile("TV kids", true);
    expect(state.manage().createFirst("André", "1111")).toMatchObject({ admin: true });
    expect(state.manage().createFirst("Maja", "2222")).toEqual({ reason: "not-allowed" });
  });

  test("a grown-up from before PINs counts: that household claims an admin instead", () => {
    const state = store();
    state.createProfile("Sam");
    expect(state.manage().createFirst("André", "1111")).toEqual({ reason: "not-allowed" });
  });

  test("its name and PIN have to be usable", () => {
    const state = store();
    expect(state.manage().createFirst("  ", "1111")).toEqual({ reason: "invalid" });
    expect(state.manage().createFirst("André", "11")).toEqual({ reason: "invalid" });
    expect(state.profiles()).toEqual([]);
  });
});

describe("claiming the admin", () => {
  test("the first grown-up to claim is the admin, and the PIN given becomes theirs", () => {
    const state = store();
    const andre = state.createProfile("André")!.id;
    expect(state.manage().claimAdmin(andre, "1111")).toBeNull();
    expect(profile(state, andre)).toMatchObject({ admin: true, hasPin: true });
    expect(state.manage().unlock(andre, "1111")).toBeNull();
  });

  test("there is only ever one", () => {
    const state = store();
    const andre = state.createProfile("André")!.id;
    const maja = state.createProfile("Maja")!.id;
    state.manage().claimAdmin(andre, "1111");
    expect(state.manage().claimAdmin(maja, "2222")).toEqual({ reason: "not-allowed" });
    expect(state.manage().claimAdmin(andre, "1111")).toEqual({ reason: "not-allowed" });
    // Refused, so the PIN it offered was not kept either.
    expect(profile(state, maja)).toMatchObject({ admin: false, hasPin: false });
  });

  test("once there is one, a claim is refused before any PIN is compared", () => {
    const state = store();
    const andre = state.createProfile("André")!.id;
    const maja = state.createProfile("Maja")!.id;
    state.manage().setPin(maja, "", maja, "2222");
    state.manage().claimAdmin(andre, "1111");
    expect(state.manage().claimAdmin(maja, "9999")).toEqual({ reason: "not-allowed" });
  });

  test("a grown-up with a PIN must give it, and a kid cannot claim", () => {
    const state = store();
    const maja = state.createProfile("Maja")!.id;
    const mia = state.createProfile("Mia", true)!.id;
    expect(state.manage().setPin(maja, "", maja, "2222")).toBeNull();
    expect(state.manage().claimAdmin(maja, "9999")).toEqual({ reason: "wrong-pin" });
    expect(state.manage().claimAdmin(mia, "1234")).toEqual({ reason: "not-allowed" });
    expect(state.manage().claimAdmin(maja, "2222")).toBeNull();
  });

  test("the PIN a grown-up without one would take has to be four digits", () => {
    const state = store();
    const andre = state.createProfile("André")!.id;
    expect(state.manage().claimAdmin(andre, "12345")).toEqual({ reason: "invalid" });
    expect(state.manage().claimAdmin("nobody", "1111")).toEqual({ reason: "not-found" });
  });
});

describe("entering a profile", () => {
  test("a kid's opens without a PIN", () => {
    const state = store();
    const mia = state.createProfile("Mia", true)!.id;
    expect(state.manage().unlock(mia, undefined)).toBeNull();
  });

  test("a grown-up's opens with its PIN only", () => {
    const state = store();
    const andre = state.createProfile("André")!.id;
    state.manage().claimAdmin(andre, "1111");
    expect(state.manage().unlock(andre, "1112")).toEqual({ reason: "wrong-pin" });
    expect(state.manage().unlock(andre, "1111")).toBeNull();
    // Not four digits is not a malformed request, only a wrong PIN.
    expect(state.manage().unlock(andre, "11a1")).toEqual({ reason: "wrong-pin" });
    expect(state.manage().unlock("nobody", "1111")).toEqual({ reason: "not-found" });
  });

  test("a grown-up from before PINs has none to give yet", () => {
    const state = store();
    const sam = state.createProfile("Sam")!.id;
    expect(state.manage().unlock(sam, "1234")).toEqual({ reason: "no-pin" });
  });
});

describe("a grown-up's PIN", () => {
  test("a grown-up from before PINs sets its first one with nothing to prove", () => {
    const state = store();
    const sam = state.createProfile("Sam")!.id;
    expect(state.manage().setPin(sam, "", sam, "4444")).toBeNull();
    expect(state.manage().unlock(sam, "4444")).toBeNull();
    // From then on, changing it asks for it.
    expect(state.manage().setPin(sam, "", sam, "5555")).toEqual({ reason: "wrong-pin" });
    expect(state.manage().setPin(sam, "4444", sam, "5555")).toBeNull();
    expect(state.manage().unlock(sam, "5555")).toBeNull();
  });

  test("the admin resets another grown-up's; a grown-up cannot reset the admin's", () => {
    const state = store();
    const andre = state.createProfile("André")!.id;
    const maja = state.createProfile("Maja")!.id;
    state.manage().claimAdmin(andre, "1111");
    state.manage().setPin(maja, "", maja, "2222");
    expect(state.manage().setPin(andre, "1111", maja, "3333")).toBeNull();
    expect(state.manage().unlock(maja, "2222")).toEqual({ reason: "wrong-pin" });
    expect(state.manage().unlock(maja, "3333")).toBeNull();
    expect(state.manage().setPin(maja, "3333", andre, "0000")).toEqual({ reason: "not-allowed" });
  });

  test("a kid has none, and a new PIN must be four digits", () => {
    const state = store();
    const andre = state.createProfile("André")!.id;
    const mia = state.createProfile("Mia", true)!.id;
    state.manage().claimAdmin(andre, "1111");
    expect(state.manage().setPin(andre, "1111", mia, "3333")).toEqual({ reason: "not-allowed" });
    expect(state.manage().setPin(andre, "1111", andre, "33")).toEqual({ reason: "invalid" });
  });

  test("a new PIN outdates one another device's clock stamped, however far ahead", () => {
    const state = store();
    const sam = state.createProfile("Sam")!.id;
    const ahead = Date.now() + 60_000;
    // The PIN `pin-hash.json` gives for this salt is 1234.
    state.importMerged({ profiles: [{
      name: "sam", displayName: "Sam", progress: [], watched: [],
      pin: {
        hash: "f377124b2c2ffeb096001d94cb0e3df86fbe20ad9981335bc624b960d4d80924",
        salt: "00112233445566778899aabbccddeeff",
        updatedAt: ahead,
      },
    }] });
    expect(state.manage().setPin(sam, "1234", sam, "5555")).toBeNull();
    expect(state.exportRecord("x").profiles[0]!.pin!.updatedAt).toBeGreaterThan(ahead);
  });

  test("is never told to the page", () => {
    const state = store();
    const andre = state.createProfile("André")!.id;
    state.manage().claimAdmin(andre, "1111");
    expect(JSON.stringify(state.profiles())).not.toMatch(/[0-9a-f]{32}/);
  });
});
```

- [ ] **Step 4.2: Run — expect failure**

```bash
bun test test/state-profiles-manage.test.ts
```

Expected: every test fails with `state.manage is not a function`.

- [ ] **Step 4.3: Let a new profile carry its role.** In `web/src/state/profiles.ts`:

  1. Replace `NewProfile` with:

```ts
/** What a new profile is, beside its name. */
export interface NewProfile {
  kids?: boolean;
  /** A kid's limit, chosen now. Absent is FSK 12, dated 0. */
  kidsAge?: 6 | 12;
  /** The grown-up adding this kid. */
  parentId?: string;
  /** A grown-up's first PIN, already salted and hashed. */
  pin?: { hash: string; salt: string };
  /** The first grown-up on a player, which runs the household from the start. */
  admin?: boolean;
}
```

  2. In `insertProfile`, replace the row's fields from `kidsAge: kids ? 12 : null,` through
     `pinUpdatedAt: 0,` with:

```ts
    kidsAge: kids ? role.kidsAge ?? 12 : null,
    // A limit someone chose is dated now; the default is dated 0, older than
    // any choice, so the first real one — here or synced in — wins.
    kidsAgeUpdatedAt: kids && role.kidsAge !== undefined ? createdAt : 0,
    parentId: kids ? role.parentId ?? null : null,
    adminClaimedAt: !kids && role.admin === true ? createdAt : null,
    pinHash: kids ? null : role.pin?.hash ?? null,
    pinSalt: kids ? null : role.pin?.salt ?? null,
    pinUpdatedAt: !kids && role.pin !== undefined ? createdAt : 0,
```

  3. After `import { normalName } from "./sync-record";` add
     `import { newPin } from "./profiles-pin";`, and append to the file:

```ts

/**
 * A fresh salt and hash for `pin`, dated at least a millisecond past the last
 * change: an imported PIN can carry another device's clock, and this one
 * running behind must not write a PIN that loses to the one it replaced.
 */
export function writePin(db: Database, id: string, pin: string): void {
  const { hash, salt } = newPin(pin);
  db.query("UPDATE profiles SET pin_hash = ?2, pin_salt = ?3, pin_updated_at = MAX(?4, pin_updated_at + 1) WHERE id = ?1")
    .run(id, hash, salt, Date.now());
}
```

- [ ] **Step 4.4: Implement** — `web/src/state/profiles-manage.ts`:

```ts
/**
 * Managing profiles: who may add, remove, enter and re-PIN whom.
 *
 * Every operation answers in one order: input that cannot be used (a name, a
 * new PIN, an age); somebody not there; what no PIN could make allowed — a
 * kid acting, a second admin, a first profile beside grown-ups; then, only
 * when a PIN is about to be compared, the wrong-PIN wait; a grown-up with no
 * PIN yet; a wrong PIN; and last the rule in `profiles-rules.ts`. So the page
 * can say exactly what went wrong, and a guess without the PIN learns nothing
 * about who may do what.
 *
 * Not a login, and not meant as one. A PIN keeps a child from tapping into a
 * grown-up's profile; the state API has no sessions and a kid's catalog is
 * filtered in the browser, so developer tools or `curl` get past it. What is
 * enforced here is that no management action happens unless the PIN and the
 * rule both agree.
 */

import type { Database } from "bun:sqlite";
import { cleanName, insertProfile, profileRows, writePin, type Profile, type ProfileRow } from "./profiles";
import { newPin, pinMatches, validPin } from "./profiles-pin";
import { allowed, type Action, type RoleView } from "./profiles-rules";
import type { PinWait } from "./profiles-wait";

/** Why an operation was refused: the strings an HTTP refusal carries. */
export type Refusal = "invalid" | "not-found" | "wait" | "no-pin" | "wrong-pin" | "not-allowed";

export interface Refused {
  reason: Refusal;
  /** Whole seconds until a PIN is compared again; only with `wait`. */
  retryAfter?: number;
}

/** `null` is done. */
export type Outcome = Refused | null;

const refuse = (reason: Refusal): Refused => ({ reason });

const roleView = (row: ProfileRow): RoleView => ({
  id: row.id,
  kids: row.kids !== 0,
  admin: row.adminClaimedAt !== null,
  parentId: row.parentId,
});

export class ProfileManager {
  constructor(
    private readonly db: Database | null,
    private readonly wait: PinWait,
  ) {}

  /**
   * The first grown-up on a player that has none — the only way a fresh
   * install gets anyone at all. It runs the household from the start; a
   * device that later hears of an older claim hands the role over by the
   * earliest-claim rule the merge already applies.
   */
  createFirst(name: unknown, next: unknown): Refused | Profile {
    if (!validPin(next) || cleanName(name) === null) return refuse("invalid");
    if (profileRows(this.db).some((row) => row.kids === 0)) return refuse("not-allowed");
    return insertProfile(this.db, name, { pin: newPin(next), admin: true }) ?? refuse("invalid");
  }

  /** Entering a profile from the picker. A kid's needs no PIN. */
  unlock(id: string, pin: unknown): Outcome {
    const target = this.row(id);
    if (target === undefined) return refuse("not-found");
    return target.kids !== 0 ? null : this.prove(target, pin);
  }

  /**
   * Makes `id` the household's admin — once, while this player knows of
   * none. A grown-up with a PIN gives it; one without takes the PIN given,
   * which is why that one has to be a PIN at all.
   */
  claimAdmin(id: string, pin: unknown): Outcome {
    const rows = profileRows(this.db);
    const target = rows.find((row) => row.id === id);
    if (target !== undefined && target.kids === 0 && target.pinHash === null && !validPin(pin)) {
      return refuse("invalid");
    }
    if (target === undefined) return refuse("not-found");
    if (target.kids !== 0 || rows.some((row) => row.adminClaimedAt !== null)) return refuse("not-allowed");
    if (target.pinHash !== null) {
      const refused = this.prove(target, pin);
      if (refused) return refused;
    }
    const db = this.db!;
    db.transaction(() => {
      if (target.pinHash === null && validPin(pin)) writePin(db, id, pin);
      db.query("UPDATE profiles SET admin_claimed_at = ?2 WHERE id = ?1").run(id, Date.now());
    })();
    return null;
  }

  /** A grown-up's PIN: its own, or — for the admin — anyone's. */
  setPin(actorId: string, pin: unknown, id: string, next: unknown): Outcome {
    if (!validPin(next)) return refuse("invalid");
    const actor = this.row(actorId);
    // A grown-up from before PINs sets its first one with nothing to prove:
    // until it has one, its profile is as open as every profile was.
    const first = actorId === id && actor !== undefined && actor.kids === 0 && actor.pinHash === null;
    const refused = this.check(actorId, pin, "set-pin", id, first);
    if (refused) return refused;
    writePin(this.db!, id, next);
    return null;
  }

  private row(id: string): ProfileRow | undefined {
    return profileRows(this.db).find((row) => row.id === id);
  }

  /**
   * Somebody there, a grown-up acting, its PIN unless `unproven`, then the
   * rule. A player that cannot remember has no rows, so nothing gets past.
   */
  private check(actorId: string, pin: unknown, action: Action, targetId: string | null, unproven = false): Outcome {
    const rows = profileRows(this.db);
    const actor = rows.find((row) => row.id === actorId);
    if (actor === undefined || (targetId !== null && !rows.some((row) => row.id === targetId))) {
      return refuse("not-found");
    }
    // A kid manages nothing, and has no PIN to prove otherwise with.
    if (actor.kids !== 0) return refuse("not-allowed");
    if (!unproven) {
      const refused = this.prove(actor, pin);
      if (refused) return refused;
    }
    return allowed(rows.map(roleView), actorId, action, targetId ?? "") ? null : refuse("not-allowed");
  }

  /**
   * A grown-up's PIN: none yet; or — only now that one is about to be
   * compared — the wait, then the comparison and the count it keeps. A PIN
   * that is not four digits is compared like any other, and is wrong.
   */
  private prove(row: ProfileRow, pin: unknown): Outcome {
    if (row.pinHash === null || row.pinSalt === null) return refuse("no-pin");
    const left = this.wait.secondsLeft();
    if (left > 0) return { reason: "wait", retryAfter: left };
    if (!validPin(pin) || !pinMatches(row.pinHash, row.pinSalt, pin)) {
      this.wait.failed();
      return refuse("wrong-pin");
    }
    this.wait.succeeded();
    return null;
  }
}
```

- [ ] **Step 4.5: Give `WatchState` its count and the accessor.** In `web/src/state/store.ts`:

  1. After the `./profiles` import block add:

```ts
import { ProfileManager } from "./profiles-manage";
import { PinWait } from "./profiles-wait";
```

  2. After `private readonly db: Database | null;` (`:105`) add:

```ts
  /** One wrong-PIN count per store, which is one per server process. */
  private readonly pins = new PinWait();
```

  3. After `settings()` (the method returning `new Settings(this.db)`) add:

```ts

  /** Adding, removing and entering profiles, PIN and rule checked. */
  manage(): ProfileManager {
    return new ProfileManager(this.db, this.pins);
  }
```

- [ ] **Step 4.6: Run — expect pass**

```bash
bun test test/state-profiles-manage.test.ts test/state-roles-exchange.test.ts test/state-store.test.ts && \
  bun run typecheck && wc -l src/state/profiles-manage.ts src/state/profiles.ts src/state/store.ts
```

Expected: all pass; `profiles-manage.ts` 146, `profiles.ts` 186, `store.ts` 784.

- [ ] **Step 4.7: Bump (§ Bumping, patch) and commit**

```bash
cd /home/andre/Workspace/mediagram
git add web/src/state/profiles-manage.ts web/src/state/profiles.ts web/src/state/store.ts \
  web/test/state-profiles-manage.test.ts \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(web): a first profile runs the household, and a grown-up's opens with its PIN; release <next>"
```

### Task 5 — Adding, removing and limiting, by the rule

**Files:** modify `web/src/state/profiles-manage.ts`, `web/test/state-profiles-manage.test.ts`.

- [ ] **Step 5.1: Write the failing test.** Append to `web/test/state-profiles-manage.test.ts`:

```ts
/** André the admin (PIN 1111), Maja a parent (2222), her kid Mia (12), André's kid Leo (6). */
function household() {
  const state = store();
  const manage = state.manage();
  const andre = made(manage.createFirst("André", "1111"));
  const maja = made(manage.createGrownUp(andre, "1111", "Maja", "2222"));
  const mia = made(manage.createKid(maja, "2222", "Mia", 12));
  const leo = made(manage.createKid(andre, "1111", "Leo", 6));
  return { state, manage, andre, maja, mia, leo };
}

function made(result: Refused | Profile): string {
  if ("reason" in result) throw new Error(`refused: ${result.reason}`);
  return result.id;
}

describe("adding to the household", () => {
  test("the admin adds a grown-up with a first PIN, and a grown-up adds its own kid", () => {
    const { state, manage, andre, maja, mia, leo } = household();
    expect(profile(state, maja)).toMatchObject({ name: "Maja", kids: false, hasPin: true, admin: false });
    expect(manage.unlock(maja, "2222")).toBeNull();
    expect(profile(state, mia)).toMatchObject({ kids: true, kidsAge: 12, parentId: maja, hasPin: false });
    expect(profile(state, leo)).toMatchObject({ kids: true, kidsAge: 6, parentId: andre });
  });

  test("a chosen limit is dated, so a default synced in later cannot undo it", () => {
    const { state } = household();
    const leo = state.exportRecord("laptop").profiles.find((one) => one.name === "Leo")!;
    expect(leo.kidsAge!.age).toBe(6);
    expect(leo.kidsAge!.updatedAt).toBeGreaterThan(0);
    expect(leo.parent).toBe("André");
  });

  test("only the admin adds a grown-up", () => {
    const { manage, maja } = household();
    expect(manage.createGrownUp(maja, "2222", "Ben", "3333")).toEqual({ reason: "not-allowed" });
  });

  test("a kid adds nobody", () => {
    const { manage, mia } = household();
    expect(manage.createKid(mia, "0000", "Zoe", 6)).toEqual({ reason: "not-allowed" });
  });

  test("a name, a new PIN and a limit have to be usable, before anything else is asked", () => {
    const { manage, andre } = household();
    expect(manage.createGrownUp(andre, "1111", "   ", "3333")).toEqual({ reason: "invalid" });
    expect(manage.createGrownUp(andre, "1111", "Ben", "33")).toEqual({ reason: "invalid" });
    expect(manage.createGrownUp(andre, "1111", "Ben", undefined)).toEqual({ reason: "invalid" });
    expect(manage.createKid(andre, "1111", "Zoe", 9)).toEqual({ reason: "invalid" });
    expect(manage.createKid(andre, "1111", "Zoe", "6")).toEqual({ reason: "invalid" });
    expect(manage.createKid("nobody", "1111", "Zoe", 9)).toEqual({ reason: "invalid" });
  });
});

describe("removing", () => {
  test("the admin removes a grown-up, and its kids go with it", () => {
    const { state, manage, andre, maja, mia, leo } = household();
    expect(manage.remove(andre, "1111", maja)).toBeNull();
    const left = state.profiles().map((one) => one.id);
    expect(left).toContain(andre);
    expect(left).toContain(leo);
    expect(left).not.toContain(maja);
    expect(left).not.toContain(mia);
  });

  test("the admin cannot be removed, by anyone", () => {
    const { manage, andre, maja } = household();
    expect(manage.remove(andre, "1111", andre)).toEqual({ reason: "not-allowed" });
    expect(manage.remove(maja, "2222", andre)).toEqual({ reason: "not-allowed" });
  });

  test("a parent removes its own kid, and nobody else's", () => {
    const { state, manage, andre, maja, mia, leo } = household();
    expect(manage.remove(andre, "1111", mia)).toEqual({ reason: "not-allowed" });
    expect(manage.remove(maja, "2222", leo)).toEqual({ reason: "not-allowed" });
    expect(manage.remove(maja, "2222", mia)).toBeNull();
    expect(profile(state, mia)).toBeUndefined();
  });

  test("a kid from before parents is the admin's", () => {
    const { state, manage, andre, maja } = household();
    const tvKids = state.createProfile("TV kids", true)!.id;
    expect(manage.remove(maja, "2222", tvKids)).toEqual({ reason: "not-allowed" });
    expect(manage.remove(andre, "1111", tvKids)).toBeNull();
  });

  test("somebody not there is not found", () => {
    const { manage, andre } = household();
    expect(manage.remove(andre, "1111", "nobody")).toEqual({ reason: "not-found" });
    expect(manage.remove("nobody", "1111", andre)).toEqual({ reason: "not-found" });
  });
});

describe("a kid's limit", () => {
  test("is its parent's to set, and nobody else's", () => {
    const { state, manage, andre, maja, mia } = household();
    expect(manage.setKidsAge(maja, "2222", mia, 6)).toBeNull();
    expect(profile(state, mia)!.kidsAge).toBe(6);
    expect(manage.setKidsAge(andre, "1111", mia, 12)).toEqual({ reason: "not-allowed" });
    expect(manage.setKidsAge(maja, "2222", mia, 9)).toEqual({ reason: "invalid" });
    expect(manage.setKidsAge(maja, "2222", maja, 6)).toEqual({ reason: "not-allowed" });
  });

  test("a change outdates one another device's clock stamped, however far ahead", () => {
    const { state, manage, maja, mia } = household();
    const ahead = Date.now() + 60_000;
    state.importMerged({ profiles: [{
      name: "mia", displayName: "Mia", kids: true, kidsAge: { age: 12, updatedAt: ahead }, progress: [], watched: [],
    }] });
    expect(manage.setKidsAge(maja, "2222", mia, 6)).toBeNull();
    const limit = state.exportRecord("x").profiles.find((one) => one.name === "Mia")!.kidsAge!;
    expect(limit.age).toBe(6);
    expect(limit.updatedAt).toBeGreaterThan(ahead);
  });
});

describe("wrong PINs", () => {
  test("five in a row, on any profiles, and even the right PIN waits", () => {
    const { manage, andre, maja, leo } = household();
    for (const [who, pin] of [[maja, "0000"], [andre, "0000"], [maja, "0001"], [andre, "0001"], [maja, "0002"]] as const) {
      expect(manage.unlock(who, pin)).toEqual({ reason: "wrong-pin" });
    }
    const waiting = manage.unlock(andre, "1111") as Refused;
    expect(waiting.reason).toBe("wait");
    expect(waiting.retryAfter).toBeGreaterThan(0);
    expect(waiting.retryAfter).toBeLessThanOrEqual(60);
    // Every call that compares a PIN waits, not only entering.
    expect(manage.setKidsAge(andre, "1111", leo, 12)).toMatchObject({ reason: "wait" });
  });

  test("a current PIN that is not four digits is simply wrong, and counts", () => {
    const { manage, andre } = household();
    for (const pin of ["12", "abcd", "", "11111"]) expect(manage.unlock(andre, pin)).toEqual({ reason: "wrong-pin" });
    expect(manage.unlock(andre, undefined)).toEqual({ reason: "wrong-pin" });
    expect(manage.unlock(andre, "1111")).toMatchObject({ reason: "wait" });
  });

  test("bad input, somebody not there, and what no PIN could allow are said before the wait", () => {
    const { manage, andre, maja, mia } = household();
    for (let wrong = 0; wrong < 5; wrong++) manage.unlock(maja, "0000");
    expect(manage.setKidsAge(andre, "1111", mia, 9)).toEqual({ reason: "invalid" });
    expect(manage.remove("nobody", "1111", mia)).toEqual({ reason: "not-found" });
    expect(manage.createKid(mia, "0000", "Zoe", 6)).toEqual({ reason: "not-allowed" });
    expect(manage.claimAdmin(maja, "2222")).toEqual({ reason: "not-allowed" });
    expect(manage.createFirst("Zoe", "3333")).toEqual({ reason: "not-allowed" });
    // The rule comes after the PIN, so this one waits.
    expect(manage.remove(maja, "2222", andre)).toMatchObject({ reason: "wait" });
  });

  test("only a call about to compare a PIN waits", () => {
    const { state, manage, maja, mia } = household();
    const sam = state.createProfile("Sam")!.id;
    for (let wrong = 0; wrong < 5; wrong++) manage.unlock(maja, "0000");
    expect(manage.unlock(mia, undefined)).toBeNull();
    expect(manage.unlock(sam, "1234")).toEqual({ reason: "no-pin" });
    expect(manage.setPin(sam, "", sam, "4444")).toBeNull();
  });

  test("a right PIN wipes the count", () => {
    const { manage, andre } = household();
    for (let wrong = 0; wrong < 4; wrong++) manage.unlock(andre, "0000");
    expect(manage.unlock(andre, "1111")).toBeNull();
    for (let wrong = 0; wrong < 4; wrong++) manage.unlock(andre, "0000");
    expect(manage.unlock(andre, "1111")).toBeNull();
  });

  test("a kid proves nothing, so it adds nothing to the count", () => {
    const { manage, andre, mia } = household();
    for (let tries = 0; tries < 5; tries++) {
      expect(manage.createKid(mia, "0000", "Zoe", 6)).toEqual({ reason: "not-allowed" });
    }
    expect(manage.unlock(andre, "1111")).toBeNull();
  });

  test("a wrong PIN is said before what the rule would have said", () => {
    const { manage, andre, maja } = household();
    expect(manage.remove(maja, "0000", andre)).toEqual({ reason: "wrong-pin" });
  });
});
```

- [ ] **Step 5.2: Run — expect failure**

```bash
bun test test/state-profiles-manage.test.ts
```

Expected: every new test fails in `household()` with `manage.createGrownUp is not a function`;
Task 4's tests still pass.

- [ ] **Step 5.3: The four household operations.** In `web/src/state/profiles-manage.ts`:

  1. After `const refuse = …;` add:

```ts
const validAge = (age: unknown): age is 6 | 12 => age === 6 || age === 12;
```

  2. Between `createFirst` and `unlock`, insert:

```ts
  /** A grown-up, with its first PIN. The admin's to add. */
  createGrownUp(actorId: string, pin: unknown, name: unknown, next: unknown): Refused | Profile {
    if (!validPin(next) || cleanName(name) === null) return refuse("invalid");
    const refused = this.check(actorId, pin, "create-grown-up", null);
    return refused ?? insertProfile(this.db, name, { pin: newPin(next) }) ?? refuse("invalid");
  }

  /** A kid, belonging to whichever grown-up adds it, with its own limit. */
  createKid(actorId: string, pin: unknown, name: unknown, kidsAge: unknown): Refused | Profile {
    if (!validAge(kidsAge) || cleanName(name) === null) return refuse("invalid");
    const refused = this.check(actorId, pin, "create-kid", null);
    return refused ?? insertProfile(this.db, name, { kids: true, kidsAge, parentId: actorId }) ?? refuse("invalid");
  }

  /** Removing a grown-up takes its kids with it. */
  remove(actorId: string, pin: unknown, id: string): Outcome {
    const refused = this.check(actorId, pin, "remove", id);
    if (refused) return refused;
    const db = this.db!;
    // By hand, not by a foreign key: `parent_id` deliberately is not one, so
    // a kid whose parent is gone here but known elsewhere stays readable.
    db.transaction(() => {
      db.query("DELETE FROM profiles WHERE parent_id = ?1").run(id);
      db.query("DELETE FROM profiles WHERE id = ?1").run(id);
    })();
    return null;
  }

  /** A kid's limit, set by the grown-up it belongs to. */
  setKidsAge(actorId: string, pin: unknown, id: string, age: unknown): Outcome {
    if (!validAge(age)) return refuse("invalid");
    const refused = this.check(actorId, pin, "set-kids-age", id);
    if (refused) return refused;
    // Dated past the last change, for the reason `writePin` in `profiles.ts` gives.
    this.db!
      .query("UPDATE profiles SET kids_age = ?2, kids_age_updated_at = MAX(?3, kids_age_updated_at + 1) WHERE id = ?1")
      .run(id, age, Date.now());
    return null;
  }

```

- [ ] **Step 5.4: Run — expect pass**

```bash
bun test test/state-profiles-manage.test.ts test/state-roles-exchange.test.ts test/state-store.test.ts && \
  bun run typecheck && wc -l src/state/profiles-manage.ts
```

Expected: all pass; `profiles-manage.ts` 189, `profiles.ts` 186 (both < 200).

- [ ] **Step 5.5: Bump (§ Bumping, patch) and commit**

```bash
cd /home/andre/Workspace/mediagram
git add web/src/state/profiles-manage.ts web/test/state-profiles-manage.test.ts \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(web): grown-ups and kids are added, removed and limited by the rule; release <next>"
```

### Task 6 — The profile routes ask who is asking; rename goes

**Files:** create `web/src/state/route-json.ts`, `web/src/state/profiles-routes.ts`; modify
`web/src/state/routes.ts`, `web/src/state/store.ts`, `web/src/server.ts`,
`web/src/http/browser-write.ts`, `web/src/routes.ts`, `web/test/state-http.test.ts`,
`web/test/state-write-triggers-sync.test.ts`.

- [ ] **Step 6.1: Write the failing test.** In `web/test/state-http.test.ts`, replace the whole
  `describe("profiles", () => { … });` block (`:232-296`, from `describe("profiles"` to the
  `});` before `describe("the editor's choice"`) with:

```ts
describe("profiles", () => {
  const read = (response: { body: Uint8Array }) => JSON.parse(new TextDecoder().decode(response.body));
  const listed = async () =>
    read(await rawRequest(server.port, "/api/profiles")).profiles as Array<Record<string, unknown>>;
  const idOf = async (name: string) => (await listed()).find((profile) => profile.name === name)!.id as string;

  test("are listed with their role, and never a PIN", async () => {
    expect((await listed()).find((profile) => profile.id === me)).toEqual({
      id: me, name: "André", createdAt: expect.any(Number),
      kids: false, kidsAge: null, parentId: null, admin: false, hasPin: false,
    });
  });

  test("the first admin is claimed once, and the PIN given becomes theirs", async () => {
    expect((await send(`/api/profiles/${me}/claim-admin`, "POST", { pin: "1111" })).status).toBe(204);
    expect((await listed()).find((profile) => profile.id === me)).toMatchObject({ admin: true, hasPin: true });
    const again = await send(`/api/profiles/${me}/claim-admin`, "POST", { pin: "1111" });
    expect(again.status).toBe(403);
    expect(read(again)).toEqual({ reason: "not-allowed" });
    const text = new TextDecoder().decode((await rawRequest(server.port, "/api/profiles")).body);
    expect(text).not.toMatch(/pin_?hash|pin_?salt/i);
  });

  test("the admin adds a grown-up with a first PIN, and a grown-up adds its own kid", async () => {
    const maja = await send("/api/profiles", "POST", { actorId: me, pin: "1111", name: "Maja", kids: false, newPin: "2222" });
    expect(maja.status).toBe(201);
    const majaId = read(maja).id as string;
    expect(read(maja)).toMatchObject({ name: "Maja", kids: false, kidsAge: null, admin: false, hasPin: true });
    const mia = await send("/api/profiles", "POST", { actorId: majaId, pin: "2222", name: "Mia", kids: true, kidsAge: 6 });
    expect(mia.status).toBe(201);
    expect(read(mia)).toMatchObject({ name: "Mia", kids: true, kidsAge: 6, parentId: majaId, hasPin: false });
  });

  test("a refusal says why, with its own status", async () => {
    const majaId = await idOf("Maja");
    const cases: [Record<string, unknown>, number, string][] = [
      [{ actorId: me, pin: "1111", name: "Ben", kids: false }, 400, "invalid"],
      [{ actorId: me, pin: "1111", name: "Zoe", kids: true, kidsAge: 9 }, 400, "invalid"],
      [{ actorId: "nobody", pin: "1111", name: "Zoe", kids: true, kidsAge: 12 }, 404, "not-found"],
      [{ actorId: me, pin: "9999", name: "Zoe", kids: true, kidsAge: 12 }, 403, "wrong-pin"],
      [{ actorId: majaId, pin: "2222", name: "Ben", kids: false, newPin: "3333" }, 403, "not-allowed"],
      // Nobody asking is a first profile, and this player has grown-ups.
      [{ name: "Zoe", newPin: "3333" }, 403, "not-allowed"],
    ];
    for (const [body, code, reason] of cases) {
      const answer = await send("/api/profiles", "POST", body);
      expect(answer.status).toBe(code);
      expect(read(answer)).toEqual({ reason });
    }
  });

  test("a grown-up from before PINs is told so, and sets its first with nothing to prove", async () => {
    const sam = state.createProfile("Sam")!.id;
    const locked = await send(`/api/profiles/${sam}/unlock`, "POST", { pin: "1234" });
    expect(locked.status).toBe(409);
    expect(read(locked)).toEqual({ reason: "no-pin" });
    expect((await send(`/api/profiles/${sam}/pin`, "PUT", { actorId: sam, pin: "", newPin: "4444" })).status).toBe(204);
    expect((await send(`/api/profiles/${sam}/unlock`, "POST", { pin: "4444" })).status).toBe(204);
  });

  test("a kid's profile opens without a PIN, a grown-up's with its own", async () => {
    expect((await send(`/api/profiles/${await idOf("Mia")}/unlock`, "POST", {})).status).toBe(204);
    expect((await send(`/api/profiles/${me}/unlock`, "POST", { pin: "1112" })).status).toBe(403);
    expect((await send(`/api/profiles/${me}/unlock`, "POST", { pin: "1111" })).status).toBe(204);
  });

  test("a parent changes its kid's limit", async () => {
    const [majaId, miaId] = [await idOf("Maja"), await idOf("Mia")];
    const changed = await send(`/api/profiles/${miaId}/kids-age`, "PUT", { actorId: majaId, pin: "2222", age: 12 });
    expect(changed.status).toBe(204);
    expect((await listed()).find((profile) => profile.id === miaId)).toMatchObject({ kidsAge: 12 });
  });

  test("the admin removes a grown-up — its kids go too — but never itself", async () => {
    const [majaId, miaId] = [await idOf("Maja"), await idOf("Mia")];
    expect((await send(`/api/profiles/${me}`, "DELETE", { actorId: me, pin: "1111" })).status).toBe(403);
    expect((await send(`/api/profiles/${majaId}`, "DELETE", { actorId: me, pin: "1111" })).status).toBe(204);
    const ids = (await listed()).map((profile) => profile.id);
    expect(ids).not.toContain(majaId);
    expect(ids).not.toContain(miaId);
  });

  test("a removal from another origin is refused, body and all", async () => {
    const response = await send(`/api/profiles/${me}`, "DELETE", { actorId: me, pin: "1111" }, {
      Origin: "https://elsewhere.example",
    });
    expect(response.status).toBe(403);
    expect((await listed()).some((profile) => profile.id === me)).toBe(true);
  });

  test("renaming is not a route", async () => {
    expect((await send(`/api/profiles/${me}`, "PATCH", { name: "Someone" })).status).toBe(405);
  });

  /**
   * The point of the whole change: one profile's shelves are not another's,
   * and a path is what says which. A profile that does not exist is a 404
   * rather than a write that lands somewhere plausible.
   */
  test("one profile's state is not another's", async () => {
    const you = state.createProfile("Maja")!.id;

    await send(mine(`/progress/${SET}`), "PUT", { at: 1234, duration: 2400 });
    await send(mine(`/watchlist/${SET}`), "PUT", {});

    const theirs = await snapshot(you);
    expect(theirs.progress).toEqual([]);
    expect(theirs.watchlist).toEqual([]);

    // And mine is still mine.
    const ours = await snapshot();
    expect(ours.progress[0]).toMatchObject({ setId: SET, at: 1234 });

    state.deleteProfile(you);
  });

  test("state for a profile that is not there is a 404, not an empty shelf", async () => {
    expect((await rawRequest(server.port, "/api/profiles/nobody/state")).status).toBe(404);
    expect((await send("/api/profiles/nobody/progress/" + SET, "PUT", { at: 5 })).status).toBe(404);
  });

  test("a player with no grown-up makes its first profile, which runs the household", async () => {
    const fresh = new WatchState(join(dir, "fresh.db"));
    const other = await startServer({ db: index(), source: new NoSource(), state: fresh });
    const first = (name: string, newPin: string) => rawRequest(other.port, "/api/profiles", {
      method: "POST", headers: JSON_HEAD, body: JSON.stringify({ name, newPin }),
    });
    try {
      const made = await first("André", "1111");
      expect(made.status).toBe(201);
      expect(read(made)).toMatchObject({ name: "André", kids: false, admin: true, hasPin: true });
      const second = await first("Maja", "2222");
      expect(second.status).toBe(403);
      expect(read(second)).toEqual({ reason: "not-allowed" });
    } finally {
      await other.close();
      fresh.close();
    }
  });

  test("five wrong PINs make every PIN wait, answered 429 with Retry-After", async () => {
    // Its own store: the count is one per store, and this one must not
    // leave the shared server waiting for every test after it.
    const waiting = new WatchState(join(dir, "waiting.db"));
    const other = await startServer({ db: index(), source: new NoSource(), state: waiting });
    try {
      const andre = waiting.createProfile("André")!.id;
      waiting.manage().claimAdmin(andre, "1111");
      const unlock = (pin: string) => rawRequest(other.port, `/api/profiles/${andre}/unlock`, {
        method: "POST", headers: JSON_HEAD, body: JSON.stringify({ pin }),
      });
      for (let wrong = 0; wrong < 5; wrong++) expect((await unlock("0000")).status).toBe(403);
      const right = await unlock("1111");
      expect(right.status).toBe(429);
      const seconds = Number(right.headers.get("retry-after"));
      expect(seconds).toBeGreaterThan(0);
      expect(read(right)).toEqual({ reason: "wait", retryAfter: seconds });
    } finally {
      await other.close();
      waiting.close();
    }
  });
});
```

  In `web/test/state-write-triggers-sync.test.ts`, replace the test
  `"fires for the watchlist, Kids and a profile write"` (`:157-163`) with:

```ts
  test("fires for the watchlist, Kids and a profile write", async () => {
    const route = router();
    expect((await route(request(`/api/profiles/${me}/watchlist/${SET}`, "PUT", {}))).status).toBe(204);
    expect((await route(request(`/api/kids/${SET}`, "PUT", {}))).status).toBe(204);
    expect((await route(request(`/api/profiles/${me}/claim-admin`, "POST", { pin: "1111" }))).status).toBe(204);
    expect(writes).toBe(3);
  });

  test("never fires for entering a profile, which changes nothing a document says", async () => {
    const route = router();
    await route(request(`/api/profiles/${me}/claim-admin`, "POST", { pin: "1111" }));
    writes = 0;
    expect((await route(request(`/api/profiles/${me}/unlock`, "POST", { pin: "1111" }))).status).toBe(204);
    expect(writes).toBe(0);
  });
```

- [ ] **Step 6.2: Run — expect failure**

```bash
bun test test/state-http.test.ts test/state-write-triggers-sync.test.ts
```

Expected: the new profile tests fail (`POST /api/profiles` with an actor still answers 201 via
the old route, but `claim-admin`/`unlock`/`pin`/`kids-age` answer 404, `PATCH` answers 204).

- [ ] **Step 6.3: The shared JSON helpers** — create `web/src/state/route-json.ts`:

```ts
/**
 * JSON in and out of the state routes, shared by `routes.ts` and
 * `profiles-routes.ts` so both read a body and answer one the same way.
 */

import type { PlayerResponse } from "../http/contracts";
import { bodiless, withBody } from "../response";

/** A request body as JSON, or `null` for none or for one that is not JSON. */
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

- [ ] **Step 6.4: The profile routes** — create `web/src/state/profiles-routes.ts`:

```ts
/**
 * The profile routes: who watches this library, and managing them.
 *
 * Kept out of `routes.ts` for its line limit. Every write passes the same
 * `refuseUnsafeBrowserWrite` check the other state writes do; whether the PIN
 * is right and the rule agrees is `profiles-manage.ts`'s to decide, and a
 * refusal answers with its reason so the page can say what went wrong.
 */

import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import { refuseUnsafeBrowserWrite } from "../http/browser-write";
import { withBody } from "../response";
import type { Profile } from "./profiles";
import type { Refusal, Refused } from "./profiles-manage";
import { json, parse, status } from "./route-json";
import type { WatchState } from "./store";

/** A profile's id in a path; `routes.ts` matches every profile path by it. */
export const PROFILE_ID = "([A-Za-z0-9-]{1,64})";
const PROFILES = /^\/api\/profiles$/;
const PROFILE = new RegExp(`^/api/profiles/${PROFILE_ID}$`);
const ACTION = new RegExp(`^/api/profiles/${PROFILE_ID}/(unlock|claim-admin|pin|kids-age)$`);

/** A wrong PIN and a refused role are both 403; the body says which. */
const CODES: Record<Refusal, number> = {
  invalid: 400,
  "not-found": 404,
  wait: 429,
  "no-pin": 409,
  "wrong-pin": 403,
  "not-allowed": 403,
};

/** Answers a profile route, or `null` when the path is not one. */
export function profileRoute(request: PlayerRequest, state: WatchState): PlayerResponse | null {
  const { method, path } = request;
  const list = PROFILES.test(path);
  const named = PROFILE.exec(path);
  const acting = ACTION.exec(path);
  if (!list && named === null && acting === null) return null;

  // Who watches this library: the one question askable before anyone has
  // said who they are. It says whether a profile has a PIN, never what.
  if (method === "GET" || method === "HEAD") {
    if (!list) return status(405);
    return json(JSON.stringify({ remembers: state.remembers, profiles: state.profiles() }), method === "HEAD");
  }

  const refusal = refuseUnsafeBrowserWrite(request);
  if (refusal) return refusal;
  const body = (parse(request.body) ?? {}) as Record<string, unknown>;
  const actorId = typeof body.actorId === "string" ? body.actorId : "";
  const manage = state.manage();

  if (list) {
    if (method !== "POST") return status(405);
    // Nobody asking is the first profile, on a player with no grown-up yet.
    if (body.actorId === undefined) return answer(manage.createFirst(body.name, body.newPin));
    // Only a literal true: a restricting flag is not switched on by accident.
    return answer(body.kids === true
      ? manage.createKid(actorId, body.pin, body.name, body.kidsAge)
      : manage.createGrownUp(actorId, body.pin, body.name, body.newPin));
  }
  if (named !== null) {
    return method === "DELETE" ? answer(manage.remove(actorId, body.pin, named[1]!)) : status(405);
  }

  const id = acting![1]!;
  switch (`${method} ${acting![2]}`) {
    case "POST unlock":
      return answer(manage.unlock(id, body.pin));
    case "POST claim-admin":
      return answer(manage.claimAdmin(id, body.pin));
    case "PUT pin":
      return answer(manage.setPin(actorId, body.pin, id, body.newPin));
    case "PUT kids-age":
      return answer(manage.setKidsAge(actorId, body.pin, id, body.age));
    default:
      return status(405);
  }
}

/** 204 for done, 201 and the profile for one made, a reason for a refusal. */
function answer(result: Refused | Profile | null): PlayerResponse {
  if (result === null) return status(204);
  if (!("reason" in result)) return json(JSON.stringify(result), false, 201);
  const headers: Record<string, string> =
    result.retryAfter === undefined ? {} : { "retry-after": String(result.retryAfter) };
  return withBody(JSON.stringify(result), "application/json", { status: CODES[result.reason], headers });
}
```

- [ ] **Step 6.5: Route through them from `web/src/state/routes.ts`**

  1. Replace the imports (`:12-15`) with:

```ts
import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import { refuseUnsafeBrowserWrite } from "../http/browser-write";
import { PROFILE_ID, profileRoute } from "./profiles-routes";
import { json, parse, status } from "./route-json";
import type { WatchState } from "./store";
```

  2. Replace `const P = "([A-Za-z0-9-]{1,64})";` (`:26`) with `const P = PROFILE_ID;` and delete the
     two lines after it, `const PROFILES = …` and `const PROFILE = …` (`:27-28`).
  3. Replace the block from `// Who watches this library, which is the one question askable before`
     through the end of the `if (named) { … }` block (`:94-120`) with:

```ts
    // Who watches this library, and managing them — PIN and rule checked in
    // `profiles-routes.ts`.
    const profile = profileRoute(request, state);
    if (profile) return profile;
```

  4. Delete everything after `createStateRouter`'s closing `}` (`:254-267`: the blank line,
     `function parse`, `const json`, `const status`) — they are `route-json.ts` now.

- [ ] **Step 6.6: `WatchState` loses the rename.** In `web/src/state/store.ts`, delete
  `renameProfile` and the blank line after it:

```ts
  renameProfile(id: string, name: unknown): boolean {
    if (!this.db) return false;
    const clean = cleanName(name);
    if (clean === null) return false;
    return this.db.query("UPDATE profiles SET name = ?2 WHERE id = ?1").run(id, clean).changes > 0;
  }

```

- [ ] **Step 6.7: A `DELETE` may carry a body.** In `web/src/server.ts`, replace (`:160-162`)

```ts
    // Read only for the methods that carry one, so a GET is never held up
    // waiting on a stream that will not produce anything.
    const carriesBody = !["GET", "HEAD", "DELETE"].includes(described.method);
```

with

```ts
    // Read for every method that may carry one, a DELETE included — it may say
    // who is asking. A GET or HEAD never waits on a stream that produces nothing.
    const carriesBody = !["GET", "HEAD"].includes(described.method);
```

  and in `web/src/http/browser-write.ts` replace
  ``  // `DELETE` carries no body, so nothing to declare a type for.`` (`:25`) with:

```ts
  // `DELETE` is exempt: no form can send one, and a cross-origin script needs
  // a preflight this server never answers. The Origin check above still holds.
```

- [ ] **Step 6.8: Entering a profile is not worth a sync round.** In `web/src/routes.ts`, in
  `writeWorthSyncing`, after `if (request.path.endsWith("/preferences")) return false;` (`:79`) add:

```ts
  // Entering a profile checks a PIN and changes nothing a document says.
  if (request.path.endsWith("/unlock")) return false;
```

- [ ] **Step 6.9: Run — expect pass**

```bash
bun test test/state-http.test.ts test/state-write-triggers-sync.test.ts test/http.test.ts \
  test/state-profiles-manage.test.ts test/state-store.test.ts && bun run typecheck && \
  wc -l src/state/routes.ts src/state/profiles-routes.ts src/state/route-json.ts src/state/store.ts src/server.ts src/routes.ts
```

Expected: all pass (`http.test.ts:437` body-less `DELETE` still 204); `routes.ts` 229,
`profiles-routes.ts` 90, `route-json.ts` 22, `store.ts` 777, `server.ts` 269, `src/routes.ts` 156.

- [ ] **Step 6.10: Bump (§ Bumping, patch) and commit**

```bash
cd /home/andre/Workspace/mediagram
git add web/src/state/route-json.ts web/src/state/profiles-routes.ts web/src/state/routes.ts web/src/state/store.ts \
  web/src/server.ts web/src/http/browser-write.ts web/src/routes.ts \
  web/test/state-http.test.ts web/test/state-write-triggers-sync.test.ts \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(web): profile routes ask who is asking and their PIN; renaming a profile is gone; release <next>"
```

### Task 7 — A Kids mark from 6, over the API; the ceilings locked in

**Files:** modify `web/src/state/routes.ts`, `web/test/state-http.test.ts`, `web/test/code-standards.test.ts`.

- [ ] **Step 7.1: Write the failing test.** In `web/test/state-http.test.ts`, inside
  `describe("marking a title as a child's", …)`, after the test
  `"is not under a profile, because the mark is not one"` add:

```ts
  test("a mark is from 12 unless it says 6, and saying so again moves it", async () => {
    expect((await send(`/api/kids/${SET}`, "PUT", { age: 6 })).status).toBe(204);
    let marks = await read();
    expect(marks.kids).toContain(SET);
    expect(marks.fromSix).toContain(SET);

    expect((await send(`/api/kids/${SET}`, "PUT", {})).status).toBe(204);
    marks = await read();
    expect(marks.kids).toContain(SET);
    expect(marks.fromSix).not.toContain(SET);

    expect((await send(`/api/kids/${SET}`, "PUT", { age: 7 })).status).toBe(400);
    expect((await send(`/api/kids/${SET}`, "DELETE")).status).toBe(204);
  });
```

- [ ] **Step 7.2: Run — expect failure**

```bash
bun test test/state-http.test.ts -t "from 12 unless it says 6"
```

Expected: fails — `marks.fromSix` is `undefined`.

- [ ] **Step 7.3: Implement.** In `web/src/state/routes.ts`:

  1. Replace, in the `KIDS` branch,
     `if (reading) return json(JSON.stringify({ kids: state.kids() }), method === "HEAD");` with:

```ts
      // Every live mark, and which of them are "from 6" — a subset, so a page
      // that reads `kids` alone still sees every mark.
      if (reading) return json(JSON.stringify({ kids: state.kids(), fromSix: state.kidsFromSix() }), method === "HEAD");
```

  2. In the `KIDS_ITEM` branch, replace `state.setKids(kid[1]!, method === "PUT");` with:

```ts
      // No age is from 12, which is what every mark meant before there were
      // two; anything but 6 or 12 is not an age.
      const age = (parse(request.body) as { age?: unknown } | null)?.age ?? 12;
      if (method === "PUT" && age !== 6 && age !== 12) return status(400);
      state.setKids(kid[1]!, method === "PUT", age === 6 ? 6 : 12);
```

- [ ] **Step 7.4: Run — expect pass, then measure**

```bash
bun test test/state-http.test.ts test/state-write-triggers-sync.test.ts && \
  wc -l src/state/store.ts src/state/routes.ts src/state/sync-record.ts
```

Expected: pass; `store.ts` 777, `routes.ts` 235, `sync-record.ts` 322.

- [ ] **Step 7.5: Lock the shrink in.** In `web/test/code-standards.test.ts`, set the three
  entries to the numbers `wc -l` printed in 7.4:

```ts
  "src/state/routes.ts": 235,
  "src/state/store.ts": 777,
  "src/state/sync-record.ts": 322,
```

and extend the comment's last sentence (after the `schema.ts` revision added in the previous
phase) so it ends:

```ts
 * as one list; moving one version into a second file would make that harder.
 * Lowered the same day for `src/state/store.ts`, `src/state/routes.ts` and
 * `src/state/sync-record.ts`, once profiles, their routes and their role keys
 * moved out to `profiles*.ts`, `route-json.ts` and `roles-record.ts`.
 */
```

- [ ] **Step 7.6: Run — expect pass**

```bash
bun test test/code-standards.test.ts
```

- [ ] **Step 7.7: Bump (§ Bumping, patch) and commit**

```bash
cd /home/andre/Workspace/mediagram
git add web/src/state/routes.ts web/test/state-http.test.ts web/test/code-standards.test.ts \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(web): a Kids mark from 6 is set and read over the API; release <next>"
```

### Task 8 — Phase gate (no commit unless something had to change)

- [ ] **Step 8.1: Whole web suite, types, lint, line limits**

```bash
cd /home/andre/Workspace/mediagram/web && bun test && bun run typecheck && bun run lint && \
  wc -l src/state/profiles*.ts src/state/route-json.ts src/state/{routes,store,sync-record}.ts src/server.ts
```

Expected: every test passes; no type or lint errors; every new file < 200; listed files at or
under their (now lowered) ceilings.

- [ ] **Step 8.2: Do not push yet.** This phase's commits wait on `main` locally until phase 03's
  are on top of them; push both together.

## Todo list

- [x] Task 1 — `profiles-pin.ts`, `pin-hash.json`, runner
- [x] Task 2 — `profiles-wait.ts` with an injectable clock
- [x] Task 3 — `profiles-rules.ts`, `profile-rules.json` (42 cases), runner
- [x] Task 4 — `ProfileManager`: create-first, unlock, claim-admin, set-pin; `NewProfile` role fields; `WatchState.manage()`
- [x] Task 5 — create grown-up / kid, remove (cascade), set-kids-age; order and wait tests
- [x] Task 6 — `profiles-routes.ts` (helpers from the existing `route-shared.ts`); rename gone; `DELETE` body read; unlock not synced
- [x] Task 7 — `/api/kids` age; ceilings lowered
- [x] Task 8 — phase gate green; held for phase 03

## Success criteria

- `bun test` green in `web/`, including both shared fixture runners and `code-standards.test.ts`; typecheck and lint clean.
- Every row of contract §2 is a case in `profile-rules.json` and passes; both §5 vectors pass.
- Each §3 reason is observed over a real socket with its status and body (`state-http.test.ts` "a refusal says why…", "no-pin", 429 with `Retry-After`).
- Five wrong PINs then the right one answers `wait` (review focus 5); a PIN-less grown-up sets a first PIN (review focus 1).
- A fresh player makes its first profile over `POST /api/profiles { name, newPin }` and it is the admin; a second such call answers 403 `not-allowed`.
- `PATCH /api/profiles/:id` answers 405; `GET /api/profiles` text never matches `pin_hash|pin_salt|pinHash|pinSalt`.
- `routes.ts` and `store.ts` smaller than when the phase started; ceilings lowered to match.

## Risk assessment

| Risk | L×I | Mitigation |
|---|---|---|
| Pushed without phase 03: the picker cannot add or remove anyone | M×H | Overview and Step 8.2 hold the push; plan.md § Dependencies says the same |
| A device bootstraps (`create-first`) before its first sync and becomes a second admin | M×L | Contract §3: the merge's earliest-claim rule hands the role to the older claim; `importRoles` clears the local claim (phase 01) |
| Wait lost on a catalog swap if kept in the router | — | Kept on `WatchState`, one per process (`index.ts:135`) |
| Reading `DELETE` bodies changes an unrelated route | L×L | Body-less `DELETE` reads an empty body; `http.test.ts:437-444` pins it |
| A device whose clock runs behind writes a limit or PIN that loses to the value it replaced | M×M | `MAX(now, stored + 1)` in `writePin` and `setKidsAge` (§7); far-ahead-clock tests in Tasks 4 and 5 |
| `store.ts` ceiling raced by another session's commit | L×M | Rebase before the phase; the ratchet test fails loudly; lower ceilings only in the last task |
| Duplicate display names across a grown-up and a kid merge into one viewer on sync | L×M | Pre-existing (sync identity is the name); not widened here; noted |

**Rollback.** Revert Tasks 7 → 1 in order; each is one commit. Reverting Task 6 restores the
old routes and the picker works again; PINs, admin claims and limits written meanwhile stay in
their columns and keep syncing (phase 01), harmless to the old routes.

## Security considerations

- **Not a login** (spec §1): the state API has no sessions and a kid's filter runs in the
  browser; the PIN stops a child tapping into a grown-up's profile, nothing more. This is said
  where the next reader finds it — `profiles-manage.ts`'s header.
- Every management action is decided on the server (`ProfileManager`), so a page that forgets
  to hide a button still cannot do the thing.
- A wrong PIN is answered before the rule, so a guess without the PIN learns nothing about who
  may do what; the wait is global per process and answers without comparing.
- The PIN is compared as two SHA-256 digests with `timingSafeEqual`; a wrong-length stored
  hash returns `false` before the call. No PIN, hash or salt is logged or returned over HTTP.
- `create-first` is the one write with no PIN behind it, and it only works on a player with
  no grown-up — the state every profile write had before this change, and the only way a
  fresh install gets anyone. Once one grown-up exists it answers `not-allowed`.
- Writes keep the existing `Origin` and JSON-type checks (`refuseUnsafeBrowserWrite`); the
  `DELETE` exemption from the type check is unchanged and still sound (no form sends `DELETE`;
  a cross-origin script needs an unanswered preflight). Bodies stay bounded at 64 KiB
  (`server.ts:46`).
- Four digits under a salted fast hash are brute-forceable by anyone holding the sync document
  (the household's own channel) — the accepted threat model (spec §3).

## Next steps

- Phase 03 (browser) consumes contract §8 only: picker, PIN prompt, manage panel, the kid
  filter and the three-way Kids mark. Push this phase together with it.
- Phase 05 (core) ports `allowed`, the PIN hash, the wait and `create_first_admin` against
  `profile-rules.json` and `pin-hash.json`, and mirrors this phase's order (structural checks
  before the wait; the wait only inside a comparison; a malformed current PIN is `wrong-pin`)
  and the `MAX(now, previous + 1)` dating of `kids_age_updated_at` / `pin_updated_at`.
