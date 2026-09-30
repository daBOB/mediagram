/**
 * Covers `transport`: what the bar's controls say, before any of them move.
 *
 * The subtitle picker moved to `subtitle-picker.js` (tests: `subtitle-picker.test.ts`);
 * this file's own subtitle coverage moved with it.
 */

import { describe, expect, test } from "bun:test";
import { isSilent, playLabel, speedLabel } from "../public/lib/playback/transport.js";

describe("the speed on the menu", () => {
  test("drops the noise after the point", () => {
    expect(speedLabel(1)).toBe("1×");
    expect(speedLabel(1.5)).toBe("1.5×");
    expect(speedLabel(0.75)).toBe("0.75×");
  });

  test("a rate that is not one falls back to one", () => {
    // `playbackRate` is never absent on a real element, but a picker built
    // from a stale value should not print `NaN×` at a viewer.
    for (const rate of [undefined, null, 0, -1, Number.NaN, "fast"]) {
      expect(speedLabel(rate as never)).toBe("1×");
    }
  });
});

describe("what the play button offers", () => {
  test("the opposite of what is happening", () => {
    expect(playLabel(true)).toBe("Play");
    expect(playLabel(false)).toBe("Pause");
  });
});

describe("silence", () => {
  test("muted, or turned all the way down, is the same to a listener", () => {
    expect(isSilent({ volume: 1, muted: true })).toBe(true);
    expect(isSilent({ volume: 0, muted: false })).toBe(true);
  });

  test("and anything audible is not", () => {
    expect(isSilent({ volume: 1, muted: false })).toBe(false);
    expect(isSilent({ volume: 0.01, muted: false })).toBe(false);
  });
});
