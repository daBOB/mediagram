import { describe, expect, test } from "bun:test";
import { PlaybackReports, validateReport, type PlaybackReport } from "../src/status/playback-reports";

const VALID: PlaybackReport = {
  viewer: "11111111-1111-4111-8111-111111111111",
  setId: "01SET",
  title: "A Film",
  mode: "direct",
  videoCodec: "h264",
  audioCodec: "aac",
  bitrateBits: 8_200_000,
  ahead: 72,
  health: "ok",
  fillRate: 1.1,
  dropped: 0,
  frames: 41_200,
  paused: false,
  held: false,
};

describe("validating a playback report", () => {
  test("accepts a well-formed report", () => {
    expect(validateReport(VALID)).toMatchObject({ setId: "01SET", mode: "direct", health: "ok" });
  });

  test("rejects a body that is not an object", () => {
    expect(validateReport("nope")).toBe(null);
    expect(validateReport(null)).toBe(null);
    expect(validateReport(undefined)).toBe(null);
  });

  test("rejects a report with no viewer to key it by", () => {
    const { viewer: _viewer, ...rest } = VALID;
    expect(validateReport(rest)).toBe(null);
  });

  test("rejects a report with no set id", () => {
    const { setId: _setId, ...rest } = VALID;
    expect(validateReport(rest)).toBe(null);
  });

  test("rejects an unknown mode rather than guessing what it meant", () => {
    expect(validateReport({ ...VALID, mode: "streaming" })).toBe(null);
  });

  test("rejects an unknown health state", () => {
    expect(validateReport({ ...VALID, health: "great" })).toBe(null);
  });

  test("clamps an oversized title rather than rejecting the report", () => {
    const huge = "x".repeat(500);
    const report = validateReport({ ...VALID, title: huge });
    expect(report?.title).toHaveLength(200);
  });

  test("clamps an oversized codec name", () => {
    const report = validateReport({ ...VALID, videoCodec: "x".repeat(50) });
    expect(report?.videoCodec).toHaveLength(16);
  });

  test("treats a non-finite number as unmeasured rather than failing the report", () => {
    const report = validateReport({ ...VALID, bitrateBits: Number.NaN, ahead: "a lot" });
    expect(report?.bitrateBits).toBe(null);
    expect(report?.ahead).toBe(null);
  });

  test("treats a negative number as unmeasured", () => {
    expect(validateReport({ ...VALID, dropped: -5 })?.dropped).toBe(null);
  });

  test("defaults a missing title to empty rather than rejecting", () => {
    const { title: _title, ...rest } = VALID;
    expect(validateReport(rest)?.title).toBe("");
  });

  test("coerces paused and held strictly, so a truthy string cannot pass as boolean", () => {
    const report = validateReport({ ...VALID, paused: "yes", held: 1 });
    expect(report?.paused).toBe(false);
    expect(report?.held).toBe(false);
  });
});

describe("keeping the most recent reading per viewer", () => {
  test("replaces a viewer's own reading rather than accumulating one each", () => {
    const store = new PlaybackReports(() => 0);
    store.put({ ...VALID, ahead: 10 }, "192.168.1.10");
    store.put({ ...VALID, ahead: 90 }, "192.168.1.10");
    expect(store.list()).toHaveLength(1);
    expect(store.list()[0]?.ahead).toBe(90);
  });

  test("expires a reading once its TTL has passed", () => {
    let clock = 0;
    const store = new PlaybackReports(() => clock);
    store.put(VALID, "192.168.1.10");
    clock = 14_000;
    expect(store.list()).toHaveLength(1);
    clock = 15_001;
    expect(store.list()).toHaveLength(0);
  });

  test("evicts the stalest viewer once the cap is reached, admitting the new one", () => {
    let clock = 0;
    const store = new PlaybackReports(() => clock);
    for (let i = 0; i < 16; i++) {
      clock = i;
      store.put({ ...VALID, viewer: `viewer-${i}`, setId: `set-${String(i).padStart(2, "0")}` }, "192.168.1.10");
    }
    clock = 16;
    store.put({ ...VALID, viewer: "viewer-new", setId: "set-99" }, "192.168.1.10");

    const setIds = store.list().map((row) => row.setId);
    expect(setIds).toHaveLength(16);
    // viewer-0, the stalest, was evicted; the new one is present.
    expect(setIds).not.toContain("set-00");
    expect(setIds).toContain("set-99");
  });

  test("does not evict anyone when the same viewer reports again at the cap", () => {
    let clock = 0;
    const store = new PlaybackReports(() => clock);
    for (let i = 0; i < 16; i++) {
      clock = i;
      store.put({ ...VALID, viewer: `viewer-${i}` }, "192.168.1.10");
    }
    clock = 100;
    store.put({ ...VALID, viewer: "viewer-0", ahead: 5 }, "192.168.1.10");
    expect(store.list()).toHaveLength(16);
  });

  test("stamps who and how long ago, but never the viewer id", () => {
    let clock = 0;
    const store = new PlaybackReports(() => clock);
    store.put(VALID, "192.168.1.23");
    clock = 5000;
    const [row] = store.list();
    expect(row?.from).toBe("192.168.1.23");
    expect(row?.ageSeconds).toBe(5);
    expect(row).not.toHaveProperty("viewer");
  });

  test("sorts by set id and then by who is watching, for a stable listing", () => {
    const store = new PlaybackReports(() => 0);
    store.put({ ...VALID, viewer: "a", setId: "01B" }, "192.168.1.30");
    store.put({ ...VALID, viewer: "b", setId: "01A" }, "192.168.1.20");
    store.put({ ...VALID, viewer: "c", setId: "01A" }, "192.168.1.10");
    expect(store.list().map((row) => [row.setId, row.from])).toEqual([
      ["01A", "192.168.1.10"],
      ["01A", "192.168.1.20"],
      ["01B", "192.168.1.30"],
    ]);
  });
});
