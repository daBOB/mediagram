import { describe, expect, test } from "bun:test";
import { hostRows, linkRows } from "../public/lib/status/status-link-lines.js";

describe("the Telegram link rows", () => {
  test("says nothing has been requested yet", () => {
    expect(linkRows({ dcs: [], flood: { count: 0, totalSeconds: 0 }, reconnects: 0 })).toEqual([["DC", "nothing requested yet"]]);
  });

  test("names each DC by requests, bytes and latency", () => {
    const rows = linkRows({
      dcs: [{ dc: 4, requests: 1284, errors: 0, bytes: 3.1 * 1024 ** 3, p50Ms: 180, p95Ms: 640 }],
      flood: { count: 0, totalSeconds: 0 },
      reconnects: 0,
    });
    expect(rows[0]).toEqual(["DC 4", "1,284 requests · 3.1 GB · 180 ms typical, 640 ms slow"]);
  });

  test("appends the failure count only when there were failures", () => {
    const failed = linkRows({
      dcs: [{ dc: 2, requests: 10, errors: 2, bytes: 0, p50Ms: null, p95Ms: null }],
      flood: { count: 0, totalSeconds: 0 },
      reconnects: 0,
    });
    expect(failed[0]?.[1]).toContain(", 2 failed");
    const clean = linkRows({
      dcs: [{ dc: 2, requests: 10, errors: 0, bytes: 0, p50Ms: null, p95Ms: null }],
      flood: { count: 0, totalSeconds: 0 },
      reconnects: 0,
    });
    expect(clean[0]?.[1]).not.toContain("failed");
  });

  test("sums flood waits, or says none", () => {
    const none = linkRows({ dcs: [{ dc: 1, requests: 1, errors: 0, bytes: 0, p50Ms: null, p95Ms: null }], flood: { count: 0, totalSeconds: 0 }, reconnects: 0 });
    expect(none.find((row) => row[0] === "Flood waits")?.[1]).toBe("none");
    const some = linkRows({ dcs: [{ dc: 1, requests: 1, errors: 0, bytes: 0, p50Ms: null, p95Ms: null }], flood: { count: 3, totalSeconds: 47 }, reconnects: 0 });
    expect(some.find((row) => row[0] === "Flood waits")?.[1]).toBe("3, 47 s waited in all");
  });

  test("counts reconnects, or says none", () => {
    const rows = linkRows({ dcs: [{ dc: 1, requests: 1, errors: 0, bytes: 0, p50Ms: null, p95Ms: null }], flood: { count: 0, totalSeconds: 0 }, reconnects: 2 });
    expect(rows.find((row) => row[0] === "Reconnects")?.[1]).toBe("2");
  });
});

const HOST = {
  rssBytes: 412 * 1024 ** 2,
  heapBytes: 96 * 1024 ** 2,
  loopLagMs: { p50: 2, p99: 18, max: 38 },
  disks: [{ dirs: ["/var/cache/mediagram", "/var/tmp/mediagram-transcode"], freeBytes: 118 * 1024 ** 3, totalBytes: 460 * 1024 ** 3 }],
  bun: "1.4.2",
};

describe("the host rows", () => {
  test("says resident and heap memory together", () => {
    const rows = hostRows(HOST, "/var/cache/mediagram", "/var/tmp/mediagram-transcode");
    expect(rows.find((row) => row[0] === "Memory")?.[1]).toBe("412 MB resident, 96 MB heap");
  });

  test("says the event loop's typical and worst readings from the last window", () => {
    const rows = hostRows(HOST, "/var/cache/mediagram", "/var/tmp/mediagram-transcode");
    expect(rows.find((row) => row[0] === "Event loop")?.[1]).toBe("2 ms typical, 38 ms worst");
  });

  test("leaves out the event loop row before a window has completed", () => {
    const rows = hostRows({ ...HOST, loopLagMs: null }, "/var/cache/mediagram", "/var/tmp/mediagram-transcode");
    expect(rows.find((row) => row[0] === "Event loop")?.[1]).toBe(null);
  });

  test("names the cache and conversions directories sharing a device", () => {
    const rows = hostRows(HOST, "/var/cache/mediagram", "/var/tmp/mediagram-transcode");
    expect(rows.find((row) => row[0] === "Disk free")?.[1]).toBe("118 GB of 460 GB, cache and conversions");
  });

  test("names each device separately when they do not share one", () => {
    const rows = hostRows(
      {
        ...HOST,
        disks: [
          { dirs: ["/var/cache/mediagram"], freeBytes: 50 * 1024 ** 3, totalBytes: 100 * 1024 ** 3 },
          { dirs: ["/var/tmp/mediagram-transcode"], freeBytes: 200 * 1024 ** 3, totalBytes: 500 * 1024 ** 3 },
        ],
      },
      "/var/cache/mediagram",
      "/var/tmp/mediagram-transcode",
    );
    const values = rows.filter((row) => row[0] === "Disk free").map((row) => row[1]);
    expect(values).toEqual(["50 GB of 100 GB, cache", "200 GB of 500 GB, conversions"]);
  });

  test("says the runtime version", () => {
    const rows = hostRows(HOST, "/var/cache/mediagram", "/var/tmp/mediagram-transcode");
    expect(rows.find((row) => row[0] === "Runtime")?.[1]).toBe("Bun 1.4.2");
  });
});
