import { describe, expect, test } from "bun:test";
import {
  cacheReadsLine,
  ofBudget,
  pollStatus,
  refreshLine,
  throughput,
  transcodeRows,
  uptime,
} from "../public/lib/status-lines.js";

describe("what the cache is holding", () => {
  test("is said against the budget it was given", () => {
    expect(ofBudget(7 * 1024 ** 3, 20 * 1024 ** 3)).toBe("7.0 GB of 20 GB (35%)");
  });

  test("admits to being unmeasured rather than reporting zero", () => {
    // A failed scan and an empty cache are different, and only one of them
    // means something is wrong.
    expect(ofBudget(null, 20 * 1024 ** 3)).toBe("unmeasured of 20 GB");
  });

  test("says just the amount when there is no budget to measure against", () => {
    expect(ofBudget(512, 0)).toBe("512 B");
  });
});

describe("uptime", () => {
  test("is said the way a person says it", () => {
    expect(uptime(90)).toBe("1m");
    expect(uptime(4_320)).toBe("1h 12m");
    expect(uptime(180_000)).toBe("2d 2h");
  });

  test("has no answer for a figure that is not one", () => {
    expect(uptime(Number.NaN)).toBe(null);
    expect(uptime(-1)).toBe(null);
  });
});

describe("the refresh line", () => {
  const now = Date.parse("2026-09-19T00:00:00Z");
  const threeDaysAgo = now - 3 * 86_400_000;

  test("warns, in words, when a refresh was refused", () => {
    const line = refreshLine(
      {
        origin: "package",
        publishedAt: threeDaysAgo,
        refresh: "kept",
        reason: "could not read the pointer: fetch failed",
      },
      now,
    );
    // The whole reason this panel exists: a stale catalogue is invisible
    // otherwise.
    expect(line).toContain("refresh refused");
    expect(line).toContain("fetch failed");
    expect(line).toContain("still serving the one published 3 days ago");
  });

  test("still warns when the refusal came with no reason", () => {
    const line = refreshLine(
      { origin: "package", publishedAt: threeDaysAgo, refresh: "kept", reason: null },
      now,
    );
    expect(line).toContain("refresh refused");
    expect(line).not.toContain("null");
  });

  test("says so plainly when the refresh worked", () => {
    expect(
      refreshLine({ origin: "package", publishedAt: threeDaysAgo, refresh: "updated" }, now),
    ).toContain("refreshed just now");
    expect(
      refreshLine({ origin: "package", publishedAt: threeDaysAgo, refresh: "unchanged" }, now),
    ).toContain("already current");
  });

  test("has nothing to refresh when the index is on this machine", () => {
    expect(refreshLine({ origin: "local", publishedAt: null, refresh: null }, now)).toBe(
      "read from this machine",
    );
  });
});

describe("the cache reads line", () => {
  test("separates nothing read yet from nothing found", () => {
    expect(cacheReadsLine({ hitRate: null, hits: 0, misses: 0 })).toBe("nothing read yet");
    expect(cacheReadsLine({ hitRate: 0, hits: 0, misses: 40 })).toContain("0% from disk");
  });

  test("gives the rate and the figures behind it", () => {
    expect(cacheReadsLine({ hitRate: 0.9, hits: 900, misses: 100 })).toBe(
      "90% from disk (900 hits, 100 misses)",
    );
  });
});

describe("the conversion rows", () => {
  test("say the capacity even when nothing is running", () => {
    expect(transcodeRows({ running: 0, capacity: 4, sessions: [] })).toContainEqual([
      "Running",
      "none, of 4 allowed",
    ]);
  });

  test("describe each conversion by rate, position and audience", () => {
    const rows = transcodeRows({
      running: 1,
      capacity: 4,
      sessions: [{ maxrateBits: 8_000_000, seekSeconds: 1_800, watchers: 2 }],
    });
    expect(rows[0]).toEqual(["Running", "1 of 4"]);
    expect(rows[1]?.[1]).toBe("8.0 Mbps from 30m, 2 watching");
  });
});

describe("the poller", () => {
  const ok = (body: unknown) =>
    ({ ok: true, status: 200, json: async () => body }) as unknown as Response;

  test("takes a first reading immediately rather than after an interval", async () => {
    const seen: unknown[] = [];
    const stop = pollStatus({
      fetchStatus: async () => ok({ up: 1 }),
      onSnapshot: (snapshot) => seen.push(snapshot),
      onError: () => {},
      schedule: () => 0,
      cancel: () => {},
    });
    await Promise.resolve();
    await Promise.resolve();
    stop();
    expect(seen).toEqual([{ up: 1 }]);
  });

  test("cancels the interval when stopped", () => {
    let cancelled = 0;
    const stop = pollStatus({
      fetchStatus: async () => ok({}),
      onSnapshot: () => {},
      onError: () => {},
      schedule: () => 77,
      cancel: (handle) => {
        expect(handle).toBe(77);
        cancelled += 1;
      },
    });
    stop();
    expect(cancelled).toBe(1);
  });

  test("drops a reply that lands after it was stopped", async () => {
    let release: (() => void) | null = null;
    const inFlight = new Promise<void>((resolve) => {
      release = resolve;
    });
    let rendered = 0;

    const stop = pollStatus({
      fetchStatus: async () => {
        await inFlight;
        return ok({});
      },
      onSnapshot: () => {
        rendered += 1;
      },
      onError: () => {
        rendered += 1;
      },
      schedule: () => 0,
      cancel: () => {},
    });

    stop();
    release?.();
    await inFlight;
    await Promise.resolve();
    await Promise.resolve();
    // Painting into a view the viewer has already left is the bug this guard
    // exists for.
    expect(rendered).toBe(0);
  });

  test("reports a refusal as an error rather than rendering nothing", async () => {
    const errors: string[] = [];
    const stop = pollStatus({
      fetchStatus: async () => ({ ok: false, status: 404 }) as Response,
      onSnapshot: () => {},
      onError: (error) => errors.push(error.message),
      schedule: () => 0,
      cancel: () => {},
    });
    await Promise.resolve();
    await Promise.resolve();
    stop();
    // What a remote viewer reaching `#/status` by hand actually gets.
    expect(errors[0]).toContain("404");
  });
});

describe("upstream throughput", () => {
  const at = (bytes: number, seconds: number) => ({ bytes, at: seconds * 1000 });

  test("is the difference between two readings over the time between them", () => {
    // 10 MB in 8s = 10 Mbps.
    expect(throughput(at(0, 0), at(10_000_000, 8))).toBe("10 Mbps");
  });

  test("keeps a decimal while the first one still decides anything", () => {
    expect(throughput(at(0, 0), at(1_000_000, 1))).toBe("8.0 Mbps");
  });

  test("says idle rather than 0.0 Mbps when nothing moved", () => {
    expect(throughput(at(500, 0), at(500, 2))).toBe("idle");
  });

  test("has no answer until there are two readings", () => {
    expect(throughput(null, at(100, 1))).toBe(null);
  });

  test("has no answer when the counter went backwards, which is a restart", () => {
    expect(throughput(at(900, 0), at(100, 2))).toBe(null);
  });

  test("has no answer when no time passed, rather than dividing by zero", () => {
    expect(throughput(at(0, 5), at(100, 5))).toBe(null);
  });
});

describe("the conversion rows, with disk", () => {
  test("say what is on disk even when nothing is running", () => {
    // Segments outlive the conversion that wrote them until the reaper runs.
    const rows = transcodeRows({
      running: 0,
      capacity: 4,
      sessions: [],
      heldBytes: 7 * 1024 ** 3,
      dir: "/var/tmp/t",
    });
    expect(rows).toContainEqual(["On disk", "7.0 GB"]);
    expect(rows).toContainEqual(["Directory", "/var/tmp/t"]);
  });

  test("say nothing about disk when it could not be measured", () => {
    const rows = transcodeRows({ running: 0, capacity: 4, sessions: [], heldBytes: null });
    expect(rows).toContainEqual(["On disk", null]);
  });
});
