/**
 * The bounded cache of a message's document media, in front of the one
 * request every fetched run used to pay again for the same answer.
 */

import { describe, expect, test } from "bun:test";
import { MediaCache } from "../src/telegram/media-cache";

describe("answering from the cache", () => {
  test("a second lookup within the TTL does not ask again", async () => {
    let calls = 0;
    const cache = new MediaCache<string>(async (id) => { calls += 1; return `media-${id}`; });

    expect(await cache.get(7)).toBe("media-7");
    expect(await cache.get(7)).toBe("media-7");
    expect(calls).toBe(1);
  });

  test("different messages are answered independently", async () => {
    const cache = new MediaCache<string>(async (id) => `media-${id}`);

    expect(await cache.get(1)).toBe("media-1");
    expect(await cache.get(2)).toBe("media-2");
  });

  test("an answer past its TTL is asked for again", async () => {
    let calls = 0;
    let now = 0;
    const cache = new MediaCache<string>(async (id) => { calls += 1; return `media-${id}-${calls}`; }, 1000);
    const originalNow = Date.now;
    Date.now = () => now;
    try {
      expect(await cache.get(1)).toBe("media-1-1");
      now = 999;
      expect(await cache.get(1)).toBe("media-1-1");
      now = 1000;
      expect(await cache.get(1)).toBe("media-1-2");
      expect(calls).toBe(2);
    } finally {
      Date.now = originalNow;
    }
  });
});

describe("concurrent lookups", () => {
  test("two lookups of the same message in flight share one request", async () => {
    let calls = 0;
    const gate = Promise.withResolvers<void>();
    const cache = new MediaCache<string>(async (id) => {
      calls += 1;
      await gate.promise;
      return `media-${id}`;
    });

    const first = cache.get(5);
    const second = cache.get(5);
    gate.resolve();

    expect(await first).toBe("media-5");
    expect(await second).toBe("media-5");
    expect(calls).toBe(1);
  });
});

describe("invalidation", () => {
  test("a dropped entry is asked for again on the next lookup", async () => {
    let calls = 0;
    const cache = new MediaCache<string>(async (id) => { calls += 1; return `media-${id}-${calls}`; });

    expect(await cache.get(3)).toBe("media-3-1");
    cache.invalidate(3);
    expect(await cache.get(3)).toBe("media-3-2");
    expect(calls).toBe(2);
  });

  test("invalidating an unknown message is harmless", () => {
    const cache = new MediaCache<string>(async (id) => `media-${id}`);
    expect(() => cache.invalidate(999)).not.toThrow();
  });
});

describe("the bound", () => {
  test("the oldest answer is dropped once the cache is full", async () => {
    let calls = 0;
    const cache = new MediaCache<string>(async (id) => { calls += 1; return `media-${id}`; }, 60_000, 2);

    await cache.get(1);
    await cache.get(2);
    await cache.get(3); // evicts message 1

    calls = 0;
    await cache.get(1);
    expect(calls).toBe(1); // asked again: it was evicted
    calls = 0;
    await cache.get(3);
    expect(calls).toBe(0); // still held
  });
});
