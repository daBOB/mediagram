/**
 * A fetched run used to be handed back only once every chunk of it had also
 * been written to disk: a viewer waited on maintenance work that has nothing
 * to do with the bytes in front of them. Now a read completes as soon as its
 * bytes have arrived, and the cache write for them runs in the background.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { CACHE_CHUNK } from "../src/cache/key";
import { CachedReader } from "../src/cache/reader";
import { ChunkCache } from "../src/cache/store";
import { collectRead } from "./support/cache-reader";

const SET = "01SET0000000000000000001";
let root: string;

beforeEach(async () => { root = await mkdtemp(join(tmpdir(), "mediagram-write-behind-")); });
afterEach(async () => { await rm(root, { recursive: true, force: true }); });

describe("a foreground read does not wait on its own persistence", () => {
  test("the stream completes while the chunk's write is still gated", async () => {
    const cache = new ChunkCache(root, 10_000_000);
    const release = Promise.withResolvers<void>();
    let putCalled = false;
    const originalPut = cache.put.bind(cache);
    // A stand-in `put` that never resolves until the test says so: if a read
    // still awaited it internally, `collectRead` below would hang and this
    // test would time out rather than fail cleanly.
    cache.put = (async (...args: Parameters<ChunkCache["put"]>) => {
      putCalled = true;
      await release.promise;
      return originalPut(...args);
    }) as ChunkCache["put"];

    const payload = new Uint8Array(CACHE_CHUNK).fill(6);
    const reader = new CachedReader(cache);

    const delivered = await collectRead(reader, {
      setId: SET, partIdx: 0, start: 0, length: CACHE_CHUNK,
      partLength: payload.length, fetch: async () => payload,
    });

    expect(delivered).toEqual(payload);
    expect(putCalled).toBe(true);

    release.resolve();
    await reader.settle();
    expect(await cache.get(SET, 0, 0)).toEqual(payload);
  });
});
