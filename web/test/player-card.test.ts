/**
 * The control card, driven through the real player: the speed menu, restart,
 * the skip at either edge of a title, and the card staying up while a menu
 * is open.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { browserEnvironment, chooseInMenu, markedInMenu, settle } from "./support/player-environment";
import * as state from "../public/lib/watch-state.js";

let env: ReturnType<typeof browserEnvironment>;
let openPlayer: (set: any, options?: any) => void;
let serial = 0;
const set = (id: string, convert = false) => ({
  setId: id,
  title: id,
  kind: "movie",
  duration: 600,
  container: convert ? "mkv" : "mp4",
  vcodec: "h264",
  acodec: "aac",
});
const rows = () => env.node("card-menu").children;

beforeEach(async () => {
  env = browserEnvironment();
  await state.useProfile(null);
  const player = await import(`../public/lib/playback/player.js?card=${++serial}`);
  player.initializePlayer();
  ({ openPlayer } = player);
});

afterEach(async () => {
  env.node("player").close();
  await settle();
  env.restore();
});

test("Speed opens the six speeds with the current one marked, and a choice plays at it", () => {
  openPlayer(set("speed"));
  expect(env.node("speed").textContent).toBe("1×");
  expect(markedInMenu(env.node, "speed")).toBe("1");
  env.node("speed").fire("click");
  expect(rows().map((row) => row.textContent)).toEqual(["0.75×", "1×", "1.25×", "1.5×", "1.75×", "2×"]);
  env.node("speed").fire("click");
  chooseInMenu(env.node, "speed", "1.5");
  env.video.fire("ratechange");
  expect(env.video.playbackRate).toBe(1.5);
  expect(env.node("speed").textContent).toBe("1.5×");
  expect(env.node("card-menu").hidden).toBe(true);
});

test("Restart goes back to 0:00 and leaves playing or paused as it was", async () => {
  openPlayer(set("restart"));
  env.video.currentTime = 300;
  await env.video.play();
  env.node("restart").fire("click");
  expect(env.video.currentTime).toBe(0);
  expect(env.video.paused).toBe(false);
  env.video.pause();
  env.video.currentTime = 120;
  env.node("restart").fire("click");
  expect(env.video.currentTime).toBe(0);
  expect(env.video.paused).toBe(true);
});

test("Restart on a converted title starts the conversion again from 0:00", async () => {
  env.respondWith(async (url) =>
    url.includes("/transcode?") ? Response.json({ playlist: "/hls/0000000000000001/index.m3u8" }) : new Response("", { status: 404 }));
  openPlayer(set("converted", true));
  await settle();
  env.node("seek-to").value = "200";
  env.node("seek-to").fire("change");
  await settle();
  env.node("restart").fire("click");
  await settle();
  const seeks = env.requests.filter(({ url }) => url.includes("/transcode?"))
    .map(({ url }) => new URL(url, "http://local").searchParams.get("seek"));
  expect(seeks).toEqual(["0", "200", "0"]);
});

test("back 15 near the start lands on 0:00; on 15 near the end stops at the end and the ended path still runs", () => {
  const opened: string[] = [];
  openPlayer(set("edges"), { next: set("after"), onOpenNext: (following: { setId: string }) => opened.push(following.setId) });
  env.video.currentTime = 5;
  env.node("skip-back").fire("click");
  expect(env.video.currentTime).toBe(0);
  env.video.currentTime = 590;
  env.node("skip-forward").fire("click");
  expect(env.video.currentTime).toBe(600);
  // Inside the last half minute: the next title is offered, not yet started.
  expect(env.node("up-next").hidden).toBe(false);
  expect(opened).toEqual([]);
  env.video.ended = true;
  env.video.fire("ended");
  env.advance(10_000);
  expect(opened).toEqual(["after"]);
});

test("the card stays up while a menu is open and rests on the usual timer once it closes", async () => {
  openPlayer(set("held"));
  await env.video.play();
  env.node("speed").fire("click");
  env.advance(10_000);
  expect(env.node("player").classes.has("resting")).toBe(false);
  chooseInMenu(env.node, "speed", "1");
  env.advance(2600);
  expect(env.node("player").classes.has("resting")).toBe(true);
});

test("a film has no run, so Previous and Next are hidden rather than disabled", () => {
  openPlayer(set("film"));
  expect(env.node("previous").hidden).toBe(true);
  expect(env.node("play-next").hidden).toBe(true);
});

test("in a run, Previous and Next open their neighbours the way the next title opens, and each end is disabled", () => {
  const opened: Array<[string, unknown]> = [];
  const onOpenNext = (following: { setId: string }, how: unknown) => opened.push([following.setId, how]);
  openPlayer(set("first"), { next: set("second"), previous: null, inRun: true, onOpenNext });
  expect(env.node("previous").hidden).toBe(false);
  expect(env.node("previous").disabled).toBe(true);
  expect(env.node("play-next").disabled).toBe(false);
  expect(env.node("play-next").title).toBe("second");
  openPlayer(set("second"), { next: set("third"), previous: set("first"), inRun: true, onOpenNext });
  env.node("previous").fire("click");
  env.node("play-next").fire("click");
  expect(opened).toEqual([["first", { autoplay: "asap" }], ["third", { autoplay: "asap" }]]);
  openPlayer(set("third"), { next: null, previous: set("second"), inRun: true, onOpenNext });
  expect(env.node("play-next").hidden).toBe(false);
  expect(env.node("play-next").disabled).toBe(true);
  expect(env.node("previous").disabled).toBe(false);
});

test("choosing a menu row hands focus back to its button, so the player's keys keep working", async () => {
  openPlayer(set("keys"));
  chooseInMenu(env.node, "speed", "1.25");
  expect(env.document.activeElement).toBe(env.node("speed"));
  expect(env.video.paused).toBe(true);
  env.node("player").dispatchEvent(Object.assign(new Event("keydown"), { key: "k" }));
  await settle();
  expect(env.video.paused).toBe(false);
});

describe("the episode sidebar", () => {
  const ep = (id: string, episode: string) => ({ ...set(id), kind: "ep", show: "Star City", season: 1, episode });
  const show = { name: "Star City", divisions: [{ title: "Season 1", season: 1, items: [ep("e1", "1"), ep("e2", "2"), ep("e3", "3")], children: [] }] };
  const escape = () => {
    const event = Object.assign(new Event("keydown", { cancelable: true }), { key: "Escape" });
    env.node("player").dispatchEvent(event);
    return event;
  };

  test("☰ is offered for a run with a show behind it and not for a film", () => {
    openPlayer(set("film"));
    expect(env.node("episodes").hidden).toBe(true);
    openPlayer(ep("e2", "2"), { next: ep("e3", "3"), previous: ep("e1", "1"), inRun: true, collection: show });
    expect(env.node("episodes").hidden).toBe(false);
  });

  test("a row opens through the same path as Next, and the sidebar shuts", () => {
    const opened: Array<[string, unknown]> = [];
    const onOpenNext = (following: { setId: string }, how: unknown) => opened.push([following.setId, how]);
    openPlayer(ep("e1", "1"), { next: ep("e2", "2"), inRun: true, collection: show, onOpenNext });
    env.node("episodes").fire("click");
    env.node("episode-sidebar").children[1]!.children[0]!.children[2]!.fire("click");
    expect(opened).toEqual([["e3", { autoplay: "asap" }]]);
    expect(env.node("episode-sidebar").hidden).toBe(true);
  });

  test("the card stays up while it is open, and Esc shuts a menu over it before the sidebar, the player staying open", async () => {
    openPlayer(ep("e1", "1"), { next: ep("e2", "2"), inRun: true, collection: show });
    env.node("player").open = true;
    await env.video.play();
    env.node("episodes").fire("click");
    env.advance(10_000);
    expect(env.node("player").classes.has("resting")).toBe(false);
    env.node("speed").fire("click");
    expect(escape().defaultPrevented).toBe(true);
    expect(env.node("card-menu").hidden).toBe(true);
    expect(env.node("episode-sidebar").hidden).toBe(false);
    expect(escape().defaultPrevented).toBe(true);
    expect(env.node("episode-sidebar").hidden).toBe(true);
    expect(env.node("player").open).toBe(true);
    expect(escape().defaultPrevented).toBe(false);
    env.advance(2600);
    expect(env.node("player").classes.has("resting")).toBe(true);
  });
});
