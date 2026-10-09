# Phase 03 — Web server: channel, hub, routes, push

**Spec:** `../spec.md` §1, §4 (Web), §5. **Status:** pending. **Depends on:** phases 01 and 02.

## Context

- **No logic is decided twice.** The server imports phase 02's modules (`../../public/lib/together/*.js`), as `src/transcode/video-copy.ts` imports `public/lib/playable.js`.
- **The server is the hub for its tabs.** It keeps the latest session it knows, sends every tab's move through one sender that keeps only the latest write, and forwards every edit it hears over the existing SSE stream. **The tabs themselves do the following** (phase 04).
- **Telegram facts** (from `web/src/telegram/state-channel.ts` and `channel-index/find-newest-channel-index.ts`):
  - `telegram.peer` is an `Api.InputPeerChannel`.
  - The calls are `getMessages(peer, { limit })`, `editMessage(peer, { message: id, text })` and `deleteMessages(peer, [id], { revoke: true })`.
  - There is no `sendMessage` yet; GramJS has `sendMessage(peer, { message, silent })`, which returns `Api.Message`. Channel posts carry `post === true`.
  - teleproto auto-sleeps through a short flood wait (`Telegram.open` leaves `floodSleepThreshold` at its default). A longer one throws `errors.FloodWaitError` with `.seconds` (`link-stats.ts:59-63`).
- **Line ceilings** (`web/test/code-standards.test.ts`):
  - `src/index.ts` is at 445 of 446, and this phase adds 3 lines. Raise its ceiling to 448 with a dated sentence in the comment, as the comment's history does for wiring.
  - Every new file must stay under 200 lines.

## Files

- Modify: `web/src/telegram/channel-captions.ts`: add `TOGETHER_PREFIX`.
- Modify: `web/src/telegram/updates.ts`: `ChannelUpdate.id`, the `"together"` event, and classify.
- Modify: `web/src/telegram/channel-events.ts`: put `id` on updates, and deliver together at once.
- Modify: `web/src/application/lifecycle.ts`: `LibraryUpdates` routes together to a sink.
- Modify: `web/src/application/telegram-binding.ts`: `UpdatesBinding.setTogether`.
- Create: `web/src/telegram/together-channel.ts`: `TelegramTogetherChannel`.
- Create: `web/src/together/hub.ts`: `TogetherHub`.
- Create: `web/src/together/routes.ts`: `togetherRoute`.
- Modify: `web/src/routes.ts`: the `together?` option, dispatched before the GET gate (`:236`).
- Modify: `web/src/catalog-events.ts`: `together(id, session, serverNow)`.
- Modify: `web/src/index.ts`: wiring, plus the ceiling in `test/code-standards.test.ts`.
- Create: `web/test/fixtures/channel-updates/together.json`
- Modify: `web/test/channel-updates-fixtures.test.ts` and `web/test/channel-events.test.ts`
- Create: `web/test/together-hub.test.ts` and `web/test/together-routes.test.ts`

## Interfaces

- **Consumes** from phase 02:
  - `parseSession` and `writeSession`;
  - `started`, `joined`, `left` and `acted`;
  - `isLive`;
  - the types `Session`, `Member` and `Playback`.
- **Produces** for phase 04 (the browser):
  - `GET /api/together` → `200 {available: boolean, reason: string|null, live: {id: number, session: Session}|null, serverNow: number}`
  - `POST /api/together`, JSON, one of:
    - `{op:"start", screen, set, playing, pos, rate}` → `200 {id, session, serverNow}`. When a live session exists, it answers `409 {id, session, serverNow}` with that session.
    - `{op:"join", id, screen}` → `200 {id, session, serverNow}`, or `404` if it's gone.
    - `{op:"act", id, screen, change:{set?, playing?, pos?, rate?}}` → `200 {session, serverNow}`, or `404`.
    - `{op:"leave", id, screen}` → `204`.
    - `{op:"end", id, screen}` → `204`. This is the end of the last title: the session is over for everyone.
    - Any op while Telegram is unavailable → `503 {reason:"Telegram isn't reachable"}`. A bad body → `400`.
  - **SSE:** `event: together`, `data: {"id":n,"session":Session|null,"serverNow":n}`. A `null` session means the session is over.
  - The server sets every browser member's `label` to `"Browser"` itself.
- **Produces** for phase 05: the fixture file `channel-updates/together.json`, which the Rust classifier must also pass.

## Tasks

### Task 3.1: Classify together updates, delivered without the debouncer

- [ ] **Write `web/test/fixtures/channel-updates/together.json`.** It has the same case shape as `classify.json`:

```json
[
  {"name":"a new session message is together","context":{"channel":3816522481,"ownDevice":"laptop"},
   "update":{"kind":"new","channel":3816522481,"id":901,"caption":"#mlib-together v=1\n{}"},"expect":"together"},
  {"name":"an edited session message is together","context":{"channel":3816522481,"ownDevice":"laptop"},
   "update":{"kind":"edit","channel":3816522481,"id":901,"caption":"#mlib-together v=1\n{}"},"expect":"together"},
  {"name":"together on another channel is nothing","context":{"channel":3816522481,"ownDevice":"laptop"},
   "update":{"kind":"edit","channel":42,"id":901,"caption":"#mlib-together v=1\n{}"},"expect":null},
  {"name":"a word that only starts the same is nothing","context":{"channel":3816522481,"ownDevice":"laptop"},
   "update":{"kind":"new","channel":3816522481,"id":902,"caption":"#mlib-togetherness"},"expect":null},
  {"name":"a service message is nothing","context":{"channel":3816522481,"ownDevice":"laptop"},
   "update":{"kind":"new","channel":3816522481,"id":903,"service":true},"expect":null}
]
```

- [ ] **Add a loop** for `together.json` in `channel-updates-fixtures.test.ts`, the same as the classify loop. Run `bun test test/channel-updates-fixtures.test.ts` and see it FAIL with `null` vs `"together"`.
- [ ] **Implement:**
  - `channel-captions.ts`: `export const TOGETHER_PREFIX = "#mlib-together ";`. The space is part of it, so `#mlib-togetherness` doesn't match.
  - `updates.ts`:
    - `ChannelUpdate` gains `/** The message id, for new and edit. */ id?: number;`.
    - `export type LibraryEvent = "state" | "index" | "together";`.
    - In `classify`, after the service check, add `if (caption.startsWith(TOGETHER_PREFIX)) return "together";`. Do **not** add it to `ORDER`: together is never offered to the debouncer.
- [ ] **Run the fixtures.** Expected: PASS. Rust's `shared_channel_update_fixtures.rs` doesn't read `together.json` until phase 05, so `cargo test` stays green.
- [ ] **Write a failing test** in `channel-events.test.ts`: `"a together edit reaches its sink at once, with its id and text"`.
  - Use the existing `edited(text)` helper (message id 7). Also feed a state edit to show the state event still waits for its window.
  - Assert that `onEvent` received `("together", { id: 7, text })` before any `clock` advance.
- [ ] **Implement, in `channel-events.ts`:**
  - `toChannelUpdate` sets `id: message.id` on both message branches.
  - The callback becomes `onEvent: (event: LibraryEvent, heard?: { id: number; text: string }) => void`.
  - The handler, before `debouncer.offer`:

```ts
    if (event === "together") {
      // Watch together is minded in seconds, not windows: every edit is the session's whole state, and the newest wins.
      if (update.id !== undefined) onEvent(event, { id: update.id, text: update.caption ?? "" });
      return;
    }
```

- [ ] **`lifecycle.ts`:**
  - `LibraryUpdates` constructor gains a third parameter, `private readonly together: (id: number, text: string) => void = () => {}`. Its `subscribe` type becomes `(event: (kind: LibraryEvent, heard?: { id: number; text: string }) => void) => () => void`.
  - In `start()`, first thing in the callback: `if (event === "together") { if (heard) this.together(heard.id, heard.text); return; }`.
- [ ] **`telegram-binding.ts`:**
  - `UpdatesBinding` gains `private together: (id: number, text: string) => void = () => {}` and `setTogether(sink) { this.together = sink; }`, documented like `setFollower`.
  - `start()` passes `this.together` as `LibraryUpdates`' third argument. The arrow passing `onEvent` already forwards both arguments.
- [ ] **Run:** `bun test test/channel-events.test.ts test/channel-updates-fixtures.test.ts test/application-lifecycle.test.ts && bun run typecheck`. Expected: PASS.
- [ ] **Commit:** `feat(web): hear watch-together edits at once`

### Task 3.2: The channel adapter

- [ ] **Create `web/src/telegram/together-channel.ts`:**

```ts
/**
 * Watch-together session messages, on the library channel: one unpinned text
 * message per session, edited in place.
 *
 * Found by reading the newest messages, never by search. A freshly sent
 * message is never found by search, and `#mlib-together` is read as the
 * hashtag `#mlib` (see `state-channel.ts`). Not pinned: pins are flood-limited.
 */

import { errors } from "teleproto";
import type { TelegramConnection } from "./connection";
import { TOGETHER_PREFIX } from "./channel-captions";
import type { EditOutcome, TogetherChannel } from "../together/hub";

/** How far back a screen looks for a session: one history read. */
const RECENT = 100;

const rpcName = (error: unknown): string | undefined => (error as { errorMessage?: string } | null)?.errorMessage;

export class TelegramTogetherChannel implements TogetherChannel {
  constructor(private readonly connection: TelegramConnection) {}

  available(): boolean {
    return this.connection.current() !== null;
  }

  async recent(): Promise<{ id: number; text: string }[]> {
    const telegram = await this.connection.ready();
    if (!telegram) return [];
    const messages = await telegram.client.getMessages(telegram.peer, { limit: RECENT });
    return messages
      .filter((m) => m.post === true && (m.message ?? "").startsWith(TOGETHER_PREFIX))
      .map((m) => ({ id: m.id, text: m.message ?? "" }));
  }

  async read(id: number): Promise<string | null> {
    const telegram = await this.connection.ready();
    if (!telegram) return null;
    const [message] = await telegram.client.getMessages(telegram.peer, { ids: [id] });
    return message?.message ?? null;
  }

  async send(text: string): Promise<number> {
    const telegram = await this.connection.ready();
    if (!telegram) throw new Error("Telegram isn't reachable");
    const sent = await telegram.client.sendMessage(telegram.peer, { message: text, silent: true });
    return sent.id;
  }

  async edit(id: number, text: string): Promise<EditOutcome> {
    const telegram = await this.connection.ready();
    if (!telegram) throw new Error("Telegram isn't reachable");
    try {
      await telegram.client.editMessage(telegram.peer, { message: id, text });
      return "ok";
    } catch (error) {
      if (error instanceof errors.FloodWaitError) return { wait: error.seconds };
      if (rpcName(error) === "MESSAGE_NOT_MODIFIED") return "ok";
      if (rpcName(error) === "MESSAGE_ID_INVALID") return "gone";
      throw error;
    }
  }

  async remove(id: number): Promise<void> {
    const telegram = await this.connection.ready();
    if (telegram) await telegram.client.deleteMessages(telegram.peer, [id], { revoke: true });
  }
}
```

- [ ] **Run `bun run typecheck`.** If teleproto's `getMessages` option is spelled differently for ids, use what `node_modules/teleproto` declares (`ids` in GramJS). The repo hasn't used it before.
- [ ] **Commit:** `feat(web): watch-together channel adapter`. It is untested here, because, like `state-channel.ts`, it is the part that needs a real account. The phase 01 probe and the phase 09 device walk exercise it.

### Task 3.3: The hub

- [ ] **Write the failing tests** in `web/test/together-hub.test.ts`. Use a `FakeChannel` that records `sent`, `edits` and `removed`, serves `recent` from an array, and lets each `edit` be answered by a queued outcome or a deferred promise. Pass a fixed `now` and a `sleep` that resolves at once but records its argument:
  1. `start` sends one message, announces it and returns `conflict: false`. A second `start` while it is live returns `conflict: true` with the first, and sends nothing.
  2. `live` skips a stale session (playing, 200 s past a 100 s duration), removes it, and returns the newest live one.
  3. Three `act` calls in a row, with the first edit held: exactly two edits, and the second holds the third change. Latest-wins.
  4. An edit answered `{wait: 5}` sleeps 5000 ms and sends the latest text again. If a foreign `heard()` arrives during the wait, nothing is resent.
  5. `heard()` from a foreign screen announces. An own echo while an own write is still queued doesn't.
  6. An edit answered `"gone"` announces `(id, null)` and forgets the session.
  7. The last `leave` sends an edit with `ended: true`, then removes the message. `end` does the same while others are still members.
  8. `recheck()` reads each known session that a local screen is in. A changed text is announced. A missing message is announced as `(id, null)`. A read that throws announces nothing and doesn't throw.
- [ ] **Run them and see them fail** (module missing).
- [ ] **Implement `web/src/together/hub.ts`:**

```ts
/**
 * The web server's side of watch together: the one place its tabs' moves
 * reach the channel, and the channel's edits reach its tabs. The tabs do the
 * following (`public/lib/playback/together.js`). This keeps the latest
 * session it knows, writes through one sender that keeps only the newest
 * text per message, and forwards every edit it hears.
 */

import { parseSession, writeSession } from "../../public/lib/together/session.js";
import type { Member, Session } from "../../public/lib/together/session.js";
import { acted, finished, joined, left, started } from "../../public/lib/together/moves.js";
import type { Playback } from "../../public/lib/together/moves.js";
import { isLive } from "../../public/lib/together/follow.js";
import { failureMessage } from "../failure-message";

export type EditOutcome = "ok" | "gone" | { wait: number };

export interface TogetherChannel {
  available(): boolean;
  recent(): Promise<{ id: number; text: string }[]>;
  read(id: number): Promise<string | null>;
  send(text: string): Promise<number>;
  edit(id: number, text: string): Promise<EditOutcome>;
  remove(id: number): Promise<void>;
}

export interface Live { id: number; session: Session }
export type DurationOf = (set: string) => number | null;

export class TogetherHub {
  private readonly known = new Map<number, Session>();
  /** Screens whose moves went through this server: their echoes are not news. */
  private readonly local = new Set<string>();
  /** The newest unsent text per message. */
  private readonly outgoing = new Map<number, string>();
  private sending: Promise<void> | null = null;

  constructor(
    private readonly channel: TogetherChannel,
    private readonly announce: (id: number, session: Session | null) => void,
    private readonly now: () => number = Date.now,
    private readonly sleep: (ms: number) => Promise<void> = (ms) => new Promise((done) => setTimeout(done, ms)),
  ) {}

  available(): boolean {
    return this.channel.available();
  }

  /** An edit heard from the channel. */
  heard(id: number, text: string): void {
    const session = parseSession(text);
    if (!session) return;
    if (!this.local.has(session.by)) {
      // Another screen moved after this server queued its own write; sending it now would undo that move.
      this.outgoing.delete(id);
    } else if (this.outgoing.has(id)) {
      return; // this server's newer write is still on its way
    }
    this.keep(id, session);
  }

  /** The newest live session in the channel. Stale ones found on the way are deleted, as the spec asks of whoever reads them. */
  async live(durationOf: DurationOf): Promise<Live | null> {
    let found: Live | null = null;
    for (const { id, text } of await this.channel.recent()) {
      const session = this.outgoing.has(id) ? this.known.get(id) : parseSession(text);
      if (!session) continue;
      if (!isLive(session, durationOf(session.set), this.now())) {
        this.known.delete(id);
        void this.channel.remove(id).catch(() => {});
        continue;
      }
      this.known.set(id, session);
      found ??= { id, session };
    }
    return found;
  }

  async start(me: Member, own: Playback, durationOf: DurationOf): Promise<{ live: Live; conflict: boolean }> {
    const existing = await this.live(durationOf);
    if (existing) return { live: existing, conflict: true };
    const session = started(crypto.randomUUID(), me, own, this.now());
    const id = await this.channel.send(writeSession(session));
    this.local.add(me.screen);
    this.keep(id, session);
    return { live: { id, session }, conflict: false };
  }

  async join(id: number, me: Member): Promise<Live | null> {
    const text = this.outgoing.has(id) ? null : await this.channel.read(id);
    const current = text === null ? this.known.get(id) : parseSession(text);
    if (!current || current.ended) return null;
    this.local.add(me.screen);
    return { id, session: this.write(id, joined(current, me)) };
  }

  act(id: number, screen: string, change: Partial<Playback>): Session | null {
    const current = this.known.get(id);
    if (!current || current.ended) return null;
    this.local.add(screen);
    return this.write(id, acted(current, change, screen, this.now()));
  }

  async leave(id: number, screen: string): Promise<void> {
    const current = this.known.get(id);
    if (!current) return;
    const next = this.write(id, left(current, screen));
    this.local.delete(screen);
    if (!next.ended) return;
    await this.sending;
    this.known.delete(id);
    await this.channel.remove(id).catch(() => {});
  }

  /** The end of the last title: over for everyone, not just this screen. */
  async end(id: number, screen: string): Promise<void> {
    const current = this.known.get(id);
    if (!current) return;
    this.write(id, finished(current, screen));
    await this.sending;
    this.known.delete(id);
    await this.channel.remove(id).catch(() => {});
  }

  /**
   * Re-reads every session a local screen is in. A push missed while the
   * connection was down is not replayed (`channel-events.ts`), so this is how
   * a tab that kept playing learns what it missed.
   */
  async recheck(): Promise<void> {
    for (const [id, session] of [...this.known]) {
      if (!session.members.some((m) => this.local.has(m.screen)) || this.outgoing.has(id)) continue;
      const text = await this.channel.read(id).catch(() => undefined);
      if (text === undefined) continue;
      if (text === null) {
        this.known.delete(id);
        this.announce(id, null);
      } else if (JSON.stringify(parseSession(text)) !== JSON.stringify(session)) {
        // Compared parsed: Kotlin and JS print the same numbers differently.
        this.heard(id, text);
      }
    }
  }

  private write(id: number, session: Session): Session {
    this.keep(id, session);
    this.outgoing.set(id, writeSession(session));
    this.sending ??= this.drain().finally(() => { this.sending = null; });
    return session;
  }

  private keep(id: number, session: Session): void {
    this.known.set(id, session);
    this.announce(id, session.ended ? null : session);
  }

  private async drain(): Promise<void> {
    for (let next = this.outgoing.entries().next(); !next.done; next = this.outgoing.entries().next()) {
      const [id, text] = next.value;
      this.outgoing.delete(id);
      try {
        const outcome = await this.channel.edit(id, text);
        if (outcome === "gone") {
          this.known.delete(id);
          this.announce(id, null);
        } else if (outcome !== "ok") {
          // Flood-limited: try again once allowed, with whatever is newest by then.
          if (!this.outgoing.has(id)) this.outgoing.set(id, text);
          await this.sleep(outcome.wait * 1000);
        }
      } catch (error) {
        console.warn(`together: an edit failed: ${failureMessage(error)}`);
      }
    }
  }
}
```

  Check it against test 4: the resend is queued *before* the sleep, so a foreign `heard()` during the wait deletes it. That is the intended "dropped if another screen moved" rule.
- [ ] **Run `bun test test/together-hub.test.ts`.** Expected: PASS.
- [ ] **Commit:** `feat(web): watch-together hub`

### Task 3.4: Route, SSE event, wiring

- [ ] **Write the failing tests** in `web/test/together-routes.test.ts`. Build the router as `test/state-write-triggers-sync.test.ts:23-35` does (`createRouter({ db, source, together: hub })`, with a `PlayerRequest` that has `contentType: "application/json"`), using `emptyIndex()` plus one inserted playable set, `film-1` (see `test/index-fixture.ts`). The cases:
  - `GET` with the channel unavailable → `{available:false, reason:"Telegram isn't reachable", live:null}`.
  - `POST start` for an unknown set → 400.
  - `POST start` with `rate: 0` → 400.
  - `POST start` OK → 200, with `session.members[0].label === "Browser"`.
  - `POST act` for an unknown id → 404.
  - `POST` with `op: "nope"` → 400.
  - With no `together` option → 404.
- [ ] **Implement `web/src/together/routes.ts`:**

```ts
/** `/api/together`: the browser's whole view of watch together. See `hub.ts`. */

import type { Database } from "bun:sqlite";
import { playableSet } from "../catalog";
import { jsonBody } from "../http/browser-write";
import type { PlayerRequest, PlayerResponse } from "../http/contracts";
import { bodiless } from "../response";
import { json } from "../state/route-shared";
import type { Playback } from "../../public/lib/together/moves.js";
import type { TogetherHub } from "./hub";

const UNREACHABLE = "Telegram isn't reachable";
const SCREEN = /^[A-Za-z0-9-]{1,64}$/;

type Body = Record<string, unknown>;

function change(body: Body, db: Database, whole: boolean): Partial<Playback> | null {
  const out: Partial<Playback> = {};
  if (body.set !== undefined || whole) {
    if (typeof body.set !== "string" || playableSet(db, body.set) === null) return null;
    out.set = body.set;
  }
  if (body.playing !== undefined || whole) {
    if (typeof body.playing !== "boolean") return null;
    out.playing = body.playing;
  }
  if (body.pos !== undefined || whole) {
    if (typeof body.pos !== "number" || !Number.isFinite(body.pos) || body.pos < 0) return null;
    out.pos = body.pos;
  }
  if (body.rate !== undefined || whole) {
    if (typeof body.rate !== "number" || !(body.rate > 0 && body.rate <= 4)) return null;
    out.rate = body.rate;
  }
  return out;
}

export async function togetherRoute(request: PlayerRequest, hub: TogetherHub | undefined, db: Database): Promise<PlayerResponse | null> {
  if (request.path !== "/api/together") return null;
  if (!hub) return bodiless(404);
  const headOnly = request.method === "HEAD";
  const reply = (body: unknown, code = 200) => json(JSON.stringify(body), headOnly, code);
  const durationOf = (set: string) => playableSet(db, set)?.duration ?? null;
  if (request.method === "GET" || headOnly) {
    const available = hub.available();
    const live = available ? await hub.live(durationOf).catch(() => null) : null;
    return reply({ available, reason: available ? null : UNREACHABLE, live, serverNow: Date.now() });
  }
  if (request.method !== "POST") return bodiless(405);
  if (!hub.available()) return reply({ reason: UNREACHABLE }, 503);
  const body = jsonBody(request.body);
  const screen = typeof body?.screen === "string" && SCREEN.test(body.screen) ? body.screen : null;
  if (!body || !screen) return bodiless(400);
  const me = { screen, label: "Browser" };
  const id = Number(body.id);
  try {
    if (body.op === "start") {
      const own = change(body, db, true);
      if (!own) return bodiless(400);
      const { live, conflict } = await hub.start(me, own as Playback, durationOf);
      return reply({ ...live, serverNow: Date.now() }, conflict ? 409 : 200);
    }
    if (!Number.isInteger(id) || id <= 0) return bodiless(400);
    if (body.op === "join") {
      const live = await hub.join(id, me);
      return live ? reply({ ...live, serverNow: Date.now() }) : bodiless(404);
    }
    if (body.op === "act") {
      const moved = typeof body.change === "object" && body.change !== null ? change(body.change as Body, db, false) : null;
      if (!moved) return bodiless(400);
      const session = hub.act(id, screen, moved);
      return session ? reply({ session, serverNow: Date.now() }) : bodiless(404);
    }
    if (body.op === "leave" || body.op === "end") {
      await (body.op === "leave" ? hub.leave(id, screen) : hub.end(id, screen));
      return bodiless(204);
    }
    return bodiless(400);
  } catch {
    return reply({ reason: UNREACHABLE }, 503);
  }
}
```

- [ ] **`routes.ts`:**
  - Add `together?: TogetherHub;` to `RouterOptions`, with a doc line.
  - Dispatch it **before** the GET-only gate at `:236`, next to the transcode POST: `const together = await togetherRoute(request, options.together, options.db); if (together) return together;`.
  - Unsafe browser writes are already refused before that point (`refuseUnsafeBrowserWrite`).
- [ ] **`catalog-events.ts`:** add

```ts
  /** Tells every open page what a watch-together session now is; `null` when it is over. */
  together(id: number, session: Session | null, serverNow: number): void {
    this.send(`event: together\ndata: ${JSON.stringify({ id, session, serverNow })}\n\n`);
  }
```

  Import the `Session` type from `../public/lib/together/session.js`. The file is at 98 lines, so there is room.
- [ ] **`index.ts`:** after `await updatesBinding.start();` (`:251`), add:

```ts
    const together = new TogetherHub(new TelegramTogetherChannel(connection), (id, session) => events.together(id, session, Date.now()));
    updatesBinding.setTogether((id, text) => together.heard(id, text));
    resources.timers.push(setInterval(() => void together.recheck(), TOGETHER_RECHECK_MS));
```

  `TOGETHER_RECHECK_MS = 30_000` is a constant beside the hub, exported from `hub.ts`.

  Then add `together,` to the `startServer({ … })` options, plus the two imports (they go at the top, which counts against the ceiling). Raise `"src/index.ts"` in `code-standards.test.ts` to the new length. Add a dated sentence to the comment, for example: "Raised 2026-10-xx for `src/index.ts`, by N, for watch together's hub and push wiring."
- [ ] **Run:** `bun test && bun run typecheck && bun run lint`. Expected: everything passes.
- [ ] **Commit:** `feat(web): /api/together and the together event`

## Todo

- [ ] 3.1 Classify, plus immediate delivery
- [ ] 3.2 Channel adapter
- [ ] 3.3 Hub
- [ ] 3.4 Route, SSE and wiring

## Success criteria

- Every web test passes, including the line ratchet.
- The routes reject bad bodies with 400 and foreign origins with 403 (the existing guard).
- A session's message is written by one sender, and the newest write wins.

## Risks

- **`getMessages(peer, {ids})` and `sendMessage` are new to this repo.** Check them against teleproto's typings at implementation time. The probe used them first.
- **A flood wait under the auto-sleep threshold blocks `edit` for up to that long.** Latest-wins covers it: moves made meanwhile replace the queued text.

## Security

- **Trust boundary.** `/api/together` is reachable wherever the player is, the same reach as the watch-state writes. It writes only to the library channel, only in the `#mlib-together` shape, and only for sets in the catalog.
- **Validation.** The body is size-capped (64 KiB, `server.ts:46`). `screen` must match `[A-Za-z0-9-]{1,64}`, `rate` must be in (0, 4], and `pos` must be ≥ 0.

## Pre-flight rulings (binding; they override the text above where they differ)

- **3.1:** setting `ChannelUpdate.id` changes the two `toChannelUpdate` expectations in `web/test/channel-events.test.ts` (~lines 42, 48): update them (`id: 7` / `id: 8`).
- **Availability (spec §3/§5 "Telegram unreachable"):** `available()` is signed in **and** connected (`current()?.connected !== false`, `web/src/telegram/client.ts` ~163). `GET /api/together` also reports `available:false` with the reason when `live()` throws.
- **Discovery keeps pushed sessions (spec §1 "It also keeps any session it heard about through a push"):** the hub remembers the newest session id it heard by push or read. `live()` returns the newest live session from the 100-message read; if the remembered one is missing from that read (a busy channel pushed it out), it reads it by id and uses it while it's still live. So a busy channel can't let a second session start beside a live one.
- **Flood wait follows the spec (§5: "keeps its own state and sends the latest one once the wait ends. Earlier ones are dropped"):** the single sender keeps only the latest own write. When the wait ends it sends it **re-based** on the newest known session: playback fields (`set, playing, pos, rate, at, by`) come from the own write; `session, members, ended` come from the newest known. If the newest known is ended or gone, it drops the write. A foreign edit arriving during the wait no longer cancels the own write. Add a hub test: a foreign join during a flood wait → after the wait the edit carries the own playback **and** the joiner in `members`.
- **`join` on a deleted message:** `if (text === null && !this.outgoing.has(id)) return null` → the route answers 404, as stated above.
- Extract the duplicated `leave`/`end` tail into one helper. Put `TOGETHER_RECHECK_MS` in hub.ts. Hub test 4 needs a held `sleep`, and FakeChannel `send` must feed `recent`.
- Ceilings: index.ts will be ≈451 (measure, don't trust 448). The GET gate is in `src/routes.ts` ~138, not 236. Cite `docs/mlib-spec.md` §7b in comments, not "the spec".
