# Phase 04 — The page and the TV

**Priority:** after phase 03. **Status:** todo. **Spec:** approach 1 (the page stays in charge); my calls 2, 4, 6, 9; TV in the played-back proposals.

## Overview

The client gains:
- a shared ink model, used by the TV and the board page
- the TV page for Kritzelei
- buzzes the game asks for
- board mode on the phone page, which drives the native board over the bridge

It ends with a browser walk that fakes the bridge, so the whole game runs before any Swift exists.

## Key insights

- `Connection` (`client/src/net.ts`) has one socket. `send` drops messages while the socket is closed. After a reconnect the box's flush sends `Draw` from 0 before the board view. The page passes `resync: true` with the first native show after each Welcome, so board and TV agree again.
- Every change in the room re-sends views. Key effects on the JSON of the board screen, never on object identity.
- `buzzFor` (`phone/buzz-moments.ts`) never buzzes on Wait or on a good Outcome, and `tests/buzz-moments.test.ts` keeps that. An explicit `PhoneView.buzz` takes over only when it's set.
- Phones store the name and seat token per origin (localStorage, or the app's memory). In a browser walk each phone needs its own origin.

## Task 1: Ink model and painter

**Files:**
- Create: `client/src/ink/ink-style.ts`, `client/src/ink/ink-log.ts`, `client/src/ink/paint-ink.ts`
- Test: `client/tests/ink-log.test.ts`

(`client/` = `crates/mediagram-game/client/`.)

**Interfaces:**
```ts
// ink-style.ts — must match ControllerCore's PaperStyle (phase 05)
export const PAPER_ASPECT = 16 / 9;
export const INK_MAX = 10000;
export const INK_COLORS = ["#1b1b1b", "#d8342b", "#2c6bed", "#2f9e44", "#f2a20e", "#fbf8f1"]; // index 5: eraser, the paper colour
export const INK_WIDTHS = [0.005, 0.01, 0.017]; // of the paper's width
export const ERASER_SCALE = 3;
export function lineWidthPx(color: number, width: number, paperWidthPx: number): number;

// ink-log.ts
export type Stroke = { color: number; width: number; points: number[] };
export function strokesOf(ops: DrawOp[]): Stroke[];
export class InkLog {
  turn: number | null; ops: DrawOp[];
  apply(msg: { turn: number; from: number; ops: DrawOp[] }): "reset" | "append" | "ignored";
  /** "reset" = the drawing was replaced, repaint from scratch; "append" = ops[from..] are new, paint just those. */
  subscribe(fn: (change: "reset" | "append", from: number) => void): () => void;
}

// paint-ink.ts
export function paintAll(ctx: CanvasRenderingContext2D, strokes: Stroke[], w: number, h: number): void; // fills paper colour first
/** Paints ops[from..]; returns false when they hold an undo or clear and the caller must paintAll. */
export function paintTail(ctx: CanvasRenderingContext2D, ops: DrawOp[], from: number, w: number, h: number): boolean;
```
Round caps and joins. Smooth through the midpoints with `quadraticCurveTo`.

- [ ] **Step 1: Failing tests:**
  - `from 0 starts afresh`
  - `a continuation appends`
  - `a gap is ignored` (from ≠ ops.length)
  - `subscribers hear reset on a replacement and append with from on a continuation`
  - `a new turn from 0 resets`
  - `line then more is one stroke`
  - `undo drops the last stroke`
  - `clear empties`
  - `more after undo extends nothing`
  - `the eraser is three times wider`
- [ ] **Step 2:** `cd client && bun test tests/ink-log.test.ts` → FAIL. **Step 3:** implement. **Step 4:** PASS, `bun run typecheck` clean.
- [ ] **Step 5: Commit** — `feat(spieltisch): the ink model the TV and the board page share`.

## Task 2: The TV page

**Files:**
- Create: `client/src/tv/TvKritzelei.tsx`, `client/src/tv/kritzelei/InkCanvas.tsx`, `client/src/tv/kritzelei/kritzelei.css`
- Modify: `client/src/tv/TvApp.tsx` — one `InkLog` per TV (`useRef`); `msg.t === "draw"` → `ink.apply(msg)`; the `kritzelei` branch renders `<TvKritzelei scene players conn ink />`
- Modify: `client/src/tv/lobby/TablePanel.tsx` — when the picked game is `kritzelei`, a chip: „Zeichenbrett bereit“ or „Zeichenbrett fehlt“ from `scene.board`

**Interfaces:** `InkCanvas({ ink }: { ink: InkLog })` is a 16:9 canvas sized to its box at `devicePixelRatio`. It subscribes to `ink`:
- `reset` → `paintAll`
- `append` (with `from`) → `paintTail(ctx, ops, from, …)`, or `paintAll` when that returns false
- a resize → `paintAll`

**Layout (my call; the mockup's TV paper was near-square):** the paper is 16:9, about 1400 px wide at 1080p. Drawer avatar and name, the clock ring (from `useServerNow`) and the hint sit to its left; the scores sit to its right. Per stage:

| Stage | TV shows |
|---|---|
| Handover | empty paper, „{Name} ist dran“ and „zeichnet gleich“ |
| Drawing | live ink, the running ring, „Ruft laut, was ihr seht!“ |
| Picking | the ring stands, the hint reads „Wer hat's erraten?“ |
| Reveal | the word large, „{guesser} +2 · {drawer} +1“ or „Niemand hat's erraten“, the finished drawing |
| Final | `<TvFinal players conn />` |

When `scene.board` is false outside Final, a banner reads „Das Zeichenbrett ist weg – die Uhr steht.“

- [ ] **Step 1:** Implement. `bun run typecheck` clean, `bun test` green.
- [ ] **Step 2: Commit** — `feat(spieltisch): Kritzelei on the TV, the line live as it is drawn`. Visual check comes in Task 5.

## Task 3: Buzzes the game asks for

**Files:**
- Modify: `client/src/phone/buzz-moments.ts`, `client/src/phone/PhoneApp.tsx`
- Test: `client/tests/buzz-moments.test.ts`

**Interface:** `export function buzzForView(prev: PhoneScreen | null, next: PhoneScreen, ask: Buzz | null): BuzzKind | null`:
- With `ask` set: buzz when `prev` is null or its JSON differs from `next`.
- Otherwise: `buzzFor(prev, next)`.

`PhoneApp` calls it with `msg.view.buzz`. That is a dispatch line only; the logic stays in `buzz-moments.ts`.

- [ ] **Step 1: Failing tests:**
  - `an asked buzz fires once when the screen changes`: lobby → drawer's Wait with ask `turn` → `"turn"`; the same Wait again → `null`.
  - `an asked buzz overrides a quiet good outcome`: a good Outcome with ask `accepted` → `"accepted"`.
  - `without an ask the old rules hold`: a good Outcome with `null` → `null`.
- [ ] **Step 2–4:** FAIL → implement → PASS (the old tests stay green). **Step 5: Commit** — `feat(spieltisch): a game can ask a phone to buzz`.

## Task 4: Board mode on the page

**Files:**
- Modify: `client/src/phone/app-bridge.ts`
- Create: `client/src/phone/board-native.ts`, `client/src/phone/use-board-mode.ts`, `client/src/phone/BoardPage.tsx`
- Modify: `client/src/phone/PhoneApp.tsx` (dispatch lines only), `client/src/phone/PhoneLobby.tsx`
- Test: `client/tests/app-bridge.test.ts`, `client/tests/board-native.test.ts`

**Interfaces:**
```ts
// app-bridge.ts — the bridge gains `board?: boolean; showBoard?(json: string): void; hideBoard?(): void`
export function boardCapable(): boolean;                        // bridge?.board === true
export function showBoard(show: NativeBoardShow): void;          // JSON.stringify → bridge.showBoard
export function hideBoard(): void;
/** Installs `globalThis.mediagramBoard = { receive(json) }`, which the app calls; returns the remover. */
export function listenToBoard(onAct: (act: BoardAct) => void): () => void;

// board-native.ts
export type NativeClock = { ends_at_epoch_ms: number | null; left_ms: number; total_ms: number };
export type NativeScreen =
  | { kind: "handover"; turn: number; drawer: BoardPlayer }
  | { kind: "drawing"; turn: number; drawer: BoardPlayer; word: string; clock: NativeClock }
  | { kind: "picking"; turn: number; drawer: BoardPlayer; word: string; clock: NativeClock; guessers: BoardPlayer[]; can_go_back: boolean };
export type NativeBoardShow = { screen: NativeScreen; ops: DrawOp[]; resync: boolean };
/** What the app shows for `screen`, or null when the page draws it (wait). */
export function nativeShow(screen: BoardScreen, ops: DrawOp[], resync: boolean, serverNow: number, dateNow: number): NativeBoardShow | null;

// use-board-mode.ts — keeps PhoneApp small
export function useBoardMode(): {
  asBoard: boolean; screen: BoardScreen | null; ink: InkLog; resync: MutableRefObject<boolean>;
  hello(): ClientMsg;                            // { t: "hello", role: "board", name: null, token: <stored board token> }
  onMessage(msg: ServerMsg): boolean;            // welcome (store token, resync = true), board, draw, error-before-welcome (leave, toast); true when handled
  become(): void; leave(): void;                 // memory "spieltisch.board" = "1" / removed; "spieltisch.board-token"
};
```

- `ends_at_epoch_ms = dateNow + (ends_at − serverNow)`, or `null` when paused.
- `BoardPage({ mode, conn, onLeave })`:
  - It always forwards board acts as `{ t: "action", action: { t: "board", act } }`.
  - On every change of `JSON.stringify(screen)` it calls `showBoard(nativeShow(…))` and then sets `resync.current = false`, or `hideBoard()` for a wait screen. It also hides on unmount.
  - The first board screen after each (re)connection is shown with `resync: true` even when its JSON is unchanged. `use-board-mode.ts` bumps a counter on every Welcome, and `BoardPage` keys the show on the screen JSON and that counter.
  - Wait screens render like `Centered`, plus a „Doch mitspielen“ button when `can_leave`.
- `PhoneLobby` gets `onBecomeBoard`. It shows „Als Zeichenbrett verwenden“ only when `boardCapable() && screen.settings.game === "kritzelei"`.
- `PhoneApp` gets dispatch lines only; the logic lives in `use-board-mode.ts` and `BoardPage.tsx`:
  - the hello is `mode.hello()` while `asBoard`
  - messages go through `mode.onMessage` first
  - it renders `BoardPage` while `asBoard`
  - after `leave()` it re-hellos as a phone, or shows the join screen when there's no name

- [ ] **Step 1: Failing tests:**
  - `the board is offered only when the app says it is an iPad`: `mediagramApp` absent, `board: false`, `board: true` → false, false, true.
  - `board acts reach the page parsed, until removed`.
  - `nativeShow`:
    - leaves a wait screen to the page (null)
    - turns a running clock into wall time (serverNow 10_000, dateNow 1_700_000_000_000, ends_at 40_000 → 1_700_000_030_000)
    - keeps a paused clock paused
    - carries the ops and the resync flag
  - `a_reconnect_reshows_an_unchanged_screen_with_resync` (in `use-board-mode`'s tests, or `board-native.test.ts`): the same board screen after a second Welcome yields a show with `resync: true`, then the next change `false`.
- [ ] **Step 2–4:** FAIL → implement → PASS, typecheck clean.
- [ ] **Step 5: Commit** — `feat(spieltisch): an iPad's page can become the drawing board`.

## Task 5: Browser walk with a fake bridge

No code. Verification uses gstack `/browse`, never the Chrome MCP tools.

- [ ] **Step 1:** `cd client && bun run build`, then `GAME_PORT=8080 cargo run -p mediagram-game --bin game-night-dev`.
- [ ] **Step 2:** Open these tabs, one origin per seat (localStorage is per origin):
  - TV: `http://localhost:8080/tv`
  - Ada: `http://127.0.0.1:8080/`
  - Bo: `http://192.168.0.118:8080/`
  - Cy: `http://localhost:8080/`
- [ ] **Step 3:** Make Cy the board:
  1. Inject `window.mediagramApp = { board: true, _m: {}, vibrate(){}, getMemory(){ return JSON.stringify(this._m) }, setMemory(k,v){ v===null ? delete this._m[k] : this._m[k]=v }, showBoard(j){ (window.__shown ||= []).push(JSON.parse(j)) }, hideBoard(){ (window.__shown ||= []).push(null) } }`.
  2. Ada picks Kritzelei. Expected: the TV lobby reads „Zeichenbrett fehlt“ and the start is blocked with „Kritzelei braucht das iPad als Zeichenbrett.“
  3. Cy taps „Als Zeichenbrett verwenden“. Expected: „Zeichenbrett bereit“ and the start unblocks.
- [ ] **Step 4:** Start. Then drive Cy with `window.mediagramBoard.receive(JSON.stringify(act))`:
  1. `{"t":"ready"}`
  2. a `line` and two `more` ops in the 0..10000 range
  3. `{"t":"guessed"}`
  4. `{"t":"pick","player":<Bo's id>}`

  Screenshot the TV and both phones after each step. Expected:
  - the strokes appear on the TV
  - the drawer's phone reads „Du zeichnest – hol dir das iPad“, the other „Rate laut!“
  - the word shows nowhere but `__shown` until the reveal
  - Bo's phone reads „Richtig!“ +2
  - after 6 s, turn 2 begins on a blank paper
- [ ] **Step 5:** Reload the TV tab mid-turn. Expected: the whole drawing comes back. Drop Cy's socket mid-turn without reloading (a reload would kill the fake bridge and its memory). Before the page loads, wrap `window.WebSocket` to keep the instance (`window.__ws`); `Connection.ws` is private (`net.ts`). Then run `window.__ws.close()` and let `net.ts` reconnect. Expected: the last `__shown` entry has `resync: true` and all the turn's ops. Reload and app-kill recovery is walked on the real iPad in phase 06 Task 4.
- [ ] **Step 6:** Play to the ceremony. Write findings in `plans/261113-2115-kritzelei-drawing-game/reports/web-walk-report.md`, then fix anything wrong (TDD, its own commit).

## Success criteria

`bun run typecheck` and `bun test` are green. The browser walk plays a whole game with the word never leaking, and both reloads recover.
