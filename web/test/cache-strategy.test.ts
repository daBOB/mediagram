/**
 * What the cache fetches beyond what was asked.
 *
 * Caching only what was requested helps a second viewing and does nothing for
 * the first: playback walks forward through a file, and every chunk is a miss
 * until someone has already watched it. Each miss is a Telegram round trip of
 * 150-450 ms, and a 4 Mbit/s stream wants a 512 KiB chunk roughly every
 * second — so misses in series are what makes playback stutter.
 *
 * Reading ahead turns that into one round trip amortised over several chunks.
 * It is deliberately only done when a read looks sequential: a scrub bar
 * produces scattered reads, and reading ahead from each of those would spend
 * bandwidth on bytes nobody will watch.
 */

import { describe, expect, test } from "bun:test";
import { CACHE_CHUNK } from "../src/cache/key";
import { ReadaheadTracker } from "../src/cache/strategy";

describe("recognising sequential playback", () => {
  test("the first read of a part is not yet a sequence", () => {
    const tracker = new ReadaheadTracker(4);

    expect(tracker.aheadFor("s", 0, 0, CACHE_CHUNK)).toBe(0);
  });

  test("a read continuing where the last one ended is sequential", () => {
    const tracker = new ReadaheadTracker(4);
    tracker.aheadFor("s", 0, 0, CACHE_CHUNK);

    expect(tracker.aheadFor("s", 0, CACHE_CHUNK, CACHE_CHUNK)).toBeGreaterThan(0);
  });

  test("readahead grows as the sequence continues, up to the limit", () => {
    const tracker = new ReadaheadTracker(4);
    let last = 0;
    for (let i = 0; i < 8; i++) {
      last = tracker.aheadFor("s", 0, i * CACHE_CHUNK, CACHE_CHUNK);
    }
    expect(last).toBe(4);
  });

  /** A scrub bar. Reading ahead from each jump would buy bytes nobody wants. */
  test("a jump elsewhere is not sequential and reads ahead nothing", () => {
    const tracker = new ReadaheadTracker(4);
    tracker.aheadFor("s", 0, 0, CACHE_CHUNK);
    tracker.aheadFor("s", 0, CACHE_CHUNK, CACHE_CHUNK);

    expect(tracker.aheadFor("s", 0, 500 * CACHE_CHUNK, CACHE_CHUNK)).toBe(0);
  });

  test("a jump starts a new sequence rather than poisoning the old one", () => {
    const tracker = new ReadaheadTracker(4);
    tracker.aheadFor("s", 0, 0, CACHE_CHUNK);
    tracker.aheadFor("s", 0, 500 * CACHE_CHUNK, CACHE_CHUNK);

    expect(tracker.aheadFor("s", 0, 501 * CACHE_CHUNK, CACHE_CHUNK)).toBeGreaterThan(0);
  });

  test("two parts of one set are tracked apart", () => {
    const tracker = new ReadaheadTracker(4);
    tracker.aheadFor("s", 0, 0, CACHE_CHUNK);
    tracker.aheadFor("s", 1, 0, CACHE_CHUNK);

    // Part 0 continues; part 1's first read must not have broken it.
    expect(tracker.aheadFor("s", 0, CACHE_CHUNK, CACHE_CHUNK)).toBeGreaterThan(0);
  });

  test("two sets are tracked apart", () => {
    const tracker = new ReadaheadTracker(4);
    tracker.aheadFor("a", 0, 0, CACHE_CHUNK);
    tracker.aheadFor("b", 0, 0, CACHE_CHUNK);

    expect(tracker.aheadFor("a", 0, CACHE_CHUNK, CACHE_CHUNK)).toBeGreaterThan(0);
  });

  test("readahead can be turned off entirely", () => {
    const tracker = new ReadaheadTracker(0);
    tracker.aheadFor("s", 0, 0, CACHE_CHUNK);

    expect(tracker.aheadFor("s", 0, CACHE_CHUNK, CACHE_CHUNK)).toBe(0);
  });

  /** Memory must not grow with every set a long-running server ever served. */
  test("the tracker does not grow without bound", () => {
    const tracker = new ReadaheadTracker(4);
    for (let i = 0; i < 500; i++) tracker.aheadFor(`set${i}`, 0, 0, CACHE_CHUNK);

    expect(tracker.size()).toBeLessThanOrEqual(64);
  });
});
