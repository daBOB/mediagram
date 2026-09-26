import { afterEach, beforeEach, expect, spyOn, test } from "bun:test";
import { mkdtemp, readdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { CachedReader } from "../src/cache/reader";
import { ChunkCache } from "../src/cache/store";
import { CACHE_CHUNK, chunkPath } from "../src/cache/key";
import { HeldSets, expectedChunks } from "../src/cache/held";
import { TelegramSource } from "../src/telegram/source";
import { TelegramConnection } from "../src/telegram/connection";
import { SheetStore } from "../src/thumbs/sheets";
import { startServer } from "../src/server";
import { createRouter } from "../src/routes";
import { spritePlan } from "../public/lib/sprite-plan.js";
import { library, telegramBoundary } from "./application-fixture";

let root: string;
beforeEach(async () => { root = await mkdtemp(join(tmpdir(), "thumbnail-cache-only-")); });
afterEach(async () => { await rm(root, { recursive: true, force: true }); });
const payload = new TextEncoder().encode("0123456789");

test.each(["evicted", "truncated"])("a chunk %s after thumbnail admission cannot fetch upstream or publish a sheet", async (failure) => {
  const cacheRoot = join(root, "cache");
  const cache = new ChunkCache(cacheRoot, 1_000_000);
  await cache.put("01SET", 0, 0, payload);
  const db = library("Held movie");
  db.run("UPDATE parts SET chat_id = 1");
  const held = new HeldSets(cacheRoot, expectedChunks(db));
  await held.refresh();
  expect(held.has("01SET")).toBe(true);
  let upstream = 0;
  const telegram = telegramBoundary([]);
  telegram.partMedia = async () => { upstream++; return {} as never; };
  telegram.client.iterDownload = (async function* () { yield payload; }) as never;
  const source = new TelegramSource(TelegramConnection.fixed(telegram), new CachedReader(cache, 4));
  const server = await startServer({ db, source,
    cacheSource: { stream: (...args) => source.streamCached(...args) },
  });
  const errors = spyOn(console, "error").mockImplementation(() => {});
  const inputs: string[] = [];
  const sheets = new SheetStore({ directory: join(root, "sheets"), baseUrl: server.baseUrl, isHeld: (id) => held.has(id) }, async (args) => {
    const input = args[args.indexOf("-i") + 1]!;
    inputs.push(input);
    // The process boundary runs only after held-state admission. Reproduce a
    // real filesystem change before ffmpeg's first HTTP range request.
    const path = chunkPath(cacheRoot, "01SET", 0, 0);
    if (failure === "evicted") await rm(path);
    else await writeFile(path, payload.subarray(0, 5));
    await writeFile(args.at(-1)!, new Uint8Array([255, 216, 255, 217]));
    try {
      const response = await fetch(input, { headers: { range: "bytes=0-9" } });
      const body = await response.arrayBuffer();
      return response.status === 206 && body.byteLength === 10 ? 0 : 1;
    } catch { return 1; }
  });
  try {
    const published = await sheets.ensure("01SET", spritePlan(120));
    expect(upstream).toBe(0);
    expect(errors.mock.calls).toEqual([["stream aborted mid-body", expect.objectContaining({
      message: "cached chunk 0 of part 0 is unavailable",
    })]]);
    expect(published).toBe(false);
    expect(await sheets.sizeOf("01SET")).toBeNull();
    expect(await readdir(join(root, "sheets"))).toEqual([]);
    expect(inputs).toEqual([`${server.baseUrl}/api/sets/01SET/cached-stream`]);
  } finally {
    await sheets.stop();
    await server.close();
    errors.mockRestore();
    db.close();
  }
});

test("cache-only stream preserves GET and HEAD ranges and refuses writes or absent cache support", async () => {
  const cache = new ChunkCache(root, 1_000_000);
  await cache.put("01SET", 0, 0, payload);
  const db = library("Cached");
  db.run("UPDATE parts SET chat_id = 1");
  const source = new TelegramSource(TelegramConnection.fixed(telegramBoundary([])), new CachedReader(cache));
  const cached = spyOn(source, "streamCached");
  const route = createRouter({ db, source, cacheSource: { stream: (...args) => source.streamCached(...args) } });
  const request = { method: "GET", path: "/api/sets/01SET/cached-stream", range: "bytes=2-5" };
  try {
    const response = await route(request);
    expect(response.status).toBe(206);
    expect(response.headers["content-range"]).toBe("bytes 2-5/10");
    expect(response.headers["content-length"]).toBe("4");
    if (!(response.body instanceof ReadableStream)) throw new Error("missing cached byte stream");
    expect(await new Response(response.body).text()).toBe("2345");
    const head = await route({ ...request, method: "HEAD" });
    expect(head.status).toBe(206);
    expect(head.headers).toEqual(response.headers);
    expect(head.body).toBeNull();
    expect((await route({ ...request, range: "bytes=10-" })).status).toBe(416);
    expect((await route({ ...request, method: "POST" })).status).toBe(405);
    expect((await createRouter({ db, source })(request)).status).toBe(404);
    expect((await route({ ...request, path: "/api/sets/UNKNOWN/cached-stream" })).status).toBe(404);
    expect(cached).toHaveBeenCalledTimes(1);
    const whole = await route({ ...request, range: null });
    expect(whole.status).toBe(200);
    if (!(whole.body instanceof ReadableStream)) throw new Error("missing full cached byte stream");
    expect(await new Response(whole.body).text()).toBe("0123456789");
  } finally { cached.mockRestore(); db.close(); }
});

test("cache-only streaming without a cache refuses rather than using Telegram", async () => {
  const telegram = telegramBoundary([]);
  const partMedia = spyOn(telegram, "partMedia");
  const source = new TelegramSource(TelegramConnection.fixed(telegram));
  try {
    const body = source.streamCached(
      [{ span: { idx: 0, off: 0, len: 10 }, chatId: 1, messageId: 1 }],
      [{ partIdx: 0, offset: 0, headDrop: 0, take: 10 }], "01SET",
    );
    await expect(new Response(body).arrayBuffer()).rejects.toThrow("cached streaming is unavailable");
    expect(partMedia).not.toHaveBeenCalled();
  } finally { partMedia.mockRestore(); }
});

test("cache-only reads never look ahead beyond requested chunks", async () => {
  const cache = new ChunkCache(root, 10_000_000);
  for (let index = 0; index < 4; index++) await cache.put("01SET", 0, index, new Uint8Array(CACHE_CHUNK));
  const get = spyOn(cache, "get");
  const reader = new CachedReader(cache, 4);
  try {
    for (const start of [0, CACHE_CHUNK]) {
      for await (const _ of reader.readStream({ setId: "01SET", partIdx: 0, start, length: CACHE_CHUNK, partLength: 4 * CACHE_CHUNK })) { /* drain */ }
    }
    await reader.settle();
    expect(get.mock.calls.map((args) => args[2])).toEqual([0, 1]);
    expect(reader.stats().fetchedBytes).toBe(0);
  } finally { get.mockRestore(); }
});
