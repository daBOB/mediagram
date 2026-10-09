import { afterEach, beforeEach, expect, test } from "bun:test";
import { browserEnvironment } from "./support/player-environment";
import {
  heardTogether, inTogether, onLeaveTogether, onTogether, openForTogether, refreshTogether, screen, serverNow,
  setForTogether, setInTogether, setTogetherOpener, takeJoin, together, togetherNow,
} from "../public/lib/together-now.js";
import type { Session } from "../public/lib/together/session.js";

let env: ReturnType<typeof browserEnvironment>;
const sets = new Map([["film-1", { setId: "film-1", title: "Film", duration: 600 }]]);
const opened: string[] = [];

const session = (over: Partial<Session> = {}): Session => ({
  session: "s-1", set: "film-1", playing: true, pos: 10, rate: 1, at: 0, by: "tv",
  members: [{ screen: "tv", label: "TV" }], ended: false, ...over,
});

function answerGet(body: unknown, status = 200) {
  env.respondWith(async () => (status === 200 ? Response.json(body) : new Response(null, { status })));
}

beforeEach(async () => {
  env = browserEnvironment();
  opened.length = 0;
  setTogetherOpener((set) => { opened.push(set.setId); }, (setId) => sets.get(setId) as never ?? null);
  // The module is one per tab: every test starts it from a read that found nothing.
  answerGet({ available: true, reason: null, live: null, serverNow: 0 });
  await refreshTogether();
  takeJoin();
});

afterEach(() => env.restore());

test("a read stores what the server said and how far its clock is from this one", async () => {
  env.advance(1_000);
  answerGet({ available: true, reason: null, live: { id: 7, session: session({ at: 3_000 }) }, serverNow: 4_500 });
  await refreshTogether();
  expect(togetherNow()).toEqual({ available: true, reason: null, live: { id: 7, session: session({ at: 3_000 }) }, offset: 3_500 });
  expect(serverNow()).toBe(4_500);
});

test("a server with no watch together reads as unreachable, and a failed read keeps what was known", async () => {
  answerGet(null, 404);
  await refreshTogether();
  expect(togetherNow()).toMatchObject({ available: false, reason: "Telegram isn't reachable", live: null });
  answerGet({ available: true, reason: null, live: { id: 7, session: session() }, serverNow: 0 });
  await refreshTogether();
  env.respondWith(async () => { throw new TypeError("offline"); });
  await refreshTogether();
  expect(togetherNow().live?.id).toBe(7);
});

test("a heard session becomes the live one, and only its own end clears it", () => {
  heardTogether({ id: 7, session: session(), serverNow: 0 });
  expect(togetherNow().live).toEqual({ id: 7, session: session() });
  heardTogether({ id: 6, session: null, serverNow: 0 });
  expect(togetherNow().live?.id).toBe(7);
  heardTogether({ id: 7, session: null, serverNow: 0 });
  expect(togetherNow().live).toBeNull();
});

test("a session heard ended or stale is over here, whatever the server still held", () => {
  heardTogether({ id: 7, session: session(), serverNow: 0 });
  heardTogether({ id: 7, session: session({ ended: true }), serverNow: 0 });
  expect(togetherNow().live).toBeNull();
  heardTogether({ id: 7, session: session(), serverNow: 0 });
  // Ten minutes into a ten-minute film: more than two minutes past its end.
  heardTogether({ id: 7, session: session({ pos: 600, at: 0 }), serverNow: 121_000 });
  expect(togetherNow().live).toBeNull();
});

test("an event says the server reaches Telegram, so Join stops being greyed", async () => {
  answerGet(null, 503);
  await refreshTogether();
  heardTogether({ id: 7, session: session(), serverNow: 0 });
  expect(togetherNow()).toMatchObject({ available: true, reason: null });
});

test("listeners hear the view and what was heard, null after a read", async () => {
  const calls: unknown[] = [];
  const stop = onTogether((view, heard) => calls.push([view.live?.id ?? null, heard]));
  heardTogether({ id: 7, session: session(), serverNow: 0 });
  await refreshTogether();
  stop();
  heardTogether({ id: 7, session: null, serverNow: 0 });
  expect(calls).toEqual([[7, { id: 7, session: session() }], [null, null]]);
});

test("this tab is in a session as its player says, whatever a read finds, and its leaving is told", async () => {
  let left = 0;
  onLeaveTogether(() => { left++; });
  setInTogether(true);
  answerGet({ available: false, reason: "Telegram isn't reachable", live: null, serverNow: 0 });
  await refreshTogether();
  expect(inTogether()).toBe(true);
  setInTogether(false);
  expect([inTogether(), left]).toEqual([false, 1]);
  expect(screen).toMatch(/^[A-Za-z0-9-]{1,64}$/);
});

test("a read asked before a session was heard, as a join's answer is, keeps that session", async () => {
  let answer!: (response: Response) => void;
  env.respondWith(() => new Promise<Response>((done) => { answer = done; }));
  const reading = refreshTogether();
  heardTogether({ id: 7, session: session(), serverNow: 0 });
  answer(Response.json({ available: true, reason: null, live: null, serverNow: 0 }));
  await reading;
  expect(togetherNow().live?.id).toBe(7);
  answerGet({ available: true, reason: null, live: null, serverNow: 0 });
  await refreshTogether();
  expect(togetherNow().live).toBeNull();
});

test("a move is posted as JSON that outlives the page, and never throws", async () => {
  env.respondWith(async () => Response.json({ id: 7 }, { status: 409 }));
  expect(await together({ op: "start" })).toEqual({ ok: false, status: 409, body: { id: 7 } });
  const sent = env.requests.at(-1)!;
  expect(sent.url).toBe("/api/together");
  expect(sent.options).toMatchObject({ method: "POST", keepalive: true, headers: { "content-type": "application/json" }, body: "{\"op\":\"start\"}" });
  env.respondWith(async () => new Response(null, { status: 204 }));
  expect(await together({ op: "leave" })).toEqual({ ok: true, status: 204, body: null });
  env.respondWith(async () => { throw new TypeError("offline"); });
  expect(await together({ op: "leave" })).toEqual({ ok: false, status: 0, body: { reason: "Telegram isn't reachable" } });
});

test("a title is opened through the app, and the join it carries is taken once", () => {
  openForTogether("film-1", 7);
  expect(opened).toEqual(["film-1"]);
  expect(takeJoin()).toBe(7);
  expect(takeJoin()).toBeNull();
  expect(setForTogether("film-1")?.title).toBe("Film");
});

test("a title this profile can't see is not opened, and leaves no join behind", () => {
  openForTogether("grown-ups-only", 7);
  expect(opened).toEqual([]);
  expect(takeJoin()).toBeNull();
  expect(setForTogether("grown-ups-only")).toBeNull();
});
