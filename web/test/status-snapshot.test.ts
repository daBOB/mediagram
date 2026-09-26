import { describe, expect, test } from "bun:test";
import { buildSnapshot, type LiveFacts } from "../src/status/snapshot";
import type { StartupFacts } from "../src/status/facts";

const facts: StartupFacts = {
  catalog: {
    origin: "package",
    publishedAt: 1_758_000_000_000,
    refresh: "kept",
    reason: "could not read the pointer: fetch failed",
    schema: 6,
    sets: 340,
    posters: 62,
  },
  encoder: { name: "h264_vaapi", kind: "vaapi", device: "/dev/dri/renderD128" },
  transcodeDir: "/var/tmp/mediagram-transcode",
  cache: { dir: "/var/cache/mediagram", budget: 20 * 1024 ** 3, readahead: 4 },
  state: { remembered: true, path: "/var/lib/mediagram/state.db" },
  startedAt: 1_000_000,
  runtime: { bun: "1.4.2" },
};

const live: LiveFacts = {
  cacheHeldBytes: 7 * 1024 ** 3,
  cacheHits: 900,
  cacheMisses: 100,
  cacheEvicted: 12,
  fetchedBytes: 5_000_000,
  transcodes: {
    running: 1,
    capacity: 4,
    sessions: [{ setId: "abc", seekSeconds: 120, maxrateBits: 8_000_000, audioTrack: 1, watchers: 2 }],
  },
  transcodeBytes: 3 * 1024 ** 3,
  telegramConnected: true,
  failedReads: 2,
  host: {
    rssBytes: 180 * 1024 ** 2,
    heapBytes: 90 * 1024 ** 2,
    loopLagMs: { p50: 2, p99: 18, max: 40 },
    disks: [{ dirs: ["/var/cache/mediagram"], freeBytes: 100 * 1024 ** 3, totalBytes: 500 * 1024 ** 3 }],
  },
  now: 1_000_000 + 3_600_000,
};

describe("the status snapshot", () => {
  test("carries the refresh verdict, which is the fact a log would have buried", () => {
    const snapshot = buildSnapshot(facts, live);
    // A player quietly serving a fortnight-old catalogue looks exactly like
    // one serving a current one. This is the difference.
    expect(snapshot.catalog.refresh).toBe("kept");
    expect(snapshot.catalog.reason).toContain("could not read the pointer");
  });

  test("reports uptime in seconds from when the process finished starting", () => {
    expect(buildSnapshot(facts, live).uptimeSeconds).toBe(3600);
  });

  test("never reports a negative uptime, whatever the clock did", () => {
    const backwards = buildSnapshot(facts, { ...live, now: 0 });
    expect(backwards.uptimeSeconds).toBe(0);
  });

  test("states the hit rate rather than leaving it to be divided", () => {
    expect(buildSnapshot(facts, live).cache?.hitRate).toBeCloseTo(0.9);
  });

  test("distinguishes no reads yet from a hit rate of zero", () => {
    const fresh = buildSnapshot(facts, { ...live, cacheHits: 0, cacheMisses: 0 });
    expect(fresh.cache?.hitRate).toBe(null);
    const cold = buildSnapshot(facts, { ...live, cacheHits: 0, cacheMisses: 50 });
    expect(cold.cache?.hitRate).toBe(0);
  });

  test("says the cache is off rather than reporting an empty one", () => {
    const off = buildSnapshot({ ...facts, cache: null }, live);
    expect(off.cache).toBe(null);
  });

  test("names no session id, which is a directory this server writes to", () => {
    const body = JSON.stringify(buildSnapshot(facts, live));
    expect(body).not.toContain("directory");
    expect(body).not.toContain('"id"');
  });

  test("carries nothing that says where the bytes live", () => {
    const body = JSON.stringify(buildSnapshot(facts, live));
    for (const secret of ["chatId", "chat_id", "messageId", "message_id", "docId", "doc_id"]) {
      expect(body).not.toContain(secret);
    }
  });
});

describe("the readings added after the first pass", () => {
  test("reports what the conversions are holding, which has no budget to evict against", () => {
    const snapshot = buildSnapshot(facts, live);
    expect(snapshot.transcodes.heldBytes).toBe(3 * 1024 ** 3);
    expect(snapshot.transcodes.dir).toBe("/var/tmp/mediagram-transcode");
  });

  test("reports failed reads, which a viewer feels and cannot see", () => {
    expect(buildSnapshot(facts, live).telegram.failedReads).toBe(2);
  });

  test("reports resident memory, for a player left running for a week", () => {
    expect(buildSnapshot(facts, live).host.rssBytes).toBe(180 * 1024 ** 2);
  });

  test("carries the host group's heap, loop lag and disk figures, plus the runtime version", () => {
    const snapshot = buildSnapshot(facts, live);
    expect(snapshot.host.heapBytes).toBe(90 * 1024 ** 2);
    expect(snapshot.host.loopLagMs).toEqual({ p50: 2, p99: 18, max: 40 });
    expect(snapshot.host.disks).toHaveLength(1);
    expect(snapshot.host.bun).toBe("1.4.2");
  });

  test("says the event loop cannot be measured rather than guessing", () => {
    const snapshot = buildSnapshot(facts, { ...live, host: { ...live.host, loopLagMs: null } });
    expect(snapshot.host.loopLagMs).toBe(null);
  });

  test("hands over fetched bytes rather than a rate it cannot compute", () => {
    // The server does not know how often it is being asked, so the rate is
    // the caller's to derive from two readings.
    expect(buildSnapshot(facts, live).fetchedBytes).toBe(5_000_000);
  });
});
