/**
 * The watch-together follower (`playback/together.js`) mounted on its own,
 * over a fake `<video>`: what it does when the session moves, and what it
 * sends when the viewer does. The player's own call paths into it are in
 * `together-player-controls.test.ts`.
 */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { browserEnvironment, settle } from "./support/player-environment";
import { mountTogether } from "../public/lib/playback/together.js";
import {
  heardTogether, inTogether, openForTogether, refreshTogether, screen, setTogetherOpener, takeJoin, togetherNow,
} from "../public/lib/together-now.js";
import type { Session } from "../public/lib/together/session.js";

let env: ReturnType<typeof browserEnvironment>;
let follower: ReturnType<typeof mountTogether>;
let converting = false;
/** The session the server holds, which a join answers with this screen added. */
let held: Session;
const seeks: number[] = [];
const opened: string[] = [];
const film = (setId: string) => ({ setId, title: setId, kind: "movie", duration: 600 });
const sets = new Map(["film-1", "film-2"].map((id) => [id, film(id)]));
const TV = { screen: "tv", label: "TV" };
const ME = { screen, label: "Browser" };

const session = (over: Partial<Session> = {}): Session => ({
  session: "s-1", set: "film-1", playing: true, pos: 0, rate: 1, at: 0, by: "tv", members: [TV], ended: false, ...over,
});
const note = () => env.node("together-note");
const button = () => env.node("together");
const posts = () => env.requests
  .filter((request) => request.url === "/api/together" && request.options?.method === "POST")
  .map((request) => JSON.parse(String(request.options!.body)));

beforeEach(async () => {
  env = browserEnvironment();
  converting = false;
  held = session();
  seeks.length = 0;
  opened.length = 0;
  env.video.readyState = 4;
  setTogetherOpener((set) => { opened.push(set.setId); }, (setId) => sets.get(setId) as never ?? null);
  env.respondWith(async (_url, options) => {
    const body = options?.body ? JSON.parse(String(options.body)) : null;
    if (!body) return Response.json({ available: true, reason: null, live: null, serverNow: Date.now() });
    if (body.op === "join") return Response.json({ id: body.id, session: { ...held, by: screen, members: [TV, ME] }, serverNow: Date.now() });
    if (body.op === "act") return Response.json({ session: session(), serverNow: Date.now() });
    return new Response(null, { status: 204 });
  });
  // The module is one per tab: every test starts it from a read that found nothing.
  await refreshTogether();
  takeJoin();
  follower = mountTogether({
    video: env.video, button: button(), label: env.node("together-label"), note: note(),
    filmTime: () => env.video.currentTime,
    seekTo: (pos: number) => { seeks.push(pos); env.video.currentTime = pos; },
    setRate: (rate: number) => { env.video.playbackRate = rate; },
    converting: () => converting,
  });
});

afterEach(async () => {
  follower.clear();
  await settle();
  env.restore();
});

/** In the TV's session on film-1, playing from 0 since now, with the join posted. */
async function joined(over: Partial<Session> = {}) {
  held = session({ at: Date.now(), ...over });
  heardTogether({ id: 7, session: held, serverNow: Date.now() });
  follower.open(film("film-1"), { next: null });
  button().fire("click");
  await settle();
  env.requests.length = 0;
  seeks.length = 0;
}

/** What the TV did next, as the stream says it. */
function heardFromTv(over: Partial<Session>) {
  heardTogether({ id: 7, session: session({ members: [TV, ME], at: Date.now(), ...over }), serverNow: Date.now() });
}

test("another screen's pause pauses this one and says so for two seconds", async () => {
  await joined();
  expect(env.video.paused).toBe(false);
  heardFromTv({ playing: false, pos: 0 });
  expect(env.video.paused).toBe(true);
  expect(note().textContent).toBe("Paused by TV");
  env.advance(1_999);
  expect(note().textContent).toBe("Paused by TV");
  env.advance(1);
  expect(note().textContent).toBe("");
  expect(posts()).toEqual([]);
});

test("this screen's own edit coming back changes nothing", async () => {
  await joined();
  follower.viewer({ playing: false });
  env.video.pause();
  const sent = posts().length;
  heardTogether({ id: 7, session: session({ by: screen, members: [TV, ME], playing: true, pos: 3, at: Date.now() }), serverNow: Date.now() });
  expect(env.video.paused).toBe(true);
  expect(seeks).toEqual([]);
  expect(note().textContent).toBe("");
  expect(posts()).toHaveLength(sent);
});

test("the viewer's play is posted once, with where it played from", async () => {
  await joined({ playing: false, pos: 12 });
  follower.viewer({ playing: true });
  await settle();
  expect(posts()).toEqual([{ op: "act", screen, id: 7, change: { playing: true, pos: 12 } }]);
});

test("a scrub posts one edit, 300 ms after it stops, with where it stopped", async () => {
  await joined({ playing: false });
  follower.viewer({ pos: 90 }, { settle: true });
  env.advance(100);
  follower.viewer({ pos: 95 }, { settle: true });
  env.advance(100);
  follower.viewer({ pos: 100 }, { settle: true });
  env.advance(299);
  expect(posts()).toEqual([]);
  env.advance(1);
  expect(posts()).toEqual([{ op: "act", screen, id: 7, change: { pos: 100 } }]);
});

test("a drift check seeks a screen more than half a second out, but not one that is buffering", async () => {
  await joined();
  env.video.currentTime = 1.6;
  env.advance(2_000);
  expect(seeks).toEqual([]);
  env.video.currentTime = 3.4;
  env.advance(2_000);
  expect(seeks).toEqual([4]);
  env.video.readyState = 2;
  env.video.currentTime = 0;
  env.advance(2_000);
  expect(seeks).toEqual([4]);
  env.video.readyState = 4;
  env.advance(2_000);
  expect(seeks).toEqual([4, 8]);
});

test("a drift check never seeks a conversion, which would land as far behind every time; an edit still does", async () => {
  await joined();
  converting = true;
  env.advance(2_000);
  expect(seeks).toEqual([]);
  heardFromTv({ pos: 300 });
  expect(seeks).toEqual([300]);
  expect(note().textContent).toBe("Skipped to 5:00 by TV");
});

test("a session that ends, or that a fresh read no longer finds, leaves this screen playing alone", async () => {
  for (const ending of ["event", "read"] as const) {
    await joined();
    if (ending === "event") heardTogether({ id: 7, session: null, serverNow: Date.now() });
    else await refreshTogether();
    expect(note().textContent).toBe("Watch together ended");
    expect(env.node("together-label").hidden).toBe(true);
    expect(env.video.paused).toBe(false);
    env.advance(4_000);
    expect(seeks).toEqual([]);
    expect(posts()).toEqual([]);
  }
});

test("a read that can't reach Telegram keeps this screen in the session, playing on alone", async () => {
  await joined();
  env.respondWith(async () => Response.json({ available: false, reason: "Telegram isn't reachable", live: null, serverNow: Date.now() }));
  await refreshTogether();
  expect(note().textContent).toBe("");
  expect(button().textContent).toBe("Leave watch together");
  expect(button().disabled).toBe(false);
});

test("the end of the last title ends the session for everyone, once; with a title next it doesn't", async () => {
  await joined();
  env.video.fire("ended");
  env.video.fire("ended");
  await settle();
  expect(posts()).toEqual([{ op: "end", screen, id: 7 }]);
  await joined();
  follower.open(film("film-1"), { next: film("film-2") });
  env.requests.length = 0;
  env.video.fire("ended");
  await settle();
  expect(posts()).toEqual([]);
});

test("a title the viewer opens moves the session; one opened because the session moved does not", async () => {
  await joined();
  follower.open(film("film-2"), { next: null });
  await settle();
  expect(posts()).toEqual([{ op: "act", screen, id: 7, change: { set: "film-2", pos: 0, playing: true } }]);
  env.requests.length = 0;
  heardFromTv({ set: "film-1", pos: 0 });
  expect(opened).toEqual(["film-1"]);
  expect(note().textContent).toBe("Next episode by TV");
  env.video.currentTime = 40;
  follower.open(film("film-1"), { next: null });
  await settle();
  expect(posts()).toEqual([]);
  expect(seeks).toEqual([0]);
});

test("a session moving to a title this profile can't see is where this screen leaves", async () => {
  await joined();
  heardFromTv({ set: "grown-ups-only" });
  await settle();
  expect(opened).toEqual([]);
  expect(note().textContent).toBe("Left watch together: that title isn't on this profile");
  expect(posts()).toEqual([{ op: "leave", screen, id: 7 }]);
  expect(button().textContent).toBe("Join");
});

test("the button offers to start, to join, or to leave, and says why it can't", async () => {
  follower.open(film("film-1"), { next: null });
  expect([button().textContent, button().disabled]).toEqual(["Watch together", false]);
  heardTogether({ id: 7, session: session({ set: "grown-ups-only", at: Date.now() }), serverNow: Date.now() });
  expect([button().textContent, button().disabled, button().title]).toEqual(["Join", true, "That title isn't on this profile"]);
  heardTogether({ id: 7, session: session({ at: Date.now() }), serverNow: Date.now() });
  expect([button().textContent, button().disabled]).toEqual(["Join", false]);
  button().fire("click");
  await settle();
  expect([button().textContent, env.node("together-label").textContent]).toEqual(["Leave watch together", "Together · TV, Browser"]);
  button().fire("click");
  await settle();
  expect(posts().at(-1)).toEqual({ op: "leave", screen, id: 7 });
  env.respondWith(async () => Response.json({ available: false, reason: "Telegram isn't reachable", live: null, serverNow: Date.now() }));
  await refreshTogether();
  expect([button().textContent, button().disabled, button().title]).toEqual(["Watch together", true, "Telegram isn't reachable"]);
});

test("starting while another screen started first joins theirs", async () => {
  follower.open(film("film-1"), { next: null });
  env.respondWith(async (_url, options) => {
    const body = JSON.parse(String(options!.body));
    if (body.op === "start") return Response.json({ id: 7, session: session({ at: Date.now() }), serverNow: Date.now() }, { status: 409 });
    return Response.json({ id: 7, session: session({ by: screen, members: [TV, ME], at: Date.now() }), serverNow: Date.now() });
  });
  button().fire("click");
  await settle();
  expect(posts().map((body) => body.op)).toEqual(["start", "join"]);
  expect(button().textContent).toBe("Leave watch together");
});

test("a join the title page asked for is made as the player opens that title", async () => {
  heardTogether({ id: 7, session: session({ at: Date.now() }), serverNow: Date.now() });
  openForTogether("film-1", 7);
  follower.open(film("film-1"), { next: null });
  await settle();
  expect(posts()).toEqual([{ op: "join", screen, id: 7 }]);
  expect(button().textContent).toBe("Leave watch together");
});

test("a join answered after the player closed takes this screen out again, and leaves nothing behind", async () => {
  let answer!: () => void;
  const gate = new Promise<void>((done) => { answer = done; });
  env.respondWith(async (_url, options) => {
    const body = options?.body ? JSON.parse(String(options.body)) : null;
    if (body?.op !== "join") return new Response(null, { status: 204 });
    await gate;
    return Response.json({ id: 7, session: { ...held, by: screen, members: [TV, ME] }, serverNow: Date.now() });
  });
  heardTogether({ id: 7, session: held, serverNow: Date.now() });
  follower.open(film("film-1"), { next: null });
  button().fire("click");
  await settle();
  follower.clear();
  answer();
  await settle();
  expect(posts().map((body) => body.op)).toEqual(["join", "leave"]);
  expect([env.node("together-label").hidden, button().textContent, inTogether()]).toEqual([true, "Join", false]);
  expect([...env.timers.values()].filter((timer) => timer.interval > 0)).toEqual([]);
});

test("a read asked before a join doesn't end the session the join entered", async () => {
  let answerRead!: (response: Response) => void;
  heardTogether({ id: 7, session: held, serverNow: Date.now() });
  env.respondWith(async (_url, options) => {
    const body = options?.body ? JSON.parse(String(options.body)) : null;
    if (!body) return new Promise<Response>((done) => { answerRead = done; });
    return Response.json({ id: 7, session: { ...held, by: screen, members: [TV, ME] }, serverNow: Date.now() });
  });
  const reading = refreshTogether();
  follower.open(film("film-1"), { next: null });
  button().fire("click");
  await settle();
  answerRead(Response.json({ available: true, reason: null, live: null, serverNow: Date.now() }));
  await reading;
  expect([button().textContent, note().textContent]).toEqual(["Leave watch together", ""]);
});

test("a session gone stale under this screen, nobody having moved on from its end, is over", async () => {
  await joined({ pos: 590 });
  env.advance(130_000);
  expect(env.node("together-label").hidden).toBe(false);
  env.advance(2_000);
  expect(note().textContent).toBe("Watch together ended");
  expect([env.node("together-label").hidden, inTogether()]).toEqual([true, false]);
  // Out of the tab's view as well, so nothing offers to join what just ended.
  expect([button().textContent, togetherNow().live]).toEqual(["Watch together", null]);
  expect(posts()).toEqual([]);
});

test("at its title's end this screen waits for the next one: neither an edit nor a drift check starts it again", async () => {
  await joined({ pos: 598 });
  Object.assign(env.video, { currentTime: 600, paused: true, ended: true });
  heardFromTv({ pos: 600, members: [TV, ME, { screen: "tab", label: "Tablet" }] });
  env.advance(4_000);
  expect([env.video.paused, seeks]).toEqual([true, []]);
});

test("a move the server has no session for ends it here; one it can't send says why and plays on", async () => {
  await joined();
  env.respondWith(async () => Response.json({ reason: "Telegram isn't reachable" }, { status: 503 }));
  follower.viewer({ playing: false });
  await settle();
  expect([note().textContent, button().textContent]).toEqual(["Telegram isn't reachable", "Leave watch together"]);
  env.advance(2_000);
  env.respondWith(async () => { throw new TypeError("offline"); });
  follower.viewer({ playing: true });
  await settle();
  expect(note().textContent).toBe("Telegram isn't reachable");
  env.respondWith(async () => new Response(null, { status: 404 }));
  follower.viewer({ pos: 30 }, { settle: true });
  env.advance(300);
  await settle();
  expect([note().textContent, env.node("together-label").hidden, inTogether()]).toEqual(["Watch together ended", true, false]);
  expect([button().textContent, togetherNow().live]).toEqual(["Watch together", null]);
});

test("a title opened in a session, or to join one, starts at the session's place", async () => {
  expect(follower.open(film("film-1"), { next: null })).toBeNull();
  heardTogether({ id: 7, session: session({ pos: 120, at: Date.now() }), serverNow: Date.now() });
  openForTogether("film-1", 7);
  expect(follower.open(film("film-1"), { next: null })).toBe(120);
  await settle();
  env.advance(1_000);
  heardFromTv({ set: "film-2", pos: 30 });
  expect(follower.open(film("film-2"), { next: null })).toBe(30);
  expect(follower.open(film("film-1"), { next: null })).toBe(0);
});

test("at its end, an edit whose place reads on past the end neither seeks nor plays; a seek back is followed", async () => {
  await joined({ pos: 598 });
  env.advance(2_000);
  Object.assign(env.video, { currentTime: 600, paused: true, ended: true });
  seeks.length = 0;
  env.advance(4_000);
  // The TV leaves: its edit keeps the session's place and time, which now reads 604 s into a 600 s title.
  heardTogether({ id: 7, session: session({ members: [ME], by: "tv", pos: 598, at: 0 }), serverNow: Date.now() });
  expect([seeks, env.video.paused]).toEqual([[], true]);
  heardFromTv({ pos: 300 });
  expect([seeks, env.video.paused]).toEqual([[300], false]);
});

test("a move queued while Telegram can't be reached says why", async () => {
  await joined();
  env.respondWith(async () => Response.json({ session: session(), serverNow: Date.now(), reason: "Telegram isn't reachable" }));
  follower.viewer({ playing: false });
  await settle();
  expect(note().textContent).toBe("Telegram isn't reachable");
});

test("a read that finds another session the newest leaves this one, rather than stay listed in it", async () => {
  await joined();
  const other = session({ session: "s-2", at: Date.now() });
  env.respondWith(async (_url, options) => options?.body
    ? new Response(null, { status: 204 })
    : Response.json({ available: true, reason: null, live: { id: 8, session: other }, serverNow: Date.now() }));
  await refreshTogether();
  await settle();
  expect(note().textContent).toBe("Watch together ended");
  expect(posts()).toEqual([{ op: "leave", screen, id: 7 }]);
});

test("a session found stale here is read again, so the server deletes it as whoever reads one must", async () => {
  await joined({ pos: 590 });
  env.advance(132_000);
  expect(note().textContent).toBe("Watch together ended");
  expect(env.requests.filter((request) => request.options?.method === undefined).map((request) => request.url)).toEqual(["/api/together"]);
});

test("a title the session was moving to, opened by hand once this screen is in a session again, is the viewer's move", async () => {
  await joined();
  heardFromTv({ set: "film-2", pos: 0 });
  expect(opened).toEqual(["film-2"]);
  // Left before that title opened; back in once the TV went back to this one.
  button().fire("click");
  await settle();
  heardFromTv({ set: "film-1", pos: 0 });
  button().fire("click");
  await settle();
  env.requests.length = 0;
  follower.open(film("film-2"), { next: null });
  await settle();
  expect(posts()).toEqual([{ op: "act", screen, id: 7, change: { set: "film-2", pos: 0, playing: true } }]);
});
