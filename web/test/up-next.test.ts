import { describe, expect, test } from "bun:test";
import { COUNTDOWN_SECONDS, WARN_SECONDS, upNextPhase } from "../public/lib/playback/up-next.js";

const at = (over: Record<string, unknown> = {}) => ({
  hasNext: true,
  cancelled: false,
  remainingSeconds: 600,
  ended: false,
  ...over,
});

describe("when the next title is offered", () => {
  test("is not offered in the middle of a title", () => {
    expect(upNextPhase(at())).toBe("hidden");
  });

  test("is offered as a heads-up near the end", () => {
    expect(upNextPhase(at({ remainingSeconds: WARN_SECONDS }))).toBe("waiting");
    expect(upNextPhase(at({ remainingSeconds: 5 }))).toBe("waiting");
  });

  test("never counts down before the title has ended", () => {
    // The bug: the panel appeared 30s out carrying a 10s timer, so the next
    // episode began with 20 seconds of this one still to play.
    for (const remaining of [WARN_SECONDS, 20, 5, 1, 0]) {
      expect(upNextPhase(at({ remainingSeconds: remaining }))).not.toBe("counting");
    }
  });

  test("counts down once it has ended", () => {
    expect(upNextPhase(at({ ended: true, remainingSeconds: 0 }))).toBe("counting");
  });

  test("counts down on an end that arrived by seeking past the last frame", () => {
    expect(upNextPhase(at({ ended: true, remainingSeconds: 600 }))).toBe("counting");
  });
});

describe("when it is not offered at all", () => {
  test("nothing follows this title", () => {
    expect(upNextPhase(at({ hasNext: false, remainingSeconds: 2 }))).toBe("hidden");
    expect(upNextPhase(at({ hasNext: false, ended: true }))).toBe("hidden");
  });

  test("the viewer cancelled it, which is remembered for this title", () => {
    expect(upNextPhase(at({ cancelled: true, remainingSeconds: 2 }))).toBe("hidden");
    // Cancelling must survive the end, or the countdown would arrive anyway.
    expect(upNextPhase(at({ cancelled: true, ended: true }))).toBe("hidden");
  });

  test("the runtime is unknown, so there is nothing to count down from", () => {
    expect(upNextPhase(at({ remainingSeconds: null }))).toBe("hidden");
    expect(upNextPhase(at({ remainingSeconds: Number.NaN }))).toBe("hidden");
    // The end still catches it, whatever the runtime said.
    expect(upNextPhase(at({ remainingSeconds: null, ended: true }))).toBe("counting");
  });
});

describe("the timings themselves", () => {
  test("warn before the end, and count after it", () => {
    expect(WARN_SECONDS).toBeGreaterThan(0);
    expect(COUNTDOWN_SECONDS).toBeGreaterThan(0);
  });
});
