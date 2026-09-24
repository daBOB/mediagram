import { afterEach, expect, test } from "bun:test";
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
    expect(env.node("speed-rate").children).toHaveLength(0);
    player.initializePlayer();
    expect(env.node("speed-rate").children).toHaveLength(6);
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
  expect(env.node("speed-rate").children).toHaveLength(0);
  player.initializePlayer();
  player.initializePlayer();
  expect(env.node("speed-rate").children).toHaveLength(6);
  expect(env.node(".hud-bottom").children).toHaveLength(1);
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
