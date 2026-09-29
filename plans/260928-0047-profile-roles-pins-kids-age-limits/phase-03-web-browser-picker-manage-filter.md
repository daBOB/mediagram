# Phase 03 — Web: picker, PIN prompt, manage panel, per-kid filter, marks

## Context links

- Contract (authoritative): [shared-contract.md](shared-contract.md) — this phase consumes **§8** (HTTP), **§11** (client filter) and **§12** (picker start states); §3 (reasons, amended 2026-09-28) for what the prompt says; §1/§2 define the "who may manage whom" view the panel draws; §10's `profile-rules.json` is read by one test.
- Spec: `docs/superpowers/specs/2026-09-28-profile-roles-design.md` §1 (not a login), §2 (Managing, first admin, pre-upgrade grown-ups, wrong PINs), §4 (what a kid sees, the Kids control), §5 Browser.
- Plan: [plan.md](plan.md) — **02 is pushed together with 03** (02 changes the routes today's picker calls).
- Bumping procedure used by every commit here: [phase-08 § Bumping](phase-08-verify-docs-version.md) (B1–B4, patch).
- Visual rules: `web/DESIGN.md` (Components → Pills; Do's/Don'ts: no boxed panels on paper, Fraunces for headings, Geist for labels, 44px targets).
- UI check: stub harness `cd web && bun run preview` (copies of the index and `state.db`, no Telegram; `web/scripts/preview.ts:48-54`) — never the real player.

## Overview

Priority P1 (the web half of the feature is unusable without it). Status: pending.
Depends on phases 01 and 02 (schema, routes, `profile-rules.json`).

The browser side of profile roles:

1. **Per-kid filter** — `forKidsProfile(sets, marks, limit)` / `kidsVerdict(set, limit)`; Kids marks carry an age (from 6 / from 12); every "12" the client assumed becomes the kid's own limit.
2. **Kids control** — on an unrated title a native `<select>` "Not for kids / From 6 / From 12"; rated titles unchanged (locked verdict); hidden on kids profiles.
3. **Client for §8** — `profile-api.js`; the three profile writers leave `watch-state.js` (rename outright).
4. **One PIN prompt** — `pin-prompt.js`.
5. **Manage profiles panel** — `profile-manage.js`.
6. **Picker** — the three start states of contract §12 (no grown-up → "Create the first profile — it runs this household"; grown-ups but no admin → "Who runs this household?"; an admin → tiles + Manage); kid opens at once, grown-up asks PIN (or sets one), honest note; New profile tile and `window.prompt` remove flow gone.

## Key insights (verified)

**Line budgets** (`web/test/code-standards.test.ts:15` LIMIT 200; ceilings `:36-65`; count = `split("\n").length - 1` = `wc -l`):

| File | Now | Ceiling | Plan |
|---|---|---|---|
| `public/lib/watch-state.js` | 502 | 502 (`:47`) | Task 1 net 0 (kids section rewritten in 30 lines); Task 6 −24 (three profile writers `:153-175`) → ~478, ceiling lowered |
| `public/app.js` | 742 | 742 (`:36`) | Task 2: +1 import, −2 (`kidsProfile` `:93` + blank `:94`) → 741, ceiling lowered; Task 6 `switchProfile` net 0 |
| `public/lib/library-session.js` | 200 | 200 (not listed) | Task 1: `:56-59` (4 lines) → 3 lines → 199 |
| `public/lib/catalog/shelf-view.js` | 285 | 285 (`:40`) | Task 2: `emptyState` `:98-102` edited in place, net 0 |
| `public/lib/profile-picker.js` | 179 | 200 | 174 as written below (old `:81-149` out, the three §12 start states in) |
| `public/lib/catalog/settings-page.js` | 115 | 200 | +1 |
| `public/lib/catalog/department-pages.js` | 173 | 200 | net 0 |
| `public/lib/playback/player-library-marks.js` | 69 | 200 | ~70 |
| `public/styles/library-controls.css` | 76 | 200 | ~88 |
| new `profile-api.js` / `pin-prompt.js` / `profile-manage.js` | — | 200 | 94 / 116 / 169 as written below |

**Contract facts that shape the browser (§3 amended, §8, §12)**

- **Bootstrap**: `POST /api/profiles` with **no `actorId`** and `{ name, newPin }` is `create-first` → 201, the new grown-up is the admin; 403 `not-allowed` once any grown-up exists. The picker offers it only while no grown-up is known locally (§12 row 1) — which also covers a player that only knows kids.
- `wait` is answered only by calls that compare a PIN; a kid's unlock and a PIN-less self set-pin never wait. The web picker never calls `unlock` for a kid at all.
- A malformed *current* PIN is compared and answers `wrong-pin` (counted), not `invalid`; `invalid` is left for `name`/`newPin`/`age`. The prompt checks `newPin`'s shape before sending, so an `invalid` it shows is nearly always the name.
- Consequence: a tab whose `hasPin` is stale (another player set this grown-up's PIN) sends a PIN-less self set-pin with `pin: ""`, which is now **compared → `wrong-pin`, counted**. The picker re-reads profiles when opened on a running page, and after a refused first-profile attempt.

**Code facts**

- `age-rating.js:19` `KIDS_AGE_LIMIT = 12`; `kidsVerdict(set)` `:38-42`; `forKidsProfile(sets, marked: Set)` `:52-57`. Callers: `library-session.js:59` (filter), `player-library-marks.js:21,50` (verdict). `ageOf`/`ageLabel` also used by `catalog/film-page.js:36,76`, `catalog/home-cover.js:134` — unchanged.
- `watch-state.js`: `held.kids = new Set()` `:57-59` (type `:43`); `loadKids` `:396-407` reads only `said.kids`; `isKids` `:409`, `kids()` `:410`, `setKids(setId, marked)` `:413-418` PUTs `{}`. Callers: `isKids` → `player-library-marks.js:24,51`, `test/player-features.test.ts:119-131`; `kids()` → `library-session.js:59`, `library-session.d.ts:33`, `test/library-session.test.ts:23`.
- `watch-state.js` profile writers: `createProfile` `:153-159` (caller `profile-picker.js:115`, `test/watch-state-async.test.ts:100,188`), `renameProfile` `:161-167` (no caller — spec §5), `deleteProfile` `:169-175` (caller `profile-picker.js:143`). Their `write()` `:95-106` returns `null` on any non-2xx and drops the body, so it cannot carry a refusal reason → `profile-api.js` needs its own fetch. `createRecord` `:109-116` stays (collections). `loadProfiles` `:124-135` is exported — the refresh `profile-api.js` uses. `useProfile(null)` `:184-194` clears the selection and the remembered id.
- `library-session.js:56` `kidsProfile`, `:59` `filtered`; `.d.ts:31-34` `LibrarySessionState { profile(); kids(): string[] }`; test fake `test/library-session.test.ts:19-26`.
- `app.js:93` `kidsProfile`, used at `:202`, `:241` (`emptyState`) and `:272` (`deptContext.kids`); `department-pages.js:31` (typedef), `:39,:83,:129` pass `cx.kids`. `shelf-view.js:102` hard-codes `"Nothing rated FSK 12 or under yet."`; `settings-page.js:103` shows `· Kids`.
- `app.js:433-443` `switchProfile`: `if (chosen === before) return;` skips `reapply()` — so a kid's limit changed (or the kid removed) in Manage while this device is on it would keep the old filter. Task 6 compares the profile's content instead.
- `catalog/pin-control.js:22` checks `kids` only to hide the editor's-choice control — no limit involved; **no change**.
- `player-library-marks.js:17-36` `refreshKids`, `:49-53` click toggles the mark; markup `index.html:139` `<button id="kids" class="ghost">`. HUD selects are already interactive (`playback.css:43-47`) and `.ghost` (`playback.css:110-124`) styles a select as well → **no playback.css change** (it is at its 752 ceiling).
- `profile-picker.js`: New profile tile `:81-91`, naming form `:94-126`, `window.prompt` remove `:128-149`, note `:151-161`, "Stay as I am" `:165-174`.
- Server guard `src/http/browser-write.ts:15-30`: cross-origin → **bodiless 403**; non-DELETE without `application/json` → 415. So every call sends the JSON content type (DELETE too — it carries `{ actorId, pin }`), and a 403 with no body must read as "no reason", not as `wrong-pin`.
- Precedent for server importing browser JS: `src/transcode/video-copy.ts:21` (`playable.js`), `src/catalog/artwork-routes.ts:8` — relevant to contract question 2.
- **DOM tests**: fake `Node` `test/support/player-environment.ts:2-106` (`fire`, `showModal` sets `open`, `close()` fires `close`, `setAttribute/getAttribute`, `value`, `hidden`); `browserEnvironment()` `:219-326` (`respondWith`, `requests`, `window` with `localStorage`); `descendants`/`textOf` in `test/support/browser-application.ts`. Picker tests today: `test/management-controls.test.ts:53-217`; whole-app tests `test/browser-application.test.ts` (`finishProfilePicker` `:497-518` creates via `who-add`, `:520-545`, empty discovery `:574-587` expects "New profile", kids block `:619-724`). `browser-html-player.test.ts` parses the real `index.html` → a missing `#kids-age` would throw.
- **The TS env has no DOM lib**: `bunx tsc --ignoreConfig --lib ESNext --types bun-types` on `const x: HTMLElement` → `TS2304 Cannot find name 'HTMLElement'` (probed 2026-09-28). JSDoc `{HTMLElement}` in JS therefore reads as `any`; tests pass the fake `Node` **without casts** (a cast to `HTMLElement` would fail `bun run typecheck`).
- CSS home for `.who*`: `styles/library-controls.css:2-19`. `.who-add` `:10` and `.who-kids-choice` `:16` die with the naming form; `.who-new`/`.who-create` `:12-18` are reused by the panel's add forms; `.settings-dialog` `:64-65` is the existing modal look (reused by the prompt); `.settings-session` `:67` is the row pattern. Pills: `styles/title-page.css:6-24`.
- Baseline (2026-09-28): the 7 affected test files pass (73 tests), `bun run typecheck` and `bun run lint` clean.
- Preview state copy holds `andre|0, test|0, TV test|0, TV kids|1` (read-only query of `~/.local/share/mediagram-player/state.db`) — the spec's pre-upgrade case exactly.

## Requirements

Functional
- Filter per contract §11: visible iff (rated and `age <= limit`) or (unrated and `marks.get(setId) <= limit`); `KIDS_AGE_LIMIT` deleted; `kidsLimitOf(profile)` fails closed (a kid is never `null` → never unfiltered).
- `loadKids` reads `{ kids, fromSix }` into `Map<setId, 6|12>`; `kidsAge(setId)`, `kidsMarks()`, `setKids(setId, 6|12|null)` (`PUT {age}` / `DELETE`).
- Kids control: rated → locked verdict button as today; unrated → `<select>` Not for kids / From 6 / From 12; both hidden on a kids profile.
- Empty shelf: `Nothing rated FSK ${limit} or under yet.`; Settings: `Name · Kids · FSK N`; picker tile `Kids · FSK N`.
- Picker start states (contract §12): **no grown-up** (none at all, or only kids) → "Create the first profile — it runs this household": name + PIN twice → `create-first` (no actor), kid tiles (if any) still shown and still open freely, no "Manage profiles" (nobody could manage); **grown-ups, none admin** → "Who runs this household?" above the tiles (grown-ups only; `claim-admin`, PIN entered or set twice) + tiles + Manage; **an admin** → tiles + "Manage profiles". After a first profile or a claim the picker redraws; the viewer then enters by tile like anyone else.
- Picker tiles: kid → `useProfile` at once; grown-up → PIN (`unlock`) or, with no PIN, set one twice (`PUT pin` self, `pin: ""`) → `useProfile`; no New profile, no Rename or remove; note per spec §1; discovery failure keeps "Retry profiles"; a remembered profile at start-up asks nothing (unchanged `app.js:700-701`).
- Panel: choose yourself (grown-ups) → PIN once (or set one) → admin: other grown-ups (Reset PIN, Remove), Add a grown-up (first PIN twice); every grown-up: own kids (FSK select, Remove), Add a kid (name + FSK), Change your PIN. PIN held in the panel's closure, dropped on Done. Server refusals shown (`wrong-pin`, `wait N s`, `not-allowed`, …).
- A change that removes this device's own profile (directly or via its parent) lets go of it (`useProfile(null)`), and the picker then offers no "Stay as I am".

Non-functional
- Ratchet green at every commit; new files < 200 lines; no `innerHTML`; `bun run lint`, `bun run typecheck` clean.
- DESIGN.md: full-screen paper panel (no boxed card), Fraunces headings, Geist controls, pills for actions, palette colours only, ≥44px targets.

## Architecture

```
GET /api/profiles ──► watch-state.loadProfiles ──► held.profiles
      ▲                                              ├─► profile-picker: tiles ("Kids · FSK N"), household question, Manage
      │ (re-read after every successful change,      └─► profile-manage: manageable(profiles, actorId)
      │  and when the picker opens on a running page)
profile-api.call ──(JSON body; PIN never in the URL)──► §8 routes
   2xx  ─► change(): loadProfiles(); own profile gone? useProfile(null)
   4xx  ─► { ok:false, reason ∈ §3 | null, retryAfter } ─► pin-prompt.refusalText ─► prompt / panel message

picker start state (§12): no grown-up ─► first-profile form ─► askPin(confirm) ─► api.createFirst {name,newPin} ─► reload ─► redraw
                          grown-ups, no admin ─► household question ─► askGrownUp ─► api.claimAdmin ─► redraw (question gone)
                          admin ─► tiles + Manage
picker tile(kid) ─────────────────────────────► useProfile(id) ─► resolve(id)
picker tile(grown-up) ─► askGrownUp ─► api.prove ─► POST unlock | PUT pin(self, "") ─► useProfile(id)
Manage ─► who are you ─► askGrownUp/api.prove ─► session {actorId, pin} (closure, dropped on Done / wrong-pin)
       ─► api.create | remove | setPin | setKidsAge (actorId, session.pin, …)

GET /api/kids {kids, fromSix} ─► loadKids ─► held.kids Map ─► library-session.filtered:
      kidsLimitOf(profile()) === null ? sets : forKidsProfile(sets, kidsMarks(), limit) ─► every shelf/search/reel
player #kids-age change ─► setKids(setId, 6|12|null) ─► PUT {age} | DELETE /api/kids/:setId
switchProfile: picker closes ─► profile content changed? (other profile / new limit / gone) ─► showProfile + reapply + route
```

## Interfaces

**Consumes** — contract §8: Profile JSON `{ id, name, createdAt, kids, kidsAge, parentId, admin, hasPin }`; `POST /api/profiles {actorId, pin, name, kids, kidsAge?|newPin?}`; `POST /api/profiles {name, newPin}` without `actorId` (`create-first`); `DELETE /api/profiles/:id {actorId, pin}`; `POST …/unlock {pin}`; `POST …/claim-admin {pin}`; `PUT …/pin {actorId, pin, newPin}`; `PUT …/kids-age {actorId, pin, age}`; `GET /api/kids → {kids, fromSix}`; `PUT /api/kids/:setId {age}`; `DELETE /api/kids/:setId`; error body `{ reason, retryAfter? }` with §3 reasons. Contract §11: `forKidsProfile`, `kidsVerdict`, empty-shelf text. Contract §12: the three picker start states. Contract §10 `profile-rules.json` (test only).

**Produces** (module exports)

| Module | Exports |
|---|---|
| `lib/age-rating.js` | `ageOf`, `ageLabel` (unchanged), `kidsLimitOf(profile) → 6\|12\|null`, `kidsVerdict(set, limit)`, `forKidsProfile(sets, marks: Map, limit)`; **removed** `KIDS_AGE_LIMIT` |
| `lib/watch-state.js` | `kidsAge(setId) → 6\|12\|null`, `kidsMarks() → Map`, `setKids(setId, 6\|12\|null)`, `loadKids()` reads `fromSix`; **removed** `isKids`, `kids`, `createProfile`, `renameProfile`, `deleteProfile` |
| `lib/library-session.d.ts` | `LibrarySessionState { profile(): {kids?, kidsAge?}\|null; kidsMarks(): Map<string, number> }` |
| `lib/profile-api.js` (new) | `unlock`, `claimAdmin`, `setPin`, `create`, `createFirst`, `remove`, `setKidsAge`, `prove` → `Promise<{ok:true} \| {ok:false, reason: string\|null, retryAfter?: number}>`; `ownerOf(profiles, kid)`, `manageable(profiles, actorId) → {actor, grownUps, kids}` |
| `lib/pin-prompt.js` (new) | `askPin(parent, {title, confirm?, send}) → Promise<string\|null>`, `askGrownUp(parent, entry, send)`, `pinProblem(pin, again, confirm)`, `refusalText(outcome)` |
| `lib/profile-manage.js` (new) | `openManage(parent, onClose)`, `addForm(label, extra, submit)` (the name + button form the picker's first-profile form reuses) |
| `lib/profile-picker.js` | `chooseProfile(root, opts)` (same signature), `initialOf` |
| `lib/catalog/shelf-view.js` | `emptyState(section, { kidsLimit })` (option was `kids`) |
| `index.html` | `<select id="kids-age">` in the player rail |

## Related code files

Modify: `web/public/lib/age-rating.js`, `web/public/lib/watch-state.js`, `web/public/lib/library-session.js`, `web/public/lib/library-session.d.ts`, `web/public/lib/playback/player-library-marks.js`, `web/public/index.html`, `web/public/lib/catalog/shelf-view.js`, `web/public/lib/catalog/department-pages.js`, `web/public/lib/catalog/settings-page.js`, `web/public/app.js`, `web/public/lib/profile-picker.js`, `web/public/styles/library-controls.css`; tests `web/test/age-rating.test.ts`, `library-session.test.ts`, `player-features.test.ts`, `shelf-view.test.ts`, `management-controls.test.ts`, `browser-application.test.ts`, `watch-state-async.test.ts`, `code-standards.test.ts`, `support/browser-application.ts`.

Create: `web/public/lib/profile-api.js`, `web/public/lib/pin-prompt.js`, `web/public/lib/profile-manage.js`; tests `web/test/kids-marks.test.ts`, `profile-api.test.ts`, `pin-prompt.test.ts`, `profile-manage.test.ts`, `profile-picker.test.ts`.

Delete: none (functions and CSS rules only). Not touched: `web/src/**` (phase 02), `catalog/pin-control.js`, `styles/playback.css`.

## Implementation steps

Every `cd web` below is `/home/andre/Workspace/mediagram/web`. Test fixtures used across new test files:

```ts
const ANDRE = { id: "andre", name: "andre", createdAt: 1, kids: false, kidsAge: null, parentId: null, admin: true, hasPin: true };
const MAJA = { id: "maja", name: "Maja", createdAt: 2, kids: false, kidsAge: null, parentId: null, admin: false, hasPin: true };
const TIM = { id: "tim", name: "Tim", createdAt: 3, kids: true, kidsAge: 6, parentId: "maja", admin: false, hasPin: false };
const LEA = { id: "lea", name: "Lea", createdAt: 4, kids: true, kidsAge: 12, parentId: null, admin: false, hasPin: false };
```

### Task 0: Preconditions

- [ ] **0.1** `cd /home/andre/Workspace/mediagram && git fetch -q origin && git rebase origin/main` (another session commits to `main`).
- [ ] **0.2** Phase 02 has landed:
  ```bash
  cd web
  grep -n "unlock\|claim-admin\|kids-age\|fromSix" src/state/routes.ts
  ls test/fixtures/watch-state/profile-rules.json
  grep -rn "export interface Profile" src/state/
  ```
  Expected: routes present, fixture present, `Profile` has `hasPin`/`admin`/`kidsAge`/`parentId`. If any is missing: stop, report BLOCKED on phase 02. Note the file `Profile` lives in — Task 6 points `watch-state.js:40`'s JSDoc at it.
- [ ] **0.3** Baseline: `cd web && bun test && bun run typecheck && bun run lint` — all green.

### Task 1: Each kid sees up to its own limit; a mark says from which age

- [ ] **1.1 Acceptance test first** — `test/browser-application.test.ts`, inside `describe("kids profiles")` after the `beforeEach` (`:627-635`), add the helper and the test:
  ```ts
  /** The same household with its kid at `kidsAge`, and the Kids marks the server holds. */
  function kidAt(kidsAge: number, kids: object = { kids: ["Marked"], fromSix: [] }) {
    const base = intercept;
    intercept = (url, init) => {
      if (url === "/api/profiles" && !init?.method) {
        return Promise.resolve(Response.json({ remembers: true, profiles: profiles.map((entry) => (entry.kids ? { ...entry, kidsAge } : entry)) }));
      }
      if (url === "/api/kids") return Promise.resolve(Response.json(kids));
      return base(url, init);
    };
  }

  test("an FSK 6 kid sees FSK 6 and what is marked from 6, nothing marked from 12", async () => {
    catalog = JSON.stringify([rated("Family", "6"), rated("Twelve", "12"), rated("FromSix", null), rated("Marked", null)]);
    kidAt(6, { kids: ["FromSix", "Marked"], fromSix: ["FromSix"] });
    await start();
    expect(env.node("n-movies").textContent).toBe("2");
    await env.navigate("#/movies");
    expect(page()).toContain("Family");
    expect(page()).toContain("FromSix");
    expect(page()).not.toContain("Twelve");
    expect(page()).not.toContain("Marked");
  });
  ```
  and in "the player offers no Kids mark on a kids profile" (`:691-697`) add `expect(env.node("kids-age").hidden).toBe(true);`.
  Run `cd web && bun test test/browser-application.test.ts -t "FSK 6 kid"` → **FAIL**: `n-movies` is `"4"` — the fixed limit of 12 lets `Twelve` through and `Marked` counts as a plain mark. Stays red until 1.13.
- [ ] **1.2 Failing rule tests** — replace `test/age-rating.test.ts` with:
  ```ts
  import { describe, expect, test } from "bun:test";
  import * as rating from "../public/lib/age-rating.js";
  import { ageLabel, ageOf, forKidsProfile, kidsLimitOf, kidsVerdict } from "../public/lib/age-rating.js";

  const film = (title: string, fsk: string | null) => ({ setId: title, kind: "movie", title, fsk });
  const episode = (show: string, fsk: string | null, n = 1) => ({ setId: `${show}-${n}`, kind: "ep", show, fsk });

  describe("reading a rating", () => {
    test("an FSK is an age", () => {
      expect(ageOf(film("A", "12"))).toBe(12);
      expect(ageOf(film("A", "0"))).toBe(0);
      expect(ageLabel(film("A", "16"))).toBe("FSK 16");
    });
    test("no rating, or one that is not an age, is unrated", () => {
      expect(ageOf(film("A", null))).toBeNull();
      expect(ageOf(film("A", "PG-13"))).toBeNull();
      expect(ageLabel(film("A", null))).toBeNull();
    });
  });

  describe("the three rules, at a kid's own limit", () => {
    test("at 12, FSK 12 and younger is for kids; 16 and 18 are not", () => {
      for (const fsk of ["0", "6", "12"]) expect(kidsVerdict(film("A", fsk), 12)).toBe("safe");
      for (const fsk of ["16", "18"]) expect(kidsVerdict(film("A", fsk), 12)).toBe("unsafe");
      expect(kidsVerdict(film("A", null), 12)).toBe("unrated");
    });
    test("at 6, FSK 12 is not for kids", () => {
      for (const fsk of ["0", "6"]) expect(kidsVerdict(film("A", fsk), 6)).toBe("safe");
      for (const fsk of ["12", "16"]) expect(kidsVerdict(film("A", fsk), 6)).toBe("unsafe");
    });
    test("there is no one limit for every kid any more", () => {
      expect("KIDS_AGE_LIMIT" in rating).toBe(false);
    });
  });

  describe("a profile's limit", () => {
    test("a kid's own, 12 when it has none that reads as 6, and none for a grown-up", () => {
      expect(kidsLimitOf({ kids: true, kidsAge: 6 })).toBe(6);
      expect(kidsLimitOf({ kids: true, kidsAge: 12 })).toBe(12);
      expect(kidsLimitOf({ kids: true, kidsAge: null })).toBe(12);
      expect(kidsLimitOf({ kids: false, kidsAge: null })).toBeNull();
      expect(kidsLimitOf(null)).toBeNull();
    });
  });

  describe("what a kids profile sees", () => {
    const sets = [
      film("Zero", "0"), film("Six", "6"), film("Twelve", "12"), film("Sixteen", "16"), film("Eighteen", "18"),
      film("Unrated", null), film("FromSix", null), film("FromTwelve", null), film("SixteenMarked", "16"),
      { setId: "lesson-1", kind: "tut", show: "Course", fsk: null },
      { setId: "lesson-2", kind: "tut", show: "Course", fsk: null },
      episode("Bluey", "0"), episode("Dexter", "18"),
    ] as any;
    const marks = new Map([["FromSix", 6], ["FromTwelve", 12], ["SixteenMarked", 6], ["lesson-2", 12]]);
    const seen = (limit: number) => forKidsProfile(sets, marks, limit).map((set: { setId: string }) => set.setId);

    test("at 12: rated twelve and under, and marks from 6 or 12, in the catalog's order", () => {
      expect(seen(12)).toEqual(["Zero", "Six", "Twelve", "FromSix", "FromTwelve", "lesson-2", "Bluey-1"]);
    });
    test("at 6: rated six and under, and marks from 6 only", () => {
      expect(seen(6)).toEqual(["Zero", "Six", "FromSix", "Bluey-1"]);
    });
    test("a hand mark does not override a rating", () => {
      for (const limit of [6, 12]) expect(seen(limit)).not.toContain("SixteenMarked");
    });
    test("unrated and unmarked is hidden at either limit", () => {
      for (const limit of [6, 12]) {
        expect(seen(limit)).not.toContain("Unrated");
        expect(seen(limit)).not.toContain("lesson-1");
      }
    });
  });
  ```
- [ ] **1.3** `cd web && bun test test/age-rating.test.ts` → **FAIL**: `SyntaxError: Export named 'kidsLimitOf' not found in module '…/age-rating.js'`.
- [ ] **1.4 Implement** — replace `public/lib/age-rating.js` with:
  ```js
  /**
   * Age ratings, and what they decide about a kids profile.
   *
   * The rating is TMDB's for the library's country — an FSK in Germany — carried
   * on every catalog row as `fsk` (`"12"`), or `null` when the title has none.
   * Every kids profile has its own limit, 6 or 12, set by the grown-up it
   * belongs to. The rules for a kid with limit N, as the household chose them:
   *
   *  - Rated N or under: for that kid by itself. Nobody has to mark it, and a
   *    mark cannot take it off — the rating decides.
   *  - Rated above N: not for that kid. A hand mark does not bring it back.
   *  - Unrated: only what someone marked by hand, and only from an age at or
   *    under N — "from 6" is for every kid, "from 12" for a 12 only.
   *
   * Pure, so the rules are tested without a browser; the catalog filter and the
   * player's Kids control only ask.
   */

  /**
   * The rating as an age, or `null` when there is none that reads as one.
   * FSK is always a bare number; a letter rating from another country is not an
   * age this rule can compare, so it counts as unrated.
   */
  export function ageOf(set) {
    const text = String(set?.fsk ?? "").trim();
    return /^\d{1,2}$/.test(text) ? Number(text) : null;
  }

  /** `"FSK 12"`, or `null` for an unrated title. */
  export function ageLabel(set) {
    const age = ageOf(set);
    return age === null ? null : `FSK ${age}`;
  }

  /**
   * The limit a profile sees up to: 6 or 12 on a kids profile, `null` for a
   * grown-up or for nobody. Anything but 6 reads as 12 — the limit every kid
   * had before each had its own — so a kid is never taken for a grown-up.
   */
  export const kidsLimitOf = (profile) => (profile?.kids === true ? (profile.kidsAge === 6 ? 6 : 12) : null);

  /** `"safe"`, `"unsafe"`, or `"unrated"` — which of the three rules applies at `limit`. */
  export function kidsVerdict(set, limit) {
    const age = ageOf(set);
    if (age === null) return "unrated";
    return age <= limit ? "safe" : "unsafe";
  }

  /**
   * The catalog a kid with this limit sees: rated at or under it, or unrated and
   * marked by hand from an age at or under it. Applied once to the whole
   * catalog, so every shelf, search and reel built from it agrees.
   * @param {import("./library.js").CatalogSet[]} sets
   * @param {Map<string, number>} marks set id -> the age it is for kids from, 6 or 12
   * @param {number} limit the kid's own, 6 or 12
   */
  export function forKidsProfile(sets, marks, limit) {
    return sets.filter((set) => {
      const verdict = kidsVerdict(set, limit);
      return verdict === "safe" || (verdict === "unrated" && (marks.get(set.setId) ?? Infinity) <= limit);
    });
  }
  ```
- [ ] **1.5** `cd web && bun test test/age-rating.test.ts` → **PASS**.
- [ ] **1.6 Failing marks test** — create `test/kids-marks.test.ts`:
  ```ts
  import { afterEach, beforeEach, expect, test } from "bun:test";
  import * as state from "../public/lib/watch-state.js";
  import { browserEnvironment } from "./support/player-environment";

  let env: ReturnType<typeof browserEnvironment>;
  beforeEach(() => { env = browserEnvironment(); });
  afterEach(() => env.restore());

  test("marks are read with the age each is for kids from", async () => {
    env.respondWith(async () => Response.json({ kids: ["a", "b"], fromSix: ["b"] }));
    await state.loadKids();
    expect(state.kidsAge("a")).toBe(12);
    expect(state.kidsAge("b")).toBe(6);
    expect(state.kidsAge("c")).toBeNull();
    expect([...state.kidsMarks()]).toEqual([["a", 12], ["b", 6]]);
  });

  test("marking says the age; unmarking takes the mark off", () => {
    state.setKids("film", 6);
    expect(state.kidsAge("film")).toBe(6);
    expect(env.requests.at(-1)).toMatchObject({ url: "/api/kids/film", options: { method: "PUT", body: JSON.stringify({ age: 6 }) } });
    state.setKids("film", null);
    expect(state.kidsAge("film")).toBeNull();
    expect(env.requests.at(-1)?.options?.method).toBe("DELETE");
  });
  ```
- [ ] **1.7** `cd web && bun test test/kids-marks.test.ts` → **FAIL**: `TypeError: state.kidsAge is not a function`.
- [ ] **1.8 Implement** in `public/lib/watch-state.js` (net 0 lines):
  - `:43` type: `kids: Map<string, number>, preferences: Map<string, string>}}`
  - `:57-59`:
    ```js
      /** Marked as a child's, as set id -> the age it is for kids from, 6 or 12.
       *  Shared by everyone on this player, not held per profile — a mark is
       *  about the title, not about who is watching. */
      kids: new Map(),
    ```
    (the comment stays 3 lines: fold "Marked as a child's" into the first).
  - replace `:389-418` with exactly these 30 lines:
    ```js
    /**
     * The titles marked as a child's, as set id -> the age each is for kids from:
     * 6 for the answer's `fromSix`, 12 for every other mark. Read once at startup
     * and not per profile — the mark belongs to the library, not to whoever is
     * watching, which is why its path has no profile in it either.
     */
    export async function loadKids() {
      try {
        const response = await fetch("/api/kids");
        if (!response.ok) return;
        const said = await response.json();
        const fromSix = new Set(Array.isArray(said.fromSix) ? said.fromSix : []);
        held.kids = new Map((Array.isArray(said.kids) ? said.kids : []).map((id) => [id, fromSix.has(id) ? 6 : 12]));
        changed();
      } catch {
        // A player that cannot ask simply has an empty shelf, which is the same
        // thing it has before anything is marked.
      }
    }

    export const kidsAge = (setId) => held.kids.get(setId) ?? null;
    export const kidsMarks = () => held.kids;

    /** Marks a title for kids from `age` (6 or 12), or unmarks it with `null`; persists best-effort. @returns {void} */
    export function setKids(setId, age) {
      if (age === null) held.kids.delete(setId);
      else held.kids.set(setId, age);
      void write(`/api/kids/${encodeURIComponent(setId)}`, age === null ? "DELETE" : "PUT", age === null ? undefined : { age });
      changed();
    }
    ```
  - `wc -l public/lib/watch-state.js` → **502** (must not exceed).
- [ ] **1.9** `cd web && bun test test/kids-marks.test.ts` → **PASS**.
- [ ] **1.10 Failing session test** — in `test/library-session.test.ts` replace `fakeState` (`:18-26`) with:
  ```ts
  /** The two calls the session reads from `watch-state.js`, held by a test. */
  function fakeState(profile: { kids?: boolean; kidsAge?: number | null } | null = null, marks: Record<string, number> = {}): LibrarySessionState & { set(next: typeof profile): void } {
    let current = profile;
    return {
      profile: () => current,
      kidsMarks: () => new Map(Object.entries(marks)),
      set(next) { current = next; },
    };
  }
  ```
  and add to `describe("a kids profile")`:
  ```ts
    test("each kid sees up to its own limit, hand marks included", async () => {
      const fake = fakeLibraryPort(JSON.stringify([rated("Six", "6"), rated("Twelve", "12"), rated("FromSix", null), rated("FromTwelve", null)]));
      const state = fakeState({ kids: true, kidsAge: 6 }, { FromSix: 6, FromTwelve: 12 });
      const lib = createLibrarySession({ port: fake.port, state, remoteState: () => {} });
      await lib.start();
      expect(lib.current().library.movies.map((set) => set.setId).sort()).toEqual(["FromSix", "Six"]);
      state.set({ kids: true, kidsAge: 12 });
      lib.reapply();
      expect(lib.current().library.movies.map((set) => set.setId).sort()).toEqual(["FromSix", "FromTwelve", "Six", "Twelve"]);
    });
  ```
  Update `public/lib/library-session.d.ts:30-34`:
  ```ts
  /** The slice of `watch-state.js` the session reads to filter for a profile. */
  export interface LibrarySessionState {
    profile(): { kids?: boolean; kidsAge?: number | null } | null;
    /** Every Kids mark, set id -> the age it is for kids from (6 or 12). */
    kidsMarks(): Map<string, number>;
  }
  ```
  `cd web && bun test test/library-session.test.ts` → **FAIL**: `TypeError: state.kids is not a function`.
- [ ] **1.11 Implement** `public/lib/library-session.js`: `:21` → `import { forKidsProfile, kidsLimitOf } from "./age-rating.js";`; replace `:56-59` with
  ```js
    /** What the chosen profile may see of a catalog; a kids profile is a filter at its own limit, nothing else. */
    const filtered = (sets, limit = kidsLimitOf(state.profile())) =>
      (limit === null ? sets : forKidsProfile(sets, state.kidsMarks(), limit));
  ```
  `bun test test/library-session.test.ts` → **PASS**; `wc -l public/lib/library-session.js` → **199**.
- [ ] **1.12 Failing player test** — in `test/player-features.test.ts` replace the test at `:114-133` with:
  ```ts
    test("a rating decides and is shown locked; an unrated title is for kids from an age, or not at all", () => {
      const marks = mountPlayerLibraryMarks();
      const kids = env.node("kids");
      const age = env.node("kids-age");
      marks.open({ setId: "rated-safe", fsk: "6" });
      expect(kids.hidden).toBe(false);
      expect(kids.textContent).toBe("For kids · FSK 6");
      expect(kids.disabled).toBe(true);
      expect(age.hidden).toBe(true);
      marks.open({ setId: "rated-adult", fsk: "18" });
      expect(kids.textContent).toBe("FSK 18 · not for kids");

      marks.open({ setId: "unrated-editable" });
      expect(kids.hidden).toBe(true);
      expect(age.hidden).toBe(false);
      expect(age.value).toBe("");
      for (const [chosen, held] of [["6", 6], ["12", 12], ["", null]] as const) {
        age.value = chosen;
        age.fire("change");
        expect(state.kidsAge("unrated-editable")).toBe(held);
      }
      marks.clear();
      age.value = "6";
      age.fire("change");
      expect(state.kidsAge("unrated-editable")).toBeNull();
    });
  ```
  `bun test test/player-features.test.ts` → **FAIL** (`state.isKids is not a function` inside `refreshKids`).
- [ ] **1.13 Implement** — `public/lib/playback/player-library-marks.js`:
  ```js
  /** Watchlist, kids and collection controls for the title currently open. */
  import * as state from "../watch-state.js";
  import { ageLabel, kidsVerdict } from "../age-rating.js";

  /**
   * The older of the two limits a kid can have. A rating above it is for no kid;
   * one at or under it is for some, which is what the locked button says.
   */
  const OLDEST_KIDS_LIMIT = 12;

  export function mountPlayerLibraryMarks() {
    const watchlist = document.getElementById("watchlist");
    const kids = document.getElementById("kids");
    const kidsAge = document.getElementById("kids-age");
    const addTo = document.getElementById("add-to");
    let title = null;
    // Only ever shows what a rating decided; the choice itself is `kidsAge`.
    kids.disabled = true;
    kids.title = "Decided by the title's age rating, not by a mark";

    function refreshWatchlist() {
      const listed = title !== null && state.isWatchlisted(title.setId);
      watchlist.setAttribute("aria-pressed", String(listed));
      watchlist.textContent = listed ? "On the list" : "Watchlist";
    }

    function refreshKids() {
      // A child does not approve titles for themselves; marking is for the
      // grown-ups' profiles. A rated title shows its verdict, locked; an
      // unrated one offers the age it is for kids from, or none.
      const child = state.profile()?.kids === true;
      const verdict = title === null ? "unrated" : kidsVerdict(title, OLDEST_KIDS_LIMIT);
      kids.hidden = child || verdict === "unrated";
      kidsAge.hidden = child || verdict !== "unrated";
      kidsAge.value = title === null ? "" : String(state.kidsAge(title.setId) ?? "");
      const rating = title === null ? null : ageLabel(title);
      kids.setAttribute("aria-pressed", String(verdict === "safe"));
      kids.textContent = verdict === "safe" ? `For kids · ${rating}` : `${rating} · not for kids`;
    }

    function open(set) {
      title = set;
      refreshWatchlist();
      refreshKids();
    }

    watchlist.addEventListener("click", () => {
      if (!title) return;
      state.setWatchlisted(title.setId, !state.isWatchlisted(title.setId));
      refreshWatchlist();
    });
    kidsAge.addEventListener("change", () => {
      if (!title || kidsAge.hidden) return;
      state.setKids(title.setId, kidsAge.value === "" ? null : Number(kidsAge.value));
    });
    addTo.addEventListener("click", () => {
      if (!title) return;
      const lists = state.collections();
      if (lists.length === 0) {
        window.alert("No lists yet. Make one on the Collections shelf.");
        return;
      }
      const names = lists.map((list, index) => `${index + 1}. ${list.name}`).join("\n");
      const answer = window.prompt(`Add to which list?\n\n${names}\n\nNumber:`);
      if (answer === null) return;
      const chosen = lists[Number(answer) - 1];
      if (chosen) state.setInCollection(chosen.id, title.setId, true);
    });

    return { open, clear: () => open(null) };
  }
  ```
  (The `addTo` handler is today's, unchanged; only the kids button's click handler `:49-53` goes.) In `public/index.html` after `:139` insert:
  ```html
            <!-- An unrated title is for kids only from the age a grown-up picks
                 here; a rated one is decided by its rating, shown on the button above. -->
            <select id="kids-age" class="ghost" aria-label="For kids" hidden>
              <option value="">Not for kids</option>
              <option value="6">From 6</option>
              <option value="12">From 12</option>
            </select>
  ```
- [ ] **1.14** `cd web && bun test test/player-features.test.ts test/browser-application.test.ts test/browser-html-player.test.ts` → **PASS** (including 1.1).
- [ ] **1.15** `cd web && bun test && bun run typecheck && bun run lint` → all green; `grep -rn "isKids\|state.kids()\|KIDS_AGE_LIMIT" public test` → no hits.
- [ ] **1.16 Commit** — bump the three manifests by pattern — see phase-08 § Bumping (B1–B4, patch, changelog entry: "A kids profile now sees up to its own age limit; a title can be marked for kids from 6 or from 12"). Then:
  ```bash
  cd /home/andre/Workspace/mediagram
  git add web/public/lib/age-rating.js web/public/lib/watch-state.js web/public/lib/library-session.js \
    web/public/lib/library-session.d.ts web/public/lib/playback/player-library-marks.js web/public/index.html \
    web/test/age-rating.test.ts web/test/kids-marks.test.ts web/test/library-session.test.ts \
    web/test/player-features.test.ts web/test/browser-application.test.ts \
    Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
  git commit -m "feat(web): each kids profile sees up to its own FSK limit, and a Kids mark says from which age; release <next>"
  ```

### Task 2: The kid's own limit, in words

- [ ] **2.1 Failing tests** — `test/shelf-view.test.ts`: import `emptyState` beside the grids and add
  ```ts
  describe("an empty shelf", () => {
    test("a kid is told its own limit, not how to upload", () => {
      expect(emptyState("movies", { kidsLimit: 6 }).textContent).toBe("Nothing rated FSK 6 or under yet.");
      expect(emptyState("movies", { kidsLimit: 12 }).textContent).toBe("Nothing rated FSK 12 or under yet.");
    });
  });
  ```
  `test/browser-application.test.ts` (kids block, uses `kidAt` from 1.1):
  ```ts
  test("an FSK 6 kid's empty shelf names its own limit", async () => {
    catalog = JSON.stringify([rated("Twelve", "12")]);
    kidAt(6);
    await start();
    await env.navigate("#/movies");
    expect(page()).toContain("Nothing rated FSK 6 or under yet.");
  });

  test("Settings names the kid and its own limit", async () => {
    kidAt(6);
    await start();
    await env.navigate("#/settings");
    descendants(env.node("main")).find((node) => node.className.split(" ").includes("tab") && textOf(node) === "Profile")!.fire("click");
    expect(page()).toContain("Viewer · Kids · FSK 6");
  });
  ```
- [ ] **2.2** `cd web && bun test test/shelf-view.test.ts test/browser-application.test.ts -t "limit"` → **FAIL** (shelf-view: `p.append is not a function` — `{ kidsLimit }` is ignored and the upload hint is built; browser: text says `FSK 12` / `· Kids`).
- [ ] **2.3 Implement**
  - `public/lib/catalog/shelf-view.js:98-102` (net 0):
    ```js
     * @param {{kidsLimit?: number|null}} [options] a kids profile is waiting for
     *   ratings at its own limit, not uploads, so it is told that instead
     */
    export function emptyState(section, { kidsLimit = null } = {}) {
      if (kidsLimit !== null) return el("p", "empty", `Nothing rated FSK ${kidsLimit} or under yet.`);
    ```
  - `public/app.js`: add `import { kidsLimitOf } from "./lib/age-rating.js";` after `:28`; delete `:93-94`; `:202` → `return main.append(emptyState("movies", { kidsLimit: kidsLimitOf(state.profile()) }));`; `:241` → same argument; `:272` → `kidsLimit: kidsLimitOf(state.profile()), play: (set) => play(set), openFilm, reel: reelButton(),`.
  - `public/lib/catalog/department-pages.js:31` `kids: boolean` → `kidsLimit: number|null`; `:39,:83,:129` `{ kids: cx.kids }` → `{ kidsLimit: cx.kidsLimit }`.
  - `public/lib/catalog/settings-page.js`: `import { kidsLimitOf } from "../age-rating.js";` after `:14`; `:42` type `{name: string, kids?: boolean, kidsAge?: number|null}|null`; `:103` → ``profile ? `${profile.name}${profile.kids ? ` · Kids · FSK ${kidsLimitOf(profile)}` : ""}` : "Nobody chosen"``.
- [ ] **2.4** Same run as 2.2 → **PASS**. `wc -l public/app.js public/lib/catalog/shelf-view.js` → **741**, **285**.
- [ ] **2.5** `test/code-standards.test.ts`: `"public/app.js": 742` → `741`; append to the doc comment: "Lowered 2026-09-28 for `app.js`, once a kid's own age limit replaced the one-flag kids check."
- [ ] **2.6** `cd web && bun test && bun run typecheck && bun run lint` → green; `grep -rn "FSK 12 or under" public` → no hit.
- [ ] **2.7 Commit** — bump by pattern (phase-08 § Bumping, patch, changelog), then `git add` the files above + `web/test/shelf-view.test.ts web/test/browser-application.test.ts web/test/code-standards.test.ts` + manifests + changelog;
  `git commit -m "feat(web): an empty shelf and Settings name the kid's own limit; release <next>"`.

### Task 3: A client for the profile routes

- [ ] **3.1 Failing tests** — create `test/profile-api.test.ts` (fixtures above):
  ```ts
  import { afterEach, beforeEach, describe, expect, test } from "bun:test";
  import { readFileSync } from "node:fs";
  import { join } from "node:path";
  import * as api from "../public/lib/profile-api.js";
  import * as state from "../public/lib/watch-state.js";
  import { browserEnvironment } from "./support/player-environment";

  /* ANDRE, MAJA, TIM, LEA as in the phase preamble */

  let env: ReturnType<typeof browserEnvironment>;
  let household: object[];
  beforeEach(async () => {
    env = browserEnvironment();
    household = [ANDRE, MAJA, TIM, LEA];
    env.respondWith(async (url, options) => {
      if (url === "/api/profiles" && !options?.method) return Response.json({ remembers: true, profiles: household });
      if (url.endsWith("/state")) return Response.json({});
      return new Response(null, { status: 204 });
    });
    await state.loadProfiles();
  });
  afterEach(async () => { await state.useProfile(null); env.restore(); });

  const sent = () => env.requests.filter((request) => request.options?.method)
    .map((request) => [request.options!.method, request.url, JSON.parse(String(request.options!.body))]);

  const CALLS = [
    { name: "unlock", call: () => api.unlock("andre", "1234"), expected: ["POST", "/api/profiles/andre/unlock", { pin: "1234" }] },
    { name: "claimAdmin", call: () => api.claimAdmin("maja", "4321"), expected: ["POST", "/api/profiles/maja/claim-admin", { pin: "4321" }] },
    { name: "setPin", call: () => api.setPin("andre", "1234", "maja", "5555"), expected: ["PUT", "/api/profiles/maja/pin", { actorId: "andre", pin: "1234", newPin: "5555" }] },
    { name: "create a kid", call: () => api.create("maja", "4321", { name: "Ben", kids: true, kidsAge: 6 }), expected: ["POST", "/api/profiles", { actorId: "maja", pin: "4321", name: "Ben", kids: true, kidsAge: 6 }] },
    { name: "create a grown-up", call: () => api.create("andre", "1234", { name: "Oma", kids: false, newPin: "2468" }), expected: ["POST", "/api/profiles", { actorId: "andre", pin: "1234", name: "Oma", kids: false, newPin: "2468" }] },
    { name: "createFirst, with no actor", call: () => api.createFirst("andre", "1234"), expected: ["POST", "/api/profiles", { name: "andre", newPin: "1234" }] },
    { name: "remove", call: () => api.remove("maja", "4321", "tim"), expected: ["DELETE", "/api/profiles/tim", { actorId: "maja", pin: "4321" }] },
    { name: "setKidsAge", call: () => api.setKidsAge("maja", "4321", "tim", 12), expected: ["PUT", "/api/profiles/tim/kids-age", { actorId: "maja", pin: "4321", age: 12 }] },
    { name: "prove, with a PIN", call: () => api.prove(ANDRE, "1234"), expected: ["POST", "/api/profiles/andre/unlock", { pin: "1234" }] },
    { name: "prove, with none yet", call: () => api.prove({ ...MAJA, hasPin: false }, "4321"), expected: ["PUT", "/api/profiles/maja/pin", { actorId: "maja", pin: "", newPin: "4321" }] },
  ];

  describe("each call is the server's route, the PIN in the body", () => {
    for (const { name, call, expected } of CALLS) {
      test(name, async () => {
        expect(await call()).toEqual({ ok: true });
        expect(sent()).toEqual([expected]);
      });
    }
  });

  describe("a refusal says why", () => {
    test("the server's reason, and a wait's seconds", async () => {
      env.respondWith(async () => Response.json({ reason: "wait", retryAfter: 42 }, { status: 429, headers: { "retry-after": "42" } }));
      expect(await api.unlock("andre", "0000")).toEqual({ ok: false, reason: "wait", retryAfter: 42 });
      env.respondWith(async () => Response.json({ reason: "wrong-pin" }, { status: 403 }));
      expect(await api.unlock("andre", "0000")).toEqual({ ok: false, reason: "wrong-pin" });
    });
    const unnamed = [
      ["a network failure", async () => { throw new Error("offline"); }],
      ["a bodiless refusal", async () => new Response(null, { status: 403 })],
      ["a reason nobody named", async () => Response.json({ reason: "nope" }, { status: 400 })],
    ] as const;
    for (const [name, answer] of unnamed) {
      test(`${name} has no reason, and changes nothing here`, async () => {
        env.respondWith(answer);
        expect((await api.remove("andre", "1234", "maja")).reason).toBeNull();
        expect(state.profiles()).toHaveLength(4);
      });
    }
  });

  describe("after a change", () => {
    test("the profile list is read again", async () => {
      household = [ANDRE, MAJA, LEA];
      await api.remove("maja", "4321", "tim");
      expect(state.profiles().map((entry) => entry.id)).toEqual(["andre", "maja", "lea"]);
    });
    test("a device on a profile that went with its parent lets go of it", async () => {
      await state.useProfile("tim");
      household = [ANDRE, LEA];
      await api.remove("andre", "1234", "maja");
      expect(state.profileId()).toBeNull();
    });
    test("unlocking changes nothing, so nothing is read again", async () => {
      await api.unlock("andre", "1234");
      expect(env.requests.filter((request) => request.url === "/api/profiles" && !request.options?.method)).toHaveLength(1);
    });
  });

  describe("what each grown-up may manage", () => {
    test("the admin: every other grown-up, and the kids that are its own — a kid with no parent included", () => {
      const view = api.manageable([ANDRE, MAJA, TIM, LEA], "andre");
      expect(view.grownUps.map((entry: { id: string }) => entry.id)).toEqual(["maja"]);
      expect(view.kids.map((entry: { id: string }) => entry.id)).toEqual(["lea"]);
    });
    test("a parent: its own kids, no grown-ups", () => {
      const view = api.manageable([ANDRE, MAJA, TIM, LEA], "maja");
      expect(view.grownUps).toEqual([]);
      expect(view.kids.map((entry: { id: string }) => entry.id)).toEqual(["tim"]);
    });
    test("a kid, or nobody, manages nothing", () => {
      expect(api.manageable([ANDRE, MAJA, TIM, LEA], "tim").actor).toBeNull();
      expect(api.manageable([ANDRE, MAJA, TIM, LEA], "gone").actor).toBeNull();
    });
    test("a kid whose parent is not here belongs to the admin", () => {
      const ida = { ...TIM, id: "ida", parentId: "removed-elsewhere" };
      expect(api.ownerOf([ANDRE, MAJA, ida], ida)).toBe("andre");
    });
  });

  /** Whether the panel offers `action` on `targetId` — which the server's rule must agree with. */
  function offered(profiles: object[], actorId: string, action: string, targetId: string | null) {
    const { actor, grownUps, kids } = api.manageable(profiles, actorId);
    const has = (list: Array<{ id: string }>) => list.some((entry) => entry.id === targetId);
    if (!actor) return false;
    if (action === "create-grown-up") return actor.admin === true;
    if (action === "create-kid") return true;
    if (action === "remove") return has(grownUps) || has(kids);
    if (action === "set-pin") return targetId === actorId || has(grownUps);
    if (action === "set-kids-age") return has(kids);
    throw new Error(`Unknown action ${action}`);
  }

  const RULES = JSON.parse(readFileSync(join(import.meta.dir, "fixtures", "watch-state", "profile-rules.json"), "utf8")) as
    Array<{ name: string; profiles: object[]; actorId: string; action: string; targetId: string | null; expect: boolean }>;
  describe("the panel offers what the server's rule allows", () => {
    for (const rule of RULES) {
      test(rule.name, () => expect(offered(rule.profiles, rule.actorId, rule.action, rule.targetId ?? null)).toBe(rule.expect));
    }
  });
  ```
- [ ] **3.2** `cd web && bun test test/profile-api.test.ts` → **FAIL**: `Cannot find module '../public/lib/profile-api.js'`.
- [ ] **3.3 Implement** `public/lib/profile-api.js`:
  ```js
  /**
   * Managing profiles: the calls behind the picker's PIN prompt and the
   * Manage profiles panel, and what each grown-up may manage.
   *
   * Every call answers an outcome and never throws: `{ ok: true }`, or
   * `{ ok: false, reason, retryAfter }` — `reason` one of the server's
   * (`invalid`, `not-found`, `wait`, `no-pin`, `wrong-pin`, `not-allowed`;
   * `retryAfter` in seconds with `wait`), or `null` when no answer named one:
   * the network failed, or the request was turned away before the rule saw it
   * (a cross-site write is a bare 403).
   *
   * Nothing here is a check — the server decides every one of these. A change
   * that goes through reads the profile list again, so the picker, the panel
   * and the header all see it; and if it took away the profile this device is
   * on (with its parent, perhaps), the device lets go of it rather than keep
   * writing to nobody — or, on a kid's screen, fall back to showing everything.
   */

  import { loadProfiles, profile, profileId, useProfile } from "./watch-state.js";

  const REASONS = new Set(["invalid", "not-found", "wait", "no-pin", "wrong-pin", "not-allowed"]);

  /** One request. The PIN travels in the body, never in the address, so no log or history keeps it. */
  async function call(method, path, body) {
    try {
      const response = await fetch(path, {
        method,
        headers: { "content-type": "application/json" },
        body: JSON.stringify(body),
      });
      if (response.ok) return { ok: true };
      const said = await response.json();
      const retryAfter = Number(said?.retryAfter) || undefined;
      return { ok: false, reason: REASONS.has(said?.reason) ? said.reason : null, ...(retryAfter ? { retryAfter } : {}) };
    } catch {
      return { ok: false, reason: null };
    }
  }

  /** A call that changes who there is: on success the list is read again. */
  async function change(method, path, body) {
    const outcome = await call(method, path, body);
    if (outcome.ok) {
      await loadProfiles();
      if (profileId() !== null && profile() === null) await useProfile(null);
    }
    return outcome;
  }

  const at = (id, rest = "") => `/api/profiles/${encodeURIComponent(id)}${rest}`;

  /** Checks a PIN for entering `id` from the picker. Changes nothing. */
  export const unlock = (id, pin) => call("POST", at(id, "/unlock"), { pin });
  /** Makes `id` the household's admin; one with no PIN yet takes `pin` as its PIN. */
  export const claimAdmin = (id, pin) => change("POST", at(id, "/claim-admin"), { pin });
  /** Gives `id` the PIN `newPin`. A grown-up with none yet sets its own with `pin` empty. */
  export const setPin = (actorId, pin, id, newPin) => change("PUT", at(id, "/pin"), { actorId, pin, newPin });
  /** A grown-up (`{ name, kids: false, newPin }`) or a kid (`{ name, kids: true, kidsAge }`). */
  export const create = (actorId, pin, fields) => change("POST", "/api/profiles", { actorId, pin, ...fields });
  /**
   * The first grown-up on a player that knows none: no actor and no current
   * PIN, and it runs the household. Refused once any grown-up exists here.
   */
  export const createFirst = (name, newPin) => change("POST", "/api/profiles", { name, newPin });
  /** Removes `id` and everything they watched; a grown-up's kids go with them. */
  export const remove = (actorId, pin, id) => change("DELETE", at(id), { actorId, pin });
  /** Sets the kid `id`'s limit to `age`, 6 or 12. */
  export const setKidsAge = (actorId, pin, id, age) => change("PUT", at(id, "/kids-age"), { actorId, pin, age });

  /**
   * A grown-up saying who they are: their PIN, or — for one from before PINs —
   * the one they choose now, which becomes theirs.
   */
  export const prove = (entry, pin) => (entry.hasPin ? unlock(entry.id, pin) : setPin(entry.id, "", entry.id, pin));

  /** Whose kid this is: its parent while that is a grown-up here, otherwise the admin's, otherwise nobody's. */
  export function ownerOf(profiles, kid) {
    if (profiles.some((entry) => entry.id === kid.parentId && !entry.kids)) return kid.parentId;
    return profiles.find((entry) => entry.admin)?.id ?? null;
  }

  /**
   * What `actorId` may manage, as Manage profiles draws it: every other
   * grown-up when the actor is the admin, and the kids that are the actor's own.
   * `actor` is `null` for a kid or for nobody, who manage nothing.
   */
  export function manageable(profiles, actorId) {
    const actor = profiles.find((entry) => entry.id === actorId && !entry.kids) ?? null;
    return {
      actor,
      grownUps: actor?.admin ? profiles.filter((entry) => !entry.kids && entry.id !== actorId) : [],
      kids: actor ? profiles.filter((entry) => entry.kids && ownerOf(profiles, entry) === actorId) : [],
    };
  }
  ```
- [ ] **3.4** `cd web && bun test test/profile-api.test.ts` → **PASS** (every `profile-rules.json` case included). If a fixture case fails, the panel and the server disagree: fix `manageable`/`ownerOf`, never the fixture (it is phase 02's).
- [ ] **3.5** Suite + typecheck + lint green. **Commit** — bump by pattern (phase-08 § Bumping), `git add web/public/lib/profile-api.js web/test/profile-api.test.ts` + manifests + changelog;
  `git commit -m "feat(web): a client for the profile management routes; release <next>"`.

### Task 4: One PIN prompt

- [ ] **4.1 Test helpers** — append to `test/support/browser-application.ts`:
  ```ts
  /** The first node under `root` whose class list has `className`. */
  export function byClass(root: Node, className: string): Node {
    const found = descendants(root).find((node) => node.className.split(" ").includes(className));
    if (!found) throw new Error(`Missing .${className}`);
    return found;
  }

  /** The button under `root` that says `label`. */
  export function buttonNamed(root: Node, label: string): Node {
    const found = descendants(root).find((node) => node.tagName === "BUTTON" && node.textContent === label);
    if (!found) throw new Error(`Missing button: ${label}`);
    return found;
  }

  /** Types `pins` into the open PIN prompt's fields in order (the first again for any left over) and submits. */
  export async function answerPin(root: Node, ...pins: string[]) {
    const prompt = byClass(root, "pin-prompt");
    descendants(prompt).filter((node) => node.tagName === "INPUT").forEach((field, at) => { field.value = pins[at] ?? pins[0] ?? ""; });
    descendants(prompt).find((node) => node.tagName === "FORM")!.fire("submit");
    await settle();
  }
  ```
- [ ] **4.2 Failing tests** — create `test/pin-prompt.test.ts`:
  ```ts
  import { afterEach, beforeEach, describe, expect, test } from "bun:test";
  import { askGrownUp, askPin, pinProblem, refusalText } from "../public/lib/pin-prompt.js";
  import { answerPin, buttonNamed, byClass, descendants, textOf } from "./support/browser-application";
  import { browserEnvironment, Node } from "./support/player-environment";

  let env: ReturnType<typeof browserEnvironment>;
  beforeEach(() => { env = browserEnvironment(); });
  afterEach(() => env.restore());

  const fields = (root: Node) => descendants(root).filter((node) => node.tagName === "INPUT");
  const open = (root: Node) => descendants(root).some((node) => node.className.split(" ").includes("pin-prompt"));
  type Outcome = { ok: boolean; reason?: string | null; retryAfter?: number };

  describe("what is said before and after sending", () => {
    test("a PIN is four digits, and a new one must be typed the same twice", () => {
      expect(pinProblem("1234", "", false)).toBeNull();
      for (const typed of ["", "123", "12345", "12a4", " 1234"]) expect(pinProblem(typed, "", false)).toBe("A PIN is four digits.");
      expect(pinProblem("1234", "1243", true)).toBe("The two PINs are not the same.");
      expect(pinProblem("1234", "1234", true)).toBeNull();
    });
    test("each refusal in words, the wait in seconds", () => {
      expect(refusalText({ reason: "wrong-pin" })).toBe("Wrong PIN.");
      expect(refusalText({ reason: "wait", retryAfter: 42 })).toBe("Too many wrong PINs. Try again in 42 s.");
      expect(refusalText({ reason: "not-allowed" })).toBe("That is not allowed.");
      expect(refusalText({ reason: "no-pin" })).toBe("This profile has no PIN yet. Choose it again to set one.");
      expect(refusalText({ reason: "not-found" })).toBe("That profile is not here any more.");
      expect(refusalText({ reason: "invalid" })).toBe("That was not accepted. Check the name and the PIN.");
      expect(refusalText({ reason: null })).toBe("That did not go through. Please try again.");
    });
  });

  describe("the dialog", () => {
    test("a masked, numeric field the browser is asked not to fill in", () => {
      const root = new Node();
      void askPin(root, { title: "andre’s PIN", send: async () => ({ ok: true }) });
      expect(byClass(root, "pin-prompt").open).toBe(true);
      expect(fields(root)).toHaveLength(1);
      const field = fields(root)[0]!;
      expect(field.getAttribute("type")).toBe("password");
      expect(field.getAttribute("inputmode")).toBe("numeric");
      expect(field.getAttribute("autocomplete")).toBe("off");
      expect(field.getAttribute("maxlength")).toBe("4");
      expect(textOf(root)).toContain("andre’s PIN");
    });
    test("a PIN of the wrong shape is said at once and not sent", async () => {
      const root = new Node();
      const sent: string[] = [];
      void askPin(root, { title: "PIN", send: async (pin: string) => { sent.push(pin); return { ok: true }; } });
      await answerPin(root, "12");
      expect(textOf(byClass(root, "pin-message"))).toBe("A PIN is four digits.");
      expect(sent).toEqual([]);
    });
    test("a refusal is shown, the field emptied, another try allowed", async () => {
      const root = new Node();
      const answers: Outcome[] = [{ ok: false, reason: "wrong-pin" }, { ok: true }];
      const asked = askPin(root, { title: "PIN", send: async () => answers.shift()! });
      await answerPin(root, "1111");
      expect(textOf(byClass(root, "pin-message"))).toBe("Wrong PIN.");
      expect(fields(root)[0]!.value).toBe("");
      expect(open(root)).toBe(true);
      await answerPin(root, "1234");
      expect(await asked).toBe("1234");
      expect(open(root)).toBe(false);
    });
    test("a waiting player's answer is shown in seconds", async () => {
      const root = new Node();
      void askPin(root, { title: "PIN", send: async () => ({ ok: false, reason: "wait", retryAfter: 60 }) });
      await answerPin(root, "1234");
      expect(textOf(byClass(root, "pin-message"))).toBe("Too many wrong PINs. Try again in 60 s.");
    });
    test("Cancel gives up", async () => {
      const root = new Node();
      const asked = askPin(root, { title: "PIN", send: async () => ({ ok: true }) });
      buttonNamed(root, "Cancel").fire("click");
      expect(await asked).toBeNull();
      expect(root.children).toHaveLength(0);
    });
    test("setting a PIN asks twice; two different ones are not sent", async () => {
      const root = new Node();
      const sent: string[] = [];
      const asked = askPin(root, { title: "New", confirm: true, send: async (pin: string) => { sent.push(pin); return { ok: true }; } });
      expect(fields(root)).toHaveLength(2);
      await answerPin(root, "1234", "4321");
      expect(sent).toEqual([]);
      await answerPin(root, "1234", "1234");
      expect(await asked).toBe("1234");
      expect(sent).toEqual(["1234"]);
    });
    test("a grown-up with a PIN is asked it; one without chooses one", () => {
      const root = new Node();
      void askGrownUp(root, { name: "andre", hasPin: true }, async () => ({ ok: true }));
      expect(fields(root)).toHaveLength(1);
      expect(textOf(root)).toContain("andre’s PIN");
      const other = new Node();
      void askGrownUp(other, { name: "Maja", hasPin: false }, async () => ({ ok: true }));
      expect(fields(other)).toHaveLength(2);
      expect(textOf(other)).toContain("Choose a PIN for Maja");
    });
  });
  ```
- [ ] **4.3** `cd web && bun test test/pin-prompt.test.ts` → **FAIL**: `Cannot find module '../public/lib/pin-prompt.js'`.
- [ ] **4.4 Implement** `public/lib/pin-prompt.js`:
  ```js
  /**
   * The one PIN dialog, for the picker and for Manage profiles.
   *
   * Four digits, in a masked field the browser is asked not to fill in or
   * remember. A PIN keeps a child out of a grown-up's profile and out of the
   * household's management; it is not a login and does not pretend to be one —
   * the kids filter runs in this browser, where developer tools reach it. This
   * checks only the shape, so a mistyped PIN is said at once; whether it is the
   * right one is the server's to say, which also makes the whole player wait
   * after five wrong ones.
   */

  import { el } from "./dom.js";

  const FOUR_DIGITS = /^[0-9]{4}$/;

  /** What is wrong with what was typed, or `null` when it can be sent. */
  export function pinProblem(pin, again, confirm = false) {
    if (!FOUR_DIGITS.test(pin)) return "A PIN is four digits.";
    if (confirm && pin !== again) return "The two PINs are not the same.";
    return null;
  }

  const REFUSALS = {
    invalid: "That was not accepted. Check the name and the PIN.",
    "not-found": "That profile is not here any more.",
    "no-pin": "This profile has no PIN yet. Choose it again to set one.",
    "wrong-pin": "Wrong PIN.",
    // One wording for every refusal by the rule: a manage action outside the
    // viewer's role, and a first profile when a grown-up has arrived meanwhile.
    "not-allowed": "That is not allowed.",
  };

  /** What to tell the viewer about an outcome from `profile-api.js` that did not go through. */
  export function refusalText({ reason, retryAfter }) {
    if (reason === "wait") return `Too many wrong PINs. Try again in ${retryAfter} s.`;
    return REFUSALS[reason] ?? "That did not go through. Please try again.";
  }

  function pinField(label) {
    const field = el("input");
    field.setAttribute("type", "password");
    field.setAttribute("maxlength", "4");
    field.setAttribute("inputmode", "numeric");
    field.setAttribute("autocomplete", "off");
    field.setAttribute("aria-label", label);
    return field;
  }

  /**
   * Asks for a PIN until `send` accepts one or the viewer gives up.
   * @param {HTMLElement} parent where the dialog is attached; it removes itself
   * @param {{ title: string, confirm?: boolean,
   *   send: (pin: string) => Promise<{ ok: boolean, reason?: string|null, retryAfter?: number }> }} options
   *   `confirm` asks twice, for a PIN being set
   * @returns {Promise<string|null>} the PIN `send` accepted, or `null`
   */
  export function askPin(parent, { title, confirm = false, send }) {
    return new Promise((resolve) => {
      const dialog = el("dialog", "settings-dialog pin-prompt");
      dialog.setAttribute("aria-label", title);
      const form = el("form");
      const pin = pinField(confirm ? "New PIN" : "PIN");
      const again = pinField("The new PIN again");
      const message = el("p", "pin-message");
      message.setAttribute("role", "alert");
      const cancel = el("button", "pill pill-line", "Cancel");
      cancel.type = "button";
      const ok = el("button", "pill pill-solid", "OK");
      ok.type = "submit";
      let accepted = null;

      cancel.addEventListener("click", () => dialog.close());
      // Escape closes a modal dialog as well; either way the answer is what was accepted.
      dialog.addEventListener("close", () => {
        dialog.remove();
        resolve(accepted);
      });
      form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (ok.disabled) return;
        const problem = pinProblem(pin.value, again.value, confirm);
        if (problem) {
          message.textContent = problem;
          return;
        }
        ok.disabled = true;
        const outcome = await send(pin.value);
        ok.disabled = false;
        if (outcome.ok) {
          accepted = pin.value;
          dialog.close();
          return;
        }
        message.textContent = refusalText(outcome);
        pin.value = "";
        again.value = "";
        pin.focus();
      });

      const actions = el("div", "settings-actions");
      actions.append(cancel, ok);
      form.append(el("h2", null, title), pin, ...(confirm ? [again] : []), message, actions);
      dialog.append(form);
      parent.append(dialog);
      dialog.showModal();
      pin.focus();
    });
  }

  /** A grown-up's PIN — or, for one from before PINs, a new one, asked twice — handed to `send`. */
  export function askGrownUp(parent, entry, send) {
    return askPin(parent, entry.hasPin
      ? { title: `${entry.name}’s PIN`, send }
      : { title: `Choose a PIN for ${entry.name}`, confirm: true, send });
  }
  ```
- [ ] **4.5** `cd web && bun test test/pin-prompt.test.ts` → **PASS**.
- [ ] **4.6 CSS** — append to `public/styles/library-controls.css` before the `@media` block:
  ```css
  .pin-prompt form { display: flex; flex-direction: column; gap: 12px; }
  .pin-prompt h2 { margin: 0 0 4px; font: 500 1.22rem/1.25 var(--display); }
  .pin-prompt input { min-height: 48px; padding: 8px 12px; border: 1px solid var(--rule); border-radius: var(--radius); background: var(--surface); color: var(--ink); font: 500 1.5rem/1 var(--text); letter-spacing: 0.5em; text-align: center; }
  .pin-message { min-height: 1.4em; margin: 0; color: var(--warn); font-size: 0.875rem; }
  ```
  (`.settings-dialog`, `.settings-actions` and pills already exist; focus ring is global `theme.css:169`.)
- [ ] **4.7** Suite + typecheck + lint green. **Commit** — bump by pattern (phase-08 § Bumping), `git add web/public/lib/pin-prompt.js web/public/styles/library-controls.css web/test/pin-prompt.test.ts web/test/support/browser-application.ts` + manifests + changelog;
  `git commit -m "feat(web): one PIN prompt for the picker and profile management; release <next>"`.

### Task 5: The Manage profiles panel

- [ ] **5.1 Failing tests** — create `test/profile-manage.test.ts` (fixtures above; `MAJA.hasPin` is `true`):
  ```ts
  import { afterEach, beforeEach, expect, test } from "bun:test";
  import { openManage } from "../public/lib/profile-manage.js";
  import * as state from "../public/lib/watch-state.js";
  import { answerPin, buttonNamed, byClass, descendants, textOf } from "./support/browser-application";
  import { browserEnvironment, Node, settle } from "./support/player-environment";

  /* ANDRE, MAJA, TIM, LEA */

  let env: ReturnType<typeof browserEnvironment>;
  let household: object[];
  let refusal: { status: number; body: object } | null;
  let questions: string[];
  beforeEach(async () => {
    env = browserEnvironment();
    household = [ANDRE, MAJA, TIM, LEA];
    refusal = null;
    questions = [];
    Object.assign(env.window, { confirm: (question: string) => { questions.push(question); return true; } });
    env.respondWith(async (url, options) => {
      if (url === "/api/profiles" && !options?.method) return Response.json({ remembers: true, profiles: household });
      if (refusal) return Response.json(refusal.body, { status: refusal.status });
      return new Response(null, { status: 204 });
    });
    await state.loadProfiles();
  });
  afterEach(() => env.restore());

  const sent = () => env.requests.filter((request) => request.options?.method)
    .map((request) => [request.options!.method, request.url, JSON.parse(String(request.options!.body))]);
  const rowOf = (root: Node, name: string) => descendants(root).find((node) => node.className === "manage-row" && textOf(node).startsWith(name));
  const limitOf = (root: Node, name: string) => descendants(root).find((node) => node.tagName === "SELECT" && node.getAttribute("aria-label") === `Age limit for ${name}`)!;
  const formOf = (root: Node, label: string) => descendants(root).find((node) => node.tagName === "FORM" && textOf(node).includes(label))!;

  /** Opens the panel as `name`, answering the PIN prompt with `pins`. */
  async function manageAs(name: string, ...pins: string[]) {
    const root = new Node();
    let closes = 0;
    openManage(root, () => { closes++; });
    buttonNamed(root, name).fire("click");
    await answerPin(root, ...pins);
    return { root, closes: () => closes };
  }

  test("only grown-ups are offered as who you are", () => {
    const root = new Node();
    openManage(root, () => {});
    expect(descendants(byClass(root, "who-ask")).filter((node) => node.tagName === "BUTTON").map((node) => node.textContent)).toEqual(["andre", "Maja"]);
  });

  test("a parent sees its own kids and its own PIN — no grown-ups, no other parent's kids", async () => {
    const { root } = await manageAs("Maja", "4321");
    expect(sent()).toEqual([["POST", "/api/profiles/maja/unlock", { pin: "4321" }]]);
    expect(rowOf(root, "Tim")).toBeDefined();
    expect(rowOf(root, "Lea")).toBeUndefined();
    expect(textOf(root)).not.toContain("Grown-ups");
    expect(textOf(root)).not.toContain("Add a grown-up");
    expect(buttonNamed(root, "Add a kid")).toBeDefined();
    expect(buttonNamed(root, "Change your PIN")).toBeDefined();
  });

  test("the admin sees every other grown-up and its own kids, and no way to remove itself", async () => {
    const { root } = await manageAs("andre", "1234");
    expect(textOf(rowOf(root, "Maja")!)).toContain("Reset PIN");
    expect(textOf(rowOf(root, "Maja")!)).toContain("Remove");
    expect(rowOf(root, "Lea")).toBeDefined();
    expect(rowOf(root, "Tim")).toBeUndefined();
    expect(rowOf(root, "andre")).toBeUndefined();
    expect(buttonNamed(root, "Add a grown-up")).toBeDefined();
  });

  test("a kid's limit changes with the PIN given once", async () => {
    const { root } = await manageAs("Maja", "4321");
    expect(limitOf(root, "Tim").value).toBe("6");
    limitOf(root, "Tim").value = "12";
    limitOf(root, "Tim").fire("change");
    await settle();
    expect(sent().at(-1)).toEqual(["PUT", "/api/profiles/tim/kids-age", { actorId: "maja", pin: "4321", age: 12 }]);
  });

  test("a new kid is added under the parent, with the limit chosen", async () => {
    const { root } = await manageAs("Maja", "4321");
    const form = formOf(root, "Add a kid");
    descendants(form).find((node) => node.tagName === "INPUT")!.value = "Ben";
    descendants(form).find((node) => node.tagName === "SELECT")!.value = "12";
    form.fire("submit");
    await settle();
    expect(sent().at(-1)).toEqual(["POST", "/api/profiles", { actorId: "maja", pin: "4321", name: "Ben", kids: true, kidsAge: 12 }]);
  });

  test("a new grown-up is given a first PIN, asked twice", async () => {
    const { root } = await manageAs("andre", "1234");
    const form = formOf(root, "Add a grown-up");
    descendants(form).find((node) => node.tagName === "INPUT")!.value = "Oma";
    form.fire("submit");
    await settle();
    await answerPin(root, "2468", "2468");
    expect(sent().at(-1)).toEqual(["POST", "/api/profiles", { actorId: "andre", pin: "1234", name: "Oma", kids: false, newPin: "2468" }]);
  });

  test("removing asks first; a refusal is shown and the profile stays", async () => {
    const { root } = await manageAs("Maja", "4321");
    refusal = { status: 403, body: { reason: "not-allowed" } };
    buttonNamed(rowOf(root, "Tim")!, "Remove").fire("click");
    await settle();
    expect(questions).toEqual(["Remove Tim and everything they have watched?"]);
    expect(sent().at(-1)).toEqual(["DELETE", "/api/profiles/tim", { actorId: "maja", pin: "4321" }]);
    expect(textOf(byClass(root, "pin-message"))).toBe("That is not allowed.");
    expect(rowOf(root, "Tim")).toBeDefined();
  });

  test("removing a grown-up says their kids go too, and the panel follows the list", async () => {
    const { root } = await manageAs("andre", "1234");
    household = [ANDRE, LEA];
    buttonNamed(rowOf(root, "Maja")!, "Remove").fire("click");
    await settle();
    expect(questions).toEqual(["Remove Maja, their kids, and everything they have watched?"]);
    expect(rowOf(root, "Maja")).toBeUndefined();
  });

  test("a wrong PIN on a change asks who you are again", async () => {
    const { root } = await manageAs("Maja", "4321");
    refusal = { status: 403, body: { reason: "wrong-pin" } };
    limitOf(root, "Tim").value = "12";
    limitOf(root, "Tim").fire("change");
    await settle();
    expect(textOf(root)).toContain("Wrong PIN.");
    expect(textOf(root)).toContain("Who are you?");
  });

  test("changing your own PIN changes the one sent after it", async () => {
    const { root } = await manageAs("Maja", "4321");
    buttonNamed(root, "Change your PIN").fire("click");
    await answerPin(root, "9999", "9999");
    expect(sent().at(-1)).toEqual(["PUT", "/api/profiles/maja/pin", { actorId: "maja", pin: "4321", newPin: "9999" }]);
    limitOf(root, "Tim").value = "12";
    limitOf(root, "Tim").fire("change");
    await settle();
    expect(sent().at(-1)?.[2]).toMatchObject({ pin: "9999" });
  });

  test("a grown-up with no PIN sets one before managing", async () => {
    household = [ANDRE, { ...MAJA, hasPin: false }, TIM, LEA];
    await state.loadProfiles();
    household = [ANDRE, MAJA, TIM, LEA];
    const { root } = await manageAs("Maja", "4321", "4321");
    expect(sent()).toEqual([["PUT", "/api/profiles/maja/pin", { actorId: "maja", pin: "", newPin: "4321" }]]);
    expect(rowOf(root, "Tim")).toBeDefined();
  });

  test("closing drops the PIN: the next opening asks who you are", async () => {
    const { root, closes } = await manageAs("Maja", "4321");
    buttonNamed(root, "Done").fire("click");
    expect(closes()).toBe(1);
    expect(descendants(root).some((node) => node.className.includes("who-manage"))).toBe(false);
    openManage(root, () => {});
    expect(textOf(root)).toContain("Who are you?");
    expect(rowOf(root, "Tim")).toBeUndefined();
  });
  ```
- [ ] **5.2** `cd web && bun test test/profile-manage.test.ts` → **FAIL**: `Cannot find module '../public/lib/profile-manage.js'`.
- [ ] **5.3 Implement** `public/lib/profile-manage.js`:
  ```js
  /**
   * Manage profiles: who there is, a kid's age limit, and grown-ups' PINs.
   *
   * Opened from the picker only — entering a profile never opens it, so a
   * device left on a grown-up's profile does not hand a child these controls.
   * A grown-up says who they are and gives their PIN once; it is held in this
   * panel while it is open, sent with every change, and dropped when it closes.
   *
   * What the panel offers follows the household's rule: the admin adds,
   * removes and resets grown-ups; every grown-up manages their own kids and
   * their own PIN. The server decides all the same — something offered here by
   * mistake is still refused there, and the refusal is shown as it comes.
   */

  import { el } from "./dom.js";
  import * as state from "./watch-state.js";
  import * as api from "./profile-api.js";
  import { kidsLimitOf } from "./age-rating.js";
  import { askGrownUp, askPin, refusalText } from "./pin-prompt.js";

  /** The two limits a kid can have. A new kid starts at the stricter; its parent raises it. */
  const LIMITS = [6, 12];

  function pill(label, onClick) {
    const button = el("button", "pill pill-line", label);
    button.type = "button";
    button.addEventListener("click", onClick);
    return button;
  }

  /** A native choice between the two limits. */
  function limitChoice(value, label) {
    const choice = el("select");
    choice.setAttribute("aria-label", label);
    for (const age of LIMITS) {
      const option = el("option", null, `FSK ${age}`);
      option.value = String(age);
      choice.append(option);
    }
    choice.value = String(value);
    return choice;
  }

  function section(title, ...nodes) {
    const box = el("section", "manage-section");
    box.append(el("h2", null, title), ...nodes);
    return box;
  }

  function row(name, ...controls) {
    const line = el("div", "manage-row");
    line.append(el("span", null, name), ...controls);
    return line;
  }

  /** A name, whatever `extra` asks beside it, and the button that adds. The picker's first-profile form is one too. */
  export function addForm(label, extra, submit) {
    const form = el("form", "who-new");
    const name = el("input");
    name.maxLength = 120;
    name.placeholder = "Name";
    name.setAttribute("aria-label", `${label}: name`);
    const add = el("button", "who-create", label);
    add.type = "submit";
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      if (name.value.trim() !== "") void submit(name.value.trim());
    });
    form.append(name, ...extra, add);
    return form;
  }

  /**
   * Covers `parent` — the picker — with the panel.
   * @param {HTMLElement} parent
   * @param {() => void} onClose called once the panel is gone
   */
  export function openManage(parent, onClose) {
    const screen = el("div", "who who-manage");
    const card = el("div", "who-card");
    const body = el("div");
    const message = el("p", "pin-message");
    message.setAttribute("role", "alert");
    /** `{ actorId, pin }` once a grown-up has said who they are. */
    let session = null;

    const done = pill("Done", () => {
      session = null;
      screen.remove();
      onClose();
    });

    /** Shows how a change went. A wrong PIN means the one held is no longer theirs — ask again rather than spend the household's tries. */
    function report(outcome) {
      message.textContent = outcome.ok ? "" : refusalText(outcome);
      if (outcome.reason === "wrong-pin") session = null;
      draw();
    }

    function draw() {
      body.textContent = "";
      const view = session === null ? null : api.manageable(state.profiles(), session.actorId);
      if (view?.actor) drawRole(view);
      else drawWho();
    }

    function drawWho() {
      session = null;
      body.append(el("h1", null, "Manage profiles"), el("p", "who-note", "Who are you?"));
      const choices = el("div", "who-ask");
      for (const entry of state.profiles().filter((profile) => !profile.kids)) {
        choices.append(pill(entry.name, async () => {
          const pin = await askGrownUp(screen, entry, (given) => api.prove(entry, given));
          if (pin === null) return;
          session = { actorId: entry.id, pin };
          message.textContent = "";
          draw();
        }));
      }
      body.append(choices);
    }

    function drawRole({ actor, grownUps, kids }) {
      body.append(el("h1", null, "Manage profiles"), el("p", "who-note", `As ${actor.name}`));
      if (actor.admin) {
        body.append(section("Grown-ups",
          ...grownUps.map((entry) => row(entry.name,
            pill("Reset PIN", () => void newPin(entry)),
            pill("Remove", () => void removeOne(entry, `Remove ${entry.name}, their kids, and everything they have watched?`)))),
          addForm("Add a grown-up", [], async (name) => {
            const made = await askPin(screen, { title: `A PIN for ${name}`, confirm: true,
              send: (pin) => api.create(session.actorId, session.pin, { name, kids: false, newPin: pin }) });
            if (made !== null) report({ ok: true });
          })));
      }
      const newLimit = limitChoice(LIMITS[0], "Age limit for the new kid");
      body.append(section("Kids",
        ...kids.map((kid) => {
          const limit = limitChoice(kidsLimitOf(kid), `Age limit for ${kid.name}`);
          limit.addEventListener("change", async () =>
            report(await api.setKidsAge(session.actorId, session.pin, kid.id, Number(limit.value))));
          return row(kid.name, limit,
            pill("Remove", () => void removeOne(kid, `Remove ${kid.name} and everything they have watched?`)));
        }),
        addForm("Add a kid", [newLimit], async (name) =>
          report(await api.create(session.actorId, session.pin, { name, kids: true, kidsAge: Number(newLimit.value) })))));
      body.append(section("Your PIN", pill("Change your PIN", () => void newPin(actor))));
    }

    /** A new PIN for `entry`, asked twice. Changing one's own changes the one held here, or the next change would be refused. */
    async function newPin(entry) {
      const own = entry.id === session.actorId;
      const given = await askPin(screen, { title: own ? "Your new PIN" : `A new PIN for ${entry.name}`, confirm: true,
        send: (pin) => api.setPin(session.actorId, session.pin, entry.id, pin) });
      if (given === null) return;
      if (own) session.pin = given;
      report({ ok: true });
    }

    async function removeOne(entry, question) {
      if (!window.confirm(question)) return;
      report(await api.remove(session.actorId, session.pin, entry.id));
    }

    card.append(body, message, done);
    screen.append(card);
    parent.append(screen);
    draw();
  }
  ```
- [ ] **5.4** `cd web && bun test test/profile-manage.test.ts` → **PASS**; `wc -l public/lib/profile-manage.js` → < 200.
- [ ] **5.5 CSS** — in `public/styles/library-controls.css`: `:67` `.settings-session {` → `.settings-session, .manage-row {`; append before `@media`:
  ```css
  .who-ask { margin: 0 0 32px; }
  .who-ask h2, .manage-section h2 { margin: 0 0 12px; font: 500 1.35rem/1.1 var(--display); color: var(--ink); }
  .who-ask .pill { margin: 4px; }
  .manage-section { margin: 0 0 32px; text-align: left; }
  .manage-row { flex-wrap: wrap; }
  .manage-row > span { flex: 1; min-width: 0; color: var(--ink); }
  .manage-row select, .who-new select { min-height: 44px; padding: 8px 12px; border: 1px solid var(--rule); border-radius: var(--radius); background: var(--surface); color: var(--ink); font-size: 0.875rem; }
  ```
  (The panel is a second full-screen `.who` layer — paper, no boxed card, per DESIGN.md.)
- [ ] **5.6** Suite + typecheck + lint green. **Commit** — bump by pattern (phase-08 § Bumping), `git add web/public/lib/profile-manage.js web/public/styles/library-controls.css web/test/profile-manage.test.ts` + manifests + changelog;
  `git commit -m "feat(web): a Manage profiles panel that offers what each grown-up may do; release <next>"`.

### Task 6: The picker

- [ ] **6.1 Failing tests** — create `test/profile-picker.test.ts` (fixtures above, but `MAJA.hasPin: false` here — the pre-upgrade case):
  ```ts
  import { afterEach, beforeEach, expect, test } from "bun:test";
  import { chooseProfile } from "../public/lib/profile-picker.js";
  import * as state from "../public/lib/watch-state.js";
  import { answerPin, buttonNamed, byClass, descendants, textOf } from "./support/browser-application";
  import { browserEnvironment, Node, settle } from "./support/player-environment";

  /* ANDRE, MAJA = { ...hasPin: false }, TIM, LEA */

  let env: ReturnType<typeof browserEnvironment>;
  let household: object[];
  let refusal: { status: number; body: object } | null;
  beforeEach(async () => {
    env = browserEnvironment();
    household = [ANDRE, MAJA, TIM, LEA];
    refusal = null;
    env.respondWith(async (url, options) => {
      if (url === "/api/profiles" && !options?.method) return Response.json({ remembers: true, profiles: household });
      if (url.endsWith("/state")) return Response.json({});
      if (refusal) return Response.json(refusal.body, { status: refusal.status });
      return new Response(null, { status: 204 });
    });
    await state.loadProfiles();
  });
  afterEach(async () => { await state.useProfile(null); env.restore(); });

  const tile = (root: Node, name: string) => descendants(root).find((node) => node.className === "who-tile" && textOf(node).includes(name))!;
  const sent = () => env.requests.filter((request) => request.options?.method)
    .map((request) => [request.options!.method, request.url, JSON.parse(String(request.options!.body))]);
  const prompting = (root: Node) => descendants(root).some((node) => node.className.split(" ").includes("pin-prompt"));

  test("a kid's tile names its limit and opens at once, with no PIN", async () => {
    const root = new Node();
    const chosen = chooseProfile(root);
    expect(textOf(tile(root, "Tim"))).toContain("Kids · FSK 6");
    expect(textOf(tile(root, "Lea"))).toContain("Kids · FSK 12");
    tile(root, "Tim").fire("click");
    expect(await chosen).toBe("tim");
    expect(sent()).toEqual([]);
  });

  test("a grown-up's tile asks the PIN, and only an accepted one enters", async () => {
    const root = new Node();
    let entered = false;
    const chosen = chooseProfile(root).then((id) => { entered = true; return id; });
    tile(root, "andre").fire("click");
    expect(prompting(root)).toBe(true);
    refusal = { status: 403, body: { reason: "wrong-pin" } };
    await answerPin(root, "1111");
    expect(textOf(byClass(root, "pin-message"))).toBe("Wrong PIN.");
    expect(entered).toBe(false);
    refusal = null;
    await answerPin(root, "1234");
    expect(await chosen).toBe("andre");
    expect(sent().at(-1)).toEqual(["POST", "/api/profiles/andre/unlock", { pin: "1234" }]);
  });

  test("a waiting player's answer is shown in seconds", async () => {
    const root = new Node();
    void chooseProfile(root);
    tile(root, "andre").fire("click");
    refusal = { status: 429, body: { reason: "wait", retryAfter: 60 } };
    await answerPin(root, "1234");
    expect(textOf(byClass(root, "pin-message"))).toBe("Too many wrong PINs. Try again in 60 s.");
    expect(prompting(root)).toBe(true);
  });

  test("a grown-up with no PIN sets one, twice, before entering", async () => {
    const root = new Node();
    const chosen = chooseProfile(root);
    tile(root, "Maja").fire("click");
    await answerPin(root, "4321", "4312");
    expect(textOf(byClass(root, "pin-message"))).toBe("The two PINs are not the same.");
    expect(sent()).toEqual([]);
    household = [ANDRE, { ...MAJA, hasPin: true }, TIM, LEA];
    await answerPin(root, "4321", "4321");
    expect(await chosen).toBe("maja");
    expect(sent()).toEqual([["PUT", "/api/profiles/maja/pin", { actorId: "maja", pin: "", newPin: "4321" }]]);
  });

  test("giving up on the PIN leaves the picker as it was", async () => {
    const root = new Node();
    void chooseProfile(root);
    tile(root, "andre").fire("click");
    buttonNamed(root, "Cancel").fire("click");
    await settle();
    expect(prompting(root)).toBe(false);
    expect(env.requests.some((request) => request.url.endsWith("/state"))).toBe(false);
  });

  test("while nobody runs the household, the picker asks who does — grown-ups only", async () => {
    household = [{ ...ANDRE, admin: false }, MAJA, TIM, LEA];
    await state.loadProfiles();
    const root = new Node();
    void chooseProfile(root);
    const ask = byClass(root, "who-ask");
    expect(textOf(ask)).toContain("Who runs this household?");
    expect(descendants(ask).filter((node) => node.tagName === "BUTTON").map((node) => node.textContent)).toEqual(["andre", "Maja"]);
    household = [ANDRE, MAJA, TIM, LEA];
    buttonNamed(ask, "andre").fire("click");
    await answerPin(root, "1234");
    expect(sent()).toEqual([["POST", "/api/profiles/andre/claim-admin", { pin: "1234" }]]);
    expect(descendants(root).some((node) => node.className === "who-ask")).toBe(false);
  });

  test("once someone runs the household, nobody is asked", () => {
    const root = new Node();
    void chooseProfile(root);
    expect(textOf(root)).not.toContain("Who runs this household?");
    expect(textOf(root)).not.toContain("Create the first profile");
  });

  test("with no grown-up here, the first one is made — and runs the household; kids still open", async () => {
    household = [LEA];
    await state.loadProfiles();
    const root = new Node();
    void chooseProfile(root);
    expect(textOf(root)).toContain("Create the first profile — it runs this household");
    expect(textOf(root)).not.toContain("Manage profiles");
    expect(textOf(tile(root, "Lea"))).toContain("Kids · FSK 12");
    const form = descendants(root).find((node) => node.tagName === "FORM" && node.className === "who-new")!;
    descendants(form).find((node) => node.tagName === "INPUT")!.value = "andre";
    household = [ANDRE, LEA];
    form.fire("submit");
    await settle();
    await answerPin(root, "1234", "1234");
    await settle();
    expect(sent()).toEqual([["POST", "/api/profiles", { name: "andre", newPin: "1234" }]]);
    expect(textOf(root)).not.toContain("Create the first profile");
    expect(tile(root, "andre")).toBeDefined();
    expect(buttonNamed(root, "Manage profiles")).toBeDefined();
  });

  test("a first profile refused because a grown-up arrived meanwhile shows who is here now", async () => {
    household = [];
    await state.loadProfiles();
    const root = new Node();
    void chooseProfile(root);
    const form = descendants(root).find((node) => node.tagName === "FORM" && node.className === "who-new")!;
    descendants(form).find((node) => node.tagName === "INPUT")!.value = "andre";
    form.fire("submit");
    await settle();
    refusal = { status: 403, body: { reason: "not-allowed" } };
    household = [ANDRE];
    await answerPin(root, "1234", "1234");
    expect(textOf(byClass(root, "pin-message"))).toBe("That is not allowed.");
    buttonNamed(root, "Cancel").fire("click");
    await settle();
    expect(textOf(root)).not.toContain("Create the first profile");
    expect(tile(root, "andre")).toBeDefined();
  });

  test("profiles are made and removed in Manage profiles, not on the picker", () => {
    const root = new Node();
    void chooseProfile(root);
    expect(textOf(root)).not.toContain("New profile");
    expect(textOf(root)).not.toContain("Rename or remove");
    buttonNamed(root, "Manage profiles").fire("click");
    expect(textOf(byClass(root, "who-manage"))).toContain("Who are you?");
  });

  test("the note says what a PIN does, and that it is not a login", () => {
    const root = new Node();
    void chooseProfile(root);
    expect(textOf(byClass(root, "who-note"))).toContain("it is not a login");
  });

  test("with this device's profile gone, there is nothing to stay as", async () => {
    await state.useProfile("lea");
    household = [ANDRE, MAJA, TIM];
    await state.loadProfiles();
    const root = new Node();
    void chooseProfile(root, { canCancel: true });
    expect(buttonNamed(root, "Stay as I am").hidden).toBe(true);
  });

  test("opened from a running page, the picker reads who there is again", async () => {
    household = [ANDRE, MAJA, TIM, LEA, { ...TIM, id: "ben", name: "Ben", createdAt: 5 }];
    const root = new Node();
    void chooseProfile(root, { canCancel: true });
    expect(descendants(root).some((node) => node.className === "who-tile" && textOf(node).includes("Ben"))).toBe(false);
    await settle();
    expect(textOf(tile(root, "Ben"))).toContain("Kids · FSK 6");
  });
  ```
  Update the existing tests that describe the old picker (same commit):
  - `test/management-controls.test.ts`: beforeEach profile `{ id: "alice", name: "Alice", kids: true, kidsAge: 12 }` (a kid enters without a PIN, keeping those tests about state loading; with no grown-up the first-profile form is drawn above the kid tiles, which these tests ignore); in "canceling pending profile selection…" (`:74-90`) load `[alice, { id: "bob", name: "Bob", kids: true, kidsAge: 12 }]`, answer `/api/profiles` directly and count only the `/state` requests (`env.respondWith((url) => url === "/api/profiles" ? Response.json({ remembers: true, profiles }) : (requests++, pending.promise))`), click Bob's tile; **delete** "a rejected new profile remains retryable…" (`:92-111`) and "profile deletion failure keeps the chooser…" (`:113-130`) — their cases live in `profile-manage.test.ts`; in "profile discovery retry…" (`:183`) `button(root, "Rename or remove…")` → `button(root, "Manage profiles")`.
  - `test/browser-application.test.ts`: default `/api/profiles` Viewer (`:41`) → `{ id: "viewer", name: "Viewer", kids: false, kidsAge: null, parentId: null, admin: true, hasPin: true }`; import `answerPin, buttonNamed, byClass` and `type Node`; add
    ```ts
    /** Chooses `tile` the way a viewer does: a grown-up's asks its PIN, which the stub server accepts. */
    async function choose(tile: Node) {
      tile.fire("click");
      await settle();
      if (descendants(env.document.body).some((node) => node.className.split(" ").includes("pin-prompt"))) {
        await answerPin(env.document.body, "1234", "1234");
      }
    }
    ```
    rewrite `finishProfilePicker` (`:497-518`) — the `who-add` branch goes:
    ```ts
    async function finishProfilePicker(starting: Promise<void>) {
      // Also releases startup when an assertion above failed: with nothing
      // intercepted the stub server knows Viewer, the admin, and takes any PIN.
      if (descendants(env.document.body).some((node) => node.className === "who")) {
        intercept = () => null;
        const retry = descendants(env.document.body).find((node) => node.textContent === "Retry profiles");
        const first = descendants(env.document.body).find((node) => node.tagName === "FORM" && node.className === "who-new");
        if (retry) retry.fire("click");
        else if (first) {
          descendants(first).find((node) => node.tagName === "INPUT")!.value = "Viewer";
          first.fire("submit");
          await settle();
          await answerPin(env.document.body, "1234", "1234");
        }
        await settle();
        const tile = descendants(env.document.body).find((node) => node.className === "who-tile" && textOf(node).includes("Viewer"));
        if (!tile) throw new Error("Recovered profile tile missing");
        await choose(tile);
      }
      await starting;
    }
    ```
    `:535-536` → `await choose(tile)`; empty discovery (`:574-587`) → title "successful empty profile discovery (remembers=%s) offers the first profile without a retry error", expect "Create the first profile — it runs this household" instead of "New profile" (the "Could not load profiles." / "Retry profiles" / "cannot save anything" assertions stay); kids block `profiles` Adult → `{ …, admin: true, hasPin: true }`, "choosing an adult profile…" → `await choose(adult)`; replace "a new profile can be made a kids profile…" (`:699-723`) with
    ```ts
    test("a kid's tile names its own limit", async () => {
      kidAt(6);
      await start();
      env.node("who").fire("click");
      await settle();
      const tile = descendants(env.document.body).find((node) => node.className === "who-tile" && textOf(node).includes("Viewer"))!;
      expect(textOf(tile)).toContain("Kids · FSK 6");
    });

    test("a kid whose limit changes in Manage profiles is filtered anew when the picker closes", async () => {
      let kidsAge = 12;
      const base = intercept;
      intercept = (url, init) => {
        if (url === "/api/profiles" && !init?.method) {
          return Promise.resolve(Response.json({ remembers: true, profiles: profiles.map((entry) => (entry.kids ? { ...entry, kidsAge } : entry)) }));
        }
        if (url === "/api/profiles/viewer/kids-age") { kidsAge = 6; return Promise.resolve(new Response(null, { status: 204 })); }
        return base(url, init);
      };
      catalog = JSON.stringify([rated("Family", "6"), rated("Twelve", "12")]);
      await start();
      expect(env.node("n-movies").textContent).toBe("2");
      env.node("who").fire("click");
      await settle();
      buttonNamed(env.document.body, "Manage profiles").fire("click");
      buttonNamed(byClass(env.document.body, "who-manage"), "Adult").fire("click");
      await answerPin(env.document.body, "1234");
      const limit = descendants(env.document.body).find((node) => node.tagName === "SELECT" && node.getAttribute("aria-label") === "Age limit for Viewer")!;
      limit.value = "6";
      limit.fire("change");
      await settle();
      buttonNamed(env.document.body, "Done").fire("click");
      buttonNamed(env.document.body, "Stay as I am").fire("click");
      await settle();
      expect(env.node("n-movies").textContent).toBe("1");
    });
    ```
  - `test/watch-state-async.test.ts`: delete the `"profile"` row of `creations` (`:100`) and make `:188` `const body = { name: "New" };`.
- [ ] **6.2** `cd web && bun test test/profile-picker.test.ts test/management-controls.test.ts test/browser-application.test.ts` → **FAIL** (tiles say "Kids", a grown-up tile enters without a PIN, no "Manage profiles", no household question, no first-profile form; the Manage-driven limit change is not re-filtered).
- [ ] **6.3 Implement** — replace `public/lib/profile-picker.js` with:
  ```js
  /**
   * Who is watching.
   *
   * Profiles keep a household's places and lists apart. A kid's tile opens at
   * once; a grown-up's asks their PIN — or, for one from before PINs, has them
   * set one first — so a child cannot tap into a parent's profile. That is all
   * a PIN does here. It is not a login: the kids filter runs in this browser and
   * the state API has no sessions, so developer tools or `curl` get past it.
   * What the server does check is every change to who there is, made through
   * Manage profiles (`profile-manage.js`).
   *
   * The answer is kept on the device rather than on the server, so a television
   * stays on the television's profile and a shared laptop asks again; a
   * profile remembered at start-up is not asked for its PIN.
   */

  import { el } from "./dom.js";
  import * as state from "./watch-state.js";
  import * as api from "./profile-api.js";
  import { kidsLimitOf } from "./age-rating.js";
  import { askGrownUp, askPin } from "./pin-prompt.js";
  import { addForm, openManage } from "./profile-manage.js";

  /** Said plainly, so nobody takes a PIN for a login. */
  const NOTE = "Profiles keep your places and lists apart. A grown-up’s PIN keeps children out of it; it is not a login, and someone who knows their way around a browser can get past it.";

  /** The letter on a profile's tile. */
  export const initialOf = (name) => (name ?? "?").trim().charAt(0).toUpperCase() || "?";

  /**
   * Shows the chooser and resolves once somebody has been chosen.
   *
   * Resolves rather than calling back, because everything downstream — the
   * catalog render, the shelves, the player — is waiting on the answer and
   * reads better as one await than as a continuation.
   */
  export function chooseProfile(root, { canCancel = false, discoveryFailed = false, stateFailed = false } = {}) {
    return new Promise((resolve) => {
      const screen = el("div", "who");
      const card = el("div", "who-card");
      card.append(el("h1", null, "Who's watching?"));

      const choices = el("div");
      card.append(choices);
      let closed = false;
      let selecting = false;
      const selection = new AbortController();
      const back = canCancel ? el("button", "quiet", "Stay as I am") : null;

      /** A grown-up proves who they are first; a kid goes straight in. */
      async function enter(entry) {
        if (closed || selecting) return;
        if (!entry.kids && (await askGrownUp(screen, entry, (pin) => api.prove(entry, pin))) === null) return;
        if (closed) return;
        selecting = true;
        stateFailed = false;
        draw();
        const applied = await state.useProfile(entry.id, selection.signal);
        if (closed) return;
        selecting = false;
        if (!applied) {
          stateFailed = true;
          draw();
          return;
        }
        closed = true;
        screen.remove();
        resolve(entry.id);
      }

      /**
       * No grown-up here yet — a new player, or one that only knows kids: the
       * first grown-up made runs the household. Whether it was made or refused
       * (one arrived from another player meanwhile), draw who is here now.
       */
      function firstProfile() {
        const box = el("div", "who-ask");
        box.append(el("h2", null, "Create the first profile — it runs this household"),
          addForm("Create", [], async (name) => {
            await askPin(screen, { title: `A PIN for ${name}`, confirm: true, send: (pin) => api.createFirst(name, pin) });
            if (!closed && (await state.loadProfiles())) draw();
          }));
        return box;
      }

      /** Asked while grown-ups exist but nobody runs this household. Only a grown-up can; one with no PIN sets it here. */
      function householdQuestion(grownUps) {
        const ask = el("div", "who-ask");
        ask.append(el("h2", null, "Who runs this household?"));
        for (const entry of grownUps) {
          const pick = el("button", "pill pill-line", entry.name);
          pick.type = "button";
          pick.disabled = selecting;
          pick.addEventListener("click", async () => {
            if (closed || selecting) return;
            const pin = await askGrownUp(screen, entry, (given) => api.claimAdmin(entry.id, given));
            if (pin !== null && !closed) draw();
          });
          ask.append(pick);
        }
        return ask;
      }

      function tiles() {
        const row = el("div", "who-tiles");
        for (const entry of state.profiles()) {
          const tile = el("button", "who-tile");
          tile.append(el("span", "who-initial", initialOf(entry.name)));
          tile.append(el("span", "who-name", entry.name));
          if (entry.kids) tile.append(el("span", "who-kids", `Kids · FSK ${kidsLimitOf(entry)}`));
          tile.disabled = selecting;
          tile.addEventListener("click", () => void enter(entry));
          row.append(tile);
        }
        return row;
      }

      const draw = () => {
        choices.textContent = "";
        // Nothing to stay as once this device's own profile is gone.
        if (back) back.hidden = state.profile() === null;
        if (discoveryFailed) {
          choices.append(el("p", "error", "Could not load profiles. Please try again."));
          const retry = el("button", "quiet", "Retry profiles");
          retry.addEventListener("click", async () => {
            if (retry.disabled) return;
            retry.disabled = true;
            discoveryFailed = !(await state.loadProfiles());
            if (!closed) draw();
          });
          choices.append(retry);
        } else {
          if (stateFailed) choices.append(el("p", "error", "Could not load this profile. Choose it again to retry."));
          // The three ways a household can stand: nobody grown-up yet, grown-ups
          // with nobody running things, or an admin — and only then is there
          // anyone who could manage.
          const grownUps = state.profiles().filter((entry) => !entry.kids);
          if (grownUps.length === 0) choices.append(firstProfile());
          else if (!state.profiles().some((entry) => entry.admin)) choices.append(householdQuestion(grownUps));
          choices.append(tiles());
          if (grownUps.length > 0) {
            const manage = el("button", "quiet", "Manage profiles");
            manage.disabled = selecting;
            manage.addEventListener("click", () => {
              if (!closed && !selecting) openManage(screen, () => { if (!closed) draw(); });
            });
            choices.append(manage);
          }
        }
        if (!discoveryFailed) {
          choices.append(el("p", "who-note", state.remembers() ? NOTE : "This player cannot save anything, so nothing here will be kept."));
        }
      };
      draw();
      // Opened on a page that has been up a while: another player may have added
      // a kid, set a PIN or named the admin since, and the tiles should say so.
      if (canCancel && !discoveryFailed) {
        void state.loadProfiles().then((read) => { if (read && !closed && !selecting) draw(); });
      }

      if (back) {
        back.addEventListener("click", () => {
          closed = true;
          selection.abort();
          screen.remove();
          resolve(state.profileId());
        });
        card.append(back);
      }

      screen.append(card);
      root.append(screen);
    });
  }
  ```
  `public/lib/watch-state.js`: delete `:153-175` (createProfile, renameProfile, deleteProfile and their blank lines); rewrite the header `:13-16` in place (4 lines):
  ```js
   * Shelf-affecting setters notify subscribers immediately. Collection
   * management waits for the server: creation returns a record or null, and
   * rename/delete a boolean, so a caller can report a failure. Profile
   * management is `profile-api.js`'s, which reads the list here again after.
  ```
  and point `:40`'s `import("../../src/state/store.ts").Profile` at the file found in 0.2 if it moved.
  `public/app.js:433-438` (net 0):
  ```js
  // The header's name opens the picker: to become somebody else (a grown-up's PIN
  // is asked there) or to manage profiles — which can change this very profile too.
  async function switchProfile() {
    const before = JSON.stringify(state.profile());
    await chooseProfile(document.body, { canCancel: true });
    if (JSON.stringify(state.profile()) === before) return;
  ```
  `public/styles/library-controls.css`: delete `.who-add` (`:10`) and `.who-kids-choice` (`:16`).
- [ ] **6.4** Run 6.2 again → **PASS**. `wc -l public/lib/profile-picker.js public/lib/watch-state.js public/app.js` → < 200, ~478, 741.
- [ ] **6.5** `test/code-standards.test.ts`: `"public/lib/watch-state.js": 502` → the measured count; extend the doc comment: "Lowered 2026-09-28 for `watch-state.js`, once profile management moved to `lib/profile-api.js`."
- [ ] **6.6** `cd web && bun test && bun run typecheck && bun run lint` → green;
  `grep -rn "createProfile\|renameProfile\|deleteProfile\|who-add\|window.prompt(\s*\"Name of the profile" public` → no hits.
- [ ] **6.7 Commit** — bump by pattern (phase-08 § Bumping, patch, changelog: "The picker asks a grown-up's PIN, asks once who runs the household, and Manage profiles replaces New profile and Rename or remove"), `git add web/public/lib/profile-picker.js web/public/lib/watch-state.js web/public/app.js web/public/styles/library-controls.css web/test/profile-picker.test.ts web/test/management-controls.test.ts web/test/browser-application.test.ts web/test/watch-state-async.test.ts web/test/code-standards.test.ts` + manifests + changelog;
  `git commit -m "feat(web): the picker asks a grown-up's PIN and who runs the household, and hands profiles to Manage; release <next>"`.
  **Push only together with phase 02's commits** (plan.md § Dependencies).

### Task 7: By eye, on the stub preview (no commit)

`$B` = the gstack `/browse` binary. Screenshots → `/home/andre/Workspace/mediagram/plans/reports/web-profile-roles-NN-<what>.png`, taken with `--viewport` (a full-page shot misses a `<dialog>`), then opened with Read.

- [ ] **7.1 Start** (detached, or it dies with the turn):
  ```bash
  cd /home/andre/Workspace/mediagram/web
  setsid nohup bun run preview > "$SCRATCH/preview.log" 2>&1 < /dev/null & disown
  curl -s --retry 20 --retry-connrefused http://127.0.0.1:8795/api/profiles | jq -r '.profiles[] | "\(.id) \(.name) kids=\(.kids) age=\(.kidsAge) admin=\(.admin) pin=\(.hasPin)"'
  curl -s http://127.0.0.1:8795/api/sets | jq -r '[.[] | select(.kind=="movie" and .fsk==null)][0:3][] | "\(.setId) \(.title)"'   # U: unrated
  curl -s http://127.0.0.1:8795/api/sets | jq -r '[.[] | select(.kind=="movie" and .fsk=="12")][0:2][] | "\(.setId) \(.title)"'  # T: FSK 12
  ```
  Expected: andre/test/TV test grown-ups with `pin=false admin=false`, TV kids `age=12`. It is a copy — nothing reaches the real `state.db`.
- [ ] **7.2 Picker, first run** — `$B goto http://127.0.0.1:8795/`, `$B js "localStorage.removeItem('mediagram.profile')"`, `$B reload`. Expect "Who runs this household?" with andre / test / TV test (no TV kids), TV kids tile "Kids · FSK 12", "Manage profiles", no "New profile", note with "it is not a login". Screenshot `01-picker-household`.
- [ ] **7.3 Claim admin** as `test`: click its pill → two masked fields ("Choose a PIN for test") → `1357` twice → OK → the question is gone. Screenshot `02-pin-prompt` before OK (digits masked).
- [ ] **7.4 Kid opens freely** — click TV kids → no prompt; `$B js "document.getElementById('who').textContent"` → `TV kids`.
- [ ] **7.5 No-PIN grown-up** — header name → picker → andre → "Choose a PIN for andre", two fields → `2468` twice → enters as andre.
- [ ] **7.6 Wrong PIN ×5 → wait** — picker → test → `0000` + OK six times. By the sixth: "Too many wrong PINs. Try again in N s." Screenshot `03-wait`. The right PIN `1357` now → still the wait message. After 60 s (a Monitor until-loop, not a foreground sleep) → `1357` enters.
- [ ] **7.7 Manage as a parent** — picker → Manage profiles → "Who are you?" offers andre / test / TV test only → andre → `2468` → no "Grown-ups"; "Kids" empty (TV kids has no parent → the admin's); Add a kid "Mini", FSK 6 → row "Mini · FSK 6 · Remove". Screenshot `04-manage-parent`. Done.
- [ ] **7.8 Manage as admin** — Manage → test → `1357` → "Grown-ups": andre and TV test, each with Reset PIN / Remove, no row for test; Kids: TV kids, not Mini. Screenshot `05-manage-admin`. Add a grown-up "Guest" → PIN `1111` twice → row appears; Reset PIN on Guest → `2222` twice; `$B dialog-accept` then Remove on Guest → gone.
- [ ] **7.9 Kids select** — as andre, open U's page → Play; wake the HUD (`$B hover "#player"`). The Kids control is a select "Not for kids / From 6 / From 12"; `$B select "#kids-age" 6`. Screenshot `06-kids-select`. `curl -s 127.0.0.1:8795/api/kids | jq '.fromSix | index("<U>")'` → a number. On T the control reads "For kids · FSK 12", disabled. If the HUD does not render without media, check with `$B js "(() => { const s = document.getElementById('kids-age'); return [s.hidden, s.value, [...s.options].map(o => o.text)]; })()"`.
- [ ] **7.10 FSK 6 vs 12** — become Mini: `n-movies` count, U found by search, T not. Become TV kids: U and T both found. As andre set U to "From 12" → Mini no longer finds U. `#/tutorials` as Mini → "Nothing rated FSK 6 or under yet." (unless a course is marked from 6). Screenshots `07-shelf-fsk6`, `08-shelf-fsk12`. Settings → Profile as Mini → "Mini · Kids · FSK 6".
- [ ] **7.11 Remembered profile asks nothing** — `$B js "localStorage.setItem('mediagram.profile', '<andre id>')"`, `$B reload` → straight to the shelves as andre, no prompt.
- [ ] **7.12** `$B console --errors` → none. Stop: `fuser -k -n tcp 8795` (not `pkill -f`, which matches the calling shell).
- [ ] **7.13 First profile on an empty player** — the preview copies `state.db` only if it exists (`scripts/preview.ts:53`), so point it at nothing:
  ```bash
  cd /home/andre/Workspace/mediagram/web
  MEDIAGRAM_STATE_DB="$SCRATCH/no-such-state.db" setsid nohup bun run preview > "$SCRATCH/preview-empty.log" 2>&1 < /dev/null & disown
  curl -s --retry 20 --retry-connrefused http://127.0.0.1:8795/api/profiles | jq '.profiles | length'   # 0
  ```
  `$B goto http://127.0.0.1:8795/`, clear `mediagram.profile`, `$B reload` → "Create the first profile — it runs this household", no tiles, no "Manage profiles", no "Who runs this household?". Screenshot `09-first-profile`. Name "Admin" → Create → PIN `1234` twice → the form is gone; an "Admin" tile and "Manage profiles" are drawn; `curl -s 127.0.0.1:8795/api/profiles | jq '.profiles[0] | {admin, hasPin}'` → both `true`. Stop with `fuser -k -n tcp 8795`.

## Todo list

- [ ] Task 0 — rebase, phase 02 present, baseline green
- [ ] Task 1 — per-kid filter, marks with an age, Kids select (commit)
- [ ] Task 2 — empty shelf / Settings / tiles name the limit; app.js ceiling 741 (commit)
- [ ] Task 3 — `profile-api.js` + fixture-driven view test (commit)
- [ ] Task 4 — `pin-prompt.js` + CSS + test helpers (commit)
- [ ] Task 5 — `profile-manage.js` + CSS (commit)
- [ ] Task 6 — picker rewrite, profile writers leave watch-state, tests updated, ceiling lowered (commit; push with phase 02)
- [ ] Task 7 — preview walkthrough (household copy + empty player), 9 screenshots

## Success criteria

- `cd web && bun test` green (incl. `code-standards.test.ts`, every `profile-rules.json` case in `profile-api.test.ts`); `bun run typecheck`, `bun run lint` clean — at each of the six commits.
- `grep -rn "KIDS_AGE_LIMIT\|isKids\|createProfile\|renameProfile\|deleteProfile\|FSK 12 or under" web/public` → no hits.
- New files < 200 lines; `watch-state.js` and `app.js` ceilings lowered to their measured sizes; `library-session.js` 199; `shelf-view.js` 285.
- Task 7 observed step by step, screenshots `01`–`09` in `plans/reports/`, none showing a typed PIN in clear.
- All three picker start states of contract §12 covered by `profile-picker.test.ts` and seen in 7.2 / 7.13.

## Risk assessment

| Risk | L×I | Mitigation |
|---|---|---|
| Held PIN goes stale (changed on another device) → every panel action is a wrong PIN → the household's lockout | M×H | `report()` drops the session on `wrong-pin`; own PIN change updates the held PIN (tested) |
| This device's kid removed (or its parent) in Manage → "Stay as I am" leaves no profile → **unfiltered catalog on a kid's screen** | M×H | `change()` → `useProfile(null)`; picker hides "Stay as I am" when `profile()` is null; `switchProfile` redraws on any profile change (tested) |
| A kid's limit lowered in Manage while this device is on it → old filter kept | M×H | `switchProfile` compares profile content, not id (browser test) |
| Limit changed on **another** device → an open tab keeps the old filter until reload or the next picker open | M×M | Accepted for this phase (the state event re-reads positions, not profiles; `app.js` has no line to spare). Listed as follow-up / lead question |
| Stale `hasPin` in a long-open tab → "set a PIN" for a profile that has one → `pin: ""` is compared → `wrong-pin`, **counted toward the wait** (contract §3 amended) | L×M | Picker re-reads profiles when opened on a running page; at start-up the list is fresh; the prompt says "Wrong PIN." and cancelling redraws from the re-read list |
| First profile refused because a grown-up arrived by sync meanwhile | L×L | Prompt shows "That is not allowed."; the picker re-reads and draws who is there (tested) |
| Ratchet breach mid-task (watch-state at 502, app.js 742, library-session 200) | M×M | Exact net-zero edits specified; `wc -l` checks in 1.8, 1.11, 2.4, 6.4 |
| Pre-upgrade window: any viewer can claim admin or set a PIN for an unprotected grown-up | M×H | Spec-accepted transition; release note: claim admin on the web player right after deploying (andre). Nobody can reset the admin's own PIN, so a claim made by the wrong person has no in-app undo |
| Tests mis-typed against the fake `Node` (casting to `HTMLElement` breaks typecheck — no DOM lib) | M×L | Pass the fake directly; JSDoc `HTMLElement` resolves to `any` |
| Phase 02 not landed / fixture missing | L×H | Task 0.2 stops the phase |

**Rollback**: six commits, revert newest first; Tasks 3–5 are unreachable until Task 6 and revert freely. Reverting Task 6 alone restores the old picker, which calls routes phase 02 removed — so roll 02 and 03 back together (or fix forward). No data migration on the client; `localStorage` key `mediagram.profile` unchanged.

**Backwards compatibility**: the web client and its server ship in one process, so no version skew; remembered profiles still enter without a PIN; old Kids marks arrive as "from 12" (server migration); `emptyState`'s option rename has three callers, all updated here.

## Security considerations

- PINs travel only in JSON request bodies (never in URLs/history/logs), are never stored (no `localStorage`/`sessionStorage`), live in the panel's closure until Done or a wrong PIN, and the prompt clears its fields on refusal and removes itself on close.
- Masked `type="password"`, `inputmode="numeric"`, `autocomplete="off"` (browsers may still offer to save — honest ceiling).
- All text via `el()`/`textContent` — profile names arrive by sync from other devices; no `innerHTML`.
- Hiding a control is not enforcement: the server checks every management call (contract §2/§3); the picker, prompt and panel doc comments state the ceiling (spec §1).
- `kidsLimitOf` fails closed: a kid with an absent or odd `kidsAge` sees 12, never everything.
- A bodiless 403 (cross-site write refused by `browser-write.ts`) reads as "no reason", never as a PIN verdict.

## Next steps

- Push with phase 02; phase 08 walks both surfaces and records docs.
- After release: claim admin on the web player first (andre), then set PINs for test / TV test.
- Follow-ups: reach an open tab with a limit changed elsewhere (profiles re-read on the `state` event + reapply; needs `app.js` room); the contract questions in the report.
