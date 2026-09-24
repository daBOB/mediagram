/** Covers `subtitle-style`: legible subtitles, and ones that are on time. */

import { describe, expect, test } from "bun:test";
import { cueStyle, placeCues, shiftedTimes } from "../public/lib/playback/subtitle-style.js";

/** A cue, as far as anything here touches one. */
const cue = (start: number, end: number) => ({
  startTime: start,
  endTime: end,
  line: "auto" as number | "auto",
  snapToLines: true,
  lineAlign: "start",
});

/** Stands in for a `TextTrack`, which cannot be constructed outside a browser. */
const track = (...cues: ReturnType<typeof cue>[]) => ({ cues });

describe("the rule a size and a backing produce", () => {
  test("states the size as a percentage of the browser's own", () => {
    expect(cueStyle({ size: 140 })).toContain("font-size: 140%");
  });

  test("always states a background, because the default is a black box", () => {
    // Leaving it out does not remove the box; it keeps it.
    expect(cueStyle({ backing: "none" })).toContain("background: transparent");
    expect(cueStyle({ backing: "box" })).toContain("background: rgba(0, 0, 0, 0.75)");
  });

  test("a shadow is two shadows", () => {
    // A soft drop for depth and a tight one to hold the letterform together
    // against a bright shot, which a single blur does not do.
    const rule = cueStyle({ backing: "shadow" });
    expect(rule.match(/rgba\(0, 0, 0, 0\.9/g)).toHaveLength(2);
  });

  test("and a box has no shadow competing with it", () => {
    expect(cueStyle({ backing: "box" })).toContain("text-shadow: none");
  });

  test("a size nobody could read is refused", () => {
    expect(cueStyle({ size: 5 })).toContain("font-size: 50%");
    expect(cueStyle({ size: 9000 })).toContain("font-size: 200%");
  });

  test("and a size that is not a number is the browser's own", () => {
    for (const size of [undefined, null, Number.NaN, "big"]) {
      expect(cueStyle({ size: size as never })).toContain("font-size: 100%");
    }
  });
});

describe("moving a cue along the clock", () => {
  test("applies the offset to the time it was parsed with", () => {
    expect(shiftedTimes({ start: 10, end: 12 }, -0.4)).toEqual({ start: 9.6, end: 11.6 });
    expect(shiftedTimes({ start: 10, end: 12 }, 1.5)).toEqual({ start: 11.5, end: 13.5 });
  });

  test("nothing moves by nothing", () => {
    expect(shiftedTimes({ start: 10, end: 12 }, 0)).toEqual({ start: 10, end: 12 });
  });

  test("an offset that is not a number moves nothing", () => {
    for (const offset of [undefined, null, Number.NaN, "early"]) {
      expect(shiftedTimes({ start: 10, end: 12 }, offset as never)).toEqual({ start: 10, end: 12 });
    }
  });

  test("a cue cannot start before the film does", () => {
    expect(shiftedTimes({ start: 2, end: 4 }, -5)).toEqual({ start: 0, end: 0 });
  });

  test("and never ends before it starts", () => {
    const { start, end } = shiftedTimes({ start: 2, end: 2.5 }, -100);
    expect(end).toBeGreaterThanOrEqual(start);
  });
});

describe("applying it to a track", () => {
  test("moves every cue, and says how many", () => {
    const one = cue(10, 12);
    const two = cue(30, 33);
    expect(placeCues(track(one, two), { offset: -0.4 })).toBe(2);
    expect(one.startTime).toBeCloseTo(9.6);
    expect(two.startTime).toBeCloseTo(29.6);
  });

  test("twice with the same offset is not twice", () => {
    // The whole reason the parsed times are kept: nudging the current ones by
    // a delta drifts, and there is no way back to where a cue started.
    const only = cue(10, 12);
    const held = track(only);
    placeCues(held, { offset: -0.4 });
    expect(placeCues(held, { offset: -0.4 })).toBe(0);
    expect(only.startTime).toBeCloseTo(9.6);
  });

  test("and changing the offset measures from the original, not the last one", () => {
    const only = cue(10, 12);
    const held = track(only);
    placeCues(held, { offset: -0.4 });
    placeCues(held, { offset: 2 });
    expect(only.startTime).toBeCloseTo(12);
  });

  test("going back to nought puts a cue exactly where it was parsed", () => {
    const only = cue(10, 12);
    const held = track(only);
    placeCues(held, { offset: -3.5 });
    placeCues(held, { offset: 0 });
    expect(only.startTime).toBe(10);
    expect(only.endTime).toBe(12);
  });

  test("leaves the browser's own placement alone", () => {
    // Every way of moving a cue up the picture either stops Chromium wrapping
    // the text or anchors the wrong edge. `subtitle-style.js` records both.
    const only = cue(10, 12);
    placeCues(track(only), { offset: -1 });
    expect(only.line).toBe("auto");
    expect(only.snapToLines).toBe(true);
    expect(only.lineAlign).toBe("start");
  });

  test("a track with no cues yet is not an error", () => {
    // The ordinary case: cues do not exist until the file has been fetched,
    // which only happens once a track stops being disabled.
    expect(placeCues(track())).toBe(0);
    expect(placeCues(null as never)).toBe(0);
    expect(placeCues({ cues: null } as never)).toBe(0);
  });
});
