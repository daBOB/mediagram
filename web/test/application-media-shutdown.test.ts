import { afterEach, beforeEach, expect, spyOn, test } from "bun:test";
import { mkdtemp, readdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { shutdownFor } from "../src/application/lifecycle";
import { CACHE_CHUNK } from "../src/cache/key";
import { CachedReader, MAX_RUN_BYTES } from "../src/cache/reader";
import { SeriesPreload, type PreloadItem } from "../src/cache/series-preload";
import { ChunkCache } from "../src/cache/store";
import { SheetStore } from "../src/thumbs/sheets";
import { startServer } from "../src/server";
import { startPlayer } from "../src/index";
import { spritePlan } from "../public/lib/sprite-plan.js";
import { configIn, deferred, library, telegramBoundary } from "./application-fixture";

let root: string;
beforeEach(async () => { root = await mkdtemp(join(tmpdir(), "application-media-shutdown-")); });
afterEach(async () => { await rm(root, { recursive: true, force: true }); });

test("shutdown drains the active preload range before disconnecting and discards all remaining work", async () => {
  const started = deferred<void>();
  const finish = deferred<void>();
  const reader = new CachedReader(new ChunkCache(join(root, "cache"), 100_000_000));
  const fetched: Array<{ messageId: number; offset: number; length: number }> = [];
  const held: string[] = [];
  const preload = new SeriesPreload({
    fill: (...args) => reader.fill(...args),
    fetcherFor: (messageId) => async (offset, length) => {
      fetched.push({ messageId, offset, length });
      started.resolve();
      await finish.promise;
      return new Uint8Array(length);
    },
    isHeld: async () => false,
    onHeld: (setId) => { held.push(setId); },
  });
  const item = (setId: string): PreloadItem => ({
    setId, title: setId,
    locations: [0, 1].map((idx) => ({ span: { idx, off: 0, len: MAX_RUN_BYTES + CACHE_CHUNK }, chatId: 1, messageId: idx + 1 })),
  });
  preload.want([item("FIRST"), item("QUEUED")]);
  await started.promise;
  let disconnected = false;
  const stop = shutdownFor({ timers: [], preload, telegram: { disconnect: async () => { disconnected = true; } } });
  let completed = false;
  const stopped = stop().then(() => { completed = true; });
  try {
    await new Promise<void>((resolve) => setImmediate(resolve));
    expect(completed).toBe(false);
    expect(disconnected).toBe(false);
    preload.want([item("LATE")]);
  } finally {
    finish.resolve();
    await stopped;
    await preload.settle();
  }
  expect(disconnected).toBe(true);
  expect(fetched).toEqual([{ messageId: 1, offset: 0, length: MAX_RUN_BYTES }]);
  expect(held).toEqual([]);
});

test("shutdown terminates and awaits thumbnail ffmpeg before closing its HTTP source", async () => {
  const nativeSpawn = Bun.spawn.bind(Bun);
  const childReady = deferred<ReturnType<typeof Bun.spawn>>();
  const commands: string[][] = [];
  const spawn = spyOn(Bun, "spawn").mockImplementation(((command: string[]) => {
    commands.push(command);
    // A real, owned child stands in only for the ffmpeg executable. The production
    // runner retains, terminates, and awaits this process during shutdown.
    const child = nativeSpawn([process.execPath, "-e",
      "await Bun.write(process.argv[1], new Uint8Array([255, 216, 255, 217])); console.log('ready'); setInterval(() => {}, 60000)",
      command.at(-1)!,
    ], {
      stdout: "pipe", stderr: "ignore",
    });
    childReady.resolve(child);
    return child;
  }) as typeof Bun.spawn);
  const db = library("Held");
  const server = await startServer({ db, source: { stream: () => new ReadableStream<Uint8Array>() } });
  const sheets = new SheetStore({ directory: root, baseUrl: `http://127.0.0.1:${server.port}`, isHeld: () => true });
  const making = sheets.ensure("SET", spritePlan(120));
  const child = await childReady.promise;
  let childExited = false;
  void child.exited.then(() => { childExited = true; });
  const output = (child.stdout as ReadableStream<Uint8Array>).getReader();
  const order: string[] = [];
  const stop = shutdownFor({
    timers: [], sheets,
    server: { close: async () => {
      order.push("server-close");
      expect(childExited).toBe(true);
      expect(await making).toBe(false);
      await server.close();
    } },
    telegram: { disconnect: async () => { order.push("disconnect"); } },
  });
  try {
    expect(new TextDecoder().decode((await output.read()).value)).toContain("ready");
    expect(await readdir(root)).toEqual(["SET.making.jpg"]);
    await stop();
    expect(await sheets.ensure("LATE", spritePlan(120))).toBe(false);
    expect(commands).toHaveLength(1);
    expect(commands[0]?.[0]).toBe("ffmpeg");
    expect(child.signalCode).toBe("SIGTERM");
    expect(order).toEqual(["server-close", "disconnect"]);
    expect(await readdir(root)).toEqual([]);
  } finally {
    child.kill();
    await child.exited;
    await making;
    await output.cancel();
    spawn.mockRestore();
    await server.close();
    db.close();
  }
});

test("stopping a thumbnail request before process launch closes admission and awaits its filesystem work", async () => {
  let starts = 0;
  const sheets = new SheetStore({ directory: root, baseUrl: "http://127.0.0.1:8770", isHeld: () => true }, async () => {
    starts++;
    return 0;
  });
  const making = sheets.ensure("SET", spritePlan(120));
  try {
    const stopped = sheets.stop();
    expect(await sheets.ensure("LATE", spritePlan(120))).toBe(false);
    await stopped;
  } finally { await making; }
  expect(await making).toBe(false);
  expect(starts).toBe(0);
  expect(await readdir(root)).toEqual([]);
});

test("the production startup hands its preload and thumbnail workers to shutdown", async () => {
  const db = library("Episode");
  db.run("UPDATE sets SET kind = 'ep'");
  db.run("UPDATE parts SET chat_id = 1");
  await writeFile(join(root, "library.db"), db.serialize());
  db.close();
  const started = deferred<void>();
  const finish = deferred<void>();
  const order: string[] = [];
  const telegram = telegramBoundary(order);
  telegram.partMedia = async () => ({}) as never;
  telegram.client.iterDownload = (async function* () {
    order.push("download");
    started.resolve();
    await finish.promise;
    yield new Uint8Array(10);
  }) as never;
  const stopSheets = spyOn(SheetStore.prototype, "stop");
  let player: Awaited<ReturnType<typeof startPlayer>> | undefined;
  try {
    player = await startPlayer({ ...configIn(root), cacheMaxBytes: 100_000_000, seriesPreload: true }, {
      open: async () => telegram, findIndex: async () => "nothing-pinned",
      detectEncoder: async () => ({ kind: "software", name: "libx264" }),
      listen: () => () => {},
    });
    await player.ready;
    const response = await fetch(`http://127.0.0.1:${player.server.port}/api/preload`, {
      method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ setIds: ["01SET"] }),
    });
    expect(response.status).toBe(202);
    await started.promise;
    let completed = false;
    const stopped = player.stop().then(() => { completed = true; });
    await new Promise<void>((resolve) => setImmediate(resolve));
    expect(stopSheets).toHaveBeenCalledTimes(1);
    expect(completed).toBe(false);
    expect(order).toEqual(["download"]);
    finish.resolve();
    await stopped;
    expect(order).toEqual(["download", "disconnect"]);
  } finally {
    finish.resolve();
    await player?.stop();
    stopSheets.mockRestore();
  }
});
