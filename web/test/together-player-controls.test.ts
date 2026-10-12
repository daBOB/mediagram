/**
 * The real player in a watch-together session: every way a viewer plays,
 * pauses, seeks, changes speed or moves on reaches the session exactly once,
 * and nothing the player does because it is following goes back out.
 */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { browserEnvironment, chooseInMenu, settle } from "./support/player-environment";
import * as state from "../public/lib/watch-state.js";
import { heardTogether, openForTogether, refreshTogether, screen, setTogetherOpener, takeJoin } from "../public/lib/together-now.js";
import type { Session } from "../public/lib/together/session.js";

let env: ReturnType<typeof browserEnvironment>;
let openPlayer: (set: ReturnType<typeof film>, options?: object) => void;
let serial = 0;
const mediaKeys = new Map<string, () => void>();
const film = (setId: string) => ({ setId, title: setId, kind: "movie", duration: 600, container: "mp4", vcodec: "h264", acodec: "aac" });
const sets = new Map<string, object>(["film-1", "film-2"].map((id) => [id, film(id)]));
/** A title the browser can't play as it is: it is converted, and every seek starts ffmpeg again. */
sets.set("film-3", { ...film("film-3"), container: "mkv" });
const TV = { screen: "tv", label: "TV" };
const ME = { screen, label: "Browser" };
const session = (over: Partial<Session> = {}): Session => ({
  session: "s-1", set: "film-1", playing: false, pos: 0, rate: 1, at: Date.now(), by: "tv", members: [TV], ended: false, ...over,
});
const posts = () => env.requests
  .filter((request) => request.url === "/api/together" && request.options?.method === "POST")
  .map((request) => JSON.parse(String(request.options!.body)));
const acts = () => posts().filter((body) => body.op === "act").map((body) => body.change);

beforeEach(async () => {
  env = browserEnvironment();
  mediaKeys.clear();
  env.replace("navigator", { mediaSession: { setActionHandler: (action: string, run: () => void) => mediaKeys.set(action, run) } });
  await state.useProfile(null);
  env.respondWith(async (url, options) => {
    if (url !== "/api/together") return new Response(null, { status: 404 });
    const body = options?.body ? JSON.parse(String(options.body)) : null;
    if (!body) return Response.json({ available: true, reason: null, live: null, serverNow: Date.now() });
    if (body.op === "join") return Response.json({ id: 7, session: session({ by: screen, members: [TV, ME] }), serverNow: Date.now() });
    return new Response(null, { status: 204 });
  });
  // The module is one per tab: every test starts it from a read that found nothing.
  await refreshTogether();
  takeJoin();
  const player = await import(`../public/lib/playback/player.js?together=${++serial}`);
  player.initializePlayer();
  ({ openPlayer } = player);
  setTogetherOpener((set) => openPlayer(set as never), (setId) => sets.get(setId) as never ?? null);
});

afterEach(async () => {
  env.node("player").close();
  await settle();
  env.restore();
});

/** Film 1 open and joined, paused at the start, with nothing posted yet. */
async function inSession(options: object = {}) {
  heardTogether({ id: 7, session: session(), serverNow: Date.now() });
  openPlayer(film("film-1"), options);
  env.node("together").fire("click");
  await settle();
  env.requests.length = 0;
}

function press(key: string) {
  const event = new Event("keydown", { cancelable: true });
  Object.assign(event, { key, ctrlKey: false, altKey: false, metaKey: false });
  env.node("player").dispatchEvent(event);
}

test("play and pause, by the button, the keyboard or the media keys, each send one edit", async () => {
  await inSession();
  env.node("play-pause").fire("click");
  await settle();
  expect(acts()).toEqual([{ playing: true, pos: 0 }]);
  press(" ");
  await settle();
  expect(acts().map((change) => change.playing)).toEqual([true, false]);
  mediaKeys.get("play")!();
  mediaKeys.get("play")!();
  await settle();
  expect(acts().map((change) => change.playing)).toEqual([true, false, true]);
  mediaKeys.get("pause")!();
  await settle();
  expect(acts().map((change) => change.playing)).toEqual([true, false, true, false]);
  expect(env.video.paused).toBe(true);
});

test("a skip and the seek bar each send one edit, once the viewer stops", async () => {
  await inSession();
  press("ArrowRight");
  env.advance(300);
  await settle();
  expect(acts()).toEqual([{ pos: 15 }]);
  const bar = env.node("seek-to");
  bar.value = "100";
  bar.fire("input");
  bar.value = "120";
  bar.fire("input");
  bar.fire("change");
  env.advance(300);
  await settle();
  expect(acts()).toEqual([{ pos: 15 }, { pos: 120 }]);
});

test("speed, from the menu or a bracket key, sends one edit", async () => {
  await inSession();
  chooseInMenu(env.node, "speed", "1.5");
  press("]");
  await settle();
  expect(acts()).toEqual([{ rate: 1.5, pos: 0 }, { rate: 1.75, pos: 0 }]);
});

test("a frame step pauses the session as well as moving it", async () => {
  await inSession();
  env.node("play-pause").fire("click");
  await settle();
  env.requests.length = 0;
  press(".");
  env.advance(300);
  await settle();
  expect(acts().map((change) => change.playing ?? "kept")).toEqual([false, "kept"]);
});

test("following another screen's pause, seek and speed sends nothing", async () => {
  await inSession();
  heardTogether({ id: 7, session: session({ members: [TV, ME], playing: true }), serverNow: Date.now() });
  heardTogether({ id: 7, session: session({ members: [TV, ME], playing: true, pos: 200, rate: 1.5 }), serverNow: Date.now() });
  heardTogether({ id: 7, session: session({ members: [TV, ME], playing: false, pos: 210, rate: 1.5 }), serverNow: Date.now() });
  env.video.readyState = 4;
  env.advance(4_000);
  await settle();
  expect(env.video.paused).toBe(true);
  expect(env.video.currentTime).toBe(210);
  expect(env.video.playbackRate).toBe(1.5);
  expect(posts()).toEqual([]);
});

test("the next title, from the Next button, moves the session once", async () => {
  await inSession({ next: film("film-2") });
  env.node("play-next").fire("click");
  await settle();
  expect(acts()).toEqual([{ set: "film-2", pos: 0, playing: true }]);
});

test("closing the player leaves the session, once", async () => {
  await inSession();
  env.node("player").close();
  await settle();
  expect(posts()).toEqual([{ op: "leave", screen, id: 7 }]);
});

test("at the end of an episode with one next, Up next counts down and nothing starts the episode again", async () => {
  await inSession({ next: film("film-2") });
  env.video.readyState = 4;
  heardTogether({ id: 7, session: session({ members: [TV, ME], playing: true, pos: 598 }), serverNow: Date.now() });
  await settle();
  let played = 0;
  env.video.addEventListener("play", () => { played++; });
  Object.assign(env.video, { currentTime: 600, paused: true, ended: true });
  env.video.fire("ended");
  env.advance(3_000);
  await settle();
  expect([played, env.video.currentTime, env.node("up-next").hidden]).toEqual([0, 600, false]);
  expect(posts()).toEqual([]);
});

test("a held drag of the seek bar moves the session once it settles, and the drift check doesn't pull it back", async () => {
  await inSession();
  env.node("play-pause").fire("click");
  await settle();
  env.requests.length = 0;
  env.video.readyState = 4;
  const bar = env.node("seek-to");
  bar.value = "300";
  bar.fire("input");
  env.advance(2_000);
  await settle();
  expect(acts().map((change) => Math.round(change.pos))).toEqual([300]);
  expect(env.video.currentTime).not.toBeLessThan(300);
  bar.fire("change");
  env.advance(300);
  await settle();
  expect(acts()).toHaveLength(2);
});

test("a conversion opened because the session moved to it starts once, at the session's place", async () => {
  await inSession();
  // Held open, so the note stays the one the player wrote as the conversion starts.
  env.respondWith((url) => (url.includes("/transcode?") ? new Promise<Response>(() => {}) : Promise.resolve(new Response(null, { status: 404 }))));
  heardTogether({ id: 7, session: session({ members: [TV, ME], set: "film-3", playing: true, pos: 300 }), serverNow: Date.now() });
  await settle();
  const starts = env.requests.filter((request) => request.url.includes("/api/sets/film-3/transcode?"));
  expect(starts.map((request) => request.url.match(/seek=(\d+)/)?.[1])).toEqual(["300"]);
  expect(env.node("note").textContent).not.toContain("Carrying on");
});

test("a title opened to join a session starts at the session's place", async () => {
  heardTogether({ id: 7, session: session({ playing: true, pos: 120 }), serverNow: Date.now() });
  openForTogether("film-1", 7);
  env.video.fire("loadedmetadata");
  expect(env.video.currentTime).toBe(120);
  // The session's place is where everyone is, not where this profile left off.
  expect(env.node("note").textContent).not.toContain("Carrying on");
});

test("a member leaving after the end leaves Up next counting down, and the next episode moves the session", async () => {
  await inSession({ next: film("film-2") });
  env.video.readyState = 4;
  const at = Date.now();
  heardTogether({ id: 7, session: session({ members: [TV, ME], playing: true, pos: 598, at }), serverNow: Date.now() });
  await settle();
  let played = 0;
  env.video.addEventListener("play", () => { played++; });
  env.advance(2_000);
  Object.assign(env.video, { currentTime: 600, paused: true, ended: true });
  env.video.fire("ended");
  env.advance(4_000);
  heardTogether({ id: 7, session: session({ members: [ME], by: "tv", playing: true, pos: 598, at }), serverNow: Date.now() });
  await settle();
  expect([played, env.video.currentTime, env.node("up-next-in").textContent]).toEqual([0, 600, "starting in 6…"]);
  env.advance(6_000);
  await settle();
  expect(acts()).toEqual([{ set: "film-2", pos: 0, playing: true }]);
});
