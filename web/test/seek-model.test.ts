/** Covers `seek-model`: the scrub bar's arithmetic, and where a skip lands. */

import { describe, expect, test } from "bun:test";
import { seekModel, skipTo } from "../public/lib/seek-model.js";

describe("a bar there is something to draw", () => {
  test("scales to the film's runtime, not to the encode's", () => {
    const bar = seekModel({ runtime: 1204, at: 359 });
    expect(bar.usable).toBe(true);
    expect(bar.max).toBe(1204);
    expect(bar.value).toBe(359);
    expect(bar.label).toBe("5:59 / 20:04");
  });

  test("paints what is played and what is held beyond it", () => {
    const bar = seekModel({ runtime: 100, at: 40, ahead: 25 });
    expect(bar.played).toBeCloseTo(0.4);
    expect(bar.buffered).toBeCloseTo(0.65);
  });

  test("an hour-long title keeps its hours", () => {
    expect(seekModel({ runtime: 7084, at: 3725 }).label).toBe("1:02:05 / 1:58:04");
  });
});

describe("nothing worth drawing a bar for", () => {
  test("a runtime nobody knows", () => {
    for (const runtime of [undefined, null, 0, -1, Number.NaN, "soon"]) {
      expect(seekModel({ runtime: runtime as never, at: 30 }).usable).toBe(false);
    }
  });

  test("and it reports nothing rather than a bar scaled to a guess", () => {
    const bar = seekModel({ runtime: null, at: 30 });
    expect(bar.max).toBe(0);
    expect(bar.label).toBe("");
    expect(bar.played).toBe(0);
  });
});

describe("positions that cannot be right", () => {
  test("a position past the end stops at the end", () => {
    // A conversion's clock can run past the film when the encode began late
    // and nobody has corrected the base yet.
    const bar = seekModel({ runtime: 600, at: 900 });
    expect(bar.value).toBe(600);
    expect(bar.played).toBe(1);
    expect(bar.label).toBe("10:00 / 10:00");
  });

  test("a buffer reaching past the end does too", () => {
    expect(seekModel({ runtime: 600, at: 590, ahead: 300 }).buffered).toBe(1);
  });

  test("a position before the start is the start", () => {
    for (const at of [undefined, null, -5, Number.NaN]) {
      expect(seekModel({ runtime: 600, at: at as never }).value).toBe(0);
    }
  });

  test("nought is a position, not an absence", () => {
    // `Number(null)` is 0 and so is the start of the film; telling them apart
    // is the whole reason this is not left to coercion.
    expect(seekModel({ runtime: 600, at: 0 }).label).toBe("0:00 / 10:00");
  });

  test("an absent buffer is not a negative one", () => {
    for (const ahead of [undefined, null, -10, Number.NaN]) {
      const bar = seekModel({ runtime: 600, at: 60, ahead: ahead as never });
      expect(bar.buffered).toBeCloseTo(0.1);
    }
  });
});

describe("what a drag is allowed to do", () => {
  test("a file the server can seek moves as the viewer drags", () => {
    expect(seekModel({ runtime: 600, at: 0 }).seeksWhileDragging).toBe(true);
    expect(seekModel({ runtime: 600, at: 0, converting: false }).seeksWhileDragging).toBe(true);
  });

  test("a conversion waits until the viewer lets go", () => {
    // Otherwise every pixel of the drag starts an encode and finishes none.
    expect(seekModel({ runtime: 600, at: 0, converting: true }).seeksWhileDragging).toBe(false);
  });
});

describe("where a skip lands", () => {
  test("ten seconds either way", () => {
    expect(skipTo(100, 10, 600)).toBe(110);
    expect(skipTo(100, -10, 600)).toBe(90);
  });

  test("back from the first seconds lands at the start, not before it", () => {
    expect(skipTo(4, -10, 600)).toBe(0);
  });

  test("forward near the end lands at the end", () => {
    // Past it would ask a conversion to encode from beyond the file.
    expect(skipTo(595, 10, 600)).toBe(600);
  });

  test("a runtime nobody knows still clamps at the start", () => {
    expect(skipTo(4, -10, null)).toBe(0);
    expect(skipTo(100, 10, null)).toBe(110);
  });

  test("nonsense in is the position back out", () => {
    expect(skipTo(Number.NaN, 10, 600)).toBe(10);
    expect(skipTo(100, Number.NaN, 600)).toBe(100);
  });
});
