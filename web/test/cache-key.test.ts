/**
 * Which cache chunks a byte range touches.
 *
 * The cache unit is a fixed aligned block, not the request: a viewer's ranges
 * are arbitrary and overlapping, and caching them as asked would store the
 * same bytes many times over and never hit. Fixed blocks make any range a mix
 * of hits and misses with no partial-overlap arithmetic.
 */

import { describe, expect, test } from "bun:test";
import { CACHE_CHUNK, chunkPath, chunksCovering } from "../src/cache/key";

describe("the chunks a range needs", () => {
  test("a range inside one chunk needs that chunk alone", () => {
    const chunks = chunksCovering(10, 100);

    expect(chunks).toEqual([{ index: 0, offset: 0, skip: 10, take: 100 }]);
  });

  test("a range crossing a boundary needs both chunks", () => {
    const chunks = chunksCovering(CACHE_CHUNK - 10, 20);

    expect(chunks).toHaveLength(2);
    expect(chunks[0]).toEqual({ index: 0, offset: 0, skip: CACHE_CHUNK - 10, take: 10 });
    expect(chunks[1]).toEqual({ index: 1, offset: CACHE_CHUNK, skip: 0, take: 10 });
  });

  test("an aligned range of exactly one chunk skips nothing", () => {
    expect(chunksCovering(CACHE_CHUNK, CACHE_CHUNK)).toEqual([
      { index: 1, offset: CACHE_CHUNK, skip: 0, take: CACHE_CHUNK },
    ]);
  });

  test("a long range is covered completely and in order", () => {
    const chunks = chunksCovering(0, CACHE_CHUNK * 3 + 17);

    expect(chunks).toHaveLength(4);
    expect(chunks.map((c) => c.index)).toEqual([0, 1, 2, 3]);
    expect(chunks.reduce((n, c) => n + c.take, 0)).toBe(CACHE_CHUNK * 3 + 17);
  });

  /** Whatever the range, the chunks must cover it exactly. */
  test("coverage is exact for any range", () => {
    for (const [start, length] of [
      [0, 1],
      [1, 1],
      [CACHE_CHUNK - 1, 2],
      [CACHE_CHUNK * 7 + 123, 4567],
      [3_758_096_384, 65536],
    ] as [number, number][]) {
      const chunks = chunksCovering(start, length);
      expect(chunks.reduce((n, c) => n + c.take, 0)).toBe(length);
      expect(chunks[0]!.offset + chunks[0]!.skip).toBe(start);
      for (const chunk of chunks) {
        expect(chunk.offset % CACHE_CHUNK).toBe(0);
        expect(chunk.skip + chunk.take).toBeLessThanOrEqual(CACHE_CHUNK);
      }
    }
  });

  test("an empty range needs nothing", () => {
    expect(chunksCovering(100, 0)).toEqual([]);
  });
});

describe("where a chunk lives", () => {
  test("the path is derivable from the numbers alone, with no index to consult", () => {
    const path = chunkPath("/cache", "01SET0000000000000000001", 2, 41);

    expect(path).toContain("01SET0000000000000000001");
    expect(path).toContain("/2/");
    expect(path.endsWith("41")).toBe(true);
  });

  /** The chunk size is in the path, so changing it retires old entries
   *  rather than reading them as though they were the new size. */
  test("the chunk size is part of the path", () => {
    expect(chunkPath("/cache", "s", 0, 0)).toContain(String(CACHE_CHUNK));
  });

  /** A set id comes from a caption, which anyone with channel access writes. */
  test("a set id that could escape the cache directory is refused", () => {
    for (const hostile of ["../../etc", "a/b", ".", ""]) {
      expect(() => chunkPath("/cache", hostile, 0, 0)).toThrow();
    }
  });
});
