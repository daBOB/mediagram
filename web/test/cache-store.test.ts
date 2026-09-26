/**
 * The chunk store, and the quota that keeps it from eating the disk.
 *
 * A media cache with no ceiling fills whatever it is given: this library is
 * 30 GB and growing, and the machine it runs on has other work to do. The
 * quota is the point of the thing, so most of these tests are about eviction.
 */

import { collectRead } from "./support/cache-reader";
import { afterEach, beforeEach, describe, expect, spyOn, test } from "bun:test";
import { chmod, mkdir, mkdtemp, rm, stat, symlink, utimes, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";

import { ChunkCache } from "../src/cache/store";
import { CachedReader } from "../src/cache/reader";
import { CACHE_CHUNK, chunkPath } from "../src/cache/key";

const SET = "01SET0000000000000000001";
let root: string;

beforeEach(async () => {
  root = await mkdtemp(join(tmpdir(), "mediagram-cache-"));
});
afterEach(async () => {
  await rm(root, { recursive: true, force: true });
});

const block = (byte: number, size = 1024) => new Uint8Array(size).fill(byte);

describe("inventory failures", () => {
  test("a missing cache remains an empty inventory", async () => {
    const cache = new ChunkCache(join(root, "missing"), 0);
    expect(await cache.sizeOnDisk()).toBe(0);
    expect(await cache.evict()).toBe(0);
  });

  test("a non-directory cache path reports its error instead of zero usage", async () => {
    const obstruction = join(root, "file");
    await writeFile(obstruction, "not a directory");
    const cache = new ChunkCache(join(obstruction, "chunks"), 0);
    await expect(cache.sizeOnDisk()).rejects.toMatchObject({ code: "ENOTDIR" });
    await expect(cache.evict()).rejects.toMatchObject({ code: "ENOTDIR" });
  });

  test("a vanished entry is harmless but other stat errors cannot become partial totals", async () => {
    await symlink("missing", join(root, "vanished"));
    const cache = new ChunkCache(root, 0);
    expect(await cache.sizeOnDisk()).toBe(0);
    await symlink("loop", join(root, "loop"));
    await expect(cache.sizeOnDisk()).rejects.toMatchObject({ code: "ELOOP" });
    await expect(cache.evict()).rejects.toMatchObject({ code: "ELOOP" });
  });

  test.skipIf(process.getuid?.() === 0)("an unreadable subtree reports maintenance failure without rejecting delivered bytes", async () => {
    const seed = new ChunkCache(root, 10_000);
    await seed.put(SET, 0, 0, block(1));
    const locked = dirname(chunkPath(root, SET, 0, 0));
    await chmod(locked, 0o000);
    const warning = spyOn(console, "warn").mockImplementation(() => {});
    try {
      const cache = new ChunkCache(root, 10_000);
      const payload = block(2, 256);
      const reader = new CachedReader(cache);
      const delivered = await collectRead(reader, {
        setId: "NEXT", partIdx: 0, start: 0, length: payload.length,
        partLength: payload.length, fetch: async () => payload,
      });
      // The write and the eviction it schedules happen in the background: the
      // stream above already has its bytes without waiting for either.
      await reader.settle();
      expect(delivered).toEqual(payload);
      expect(warning).toHaveBeenCalledWith("cache eviction failed:", expect.objectContaining({ code: "EACCES" }));
      await expect(cache.sizeOnDisk()).rejects.toMatchObject({ code: "EACCES" });
      await expect(cache.evict()).rejects.toMatchObject({ code: "EACCES" });
    } finally {
      await chmod(locked, 0o700);
      warning.mockRestore();
    }
  });
});

describe("storing and reading", () => {
  test("a chunk survives the round trip", async () => {
    const cache = new ChunkCache(root, 10_000_000);
    await cache.put(SET, 0, 5, block(7));

    expect(await cache.get(SET, 0, 5)).toEqual(block(7));
  });

  test("a chunk that was never stored is a miss, not an error", async () => {
    const cache = new ChunkCache(root, 10_000_000);

    expect(await cache.get(SET, 0, 5)).toBeNull();
  });

  test("chunks of different parts and sets do not collide", async () => {
    const cache = new ChunkCache(root, 10_000_000);
    await cache.put(SET, 0, 1, block(1));
    await cache.put(SET, 1, 1, block(2));
    await cache.put("01SET0000000000000000002", 0, 1, block(3));

    expect((await cache.get(SET, 0, 1))![0]).toBe(1);
    expect((await cache.get(SET, 1, 1))![0]).toBe(2);
    expect((await cache.get("01SET0000000000000000002", 0, 1))![0]).toBe(3);
  });
});

describe("checking presence without reading", () => {
  test("has reports a held chunk without counting a hit", async () => {
    const cache = new ChunkCache(root, 10_000_000);
    await cache.put(SET, 0, 0, block(1, 1024));

    expect(await cache.has(SET, 0, 0, 1024)).toBe(true);
    expect(await cache.has(SET, 0, 1)).toBe(false);
    expect(cache.stats()).toMatchObject({ hits: 0, misses: 0 });
  });

  test("has rejects a chunk of the wrong size, the same as get", async () => {
    const cache = new ChunkCache(root, 10_000_000);
    await cache.put(SET, 0, 0, block(1, 100));

    expect(await cache.has(SET, 0, 0, 200)).toBe(false);
  });

  test("has does not bump the access time a real read would", async () => {
    const cache = new ChunkCache(root, 10_000_000);
    await cache.put(SET, 0, 0, block(1, 100));
    const path = chunkPath(root, SET, 0, 0);
    const before = (await stat(path)).atimeMs;

    await cache.has(SET, 0, 0, 100);

    expect((await stat(path)).atimeMs).toBe(before);
  });

  test("has sees a chunk whose write has not landed on disk yet", async () => {
    const cache = new ChunkCache(root, 10_000_000);
    const payload = block(4, 2048);

    const writing = cache.put(SET, 0, 2, payload);
    expect(await cache.has(SET, 0, 2, payload.length)).toBe(true);

    await writing;
  });
});

describe("a write not yet on disk", () => {
  test("a concurrent get is served the pending bytes rather than missing", async () => {
    const cache = new ChunkCache(root, 10_000_000);
    const payload = block(9, 4096);

    // Not awaited: the write is still in flight when `get` is called below.
    const writing = cache.put(SET, 0, 0, payload);
    const seen = await cache.get(SET, 0, 0, payload.length);

    expect(seen).toEqual(payload);
    expect(cache.stats().hits).toBe(1);
    await writing;
  });

  test("pending bytes are also rejected on a size mismatch", async () => {
    const cache = new ChunkCache(root, 10_000_000);
    const writing = cache.put(SET, 0, 0, block(1, 100));

    expect(await cache.get(SET, 0, 0, 999)).toBeNull();

    await writing;
  });
});

describe("the quota", () => {
  // Root bypasses filesystem write permissions, so it cannot reproduce EACCES.
  test.skipIf(process.getuid?.() === 0)("automatic eviction failure preserves playback while explicit eviction reports it", async () => {
    const seed = new ChunkCache(root, 10_000);
    await seed.put(SET, 0, 0, block(1));
    const locked = chunkPath(root, SET, 0, 0);
    await utimes(locked, new Date(0), new Date(0));
    await chmod(dirname(locked), 0o500);
    const warning = spyOn(console, "warn").mockImplementation(() => {});
    try {
      const cache = new ChunkCache(root, 1024);
      const payload = block(3, 256);
      const reader = new CachedReader(cache);

      const delivered = await collectRead(reader, { setId: "NEXT", partIdx: 0, start: 0, length: payload.length, partLength: payload.length, fetch: async () => payload });
      // The write and the eviction it schedules happen in the background: the
      // stream above already has its bytes without waiting for either.
      await reader.settle();

      expect(delivered).toEqual(payload);
      expect(await cache.get(SET, 0, 0)).toEqual(block(1));
      expect(await cache.get("NEXT", 0, 0)).toEqual(payload);
      expect(await cache.sizeOnDisk()).toBe(1280);
      expect(cache.stats().evicted).toBe(0);
      expect(warning).toHaveBeenCalledWith("cache eviction failed:", expect.objectContaining({ code: "EACCES" }));
      await utimes(locked, new Date(0), new Date(0));
      await expect(cache.evict()).rejects.toMatchObject({ code: "EACCES" });
    } finally {
      warning.mockRestore();
      await chmod(dirname(locked), 0o700);
    }
  });

  test("stays under the budget as chunks are added", async () => {
    const cache = new ChunkCache(root, 4096);

    for (let i = 0; i < 10; i++) {
      await cache.put(SET, 0, i, block(i, 1024));
    }

    expect(await cache.sizeOnDisk()).toBeLessThanOrEqual(4096);
  });

  /** Least recently *used*, not least recently written. */
  test("evicts what was read longest ago", async () => {
    const cache = new ChunkCache(root, 3072);
    await cache.put(SET, 0, 0, block(0));
    await cache.put(SET, 0, 1, block(1));
    await cache.put(SET, 0, 2, block(2));

    // Give every use a distinct time; adjacent writes can share a filesystem
    // timestamp, which leaves eviction free to choose either tied chunk.
    const oldest = Date.now() - 60_000;
    for (let index = 0; index < 3; index++) {
      const used = new Date(oldest + index * 1000);
      await utimes(chunkPath(root, SET, 0, index), used, used);
    }

    // get() touches in the background. Observe that write before eviction
    // instead of relying on a fixed sleep to outlast filesystem scheduling.
    await cache.get(SET, 0, 0);
    const touched = chunkPath(root, SET, 0, 0);
    const deadline = Date.now() + 1000;
    while ((await stat(touched)).atimeMs <= oldest + 2000 && Date.now() < deadline) {
      await Bun.sleep(1);
    }
    expect((await stat(touched)).atimeMs).toBeGreaterThan(oldest + 2000);

    await cache.put(SET, 0, 3, block(3));

    expect(await cache.get(SET, 0, 0)).not.toBeNull();
    expect(await cache.get(SET, 0, 1)).toBeNull();
    expect(await cache.get(SET, 0, 2)).not.toBeNull();
  });

  test("a budget of zero keeps nothing, rather than failing", async () => {
    const cache = new ChunkCache(root, 0);
    await cache.put(SET, 0, 0, block(0));

    expect(await cache.sizeOnDisk()).toBe(0);
    expect(await cache.get(SET, 0, 0)).toBeNull();
  });

  /** A chunk larger than the whole budget must not spin the evictor forever. */
  test("a chunk bigger than the budget is simply not kept", async () => {
    const cache = new ChunkCache(root, 512);
    await cache.put(SET, 0, 0, block(1, 4096));

    expect(await cache.sizeOnDisk()).toBeLessThanOrEqual(512);
  });

  test("setBudget shrinks live, oldest first", async () => {
    const cache = new ChunkCache(root, 1024 ** 3);
    await cache.put(SET, 0, 0, block(0, 1024));
    await utimes(chunkPath(root, SET, 0, 0), new Date(0), new Date(0));
    await cache.put(SET, 0, 1, block(1, 1024));

    const { freedBytes } = await cache.setBudget(1024);

    expect(freedBytes).toBe(1024);
    expect(cache.budget).toBe(1024);
    expect(await cache.get(SET, 0, 0)).toBeNull();
    expect(await cache.get(SET, 0, 1)).not.toBeNull();
  });

  test("setBudget growing evicts nothing", async () => {
    const cache = new ChunkCache(root, 1024);
    await cache.put(SET, 0, 0, block(0, 1024));

    const { freedBytes } = await cache.setBudget(1024 ** 2);

    expect(freedBytes).toBe(0);
    expect(await cache.get(SET, 0, 0)).not.toBeNull();
  });
});

describe("what must never be served", () => {
  /** A partial write, a full disk, a kill mid-copy: all look like this. */
  test("a truncated chunk is refused and removed", async () => {
    const cache = new ChunkCache(root, 10_000_000);
    await cache.put(SET, 0, 0, new Uint8Array(CACHE_CHUNK).fill(9));

    // Corrupt it the way an interrupted write would.
    const path = chunkPath(root, SET, 0, 0);
    await writeFile(path, new Uint8Array(10));

    expect(await cache.get(SET, 0, 0, CACHE_CHUNK)).toBeNull();
    await expect(stat(path)).rejects.toThrow();
  });

  test("a chunk of the expected size is served", async () => {
    const cache = new ChunkCache(root, 10_000_000);
    const full = new Uint8Array(CACHE_CHUNK).fill(3);
    await cache.put(SET, 0, 0, full);

    expect(await cache.get(SET, 0, 0, CACHE_CHUNK)).toEqual(full);
  });

  /** The last chunk of a part is short, and that is not corruption. */
  test("a short final chunk is served when no size is demanded", async () => {
    const cache = new ChunkCache(root, 10_000_000);
    await cache.put(SET, 0, 9, block(4, 100));

    expect(await cache.get(SET, 0, 9)).toEqual(block(4, 100));
  });
});

describe("concurrency", () => {
  /** Two viewers seeking to the same place must not corrupt one file. */
  test("the same chunk written twice at once stays intact", async () => {
    const cache = new ChunkCache(root, 10_000_000);
    const payload = new Uint8Array(8192).fill(5);

    await Promise.all(Array.from({ length: 8 }, () => cache.put(SET, 0, 0, payload)));

    expect(await cache.get(SET, 0, 0, 8192)).toEqual(payload);
  });

  /** Many chunks landing together each schedule eviction; none of that may be lost or duplicated. */
  test("writes past the budget arriving together still land under it once settled", async () => {
    const cache = new ChunkCache(root, 4096);

    await Promise.all(Array.from({ length: 20 }, (_, i) => cache.put(SET, 0, i, block(i, 1024))));

    expect(await cache.sizeOnDisk()).toBeLessThanOrEqual(4096);
  });

  /** A scan already running is shared rather than walked a second time in parallel. */
  test("two evictions requested together share one run and its result", async () => {
    const cache = new ChunkCache(root, 1024);
    // Written directly, bypassing `put`, so nothing has scheduled a scan yet
    // and the two calls below are the first to ask for one.
    const path = chunkPath(root, SET, 0, 0);
    await mkdir(dirname(path), { recursive: true });
    await writeFile(path, block(1, 2048));

    const first = cache.evict();
    const second = cache.evict();

    expect(first).toBe(second);
    expect(await first).toBe(2048);
  });
});
