import { afterEach, beforeEach, expect, test } from "bun:test";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { startPlayer } from "../src/index";
import { CachedReader } from "../src/cache/reader";
import { ChunkCache } from "../src/cache/store";
import { CACHE_CHUNK } from "../src/cache/key";
import { collectRead } from "./support/cache-reader";
import { configIn, deferred, library, telegramBoundary } from "./application-fixture";

let root: string;
beforeEach(async () => { root = await mkdtemp(join(tmpdir(), "cache-shutdown-")); });
afterEach(async () => { await rm(root, { recursive: true, force: true }); });

test("stopping readahead drains its active fetch and prevents warming from later read completions", async () => {
  const cache = new ChunkCache(root, 100_000_000);
  const reader = new CachedReader(cache, 2);
  const started = deferred<void>();
  const finish = deferred<void>();
  const fetched: number[] = [];
  const read = (chunk: number) => collectRead(reader, {
    setId: "SET", partIdx: 0, start: chunk * CACHE_CHUNK,
    length: CACHE_CHUNK, partLength: CACHE_CHUNK * 6,
    fetch: async (offset, length) => {
      fetched.push(offset);
      if (offset === CACHE_CHUNK * 2) { started.resolve(); await finish.promise; }
      return new Uint8Array(length);
    },
  });
  let stopping: Promise<void> | undefined;
  try {
    await read(0);
    await read(1);
    await started.promise;
    let stopped = false;
    stopping = reader.stop().then(() => { stopped = true; });
    expect(reader.stop()).toBe(reader.stop());
    // Completing the next sequential foreground read during shutdown must not
    // schedule another fetch. The already-admitted warming request still drains.
    await cache.put("SET", 0, 2, new Uint8Array(CACHE_CHUNK));
    await read(2);
    await new Promise<void>((resolve) => setImmediate(resolve));
    expect(stopped).toBe(false);
    expect(fetched).toEqual([0, CACHE_CHUNK, CACHE_CHUNK * 2]);
    finish.resolve();
    await stopping;
    await read(3);
    await reader.settle();
    expect(fetched).toEqual([0, CACHE_CHUNK, CACHE_CHUNK * 2, CACHE_CHUNK * 3]);
  } finally { finish.resolve(); await stopping; await reader.settle(); }
});

test("production shutdown waits for speculative downloads before disconnecting Telegram", async () => {
  const db = library("Cached movie");
  db.run("UPDATE sets SET total = ?", [CACHE_CHUNK * 5]);
  db.run("UPDATE parts SET chat_id = 1, byte_length = ?", [CACHE_CHUNK * 5]);
  await writeFile(join(root, "library.db"), db.serialize());
  db.close();
  const started = deferred<void>();
  const finish = deferred<void>();
  const order: string[] = [];
  const telegram = telegramBoundary(order);
  telegram.partMedia = async () => ({}) as never;
  telegram.client.iterDownload = (async function* (_media: unknown, options: { offset: unknown }) {
    const offset = Number(String(options.offset));
    if (offset === CACHE_CHUNK * 2) {
      started.resolve();
      await finish.promise;
    }
    try { yield new Uint8Array(CACHE_CHUNK); }
    finally { if (offset === CACHE_CHUNK * 2) order.push("warming-cleaned"); }
  }) as never;
  let player: Awaited<ReturnType<typeof startPlayer>> | undefined;
  let stopping: Promise<void> | undefined;
  try {
    player = await startPlayer({ ...configIn(root), cacheMaxBytes: 100_000_000, cacheReadahead: 1 }, {
      connect: async () => telegram, findIndex: async () => "nothing-pinned",
      detectEncoder: async () => ({ kind: "software", name: "libx264" }), listen: () => () => {},
    });
    await player.ready;
    for (const offset of [0, CACHE_CHUNK]) {
      const response = await fetch(`${player.server.baseUrl}/api/sets/01SET/stream`, {
        headers: { Range: `bytes=${offset}-${offset + CACHE_CHUNK - 1}` },
      });
      expect(response.status).toBe(206);
      expect((await response.arrayBuffer()).byteLength).toBe(CACHE_CHUNK);
    }
    await started.promise;
    let stopped = false;
    stopping = player.stop().then(() => { stopped = true; });
    // Even after all foreground HTTP work has closed, this independent
    // speculative request must keep its upstream connection alive.
    await player.server.close();
    await new Promise<void>((resolve) => setImmediate(resolve));
    expect(stopped).toBe(false);
    expect(order).toEqual([]);
    finish.resolve();
    await stopping;
    expect(order).toEqual(["warming-cleaned", "disconnect"]);
  } finally { finish.resolve(); await stopping; await player?.stop(); }
}, 7000);
