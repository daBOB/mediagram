import { afterEach, beforeEach, expect, spyOn, test } from "bun:test";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { AudioTrackReader } from "../src/catalog/audio-tracks";
import { shutdownFor } from "../src/application/lifecycle";
import { startPlayer } from "../src/index";
import { configIn, deferred, library, telegramBoundary } from "./application-fixture";

let root: string;
beforeEach(async () => { root = await mkdtemp(join(tmpdir(), "audio-shutdown-")); });
afterEach(async () => { await rm(root, { recursive: true, force: true }); });
const streams = JSON.stringify({ streams: [{ codec_name: "aac", tags: { language: "eng" } }] });

test("audio stop closes admission, cancels all probes and awaits their settlement", async () => {
  const finish = deferred<void>();
  const signals: AbortSignal[] = [];
  let calls = 0;
  const reader = new AudioTrackReader({ baseUrl: "http://127.0.0.1:8770" }, async (url: string, signal: AbortSignal) => {
    calls++;
    if (url.includes("/cached/")) return streams;
    signals.push(signal);
    await finish.promise;
    return streams; // A cancelled probe may still return bytes; do not publish them.
  });
  expect(await reader.read("cached")).toHaveLength(1);
  const reads = [reader.read("one"), reader.read("two"), reader.read("one")];
  let stopped = false;
  let stopping: Promise<void> | undefined;
  try {
    stopping = reader.stop().then(() => { stopped = true; });
    expect(signals.every((signal) => signal.aborted)).toBe(true);
    expect(await reader.read("late")).toEqual([]);
    expect(await reader.read("cached")).toEqual([]);
    await new Promise<void>((done) => setImmediate(done));
    expect(stopped).toBe(false);
  } finally {
    finish.resolve();
    await stopping;
    await Promise.all(reads);
  }
  expect(await Promise.all(reads)).toEqual([[], [], []]);
  await reader.stop();
  expect(calls).toBe(3);
});

test("audio work drains before application HTTP and Telegram resources close", async () => {
  const finish = deferred<void>();
  const reader = new AudioTrackReader({ baseUrl: "http://127.0.0.1:8770" }, async () => {
    await finish.promise;
    return null;
  });
  const reading = reader.read("active");
  const order: string[] = [];
  const stop = shutdownFor({ timers: [], audio: reader,
    server: { close: async () => { order.push("server"); } },
    telegram: { disconnect: async () => { order.push("telegram"); } },
  });
  const stopping = stop();
  try {
    await new Promise<void>((done) => setImmediate(done));
    expect(order).toEqual([]);
  } finally {
    finish.resolve();
    await reading;
    await stopping;
  }
  expect(order).toEqual(["server", "telegram"]);
});

test("production startup terminates and reaps an active audio probe before disconnecting", async () => {
  const db = library("Movie");
  await writeFile(join(root, "library.db"), db.serialize());
  db.close();
  const nativeSpawn = Bun.spawn.bind(Bun);
  const spawned = deferred<ReturnType<typeof Bun.spawn>>();
  const children: ReturnType<typeof Bun.spawn>[] = [];
  const order: string[] = [];
  const spawn = spyOn(Bun, "spawn").mockImplementation(((command: string[]) => {
    expect(command[0]).toBe("ffprobe");
    const child = nativeSpawn([process.execPath, "-e", "setInterval(() => {}, 60000)"], { stdout: "pipe", stderr: "ignore" });
    children.push(child);
    void child.exited.then(() => { order.push("probe-exit"); });
    spawned.resolve(child);
    return child;
  }) as typeof Bun.spawn);
  let player: Awaited<ReturnType<typeof startPlayer>> | undefined;
  let request: Promise<unknown> | undefined;
  try {
    player = await startPlayer(configIn(root), {
      open: async () => telegramBoundary(order), findIndex: async () => "nothing-pinned",
      detectEncoder: async () => ({ kind: "software", name: "libx264" }), listen: () => () => {},
    });
    await player.ready;
    request = fetch(`${player.server.baseUrl}/api/sets/01SET/audio`).then((response) => response.json()).catch(() => null);
    const child = await spawned.promise;
    expect(() => process.kill(child.pid, 0)).not.toThrow();
    await player.stop();
    expect(child.signalCode).toBe("SIGTERM");
    expect(() => process.kill(child.pid, 0)).toThrow();
    expect(order).toEqual(["probe-exit", "disconnect"]);
    expect(children).toHaveLength(1);
  } finally {
    for (const child of children) child.kill("SIGKILL");
    await Promise.all(children.map((child) => child.exited));
    await request;
    await player?.stop();
    spawn.mockRestore();
  }
});

test("audio shutdown force-kills and reaps a probe that refuses graceful termination", async () => {
  const nativeSpawn = Bun.spawn.bind(Bun);
  const ready = join(root, "ready");
  const children: ReturnType<typeof Bun.spawn>[] = [];
  const spawn = spyOn(Bun, "spawn").mockImplementation((() => {
    const child = nativeSpawn([process.execPath, "-e",
      'process.on("SIGTERM", () => {}); await Bun.write(process.argv[1], "ready"); setInterval(() => {}, 60000)', ready,
    ], { stdout: "pipe", stderr: "ignore" });
    children.push(child);
    return child;
  }) as typeof Bun.spawn);
  const reader = new AudioTrackReader({ baseUrl: "http://127.0.0.1:8770" });
  const reading = reader.read("stubborn");
  try {
    const deadline = Date.now() + 2000;
    while (!(await Bun.file(ready).exists()) && Date.now() < deadline) await Bun.sleep(5);
    expect(await Bun.file(ready).exists()).toBe(true);
    await reader.stop();
    expect(await reading).toEqual([]);
    expect(children[0]!.signalCode).toBe("SIGKILL");
    expect(() => process.kill(children[0]!.pid, 0)).toThrow();
    expect(await reader.read("late")).toEqual([]);
    expect(children).toHaveLength(1);
  } finally {
    for (const child of children) child.kill("SIGKILL");
    await Promise.all(children.map((child) => child.exited));
    await reading;
    await reader.stop();
    spawn.mockRestore();
  }
}, 10_000);
