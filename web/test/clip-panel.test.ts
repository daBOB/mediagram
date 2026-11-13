/**
 * The clip panel over the real A–B loop and the real card menus: the loop it
 * makes lasts only while it is open, and "Pick again" lets a viewer seek
 * anywhere and set both ends by touch.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mountClipPanel } from "../public/lib/playback/clip-panel.js";
import { mountPlayerMenus } from "../public/lib/playback/player-menus.js";
import { mountTransport } from "../public/lib/playback/transport.js";
import * as state from "../public/lib/watch-state.js";
import { browserEnvironment, Node, settle } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
let seeks: number[];
let transport: ReturnType<typeof mountTransport>;
let clip: ReturnType<typeof mountClipPanel>;

beforeEach(async () => {
  env = browserEnvironment();
  await state.useProfile(null);
  seeks = [];
  const video = env.video;
  const menus = mountPlayerMenus();
  transport = mountTransport({
    video, menus, onPlay: async () => {}, onPause: () => {},
    onSeekTo: (to: number) => { seeks.push(to); video.currentTime = to; },
    filmTime: () => video.currentTime, runtime: () => 3600,
    recall: () => null, remember: () => {}, toggleSubtitles: () => {},
  });
  clip = mountClipPanel({
    button: env.node("clip"), menus, transport, filmTime: () => video.currentTime, runtime: () => 3600, audioTrack: () => 0,
  });
  clip.open({ setId: "film" });
});
afterEach(async () => {
  await settle();
  env.restore();
});

function find(root: Node, match: (node: Node) => boolean): Node | null {
  if (match(root)) return root;
  for (const child of root.children) {
    const found = find(child, match);
    if (found) return found;
  }
  return null;
}
const panel = () => find(env.node("card-dock"), (node) => node.classes.has("clip-panel"))!;
const part = (name: string) => find(panel(), (node) => node.classes.has(name))!;
const labelled = (label: string) => find(panel(), (node) => node.getAttribute("aria-label") === label)!;
const pickAgain = () => find(panel(), (node) => node.textContent === "Pick again")!;

/** A pointerdown on the player that landed on `target`, as a tap does. */
function tap(target: Node) {
  const event = new Event("pointerdown");
  Object.defineProperty(event, "target", { value: target });
  env.node("player").dispatchEvent(event);
}

/** Where playback goes from `at`: `null` while the loop leaves it alone. */
function playFrom(at: number) {
  seeks.length = 0;
  env.video.currentTime = at;
  env.video.fire("timeupdate");
  return seeks.at(-1) ?? null;
}

describe("the clip panel's loop", () => {
  test("closing the panel clears the loop it made, so seeking is free again", () => {
    env.video.currentTime = 100;
    clip.toggle();
    expect(panel().hidden).toBe(false);
    expect(transport.loop()).toEqual({ from: 85, to: 115 });
    expect(playFrom(300)).toBe(85);

    tap(env.node("player"));
    expect(panel().hidden).toBe(true);
    expect(transport.loop()).toEqual({ from: null, to: null });
    expect(playFrom(300)).toBeNull();
  });

  test("a tap on the card beneath, where the seek bar is, leaves the panel open", () => {
    env.node("control-card").append(env.node("seek-to"));
    clip.toggle();
    tap(env.node("seek-to"));
    expect(panel().hidden).toBe(false);
  });

  test("Pick again frees playback; setting both ends loops again, to the exact frame", () => {
    env.video.currentTime = 100;
    clip.toggle();
    pickAgain().fire("click");
    expect(transport.loop()).toEqual({ from: null, to: null });
    expect(panel().hidden).toBe(false);
    expect(part("clip-length").textContent).toBe("Seek, then set Start and End");
    expect(part("clip-export").disabled).toBe(true);
    expect(labelled("Start: back 1 second").disabled).toBe(true);
    expect(playFrom(1800)).toBeNull();

    env.video.currentTime = 1800.25;
    labelled("Start: set to now").fire("click");
    expect(transport.loop()).toEqual({ from: 1800.25, to: null });
    expect(playFrom(2000)).toBeNull();

    env.video.currentTime = 1830.5;
    labelled("End: set to now").fire("click");
    expect(transport.loop()).toEqual({ from: 1800.25, to: 1830.5 });
    expect(part("clip-length").textContent).toBe("30 s · plays as a loop");
    expect(playFrom(1831)).toBe(1800.25);

    labelled("End: forward 1 second").fire("click");
    expect(transport.loop()).toEqual({ from: 1800.25, to: 1831.5 });
  });

  test("closing puts back the loop the viewer had set before opening", () => {
    env.video.currentTime = 50;
    transport.act({ do: "loop", end: "from" });
    env.video.currentTime = 70;
    transport.act({ do: "loop", end: "to" });
    clip.toggle();
    expect(transport.loop()).toEqual({ from: 50, to: 70 });
    pickAgain().fire("click");
    env.video.currentTime = 900;
    labelled("Start: set to now").fire("click");
    env.video.currentTime = 920;
    labelled("End: set to now").fire("click");
    clip.toggle();
    expect(transport.loop()).toEqual({ from: 50, to: 70 });

    // Half a loop is put back as half a loop, not as one the panel picked.
    transport.act({ do: "loop", end: "from" }); // `a` again clears
    env.video.currentTime = 200;
    transport.act({ do: "loop", end: "from" });
    clip.toggle();
    expect(transport.loop()).toEqual({ from: 185, to: 215 });
    clip.toggle();
    expect(transport.loop()).toEqual({ from: 200, to: null });
  });

  test("a scene opened from a mark opens on its range, and closing frees playback", () => {
    clip.open({ setId: "film" }, { clip: { from: 10.5, to: 40.5 } });
    expect(panel().hidden).toBe(false);
    expect(transport.loop()).toEqual({ from: 10.5, to: 40.5 });
    tap(env.node("player"));
    expect(transport.loop()).toEqual({ from: null, to: null });
    expect(playFrom(500)).toBeNull();
    // Still clip playback until the title is left: it saves nothing.
    expect(clip.active()).toBe(true);
  });
});

describe("the clip panel's status line", () => {
  test("announces only changes of state; the percentage is not live", async () => {
    let polls = 0;
    env.respondWith(async (url, options) => {
      if (url === "/api/profiles/p/state") return Response.json({});
      if (options?.method === "POST") return Response.json({ id: "job" }, { status: 202 });
      if (url === "/api/clips/job") return Response.json({ id: "job", state: "running", progress: ++polls / 10 });
      return Response.json([]);
    });
    await state.useProfile("p");
    clip.toggle();
    const said = part("clip-said");
    expect(said.getAttribute("role")).toBe("status");
    expect(part("clip-progress").getAttribute("role")).toBeNull();
    const heard: string[] = [];
    Object.defineProperty(said, "textContent", {
      get: () => heard.at(-1) ?? "",
      set: (text: string) => { heard.push(text); },
    });

    part("clip-export").fire("click");
    await settle();
    env.advance(1000);
    await settle();
    env.advance(1000);
    await settle();
    expect(polls).toBeGreaterThanOrEqual(2);
    expect(heard).toEqual(["Making the clip…"]);
    expect(part("clip-progress").textContent).toBe(`${polls * 10}%`);
    await state.useProfile(null);
  });
});
