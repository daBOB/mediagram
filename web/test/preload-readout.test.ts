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
