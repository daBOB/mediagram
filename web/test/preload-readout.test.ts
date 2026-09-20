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

describe("what the readout calls the state", () => {
  test("says buffering only when the element is actually waiting on data", () => {
    expect(preloadReadout({ readyState: 3, ahead: 12, starved: true })).toBe(
      "buffering · 0:12 ahead",
    );
  });

  test("does not call a capped buffer buffering, which was the bug", () => {
    // `readyState` 3 is HAVE_FUTURE_DATA: "I can play forward". hls.js caps
    // its buffer on purpose, so every transcoded title sits here for its whole
    // running time. Reading it as buffering described a film playing from
    // local disk, fully encoded, with a minute in hand, as though it were
    // stuck on the network.
    expect(preloadReadout({ readyState: 3, ahead: 61 })).toBe("ready · 1:01 ahead");
    expect(preloadReadout({ readyState: 3, ahead: 61, starved: false })).toBe(
      "ready · 1:01 ahead",
    );
  });

  test("says opening only before there is a frame to show", () => {
    expect(preloadReadout({ readyState: 0, ahead: 0 })).toBe("opening");
    // Starved before the first frame is still opening: nothing has begun, so
    // there is nothing to have stalled.
    expect(preloadReadout({ readyState: 0, ahead: 0, starved: true })).toBe("opening");
  });

  test("says ready once it can play, whatever readiness level that is", () => {
    expect(preloadReadout({ readyState: 4, ahead: 30 })).toBe("ready · 0:30 ahead");
    expect(preloadReadout({ readyState: 1, ahead: 4 })).toBe("ready · 0:04 ahead");
  });

  test("still reports how far ahead it is while starved", () => {
    // A stall with a minute buffered somewhere is a different problem from a
    // stall with nothing, and the readout must not hide which one it is.
    expect(preloadReadout({ readyState: 2, ahead: 0, starved: true })).toBe("buffering");
  });
});

describe("the fill rate", () => {
  test("is shown when the buffer is filling faster than it drains", () => {
    expect(preloadReadout({ readyState: 2, ahead: 12, fillRate: 1.4 })).toBe(
      "ready · 0:12 ahead, filling 1.4×",
    );
  });

  test("is shown when it is falling behind, which is the whole point", () => {
    expect(preloadReadout({ readyState: 2, ahead: 4, starved: true, fillRate: 0.5 })).toBe(
      "buffering · 0:04 ahead, filling 0.5×",
    );
  });

  test("stays quiet near 1.0, where it would only flicker", () => {
    for (const rate of [1.0, 0.92, 1.1]) {
      expect(preloadReadout({ readyState: 2, ahead: 12, fillRate: rate })).toBe(
        "ready · 0:12 ahead",
      );
    }
  });

  test("is absent when nothing measured one, which is most of the time", () => {
    expect(preloadReadout({ readyState: 2, ahead: 12 })).toBe("ready · 0:12 ahead");
    expect(preloadReadout({ readyState: 2, ahead: 12, fillRate: null })).toBe(
      "ready · 0:12 ahead",
    );
  });
});

describe("dropped frames", () => {
  test("are reported once there are some", () => {
    expect(preloadReadout({ readyState: 4, ahead: 30, dropped: 12 })).toBe(
      "ready · 0:30 ahead, 12 dropped",
    );
  });

  test("are not reported when there are none, which is the ordinary case", () => {
    expect(preloadReadout({ readyState: 4, ahead: 30, dropped: 0 })).toBe("ready · 0:30 ahead");
    expect(preloadReadout({ readyState: 4, ahead: 30 })).toBe("ready · 0:30 ahead");
  });

  test("sit after the rate when both have something to say", () => {
    expect(
      preloadReadout({ readyState: 2, ahead: 4, starved: true, fillRate: 0.5, dropped: 9 }),
    ).toBe("buffering · 0:04 ahead, filling 0.5×, 9 dropped");
  });
});

describe("a call with nothing in it", () => {
  test("is opening rather than a crash", () => {
    expect(preloadReadout()).toBe("opening");
    expect(preloadReadout({})).toBe("opening");
  });
});
