# Phase 04 — Web server: hub, routes, preview stub

**Spec:** §1 liveness, §2 handshake, §9 Web server, §10. **Status:** pending.
**Context:** `web/src/together/hub.ts` (186 lines: no room, so the cast lives beside it), `routes.ts` (89), `test/together-fixture.ts` (`FakeChannel`, which records `parseSession(text)!` and so works with v=2 once phase 02 is in), `test/together-hub.test.ts` (harness :24), `test/together-routes.test.ts` (harness :25), `scripts/preview.ts` (the `report` stub at :92 is the example to copy).

### Task 4.1: Requests are never the shown session

**Files:**
- Modify: `web/src/together/hub.ts` (`live()`), `web/public/lib/together-now.js` (`stillLive`)
- Test: `web/test/together-hub.test.ts`, `web/test/together-now.test.ts`

- [ ] **Step 1: Write the failing tests.**
  - Hub test 1, `"an offer is not the live session"`: the channel holds only an `offer` session. `await hub.live(durationOf)` → `null`.
  - Hub test 2, `"an offer beside a running session leaves that one live"`: session 10 is plain and live; offer 11 is newer. `live()` → `{id: 10}`.
  - together-now test, `"a heard request never becomes the live session"`:
    - with `known.live` set to session 10, `heardTogether({id: 11, session: offer, serverNow})`;
    - `togetherNow().live.id` stays `10`;
    - the listener still receives `{id: 11, session: offer}`, because the caster's ask listens for it.
- [ ] **Step 2: Run** `bun test test/together-hub.test.ts test/together-now.test.ts` → FAIL.
- [ ] **Step 3: Implement.**
  - `live()` skips `isRequest(session)` when picking the newest. Stale requests are still deleted on the way, because `isLive` now handles them.
  - `stillLive(session, now)` returns `null` for a request.
  - In `heardTogether`, a request leaves `known.live` alone: it neither replaces nor clears it. It is still passed to `tell`.
- [ ] **Step 4: Run** → PASS, then the whole `together-*` suite. **Step 5: Commit** `fix(together): a cast request never stands in for the session screens follow`.

### Task 4.2: The cast desk

**Files:**
- Create: `web/src/together/cast-desk.ts`
- Modify: `web/src/together/hub.ts`. Make public what the desk needs; `start` and `queue`'s callers are updated.
- Test: `web/test/together-cast-desk.test.ts` (reuse `FakeChannel`, `deferred`, `settle` from `test/together-fixture.ts`)

**Interfaces — Consumes:** `offered`, `replaced`, `chose`, `CAST_ANSWER_MS` and `isRequest` (phase 02).
**Interfaces — Produces:**
- `TogetherHub`:
  - `create(screen: string, session: Session): Promise<Live>`: the send, `local.add` and `keep` that `start` does today; `start` now calls it.
  - `move(id: number, screen: string, change: Move): Session | null`: the private `queue`, renamed and made public.
  - `withdraw(id: number): Promise<void>`: `channel.remove(id)` then `forget(id)`.
  - `latest(id: number): Promise<Session | null>`: `channel.read(id)` → `parseSession`.
  - `onKept(fn: (id: number, session: Session) => void): () => void`: called from `keep`.
  - `readonly cast: CastDesk`, built in the constructor.
- `type OfferAnswer = { answer: "on" | "busy" | "refused" | "none"; live: Live | null; note: string }`
- `class CastDesk(hub: TogetherHub, now = Date.now, sleep = Bun.sleep)`:
  - `offer(me: Member, own: Playback, profile: string): Promise<OfferAnswer>`:
    - `hub.create(me.screen, offered(randomUUID, me, own, profile, now()))`;
    - then waits.
  - `replace(id, me, own, profile, durationOf): Promise<OfferAnswer>`:
    - if `latest(id)` is no longer a live request, it makes a new `offer` instead;
    - otherwise it moves `replaced(s, me.screen, own, now())` and waits.
  - `cancel(id: number): void`: `void hub.withdraw(id)`.
  - `tracks(id, screen, choice: {audio?: string; text?: string}): Session | null`: `hub.move(id, screen, s => chose(s, screen, choice))`.
  - **Waiting.** The first kept session for `id` whose state is neither `offer` nor `replace` resolves it:
    - `on` → `{answer:"on", live:{id, session}, note:""}`;
    - `busy` or `refused` → that answer, with `note`.
  - **At `CAST_ANSWER_MS` with nothing heard:** read `latest(id)` once.
    - Still a request: `withdraw`, then `{answer:"none", live:null, note:""}`.
    - Answered meanwhile: that answer.

- [ ] **Step 1: Write the failing tests.**
  1. `"an idle TV's accept answers the offer"`:
     - `offer()`; the TV writes `accepted(...)` through `channel` and `hub.heard(id, text)`;
     - the answer is `on` and `live.session.cast.tv === "tv-1"`.
  2. `"a busy TV answers with its title"`: the TV writes `busied(s,"tv-1","Dune")` → `{answer:"busy", note:"Dune"}`.
  3. `"no answer in five seconds withdraws the offer"`:
     - fake `sleep` resolves at once and `channel.read` returns the unchanged offer;
     - the answer is `none`, the channel no longer has the message, and `hub.live()` is `null`.
  4. `"an answer missed by push is read at the deadline"`: nothing is heard, and `read` returns an accepted session → `on`.
  5. `"replace after the offer went stale offers anew"`:
     - the clock moves 61 s;
     - `replace()` sends a second message, whose `cast.state` is `"offer"`.
  6. `"replace on a live busy request asks the TV to switch"`:
     - the message becomes `state:"replace"` with the new `pos` and a fresh `at`;
     - the TV's accept → `on`.
  7. `"cancel deletes the message"`.
  8. `"a remote's track choice is queued"`: `tracks(id, "tab-1", {text:"de"})` → the edit's `cast.text === "de"`, and `audio` is unchanged.
- [ ] **Step 2: Run** `bun test test/together-cast-desk.test.ts` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS, plus `bun test test/together-hub.test.ts test/together-lost-move.test.ts`.
- [ ] **Step 5: Commit** `feat(together): the web server offers a cast and waits for the TV's answer`.

### Task 4.3: Route ops

**Files:**
- Create: `web/src/together/cast-routes.ts`
- Modify: `web/src/together/routes.ts`. Before the final `return bodiless(400)`, it calls `castRoute(...)`. `join` reads `remote: body.remote === true` into `me`.
- Test: `web/test/together-cast-routes.test.ts` (reuse the `harness()` from `together-routes.test.ts`)

**Interfaces — Produces:**
- `castRoute(body: Body, me: Member, id: number | null, hub: TogetherHub, db: Database, reachable: boolean): Promise<PlayerResponse | null>`. It answers:

| op | body | answer |
|---|---|---|
| `offer` | `screen, profile, set, playing, pos, rate` | `503 {reason}` when unreachable. `400` for a bad `profile` (it must be a non-empty string of at most 128 characters) or a failed `change(body, db, true)`. Otherwise `200 {answer, live, note, serverNow}`. |
| `replace` | `+ id` | as `offer` |
| `cancel` | `id` | `204` |
| `tracks` | `id, audio?, text?` | `400` unless at least one is a string of at most 64 characters. `404` when the session is unknown. Otherwise `200 {session, serverNow}`. |

- Stop is the existing `end` op; nothing new is needed for it.

- [ ] **Step 1: Write the failing tests.**
  - `"offer answers on once the TV accepts"`: the test's fake TV accepts on the first `heard`.
  - `"offer without Telegram is 503 with the reason"`.
  - `"offer with no profile is 400"`.
  - `"offer for an unknown set is 400"`.
  - `"join as a remote adds a remote member"`: `remote:true` → the member's `remote === true`.
  - `"tracks with neither field is 400"`.
  - `"cancel is 204 and the message is gone"`.
- [ ] **Step 2: Run** `bun test test/together-cast-routes.test.ts` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS, plus `test/together-routes.test.ts`.
- [ ] **Step 5: Commit** `feat(together): offer, replace, cancel and track routes`.

### Task 4.4: Preview stub with a pretend TV

**Files:**
- Create: `web/scripts/preview-cast.ts`
- Modify: `web/scripts/preview.ts`. When `PREVIEW_CAST` is set, pass `together` and `events`.

**Interfaces — Produces:**
- `previewCast(mode: "accept" | "busy" | "refuse" | "none", events: CatalogEvents): TogetherHub`.
  - An in-memory channel (a `Map<number,string>` implementing `TogetherChannel`, with `available() → true`). Every `send` and `edit` calls `hub.heard(id, text)` on the next tick, standing in for Telegram's push.
  - A pretend TV answers each request 300 ms after it is heard, by `mode`:
    - `accept`: `accepted`, then after 300 ms more, `tracked` with audio `[{id:"0.0",label:"English 5.1"},{id:"1.0",label:"Deutsch"}]` (choice `"0.0"`) and text `[{id:"en",label:"English"},{id:"de",label:"Deutsch"}]` (choice `""`, meaning off);
    - `busy`: `busied(…, "Dune (2021)")`;
    - `refuse`: `refusedBy(…, ageReason("Mia"))`;
    - `none`: nothing.
  - While a session is `on` and playing, the pretend TV echoes the remote's moves only. It never moves on its own.
- In `preview.ts`: `const events = new CatalogEvents()`. `together: process.env.PREVIEW_CAST ? previewCast(mode, events) : undefined`, and `events` goes into `startServer`.

- [ ] **Step 1:** Implement. Then check `PREVIEW_CAST=accept bun run preview` by hand: `curl -s -XPOST localhost:8795/api/together -d '{"op":"offer","screen":"a1","profile":"<a profile id from the copied db>","set":"<a set>","playing":true,"pos":0,"rate":1}'` → `"answer":"on"` within about 400 ms.
- [ ] **Step 2: Commit** `chore(web): the preview can pretend to be a TV that answers a cast`.

**Success criteria:** `bun test` passes; `bunx tsc --noEmit -p .` is clean; `code-standards.test.ts` passes, with no new file over 200 lines and `hub.ts` under 200.
