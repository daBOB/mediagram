/**
 * The control card, driven through the real player: the speed menu, restart,
 * the skip at either edge of a title, and the card staying up while a menu
 * is open.
 */

import { afterEach, beforeEach, expect, test } from "bun:test";
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
