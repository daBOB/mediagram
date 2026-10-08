# Phase 04 — Web player: follower and UI

**Spec:** `../spec.md` §2, §3. **Status:** pending. **Depends on:** phase 03.

## Context

These facts come from mapping `web/public`:

- **The player.**
  - `player.js` mounts its modules once (`mountPlayer`, `:79`) and opens titles with `openPlayer(set, options)` (`:611`). `teardown()` (`:956`) runs only on dialog close or `pagehide`. Moving to the next episode calls `openPlayer` again, **not** teardown.
  - Time is always read through `filmTime()` (`:278`), and the single seek entry is `seekFilmTo(seconds)` (`:434`).
  - The viewer's intent is known only by call path: `onPlay: playManually` (`:392`), `onPause` (`:393`), `onSeekTo` (`:394`), the seek bar's `input`/`change` handlers (`:913-928`), and speed through the `remember("speed", …)` callback transport calls from `setSpeed` (`transport.js:185-189`).
- **No toast exists.** The `#note` element is the player's conversion note, and it stays until replaced. Watch together gets its own `#together-note`, which hides itself after 2 s.
- **One EventSource per tab.** It lives in `library-session.js:140-148` through `library-session-port.js:16-22`, and its listener set is fixed (`LibraryPortListeners`). Add `together` there; do **not** open a second stream (`library-session.js:154-157`, the six-connection limit).
- **Home:** `renderHome` (`catalog/home-view.js:64`). The notice goes right after the sr-only `h1` (`:66`). Its style follows the root `DESIGN.md:717-721` Notice: one line, ochre `--warn`, above the shelf.
- **Title pages:** films use `playPill(...)` (`catalog/film-page.js:38`, defined in `title-spread.js:52-58`), and series use `series-page.js:85`.
- **Opening a title by id:** `app.js` resolves it with `byId.get(setId)` and plays it with `play(set)` (`:236-237`, `:280`).
- **Line ceilings:**
  - `player.js` is at 994 of 994, `app.js` at 693 of 693, and `home.css` at 362 of 363.
  - This phase adds about 10 lines to `player.js` and 1 to `app.js`. Raise both ceilings to their new sizes, with a dated sentence in the comment.
  - New styles go in a **new** `styles/together.css`, not `home.css` or `playback.css`.

## Files

- Create: `web/public/lib/together-now.js` + `together-now.d.ts`: the tab's view of the session.
- Create: `web/public/lib/playback/together.js`: the follower in the player.
- Create: `web/public/lib/catalog/together-join.js`: Home's notice and Join on the title pages.
- Create: `web/public/styles/together.css`
- Modify:
  - `web/public/index.html`: the tool button, the top-bar label, the note, and the stylesheet link;
  - `web/public/lib/library-session-port.js`, `library-session.js` and `library-session.d.ts`: the `together` event;
  - `web/public/lib/playback/player.js`: the hooks;
  - `web/public/app.js`: register the opener;
  - `web/public/lib/catalog/home-view.js`, `film-page.js` and `series-page.js`: the notice and the Join pills;
  - `web/test/support/library-session-port.ts` and `web/test/support/browser-application.ts`: the fakes learn `together`;
  - `web/test/code-standards.test.ts`: the ceilings.
- Create: `web/test/together-now.test.ts`, `web/test/together-player.test.ts` and `web/test/together-join.test.ts`

## Interfaces

- **Consumes:**
  - from phase 03, the `/api/together` contract and the `together` SSE payload `{id, session|null, serverNow}`;
  - from phase 02, `follow`, `decide`, `changeOf`, `expectedPosition` and `acted`.
- **Produces** (`together-now.d.ts`):

```ts
export interface Live { id: number; session: import("./together/session.js").Session }
export interface TogetherView { available: boolean; reason: string | null; live: Live | null; offset: number }
export function togetherNow(): TogetherView;
export function serverNow(): number;
export function onTogether(listener: (view: TogetherView, heard: { id: number; session: Live["session"] | null } | null) => void): () => void;
export function refreshTogether(): Promise<void>;
export function heardTogether(payload: { id: number; session: Live["session"] | null; serverNow: number }): void;
export function together(body: Record<string, unknown>): Promise<{ ok: boolean; status: number; body: any }>;
export function setTogetherOpener(open: (setId: string) => void, titleOf: (setId: string) => string | null): void;
export function openForTogether(setId: string, join?: number | null): void;
export function titleForTogether(setId: string): string | null;
export function takeJoin(): number | null;
```

## Tasks

### Task 4.1: The tab's view (`together-now.js`) and the event

- [ ] **Write failing tests**, `test/together-now.test.ts`, with `browserEnvironment()` from `test/support/player-environment.ts` for `fetch` and `Date.now`:
  - `refreshTogether` stores `available`, `live`, and `offset = serverNow − Date.now()`.
  - `heardTogether` with a session sets `live`. With `session: null` for the live id it clears it, and for another id it keeps it.
  - Listeners get `(view, heard)`.
  - `takeJoin` returns the id once, then `null`.
- [ ] **Implement `web/public/lib/together-now.js`:**

```js
/**
 * Watch together as this tab knows it, shared by Home's notice, the title
 * pages and the player. It is fed by `GET /api/together` and by the
 * `together` event on the tab's one event stream (`library-session.js`).
 * `offset` is how far the server's clock is from this one, so expected
 * positions are reckoned on the clock the session was written with.
 */

let known = { available: false, reason: null, live: null, offset: 0 };
const listeners = new Set();
let opener = null;
let titles = () => null;
let joinNext = null;

export const togetherNow = () => known;
export const serverNow = () => Date.now() + known.offset;

export function onTogether(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function tell(heard) {
  for (const listener of listeners) listener(known, heard);
}

export async function refreshTogether() {
  try {
    const response = await fetch("/api/together");
    if (!response.ok) return;
    const body = await response.json();
    known = { available: body.available, reason: body.reason, live: body.live, offset: body.serverNow - Date.now() };
    tell(null);
  } catch {
    // Offline for a moment: what was known stays, and the next event or open corrects it.
  }
}

export function heardTogether(payload) {
  const live = payload.session !== null
    ? { id: payload.id, session: payload.session }
    : known.live?.id === payload.id ? null : known.live;
  known = { ...known, live, offset: payload.serverNow - Date.now() };
  tell({ id: payload.id, session: payload.session });
}

/** A watch-together move; never throws. */
export async function together(body) {
  try {
    const response = await fetch("/api/together", {
      method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body),
    });
    const text = await response.text();
    return { ok: response.ok, status: response.status, body: text ? JSON.parse(text) : null };
  } catch {
    return { ok: false, status: 0, body: { reason: "Telegram isn't reachable" } };
  }
}

/** How a title is opened and named by id. `app.js` registers both, because it holds the library. */
export function setTogetherOpener(open, titleOf) {
  opener = open;
  titles = titleOf;
}

export const titleForTogether = (setId) => titles(setId);

/** Opens `setId` through the app, optionally joining session `join` as the player opens it. */
export function openForTogether(setId, join = null) {
  joinNext = join;
  opener?.(setId);
}

export function takeJoin() {
  const id = joinNext;
  joinNext = null;
  return id;
}
```

- [ ] **Add the event.**
  - In `library-session-port.js`, `listen({ catalog, open, state, together })` adds `events.addEventListener("together", (event) => together(JSON.parse(event.data)));`.
  - `library-session.d.ts`: `LibraryPortListeners` gains `together(payload: { id: number; session: unknown; serverNow: number }): void;`.
  - `library-session.js` `openStream()`:
    - `open: () => { void refreshCatalog(); void refreshTogether(); }`, because a reconnect may have missed an edit;
    - `together: (payload) => heardTogether(payload)`.
  - Update the two test fakes so `fire("together", payload)` passes a payload.
- [ ] **Run:** `bun test test/together-now.test.ts test/library-session*.test.ts test/browser-application.test.ts`. Expected: PASS.
- [ ] **Commit:** `feat(web): the tab's view of watch together`

### Task 4.2: The follower in the player

- [ ] **Write failing tests**, `test/together-player.test.ts`. Mount `together.js` directly with `env.video`, `env.node(...)` elements and spies for `seekTo`/`setRate`. Use `env.advance(ms)` for timers and `env.respondWith` for POSTs:
  1. Joined, then a heard edit with `playing:false` by another screen: `video.pause()` is called, and `#together-note` reads "Paused by TV" for 2 s, then is hidden.
  2. An own echo (`by` = this screen, with the current session still its own write) does nothing.
  3. `viewer({playing:true})` POSTs `{op:"act", change:{playing:true}}` once.
  4. `viewer({pos:100}, {settle:true})` called three times within 300 ms POSTs once, with the last position.
  5. A drift check 2 s later with `filmTime()` 0.6 s behind the expected position calls `seekTo`. With `video.readyState = 2` (buffering) it doesn't.
  6. A heard `session:null` for this id shows "Watch together ended" and stops the drift timer. A refresh (`heard` = null) whose `live` no longer holds this id does the same.
  6b. `ended` with no next title POSTs `{op:"end"}` once. With a next title it posts nothing.
  7. `open(nextSet)` while in a session, not caused by following, POSTs an act `{set, pos:0, playing:true}`. `open` of the set the follower itself asked for posts nothing.
  8. The button reads "Watch together", "Join" (live session elsewhere) or "Leave watch together" (in one). It is disabled with `title` = the reason when unavailable.
- [ ] **Implement `web/public/lib/playback/together.js`:**

```js
/**
 * Watch together, in the player. It follows the session this screen is in
 * and sends the viewer's own moves. What to do is decided in
 * `../together/follow.js`; this only reads the player and applies the steps.
 * A change made here because the session moved never goes back out: only
 * `viewer()` sends, and only the player's viewer call paths call it.
 */

import { changeOf, decide, expectedPosition, follow } from "../together/follow.js";
import { acted } from "../together/moves.js";
import { onTogether, openForTogether, serverNow, takeJoin, together, togetherNow } from "../together-now.js";
import { clockTime } from "../format.js";

const DRIFT_CHECK_MS = 2000;
const NOTICE_MS = 2000;
/** A scrub sends one edit, this long after the last movement. */
const SEEK_SETTLE_MS = 300;
/** Below this the picture is buffering. Nobody waits for it, and the next check catches it up. */
const HAVE_FUTURE_DATA = 3;

export function mountTogether({ video, button, label, note, filmTime, seekTo, setRate }) {
  const screen = crypto.randomUUID();
  let setId = null;
  let id = null;
  let current = null;
  let expecting = null;
  let drift = null;
  let settle = null;
  let hide = null;
  let hasNext = false;

  const own = () => ({ set: setId, playing: !video.paused, pos: filmTime(), rate: video.playbackRate });

  function show(text) {
    note.textContent = text;
    note.hidden = false;
    clearTimeout(hide);
    hide = setTimeout(() => { note.hidden = true; }, NOTICE_MS);
  }

  function apply() {
    if (!current || (!video.paused && video.readyState < HAVE_FUTURE_DATA)) return;
    for (const step of decide(own(), current, serverNow())) {
      if (step.kind === "open") {
        expecting = step.set;
        openForTogether(step.set);
        return;
      }
      if (step.kind === "rate") setRate(step.rate);
      if (step.kind === "seek") seekTo(step.pos);
      if (step.kind === "play") void video.play().catch(() => {});
      if (step.kind === "pause") video.pause();
    }
  }

  function say({ change, label }) {
    const by = label ?? "Another screen";
    if (change === "joined") return `${by} joined`;
    if (change === "left") return `${by} left`;
    if (change === "sought") return `Skipped to ${clockTime(current.pos)} by ${by}`;
    if (change === "rate") return `${current.rate}× by ${by}`;
    if (change === "title") return `Next title by ${by}`;
    return `${change === "paused" ? "Paused" : "Resumed"} by ${by}`;
  }

  function refresh() {
    const { available, reason, live } = togetherNow();
    button.disabled = !available && id === null;
    button.title = button.disabled ? reason ?? "" : "";
    button.textContent = id !== null ? "Leave watch together" : live ? "Join" : "Watch together";
    label.hidden = current === null;
    if (current) label.textContent = `Together · ${current.members.map((m) => m.label).join(", ")}`;
  }

  function end(message) {
    if (message) show(message);
    id = null;
    current = null;
    clearInterval(drift);
    drift = null;
    refresh();
  }

  function enter(answer) {
    if (!answer.ok) return show(answer.body?.reason ?? "Watch together couldn't start");
    id = answer.body.id;
    current = answer.body.session;
    drift ??= setInterval(apply, DRIFT_CHECK_MS);
    refresh();
    apply();
  }

  async function start() {
    const answer = await together({ op: "start", screen, ...own() });
    if (answer.status === 409) return joinLive({ id: answer.body.id, session: answer.body.session });
    enter(answer);
  }

  async function joinLive(live = togetherNow().live) {
    if (!live) return;
    if (live.session.set !== setId) return openForTogether(live.session.set, live.id);
    enter(await together({ op: "join", screen, id: live.id }));
  }

  async function leave() {
    const leaving = id;
    end(null);
    if (leaving !== null) await together({ op: "leave", screen, id: leaving });
  }

  /** The viewer's own move. `settle` holds a scrub's edit until it stops moving. */
  function viewer(change, { settle: settling = false } = {}) {
    if (id === null || !current) return;
    current = acted(current, change, screen, serverNow());
    const send = () => void together({ op: "act", screen, id, change: { ...change, pos: expectedPosition(current, serverNow()) } });
    clearTimeout(settle);
    if (settling) settle = setTimeout(send, SEEK_SETTLE_MS);
    else send();
  }

  onTogether((view, heard) => {
    // A refresh (the stream reopened) carries no edit: take what it found for this session, if anything.
    if (!heard && id !== null) heard = { id, session: view.live?.id === id ? view.live.session : null };
    if (!heard || heard.id !== id) return refresh();
    if (heard.session === null) return end("Watch together ended");
    const before = current;
    current = follow(current, heard.session, screen);
    if (current === before) return;
    const change = before && current.by !== screen ? changeOf(before, current) : null;
    if (change) show(say(change));
    refresh();
    apply();
  });
  video.addEventListener("canplay", apply);
  // The end of a film, or of the last episode: over for everyone. With a next title, autoplay's open() moves the session instead.
  video.addEventListener("ended", () => {
    if (id === null || hasNext) return;
    const ending = id;
    end(null);
    void together({ op: "end", screen, id: ending });
  });
  button.addEventListener("click", () => void (id !== null ? leave() : togetherNow().live ? joinLive() : start()));

  return {
    open(set, { next = null } = {}) {
      setId = set.setId;
      hasNext = next !== null;
      if (id !== null && expecting !== setId) viewer({ set: setId, pos: 0, playing: true });
      expecting = null;
      const joining = takeJoin();
      if (joining !== null && id === null) void together({ op: "join", screen, id: joining }).then(enter);
      refresh();
    },
    viewer,
    clear: () => void leave(),
  };
}
```

  Check the length: it must stay under 200 lines. If `say` pushes it over, move `say` into `together-words.js`.
- [ ] **Hook it into `player.js`.** Each hook is one line, in an existing place:
  - The import: `import { mountTogether } from "./together.js";`.
  - After `const transport = mountTransport(...)`: `const together = mountTogether({ video, button: document.getElementById("together"), label: document.getElementById("together-label"), note: document.getElementById("together-note"), filmTime, seekTo: seekFilmTo, setRate: (rate) => { video.playbackRate = rate; } });`.
  - `openPlayer`, right after `upNext.open(set, options);`: `together.open(set, options);`. `options.next` tells it whether the end of this title is the end of the session.
  - Transport options (`:392-397`):
    - `onPlay: () => { together.viewer({ playing: true }); return playManually(); }`;
    - `onPause: () => { manualPlayRequest++; together.viewer({ playing: false }); }`;
    - `onSeekTo: (seconds) => { const to = skipTo(seconds, 0, runtimeSeconds()); seekFilmTo(to); together.viewer({ pos: to }, { settle: true }); }`;
    - `remember: (name, value) => { remember(name, value); if (name === "speed") together.viewer({ rate: Number(value) }); }`.
  - The seek bar's `change` handler (`:913-928`): after its `seekFilmTo(...)`, add `together.viewer({ pos: <the same seconds> }, { settle: true });`.
  - `teardown()`: `together.clear();`.

  `video.playbackRate` set while following doesn't go through `remember`, so it is never sent back. Transport re-applies its own `chosenRate` on `loadedmetadata` (`transport.js:249`). If that differs from the session, the next drift check sets the rate again; that is acceptable.
- [ ] **Add the markup to `index.html`:**
  - in the tools row (`:162-179`), before `.spacer`: `<button id="together" class="tool" type="button">Watch together</button>`;
  - in the top bar, next to the title: `<span id="together-label" class="together-label" hidden></span>`;
  - in the dialog: `<p id="together-note" class="together-note" role="status" hidden></p>`;
  - in the head: `<link rel="stylesheet" href="/styles/together.css">`.
- [ ] **Write `styles/together.css`.** Use the theme tokens only:
  - `.together-label` is a small caps label in `--accent`;
  - `.together-note` is a pill at the top centre of the picture, on the card's translucent tint, with `--warn` text;
  - `.together-join` is the Home notice line in `--warn`, one line, with a Join text button.
- [ ] **Raise the ceilings** for `player.js` and `app.js` (task 4.3) in `code-standards.test.ts`, with the dated sentence.
- [ ] **Run:** `bun test && bun run typecheck && bun run lint`. Expected: PASS.
- [ ] **Commit:** `feat(web): follow a watch-together session in the player`

### Task 4.3: Join on Home and the title pages

- [ ] **Write failing tests**, `test/together-join.test.ts`:
  - `togetherNotice()` is hidden with no live session. With one, it reads "Watching together: *Film* · TV, Tablet", and clicking Join calls `openForTogether(set, id)`.
  - `joinOr(["film-1"], pill)` shows `pill` until a live session plays `film-1`, then a "Join" pill. When the session ends it shows `pill` again.
- [ ] **Implement `web/public/lib/catalog/together-join.js`:**

```js
/**
 * Join, where a viewer looks for something to watch: one notice line at the
 * top of Home, and Join in place of Play on the page of the title a session
 * is playing. Both update in place as the session changes, because Home
 * doesn't redraw while it is shown for nothing else.
 */

import { el } from "../dom.js";
import { onTogether, openForTogether, titleForTogether, togetherNow } from "../together-now.js";
import { playPill } from "./title-spread.js";

export function togetherNotice() {
  const line = el("p", "together-join");
  line.hidden = true;
  const draw = ({ live }) => {
    line.replaceChildren();
    line.hidden = !live;
    if (!live) return;
    const who = live.session.members.map((m) => m.label).join(", ");
    const join = el("button", "together-join-button", "Join");
    join.type = "button";
    join.addEventListener("click", () => openForTogether(live.session.set, live.id));
    line.append(el("span", null, `Watching together: ${titleForTogether(live.session.set) ?? "a title"} · ${who}`), join);
  };
  draw(togetherNow());
  onTogether(draw);
  return line;
}

/** `pill`, or Join while a live session plays one of `setIds`. */
export function joinOr(setIds, pill) {
  const slot = el("span", "together-slot");
  const draw = ({ live }) => {
    const playing = live && setIds.includes(live.session.set);
    slot.replaceChildren(playing ? playPill("Join", () => openForTogether(live.session.set, live.id)) : pill);
  };
  draw(togetherNow());
  onTogether(draw);
  return slot;
}
```

  The listener is never removed when the page is redrawn away. That leaks one closure per page view, which is acceptable at a household's scale. If a test shows it matters, `onTogether` returns the unsubscribe; call it when the element is disconnected.
- [ ] **Wire it in:**
  - `home-view.js` after `:66`: `main.append(togetherNotice());`.
  - `film-page.js:38`: wrap the existing `playPill(...)` as `joinOr([set.setId], playPill(...))`.
  - `series-page.js:85`: `joinOr(<the series' episode set ids>, playPill(...))`. The ids come from the same run `series-resume.js` reads.
  - `app.js`, once at start-up next to the library session: `setTogetherOpener((setId) => { const set = byId.get(setId); if (set) play(set); }, (setId) => byId.get(setId)?.title ?? null);`.
- [ ] **Run:** `bun test && bun run typecheck && bun run lint`. Expected: PASS.
- [ ] **Check the layout in the preview** (`cd web && bun run preview`, port 8795, no Telegram). The button shows disabled with "Telegram isn't reachable", and the notice is hidden. Use `/browse`, not the Chrome tools. Real behaviour is checked in phase 09.
- [ ] **Commit:** `feat(web): Join on Home and the title pages`

## Todo

- [ ] 4.1 The tab's view and the event
- [ ] 4.2 The follower in the player
- [ ] 4.3 Join on Home and the title pages

## Success criteria

- Every web test passes, including the ratchet with its raised ceilings.
- Following never sends: tests 2 and 7 prove the echo rule and the opened-by-following rule.

## Risks

- **The speed menu's tick follows `chosenRate`, not the session.** While following someone else's speed, the menu can show the old choice. Accepted, and recorded in the plan's review.
- **A drift seek during a conversion** (`seekFilmTo` restarts ffmpeg) is expensive. Drift checks only seek when more than 0.5 s off, and skip while buffering.
