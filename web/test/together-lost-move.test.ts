/**
 * A tab's move whose edit throws, from the button to the channel: the
 * follower (`playback/together.js`) posting to a real hub over a fake
 * channel, and hearing the hub's announcements as its event stream would.
 * Android's twin is `TogetherControllerLostMoveTest.kt`.
 */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { browserEnvironment, settle } from "./support/player-environment";
import { mountTogether } from "../public/lib/playback/together.js";
import { heardTogether, refreshTogether, screen, setTogetherOpener, takeJoin } from "../public/lib/together-now.js";
import { parseSession, writeSession, type Session } from "../public/lib/together/session.js";
import { EDIT_RETRY_MS } from "../src/together/sender";
import { TogetherHub } from "../src/together/hub";
import { FakeChannel } from "./together-fixture";

let env: ReturnType<typeof browserEnvironment>;
let channel: FakeChannel;
let follower: ReturnType<typeof mountTogether>;
const film = { setId: "film-1", title: "Film", kind: "movie", duration: 6_000 };
const TV = { screen: "tv", label: "TV" };

beforeEach(async () => {
  env = browserEnvironment();
  env.video.readyState = 4;
  channel = new FakeChannel();
  // Its sleeps are the page's timers, so they pass as the test moves time.
  const hub = new TogetherHub(channel, (id, session, lost) => heardTogether({ id, session, serverNow: Date.now(), lost }));
  const durationOf = () => film.duration;
  env.respondWith(async (_url, options) => {
    const body = options?.body ? JSON.parse(String(options.body)) : null;
    if (!body) return Response.json({ available: true, reason: null, live: await hub.live(durationOf), serverNow: Date.now() });
    if (body.op === "join") return Response.json({ ...await hub.join(body.id, { screen: body.screen, label: "Browser" }), serverNow: Date.now() });
    if (body.op === "act") return Response.json({ session: hub.act(body.id, body.screen, body.change), serverNow: Date.now() });
    if (body.op === "leave") hub.leave(body.id, body.screen);
    return new Response(null, { status: 204 });
  });
  setTogetherOpener(() => {}, (setId) => (setId === film.setId ? film as never : null));
  const tv: Session = { session: "s-tv", set: film.setId, playing: true, pos: 0, rate: 1, at: Date.now(), by: TV.screen, members: [TV], ended: false };
  channel.messages.set(950, writeSession(tv));
  await refreshTogether();
  takeJoin();
  follower = mountTogether({
    video: env.video, button: env.node("together"), label: env.node("together-label"), note: env.node("together-note"),
    filmTime: () => env.video.currentTime,
    seekTo: (pos: number) => { env.video.currentTime = pos; },
    setRate: (rate: number) => { env.video.playbackRate = rate; },
    converting: () => false,
  });
  // In the TV's session, playing: the viewer pressed Join on this film.
  follower.open(film, { next: null });
  env.node("together").fire("click");
  await settle();
  await settle();
});

afterEach(async () => {
  follower.clear();
  await settle();
  env.restore();
});

const held = () => parseSession(channel.messages.get(950)!)!;

/** The viewer's pause, as the player hands it over. */
function pause() {
  env.video.pause();
  follower.viewer({ playing: false });
}

/** Moves time on, letting the hub's waits end and what they send land. */
async function wait(ms: number) {
  env.advance(ms);
  await settle();
  await settle();
}

test("a pause whose edit throws is tried again in 30 s, never sooner, and lands once Telegram is back", async () => {
  expect(held().members.map((m) => m.screen)).toEqual([TV.screen, screen]);
  channel.failEdits = true;
  pause();
  await settle();
  expect(channel.edits).toHaveLength(2);
  channel.failEdits = false;
  await wait(EDIT_RETRY_MS - 1);
  expect(channel.edits).toHaveLength(2);
  await wait(1);
  expect(channel.edits).toHaveLength(3);
  expect(held()).toMatchObject({ playing: false, by: screen });
  expect(env.video.paused).toBe(true);
});

test("a pause given up after three failed edits: the tab that paused follows what the channel holds again", async () => {
  channel.failEdits = true;
  pause();
  await settle();
  await wait(EDIT_RETRY_MS);
  await wait(EDIT_RETRY_MS);
  expect(channel.edits).toHaveLength(4);
  // The channel still holds the join, written by this very tab: its own echo, which the tab follows all the same.
  expect(held()).toMatchObject({ playing: true, by: screen });
  expect(env.video.paused).toBe(false);
  await wait(EDIT_RETRY_MS);
  expect(channel.edits).toHaveLength(4);
});
