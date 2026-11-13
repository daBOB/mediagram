import { afterEach, expect, test } from "bun:test";
import * as state from "../public/lib/watch-state.js";
import { browserEnvironment, settle } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment> | null = null;
let serial = 0;
const freshPlayer = () => import(`../public/lib/playback/player.js?initialization=${++serial}`);

afterEach(async () => {
  env?.node("player").close();
  await settle();
  env?.restore();
  env = null;
});

test("importing before the page exists does not look up or mount controls", async () => {
  const descriptors = ["document", "window"].map(
    (name) => [name, Object.getOwnPropertyDescriptor(globalThis, name)] as const,
  );
  for (const [name] of descriptors) Reflect.deleteProperty(globalThis, name);
  try {
    const player = await freshPlayer();
    expect(typeof player.initializePlayer).toBe("function");
    env = browserEnvironment();
    expect(env.node("speed").getAttribute("aria-haspopup")).toBeNull();
    player.initializePlayer();
    expect(env.node("speed").getAttribute("aria-haspopup")).toBe("true");
  } finally {
    // The fixture was installed while globals were absent. Restore it first,
    // then the previous page, including when import failed before mounting.
    env?.restore();
    env = null;
    for (const [name, descriptor] of descriptors) {
      if (descriptor) Object.defineProperty(globalThis, name, descriptor);
      else Reflect.deleteProperty(globalThis, name);
    }
  }
});

test("initialization twice mounts controls and click listeners once", async () => {
  env = browserEnvironment();
  const player = await freshPlayer();
  expect(env.node("speed").getAttribute("aria-haspopup")).toBeNull();
  player.initializePlayer();
  player.initializePlayer();
  // A second listener would open the list and close it again in one click.
  env.node("speed").fire("click");
  expect(env.node("card-menu").hidden).toBe(false);
  expect(env.node("card-menu").children).toHaveLength(6);
  env.node("speed").fire("click");
  // The subtitle style panel and the clip panel, each once.
  expect(env.node("card-dock").children).toHaveLength(2);
  player.openPlayer({
    setId: "one",
    container: "mp4",
    vcodec: "h264",
    acodec: "aac",
    duration: 600,
  });
  env.node("play-pause").fire("click");
  await settle();
  expect(env.video.paused).toBe(false);
  expect(env.node("play-pause").getAttribute("aria-label")).toBe("Pause");
});

test("with the clip panel open, a and b set its ends to the playhead", async () => {
  env = browserEnvironment();
  const player = await freshPlayer();
  player.initializePlayer();
  player.openPlayer({ setId: "one", container: "mp4", vcodec: "h264", acodec: "aac", duration: 600 });
  await settle();
  const press = (key: string) => env!.node("player").dispatchEvent(Object.assign(new Event("keydown", { cancelable: true }), { key }));
  const times = () => {
    const panel = env!.node("card-dock").children[1];
    const found: string[] = [];
    const walk = (node: any) => { if (node.className === "cue-at") found.push(node.textContent); for (const child of node.children ?? []) walk(child); };
    walk(panel);
    return found;
  };
  press("x");
  expect(times()).toEqual(["0:00", "0:15"]);
  // End first: a start set past the end would be sorted with it, as the keys do.
  env.video.currentTime = 130;
  press("b");
  env.video.currentTime = 100;
  press("a");
  expect(times()).toEqual(["1:40", "2:10"]);
});

test("clip mode saves no position on a grown-up's profile, still saves a kid's, and leaving it saves again", async () => {
  env = browserEnvironment();
  env.respondWith(async (url) => {
    if (url === "/api/profiles") return Response.json({ profiles: [{ id: "grown", name: "G" }, { id: "kid", name: "K", kids: true }] });
    if (url.endsWith("/state")) return Response.json({});
    return new Response(null, { status: 204 });
  });
  await state.loadProfiles();
  const player = await freshPlayer();
  player.initializePlayer();
  const press = (key: string) => env!.node("player").dispatchEvent(Object.assign(new Event("keydown", { cancelable: true }), { key }));
  const saved = () => env!.requests.filter((r) => r.url.includes("/progress/") && r.options?.method === "PUT").length;
  /** Positions written over one ten-second save tick while playing. */
  const tick = async () => {
    const before = saved();
    env!.advance(10_000);
    await settle();
    return saved() - before;
  };
  try {
    for (const [profile, kids] of [["grown", false], ["kid", true]] as const) {
      await state.useProfile(profile);
      expect(state.profile()?.kids === true).toBe(kids);
      player.openPlayer({ setId: "one", container: "mp4", vcodec: "h264", acodec: "aac", duration: 600 });
      await settle();
      env.video.currentTime = 100;
      env.video.paused = false;
      expect(await tick()).toBe(1);
      press("x");
      // A daily limit counts a kid's minutes from these writes, so clipping must not stop them.
      expect(await tick()).toBe(kids ? 1 : 0);
      press("x");
      expect(await tick()).toBe(1);
      env.node("player").close();
      await settle();
    }
  } finally {
    await state.useProfile(null);
  }
});
