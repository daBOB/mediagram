# Phase 02 — Phone page: bridge, buzz moments, memory

**Priority:** first code. **Status:** todo. Client dir: `crates/mediagram-game/client` (tests: `bun test`, typecheck: `bun run typecheck`).

### Task 2.1: App bridge module

**Files:**
- Create: `src/phone/app-bridge.ts`
- Test: `tests/app-bridge.test.ts`

**Interfaces:**
- Consumes: plan.md § Contracts (bridge, buzz kinds).
- Produces: `export type BuzzKind = "turn" | "accepted" | "wrong" | "won"`; `export function inApp(): boolean`; `export function buzz(kind: BuzzKind): void`; `export const memory: { get(key: string): string | null; set(key: string, value: string | null): void }`.

- [ ] **Step 1: Write the failing tests** (stub `globalThis.mediagramApp`, `globalThis.localStorage`, `globalThis.navigator.vibrate` per test; reset after each):
  - `"no bridge: memory reads and writes localStorage"` — `memory.set("spieltisch.name","Ada")` → `localStorage.getItem` returns `"Ada"`; `memory.set(k, null)` removes it; `inApp()` is `false`.
  - `"bridge: memory comes from getMemory and writes go to setMemory"` — bridge `getMemory()` returns `'{"spieltisch.name":"Ada"}'` → `memory.get("spieltisch.name") === "Ada"`; `memory.set("spieltisch.token","t1")` calls `setMemory("spieltisch.token","t1")` and `memory.get` then returns `"t1"`; localStorage untouched; `inApp()` is `true`.
  - `"bridge: buzz calls vibrate with the kind"` — `buzz("won")` → `vibrate("won")`, `navigator.vibrate` not called.
  - `"browser: buzz uses navigator.vibrate when present and never throws without it"`.
  - `"storage that throws is ignored"` — localStorage whose methods throw: `get` → `null`, `set` does not throw.
- [ ] **Step 2: Run** `bun test tests/app-bridge.test.ts` → FAIL (module missing).
- [ ] **Step 3: Implement** `src/phone/app-bridge.ts`. Read the bridge lazily from `globalThis.mediagramApp` (parse `getMemory()` once, keep a local copy updated by `set`). Browser vibration patterns per kind are your call (short for accepted, double for turn, long for wrong, a flourish for won).
- [ ] **Step 4: Run** `bun test tests/app-bridge.test.ts` → PASS.

### Task 2.2: Buzz moments

**Files:**
- Create: `src/phone/buzz-moments.ts`
- Test: `tests/buzz-moments.test.ts`

**Interfaces:**
- Consumes: `BuzzKind` (2.1), `PhoneScreen` (`src/protocol/PhoneScreen.ts`).
- Produces: `export function buzzFor(prev: PhoneScreen | null, next: PhoneScreen): BuzzKind | null`.

Rules (a screen **asks** when: `choice` with `picked === null`; `sheet` with `options.length > 0` and `picked === null`; `cards` with `desk.buttons.length > 0` — off-turn card players get no buttons, `maumau_views.rs:44`, `durak_views.rs`. Two screens are the **same ask** when kinds match and, for `choice`/`sheet`, `opens_at` matches):
1. `final` with `rank === 1` and `prev?.kind !== "final"` → `"won"`.
2. `outcome` with `tone === "bad"`, unless `prev` is an `outcome` with `tone "bad"` and the same `title` → `"wrong"`.
3. `choice`/`sheet` with `picked !== null`, `prev` the same ask with `picked === null` → `"accepted"`.
4. `next` asks, unless `prev` also asks and is the same ask → `"turn"`.
5. Otherwise `null`.

- [ ] **Step 1: Write the failing tests**, one `test` each, building minimal screens with a local helper:
  - `null → lobby` → `null`; `lobby → lobby` → `null`.
  - `wait → choice{opens_at:1,picked:null}` → `"turn"`.
  - **`"same screen again"`**: `choice{1,null} → choice{1,null}` → `null`; `outcome bad "Falsch" → outcome bad "Falsch"` → `null`; `final rank 1 → final rank 1` → `null`.
  - `choice{1,null} → choice{1,picked:2}` → `"accepted"`; `sheet{1,options:[x],null} → sheet{1,[x],0}` → `"accepted"`.
  - `choice{1,picked:2} → choice{2,null}` → `"turn"` (next question).
  - `choice → outcome bad` → `"wrong"`; `choice → outcome good` → `null`; `choice → outcome neutral` → `null`.
  - `outcome → final rank 1 of 3` → `"won"`; `outcome → final rank 2` → `null`.
  - `cards{buttons:[]} → cards{buttons:[play]}` → `"turn"`; `cards{[play]} → cards{[play,keep]}` → `null`.
  - `sheet{options:[]} → sheet{options:[]}` → `null`.
  - reconnect landing on an already picked choice: `null → choice{1,picked:2}` → `null`.
- [ ] **Step 2: Run** `bun test tests/buzz-moments.test.ts` → FAIL.
- [ ] **Step 3: Implement** `buzzFor` per the rules.
- [ ] **Step 4: Run** → PASS.

### Task 2.3: Wire the page

**Files:**
- Modify: `src/phone/PhoneApp.tsx` (the `store` object at lines 16–32; `onMessage` `"phone"` case; `ChoiceScreen.pick`'s `navigator.vibrate?.(25)`)
- Modify: `src/phone/avatar-memory.ts`, `src/phone/phone-fullscreen.ts`, `src/phone/PhoneLobby.tsx` (screen-lock hint, lines ~15–21), `src/main.tsx`, `src/styles.css`

**Interfaces:** Consumes `memory`, `buzz`, `inApp` (2.1) and `buzzFor` (2.2).

- [ ] **Step 1:** Replace `PhoneApp.tsx`'s local `store` and `avatar-memory.ts`'s direct `localStorage` with `memory` (same keys, same `try`-free call sites — `memory` already swallows storage errors).
- [ ] **Step 2:** In `onMessage` `"phone"`: keep the previous screen in a ref; `const kind = buzzFor(prev, msg.view.screen)`; update the ref; `if (kind) buzz(kind)`. Delete the tap-time `navigator.vibrate?.(25)` in `ChoiceScreen.pick` — the host-acknowledged `"accepted"` replaces it (no double buzz).
- [ ] **Step 3:** `goFullscreen()` returns early when `inApp()`. The lobby hint about the automatic screen lock renders only when `!inApp()`. When `inApp()`, `main.tsx` adds class `in-app` to `<html>`; `styles.css` gives `.in-app` `user-select: none`, `-webkit-touch-callout: none`, `overscroll-behavior: none` (inputs keep `user-select: text`). Browsers never get the class, so guests see no change.
- [ ] **Step 4: Verify.** `bun test` (all client tests) → PASS; `bun run typecheck` → no errors; `bun run build` → succeeds.
- [ ] **Step 5: Browser regression by hand.** Dev host + this page in a desktop browser and an Android phone's Chrome: join, pick an answer, see outcome — name/avatar remembered across reload, hint still shown, no console errors.
- [ ] **Step 6: Bump version** (minor, by pattern, all three manifests + `Cargo.lock`), then commit: `feat(game): the phone page buzzes at its four moments and keeps its memory in the controller apps`.

**Success:** all client tests pass; browser behaviour unchanged except the tap buzz now arrives on the host's acknowledgement.
