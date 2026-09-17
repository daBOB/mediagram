/**
 * The cache in front of Telegram.
 *
 * What matters is not that it stores bytes but that a second read of the same
 * range costs no upstream request at all — that is the difference between a
 * scrub bar that is free and one that is billed to your uplink.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { CACHE_CHUNK } from "../src/cache/key";
import { MAX_RUN_BYTES } from "../src/cache/reader";
import { ChunkCache } from "../src/cache/store";
import { CachedReader } from "../src/cache/reader";

const SET = "01SET0000000000000000001";
let root: string;

beforeEach(async () => {
  root = await mkdtemp(join(tmpdir(), "mediagram-reader-"));
});
afterEach(async () => {
  await rm(root, { recursive: true, force: true });
});

/** The bytes of a part, and a record of what was asked of it. */
function upstream(partLength: number) {
  const bytes = new Uint8Array(partLength);
  for (let i = 0; i < partLength; i++) bytes[i] = i % 251;
  const asked: { offset: number; length: number }[] = [];
  return {
    bytes,
    asked,
    fetch: async (offset: number, length: number) => {
      asked.push({ offset, length });
      return bytes.subarray(offset, Math.min(offset + length, partLength));
    },
  };
}

describe("reading through the cache", () => {
  test("the first read fetches and the second does not", async () => {
    const part = upstream(CACHE_CHUNK * 2);
    const reader = new CachedReader(new ChunkCache(root, 10_000_000));

    const first = await reader.read(SET, 0, 1000, 500, part.bytes.length, part.fetch);
    const before = part.asked.length;
    const second = await reader.read(SET, 0, 1000, 500, part.bytes.length, part.fetch);

    expect(first).toEqual(part.bytes.subarray(1000, 1500));
    expect(second).toEqual(first);
    expect(before).toBeGreaterThan(0);
    expect(part.asked.length).toBe(before);
  });

  test("bytes are correct across a chunk boundary", async () => {
    const part = upstream(CACHE_CHUNK * 3);
    const reader = new CachedReader(new ChunkCache(root, 10_000_000));

    const start = CACHE_CHUNK - 50;
    const got = await reader.read(SET, 0, start, 100, part.bytes.length, part.fetch);

    expect(got).toEqual(part.bytes.subarray(start, start + 100));
  });

  test("only the missing chunks are fetched on a partial hit", async () => {
    const part = upstream(CACHE_CHUNK * 3);
    const reader = new CachedReader(new ChunkCache(root, 10_000_000));

    await reader.read(SET, 0, 0, 10, part.bytes.length, part.fetch);
    const afterFirst = part.asked.length;
    // Spans chunk 0 (cached) and chunk 1 (not).
    await reader.read(SET, 0, 0, CACHE_CHUNK + 10, part.bytes.length, part.fetch);

    expect(part.asked.length).toBe(afterFirst + 1);
  });

  test("a read at the very end of a part returns the short tail", async () => {
    const length = CACHE_CHUNK + 123;
    const part = upstream(length);
    const reader = new CachedReader(new ChunkCache(root, 10_000_000));

    const got = await reader.read(SET, 0, length - 23, 23, length, part.fetch);

    expect(got).toEqual(part.bytes.subarray(length - 23));
  });

  /** With no budget at all the player must still work, just without help. */
  test("a cache that keeps nothing still returns correct bytes", async () => {
    const part = upstream(CACHE_CHUNK * 2);
    const reader = new CachedReader(new ChunkCache(root, 0));

    const got = await reader.read(SET, 0, 100, 2000, part.bytes.length, part.fetch);

    expect(got).toEqual(part.bytes.subarray(100, 2100));
  });

  test("fetches are chunk-aligned, whatever the caller asked for", async () => {
    const part = upstream(CACHE_CHUNK * 3);
    const reader = new CachedReader(new ChunkCache(root, 10_000_000));

    await reader.read(SET, 0, 1234, 5678, part.bytes.length, part.fetch);

    for (const ask of part.asked) {
      expect(ask.offset % CACHE_CHUNK).toBe(0);
    }
  });
});

describe("reading ahead", () => {
  /** Playback walks forward; the chunk after the one being served should
   *  already be arriving by the time the browser asks for it. */
  test("a sequential read warms the chunks after it", async () => {
    const part = upstream(CACHE_CHUNK * 10);
    const reader = new CachedReader(new ChunkCache(root, 10_000_000), 3);

    await reader.read(SET, 0, 0, CACHE_CHUNK, part.bytes.length, part.fetch);
    await reader.read(SET, 0, CACHE_CHUNK, CACHE_CHUNK, part.bytes.length, part.fetch);
    await reader.settle();

    const asked = part.asked.length;
    // Chunk 2 was read ahead, so serving it costs nothing upstream.
    await reader.read(SET, 0, CACHE_CHUNK * 2, CACHE_CHUNK, part.bytes.length, part.fetch);

    expect(part.asked.length).toBe(asked);
  });

  test("a scattered read does not warm anything", async () => {
    const part = upstream(CACHE_CHUNK * 100);
    const reader = new CachedReader(new ChunkCache(root, 10_000_000), 3);

    await reader.read(SET, 0, 0, 1000, part.bytes.length, part.fetch);
    await reader.read(SET, 0, CACHE_CHUNK * 50, 1000, part.bytes.length, part.fetch);
    await reader.settle();

    // Exactly the two chunks asked for, nothing speculative.
    expect(part.asked.length).toBe(2);
  });

  test("readahead never changes the bytes returned", async () => {
    const part = upstream(CACHE_CHUNK * 6);
    const reader = new CachedReader(new ChunkCache(root, 10_000_000), 3);

    const a = await reader.read(SET, 0, 0, CACHE_CHUNK, part.bytes.length, part.fetch);
    const b = await reader.read(SET, 0, CACHE_CHUNK, 777, part.bytes.length, part.fetch);
    await reader.settle();

    expect(a).toEqual(part.bytes.subarray(0, CACHE_CHUNK));
    expect(b).toEqual(part.bytes.subarray(CACHE_CHUNK, CACHE_CHUNK + 777));
  });

  test("readahead stops at the end of a part", async () => {
    const length = CACHE_CHUNK * 2 + 10;
    const part = upstream(length);
    const reader = new CachedReader(new ChunkCache(root, 10_000_000), 5);

    await reader.read(SET, 0, 0, CACHE_CHUNK, length, part.fetch);
    await reader.read(SET, 0, CACHE_CHUNK, CACHE_CHUNK, length, part.fetch);
    await reader.settle();

    for (const ask of part.asked) {
      expect(ask.offset).toBeLessThan(length);
    }
  });
});

describe("filling misses efficiently", () => {
  /**
   * The cache turned one streaming download into one request per 512 KiB
   * chunk, each a Telegram round trip of a few hundred milliseconds. A 20 MB
   * read became forty of them in series, which is slower than having no cache
   * at all — and slow enough that ffmpeg cannot get through a file.
   */
  test("consecutive missing chunks are fetched in one request", async () => {
    const part = upstream(CACHE_CHUNK * 20);
    const reader = new CachedReader(new ChunkCache(root, 10_000_000));

    await reader.read(SET, 0, 0, CACHE_CHUNK * 8, part.bytes.length, part.fetch);

    expect(part.asked).toHaveLength(1);
    expect(part.asked[0]!.length).toBe(CACHE_CHUNK * 8);
  });

  test("the bytes are still exactly right", async () => {
    const part = upstream(CACHE_CHUNK * 6);
    const reader = new CachedReader(new ChunkCache(root, 10_000_000));

    const got = await reader.read(SET, 0, 1234, CACHE_CHUNK * 4, part.bytes.length, part.fetch);

    expect(got).toEqual(part.bytes.subarray(1234, 1234 + CACHE_CHUNK * 4));
  });

  test("each chunk of a run is cached separately, so a later read hits", async () => {
    const part = upstream(CACHE_CHUNK * 10);
    const reader = new CachedReader(new ChunkCache(root, 10_000_000));

    await reader.read(SET, 0, 0, CACHE_CHUNK * 5, part.bytes.length, part.fetch);
    const after = part.asked.length;
    // A read wholly inside what was just fetched must cost nothing.
    await reader.read(SET, 0, CACHE_CHUNK * 2, CACHE_CHUNK, part.bytes.length, part.fetch);

    expect(part.asked.length).toBe(after);
  });

  /** A hit in the middle splits the run; both sides still batch. */
  test("a cached chunk between misses does not force one request per chunk", async () => {
    const part = upstream(CACHE_CHUNK * 10);
    const reader = new CachedReader(new ChunkCache(root, 10_000_000));

    // Warm chunk 2 alone.
    await reader.read(SET, 0, CACHE_CHUNK * 2, CACHE_CHUNK, part.bytes.length, part.fetch);
    const after = part.asked.length;

    // Chunks 0-4: 0,1 missing, 2 cached, 3,4 missing => two requests.
    await reader.read(SET, 0, 0, CACHE_CHUNK * 5, part.bytes.length, part.fetch);

    expect(part.asked.length).toBe(after + 2);
  });

  test("a run stops at the end of the part", async () => {
    const length = CACHE_CHUNK * 3 + 77;
    const part = upstream(length);
    const reader = new CachedReader(new ChunkCache(root, 10_000_000));

    await reader.read(SET, 0, 0, length, length, part.fetch);

    for (const ask of part.asked) {
      expect(ask.offset + ask.length).toBeLessThanOrEqual(length);
    }
  });
});

describe("streaming rather than buffering", () => {
  /**
   * The first version returned the whole range as one array, which meant the
   * response could not begin until the last byte had been fetched — headers
   * delayed by seconds, and a request without a Range would have tried to
   * hold an entire film in memory.
   */
  test("bytes arrive progressively, not all at the end", async () => {
    const part = upstream(CACHE_CHUNK * 8);
    const reader = new CachedReader(new ChunkCache(root, 10_000_000));

    const pieces: number[] = [];
    for await (const piece of reader.readStream(
      SET, 0, 0, CACHE_CHUNK * 4, part.bytes.length, part.fetch,
    )) {
      pieces.push(piece.length);
    }

    expect(pieces.length).toBeGreaterThan(1);
    expect(pieces.reduce((a, b) => a + b, 0)).toBe(CACHE_CHUNK * 4);
  });

  test("the streamed bytes are exactly the range asked for", async () => {
    const part = upstream(CACHE_CHUNK * 5);
    const reader = new CachedReader(new ChunkCache(root, 10_000_000));

    const out: number[] = [];
    for await (const piece of reader.readStream(
      SET, 0, 777, 3333, part.bytes.length, part.fetch,
    )) {
      out.push(...piece);
    }

    expect(new Uint8Array(out)).toEqual(part.bytes.subarray(777, 777 + 3333));
  });

  /**
   * A whole-file request over a cold cache is one unbroken run of misses. Left
   * unbounded, the run is the rest of the film: one fetch of several
   * gigabytes, held in memory, yielding nothing until it finishes. ffmpeg
   * stops after the segments it already has and waits forever.
   */
  test("a long run of misses is fetched in bounded pieces", async () => {
    const part = upstream(CACHE_CHUNK * 64);
    const reader = new CachedReader(new ChunkCache(root, 100_000_000));

    for await (const _ of reader.readStream(
      SET, 0, 0, CACHE_CHUNK * 64, part.bytes.length, part.fetch,
    )) { /* drain */ }

    expect(part.asked.length).toBeGreaterThan(1);
    for (const ask of part.asked) expect(ask.length).toBeLessThanOrEqual(MAX_RUN_BYTES);
  });

  test("the first bytes arrive after one fetch, not after all of them", async () => {
    const part = upstream(CACHE_CHUNK * 64);
    const reader = new CachedReader(new ChunkCache(root, 100_000_000));

    for await (const _ of reader.readStream(
      SET, 0, 0, CACHE_CHUNK * 64, part.bytes.length, part.fetch,
    )) {
      break;
    }

    expect(part.asked).toHaveLength(1);
  });

  test("a long streamed range is still byte-exact", async () => {
    const part = upstream(CACHE_CHUNK * 40);
    const reader = new CachedReader(new ChunkCache(root, 100_000_000));

    const out: number[] = [];
    for await (const piece of reader.readStream(
      SET, 0, 100, CACHE_CHUNK * 39, part.bytes.length, part.fetch,
    )) {
      out.push(...piece);
    }

    expect(new Uint8Array(out)).toEqual(part.bytes.subarray(100, 100 + CACHE_CHUNK * 39));
  });

  test("streaming still batches consecutive misses into one request", async () => {
    const part = upstream(CACHE_CHUNK * 10);
    const reader = new CachedReader(new ChunkCache(root, 10_000_000));

    for await (const _ of reader.readStream(
      SET, 0, 0, CACHE_CHUNK * 6, part.bytes.length, part.fetch,
    )) { /* drain */ }

    expect(part.asked).toHaveLength(1);
  });
});
