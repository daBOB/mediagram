/** Covers `preload-readout`: what the player says while it is filling up. */

import { describe, expect, test } from "bun:test";
import { bufferedAhead, preloadReadout } from "../public/lib/preload-readout.js";

/** A stand-in for `TimeRanges`, which cannot be constructed. */
const ranges = (...spans: [number, number][]) => ({
  length: spans.length,
  start: (i: number) => spans[i]![0],
  end: (i: number) => spans[i]![1],
});

describe("how far ahead", () => {
  test("measures from the playhead to the end of its own range", () => {
    expect(bufferedAhead(ranges([0, 30]), 0)).toBe(30);
    expect(bufferedAhead(ranges([0, 30]), 12)).toBe(18);
  });

  test("after a seek, takes the range the viewer is in", () => {
    // The stretch left behind at the start is not buffer ahead of anybody.
    expect(bufferedAhead(ranges([0, 30], [600, 640]), 610)).toBe(30);
  });

  test("a playhead in no range at all has nothing ahead of it", () => {
    expect(bufferedAhead(ranges([0, 30]), 900)).toBe(0);
    expect(bufferedAhead(ranges(), 0)).toBe(0);
    expect(bufferedAhead(null as never, 0)).toBe(0);
  });
});

describe("the readout", () => {
  test("says what the browser thinks, and how much it holds", () => {
    expect(preloadReadout(4, 24)).toBe("ready · 0:24 ahead");
    expect(preloadReadout(2, 6)).toBe("buffering · 0:06 ahead");
  });

  test("before there is anything, says only what it is doing", () => {
    expect(preloadReadout(0, 0)).toBe("opening");
    expect(preloadReadout(1, 0)).toBe("buffering");
  });

  test("a long buffer reads as a clock, like every other figure here", () => {
    expect(preloadReadout(4, 3661)).toBe("ready · 1:01:01 ahead");
  });

  test("nonsense from a media element does not produce nonsense on screen", () => {
    expect(preloadReadout(Number.NaN, Number.NaN)).toBe("opening");
    expect(preloadReadout(4, -5)).toBe("ready");
  });
});

describe("the fill rate", () => {
  test("is shown when the buffer is filling faster than it drains", () => {
    expect(preloadReadout(2, 12, 1.4)).toBe("buffering · 0:12 ahead, filling 1.4×");
  });

  test("is shown when it is falling behind, which is the whole point", () => {
    expect(preloadReadout(2, 4, 0.5)).toBe("buffering · 0:04 ahead, filling 0.5×");
  });

  test("stays quiet near 1.0, where it would only flicker", () => {
    expect(preloadReadout(2, 12, 1.0)).toBe("buffering · 0:12 ahead");
    expect(preloadReadout(2, 12, 0.92)).toBe("buffering · 0:12 ahead");
    expect(preloadReadout(2, 12, 1.1)).toBe("buffering · 0:12 ahead");
  });

  test("is absent when nothing measured one, which is most of the time", () => {
    // The signature grew; every call that predates it must read as it did.
    expect(preloadReadout(2, 12)).toBe("buffering · 0:12 ahead");
    expect(preloadReadout(2, 12, null)).toBe("buffering · 0:12 ahead");
    expect(preloadReadout(4, 0)).toBe("ready");
  });
});

describe("dropped frames", () => {
  test("are reported once there are some", () => {
    expect(preloadReadout(4, 30, null, 12)).toBe("ready · 0:30 ahead, 12 dropped");
  });

  test("are not reported when there are none, which is the ordinary case", () => {
    expect(preloadReadout(4, 30, null, 0)).toBe("ready · 0:30 ahead");
    expect(preloadReadout(4, 30)).toBe("ready · 0:30 ahead");
  });

  test("sit after the rate when both have something to say", () => {
    expect(preloadReadout(2, 4, 0.5, 9)).toBe("buffering · 0:04 ahead, filling 0.5×, 9 dropped");
  });
});
